package com.fortheelder.ui.talk

import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 闸门与清单上的**每一句话**的单元测试（JVM）。
 *
 * 这一页上真正会被用户误会的不是布局，是用词，所以措辞必须有测试守着：
 * 「常伴记住了」和「常伴帮您安排好了」在代码里只差几个字，对用户却是两件事。
 *
 * 这个文件只 import 了纯 Kotlin 的东西（`TalkVoice`、领域枚举），
 * 所以它能跑在 JVM 上，不需要模拟器。
 */
class TalkVoiceTest {

    private fun wish(status: WishStatus, term: String = "书法", kind: IntentKind = IntentKind.INTEREST) =
        StoredWish(
            kind = kind,
            term = term,
            evidence = "一直想学书法",
            sentence = "我一直想学书法",
            status = status,
            heardAtEpochMilli = 1_700_000_000_000L,
            decidedAtEpochMilli = null,
        )

    /** 所有会显示给用户的措辞，带上一份「这是哪来的」的标签，方便断言失败时定位。 */
    private fun allStrings(): List<Pair<String, String>> = buildList {
        for (kind in IntentKind.entries) {
            add("prompt($kind)" to TalkVoice.prompt(kind))
            add("confirmLabel($kind)" to TalkVoice.confirmLabel(kind))
            add("declineLabel($kind)" to TalkVoice.declineLabel(kind))
            add("reopenLabel($kind)" to TalkVoice.reopenLabel(kind))
            for (decision in WishDecision.entries) {
                add("decidedLine($kind,$decision)" to TalkVoice.decidedLine(kind, decision))
            }
            for (status in WishStatus.entries) {
                for (line in TalkVoice.offerLines(wish(status, kind = kind))) {
                    add("offerLines($kind,$status)" to line)
                }
            }
        }
        for (status in WishStatus.entries) {
            add("statusLabel($status)" to TalkVoice.statusLabel(status))
        }
    }

    // ---- offerLines：AC-07.4 的断言落在这里 ----

    @Test
    fun offerLines_confirmedGivesExactlyOneLine() {
        val lines = TalkVoice.offerLines(wish(WishStatus.CONFIRMED))
        assertEquals(1, lines.size)
        assertTrue("确认之后给的这句话里应当出现词条本身：$lines", lines.single().contains("书法"))
    }

    @Test
    fun offerLines_pendingIsEmpty() {
        // 「意愿 + 未确认，产出为空」——AC-07.4 的原话。
        assertEquals(emptyList<String>(), TalkVoice.offerLines(wish(WishStatus.PENDING)))
    }

    @Test
    fun offerLines_declinedIsEmpty() {
        assertEquals(emptyList<String>(), TalkVoice.offerLines(wish(WishStatus.DECLINED)))
    }

    @Test
    fun offerLines_emptyForEveryUnconfirmedStatusAndKind() {
        for (kind in IntentKind.entries) {
            for (status in WishStatus.entries) {
                val lines = TalkVoice.offerLines(wish(status, kind = kind))
                if (status == WishStatus.CONFIRMED) {
                    assertEquals("确认过的应当有内容（$kind, $status）", 1, lines.size)
                } else {
                    assertEquals("未确认的一律不许有内容（$kind, $status）", emptyList<String>(), lines)
                }
            }
        }
    }

    @Test
    fun offerLines_mentionsItsOwnTermOnly() {
        val a = TalkVoice.offerLines(wish(WishStatus.CONFIRMED, term = "书法")).single()
        val b = TalkVoice.offerLines(wish(WishStatus.CONFIRMED, term = "摄影")).single()
        assertTrue(a.contains("书法"))
        assertTrue(b.contains("摄影"))
        assertNotEquals(a, b)
    }

    // ---- 按钮文字 ----

    @Test
    fun confirmLabels_areNonBlankAndDistinctPerKind() {
        val labels = IntentKind.entries.map { TalkVoice.confirmLabel(it) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(IntentKind.entries.size, labels.toSet().size)
    }

    @Test
    fun declineLabels_areNonBlankAndDistinctPerKind() {
        val labels = IntentKind.entries.map { TalkVoice.declineLabel(it) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(IntentKind.entries.size, labels.toSet().size)
    }

    @Test
    fun reopenLabels_areNonBlankAndDistinctPerKind() {
        val labels = IntentKind.entries.map { TalkVoice.reopenLabel(it) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(IntentKind.entries.size, labels.toSet().size)
    }

    @Test
    fun prompts_areNonBlankAndDistinctPerKind() {
        val prompts = IntentKind.entries.map { TalkVoice.prompt(it) }
        assertTrue(prompts.all { it.isNotBlank() })
        assertEquals(IntentKind.entries.size, prompts.toSet().size)
    }

    @Test
    fun confirmAndDeclineButtonsNeverLookAlike() {
        for (kind in IntentKind.entries) {
            assertNotEquals(
                "同意和不用两个按钮不能长得一样（$kind），否则点错了都不知道",
                TalkVoice.confirmLabel(kind),
                TalkVoice.declineLabel(kind),
            )
            assertNotEquals(TalkVoice.confirmLabel(kind), TalkVoice.reopenLabel(kind))
        }
    }

    @Test
    fun interestConfirmLabelIsTheExplicitChoiceNamedByTheSpec() {
        // docs/01 的 AC-07.2 把「愿意了解一下」点名为「显式选择」的例子，
        // 改掉这句话就得同时改需求文档，所以这里钉住。
        assertEquals("愿意了解一下", TalkVoice.confirmLabel(IntentKind.INTEREST))
    }

    @Test
    fun decidedLines_areNonBlankAndAllSixDiffer() {
        val lines = buildList {
            for (kind in IntentKind.entries) {
                for (decision in WishDecision.entries) {
                    add(TalkVoice.decidedLine(kind, decision))
                }
            }
        }
        assertTrue(lines.all { it.isNotBlank() })
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test
    fun statusLabels_areNonBlankAndDistinct() {
        val labels = WishStatus.entries.map { TalkVoice.statusLabel(it) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(WishStatus.entries.size, labels.toSet().size)
    }

    // ---- 用词红线 ----

    @Test
    fun noStringUsesNotificationOrContentDeliveryWords() {
        // 这些词一旦出现，就意味着在替用户安排事——第 9/10 轮才可能有的能力。
        // 现在写了就是空头支票，所以挡在测试里，而不是靠人记得。
        val banned = listOf(
            "提醒", "通知", "推送", "安排", "课程", "报名", "链接", "优惠",
            "活动通知", "已为您", "我帮您", "帮您找",
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
    fun everyStringIsNonBlank() {
        for ((label, text) in allStrings()) {
            assertTrue("$label 是空的", text.isNotBlank())
        }
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
