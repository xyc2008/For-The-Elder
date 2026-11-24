package com.fortheelder.ui.home

import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.engine.greeting.DayPart
import com.fortheelder.domain.engine.greeting.Greeting
import com.fortheelder.domain.engine.intervention.InterventionVerdict
import com.fortheelder.domain.engine.intervention.QuietReason
import com.fortheelder.domain.engine.intervention.RhythmBasis
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * 第 78 轮起，安静语下面还多了一行补充，说最早什么时候会再迎上来说话。它是最容易被写成
 * 「到点提醒您」的位置，所以「今天」「明天」「说不出口」三档各有一条用例压着。
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

    /**
     * 判定那一刻的钟点。全文件共用一个，只有专门验「今天 / 明天 / 说不出口」的那几条才另给。
     *
     * 跟 [greeting] 摆在一起：两者是同一次判定的输入。分开写，早晚会有一条用例拿着上午的问候
     * 配深夜的钟点，而那种组合在应用里根本不会出现。
     */
    private val now = LocalDateTime.of(2026, 3, 10, 9, 0)

    /**
     * 一条已经记下的日常。
     *
     * 用真的目录条目（书法那一组的第一步），不用手编的假句子：这样下面「锚点行」引用的
     * 文案就是用户在界面上真会看到的那一句。
     */
    private val sampleAnchor = Anchor(
        term = "书法",
        optionId = "calligraphy-paper",
        action = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
        slot = AnchorSlot.MORNING,
        createdAtEpochMilli = 0L,
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
                        // 给一个**真会出现的时刻**，别给 null：安静语下面那一行也要过词表红线，
                        // 拿 null 就等于什么都没扫到。次日早上七点正是 SLEEP_HOURS 会给出的值。
                        // 用 [now] 推出来而不是再写一个字面量：两个时间各写各的，早晚会对不上。
                        earliestNextAt = now.toLocalDate().plusDays(1).atStartOfDay().plusHours(7),
                    ),
                )
            }
        }
    }

    /**
     * 每个判定**两种处境**：手里没有记下的日常，和有一条记下的日常。
     *
     * 后者是第 10 轮加进来的（FR-09）：说话的那一刻要能顺口提一句「您自己记下的事」。
     * 安静的那一路必须两种处境下产出完全一样，所以两边都要跑。
     */
    private fun allOpenings(): List<Pair<String, Opening>> = buildList {
        for ((label, verdict) in everyVerdict()) {
            add(label to InterventionVoice.opening(verdict, greeting, now))
            add("$label+anchor" to InterventionVoice.opening(verdict, greeting, now, sampleAnchor))
        }
    }

    /** 每一句会显示给用户的措辞，带标签，方便断言失败时定位。 */
    private fun allStrings(): List<Pair<String, String>> = buildList {
        for ((label, opening) in allOpenings()) {
            add("$label.headline" to opening.headline)
            add("$label.note" to opening.note)
            // 锚点行也是用户看得见的字，同样要过词表红线。
            opening.anchorLine?.let { add("$label.anchorLine" to it) }
            // 安静语下面那一行同理。它比锚点行更危险：说错一个字就成了「到点提醒您」。
            opening.earliestLine?.let { add("$label.earliestLine" to it) }
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
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, now)
            assertEquals("Speak($basis) 改掉了问候的主话", greeting.headline, opening.headline)
        }
    }

    @Test
    fun learnedKeepsTheGreetingNoteVerbatim() {
        val opening =
            InterventionVoice.opening(InterventionVerdict.Speak(RhythmBasis.LEARNED), greeting, now)

        assertEquals(greeting.note, opening.note)
    }

    @Test
    fun assumedAppendsWithoutReplacingTheGreetingNote() {
        // 「认识多少天」是事实，必须留着；补的只是「按什么作息」。
        for (basis in listOf(RhythmBasis.ASSUMED_NOT_ENOUGH_DATA, RhythmBasis.ASSUMED_INCONSISTENT)) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, now)

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
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, now)
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
            now,
        )
        val tooSoon = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.TOO_SOON, RhythmBasis.LEARNED, null),
            greeting,
            now,
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
                    now,
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
                now,
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

    @Test
    fun sampleAnchorStillQuotesTheRealFirstStep() {
        // 上面那条 anchor 的 action 是抄自目录的。抄来的东西会过期：这条测试是提醒器——
        // 目录改了而这里没跟着改，词表扫描覆盖的就是一句用户再也看不到的话。
        val option = StarterCatalog.byId("calligraphy-paper")
        assertNotNull("目录里已经没有 calligraphy-paper 了", option)
        assertEquals(option!!.firstStep, sampleAnchor.action)
        assertEquals(option.term, sampleAnchor.term)
    }

    @Test
    fun stayQuietNeverCarriesAnAnchorLine() {
        // 用户正睡着，或者刚被招呼过一回。这种时候再顺一句「您自己记下的那件事」，
        // 安静就不再是安静了。
        for (reason in QuietReason.entries) {
            for (basis in RhythmBasis.entries) {
                val opening = InterventionVoice.opening(
                    InterventionVerdict.StayQuiet(reason, basis, null),
                    greeting,
                    now,
                    sampleAnchor,
                )
                assertNull("StayQuiet($reason,$basis) 带着锚点行说话了", opening.anchorLine)
            }
        }
    }

    @Test
    fun aQuietOpeningIsIdenticalWithAndWithoutAnAnchor() {
        // 更狠一档：安静那一路的输出必须一字不差地一样。不一样就等于
        // 「记下了日常的人，收到的安静是另一种安静」——用户会以为它变吵了。
        for (reason in QuietReason.entries) {
            for (basis in RhythmBasis.entries) {
                val verdict = InterventionVerdict.StayQuiet(reason, basis, null)
                assertEquals(
                    "StayQuiet($reason,$basis) 在有没有锚点时说法不同",
                    InterventionVoice.opening(verdict, greeting, now),
                    InterventionVoice.opening(verdict, greeting, now, sampleAnchor),
                )
            }
        }
    }

    @Test
    fun speakCarriesTheAnchorLineWhenThereIsOne() {
        for (basis in RhythmBasis.entries) {
            val opening =
                InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, now, sampleAnchor)

            assertEquals(
                "Speak($basis) 没有把用户记下的那件事说出来",
                CompanionVoice.dueLine(sampleAnchor),
                opening.anchorLine,
            )
        }
    }

    @Test
    fun speakWithoutAnAnchorHasNoAnchorLine() {
        for (basis in RhythmBasis.entries) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, now)

            assertNull("Speak($basis) 手里什么都没有却报了锚点行", opening.anchorLine)
        }
    }

    @Test
    fun userAskedQuietIsPinnedVerbatim() {
        // 用户自己按下的那个开关。这两句话要说清「是您让我安静的」，不能像在赌气，
        // 也不能让他以为应用自作主张把他静音了。全句钉住：谁顺手改了它，红在这里。
        val opening = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.USER_ASKED, RhythmBasis.LEARNED, null),
            greeting,
            now,
        )

        assertEquals("好，那就先安静一会儿。", opening.headline)
        assertEquals("您说过今天先安静一会儿。我就不迎上来说话了，想说什么，随时说。", opening.note)
        assertNull(opening.anchorLine)
        assertNull(opening.earliestLine)
    }

    @Test
    fun everyQuietReasonHasItsOwnWording() {
        // 上面那条 theTwoQuietReasonsDifferInBothLines 只比了 SLEEP_HOURS 与 TOO_SOON 两种。
        // 这里把它推广到 QuietReason 的每一个取值两两相比：安静的理由不同，两句话都得不同——
        // 否则再加一种理由时会悄悄复用老句子，用户看到的还是「这会儿是您休息的时间」。
        val openings = QuietReason.entries.map { reason ->
            reason to InterventionVoice.opening(
                InterventionVerdict.StayQuiet(reason, RhythmBasis.LEARNED, null),
                greeting,
                now,
            )
        }

        for ((rank, earlier) in openings.withIndex()) {
            for (later in openings.drop(rank + 1)) {
                assertNotEquals(
                    "${earlier.first} 与 ${later.first} 的主话一样",
                    earlier.second.headline,
                    later.second.headline,
                )
                assertNotEquals(
                    "${earlier.first} 与 ${later.first} 的补充话一样",
                    earlier.second.note,
                    later.second.note,
                )
            }
        }
    }

    @Test
    fun sleepAndTooSoonSayTheEarliestMoment() {
        // 第 78 轮：安静语下面多了一行「下一次迎来说话最早在……」。这是
        // StayQuiet.earliestNextAt 第一次被摆上屏，四档一起钉住：今天、明天、
        // 秒被截掉、以及压根算不出时刻时不硬凑。
        val at = LocalDateTime.of(2026, 3, 10, 9, 0)

        val sameDay = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.TOO_SOON, RhythmBasis.LEARNED, at.plusHours(2)),
            greeting,
            at,
        )
        assertEquals("下一次迎来说话最早在 11:00 之后。", sameDay.earliestLine)

        val tomorrow = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(
                QuietReason.SLEEP_HOURS,
                RhythmBasis.LEARNED,
                LocalDateTime.of(2026, 3, 11, 7, 0),
            ),
            greeting,
            at,
        )
        assertEquals("下一次迎来说话最早在明天 07:00 之后。", tomorrow.earliestLine)

        // 判定给的秒与纳秒对用户没有意义，印出来只会是噪声。
        val withSeconds = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(
                QuietReason.SLEEP_HOURS,
                RhythmBasis.LEARNED,
                LocalDateTime.of(2026, 3, 11, 7, 0, 12, 345_000_000),
            ),
            greeting,
            at,
        )
        assertEquals("下一次迎来说话最早在明天 07:00 之后。", withSeconds.earliestLine)

        val unknown = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(QuietReason.SLEEP_HOURS, RhythmBasis.LEARNED, null),
            greeting,
            at,
        )
        assertNull("算不出恢复时刻却硬报了一行", unknown.earliestLine)
    }

    @Test
    fun userAskedQuietIgnoresTheComputedMoment() {
        // ADR-055 的钉子：用户自己按下的安静，刻意不说恢复时刻。就算上层塞进来一个
        // 非空的 earliestNextAt，这一路也得把它丢掉——不然「明天零点是假话」又回来了。
        val opening = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(
                QuietReason.USER_ASKED,
                RhythmBasis.LEARNED,
                LocalDateTime.of(2026, 3, 12, 8, 0),
            ),
            greeting,
            LocalDateTime.of(2026, 3, 10, 9, 0),
        )

        assertNull("用户自己按下的安静不该报恢复时刻", opening.earliestLine)
    }

    @Test
    fun speakNeverCarriesAnEarliestLine() {
        // 只有安静那一路才有「下次说话最早在……」这一行。说话那一路没有静默期可言，
        // 报了就是凭空造一个不存在的约定。
        val at = LocalDateTime.of(2026, 3, 10, 9, 0)
        for (basis in RhythmBasis.entries) {
            val opening = InterventionVoice.opening(InterventionVerdict.Speak(basis), greeting, at)

            assertNull("Speak($basis) 报了恢复时刻", opening.earliestLine)
        }
    }

    @Test
    fun aBoundMoreThanADayAwayStaysSilent() {
        // 只说得出口「今天」与「明天」。后天那么远就算不过来了，宁可不说；
        // 时钟被往回调造成的过去时刻同理——说一句「今天 07:00」而此刻已是 09:00，是骗人。
        val at = LocalDateTime.of(2026, 3, 10, 9, 0)

        val dayAfterTomorrow = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(
                QuietReason.SLEEP_HOURS,
                RhythmBasis.LEARNED,
                LocalDateTime.of(2026, 3, 12, 7, 0),
            ),
            greeting,
            at,
        )
        assertNull("后天那么远也报了一行", dayAfterTomorrow.earliestLine)

        val alreadyPast = InterventionVoice.opening(
            InterventionVerdict.StayQuiet(
                QuietReason.SLEEP_HOURS,
                RhythmBasis.LEARNED,
                LocalDateTime.of(2026, 3, 10, 7, 0),
            ),
            greeting,
            at,
        )
        assertNull("已经过去的时刻被当成将来报了", alreadyPast.earliestLine)
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
