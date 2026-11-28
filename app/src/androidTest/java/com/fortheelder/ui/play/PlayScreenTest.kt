package com.fortheelder.ui.play

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.play.CardWords
import com.fortheelder.domain.play.FindPair
import com.fortheelder.domain.play.KindWords
import com.fortheelder.domain.play.Memory
import com.fortheelder.domain.play.OddEvenGrid
import com.fortheelder.domain.play.OddOne
import com.fortheelder.domain.play.PlayHint
import com.fortheelder.domain.play.ReverseGrid
import com.fortheelder.domain.play.SameKind
import com.fortheelder.domain.play.SchulteGrid
import com.fortheelder.domain.play.SubtractSeven
import com.fortheelder.ui.nav.MainNav
import com.fortheelder.ui.theme.ElderDimens
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** 两份偏好也归它自己，不传的话写的会是这台设备上应用真正的设置（见 `自己的存储`）。 */
    private lateinit var 存储: 自己的存储

    @Before
    fun 用一个空库起步() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
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
     * 第四个游戏：从选择页进去、翻错一对盖回去、把六对配齐、看到结果。
     *
     * 这一条要证明的是**牌背这个新形状真的接在界面上**：一进来十二张牌全写着牌背，
     * 翻开才露字，翻错只回一句话、点一下就盖回去——而结束卡照样一个用时数字都没有
     * （这一局根本没在计时，报数就是说谎）。
     *
     * 摆法是洗出来的，所以这一局**只能在屏幕上认牌**：先翻开盖着的第一张，再逐张试它的搭档，
     * 试错了就点一下盖回去、重翻第一张，换下一张再试。走完六对才说明这一局真的赢得下来——
     * 只翻两张就收手的写法证明不了这件事。
     *
     * 第 41 轮写这条用例时在这里踩了一次，值得留着：最后一对配上的**同一刻**这一局就收了，
     * 盘面立刻下屏换成结束卡，所以「配对的两张脸同框」在最后一对上**根本观察不到**。
     * 原来的退出条件 `while (count(word) < 2)` 正是拿这件事当判据，于是在最后一对上读到
     * `count(word) == 0`（不是 2、也不是 1）、一头栽进「没配上」那一支，报出「提示只有 0 条」——
     * 红的是一个**判据本身看不见的现象**，不是产品错了。现在为最后一对单写一条断言
     * （`盖着 == 0`：盘面确实已经没了），跳出的理由从「假装看见了」换成「说得出是什么」。
     *
     * 同一次还补了一处**原来会漏掉的东西**：这一段里「翻错」那一支只有真的翻错过才走到，而
     * 「翻错只回一句话、点一下就盖回去」正是这条用例名字里承诺的事。所以末尾数了一下翻错的次数，
     * 一次都没翻错就判红——那个概率是 1/10395（见代码里的算法），是这条用例里唯一的非零余量。
     */
    @Test
    fun 记一记翻错盖回去配齐六对就收尾() {
        tap("玩一会儿")
        tap(Memory.game.name)
        tap(PlayVoice.startLabel)

        // 一进来就能看到要做什么；十二张牌一张字都没露。
        assertEquals(1, count("一次翻两张，一样的配成一对"))
        assertEquals(Memory.PAIRS * 2, count(Memory.BACK))
        for (word in CardWords.all) assertEquals("开局就把「$word」露出来了", 0, count(word))

        // 一块一块翻，直到六对都配上。已经配上的那些牌会一直亮着自己的字，所以下面认牌时要排掉。
        val solved = mutableListOf<String>()
        var 翻错几次 = 0
        repeat(Memory.PAIRS) {
            tapCell(Memory.BACK, 0)
            val word = CardWords.all.filterNot { it in solved }.single { count(it) > 0 }

            var tried = 0
            while (true) {
                tapCell(Memory.BACK, tried)
                if (count(word) == 2) break

                // 最后一对是唯一不能靠「两张都亮着」来确认的一对：它配上的**同一刻**这一局就收了，
                // 盘面立刻下屏换成结束卡，所以那两张脸同框这一步在屏幕上根本观察不到
                // （第 41 轮实测：这里读到 `盖着=0 露着=[]`，一度被误当成提示没出来）。
                // 不假装看见了它，改成正面确认「盘面已经没了」——这才是这一次跳出循环的真实原因。
                if (solved.size == Memory.PAIRS - 1) {
                    assertEquals("最后一对配上了，盘面却没下屏", 0, count(Memory.BACK))
                    break
                }

                // 不是一对：只回一句话，两张都还露着，盘面一步没动。
                assertEquals(1, count(PlayHint.MISSED_PAIR.text))

                // 点一下盖回去：两张一起回到牌背，那句话也收走。
                tapCell(Memory.BACK, 0)
                assertEquals("盖回去了那句话还在", 0, count(PlayHint.MISSED_PAIR.text))
                assertEquals("盖回去了牌面还露着", 0, count(word))

                // 重翻第一张，接着试下一张。
                tapCell(Memory.BACK, 0)
                assertEquals("重翻之后第一张没露出来", 1, count(word))
                tried++
                翻错几次++
            }
            solved += word
        }

        // 「翻错」那一支至少要真的走过一次，不然这一段就是白写的：只翻两张就收手的走法同样能过。
        // 但这件事**钉不死**——牌背着的时候，翻之前没有任何办法知道哪张不是搭档。六对里每对的
        // 第一张都正好是搭档的局真的存在，概率 (1/11)(1/9)(1/7)(1/5)(1/3) ≈ 1/10395，那一局
        // 这一条会红。这个余量写在这里、也写进 KDoc，不假装它是零。
        assertEquals("这一轮一次都没翻错，那句提示根本没被验到", true, 翻错几次 >= 1)

        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结果只说真的发生的事：六对都配上了。这一局没有计时，所以**不报用时**。
        assertEquals(1, countContaining("6 对都配上了"))
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
     * 第五个游戏：从选择页进去、点错一张、把目标类四张点齐、看到结果。
     *
     * 这一条要证明的是**第五种规则也没把界面逼出新东西**：它和前面几个共用同一套格子、
     * 同一句提示、同一张结束卡，只有题面和结果两句话不一样。它同样不计时，结束卡上照样
     * 一个用时数字都没有（对比舒尔特那条，五条用例读的是同一块屏幕）。
     *
     * 要找哪一类是洗出来的，所以这一局**只能在屏幕上认**：题面里写着类名，盘上那一类的词
     * 各有一张。点错那一张也靠屏幕自己认——从别的几类里挑一个恰好只出现一次的。
     * 类名不会出现在格子上（词表测试钉了这一点），所以「题面上出现的那一类」就是唯一判据。
     */
    @Test
    fun 找同类点错只说一句把那一类点齐就收尾() {
        tap("玩一会儿")
        tap(SameKind.game.name)
        tap(PlayVoice.startLabel)

        // 一进来就能看到要做什么：找哪一类、那一类有几个。类名只在题面上，格子上没有。
        val target = KindWords.kinds.single { countContaining(it.label) > 0 }
        val 题面 = "把「${target.label}」都点出来，一共 ${SameKind.PER_KIND} 个"
        assertEquals(1, count(题面))

        // 盘上按设计混着三类、每类的词各一张：目标类那四张得整整齐齐都在。
        val 目标词 = target.words.filter { count(it) == 1 }
        assertEquals("目标类没有整整齐齐摆在格子上", SameKind.PER_KIND, 目标词.size)
        val 别的词 = KindWords.kinds.filterNot { it === target }
            .flatMap { it.words }
            .filter { count(it) == 1 }
        assertEquals(
            "盘上少了别的类，这一局就没有混着摆",
            SameKind.PER_KIND * (SameKind.KINDS_ON_BOARD - 1),
            别的词.size,
        )

        // 点一张别的类：只回一句话，盘面一步没动。
        tap(别的词.first())
        assertEquals(1, count(PlayHint.NOT_THIS_ONE.text))
        assertEquals(1, count(题面))
        assertEquals("点错了却把一整局算成了点完", 0, countContaining("都点出来了"))

        // 点中第一张：这一局还没成——还有三张没点，而且刚才那句「不是它」被点对清掉了。
        tap(目标词[0])
        assertEquals(1, count(题面))
        assertEquals("点对了还留着上一句提示", 0, count(PlayHint.NOT_THIS_ONE.text))
        assertEquals("点错了却把一整局算成了点完", 0, countContaining("都点出来了"))

        // 再点同一张：只说一句「这个已经点过了」，已经点过的那张照样还能点。
        tap(目标词[0])
        assertEquals(1, count(PlayHint.ALREADY_DONE.text))

        // 点齐剩下三张：这一局到此为止。
        for (word in 目标词.drop(1)) tap(word)
        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结果只说真的发生的事：四个都点出来了。这一局没有计时，所以**不报用时**。
        assertEquals(1, countContaining("4 个都点出来了"))
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

        // 往回走：回到选择页，八个游戏都还在，而且并没有偷偷摆出一局。
        tap(PlayVoice.switchLabel)
        assertEquals(1, count(PlayVoice.chooseTitle))
        for (game in listOf(SchulteGrid.game, FindPair.game, SubtractSeven.game, Memory.game, SameKind.game, OddOne.game, ReverseGrid.game, OddEvenGrid.game)) {
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

    /**
     * 屏幕上每一格报出的框，单位 dp。
     *
     * 走 `getUnclippedBoundsInRoot` 而不是 `boundsInRoot`：后者是像素、而且会被祖先裁剪，
     * 屏外的格子会被读成零矩形（首页那一页第 63 轮之后就要改用前者，见 `HomeScreenTest`）。
     * 这一页的盘比视口高，量法不能挑错的。
     */
    private fun 量格子框(): List<DpRect> {
        val 格 = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo))
        val 个数 = 格.fetchSemanticsNodes().size
        return (0 until 个数).map { 格[it].getUnclippedBoundsInRoot() }
    }

    // `DpRect` 的 `width` / `height` 是 `androidx.compose.ui.unit` 里的扩展属性，
    // 少一个 import 就编不过；这里按第 80 轮那条用例的办法自己减差值。
    private fun 宽(b: DpRect) = b.right.value - b.left.value
    private fun 高(b: DpRect) = b.bottom.value - b.top.value

    /**
     * 格子的**几何**量一遍（第 82 轮）。
     *
     * 上面几条用例问的都是「屏幕上有没有这句话」，这一条换一条轴：格子摆出来之后，
     * 每一格到底多大、格子之间到底留了多少。这也是 `ElderDimens` 里两条下限第一次被放到
     * 真实版式里对账——[ElderDimens.minTouchTarget]（每格的下限）与
     * [ElderDimens.minTouchGap]（相邻可点元素之间的净间隙）。
     *
     * 两条下限在 5 列这一档上是**相撞**的：格子的净间隙取的是最窄那一档 [ElderDimens.gapXs]，
     * 而不是 16dp。这不是随手写的数——5 列时若改用 16dp 间隙，格宽会从 ≈71dp 掉到 ≈61dp，
     * 跌破 64dp 的下限。相撞时让的是**间隙**：16dp 那条来历（手指接触面比目标大）在格子上
     * 同样成立，但格子还是这一页**唯一的内容**，缩到点不中就没有第二处可看；而相邻格子的
     * 误触在一盘之内是可恢复的（点错只回一句话，步数不动）。这个取舍本身写在
     * [ElderDimens.minTouchGap] 的注释里，这里量的是它的后果。
     *
     * 让位那条写成**条件式**而不是绝对值：真机分辨率各不相同，本机这一次量到的是「装不下」，
     * 但在更宽的屏上两条下限可以同时满足，那时该断言的是另一件事。把绝对值写死会在平板上
     * 假红——一条按屏宽变的对账不该钉在一台机器的读数上。
     *
     * 量法自证（最左格的左边 = 最右格的右边 = 页边距）是这条用例的命门，不是礼貌：
     * 若坐标系取错，下面每个数都会整体平移，而**平移过的间隙照样等于 4dp**。
     * 只有边距落在如实的位置上，才说明量的是这一层。
     */
    @Test
    fun 舒尔特这一局每一格都够大而格子之间只有最窄那一档() {
        tap("玩一会儿")
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)

        val 五列 = 量格子框()
        assertEquals("5×5 的盘该有 25 个格子", 25, 五列.size)

        // 零矩形先排掉：它是「被祖先裁过」或「压根没渲出来」的样子。不排掉的话，
        // 拿两个零矩形去比间隙也会一路绿过去——那种绿是假的。
        val 尺寸读数 = 五列.joinToString(" ") { "${宽(it)}x${高(it)}" }
        assertTrue(
            "有格子的框是零矩形，下面的间隙全都不作数 [逐格 $尺寸读数]",
            五列.all { 宽(it) > 0f && 高(it) > 0f },
        )

        val 下限 = ElderDimens.minTouchTarget.value
        val 最窄格宽 = 五列.minOf { 宽(it) }
        val 最窄格高 = 五列.minOf { 高(it) }
        assertTrue(
            "每一格都得有下限那么宽 [最窄 ${最窄格宽}dp，下限 ${下限}dp]",
            最窄格宽 + 0.5f >= 下限,
        )
        assertTrue(
            "每一格都得有下限那么高 [最矮 ${最窄格高}dp，下限 ${下限}dp]",
            最窄格高 + 0.5f >= 下限,
        )

        // 按 (top, left) 还原成 5 行：盘面是洗出来的，版式不是，
        // 所以「同一行里有哪五格」这件事每次跑都该一样。
        val 五行 = 五列.sortedWith(compareBy({ it.top.value }, { it.left.value })).chunked(5)
        val 横向间隙 = 五行.flatMap { 行 -> 行.zipWithNext { a, b -> b.left.value - a.right.value } }
        val 纵向间隙 = 五行.zipWithNext { 上, 下 ->
            下.minOf { it.top.value } - 上.maxOf { it.bottom.value }
        }
        val 最窄横向间隙 = 横向间隙.min()
        val 最宽横向间隙 = 横向间隙.max()

        assertEquals(
            "同行相邻两格之间只该留最窄那一档 [实测 ${最窄横向间隙}~${最宽横向间隙}dp，" +
                "这一档 ${ElderDimens.gapXs.value}dp]",
            ElderDimens.gapXs.value.toFloat(),
            最窄横向间隙,
            1f,
        )
        assertEquals(
            "同一行里最松的那一处也是同一档：没有哪两格挨得更开 [实测 ${最宽横向间隙}dp]",
            ElderDimens.gapXs.value.toFloat(),
            最宽横向间隙,
            1f,
        )
        for (竖直 in 纵向间隙) {
            assertEquals(
                "上下相邻两行之间也该是最窄那一档 [实测 $竖直 dp，" +
                    "这一档 ${ElderDimens.gapXs.value}dp]",
                ElderDimens.gapXs.value.toFloat(),
                竖直,
                1f,
            )
        }

        // 量法自证：格子的左右边缘该正好落在页边距上。
        val 根宽 = 宽(compose.onRoot().getUnclippedBoundsInRoot())
        val 最左 = 五行.minOf { 行 -> 行.first().left.value }
        val 最右 = 五行.maxOf { 行 -> 行.last().right.value }
        assertEquals(
            "最左那一格的左边该落在页边距上 [实测 ${最左}dp，" +
                "页边距 ${ElderDimens.screenPadding.value}dp]",
            ElderDimens.screenPadding.value.toFloat(),
            最左,
            1f,
        )
        assertEquals(
            "最右那一格的右边也该落在页边距上 [实测 ${根宽 - 最右}dp，" +
                "页边距 ${ElderDimens.screenPadding.value}dp]",
            ElderDimens.screenPadding.value.toFloat(),
            根宽 - 最右,
            1f,
        )

        // 两条下限相撞时保的是哪一条：见上面那段「让位」。
        val 行宽 = 最右 - 最左
        val 两档下限 = 5 * 下限 + 4 * ElderDimens.minTouchGap.value
        val 分支 = if (行宽 < 两档下限) {
            assertTrue(
                "这一档装不下「5 个下限宽 + 4 个下限间隙」，让的必须是间隙而不是尺寸" +
                    " [行宽 ${行宽}dp < 两档下限 ${两档下限}dp，实测最窄间隙 ${最窄横向间隙}dp，" +
                    "下限 ${ElderDimens.minTouchGap.value}dp]",
                最窄横向间隙 < ElderDimens.minTouchGap.value - 0.5f,
            )
            "窄屏：让间隙"
        } else {
            "宽屏：两档都满足，这一条不适用"
        }

        // 交叉比对：换一档版式再量一次。读数该跟着版式走，不是一个常数。
        tapBack()
        tap("玩一会儿")
        tap(FindPair.game.name)
        tap(PlayVoice.startLabel)
        val 四列 = 量格子框()
        assertEquals("4×4 的盘该有 16 个格子", 16, 四列.size)
        val 四列最窄格宽 = 四列.minOf { 宽(it) }
        assertTrue(
            "行数少一档，格子就该宽一截 [4 列最窄 ${四列最窄格宽}dp，5 列最窄 ${最窄格宽}dp]",
            四列最窄格宽 > 最窄格宽 + 0.5f,
        )

        println(
            "[play-grid-geometry] 5 列：最窄格 ${最窄格宽}x${最窄格高}dp " +
                "横向间隙 ${最窄横向间隙}~${最宽横向间隙}dp 纵向间隙 $纵向间隙 " +
                "行宽 ${行宽}dp 根宽 ${根宽}dp 页边距 ${ElderDimens.screenPadding.value}dp " +
                "两档下限 ${两档下限}dp 分支「$分支」 | " +
                "4 列：最窄格宽 ${四列最窄格宽}dp",
        )
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
