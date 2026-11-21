package com.fortheelder.ui.play

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.play.FindPair
import com.fortheelder.domain.play.PlayHint
import com.fortheelder.domain.play.SchulteGrid
import com.fortheelder.domain.play.SubtractSeven
import com.fortheelder.ui.nav.MainNav
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.关库排在组合之后
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「玩一会儿」的**渲染级**验证（FR-11 / FR-12）。
 *
 * 单测证明的是值怎么走（`SchulteGridTest`、`PlayVoiceTest`），证明不了屏幕上真的走过这条链。
 * 这里从首页出发、按用户的路走完整条：点进去 → 开始 → 点错 → 点完 → 回退路。
 *
 * 三条最要紧的断言都不是「有没有这个控件」，而是「**没有**发生什么」：
 *
 * 1. 点错之后屏幕上多出来的只有一句话，步数一步没动，也没有任何地方跳出「错了几次」；
 * 2. 点完一局之后整屏找不到「再来」「下一局」这类字眼，唯一的按钮是回去歇会儿；
 * 3. 中途退出去再进来，看到的是一局全新的——库里也没有任何能放成绩的表。
 *
 * 第 3 条用一个 SQL 去扫库里的表名，而不是靠「界面上没显示」：不落库这件事要么有地方存，
 * 要么根本无处可存，这两者只能从库里看出来。
 *
 * 第 23 轮补了第四种走法（`规则页上可以换一个游戏而开始之后就没有这条路了`）：规则页上
 * 「换一个游戏」往回走到选择页，而**开始之后那条路就不该在**——这一条同样是在断言
 * 「没有发生什么」，只是对象换成了那个按钮。
 *
 * 页面上一切文案都取自 `PlayVoice` / `PlayHint` 的当前措辞：改措辞要同时改这些断言，
 * 这是有意的摩擦。
 */
@RunWith(AndroidJUnit4::class)
class PlayScreenTest {

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

    /** 这个用例自己的容器与库文件：应用那份 `elder.db` 不归它碰。 */
    private lateinit var container: AppContainer

    @Before
    fun 用一个空库起步() {
        context.deleteDatabase(TEST_DB)
        container = AppContainer(context, TEST_DB)
        compose.setContent {
            ForTheElderTheme {
                MainNav(container = container)
            }
        }
    }

    /** 屏幕上正好有几个节点带这段文字。整串相等，不做子串匹配。 */
    private fun count(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** 含有这几个字的节点有几个。用来确认「屏幕上没有冒出某类字眼」。 */
    private fun countContaining(text: String): Int =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /**
     * 点一个写着这段文字的控件。
     *
     * 先等它出现再点，不是可有可无的礼貌：首页第一帧是 `HomeUiState.Loading`，
     * 那一帧上**三个入口一个都不在**（`HomeScreen.kt:89` 的 `state as? Ready` 判空）。
     * 首页的状态是从 Room 与 DataStore 读出来的，那两条线不归 Compose 的 idling 管，
     * 所以「等到 idle」并不保证读到的是 `Ready` 那一帧。第 12 轮实测漏过一次：
     * `点完一局看到用时和退路而看不到再来一局` 在这句上直接断言失败。
     * 等的是「节点真的在」，之后的断言仍然按原样硬碰硬。
     */
    private fun tap(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun tapBack() {
        compose.onNodeWithContentDescription("返回上一页").performClick()
    }

    /**
     * 库里有多少张名字像成绩的表。
     *
     * 这一页按设计不存任何东西，所以这个数必须是 0——包括将来有人「顺手」加一张表的情况。
     * 这条断言比「界面上没显示成绩」硬得多：显示与否是界面的事，存没存是库的事。
     */
    private fun scoreLikeTables(): Int =
        container.database.query(
            SimpleSQLiteQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND (name LIKE '%play%' OR name LIKE '%game%' " +
                    "OR name LIKE '%score%' OR name LIKE '%record%')",
            ),
        ).use { it.count }

    @Test
    fun 点错了只说一句话步数不动点对就清掉() {
        tap("玩一会儿")
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)
        assertEquals(1, count("现在点 1"))
        assertEquals("刚开局不该有提示", 0, count(PlayHint.NOT_YET.text))

        // 点了排在后面的那个：只回一句话，该点的还是 1。
        tap("25")
        assertEquals(1, count(PlayHint.NOT_YET.text))
        assertEquals(1, count("现在点 1"))

        // 点对了，那句话就没了。
        tap("1")
        assertEquals(1, count("现在点 2"))
        assertEquals(0, count(PlayHint.NOT_YET.text))

        // 回头点已经点过的格子：给的是另一句话，步数照样不动。
        // 这一下点的还是一个**被压暗的**格子，所以它必须照样能点。
        tap("1")
        assertEquals(1, count(PlayHint.ALREADY_DONE.text))
        assertEquals(1, count("现在点 2"))

        // 屏幕上没有任何地方在数错了几次：这一页没有能放下这个数的字段。
        assertEquals(0, countContaining("错"))
    }

    @Test
    fun 点完一局看到用时和退路而看不到再来一局() {
        tap("玩一会儿")
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)

        for (n in 1..25) tap(n.toString())

        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结果说的是刚才真的发生的事：25 个都点到了，用了多久。
        assertEquals(1, countContaining("25 个都点到了"))
        assertEquals(1, countContaining("关掉就没了"))

        // AC-11.3：整屏找不到任何邀请第二次的字眼，唯一的按钮是回去歇会儿。
        for (word in PUSH_WORDS) assertEquals("结束卡上出现了「$word」", 0, countContaining(word))
        assertEquals(1, count(PlayVoice.leaveLabel))
        assertEquals(0, count(PlayVoice.startLabel))

        // AC-12.2：算出来的用时只在屏幕上，库里没有多出任何一张放成绩的表。
        assertEquals(0, scoreLikeTables())

        tap(PlayVoice.leaveLabel)
        assertEquals("点了退路之后该回到首页", 1, count("跟常伴说两句"))
    }

    @Test
    fun 中途退出去再进来是一局全新的() {
        tap("玩一会儿")
        tap(SchulteGrid.game.name)

        // 进去先说清规矩，用户点头了才摆格子。
        assertEquals(1, count(PlayVoice.startLabel))
        assertEquals(0, countContaining("现在点"))
        tap(PlayVoice.startLabel)

        tap("1")
        tap("2")
        tap("3")
        assertEquals(1, count("现在点 4"))

        // 半路退出：回首页，再进来。
        tapBack()
        assertEquals(1, count("跟常伴说两句"))
        tap("玩一会儿")
        tap(SchulteGrid.game.name)

        // AC-12.4：没有「未完成」这回事——看到的是刚开始的那一步，不是接着上次点。
        assertEquals(1, count(PlayVoice.startLabel))
        assertEquals(0, countContaining("现在点"))
        assertEquals(0, count(PlayHint.ALREADY_DONE.text))

        // 而且这个「没留下」是真的没地方留下。
        assertEquals(0, scoreLikeTables())
    }

    /**
     * 点第 [index] 个写着这段文字的格子。
     *
     * 找一样的盘面上同一个字出现**两次**，所以这里不能像舒尔特那样按文字找唯一节点，
     * 只能按下标点。下标是这一局摆出来的顺序，洗牌洗的是位置，两次取到的顺序是稳定的。
     */
    private fun tapCell(text: String, index: Int) {
        compose.onAllNodesWithText(text)[index].performScrollTo().performClick()
    }

    /**
     * 第二个游戏：从选择页进去、找齐一对、看到结果。
     *
     * 这一条要证明的不只是「它能玩」，而是**它说的是自己那件事**：结束卡上报的是找到了哪两个字，
     * 而且一个用时数字都没有——这一局根本没在计时（对比舒尔特那条用例断言「25 个都点到了」＋用时）。
     * 同一个界面、同一套按钮，两句结果各不相同，这就是 [PlayRound] 按类型分派所要的效果。
     */
    @Test
    fun 找一样的点错只说一句找齐两张就收尾() {
        tap("玩一会儿")
        tap(FindPair.game.name)
        tap(PlayVoice.startLabel)

        // 一进来就能看到要做什么；盘面恰好有一对，其余都是单张。
        // 测试从这一局自己认出哪两个是一对，而不是假定某个字摆在哪——摆法是洗出来的。
        assertEquals(1, count("找出一样的那两张"))
        val pair = FindPair.words.first { count(it) == 2 }
        val single = FindPair.words.first { count(it) == 1 }

        // 点一张单张：只回一句话，盘面一格里外没动。
        tap(single)
        assertEquals(1, count(PlayHint.NOT_THIS_ONE.text))
        assertEquals(1, count(single))
        assertEquals("点错了却把一整局算成了找到", 0, countContaining("都找到了"))

        // 点中第一张：这一局还没成——还有第二张没找到，而且刚才那句「不是它」被点对清掉了。
        tapCell(pair, 0)
        assertEquals(0, countContaining("都找到了"))
        assertEquals("点对了还留着上一句提示", 0, count(PlayHint.NOT_THIS_ONE.text))

        // 再点同一张：只说一句「这个已经点过了」，挑起来的那张照样还能点。
        tapCell(pair, 0)
        assertEquals(1, count(PlayHint.ALREADY_DONE.text))

        // 点第二张：这一局到此为止。
        tapCell(pair, 1)
        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结果只说真的发生的事：找到了哪两个字。这一局没有计时，所以**不报用时**。
        assertEquals(1, countContaining("都找到了"))
        assertEquals(1, countContaining(pair))
        assertEquals(1, countContaining("关掉就没了"))
        assertEquals("结束卡上冒出了一个用时数字", 0, countContaining("这一局用了"))

        // AC-11.3：整屏找不到任何邀请第二次的字眼，唯一的按钮是回去歇会儿。
        for (word in PUSH_WORDS) assertEquals("结束卡上出现了「$word」", 0, countContaining(word))
        assertEquals(1, count(PlayVoice.leaveLabel))
        assertEquals(0, count(PlayVoice.startLabel))

        // AC-12.2：算出来的用时只在屏幕上，库里没有多出任何一张放成绩的表。
        assertEquals(0, scoreLikeTables())

        // 退路回到首页，不是回到选择页。
        tap(PlayVoice.leaveLabel)
        assertEquals("点了退路之后该回到首页", 1, count("跟常伴说两句"))
    }

    /**
     * 第三个游戏：从选择页进去、算错一道、把五道算完、看到结果。
     *
     * 这一条要证明的是**第三种规则也没把界面逼出新东西**：它和找一样的共用同一套格子、
     * 同一句提示、同一张结束卡，只有题面和结果两句话不一样。它同样不计时，所以结束卡上
     * 照样一个用时数字都没有（对比舒尔特那条，三条用例读的是同一块屏幕）。
     *
     * 五道题的答案从 100 起每次减 7，是写死在 [SubtractSeven] 里的常量，不洗牌；
     * 干扰项是洗出来的，所以「点哪一个是错的」靠屏幕自己认——从候选里挑一个恰好只出现一次的。
     */
    @Test
    fun 算一算点错只说一句五道算完就收尾() {
        tap("玩一会儿")
        tap(SubtractSeven.game.name)
        tap(PlayVoice.startLabel)

        // 一进来就能看到要做什么：题面是「100 减 7 是多少」。
        assertEquals(1, countContaining("100 减 7 是多少"))

        // 点一个错数：只回一句话，题面一步没动。
        val wrong = listOf(83, 91, 92, 94, 95).first { count(it.toString()) == 1 }
        tap(wrong.toString())
        assertEquals(1, count(PlayHint.NOT_THIS_ONE.text))
        assertEquals(1, countContaining("100 减 7 是多少"))
        assertEquals("点错了却把一整局算成了算完", 0, countContaining("都算好了"))

        // 依次点对五道。第一道点对之后，刚才那句「不是它」被清掉。
        for ((index, answer) in ANSWERS.withIndex()) {
            tap(answer.toString())
            if (index == 0) assertEquals("点对了还留着上一句提示", 0, count(PlayHint.NOT_THIS_ONE.text))
        }
        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结果只说真的发生的事：五道都算完了。这一局没有计时，所以**不报用时**。
        assertEquals(1, countContaining("5 道都算好了"))
        assertEquals(1, countContaining("关掉就没了"))
        assertEquals("结束卡上冒出了一个用时数字", 0, countContaining("这一局用了"))

        // AC-11.3：整屏找不到任何邀请第二次的字眼，唯一的按钮是回去歇会儿。
        for (word in PUSH_WORDS) assertEquals("结束卡上出现了「$word」", 0, countContaining(word))
        assertEquals(1, count(PlayVoice.leaveLabel))
        assertEquals(0, count(PlayVoice.startLabel))

        // AC-12.2：这一局同样只在屏幕上，库里没有多出任何一张放成绩的表。
        assertEquals(0, scoreLikeTables())

        // 退路回到首页，不是回到选择页。
        tap(PlayVoice.leaveLabel)
        assertEquals("点了退路之后该回到首页", 1, count("跟常伴说两句"))
    }

    /**
     * 规则页上的第二条退路（第 23 轮加）。
     *
     * 挑错了游戏想看别的，不该被逼着回首页重走一遍：规则页下面多一条「换一个游戏」，
     * 点了就回到那张选择页，什么都还在。它只出现在**开始之前**——一旦摆出格子，
     * 屏幕上的每个按钮都在跟格子抢注意力，而那时回首页那条路一直都在
     * （上面三条用例钉的就是那个）。
     */
    @Test
    fun 规则页上可以换一个游戏而开始之后就没有这条路了() {
        tap("玩一会儿")
        tap(SchulteGrid.game.name)

        // 开始之前：退路有两条，一条往回、一条往前。
        assertEquals(1, count(PlayVoice.startLabel))
        assertEquals(1, count(PlayVoice.switchLabel))

        // 往回走：回到选择页，三个游戏都还在，而且并没有偷偷摆出一局。
        tap(PlayVoice.switchLabel)
        assertEquals(1, count(PlayVoice.chooseTitle))
        for (game in listOf(SchulteGrid.game, FindPair.game, SubtractSeven.game)) {
            assertEquals("选择页上少了「${game.name}」", 1, count(game.name))
        }
        assertEquals("往回走却摆出了格子", 0, count(PlayVoice.startLabel))

        // 换一个再进去：看到的是它自己的规矩，不是上一个游戏的。
        tap(FindPair.game.name)
        assertEquals(1, count(PlayVoice.startLabel))
        assertEquals(1, count(PlayVoice.switchLabel))

        // 开始之后这条路就没了：正在点的格子旁边不多一个按钮。
        tap(PlayVoice.startLabel)
        assertEquals(1, count("找出一样的那两张"))
        assertEquals("开始之后规则页那条退路还留在屏幕上", 0, count(PlayVoice.switchLabel))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-play.db"

        /** 催促续玩的说法。结束卡上一个字都不许有。 */
        val PUSH_WORDS = listOf("再来", "继续玩", "下一局", "再玩", "倒计时", "还剩", "加油", "坚持")

        /** 算一算五道题的答案：100 起每次减 7。写死在 [SubtractSeven] 里，不洗牌。 */
        val ANSWERS = listOf(93, 86, 79, 72, 65)
    }
}
