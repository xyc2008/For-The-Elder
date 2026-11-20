package com.fortheelder.domain.engine.rhythm

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/**
 * 推断参数。全部给默认值，只有在测试里需要构造极端场景时才改。
 *
 * 这些阈值不是随便定的，每一个都对应一个「宁可不说，也不要猜错」的取舍，见各自注释。
 */
data class RhythmConfig(
    /**
     * 作息日的分界点。凌晨 4 点。
     *
     * 用自然日会把「晚上 11 点半看一眼手机 + 次日 0 点半再看了一眼」拆成两天，
     * 第二天就变成「0:30 就起床了」，起床时刻全废。往后挪 4 小时就归成一天了。
     */
    val cutoverHour: Int = 4,
    /** 至少要有几天数据才敢说「这是您的作息」。3 天是刚能看出重复性的下限。 */
    val minDays: Int = 3,
    /**
     * 一天里最少要有几条「人在场」信号才算有效天。
     *
     * 20 条是「这天几乎没碰手机」和「这天正常用了手机」之间的线：
     * 手机上只要亮一次屏就是一条，一天 20 条已经算是很少用了。
     * 定得太低，稀疏得像噪声的一天会被当成正常作息，把整幅画像拉歪。
     */
    val minPresencePerDay: Int = 20,
    /** 起床到睡前至少要横跨 6 小时，否则这批信号不像一整天的活动。 */
    val minAwakeMinutes: Int = 6 * 60,
    /** 统计时间桶的粒度。30 分钟是「一段完整的空窗」和「随手看一眼」之间的分界。 */
    val bucketMinutes: Int = 30,
    /**
     * 低于当天平均活跃密度这个比例的桶，算「没在忙」。
     * 0.35 意味着只有明显低于自己平均水平的时间才被当作空窗，不追求绝对安静。
     */
    val quietDensityRatio: Double = 0.35,
    /**
     * 密度的绝对下限：平均每桶每天不足半条信号才算「没动过手机」。
     *
     * 这一条管的是「低但非零」的桶：如果一天的平均密度本来就很小
     * （[quietDensityRatio] 乘出来的阈值会低于 0.5），那么随手有过一两次亮屏的时段
     * 也会被算成空窗。加上这条下限，空窗只落在近乎完全没动手机的桶上。
     */
    val minQuietSignalsPerDay: Double = 0.5,
    /** 空窗候选至少要有 1 小时长。半小时的间隙一天里有的是，不值得打扰。 */
    val minIdleMinutes: Int = 60,
    /**
     * 起床后、睡前各留白这么久，落在这两段里的安静段一律不判为空窗。
     *
     * 刚醒那会儿本来就在洗漱做早饭，睡前那会儿本来就在收尾，
     * 把它们当成「无事可做」是误判——而误判空窗会导致一次不该来的打扰。
     */
    val edgeGuardMinutes: Int = 45,
    /** 插电时刻的允许离散度。超过这个数就不认为「定点插电」。 */
    val chargingSpreadMinutes: Int = 90,
    /**
     * 插电时刻必须落在睡前多久之内才算入睡锚点。
     * 白天在书桌上一直插着电的人，最后一次插电在下午——那不能叫入睡锚点。
     */
    val chargingLookbackMinutes: Int = 240,
) {
    init {
        require(MINUTES_PER_DAY % bucketMinutes == 0) {
            "bucketMinutes 必须整除一天：$bucketMinutes"
        }
        require(minDays >= 1) { "minDays 至少为 1：$minDays" }
        require(cutoverHour in 0..23) { "cutoverHour 必须是小时：$cutoverHour" }
    }

    /** 一天被切成多少个桶。 */
    val bucketCount: Int get() = MINUTES_PER_DAY / bucketMinutes

    internal fun bucketOf(offsetMinutes: Int): Int = offsetMinutes / bucketMinutes
}

/**
 * 无感作息推断。
 *
 * 纯函数：同一份输入永远得到同一份输出，没有隐藏状态，也不看「现在几点」
 * （docs/01 的 AC-04.2）。整类不引用任何 Android API，所以能在 JVM 单测里
 * 用合成数据把边界情况全跑一遍（AC-04.1）。
 *
 * 做法是老老实实的统计，没有任何「智能」成分，因为这里能回答的最好的问题就是
 * 「您平时几点开始用手机」——一旦试图预测用户此刻在干什么，就已经越界了。
 */
class RhythmInference(private val config: RhythmConfig = RhythmConfig()) {

    fun infer(signals: List<RhythmSignal>): RhythmResult {
        val byDay = signals.groupBy { rhythmDayOf(it.at) }
        val days = byDay.values.mapNotNull { toDayRecord(it) }
        if (days.size < config.minDays) {
            return RhythmResult.Gathering(validDays = days.size, requiredDays = config.minDays)
        }

        val wakeOffsets = days.map { it.firstPresenceOffset }.sorted()
        val sleepOffsets = days.map { it.lastPresenceOffset }.sorted()
        val wakeOffset = medianOf(wakeOffsets)
        val sleepOffset = medianOf(sleepOffsets)
        if (sleepOffset - wakeOffset < config.minAwakeMinutes) return RhythmResult.Inconsistent

        val density = densityPerDayBucket(days)
        val threshold = maxOf(
            meanDensity(density, wakeOffset, sleepOffset) * config.quietDensityRatio,
            config.minQuietSignalsPerDay,
        )
        val spans = splitByDensity(density, threshold, wakeOffset, sleepOffset)

        return RhythmResult.Ready(
            DailyRhythm(
                wakeAt = toLocalTime(wakeOffset),
                sleepAt = toLocalTime(sleepOffset),
                activeSpans = spans.active,
                idleSpans = spans.idle,
                nightChargingAt = nightChargingAnchor(days, sleepOffset),
                sampleDays = days.size,
                confidence = confidenceOf(days.size, wakeOffsets, sleepOffsets),
            ),
        )
    }

    /** 一天的小结。只留推断真正要用的几个量。 */
    private data class DayRecord(
        val presenceOffsets: List<Int>,
        val lastChargingStartOffset: Int?,
    ) {
        val firstPresenceOffset: Int get() = presenceOffsets.first()
        val lastPresenceOffset: Int get() = presenceOffsets.last()
    }

    private fun toDayRecord(daySignals: List<RhythmSignal>): DayRecord? {
        val presence = daySignals
            .filter { it.kind.isPresence }
            .map { offsetOf(it.at) }
            .sorted()
        if (presence.size < config.minPresencePerDay) return null

        return DayRecord(
            presenceOffsets = presence,
            lastChargingStartOffset = daySignals
                .filter { it.kind == RhythmKind.CHARGING_START }
                .maxOfOrNull { offsetOf(it.at) },
        )
    }

    /**
     * 每个时间桶里平均有多少条「人在场」信号。
     *
     * 除的是有效天数，不是总跨度天数：把没采到数据的日子也算进去，
     * 只会让所有桶的密度一起变低，然后所有桶都变成「空窗」。
     */
    private fun densityPerDayBucket(days: List<DayRecord>): DoubleArray {
        val totals = IntArray(config.bucketCount)
        for (day in days) {
            for (offset in day.presenceOffsets) {
                totals[config.bucketOf(offset)]++
            }
        }
        return DoubleArray(config.bucketCount) { totals[it].toDouble() / days.size }
    }

    /**
     * 扫描范围就是「第一次碰手机」到「最后一次碰手机」这两个桶之间，
     * 逐桶判断忙不忙，再把连续的忙/闲并成时段。
     *
     * 空窗候选要同时满足三条：够长、离起床够远、离睡前够远。任何一条不满足就丢掉，
     * 不进 [DailyRhythm.idleSpans]——少报一个空窗的成本，远低于多打扰一次。
     */
    private fun splitByDensity(
        density: DoubleArray,
        threshold: Double,
        wakeOffset: Int,
        sleepOffset: Int,
    ): Spans {
        val from = config.bucketOf(wakeOffset).coerceAtLeast(0)
        val to = config.bucketOf(sleepOffset).coerceAtMost(config.bucketCount - 1)

        val active = mutableListOf<TimeSpan>()
        val idle = mutableListOf<TimeSpan>()
        var bucket = from
        while (bucket <= to) {
            val isQuiet = density[bucket] < threshold
            val runStart = bucket
            while (bucket <= to && (density[bucket] < threshold) == isQuiet) bucket++
            val startOffset = runStart * config.bucketMinutes
            val endOffset = bucket * config.bucketMinutes
            val span = TimeSpan(toLocalTime(startOffset), toLocalTime(endOffset))
            when {
                !isQuiet -> active += span
                endOffset - startOffset < config.minIdleMinutes -> Unit
                startOffset - wakeOffset < config.edgeGuardMinutes -> Unit
                sleepOffset - endOffset < config.edgeGuardMinutes -> Unit
                else -> idle += span
            }
        }
        return Spans(active, idle)
    }

    private data class Spans(val active: List<TimeSpan>, val idle: List<TimeSpan>)

    /** 取不出规律就返回 null，而不是填一个平均值——那是编的。 */
    private fun nightChargingAnchor(days: List<DayRecord>, sleepOffset: Int): LocalTime? {
        val earliest = sleepOffset - config.chargingLookbackMinutes
        val candidates = days
            .map { it.lastChargingStartOffset ?: return null }
            .filter { it >= earliest }
        if (candidates.size < days.size) return null

        val sorted = candidates.sorted()
        if (medianAbsoluteDeviation(sorted) > config.chargingSpreadMinutes) return null
        return toLocalTime(medianOf(sorted))
    }

    /**
     * 置信度 = 天数覆盖率与作息稳定度各占一半。
     *
     * 7 天是覆盖率拿满分的线；2 小时是稳定度归零的线——起床时刻天天差两个小时以上，
     * 那就不叫作息了。
     */
    private fun confidenceOf(sampleDays: Int, wakeOffsets: List<Int>, sleepOffsets: List<Int>): Float {
        val coverage = (sampleDays / 7.0).coerceAtMost(1.0)
        val instability = (
            maxOf(
                medianAbsoluteDeviation(wakeOffsets),
                medianAbsoluteDeviation(sleepOffsets),
            ) / 120.0
            ).coerceAtMost(1.0)
        return ((coverage + (1.0 - instability)) / 2.0).toFloat()
    }

    /** 区间内的平均桶密度，按偏移量给的闭区间。 */
    private fun meanDensity(density: DoubleArray, fromOffset: Int, toOffset: Int): Double {
        val from = config.bucketOf(fromOffset).coerceIn(0, density.lastIndex)
        val to = config.bucketOf(toOffset).coerceIn(from, density.lastIndex)
        var sum = 0.0
        for (i in from..to) sum += density[i]
        return sum / (to - from + 1)
    }

    /** 凌晨 [RhythmConfig.cutoverHour] 点为界的作息日。 */
    private fun rhythmDayOf(at: LocalDateTime): LocalDate =
        at.minusHours(config.cutoverHour.toLong()).toLocalDate()

    /** 距离该作息日起点的分钟数，范围 0..1439。 */
    private fun offsetOf(at: LocalDateTime): Int {
        val dayStart = LocalDateTime.of(rhythmDayOf(at), LocalTime.of(config.cutoverHour, 0))
        return Duration.between(dayStart, at).toMinutes().toInt()
    }

    private fun toLocalTime(offsetMinutes: Int): LocalTime =
        LocalTime.ofSecondOfDay(((offsetMinutes + config.cutoverHour * 60) % MINUTES_PER_DAY) * 60L)
}

/**
 * 取中位数。
 *
 * 偶数个时取中间那一个而不是两数平均：这两个数是「几点几分」，
 * 两个靠近子夜的时刻求平均会算出中午。
 */
internal fun medianOf(sorted: List<Int>): Int = sorted[(sorted.size - 1) / 2]

/**
 * 中位绝对偏差。用中位数而不用标准差，是为了让偶尔熬一次夜不毁掉整周的判断。
 *
 * 代价是它需要超过一半的日子偏离中位数才会给出非零值——这是有意的：
 * 「大部分日子都差不多」本来就该判成稳定。
 */
internal fun medianAbsoluteDeviation(sorted: List<Int>): Int {
    val median = medianOf(sorted)
    return medianOf(sorted.map { abs(it - median) }.sorted())
}
