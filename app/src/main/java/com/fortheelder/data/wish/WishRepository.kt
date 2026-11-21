package com.fortheelder.data.wish

import androidx.room.withTransaction
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.db.WishEntity
import com.fortheelder.domain.engine.intent.IntentHit
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishGate
import com.fortheelder.domain.wish.WishStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * 意愿的读写入口（DR-07）。
 *
 * 这一层负责数据库不愿意管的两件事：**时区换算**（界面给的是本地时刻，库里存绝对毫秒）
 * 与**写入串行化**。
 *
 * 串行化用一把 [Mutex]，不是保险起见，是补一个真实的竞态：用户点了一句示例话之后
 * 立刻点「愿意了解一下」，这两次点击各自 `launch` 一条协程，而第一条的插入可能还没
 * 进到数据库。把所有写操作放进同一把锁里，第二次点击就会等第一次写完再执行，
 * 于是「点得比落库还快」不会变成一个点了没反应的按钮。
 *
 * 意愿**长期保留**，没有保留期：它跟作息数据不一样，不是靠积累量算出来的统计量，
 * 而是用户自己的事。三个月前说过想学书法，今天还是想学。删除只由用户决定，
 * 单条能删（[forget]），也能一键清空（[forgetAll]）。
 */
class WishRepository(
    private val database: ElderDatabase,
    private val clock: AppClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val dao = database.wishDao()

    private val writeLock = Mutex()

    /** 「常伴记住的事」，按首次听到的先后。改动一次就重新发出。 */
    val remembered: Flow<List<StoredWish>> =
        dao.allFlow().map { rows -> rows.map(::toDomain) }

    /**
     * 记下一批刚听出来的意愿。
     *
     * 已经记过的会被跳过，所以**重复提同一件事不会重置它的状态**：用户上次说了
     * 「先不用」，这次又提一遍，它还是「先不用」。要改状态只能走 [decide]。
     *
     * @param sentence 用户说的整句原话，用来回答「它是从哪儿听出这件事的」。
     */
    suspend fun remember(sentence: String, hits: List<IntentHit>) = writeLock.withLock {
        if (hits.isEmpty()) return@withLock
        val now = nowMillis()
        dao.insertAll(
            hits.map { hit ->
                WishEntity(
                    kind = hit.kind.name,
                    term = hit.term,
                    evidence = hit.evidence,
                    sentence = sentence,
                    status = WishStatus.PENDING.name,
                    heardAtEpochMilli = now,
                    decidedAtEpochMilli = null,
                )
            }
        )
    }

    /**
     * 用户在闸门上表了态。
     *
     * @return 状态真的变了才是 `true`。返回 `false` 不是错误：可能是这条意愿还没落库
     *   （点得太快），也可能是重复点。两种情况界面都不该假装成功。
     */
    suspend fun decide(kind: IntentKind, term: String, decision: WishDecision): Boolean =
        writeLock.withLock {
            val current = dao.find(kind.name, term)?.toStatus() ?: return@withLock false
            val next = WishGate.decide(current, decision) ?: return@withLock false
            dao.decide(kind.name, term, current.name, next.name, nowMillis()) > 0
        }

    /** 用户点「不用记了」。删掉这条，连带它的证据片段。 */
    suspend fun forget(kind: IntentKind, term: String): Boolean = writeLock.withLock {
        dao.delete(kind.name, term) > 0
    }

    /**
     * 用户点「全部清空」。
     *
     * 放在事务里：这是一次不可撤销的删除，不该出现删了一半的状态。
     */
    suspend fun forgetAll() = writeLock.withLock {
        database.withTransaction { dao.deleteAll() }
    }

    /** 观测用：设置页之类的地方想显示条数时用。 */
    suspend fun count(): Int = dao.count()

    private fun nowMillis(): Long = clock.now().atZone(zone).toInstant().toEpochMilli()

    private fun toDomain(entity: WishEntity): StoredWish = StoredWish(
        kind = IntentKind.valueOf(entity.kind),
        term = entity.term,
        evidence = entity.evidence,
        sentence = entity.sentence,
        status = entity.toStatus(),
        heardAtEpochMilli = entity.heardAtEpochMilli,
        decidedAtEpochMilli = entity.decidedAtEpochMilli,
    )

    /**
     * 状态列解不出来时的兜底是 [WishStatus.DECLINED]。
     *
     * 兜底方向是**刻意选的**：读不懂的状态下宁可当作「人家说了不用」而不去打扰，
     * 也不要当作「已同意」往外推内容。真要出现了（例如以后有人手改数据库），
     * 表现是这条意愿安静地躺在列表里，而不是突然变成一次推销。
     */
    private fun WishEntity.toStatus(): WishStatus =
        runCatching { WishStatus.valueOf(status) }.getOrDefault(WishStatus.DECLINED)
}
