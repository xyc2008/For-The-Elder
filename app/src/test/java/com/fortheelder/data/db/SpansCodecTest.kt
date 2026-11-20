package com.fortheelder.data.db

import com.fortheelder.domain.engine.rhythm.TimeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * 时段的文本编解码（DR-06）。
 *
 * 重点是**坏数据不炸**：这一列存在 SQLite 里，用户手改、迁移写错、磁盘损坏都可能
 * 让它变成一个看不懂的字符串。解码失败必须表现为「这天的画像不存在」，
 * 而不是让设置页崩掉。
 */
class SpansCodecTest {

    private fun span(from: Int, to: Int): TimeSpan =
        TimeSpan(LocalTime.of(from, 0), LocalTime.of(to, 0))

    @Test
    fun `空列表编成空串再解回空列表`() {
        assertEquals("", SpansCodec.encode(emptyList()))
        assertEquals(emptyList<TimeSpan>(), SpansCodec.decode(""))
    }

    @Test
    fun `单段往返`() {
        val spans = listOf(span(9, 11))
        assertEquals("09:00-11:00", SpansCodec.encode(spans))
        assertEquals(spans, SpansCodec.decode("09:00-11:00"))
    }

    @Test
    fun `多段往返`() {
        val spans = listOf(span(9, 11), span(14, 16), span(19, 21))
        val encoded = SpansCodec.encode(spans)
        assertEquals("09:00-11:00,14:00-16:00,19:00-21:00", encoded)
        assertEquals(spans, SpansCodec.decode(encoded))
    }

    @Test
    fun `跨子夜的安静段往返`() {
        val spans = listOf(TimeSpan(LocalTime.of(23, 0), LocalTime.of(6, 0)))
        assertEquals("23:00-06:00", SpansCodec.encode(spans))
        assertEquals(spans, SpansCodec.decode("23:00-06:00"))
    }

    @Test
    fun `带分钟的时段往返`() {
        val spans = listOf(TimeSpan(LocalTime.of(6, 45), LocalTime.of(8, 15)))
        assertEquals(spans, SpansCodec.decode(SpansCodec.encode(spans)))
    }

    @Test
    fun `缺横杠判为损坏`() {
        assertNull(SpansCodec.decode("09:00"))
    }

    @Test
    fun `横杠多于一个判为损坏`() {
        assertNull(SpansCodec.decode("09:00-11:00-13:00"))
    }

    @Test
    fun `时刻不是两位数判为损坏`() {
        assertNull(SpansCodec.decode("6:30-07:00"))
        assertNull(SpansCodec.decode("09:00-7:00"))
    }

    @Test
    fun `完全看不懂的字符串判为损坏`() {
        assertNull(SpansCodec.decode("bogus"))
        assertNull(SpansCodec.decode("09:00-0a:00"))
    }

    @Test
    fun `越界的时刻判为损坏`() {
        assertNull(SpansCodec.decode("25:00-26:00"))
    }

    @Test
    fun `空段判为损坏`() {
        assertNull(SpansCodec.decode("07:00-07:00"))
    }

    @Test
    fun `多段里只要有一段坏掉整行作废`() {
        assertNull(SpansCodec.decode("09:00-11:00,bogus"))
    }

    @Test
    fun `两端空白被容忍`() {
        assertEquals(listOf(span(9, 11)), SpansCodec.decode("  09:00-11:00  "))
    }
}
