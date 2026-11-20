package com.fortheelder.data.rhythm

import androidx.room.withTransaction
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.RhythmSignal
import com.fortheelder.domain.engine.rhythm.rhythmDayOf
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 作息信号保留天数。
 *
 * 30 天是「够推断出作息」和「不无限攒数据」之间的取舍：推断最少只要 3 天，
 * 30 天足够看出这个月和下个月有没有变化，同时让这个 App 手里始终只有最近一个月的
 * 屏幕开关记录。保留期一过就删，不需要用户来点「清理」。
 */
const val SIGNAL_RETENTION_DAYS = 30

/**
 * 作息画像保留天数。和信号同一个窗口，理由是画像由信号推出来，
 * 留下比信号更老的画像只会出现「画像里的日子已经没有信号了」这种对不上的状态。
 */
const val RHYTHM_RETENTION_DAYS = 30

/**
 * 作息数据的读写入口。
 *
 * 领域层是纯函数，这一层负责三件它不该管的事：
 * 1. 时区——本地时刻与绝对时刻的换算（[RhythmMapping]）；
 * 2. 时间窗口——「最近 30 天」按**作息日**切，不按自然日切（见 [windowStartEpochMilli]）；
 * 3. 保留期——读写时顺手把过期的删掉。
 *
 * 时间的读取一律走 [AppClock]，这一层里没有一处 `LocalDateTime.now()`。
 *
 * @param zone 采集与还原本地时刻用的时区。默认跟随系统；测试里传固定时区。
 * @param cutoverHour 作息日分界点。必须和推断引擎用的是同一个值，
 *   否则存进去的偏移量会被按另一个分界点解出来，整幅画像错几小时。
 */
class RhythmRepository(
    private val database: ElderDatabase,
    private val clock: AppClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val cutoverHour: Int = RhythmConfig.DEFAULT_CUTOVER_HOUR,
) {

    private val signalDao = database.rhythmSignalDao()
    private val rhythmDao = database.dailyRhythmDao()

    /**
     * 落库一批信号，返回**真正新增**的条数。
     *
     * 重复的信号（同一个种类、同一个时刻）会被数据库的唯一索引挡掉，不计入返回值。
     * 系统在某些情况下会把一次亮屏报两遍，去重是必须的：重复事件会让「这天信号数」
     * 虚高，直接影响有效天的判定。
     */
    suspend fun record(incoming: List<CapturedSignal>): Int {
        if (incoming.isEmpty()) return 0
        val rows = incoming.map { RhythmMapping.toEntity(it, zone) }
        return signalDao.insertAll(rows).count { it != -1L }
    }

    /** 最近 [sinceDays] 个**作息日**里的信号，按时刻升序。 */
    suspend fun signals(sinceDays: Int = SIGNAL_RETENTION_DAYS): List<RhythmSignal> =
        signalDao.since(windowStartEpochMilli(sinceDays)).mapNotNull(RhythmMapping::toDomain)

    /** 设置页显示「已记录 N 条」。信号变化时会自己重新发出。 */
    val signalCount: Flow<Int> get() = signalDao.countFlow()

    suspend fun signalCountOnce(): Int = signalDao.count()

    /** 存下某一天的画像。同一天重复存会整行覆盖。 */
    suspend fun saveProfile(dayEpochDay: Long, rhythm: DailyRhythm) {
        rhythmDao.upsert(
            listOf(
                RhythmMapping.toEntity(
                    dayEpochDay = dayEpochDay,
                    rhythm = rhythm,
                    computedAtEpochMilli = clock.now().atZone(zone).toInstant().toEpochMilli(),
                    cutoverHour = cutoverHour,
                )
            )
        )
    }

    suspend fun profile(dayEpochDay: Long): DailyRhythm? =
        rhythmDao.byDay(dayEpochDay)?.let { RhythmMapping.toDomain(it, cutoverHour) }

    suspend fun latestProfile(): DailyRhythm? =
        rhythmDao.latest()?.let { RhythmMapping.toDomain(it, cutoverHour) }

    /**
     * 删掉过期数据，返回删掉的总行数。
     *
     * 信号和画像一起删，放在一个事务里：只删成功一半会留下「画像还在、信号没了」
     * 的状态，下次重算就会把画像换成「数据不足」。
     */
    suspend fun prune(): Int = database.withTransaction {
        val signalCutoff = windowStartEpochMilli(SIGNAL_RETENTION_DAYS)
        val rhythmCutoff = rhythmDayOf(clock.now(), cutoverHour)
            .minusDays(RHYTHM_RETENTION_DAYS - 1L)
            .toEpochDay()
        signalDao.deleteBefore(signalCutoff) + rhythmDao.deleteBefore(rhythmCutoff)
    }

    /** 用户在设置页点了「清空作息数据」。不可撤销，界面必须先问一次。 */
    suspend fun clearAll() = database.withTransaction {
        signalDao.deleteAll()
        rhythmDao.deleteAll()
    }

    /**
     * 最近 [days] 个作息日的起点，换算成绝对毫秒。
     *
     * 窗口按作息日算而不是按「此刻往前推 30×24 小时」算，是为了让「最近 30 天」
     * 在界面上和在数据库里是同一件事——不然下午三点打开时，窗口起点会落在
     * 某个凌晨三点半，切在半夜里。
     */
    private fun windowStartEpochMilli(days: Int): Long {
        require(days >= 1) { "days 至少为 1：$days" }
        val today: LocalDate = rhythmDayOf(clock.now(), cutoverHour)
        val start: LocalDateTime = today.minusDays(days - 1L).atTime(cutoverHour, 0)
        return start.atZone(zone).toInstant().toEpochMilli()
    }
}
