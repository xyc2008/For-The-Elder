package com.fortheelder.domain.engine.rhythm

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作息推断的单元测试。
 *
 * 输入全是合成数据，没有一处读「现在几点」，所以结果确定，不会半夜跑出一片红。
 * 造数据的方式是「每个时间桶放 N 条信号」，信号从桶边界开始往后排：`perBucket = 1`
 * 时信号的时刻正好落在桶边界上，推断出的起床/睡前时刻就是整点或半点。
 */
class RhythmInferenceTest {

    private val config = RhythmConfig()
    private val inference = RhythmInference(config)

    /** 第一个作息日。固定下来才能对时刻做断言。 */
    private val firstDay: LocalDate = LocalDate.of(2026, 3, 9)

    private fun dayOf(index: Int): LocalDate = firstDay.plusDays(index.toLong())

    /** 一个作息日的起点，与 [RhythmConfig.cutoverHour] 一致。 */
    private val dayStart: LocalTime = LocalTime.of(config.cutoverHour, 0)

    /**
     * 把「桶 → 信号条数」的表变成一串信号。
     *
     * @param kind 在场信号的种类，默认亮屏。不设锁屏的用户只会给出 [RhythmKind.USER_PRESENT]。
     */
    private fun day(
        date: LocalDate,
        counts: List<Int>,
        kind: RhythmKind = RhythmKind.SCREEN_ON,
    ): List<RhythmSignal> {
        val start = LocalDateTime.of(date, dayStart)
        return counts.flatMapIndexed { bucket, count ->
            List(count) { i ->
                RhythmSignal(start.plusMinutes(bucket.toLong() * config.bucketMinutes + i), kind)
            }
        }
    }

    /** 指定桶范围各放 [perBucket] 条信号，其余桶为 0。 */
    private fun counts(vararg ranges: IntRange, perBucket: Int = 1): MutableList<Int> =
        MutableList(config.bucketCount) { 0 }.also { table ->
            ranges.forEach { range -> range.forEach { table[it] = perBucket } }
        }

    /** 06:30 开始碰手机、22:30 之后不再碰手机的一整天。 */
    private fun regularCounts(): MutableList<Int> = counts(5..37)

    private fun days(
        vararg specs: List<Int>,
        kind: RhythmKind = RhythmKind.SCREEN_ON,
    ): List<RhythmSignal> =
        specs.flatMapIndexed { index, spec -> day(dayOf(index), spec, kind) }

    private fun charging(date: LocalDate, at: LocalTime): RhythmSignal =
        RhythmSignal(LocalDateTime.of(date, at), RhythmKind.CHARGING_START)

    private fun ready(result: RhythmResult): DailyRhythm = when (result) {
        is RhythmResult.Ready -> result.rhythm
        else -> throw AssertionError("期望 RhythmResult.Ready，实际是 $result")
    }

    // ---------- 数据不足时不许发表意见 ----------

    @Test
    fun `没有信号时还在采集`() {
        assertEquals(
            RhythmResult.Gathering(validDays = 0, requiredDays = 3),
            inference.infer(emptyList()),
        )
    }

    @Test
    fun `两天数据还不够`() {
        val signals = days(regularCounts(), regularCounts())

        assertEquals(RhythmResult.Gathering(2, 3), inference.infer(signals))
    }

    @Test
    fun `信号太少的一天不算有效天`() {
        // 桶 5 只放 19 条，低于每天最少 20 条的门槛。
        val sparse = MutableList(config.bucketCount) { 0 }.also { it[5] = 19 }

        assertEquals(RhythmResult.Gathering(0, 3), inference.infer(days(sparse, sparse, sparse)))
    }

    @Test
    fun `只有插电信号不构成在场`() {
        // 插电信号单独出现时不代表人在用手机，三天都是这样仍然一天都没采到。
        val signals = (0..2).flatMap { index ->
            listOf(
                charging(dayOf(index), LocalTime.of(8, 0)),
                charging(dayOf(index), LocalTime.of(22, 0)),
            )
        }

        assertEquals(RhythmResult.Gathering(0, 3), inference.infer(signals))
    }

    @Test
    fun `只有几十分钟的活动算不上一天`() {
        // 20 条信号挤在 20 分钟内，凑不出「起床到睡前」这一段。
        val burst = MutableList(config.bucketCount) { 0 }.also { it[5] = 20 }

        assertEquals(RhythmResult.Inconsistent, inference.infer(days(burst, burst, burst)))
    }

    // ---------- 规律作息 ----------

    @Test
    fun `三天规律作息给出起床与睡前时刻`() {
        val rhythm = ready(inference.infer(days(regularCounts(), regularCounts(), regularCounts())))

        assertEquals(LocalTime.of(6, 30), rhythm.wakeAt)
        assertEquals(LocalTime.of(22, 30), rhythm.sleepAt)
        assertEquals(3, rhythm.sampleDays)
        assertEquals(TimeSpan(LocalTime.of(22, 30), LocalTime.of(6, 30)), rhythm.quietSpan)
        assertEquals(16 * 60, rhythm.awakeMinutes)
    }

    @Test
    fun `同一份输入两次结果相同`() {
        val signals = days(regularCounts(), regularCounts(), regularCounts())

        assertEquals(inference.infer(signals), inference.infer(signals))
    }

    @Test
    fun `信号先后顺序不影响结果`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())

        assertEquals(inference.infer(days(*specs.toTypedArray())), inference.infer(days(*specs.toTypedArray()).reversed()))
    }

    @Test
    fun `不设锁屏的用户用另一种在场信号也推得出来`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())

        val rhythm = ready(inference.infer(days(*specs.toTypedArray(), kind = RhythmKind.USER_PRESENT)))

        assertEquals(LocalTime.of(6, 30), rhythm.wakeAt)
        assertEquals(LocalTime.of(22, 30), rhythm.sleepAt)
    }

    @Test
    fun `跨子夜的深夜活动归到同一个作息日`() {
        // 23:30 和次日 0:30 都还在用手机。按自然日分组会把这一夜拆成两天，
        // 第二天就变成「0:30 起床」。按凌晨 4 点分界才归成一个作息日。
        val counts = regularCounts().also {
            it[39] = 1
            it[41] = 1
        }

        val rhythm = ready(inference.infer(days(counts, counts, counts)))

        assertEquals(3, rhythm.sampleDays)
        assertEquals(LocalTime.of(6, 30), rhythm.wakeAt)
        assertEquals(LocalTime.of(0, 30), rhythm.sleepAt)
    }

    @Test
    fun `偶尔熬一次夜不影响起床时刻`() {
        val signals = days(
            counts(0..37),
            regularCounts(),
            regularCounts(),
            regularCounts(),
            regularCounts(),
        )

        val rhythm = ready(inference.infer(signals))

        assertEquals(5, rhythm.sampleDays)
        assertEquals(LocalTime.of(6, 30), rhythm.wakeAt)
    }

    // ---------- 空窗候选 ----------

    @Test
    fun `一天里一直有人动手机时没有空窗候选`() {
        val rhythm = ready(inference.infer(days(regularCounts(), regularCounts(), regularCounts())))

        assertTrue("不该凭空造出空窗：${rhythm.idleSpans}", rhythm.idleSpans.isEmpty())
        assertEquals(
            listOf(TimeSpan(LocalTime.of(6, 30), LocalTime.of(23, 0))),
            rhythm.activeSpans,
        )
    }

    @Test
    fun `下午长时间没人动手机会被认成空窗候选`() {
        val afternoonQuiet = counts(5..17, 25..37)

        val rhythm = ready(inference.infer(days(afternoonQuiet, afternoonQuiet, afternoonQuiet)))

        assertEquals(
            listOf(TimeSpan(LocalTime.of(13, 0), LocalTime.of(16, 30))),
            rhythm.idleSpans,
        )
        assertEquals(
            listOf(
                TimeSpan(LocalTime.of(6, 30), LocalTime.of(13, 0)),
                TimeSpan(LocalTime.of(16, 30), LocalTime.of(23, 0)),
            ),
            rhythm.activeSpans,
        )
    }

    @Test
    fun `刚起床后的安静段不判为空窗`() {
        // 07:00–08:00 没人碰手机，但它紧接着起床时刻，那时候本来就在洗漱做饭。
        val washingUp = counts(5..5, 8..37)

        val rhythm = ready(inference.infer(days(washingUp, washingUp, washingUp)))

        assertTrue("起床后的安静段不该算空窗：${rhythm.idleSpans}", rhythm.idleSpans.isEmpty())
    }

    @Test
    fun `稍晚一点的安静段仍然会被认出来`() {
        // 与上一个用例同样长的安静段，只是往后挪了半小时，就该报出来。
        val laterQuiet = counts(5..6, 9..37)

        val rhythm = ready(inference.infer(days(laterQuiet, laterQuiet, laterQuiet)))

        assertEquals(
            listOf(TimeSpan(LocalTime.of(7, 30), LocalTime.of(8, 30))),
            rhythm.idleSpans,
        )
    }

    // ---------- 置信度 ----------

    @Test
    fun `天数越多置信度越高`() {
        val threeDays = ready(inference.infer(days(regularCounts(), regularCounts(), regularCounts())))
        val oneWeek = ready(
            inference.infer(days(*List(7) { regularCounts() }.toTypedArray())),
        )

        assertEquals(0.714f, threeDays.confidence, 0.001f)
        assertEquals(1.0f, oneWeek.confidence, 0.001f)
    }

    @Test
    fun `起床时刻散乱时置信度下降`() {
        // 桶数在变（起床越晚，一天剩下的桶越少），所以每个桶放两条，
        // 免得晚起的那几天因为信号条数不够被当成无效天，那样测的就不是「散乱」了。
        val scattered: List<List<Int>> = (0..6).map { index -> counts((5 + 3 * index)..37, perBucket = 2) }

        val steady = ready(inference.infer(days(*List(7) { regularCounts() }.toTypedArray())))
        val unstable = ready(inference.infer(days(*scattered.toTypedArray())))

        assertEquals(7, unstable.sampleDays)
        assertEquals(0.5f, unstable.confidence, 0.001f)
        assertTrue(
            "散乱作息的置信度应当低于规律作息：${unstable.confidence} vs ${steady.confidence}",
            unstable.confidence < steady.confidence,
        )
    }

    // ---------- 入睡锚点 ----------

    @Test
    fun `睡前插电规律时给出入睡锚点`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())
        val signals = days(*specs.toTypedArray()) + (0..2).map { charging(dayOf(it), LocalTime.of(22, 0)) }

        val rhythm = ready(inference.infer(signals))

        assertEquals(LocalTime.of(22, 0), rhythm.nightChargingAt)
    }

    @Test
    fun `只有一部分天插电时不给入睡锚点`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())
        val signals = days(*specs.toTypedArray()) + (0..1).map { charging(dayOf(it), LocalTime.of(22, 0)) }

        assertNull(ready(inference.infer(signals)).nightChargingAt)
    }

    @Test
    fun `插电时刻不稳时不给入睡锚点`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())
        val signals = days(*specs.toTypedArray()) + listOf(
            charging(dayOf(0), LocalTime.of(20, 0)),
            charging(dayOf(1), LocalTime.of(22, 0)),
            // 次日 1:00 仍属于第 3 个作息日（凌晨 4 点分界）。
            charging(dayOf(3), LocalTime.of(1, 0)),
        )

        assertNull(ready(inference.infer(signals)).nightChargingAt)
    }

    @Test
    fun `白天一直插着电不算入睡锚点`() {
        val specs = listOf(regularCounts(), regularCounts(), regularCounts())
        val signals = days(*specs.toTypedArray()) + (0..2).map { charging(dayOf(it), LocalTime.of(15, 0)) }

        assertNull(ready(inference.infer(signals)).nightChargingAt)
    }

    // ---------- 时段本身 ----------

    @Test
    fun `时段的包含判断跨子夜也成立`() {
        val night = TimeSpan(LocalTime.of(23, 0), LocalTime.of(6, 0))

        assertEquals(420, night.minutes)
        assertTrue(night.contains(LocalTime.of(23, 30)))
        assertTrue(night.contains(LocalTime.of(5, 0)))
        assertFalse(night.contains(LocalTime.of(12, 0)))
        assertFalse("结束时刻是开区间：$night", night.contains(LocalTime.of(6, 0)))
        assertEquals("23:00-06:00", night.toString())
    }

    // ---------- 参数校验 ----------

    @Test
    fun `不合法的推断参数在构造时就报错`() {
        assertThrows(IllegalArgumentException::class.java) { RhythmConfig(bucketMinutes = 7) }
        assertThrows(IllegalArgumentException::class.java) { RhythmConfig(minDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { RhythmConfig(cutoverHour = 24) }
    }
}
