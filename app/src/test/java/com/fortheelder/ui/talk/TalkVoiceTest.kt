package com.fortheelder.ui.talk

import com.fortheelder.domain.companion.CopyRules
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

    /**
     * `TalkVoice` 里所有 `const val`。
     *
     * 这些常量在对象类上是 `public static final String` 字段，所以能用反射一次枚举干净。
     * 手写的清单会随新常量增加而**静默过期**——第 94 轮之前这里就是手写的，只列了那七个函数，
     * `pageTitle`、`clearAll`、`forgetThis` 等 25 处屏幕文案从来没进过用词红线。
     */
    private fun talkVoiceStringConstants(): List<Pair<String, String>> =
        TalkVoice::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { field ->
                field.isAccessible = true
                field.name to (field.get(null) as String)
            }

    /**
     * 所有会显示给用户的措辞，带上一份「这是哪来的」的标签，方便断言失败时定位。
     *
     * 覆盖范围只有 `TalkVoice` **这一个产出方**：七个随状态变形的函数、全部常量、
     * 三个带参数的函数。别的页面（`HomeVoice`、`SettingsVoice`、`PlayVoice` 等）各有各的
     * 屏幕文案，不在这一份里。
     */
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
        for ((name, text) in talkVoiceStringConstants()) {
            add("常量 $name" to text)
        }
        add("evidenceLine" to TalkVoice.evidenceLine("一直想学书法"))
        add("echoLine" to TalkVoice.echoLine("我一直想学书法"))
        for (kind in IntentKind.entries) {
            add("kindLabel($kind)" to TalkVoice.kindLabel(kind))
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
    fun declineReceiptNeverNamesAnActionTheAppDoesNotHave() {
        // R86 立的口径：回执要接着说用户刚按下那颗按钮时被问的那件事，不许换成别的动作。
        // 这里延续第 86 轮吃过的教训——判据必须有**写死的字面量**，不能只用被测代码自己的
        // 返回值互比：`decidedLine()` 改成什么样，同源断言就跟着变成什么样，改错了照样绿。
        //
        // 「聊」是这个 App 明确否认的能力：它没联网、没有对话，页面上那句
        // 「没听懂的时候，常伴不会随便找个话题接着聊」说的就是这件事。所以三档拒绝回执里
        // 一个字都不许出现——第 87 轮之前，INTEREST 档那句是「好，这件事就先不聊了。」，
        // 而问句问的是「这件事要不要常伴替您记着？」，两句话说的根本不是一回事。
        // 判据是**共用动作词**而不是整句照抄：问「记下来」、答「先不记了」是可以的
        // （同一件事的另一种时态），问「记着」、答「不聊了」不行（换了一件事）。
        val expectedAction = mapOf(
            IntentKind.INTEREST to "记",
            IntentKind.STATE to "放在心上",
            IntentKind.REQUEST to "记",
        )
        for (kind in IntentKind.entries) {
            val receipt = TalkVoice.decidedLine(kind, WishDecision.DECLINE)
            assertTrue("拒绝回执里出现了「聊」，这个 App 没有对话能力（$kind）：$receipt", !receipt.contains("聊"))
            val action = expectedAction.getValue(kind)
            assertTrue("拒绝回执得与同档问句共用动作词「$action」（$kind）：$receipt", receipt.contains(action))
        }
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

    // ---- 与「文案纪律表」对齐 ----

    @Test
    fun allStrings_reallyCoversEveryTalkVoiceString() {
        // 反射扫常量这条路一旦静默返回空（字段改名、换 JDK、对象类没被初始化……），
        // 上面那两条用词红线就会全绿——那是这一页最难发现的假绿。所以哨兵必须有：
        // 两个名字得真被扫到，条数也不许比写这条用例时更少。
        val constants = talkVoiceStringConstants()
        val names = constants.map { it.first }
        assertTrue("反射没扫到 pageTitle，常量这一趟大概是空的", "pageTitle" in names)
        assertTrue("反射没扫到 forgetThis，常量这一趟大概是空的", "forgetThis" in names)
        assertTrue("TalkVoice 的 const val 只剩 ${constants.size} 个，比写这条用例时还少", constants.size >= 25)
        // 这些常量得真的进了 allStrings()，否则守卫与被扫对象脱节。
        val seen = allStrings().map { it.second }.toSet()
        for ((name, text) in constants) {
            assertTrue("常量 $name 没被 allStrings() 覆盖：$text", text in seen)
        }
    }

    @Test
    fun onlyTheTwoInterestLabelsTripTheCopyRulesAndThatIsWrittenDown() {
        // 第 94 轮把两件事对了起来：`CopyRules` 那张全 App 的词表，与这一页真正写在屏幕上的词。
        // 之前没有任何一条用例做过这件事——`CopyRulesTest` 用的是手写探针句，
        // `CopyRulesPlacementTest` 只管「表有几张、词对不对」，`CompanionVoiceTest` 只管它自己。
        //
        // 对下来只有两处命中，而且都是**有意**的：`confirmLabel(INTEREST)` 的「愿意了解一下」
        // 与 `reopenLabel(INTEREST)` 的「我现在想了解一下」各命中 `vagueWords` 里的「了解一下」。
        // 这句按钮文案是 docs/01 AC-07.2 点名叫作「显式选择」的例子——它说的是用户**按下去
        // 就能做到**的动作，与 `vagueWords` 要拦的「听着客气、照着做不出来」的空话不是同一类
        // 东西。所以这是**类别错位**，不是谁写错了：不能为了变绿删掉「了解一下」这个词
        // （会连带红掉规格表与起点目录的两条用例），也不能改掉按钮文案（会与需求文档对不上）。
        // 做法是把这两处**登记**下来。
        //
        // 登记表是写死的字面量：加了新文案、或谁动了词表，这条就红，逼人回来重新决定
        // ——是补进登记表，还是改掉那句话。另一条断言防它空转：登记表里的键必须真被扫到。
        //
        // 边界：这条只兜住 `TalkVoice` 这一个产出方。别的页面的屏幕词没有被这条表扫过，
        // 也没有对应的登记表——那是下一轮的事。
        val exempt = mapOf(
            "confirmLabel(${IntentKind.INTEREST})" to listOf("出现没法照做的空话「了解一下」"),
            "reopenLabel(${IntentKind.INTEREST})" to listOf("出现没法照做的空话「了解一下」"),
        )
        val strings = allStrings()
        val seen = strings.map { it.first }.toSet()
        for (label in exempt.keys) {
            assertTrue("登记表里的 $label 根本没被 allStrings() 扫到，这张表在空转", label in seen)
        }
        for ((label, text) in strings) {
            assertEquals(
                "「$label」与登记表对不上：$text",
                exempt[label] ?: emptyList<String>(),
                CopyRules.violations(text),
            )
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
