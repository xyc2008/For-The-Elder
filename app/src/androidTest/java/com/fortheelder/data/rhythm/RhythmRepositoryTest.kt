package com.fortheelder.data.rhythm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.TimeSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 作息仓库的**真机**验证，用的是和正式应用同一个 [ElderDatabase.open]。
 *
 * 和 `RhythmDaoTest` 的分工：那边测 SQL 本身（唯一索引、窗口、失效通知），
 * 这边测仓库把哪些事替上层做掉了——时区换算、按作息日切窗口、保留期清理、
 * 以及「同一个进程里关掉库再打开，数据还在」。
 *
 * 这里的库用的是正常配置（没有 `allowMainThreadQueries`），所有操作都放在
 * `Dispatchers.IO` 上；顺带证明主线程访问数据库这条约束没有被悄悄放开。
 */
@RunWith(AndroidJUnit4::class)
class RhythmRepositoryTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；原因见 `ElderDatabase.open` 的注释。 */
    private val testDb = "elder-test-rhythm-repository.db"

    private lateinit var database: ElderDatabase
    private lateinit var repository: RhythmRepository

    /** 固定时区：不用系统默认值，免得跑测试的机器时区一变结果就变。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」：2026-03-10 09:00，作息日就是 2026-03-10（分界点 04:00）。 */
    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /** 保留 30 天时的窗口起点：2026-03-10 往前 29 天，即 2026-02-09 04:00。 */
    private val windowStart: LocalDateTime = LocalDateTime.of(2026, 2, 9, 4, 0)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)
        repository = RhythmRepository(database, AppClock { now }, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun `落库后能按原样的本地时刻读回来`() = runBlocking(Dispatchers.IO) {
        val at = LocalDateTime.of(2026, 3, 9, 7, 30)

        assertEquals(1, repository.record(listOf(CapturedSignal(at, RhythmKind.SCREEN_ON))))

        val stored = repository.signals()
        assertEquals(1, stored.size)
        assertEquals(at, stored.single().at)
        assertEquals(RhythmKind.SCREEN_ON, stored.single().kind)
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `同一条信号重复落库只算一条`() = runBlocking(Dispatchers.IO) {
        val signal = CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 30), RhythmKind.SCREEN_ON)
        assertEquals(1, repository.record(listOf(signal)))
        assertEquals(0, repository.record(listOf(signal)))
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `一批信号按真正写进去的条数返回`() = runBlocking(Dispatchers.IO) {
        val batch = listOf(
            CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 0), RhythmKind.SCREEN_ON),
            CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 5), RhythmKind.USER_PRESENT),
            CapturedSignal(LocalDateTime.of(2026, 3, 9, 22, 0), RhythmKind.CHARGING_START),
        )

        assertEquals(3, repository.record(batch))
        // 同一批再来一次：三条都撞上唯一索引，**一条都没写进去**，所以是 0 而不是 3。
        // 返回的是「本次真正新增的条数」，设置页拿它显示「已记录 N 条」——
        // 把重复的算进去，用户会觉得手机比以前更频繁地被点亮了。
        assertEquals(0, repository.record(batch))
        assertEquals(3, repository.signals().size)
    }

    @Test
    fun `空的一批不落库也不算新增`() = runBlocking(Dispatchers.IO) {
        assertEquals(0, repository.record(emptyList()))
        assertEquals(0, repository.signalCountOnce())
    }

    @Test
    fun `保留窗口之外的信号读不到但还留在库里`() = runBlocking(Dispatchers.IO) {
        repository.record(
            listOf(CapturedSignal(LocalDateTime.of(2026, 1, 1, 7, 0), RhythmKind.SCREEN_ON))
        )

        assertEquals(0, repository.signals().size)
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `保留窗口的边界正好落在作息日的分界点上`() = runBlocking(Dispatchers.IO) {
        repository.record(
            listOf(
                CapturedSignal(windowStart.minusMinutes(1), RhythmKind.SCREEN_ON),
                CapturedSignal(windowStart, RhythmKind.SCREEN_OFF),
            )
        )

        val stored = repository.signals()
        assertEquals(1, stored.size)
        assertEquals(RhythmKind.SCREEN_OFF, stored.single().kind)
        assertEquals(windowStart, stored.single().at)
    }

    @Test
    fun `画像存下来能原样读回`() = runBlocking(Dispatchers.IO) {
        val rhythm = rhythm(wakeAt = LocalTime.of(6, 30))
        val day = LocalDate.of(2026, 3, 10).toEpochDay()

        repository.saveProfile(day, rhythm)

        assertEquals(rhythm, repository.profile(day))
        assertEquals(rhythm, repository.latestProfile())
        assertNull(repository.profile(day - 1))
    }

    @Test
    fun `同一天的画像重存会覆盖而不是多出一行`() = runBlocking(Dispatchers.IO) {
        val day = LocalDate.of(2026, 3, 10).toEpochDay()

        repository.saveProfile(day, rhythm(wakeAt = LocalTime.of(6, 30)))
        repository.saveProfile(day, rhythm(wakeAt = LocalTime.of(7, 15)))

        assertEquals(LocalTime.of(7, 15), repository.profile(day)?.wakeAt)
        assertEquals(1, database.dailyRhythmDao().count())
    }

    @Test
    fun `入睡锚点看不出规律时读回来仍是空`() = runBlocking(Dispatchers.IO) {
        val rhythm = rhythm(wakeAt = LocalTime.of(6, 30)).copy(nightChargingAt = null)
        val day = LocalDate.of(2026, 3, 10).toEpochDay()

        repository.saveProfile(day, rhythm)

        assertNull(repository.latestProfile()?.nightChargingAt)
    }

    @Test
    fun `清理只删过期的那一份`() = runBlocking(Dispatchers.IO) {
        val stale = LocalDateTime.of(2026, 1, 1, 7, 0)
        val recent = LocalDateTime.of(2026, 3, 9, 7, 0)
        repository.record(
            listOf(
                CapturedSignal(stale, RhythmKind.SCREEN_ON),
                CapturedSignal(recent, RhythmKind.SCREEN_ON),
            )
        )
        val staleDay = LocalDate.of(2026, 1, 1).toEpochDay()
        val recentDay = LocalDate.of(2026, 3, 9).toEpochDay()
        repository.saveProfile(staleDay, rhythm(wakeAt = LocalTime.of(5, 0)))
        repository.saveProfile(recentDay, rhythm(wakeAt = LocalTime.of(6, 30)))

        assertEquals(2, repository.prune())

        assertEquals(1, repository.signalCountOnce())
        assertEquals(recent, repository.signals().single().at)
        assertNull(repository.profile(staleDay))
        assertEquals(LocalTime.of(6, 30), repository.profile(recentDay)?.wakeAt)
    }

    @Test
    fun `清理在全是新数据时什么都不删`() = runBlocking(Dispatchers.IO) {
        repository.record(
            listOf(CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 0), RhythmKind.SCREEN_ON))
        )
        repository.saveProfile(LocalDate.of(2026, 3, 9).toEpochDay(), rhythm(LocalTime.of(6, 30)))

        assertEquals(0, repository.prune())
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `用户清空之后信号和画像一起没了`() = runBlocking(Dispatchers.IO) {
        repository.record(
            listOf(CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 0), RhythmKind.SCREEN_ON))
        )
        repository.saveProfile(LocalDate.of(2026, 3, 9).toEpochDay(), rhythm(LocalTime.of(6, 30)))

        repository.clearAll()

        assertEquals(0, repository.signalCountOnce())
        assertEquals(0, repository.signals().size)
        assertNull(repository.latestProfile())
    }

    @Test
    fun `关掉数据库再打开数据还在`() = runBlocking(Dispatchers.IO) {
        val at = LocalDateTime.of(2026, 3, 9, 7, 30)
        repository.record(listOf(CapturedSignal(at, RhythmKind.SCREEN_ON)))
        repository.saveProfile(LocalDate.of(2026, 3, 9).toEpochDay(), rhythm(LocalTime.of(6, 30)))
        database.close()

        database = ElderDatabase.open(context, testDb)
        val reopened = RhythmRepository(database, AppClock { now }, zone)

        assertEquals(1, reopened.signalCountOnce())
        assertEquals(at, reopened.signals().single().at)
        assertEquals(LocalTime.of(6, 30), reopened.latestProfile()?.wakeAt)
    }

    private fun rhythm(wakeAt: LocalTime) = DailyRhythm(
        wakeAt = wakeAt,
        sleepAt = LocalTime.of(22, 30),
        activeSpans = listOf(TimeSpan(LocalTime.of(7, 0), LocalTime.of(11, 0))),
        idleSpans = listOf(TimeSpan(LocalTime.of(14, 0), LocalTime.of(16, 0))),
        nightChargingAt = LocalTime.of(23, 0),
        sampleDays = 5,
        confidence = 0.75f,
    )
}
