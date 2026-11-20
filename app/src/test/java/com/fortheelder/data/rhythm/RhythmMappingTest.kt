package com.fortheelder.data.rhythm

import com.fortheelder.data.db.DailyRhythmEntity
import com.fortheelder.data.db.RhythmSignalEntity
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.TimeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 领域对象与数据库行之间的换算（ADR-008）。
 *
 * 这一层是「时区」唯一的落脚点，所以两条最关键的断言是：
 * 1. 落库存的是绝对时刻 + 当时的偏移；
 * 2. 读出来用**存下来的**偏移还原本地时刻，而不是用现在的系统时区。
 * 第 2 条要是错了，用户出趟差回来会看到自己的作息凭空平移几小时。
 */
class RhythmMappingTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val cutover = RhythmConfig.DEFAULT_CUTOVER_HOUR

    @Test
    fun `信号落库后能原样读回`() {
        val signal = CapturedSignal(LocalDateTime.of(2026, 3, 10, 7, 30), RhythmKind.SCREEN_ON)
        val entity = RhythmMapping.toEntity(signal, shanghai)

        assertEquals("SCREEN_ON", entity.kind)
        assertEquals(8 * 3600, entity.offsetSeconds)
        assertEquals(
            LocalDateTime.of(2026, 3, 10, 7, 30),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.atEpochMilli), shanghai),
        )
        assertEquals(signal.at to signal.kind, RhythmMapping.toDomain(entity)?.let { it.at to it.kind })
    }

    @Test
    fun `五种信号都能往返`() {
        for (kind in RhythmKind.entries) {
            val entity = RhythmMapping.toEntity(
                CapturedSignal(LocalDateTime.of(2026, 5, 1, 12, 0), kind),
                shanghai,
            )
            assertEquals(kind, RhythmMapping.toDomain(entity)?.kind)
        }
    }

    @Test
    fun `认不出的种类读成空而不是崩掉`() {
        val entity = RhythmSignalEntity(kind = "SOMETHING_NEW", atEpochMilli = 0L, offsetSeconds = 0)
        assertNull(RhythmMapping.toDomain(entity))
    }

    @Test
    fun `还原本地时刻用的是存下来的偏移而不是当前时区`() {
        // 同一个绝对时刻，在 +08:00 采集时看到的是次日 07:30。
        val entity = RhythmSignalEntity(
            kind = "SCREEN_ON",
            atEpochMilli = Instant.parse("2026-03-10T23:30:00Z").toEpochMilli(),
            offsetSeconds = 8 * 3600,
        )
        assertEquals(LocalDateTime.of(2026, 3, 11, 7, 30), RhythmMapping.toDomain(entity)?.at)
    }

    @Test
    fun `画像带入睡锚点时能原样读回`() {
        val rhythm = rhythm(sleepAt = LocalTime.of(22, 30), nightChargingAt = LocalTime.of(23, 0))
        val entity = RhythmMapping.toEntity(1_000L, rhythm, 999L, cutover)

        assertEquals(1_000L, entity.dayEpochDay)
        assertEquals(999L, entity.computedAtEpochMilli)
        assertEquals(rhythm, RhythmMapping.toDomain(entity, cutover))
    }

    @Test
    fun `画像没有入睡锚点时读回仍是空`() {
        val rhythm = rhythm(sleepAt = LocalTime.of(22, 30), nightChargingAt = null)
        val entity = RhythmMapping.toEntity(1_000L, rhythm, 999L, cutover)

        assertNull(entity.nightChargingOffsetMinutes)
        assertNull(RhythmMapping.toDomain(entity, cutover)?.nightChargingAt)
    }

    @Test
    fun `睡前跨子夜也能往返`() {
        val rhythm = rhythm(sleepAt = LocalTime.of(0, 40), nightChargingAt = LocalTime.of(1, 10))
        val entity = RhythmMapping.toEntity(7L, rhythm, 1L, cutover)

        assertEquals(rhythm, RhythmMapping.toDomain(entity, cutover))
    }

    @Test
    fun `跨子夜的安静段能往返`() {
        val rhythm = rhythm(
            sleepAt = LocalTime.of(23, 30),
            nightChargingAt = null,
            idleSpans = listOf(TimeSpan(LocalTime.of(23, 30), LocalTime.of(6, 30))),
        )
        val entity = RhythmMapping.toEntity(7L, rhythm, 1L, cutover)

        assertEquals(rhythm, RhythmMapping.toDomain(entity, cutover))
    }

    @Test
    fun `时段串坏掉时整行画像读成空`() {
        val rhythm = rhythm(sleepAt = LocalTime.of(22, 30), nightChargingAt = null)
        val good = RhythmMapping.toEntity(7L, rhythm, 1L, cutover)

        assertNull(RhythmMapping.toDomain(good.copy(activeSpans = "bogus"), cutover))
        assertNull(RhythmMapping.toDomain(good.copy(idleSpans = "09:00"), cutover))
    }

    @Test
    fun `空时段列表写成空串再读回空列表`() {
        val rhythm = rhythm(
            sleepAt = LocalTime.of(22, 30),
            nightChargingAt = null,
            activeSpans = emptyList(),
            idleSpans = emptyList(),
        )
        val entity = RhythmMapping.toEntity(7L, rhythm, 1L, cutover)

        assertEquals("", entity.activeSpans)
        assertEquals(rhythm, RhythmMapping.toDomain(entity, cutover))
    }

    @Test
    fun `换了分界点存进去的偏移量会被按同一个分界点读出来`() {
        // 这条守的是「仓库和引擎必须用同一个 cutoverHour」这个约定：
        // 只要两边一致，换一个分界点也不该改变画像本身。
        val rhythm = rhythm(sleepAt = LocalTime.of(22, 30), nightChargingAt = LocalTime.of(23, 0))
        val entity = RhythmMapping.toEntity(7L, rhythm, 1L, cutoverHour = 3)

        assertEquals(rhythm, RhythmMapping.toDomain(entity, cutoverHour = 3))
        // 按另一个分界点解，时刻会整体错一小时——这正是要避免的事。
        assertNotEquals(rhythm.wakeAt, RhythmMapping.toDomain(entity, cutoverHour = 4)?.wakeAt)
    }

    private fun rhythm(
        sleepAt: LocalTime,
        nightChargingAt: LocalTime?,
        wakeAt: LocalTime = LocalTime.of(6, 30),
        activeSpans: List<TimeSpan> = listOf(TimeSpan(LocalTime.of(7, 0), LocalTime.of(11, 0))),
        idleSpans: List<TimeSpan> = listOf(TimeSpan(LocalTime.of(14, 0), LocalTime.of(16, 0))),
    ) = DailyRhythm(
        wakeAt = wakeAt,
        sleepAt = sleepAt,
        activeSpans = activeSpans,
        idleSpans = idleSpans,
        nightChargingAt = nightChargingAt,
        sampleDays = 5,
        confidence = 0.75f,
    )
}
