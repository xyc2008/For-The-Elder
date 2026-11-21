package com.fortheelder.domain.engine.intervention

import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.domain.engine.rhythm.TimeSpan
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InterventionWindow] 的单元测试。
 *
 * 这一层是纯函数（没有 Android、没有数据库、没有时钟），所以能在这里把边界摊开验完：
 * 安静时段怎么算、退避序列长什么样、时钟被往回调会发生什么。
 *
 * 最关键的一条是 **AC-05.3**：被忽略的次数**只能**让开口间隔变长。它是这段代码唯一
 * 容易写反的地方（一不小心就把退避写成了「越没人理越急着说话」），所以用穷举守着。
 */
class InterventionWindowTest {

    private val config = InterventionConfig()

    private fun ready(wake: String, sleep: String, sampleDays: Int = 7): RhythmResult.Ready =
        RhythmResult.Ready(
            DailyRhythm(
                wakeAt = LocalTime.parse(wake),
                sleepAt = LocalTime.parse(sleep),
                activeSpans = listOf(TimeSpan(LocalTime.parse(wake), LocalTime.parse(sleep))),
                idleSpans = emptyList(),
                nightChargingAt = null,
                sampleDays = sampleDays,
                confidence = 0.8f,
            ),
        )

    private val gathering = RhythmResult.Gathering(validDays = 2, requiredDays = 3)
    private val inconsistent = RhythmResult.Inconsistent

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    private fun decide(
        result: RhythmResult,
        now: LocalDateTime,
        throttle: ThrottleState = ThrottleState(),
    ): InterventionVerdict = InterventionWindow.decide(result, now, throttle, config)

    // ---------- 安静时段 ----------

    @Test
    fun `没有画像时深夜不说话 并说清是按普通作息`() {
        val verdict = decide(gathering, at("2026-03-10T23:00:00"))

        val quiet = verdict as InterventionVerdict.StayQuiet
        assertEquals(QuietReason.SLEEP_HOURS, quiet.reason)
        assertEquals(RhythmBasis.ASSUMED_NOT_ENOUGH_DATA, quiet.basis)
    }

    @Test
    fun `没有画像时起床之前不说话`() {
        val verdict = decide(gathering, at("2026-03-10T06:50:00"))

        assertEquals(QuietReason.SLEEP_HOURS, (verdict as InterventionVerdict.StayQuiet).reason)
    }

    @Test
    fun `有画像时按画像的安静时段 而不是按普通作息`() {
        // 画像说 23:30 才睡；22:40 还在醒着，应当说话。
        val learned = ready(wake = "08:00", sleep = "23:30")

        val spoken = decide(learned, at("2026-03-10T22:40:00"))
        assertEquals(RhythmBasis.LEARNED, (spoken as InterventionVerdict.Speak).basis)

        // 同一时刻，若没有画像，就会退到 22:30–07:00 的假定安静时段，判定相反。
        val assumed = decide(inconsistent, at("2026-03-10T22:40:00"))
        assertEquals(QuietReason.SLEEP_HOURS, (assumed as InterventionVerdict.StayQuiet).reason)
        assertEquals(RhythmBasis.ASSUMED_INCONSISTENT, assumed.basis)
    }

    @Test
    fun `画像的入睡时刻一到就安静`() {
        val learned = ready(wake = "06:30", sleep = "22:00")

        val verdict = decide(learned, at("2026-03-10T22:00:00"))

        val quiet = verdict as InterventionVerdict.StayQuiet
        assertEquals(QuietReason.SLEEP_HOURS, quiet.reason)
        // 安静时段的终点是起床时刻，也就是下一次最早可能开口的时刻。
        assertEquals(at("2026-03-11T06:30:00"), quiet.earliestNextAt)
    }

    @Test
    fun `安静时段的下一站是起床时刻`() {
        // 已经过了午夜，起床还是同一天的早上。
        val beforeDawn = decide(gathering, at("2026-03-10T06:00:00"))
        assertEquals(at("2026-03-10T07:00:00"), (beforeDawn as InterventionVerdict.StayQuiet).earliestNextAt)

        // 刚入睡，起床是次日早上。
        val afterDark = decide(gathering, at("2026-03-10T23:00:00"))
        assertEquals(at("2026-03-11T07:00:00"), (afterDark as InterventionVerdict.StayQuiet).earliestNextAt)
    }

    // ---------- 间隔节流 ----------

    @Test
    fun `刚开过口就往后放`() {
        val throttle = ThrottleState(lastSpokenAt = at("2026-03-10T10:00:00"))

        val verdict = decide(gathering, at("2026-03-10T10:10:00"), throttle)

        val quiet = verdict as InterventionVerdict.StayQuiet
        assertEquals(QuietReason.TOO_SOON, quiet.reason)
        assertEquals(at("2026-03-10T10:30:00"), quiet.earliestNextAt)
    }

    @Test
    fun `间隔一到就能说话`() {
        val throttle = ThrottleState(lastSpokenAt = at("2026-03-10T10:00:00"))

        val verdict = decide(gathering, at("2026-03-10T10:30:00"), throttle)

        assertTrue(verdict is InterventionVerdict.Speak)
    }

    @Test
    fun `被忽略一次 间隔就往后放一个步长`() {
        // 连续两次没人接话：间隔 = 30 + 1×180 = 210 分钟。
        val throttle = ThrottleState(lastSpokenAt = at("2026-03-10T10:00:00"), ignoredStreak = 1)

        assertEquals(at("2026-03-10T13:30:00"), (decide(gathering, at("2026-03-10T13:20:00"), throttle) as InterventionVerdict.StayQuiet).earliestNextAt)
        assertTrue(decide(gathering, at("2026-03-10T13:30:00"), throttle) is InterventionVerdict.Speak)
    }

    @Test
    fun `退避序列与上限`() {
        assertEquals(listOf(30, 210, 390, 570, 750, 930, 1110, 1290, 1440, 1440), (0..9).map(config::gapMinutes))
        // 被忽略再多次也不会超过上限。
        assertEquals(1440, config.gapMinutes(40))
        assertEquals(1440, config.gapMinutes(Int.MAX_VALUE))
        // 负数（数据坏了）按 0 处理，退到最小间隔，而不是算出一个荒谬的值。
        assertEquals(30, config.gapMinutes(-5))
    }

    @Test
    fun `间隔上限保证不会永久沉默`() {
        val throttle = ThrottleState(lastSpokenAt = at("2026-03-10T10:00:00"), ignoredStreak = 99)

        assertTrue(decide(gathering, at("2026-03-10T10:29:00"), throttle) is InterventionVerdict.StayQuiet)
        assertTrue(decide(gathering, at("2026-03-11T10:01:00"), throttle) is InterventionVerdict.Speak)
    }

    @Test
    fun `接一次话 间隔就收回最短`() {
        val spoken = at("2026-03-10T10:00:00")
        val answered = ThrottleState(lastSpokenAt = spoken, ignoredStreak = 5)
            .afterAnswered(at("2026-03-10T10:05:00"))

        assertEquals(0, answered.ignoredStreak)
        // 10:40 距上次开口 40 分钟：按最小的 30 分钟够，按退避后的 930 分钟不够。
        assertTrue(decide(gathering, at("2026-03-10T10:40:00"), answered) is InterventionVerdict.Speak)
    }

    @Test
    fun `时钟被往回调也不说话`() {
        // 用户改了系统时间，落盘的「上次开口」跑到将来去了。不确定的时候不说话，
        // 而不是因为差值为负就判定「早就超过间隔了」。
        val throttle = ThrottleState(lastSpokenAt = at("2026-03-11T10:00:00"))

        val verdict = decide(gathering, at("2026-03-10T10:00:00"), throttle)

        assertEquals(QuietReason.TOO_SOON, (verdict as InterventionVerdict.StayQuiet).reason)
    }

    // ---------- AC-05.3：只往后放，不往前赶 ----------

    @Test
    fun `被忽略次数只让间隔变长或不变`() {
        for (a in 0..40) {
            for (b in a..40) {
                assertTrue(
                    "streak $a 的间隔不该比 $b 的长：${config.gapMinutes(a)} vs ${config.gapMinutes(b)}",
                    config.gapMinutes(a) <= config.gapMinutes(b),
                )
            }
        }
    }

    @Test
    fun `被忽略只会让开口更容易被推迟`() {
        // 穷举：同一个「上次开口」和同一个「现在」，只要忽略次数多的那次开口了，
        // 忽略次数少的那次就一定也开口。反过来（少的被判安静、多的却能说话）绝不允许。
        val spoken = at("2026-03-10T10:00:00")
        var comparisons = 0
        for (offsetMinutes in 0..1500 step 30) {
            val now = spoken.plusMinutes(offsetMinutes.toLong())
            for (a in 0..40) {
                for (b in a..40) {
                    val loose = decide(gathering, now, ThrottleState(spoken, ignoredStreak = a))
                    val strict = decide(gathering, now, ThrottleState(spoken, ignoredStreak = b))
                    comparisons++
                    if (strict is InterventionVerdict.Speak) {
                        assertTrue(
                            "忽略 $b 次能说话（$now 距上次 ${offsetMinutes} 分钟），忽略 $a 次反而不行",
                            loose is InterventionVerdict.Speak,
                        )
                    }
                }
            }
        }
        // 用例本身要真的跑到了：51 × 41 × 41 / 2 ≈ 42k 次比较，这里只确认量级。
        assertTrue(comparisons > 40_000)
    }

    // ---------- 数据不够或自相矛盾时照样开口 ----------

    @Test
    fun `数据不够或自相矛盾时照样会说话`() {
        // 不能因为「还没学会作息」就沉默——那就成了不干活。这时按普通作息来，并且说清楚。
        val notEnough = decide(gathering, at("2026-03-10T10:00:00"))
        assertEquals(RhythmBasis.ASSUMED_NOT_ENOUGH_DATA, (notEnough as InterventionVerdict.Speak).basis)

        val inconsistent = decide(RhythmResult.Inconsistent, at("2026-03-10T10:00:00"))
        assertEquals(RhythmBasis.ASSUMED_INCONSISTENT, (inconsistent as InterventionVerdict.Speak).basis)
    }

    @Test
    fun `三十天里开口越来越少 且不会停下来`() {
        // 模拟：每 30 分钟「打开一次应用」，从来没人接话。
        // 开始：2026-03-10 00:00，共 30 天。
        var throttle = ThrottleState()
        val start = at("2026-03-10T00:00:00")
        val ticks = 30 * 24 * 2
        val perDay = IntArray(30)
        var speaks = 0

        for (i in 0 until ticks) {
            val now = start.plusMinutes(i * 30L)
            if (decide(gathering, now, throttle) is InterventionVerdict.Speak) {
                speaks++
                perDay[i / 48]++
                throttle = throttle.afterSpoken(now)
            }
        }

        assertEquals(4, perDay[0])
        assertTrue("首日之后不该比首日更频繁：${perDay.toList()}", perDay[0] >= perDay[1])
        for (day in 1 until 29) {
            assertTrue(
                "第 ${day + 1} 天比第 $day 天更频繁（${perDay[day]} > ${perDay[day - 1]}）",
                perDay[day] <= perDay[day - 1],
            )
        }
        assertTrue("末日必须还在说话，否则就是永久沉默：${perDay[29]}", perDay[29] >= 1)
        assertTrue("总次数 $speaks 超出预期上限", speaks <= 35)
        assertTrue("总次数 $speaks 太少，退避可能过头了", speaks >= 25)
    }

    // ---------- 配置与文案 ----------

    @Test
    fun `不合法的配置直接拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { InterventionConfig(minGapMinutes = 0) }
        assertThrows(IllegalArgumentException::class.java) { InterventionConfig(backoffStepMinutes = -1) }
        assertThrows(IllegalArgumentException::class.java) { InterventionConfig(minGapMinutes = 60, maxGapMinutes = 30) }
    }

    @Test
    fun `间隔能说成人话`() {
        assertEquals("30 分钟", 30.asHumanGap())
        assertEquals("1 小时", 60.asHumanGap())
        assertEquals("1 小时 30 分钟", 90.asHumanGap())
        assertEquals("24 小时", 1440.asHumanGap())
    }

    @Test
    fun `画像摘要写得出来`() {
        val learned = ready(wake = "06:30", sleep = "22:00")

        assertEquals("06:30 起、22:00 睡", learned.rhythm.wakeSleepSummary)
    }

    @Test
    fun `没有画像时安静时段退回假定值`() {
        assertEquals(config.fallbackQuietSpan, config.quietSpanOf(gathering))
        assertEquals(config.fallbackQuietSpan, config.quietSpanOf(inconsistent))
        assertEquals(
            TimeSpan(LocalTime.parse("22:00"), LocalTime.parse("06:30")),
            config.quietSpanOf(ready(wake = "06:30", sleep = "22:00")),
        )
    }

    @Test
    fun `退避用的忽略次数不会是负的`() {
        // 数据损坏时按 0 处理，避免出现「负的忽略次数」把间隔算短。
        assertEquals(config.gapMinutes(0), config.gapMinutes(-3))
        assertFalse(ThrottleState(ignoredStreak = -3).pendingSpeak)
    }
}
