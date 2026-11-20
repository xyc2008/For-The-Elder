package com.fortheelder.domain.engine.greeting

import com.fortheelder.core.time.AppClock
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 问候引擎的单元测试。
 *
 * 时间全部由固定时钟喂进来，所以断言是确定的，不会出现「半夜跑 CI 就红」的情况。
 */
class GreetingEngineTest {

    private fun engineAt(moment: String): GreetingEngine =
        GreetingEngine(AppClock { LocalDateTime.parse(moment) })

    /** 固定「今天」为 2026-03-10，用来算天数差。 */
    private val today = LocalDateTime.parse("2026-03-10T08:00:00")

    private fun engineToday() = GreetingEngine(AppClock { today })

    @Test
    fun `早上七点带称呼问好`() {
        val greeting = engineAt("2026-03-10T07:30:00").greet("老张", firstLaunchEpochDay = null)

        assertEquals(DayPart.MORNING, greeting.dayPart)
        assertEquals("老张，早上好", greeting.headline)
        assertEquals("第一次见面，慢慢来。", greeting.note)
        assertEquals(1L, greeting.dayCount)
    }

    @Test
    fun `没填称呼时不出现多余标点`() {
        val greeting = engineAt("2026-03-10T15:00:00").greet(null, firstLaunchEpochDay = null)

        assertEquals("下午好", greeting.headline)
        assertTrue("headline 里不应出现逗号：${greeting.headline}", !greeting.headline.contains('，'))
    }

    @Test
    fun `只有空白的称呼按没填处理`() {
        val greeting = engineAt("2026-03-10T15:00:00").greet("   ", firstLaunchEpochDay = null)

        assertEquals("下午好", greeting.headline)
    }

    @Test
    fun `深夜只提示休息不引导继续用`() {
        val greeting = engineAt("2026-03-10T23:40:00").greet("老张", firstLaunchEpochDay = null)

        assertEquals(DayPart.LATE_NIGHT, greeting.dayPart)
        assertEquals("老张，夜深了", greeting.headline)
        assertEquals("不着急，什么时候想说都行。", greeting.note)
    }

    @Test
    fun `深夜的补充语不会覆盖首次见面语`() {
        // 23:40 既属于深夜，也是第一天。两者同时成立时应当说深夜那句，
        // 因为「早点休息」在这个时间点是比「慢慢来」更该说的话。
        val greeting = engineAt("2026-03-10T23:40:00").greet(null, firstLaunchEpochDay = null)

        assertEquals("不着急，什么时候想说都行。", greeting.note)
    }

    @Test
    fun `首次启动当天算第一天`() {
        val greeting = engineToday().greet(null, firstLaunchEpochDay = today.toLocalDate().toEpochDay())

        assertEquals(1L, greeting.dayCount)
        assertEquals("第一次见面，慢慢来。", greeting.note)
    }

    @Test
    fun `第二天开始报天数`() {
        val firstDay = today.toLocalDate().toEpochDay() - 1

        val greeting = engineToday().greet(null, firstLaunchEpochDay = firstDay)

        assertEquals(2L, greeting.dayCount)
        assertEquals("已经认识 2 天了。", greeting.note)
    }

    @Test
    fun `认识满三十天`() {
        val firstDay = today.toLocalDate().toEpochDay() - 29

        val greeting = engineToday().greet(null, firstLaunchEpochDay = firstDay)

        assertEquals(30L, greeting.dayCount)
        assertEquals("已经认识 30 天了。", greeting.note)
    }

    @Test
    fun `用户把系统时间往前调也不会出现第零天`() {
        // 首次启动记在「今天」，之后用户把系统时间倒拨了 5 天，差值为负。
        val firstDay = today.toLocalDate().toEpochDay() + 5

        val greeting = engineToday().greet(null, firstLaunchEpochDay = firstDay)

        assertEquals(1L, greeting.dayCount)
        assertTrue("不该出现 0 或负数：${greeting.note}", !greeting.note.contains("-"))
    }

    @Test
    fun `时段边界划分`() {
        assertEquals(DayPart.LATE_NIGHT, DayPart.of(LocalTime.of(4, 59)))
        assertEquals(DayPart.EARLY_MORNING, DayPart.of(LocalTime.of(5, 0)))
        assertEquals(DayPart.EARLY_MORNING, DayPart.of(LocalTime.of(6, 59)))
        assertEquals(DayPart.MORNING, DayPart.of(LocalTime.of(7, 0)))
        assertEquals(DayPart.FORENOON, DayPart.of(LocalTime.of(9, 0)))
        assertEquals(DayPart.NOON, DayPart.of(LocalTime.of(12, 0)))
        assertEquals(DayPart.AFTERNOON, DayPart.of(LocalTime.of(14, 0)))
        assertEquals(DayPart.EVENING, DayPart.of(LocalTime.of(18, 0)))
        assertEquals(DayPart.LATE_NIGHT, DayPart.of(LocalTime.of(23, 0)))
    }
}
