package com.fortheelder.data.anchor

import androidx.room.withTransaction
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.AnchorEntity
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.AnchorDraft
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 生活锚点的读写入口（DR-08）。
 *
 * 这一层只做三件事：时区换算、写入串行化、把时段字符串解回枚举。
 * 判断「此刻该不该摆出来」不在这里，在
 * [com.fortheelder.domain.companion.AnchorRules.dueAt]，那是纯函数，单测能直接扫边界。
 *
 * 锚点**只能由用户亲手建立**：没有任何一条路径会自己往里写。写入的唯一入口 [add]
 * 需要一份 [AnchorDraft]，而草稿只能由
 * [com.fortheelder.domain.companion.AnchorRules.draftOf] 从目录里的起点造出来，
 * 界面要先显示时段按钮、等用户点下去（AC-09.1）。
 *
 * 写入用一把 [Mutex] 串行化，理由与 `WishRepository` 相同：用户点得比落库快时，
 * 两次点击各是一条协程，去重虽然由唯一索引兜着，但「点了没反应」不该由并发决定。
 */
class AnchorRepository(
    private val database: ElderDatabase,
    private val clock: AppClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val dao = database.anchorDao()

    private val writeLock = Mutex()

    /** 「常伴记着的日常」，按用户记下的先后。改动一次就重新发出。 */
    val anchors: Flow<List<Anchor>> = dao.allFlow().map { rows -> rows.map(::toDomain) }

    /**
     * 记下一条日常。
     *
     * @return 真的写进去了才是 `true`。同一件事已经记过时返回 `false`——界面不该假装
     *   又记了一条。
     */
    suspend fun add(draft: AnchorDraft): Boolean = writeLock.withLock {
        dao.insert(
            AnchorEntity(
                term = draft.term,
                optionId = draft.optionId,
                action = draft.action,
                slot = draft.slot.name,
                createdAtEpochMilli = nowMillis(),
            )
        ) > 0
    }

    /** 用户点「不用记了」。删掉就是删掉，不追问。 */
    suspend fun remove(optionId: String): Boolean = writeLock.withLock {
        dao.deleteByOption(optionId) > 0
    }

    /**
     * 用户点「清空全部数据」。记下的日常一次全删。
     *
     * 与 `WishRepository.forgetAll` 同形：挂在写锁下、放在一个事务里。写锁是为了让
     * 「正在记一条」和「正在全删」不交错；事务是为了不出现删了一半的表——这一步不可撤销，
     * 半清状态的收尾方式是「再点一次」，那对用户来说是又一次惊吓。
     *
     * @return 真正删掉的条数。这一轮的界面用它判断该不该给回执。
     */
    suspend fun clearAll(): Int = writeLock.withLock {
        database.withTransaction { dao.deleteAll() }
    }

    /** 此刻该摆出来的日常：只有时段命中的那些。 */
    suspend fun dueNow(now: LocalDateTime): List<Anchor> =
        AnchorRules.dueAt(dao.all().map(::toDomain), now.toLocalTime())

    /** 观测用：设置页之类的地方想显示条数时用。 */
    suspend fun count(): Int = dao.count()

    private fun nowMillis(): Long = clock.now().atZone(zone).toInstant().toEpochMilli()

    private fun toDomain(entity: AnchorEntity): Anchor = Anchor(
        id = entity.id,
        term = entity.term,
        optionId = entity.optionId,
        action = entity.action,
        slot = AnchorSlot.parse(entity.slot),
        createdAtEpochMilli = entity.createdAtEpochMilli,
    )
}
