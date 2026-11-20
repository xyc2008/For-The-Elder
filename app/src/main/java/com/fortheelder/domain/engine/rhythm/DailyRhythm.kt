package com.fortheelder.domain.engine.rhythm

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 两个时刻之间的分钟数，跨子夜按加一天算。返回 0..1439。 */
internal fun spanMinutes(start: LocalTime, end: LocalTime): Int =
    ((end.toSecondOfDay() - start.toSecondOfDay()) / 60 + MINUTES_PER_DAY) % MINUTES_PER_DAY

internal const val MINUTES_PER_DAY = 24 * 60

/**
 * 作息日时间轴。
 *
 * 「作息日」以凌晨 `cutoverHour` 点为界（ADR-006）。下面三个函数是它与自然时间之间
 * 唯一的换算入口：引擎、采集器、数据库三边都调它们，而不是各自再算一遍——
 * 这三处只要有一处偏移差了一小时，整幅画像就会错一小时，而且很难看出来。
 */

/** 凌晨 [cutoverHour] 点为界的作息日。 */
fun rhythmDayOf(at: LocalDateTime, cutoverHour: Int): LocalDate =
    at.minusHours(cutoverHour.toLong()).toLocalDate()

/** 本地时刻在该作息日内的偏移分钟数，范围 0..1439。 */
fun LocalTime.toRhythmOffset(cutoverHour: Int): Int =
    ((toSecondOfDay() / 60 - cutoverHour * 60) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY

/** [toRhythmOffset] 的逆运算。 */
fun rhythmOffsetToLocalTime(offsetMinutes: Int, cutoverHour: Int): LocalTime =
    LocalTime.ofSecondOfDay(
        (((offsetMinutes + cutoverHour * 60) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY) * 60L
    )

/**
 * 一个时间段 `[start, end)`。
 *
 * 允许跨子夜（`start > end`，例如 23:00–06:00），因为「安静时段」天然跨子夜。
 * 空段（`start == end`）判为不含任何时刻——正常流程不会产出空段，
 * 最短的段也有 `bucketMinutes` 那么长。
 */
data class TimeSpan(val start: LocalTime, val end: LocalTime) {

    val minutes: Int get() = spanMinutes(start, end)

    fun contains(time: LocalTime): Boolean {
        val s = start.toSecondOfDay()
        val e = end.toSecondOfDay()
        val t = time.toSecondOfDay()
        return when {
            s < e -> t in s until e
            s > e -> t >= s || t < e
            else -> false
        }
    }

    override fun toString(): String = "${start.format(HHMM)}-${end.format(HHMM)}"

    private companion object {
        val HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * 作息画像。
 *
 * 刻意只给「典型的」时刻，不给每天精确的时间表——后者既不准也没用，
 * 而且会诱使后面的功能去精确预测用户在干什么。
 *
 * @param wakeAt 通常在这个时刻开始有人碰手机（起床）
 * @param sleepAt 通常在这个时刻之后不再碰手机（准备睡）
 * @param activeSpans 一天中明显在用手机的时段（[wakeAt] 后 45 分钟到 [sleepAt] 前 45 分钟之内）
 * @param idleSpans 一天中醒着却长时间不碰手机的时段，也就是需求里的「可疑空窗期」
 * @param nightChargingAt 稳定的入睡锚点（睡前插电时刻）。看不出规律时为 null
 * @param sampleDays 参与推断的有效天数
 * @param confidence 0..1，天数覆盖率与起床/睡前时刻的稳定度各占一半
 */
data class DailyRhythm(
    val wakeAt: LocalTime,
    val sleepAt: LocalTime,
    val activeSpans: List<TimeSpan>,
    val idleSpans: List<TimeSpan>,
    val nightChargingAt: LocalTime?,
    val sampleDays: Int,
    val confidence: Float,
) {

    /** 醒着的那一段。 */
    val awakeSpan: TimeSpan get() = TimeSpan(wakeAt, sleepAt)

    /** 安静时段：睡下到起床。深处夜里不主动打扰，见 docs/02。 */
    val quietSpan: TimeSpan get() = TimeSpan(sleepAt, wakeAt)

    val awakeMinutes: Int get() = spanMinutes(wakeAt, sleepAt)
}

/**
 * 推断结果。
 *
 * 不返回 `DailyRhythm?`，是因为「数据不够」和「数据自相矛盾」对界面是两句不同的话：
 * 前者该说「还在了解您的作息」，后者不该编，直接不多说。
 */
sealed interface RhythmResult {

    /** 作息画像可用。 */
    data class Ready(val rhythm: DailyRhythm) : RhythmResult

    /** 采到的有效天数还不够。界面据此显示进度，不猜。 */
    data class Gathering(val validDays: Int, val requiredDays: Int) : RhythmResult

    /** 有数据，但凑不出一个说得通的作息（例如信号全挤在几十分钟里）。 */
    data object Inconsistent : RhythmResult
}
