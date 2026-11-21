package com.fortheelder.data.rhythm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.domain.engine.rhythm.TimeSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 「现在这份作息画像是什么」的真机验证。
 *
 * 这里要证的**不是推断算得对不对**（那是 `RhythmInferenceTest` 的活），而是三件
 * 只有接上真数据库才成立的事：
 * 1. 今天第一次问，会把信号读出来算一遍，并**真的写进 `daily_rhythm` 表**——
 *    这张表在接入它之前是零调用者的，落了库才算接上；
 * 2. 今天第二次问，不再重算，直接返回落库的那一份（否则一天之内反复算同一批信号）；
 * 3. 「数据不够」「说不通」这两种结果**不落库**，免得下次读出来分不清是结论还是空响应。
 */
@RunWith(AndroidJUnit4::class)
class RhythmProfileServiceTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；命名必须以 `elder-test-` 开头。 */
    private val testDb = "elder-test-rhythm-profile.db"

    private lateinit var database: ElderDatabase
    private lateinit var repository: RhythmRepository
    private lateinit var service: RhythmProfileService

    /** 固定时区：不用系统默认值，免得跑测试的机器时区一变结果就变。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」：2026-03-10 09:00，作息日就是 2026-03-10（分界点 04:00）。 */
    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /** 今天这个作息日的 `epochDay`，服务内部算的就是它。 */
    private val today: Long = LocalDate.of(2026, 3, 10).toEpochDay()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)
        repository = RhythmRepository(database, AppClock { now }, zone)
        service = RhythmProfileService(repository, AppClock { now })
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun `信号够了第一次问就给出画像并真的落库`() = runBlocking(Dispatchers.IO) {
        recordThreeRegularDays()

        val result = service.current()

        val ready = result as RhythmResult.Ready
        assertEquals(3, ready.rhythm.sampleDays)
        assertEquals(LocalTime.of(7, 0), ready.rhythm.wakeAt)
        assertEquals(LocalTime.of(19, 40), ready.rhythm.sleepAt)
        // 落库这件事本身就是它的职责：读回来必须是同一份。
        assertEquals(ready.rhythm, repository.profile(today))
    }

    @Test
    fun `今天已经算过就直接返回落库的那一份`() = runBlocking(Dispatchers.IO) {
        // 先手工塞一份今天独有的画像。它和下面这批信号推出来的作息对不上，
        // 所以只要读回来是这一份，就说明没有重算。
        val saved = rhythm(wakeAt = LocalTime.of(5, 15))
        repository.saveProfile(today, saved)
        recordThreeRegularDays()

        val result = service.current()

        assertEquals(RhythmResult.Ready(saved), result)
        assertEquals(1, database.dailyRhythmDao().count())
    }

    @Test
    fun `信号不够时只是了解中而且不落库`() = runBlocking(Dispatchers.IO) {
        val result = service.current()

        assertEquals(RhythmResult.Gathering(validDays = 0, requiredDays = 3), result)
        assertNull(repository.profile(today))
        assertEquals(0, database.dailyRhythmDao().count())
    }

    @Test
    fun `信号全挤在一起时判定为说不通而且不落库`() = runBlocking(Dispatchers.IO) {
        // 每天 20 条信号，但全落在 07:00-08:16 这一小时多里，凑不出一整天的作息。
        for (day in 8..10) {
            repository.record(
                (0 until 20).map {
                    CapturedSignal(
                        LocalDateTime.of(2026, 3, day, 7, 0).plusMinutes(it * 4L),
                        RhythmKind.SCREEN_ON,
                    )
                }
            )
        }

        val result = service.current()

        assertTrue("期望 Inconsistent，实际是 $result", result is RhythmResult.Inconsistent)
        assertNull(repository.profile(today))
        assertEquals(0, database.dailyRhythmDao().count())
    }

    /**
     * 3 月 8/9/10 三天，每天 20 条「人在场」信号，从 07:00 到 19:40。
     *
     * 每天 20 条刚好够上有效天的下限，跨度 760 分钟远超过 6 小时的下限，
     * 三天几乎一样，所以推断会给出 [RhythmResult.Ready]。
     */
    private suspend fun recordThreeRegularDays() {
        for (day in 8..10) {
            repository.record(
                (0 until 20).map {
                    CapturedSignal(
                        LocalDateTime.of(2026, 3, day, 7, 0).plusMinutes(it * 40L),
                        RhythmKind.SCREEN_ON,
                    )
                }
            )
        }
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
