package com.fortheelder.ui.talk

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「可以这样开始」+「常伴记着的日常」两张卡的**渲染级**验证（FR-08 / FR-09 / FR-10）。
 *
 * 单测证明的是措辞与判断（`StarterCatalogTest`、`AnchorRulesTest`、`InterventionVoiceTest`），
 * 证明不了屏幕上真的走过这条链。这一页的承诺全在**顺序**上，而顺序只有真的点一遍才算数：
 *
 * 1. 用户没表态，「可以这样开始」整张卡不存在（不是一张空卡、也不是几个灰按钮）；
 * 2. 表过态之后起点是**收着的**，第一步的原文不在屏幕上；
 * 3. 摊开 → 「把这一步记成日常」→ 四个时段按钮，一步都不能少也不能自动跳过去；
 * 4. 时段按钮点下去之前，库里必须仍然是 0 条——「不自动替用户记」这件事要落在数据上；
 * 5. 删就是删：一次点击之后这条立刻没了，屏幕上不冒出任何询问。
 *
 * 一条长用例而不是五条短用例，是因为中间任何一步的真实状态都依赖前面几步的落库结果；
 * 拆开之后每条都得先自己造一遍前置条件，反而更容易造出一个界面上不存在的局面。
 *
 * 页面上一切文案都取自 `CompanionVoice` / `TalkVoice` 的当前措辞：改措辞要同时改这些断言，
 * 这是有意的摩擦。
 */
@RunWith(AndroidJUnit4::class)
class TalkStarterTest {

    /** 它自己不挂 `@get:Rule`：由下面的 `rules` 包住，收尾顺序才由那条链说了算。 */
    private val compose = createComposeRule()

    /**
     * 关库这一步排在 compose 规则**外面**：宿主 Activity 先被销毁，之后才关库。
     * 顺序为什么重要，写在 `关库排在组合之后` 上。
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 这个用例自己的容器与库文件：应用那份 `elder.db` 不归它碰（见 `TalkInputTest` 的说明）。 */
    private lateinit var container: AppContainer

    /** 两份偏好也归它自己，不传的话写的会是这台设备上应用真正的设置（见 `自己的存储`）。 */
    private lateinit var 存储: 自己的存储

    @Before
    fun 用一个空库起步() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
    }

    private fun show() {
        compose.setContent {
            ForTheElderTheme {
                TalkScreen(container = container, onBack = {})
            }
        }
    }

    /** 屏幕上正好有几个节点带这段文字。整串相等，不做子串匹配。 */
    private fun count(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** 含有这几个字的节点有几个。只用来确认「屏幕上没有冒出一个问句」。 */
    private fun countContaining(text: String): Int =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /** 等一段文字出现。落库与回读是异步的，断言的时刻要等它到位。 */
    private fun awaitText(text: String) {
        compose.waitUntil(STORE_TIMEOUT_MILLIS) { count(text) > 0 }
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    /**
     * 起点的标题行上带着「大概几分钟」（[StarterCard] 的拼法），所以只按标题的片段去找。
     *
     * 找片段这件事在这里是安全的：整页上带这几个字的只有这一行的标题，`anchorAsk` 里虽然也
     * 会念一遍标题，但那是摊开之后、而且带「把…记成日常？」整句。
     */
    private fun tapRow(title: String) {
        compose.onNodeWithText(title, substring = true).performScrollTo().performClick()
    }

    /** 在真输入框里把这句话打进去，再点「说完了」。 */
    private fun say(sentence: String) {
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(sentence)
        tap("说完了")
    }

    @Test
    fun 表过态才看得到起点摊开之后才记成日常删掉就没了() {
        show()

        val option = StarterCatalog.byTerm(TERM).first()
        val draft = AnchorRules.draftOf(option.id, AnchorSlot.MORNING)
        assertNotNull("这个用例挑的起点必须真在目录里，否则下面的断言全是在断言空", draft)
        val anchor = Anchor(
            term = TERM,
            optionId = option.id,
            action = draft!!.action,
            slot = AnchorSlot.MORNING,
            createdAtEpochMilli = 0L,
        )

        // AC-08.1：还没表态，两张卡都不存在——不只是没有起点，连卡片标题都不该有。
        assertEquals(0, count(CompanionVoice.optionsIntro(TERM)))
        assertEquals(0, count(CompanionVoice.anchorCardTitle()))
        assertEquals(0, count(CompanionVoice.anchorEmpty()))

        // 用户自己说想做这件事，然后自己点了同意。
        say(SENTENCE)
        awaitText(TalkVoice.confirmLabel(IntentKind.INTEREST))
        assertEquals(0, count(CompanionVoice.optionsIntro(TERM)))
        tap(TalkVoice.confirmLabel(IntentKind.INTEREST))
        awaitText(CompanionVoice.optionsIntro(TERM))

        // 卡片出现了，起点是收着的：第一步的原文不在屏幕上，要准备什么也没说。
        assertEquals(1, count(CompanionVoice.optionsIntro(TERM)))
        assertEquals(0, count(option.firstStep))
        assertEquals(0, count(CompanionVoice.prepLine(option.prep)))

        // 时段按钮属于「摊开 + 正在问」的那一步，此刻一个都不该在。
        for (slot in CompanionVoice.confirmableSlots) {
            assertEquals(0, count(CompanionVoice.confirmSlotLabel(slot)))
        }

        // AC-09.1：还没有任何日常，这张卡说的是实话（此时它已经有理由出现了）。
        assertEquals(1, count(CompanionVoice.anchorEmpty()))

        // 摊开这一步：第一步原文与要准备什么都得摆出来，两个动作各一个。
        tapRow(option.title)
        awaitText(option.firstStep)
        assertEquals(1, count(option.firstStep))
        assertEquals(1, count(CompanionVoice.prepLine(option.prep)))
        assertEquals(1, count(CompanionVoice.doneLabel()))
        assertEquals(1, count(CompanionVoice.rememberLabel()))

        // AC-09.2：记成日常要用户自己点，点了之后才允许问记在哪个时段。
        tap(CompanionVoice.rememberLabel())
        awaitText(CompanionVoice.anchorAsk(option.title))
        for (slot in CompanionVoice.confirmableSlots) {
            assertEquals(1, count(CompanionVoice.confirmSlotLabel(slot)))
        }
        assertEquals(1, count(CompanionVoice.cancelAnchorLabel()))

        // 关键的一条：问到这一步为止，库里必须仍然是空的。时段按钮点下去之前，
        // 常伴不许替用户把这件事记下来。
        assertEquals(1, count(CompanionVoice.anchorEmpty()))
        assertEquals(0, runBlocking(Dispatchers.IO) { container.anchorRepository.count() })

        // 用户自己挑了「记在上午」——这个按钮本身就是确认动作。
        tap(CompanionVoice.confirmSlotLabel(AnchorSlot.MORNING))
        awaitText(CompanionVoice.anchorActionLine(anchor))

        assertEquals(1, count(CompanionVoice.anchorActionLine(anchor)))
        assertEquals(0, count(CompanionVoice.anchorEmpty()))
        assertEquals(1, count(CompanionVoice.anchorSlotLabel(AnchorSlot.MORNING)))
        assertEquals(1, runBlocking(Dispatchers.IO) { container.anchorRepository.count() })

        // 同一步在收起之后仍然不给「记成日常」：换成一句说清现状的话，而不是一个点了没反应的按钮。
        assertEquals(1, count(CompanionVoice.anchorAlreadyLine(AnchorSlot.MORNING)))
        assertEquals(0, count(CompanionVoice.rememberLabel()))
        for (slot in CompanionVoice.confirmableSlots) {
            assertEquals(0, count(CompanionVoice.confirmSlotLabel(slot)))
        }

        // AC-09.3：删就是删。点一次之后这条立刻没了，中途不冒出任何询问——
        // 如果这里弹了对话框，下面第一句就会等到超时，这一页的承诺当场就破了。
        tap(CompanionVoice.anchorForgetLabel())
        awaitText(CompanionVoice.anchorEmpty())

        assertEquals(0, count(CompanionVoice.anchorActionLine(anchor)))
        assertEquals(1, count(CompanionVoice.anchorEmpty()))
        assertEquals("删掉之后不该冒出任何询问", 0, countContaining("确定"))
        assertEquals(0, runBlocking(Dispatchers.IO) { container.anchorRepository.count() })

        // 删完之后这一步又回到可以重新记的状态：不是留一条已删的痕迹，而是真的回到原样。
        assertEquals(1, count(CompanionVoice.rememberLabel()))
        assertEquals(0, count(CompanionVoice.anchorAlreadyLine(AnchorSlot.MORNING)))
    }

    /**
     * 第 86 轮：四个时段按钮里只有「想起来就做」和它自己的时段名不同形。
     *
     * 此前回执复述的是时段名，于是用户刚按下「想起来就做」，回执说「记在想起来的时候」——
     * 屏幕上先后闪过两个词，用户得自己在心里把它们对上。这条用例把 ANYTIME 这一档单独走一遍，
     * 也顺带把它第一次拉到设备上验证（此前只有 MORNING 被真实点过）。
     */
    @Test
    fun 想起来就做的回执复述用户按下的那个词() {
        show()

        val option = StarterCatalog.byTerm(TERM).first()
        val draft = AnchorRules.draftOf(option.id, AnchorSlot.ANYTIME)
        assertNotNull("这个用例挑的起点必须真在目录里，否则下面的断言全是在断言空", draft)
        val anchor = Anchor(
            term = TERM,
            optionId = option.id,
            action = draft!!.action,
            slot = AnchorSlot.ANYTIME,
            createdAtEpochMilli = 0L,
        )

        say(SENTENCE)
        awaitText(TalkVoice.confirmLabel(IntentKind.INTEREST))
        tap(TalkVoice.confirmLabel(IntentKind.INTEREST))
        awaitText(CompanionVoice.optionsIntro(TERM))

        tapRow(option.title)
        awaitText(option.firstStep)
        tap(CompanionVoice.rememberLabel())
        awaitText(CompanionVoice.anchorAsk(option.title))

        // 用户自己挑了「想起来就做」——这个按钮本身就是确认动作。
        tap(CompanionVoice.confirmSlotLabel(AnchorSlot.ANYTIME))
        awaitText(CompanionVoice.anchorActionLine(anchor))

        assertEquals(1, runBlocking(Dispatchers.IO) { container.anchorRepository.count() })
        // 回执里复述的就是用户刚按下的那个词，不是另一个词。
        assertEquals(1, count(CompanionVoice.anchorAlreadyLine(AnchorSlot.ANYTIME)))
        assertEquals(0, count(CompanionVoice.confirmSlotLabel(AnchorSlot.ANYTIME)))
        assertEquals("回执不该复述另一个词", 0, countContaining("记在想起来的时候"))
        // 「想起来的时候」仍留在锚点卡的时段名上：那是位置，不是刚按下的动作，有意保留。
        assertEquals(1, count(CompanionVoice.anchorSlotLabel(AnchorSlot.ANYTIME)))
    }

    private companion object {
        const val STORE_TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-talk-starter.db"

        const val TERM = "书法"
        const val SENTENCE = "我一直想学书法"
    }
}
