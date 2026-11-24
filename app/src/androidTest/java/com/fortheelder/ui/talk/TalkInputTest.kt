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
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishStatus
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「说两句」这一页的**渲染级**验证（FR-07 的四条验收标准）。
 *
 * 单测（`WishGateTest` / `TalkVoiceTest` / `TalkUiStateTest`）证明的是「判断和措辞对不对」，
 * 证明不了「屏幕上真的有这些按钮、点了真的会发生这些事」。这一页的核心承诺恰恰是**交互**：
 * 用户点同意之前不能有任何后续动作、点了不用就不能再被问、听不明白就什么也不许记。
 * 这些只有把整页渲染出来、真的点下去才算数。
 *
 * 两个必须交代的做法：
 * 1. **中文在输入框里是真打字进去的**，不是直接调 `hear()`。`adb shell input text` 送不了
 *    中文（GAP-02），但 Compose 的测试注入走的是 `TextInputService`，不受那个限制——
 *    所以「用户自己写一句话」这条路径在这里能真的被走一遍。
 * 2. **「一屏只有一组闸门按钮」是这些断言能成立的前提**。同一件事在「我听见的」和
 *    「常伴记住的事」里各出现一组按钮，`assertEquals(1, count(...))` 就会红——这条
 *    防重复规则因此被钉在这里，改坏了会立刻有测试报出来。
 *
 * 页面上一切文案都取自 `TalkVoice` / `TalkScreen` 的当前措辞：措辞是产品决定，
 * 改措辞要同时改这些断言，这是有意的摩擦。
 */
@RunWith(AndroidJUnit4::class)
class TalkInputTest {

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

    /**
     * 这一页要的容器是**这个用例自己的**，库文件也是自己的（[TEST_DB]）。
     *
     * 不能借应用那份 `ElderApp.container`：它用的是正式库 `elder.db`，而同一个测试进程里
     * 还有几个数据用例会 `deleteDatabase("elder.db")` 再重开。真机上就是这么撞出
     * `no such table: wish` 的——页面代码一行没改，它本来不会在自己的进程里删自己的库。
     */
    private lateinit var container: AppContainer

    /** 两份偏好也归它自己，不传的话写的会是这台设备上应用真正的设置（见 `自己的存储`）。 */
    private lateinit var 存储: 自己的存储

    @Before
    fun 清空已经记住的事() {
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

    /** 等一段文字出现。落库与回读是异步的，断言的时刻要等它到位。 */
    private fun awaitText(text: String) {
        compose.waitUntil(STORE_TIMEOUT_MILLIS) { count(text) > 0 }
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    /**
     * 对话框里的按钮**不能走 [tap]**。
     *
     * 对话框是另一个窗口，它的内容没有可滚动的祖先，`performScrollTo()` 会直接断言失败
     * （真机上报的是 `Semantic Node has no parent layout with a Scroll SemanticsAction`）。
     * 对话框本来就整个在屏幕上，直接点即可。
     */
    private fun tapInDialog(text: String) {
        compose.onNodeWithText(text).performClick()
    }

    /** 在真输入框里把这句话打进去，再点「说完了」。 */
    private fun say(sentence: String) {
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(sentence)
        tap("说完了")
    }

    private fun wish(status: WishStatus) = StoredWish(
        kind = IntentKind.INTEREST,
        term = "书法",
        evidence = "一直想学书法",
        sentence = "我一直想学书法",
        status = status,
        heardAtEpochMilli = 1_000L,
        decidedAtEpochMilli = null,
    )

    @Test
    fun 自己写的一句话能听出来而且只问一次要不要记() {
        show()

        say("我一直想学书法")

        // 用户的原话被原样念回来——带前缀断言，免得和输入框里的内容撞车。
        awaitText("您说的是：「我一直想学书法」")

        // 听出来的事只有这一件，类别与词条都是中文说法，不报「置信度」。
        assertEquals(1, count("想做的事：书法"))
        assertEquals(1, count("是从「一直想学书法」这几个字听出来的。"))

        // 闸门：问一次，两个按钮各一个。declineLabel 恰好 1 个是防重复规则的证据。
        assertEquals(1, count(TalkVoice.prompt(IntentKind.INTEREST)))
        assertEquals(1, count(TalkVoice.confirmLabel(IntentKind.INTEREST)))
        assertEquals(1, count(TalkVoice.declineLabel(IntentKind.INTEREST)))

        // AC-07.1：用户还没表态（这里连表态的机会都还没用手）——确认之后才有的东西一个都不许出现。
        val offerLines = TalkVoice.offerLines(wish(WishStatus.CONFIRMED))
        assertTrue("确认之后本来应当有回应，否则这条断言是空的", offerLines.isNotEmpty())
        for (line in offerLines) {
            assertEquals("还没表态就不该出现确认后才有的内容：$line", 0, count(line))
        }
    }

    @Test
    fun 点了先不用之后不再问但用户自己还能改主意() {
        show()

        say("我一直想学书法")
        awaitText("您说的是：「我一直想学书法」")

        tap(TalkVoice.declineLabel(IntentKind.INTEREST))
        awaitText(TalkVoice.decidedLine(IntentKind.INTEREST, WishDecision.DECLINE))

        // R86 的口径：回执要接着说用户刚按下那颗按钮时**被问的那件事**。下面两条是
        // **写死字面量**、与被测代码不同源——第 86 轮的教训是：像 `count(decidedLine(...))`
        // 这样拿被测代码自己的返回值互比，回执改错了也照样绿。
        // 问句问的是「这件事要不要常伴替您记着？」，所以回执必须说「记着」；
        // 那个把「记着」换成这个 App 没有的对话能力「聊」的旧句子，屏上不许再有。
        assertEquals(1, count("好，这件事就不用常伴记着了。"))
        assertEquals(0, count("好，这件事就先不聊了。"))

        // AC-07.3：定过之后这一屏上不再有问句。
        assertEquals(0, count(TalkVoice.prompt(IntentKind.INTEREST)))

        // 又说了一句别的：「我听见的」清空了，但刚才那件事的记忆还在（是落盘的）。
        tap("今天天气还行")
        awaitText("这句我没听明白")
        assertEquals(0, count("我听见的"))
        awaitText(TalkVoice.statusLabel(WishStatus.DECLINED))

        // 用户自己回到列表里主动改主意——这算显式动作，不是常伴主动提起。
        tap(TalkVoice.reopenLabel(IntentKind.INTEREST))
        awaitText(TalkVoice.statusLabel(WishStatus.CONFIRMED))

        // 再说一次同一件事：这时候才允许出现「确认之后」的回应。
        tap("我一直想学书法")
        awaitText(TalkVoice.decidedLine(IntentKind.INTEREST, WishDecision.CONFIRM))
        for (line in TalkVoice.offerLines(wish(WishStatus.CONFIRMED))) {
            assertTrue("确认之后应当看得到：$line", count(line) > 0)
        }
    }

    @Test
    fun 全部清空要先问一次再真的清掉() {
        show()

        say("我一直想学书法")
        awaitText("您说的是：「我一直想学书法」")
        tap(TalkVoice.confirmLabel(IntentKind.INTEREST))
        awaitText(TalkVoice.decidedLine(IntentKind.INTEREST, WishDecision.CONFIRM))

        assertEquals(0, count("现在还是空的。"))

        tap("全部清空")
        awaitText("确定要清空吗？")
        tapInDialog("清空")

        awaitText("现在还是空的。")
    }

    @Test
    fun 听不明白的一句话不会记下任何东西() {
        show()

        tap("今天天气还行")
        awaitText("这句我没听明白")

        assertEquals(1, count("您说的是：「今天天气还行」"))
        assertEquals(0, count("我听见的"))
        assertEquals(0, count(TalkVoice.confirmLabel(IntentKind.INTEREST)))
        assertEquals(0, count(TalkVoice.declineLabel(IntentKind.INTEREST)))

        // 页面上没有闸门，库里也必须真的是空的——「不猜」这件事要落在数据上。
        assertEquals(0, runBlocking(Dispatchers.IO) { container.wishRepository.count() })
    }

    private companion object {
        const val STORE_TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-talk-input.db"
    }
}
