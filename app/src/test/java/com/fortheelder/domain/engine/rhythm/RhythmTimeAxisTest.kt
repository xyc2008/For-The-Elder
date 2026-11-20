package com.fortheelder.domain.engine.rhythm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 作息日时间轴的换算（[rhythmDayOf] / [toRhythmOffset] / [rhythmOffsetToLocalTime]）。
 *
 * 这三条换算是引擎、采集器、数据库三边共用的地基：任何一处差一小时，
 * 整幅画像就会整体平移一小时，而且从界面上看不出来。所以这里逐点钉死具体数值，
 * 不做「大概对」的断言。
 */
class RhythmTimeAxisTest {

    private val cutover = 4

    @Test
    fun `04点整是作息日的起点`() {
        assertEquals(0, LocalTime.of(4, 0).toRhythmOffset(cutover))
    }

    @Test
    fun `子夜前后仍然属于同一天的尾段`() {
        assertEquals(1230, LocalTime.of(0, 30).toRhythmOffset(cutover))
        assertEquals(1199, LocalTime.of(23, 59).toRhythmOffset(cutover))
        assertEquals(1439, LocalTime.of(3, 59).toRhythmOffset(cutover))
    }

    @Test
    fun `分界点之后的时刻落在一天的前段`() {
        assertEquals(150, LocalTime.of(6, 30).toRhythmOffset(cutover))
        assertEquals(1110, LocalTime.of(22, 30).toRhythmOffset(cutover))
    }

    @Test
    fun `偏移量始终落在一天之内`() {
        for (hour in 0..23) {
            for (minute in intArrayOf(0, 17, 59)) {
                val offset = LocalTime.of(hour, minute).toRhythmOffset(cutover)
                assert(offset in 0 until MINUTES_PER_DAY) {
                    "$hour:$minute 算出的偏移量越界：$offset"
                }
            }
        }
    }

    @Test
    fun `偏移量与本地时刻互为逆运算`() {
        for (hour in 0..23) {
            for (minute in intArrayOf(0, 30, 59)) {
                val time = LocalTime.of(hour, minute)
                val back = rhythmOffsetToLocalTime(time.toRhythmOffset(cutover), cutover)
                assertEquals(time, back)
            }
        }
    }

    @Test
    fun `分界点换一个值也互为逆运算`() {
        for (boundary in intArrayOf(0, 3, 4, 12, 23)) {
            for (hour in 0..23) {
                val time = LocalTime.of(hour, 45)
                val back = rhythmOffsetToLocalTime(time.toRhythmOffset(boundary), boundary)
                assertEquals("分界点 $boundary，时刻 $time", time, back)
            }
        }
    }

    @Test
    fun `分界点前后分属两个作息日`() {
        assertEquals(
            LocalDate.of(2026, 1, 1),
            rhythmDayOf(LocalDateTime.of(2026, 1, 2, 3, 30), cutover),
        )
        assertEquals(
            LocalDate.of(2026, 1, 2),
            rhythmDayOf(LocalDateTime.of(2026, 1, 2, 4, 0), cutover),
        )
    }

    @Test
    fun `晚睡到子夜之后仍算前一天熬夜`() {
        val evening = rhythmDayOf(LocalDateTime.of(2026, 3, 7, 23, 30), cutover)
        val afterMidnight = rhythmDayOf(LocalDateTime.of(2026, 3, 8, 0, 30), cutover)
        assertEquals(evening, afterMidnight)
    }

    @Test
    fun `分界点为0时作息日就等于自然日`() {
        assertEquals(
            LocalDate.of(2026, 3, 8),
            rhythmDayOf(LocalDateTime.of(2026, 3, 8, 0, 0), 0),
        )
        assertEquals(
            LocalDate.of(2026, 3, 7),
            rhythmDayOf(LocalDateTime.of(2026, 3, 8, 0, 0).minusMinutes(1), 0),
        )
    }
}
