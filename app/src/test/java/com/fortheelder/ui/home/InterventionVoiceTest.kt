package com.fortheelder.ui.home

import com.fortheelder.domain.engine.greeting.DayPart
import com.fortheelder.domain.engine.greeting.Greeting
import com.fortheelder.domain.engine.intervention.InterventionVerdict
import com.fortheelder.domain.engine.intervention.QuietReason
import com.fortheelder.domain.engine.intervention.RhythmBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InterventionVoice] 的单元测试（JVM）。
 *
 * 首页顶上那张卡是这次迭代用户唯一能看见的东西，所以它说的每一句都要被钉住。
 * 三种风险各有一条测试守着：
 *
 * 1. **空头支票**。安静语很容易被写成「到点提醒您」——本版本没有通知通道（ADR-009），
 *    写了就是假的。用词红线挡在这里。
 * 2. **假装在学用户**。采集开关默认是关的，没有画像时说「还在摸您的作息」是骗人。
 * 3. **安静变成关掉**。安静语必须留一个「想说话随时说」的出口，
 *    否则用户看到的是「它不理我了」，而不是「它这会儿没迎上来」。
 *
 * 这个文件只 import 纯 Kotlin 的东西，所以跑在 JVM 上，不需要模拟器。
 */
class InterventionVoiceTest {

    private val greeting = Greeting(
        dayPart = DayPart.FORENOON,
        address = "老张",
        headline = "老张，上午好",
        note = "已经认识 12 天了。",
        dayCount = 12L,
    )

    /** 所有可能的判定结论。判定层每一个分支都必须有话说，所以这里要穷举。 */
    private fun everyVerdict(): List<Pair<String, InterventionVerdict>> = buildList {
        for (basis in RhythmBasis.entries) {
            add("Speak($basis)" to InterventionVerdict.Speak(basis))
        }
        for (reason in QuietReason.entries) {
            for (basis in RhythmBasis.entries) {
                add(
                    "StayQuiet($reason,$basis)" to InterventionVerdict.StayQuiet(
                        reason = reason,
                        basis = basis,
                        earliestNextAt = null,
                    ),
                )
            }
        }
    }

    private fun allOpenings(): List<Pair<String, Opening>> =
        everyVerdict().map { (label, verdict) -> label to InterventionVoice.opening(verdict, greeting) }

    /** 每一句会显示给用户的措辞，带标签，方便断言失败时定位。 */
    private fun allStrings(): List<Pair<String, String>> = buildList {
        for ((label, opening) in allOpenings()) {
            add("$label.headline" to opening.headline)
            add("$label.note" to opening.note)
        }
    }

    @Test
    fun everyVerdictSaysSomethingNonBlank() {
        for ((label, text) in allStrings()) {
            assertTrue("$label 是空的", text.isNotBlank())
        }
    }

    @Test
    fun speakUsesTheGreetingHeadlineVerbatim() {
        // 该说话的时候，主话必须还是问候语本身——判定不该顺手把问候改掉。
        for (basis in RhythmBasis.entries) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting)
            assertEquals("Speak($basis) 改掉了问候的主话", greeting.headline, opening.headline)
        }
    }

    @Test
    fun learnedKeepsTheGreetingNoteVerbatim() {
        val opening = InterventionVoice.opening(InterventionVerdict.Speak(RhythmBasis.LEARNED), greeting)

        assertEquals(greeting.note, opening.note)
    }

    @Test
    fun assumedAppendsWithoutReplacingTheGreetingNote() {
        // 「认识多少天」是事实，必须留着；补的只是「按什么作息」。
        for (basis in listOf(RhythmBasis.ASSUMED_NOT_ENOUGH_DATA, RhythmBasis.ASSUMED_INCONSISTENT)) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting)

            assertTrue(
                "Speak($basis) 应当保留原来的补充话：${opening.note}",
                opening.note.contains(greeting.note),
            )
            assertNotEquals(greeting.note, opening.note)
        }
    }

    @Test
    fun withoutAProfileItNeverClaimsToBeLearningTheUser() {
        // 采集开关默认关着。没有画像时说「在学您」就是空头支票。
        val promises = listOf("学您", "摸您", "了解您", "记录您", "看出您")
        for (basis in listOf(RhythmBasis.ASSUMED_NOT_ENOUGH_DATA, RhythmBasis.ASSUMED_INCONSISTENT)) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting)
            for (phrase in promises) {
                assertTrue(
                    "Speak($basis) 里出现了「$phrase」这种假装在学的说法：${opening.note}",
                    !opening.note.contains(phrase),
                )
            }
            assertTrue(
                "Speak($basis) 应当说清按什么作息来：${opening.note}",
                opening.note.contains("按普通的作息"),
            )
        }
    }

    @Test
    fun theTwoQuietReasonsDifferInBothLines() {
        // 睡了 / 刚招呼过 是两种不同的处境，不能共用一句话。
        val sleep = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.SLEEP_HOURS, RhythmBasis.LEARNED, null),
            greeting,
        )
        val tooSoon = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.TOO_SOON, RhythmBasis.LEARNED, null),
            greeting,
        )

        assertNotEquals(sleep.headline, tooSoon.headline)
        assertNotEquals(sleep.note, tooSoon.note)
    }

    @Test
    fun quietTextDoesNotDependOnTheBasis() {
        // 安静的时候不该顺带解释算法的依据：用户在凌晨两点关心的不是「有没有画像」。
        for (reason in QuietReason.entries) {
            val openings = RhythmBasis.entries.map { basis ->
                InterventionVoice.opening(
                    InterventionVerdict.StayQuiet(reason, basis, null),
                    greeting,
                )
            }
            assertEquals("StayQuiet($reason) 的说法随依据变了", 1, openings.toSet().size)
        }
    }

    @Test
    fun quietKeepsADoorOpenForTheUserToSpeak() {
        for (reason in QuietReason.entries) {
            val opening = InterventionVoice.opening(
                InterventionVerdict.StayQuiet(reason, RhythmBasis.LEARNED, null),
                greeting,
            )
            assertTrue(
                "StayQuiet($reason) 没给用户留出口：${opening.note}",
                opening.note.contains("随时说"),
            )
        }
    }

    @Test
    fun quietNeverClaimsToHaveStoppedWorking() {
        // 「安静陪着你」不等于「我下班了」。把应用说成关掉了，用户下次就不敢来了。
        val dead = listOf("关掉", "关闭", "再见", "不理", "下次再说", "改天")
        for ((label, text) in allStrings()) {
            for (word in dead) {
                assertTrue("$label 里出现了「$word」：$text", !text.contains(word))
            }
        }
    }

    @Test
    fun noStringPromisesNotificationsOrScheduling() {
        // 本版本权限为零、没有通知通道（ADR-009）。出现这些词就是空头支票。
        val banned = listOf(
            "提醒", "通知", "推送", "闹钟", "到点", "定时", "到时候叫您",
            "安排", "课程", "报名", "链接", "优惠", "活动通知", "已为您", "我帮您", "帮您找",
        )
        assertNoBannedWords(banned)
    }

    @Test
    fun noStringJudgesTheUserOrPressuresThem() {
        val banned = listOf(
            "加油", "坚持", "优秀", "真棒", "应该", "必须", "要努力", "别偷懒",
            "上次", "昨天", "多久没", "好几天",
        )
        assertNoBannedWords(banned)
    }

    private fun assertNoBannedWords(banned: List<String>) {
        for ((label, text) in allStrings()) {
            for (word in banned) {
                assertTrue(
                    "$label 里出现了不该出现的词「$word」：$text",
                    !text.contains(word),
                )
            }
        }
    }
}
