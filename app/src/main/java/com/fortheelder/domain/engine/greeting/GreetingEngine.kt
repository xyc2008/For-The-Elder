package com.fortheelder.domain.engine.greeting

import com.fortheelder.core.time.AppClock
import java.time.LocalDate
import java.time.LocalTime

/** 一天中的时段。问候语与后续的作息判断共用这一套划分。 */
enum class DayPart(val label: String) {
    EARLY_MORNING("清晨"),
    MORNING("早上"),
    FORENOON("上午"),
    NOON("中午"),
    AFTERNOON("下午"),
    EVENING("晚上"),
    LATE_NIGHT("深夜"),
    ;

    companion object {
        /**
         * 时段划分。夜里 23:00 之后到 05:00 算深夜——这个时段不主动打扰，
         * 见 docs/02 的「安静时段」约定。
         */
        fun of(time: LocalTime): DayPart = when (time.hour) {
            in 5..6 -> EARLY_MORNING
            in 7..8 -> MORNING
            in 9..11 -> FORENOON
            in 12..13 -> NOON
            in 14..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> LATE_NIGHT
        }
    }
}

/** 一条问候：一句主话 + 一句补充。补充话只讲事实，不做催促。 */
data class Greeting(
    val dayPart: DayPart,
    val address: String?,
    val headline: String,
    val note: String,
    /** 从首次启动算起，今天是第几天。至少为 1。 */
    val dayCount: Long,
)

/**
 * 问候语生成。
 *
 * 设计约束（来自需求文档的「零推销、零负担」）：
 * - 不说「您好久没来了」「今天还没打卡」——任何一句都在暗示亏欠；
 * - 深夜只提示休息，不引导继续使用；
 * - 「认识多少天」只陈述事实，不当作积分，也没有「连续」的概念。
 */
class GreetingEngine(private val clock: AppClock) {

    fun greet(address: String?, firstLaunchEpochDay: Long?): Greeting {
        val now = clock.now()
        val dayPart = DayPart.of(now.toLocalTime())
        val dayCount = dayCount(firstLaunchEpochDay, now.toLocalDate())
        val who = address?.trim()?.takeIf { it.isNotEmpty() }

        val headline = buildString {
            if (who != null) {
                append(who)
                append("，")
            }
            append(headlineFor(dayPart))
        }

        return Greeting(
            dayPart = dayPart,
            address = who,
            headline = headline,
            note = noteFor(dayPart, dayCount),
            dayCount = dayCount,
        )
    }

    private fun headlineFor(dayPart: DayPart): String = when (dayPart) {
        DayPart.EARLY_MORNING -> "早上好"
        DayPart.MORNING -> "早上好"
        DayPart.FORENOON -> "上午好"
        DayPart.NOON -> "中午好"
        DayPart.AFTERNOON -> "下午好"
        DayPart.EVENING -> "晚上好"
        DayPart.LATE_NIGHT -> "夜深了"
    }

    private fun noteFor(dayPart: DayPart, dayCount: Long): String = when {
        dayPart == DayPart.LATE_NIGHT -> "不着急，什么时候想说都行。"
        dayCount <= 1L -> "第一次见面，慢慢来。"
        else -> "已经认识 $dayCount 天了。"
    }

    private fun dayCount(firstLaunchEpochDay: Long?, today: LocalDate): Long {
        if (firstLaunchEpochDay == null) return 1L
        val diff = today.toEpochDay() - firstLaunchEpochDay
        // 用户把系统时间往前调、或者换了时区，都可能算出负数。夹到 1，不显示「第 -3 天」。
        return (diff + 1).coerceAtLeast(1L)
    }
}
