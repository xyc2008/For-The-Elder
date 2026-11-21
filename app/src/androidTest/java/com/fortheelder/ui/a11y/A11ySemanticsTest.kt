package com.fortheelder.ui.a11y

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.domain.play.FindPair
import com.fortheelder.domain.play.PlayHint
import com.fortheelder.domain.play.SchulteGrid
import com.fortheelder.domain.play.SubtractSeven
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.home.HomeVoice
import com.fortheelder.ui.nav.MainNav
import com.fortheelder.ui.onboard.OnboardVoice
import com.fortheelder.ui.onboard.OnboardingScreen
import com.fortheelder.ui.play.PlayVoice
import com.fortheelder.ui.settings.SettingsVoice
import com.fortheelder.ui.talk.TalkVoice
import com.fortheelder.ui.theme.ForTheElderTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 无障碍语义的**渲染级**验证（第 18 轮，关闭 `docs/01` 的 GAP-05）。
 *
 * 屏幕朗读（TalkBack）读的不是像素，是 Compose 的语义树。所以「这一轮加的语义标签到底
 * 有没有生效」这件事，可以在设备上直接读那棵树来证实，不需要人去戴着耳机听——
 * 那是另一件事（见下面 `平台层` 那条以及 `records/04` 里对此的说明）。
 *
 * 这一组用例要钉住四件事：
 *
 * 1. 每一屏的表头都被标成了**标题**，而且引导三页各自报得出「第几页，共几页」；
 * 2. 三个玩法的格子都报得出自己在第几行第几列，整张盘也报得出几行几列——算一算
 *    一次只问一道题，盘上是 1 行 3 列；找一样的 4×4；舒尔特 5×5。每张盘上「行列组合
 *    恰好铺满」都不依赖洗牌结果，洗成什么样都要成立；
 * 3. 题面、提示、结束语这三处「原地换字」的地方是播报区，而且**只有**这三处是；
 * 4. 这些语义真的传到了平台层——那是 TalkBack 消费的那一层，Compose 语义树对得上
 *    不代表这一层也对得上。
 *
 * 四个页面用的都是 `MainNav`，与用户自己走的路一致；引导页直接渲染 `OnboardingScreen`，
 * 因为 `MainNav` 的起点是首页（见 `MainNav` 的 route 表）。
 */
@RunWith(AndroidJUnit4::class)
class A11ySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 这个用例自己的容器与库文件：应用那份 `elder.db` 不归它碰。 */
    private lateinit var container: AppContainer

    @Before
    fun 用一个空库起步() {
        context.deleteDatabase(TEST_DB)
        container = AppContainer(context, TEST_DB)
    }

    @After
    fun 关掉并删掉测试库() {
        container.database.close()
        context.deleteDatabase(TEST_DB)
    }

    // ---- 走查用的几个小工具，和 PlayScreenTest 里那套同样硬碰硬 ----

    private fun startApp() {
        compose.setContent { ForTheElderTheme { MainNav(container = container) } }
    }

    private fun startOnboarding() {
        compose.setContent {
            ForTheElderTheme {
                OnboardingScreen(
                    container = container,
                    initialAddress = null,
                    initialFontSize = FontSize.STANDARD,
                )
            }
        }
    }

    private fun count(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun tap(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun tapBack() {
        compose.onNodeWithContentDescription(CommonVoice.back).performClick()
    }

    /** 一个语义属性的值。取不到就是 null——和「值是 null」在这次验证里不作区分，都不算有。 */
    private fun <T> valueOf(
        node: androidx.compose.ui.semantics.SemanticsNode,
        key: androidx.compose.ui.semantics.SemanticsPropertyKey<T>,
    ): T? = node.config.getOrNull(key)

    /** 被标成标题的节点上写着什么字。一个字都读不到的标题也算一个标题（返回空串）。 */
    private fun headingTexts(): List<String> = compose
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        .fetchSemanticsNodes()
        .map { node ->
            valueOf(node, SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        }

    /** 整棵语义树里有几个节点满足这个条件。 */
    private fun countMatching(matcher: SemanticsMatcher): Int =
        compose.onAllNodes(matcher).fetchSemanticsNodes().size

    // ---- 1. 每屏表头都是标题 ----

    @Test
    fun 引导三页的表头是标题而且报得出第几页() {
        startOnboarding()

        // 第一页。
        compose.onNodeWithText(CommonVoice.appTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    OnboardVoice.pageIndicator(1, PAGE_COUNT),
                ),
            )

        // 第二页。表头是「怎么称呼您」，页码也跟着走。
        tap(OnboardVoice.nextButton)
        compose.onNodeWithText(CommonVoice.addressQuestion)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    OnboardVoice.pageIndicator(2, PAGE_COUNT),
                ),
            )

        // 第三页。
        tap(OnboardVoice.nextStep)
        compose.onNodeWithText(OnboardVoice.page3Title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    OnboardVoice.pageIndicator(PAGE_COUNT, PAGE_COUNT),
                ),
            )
    }

    @Test
    fun 四个页面的表头都是标题() {
        startApp()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) > 0 }

        assertTrue("首页表头不是标题：${headingTexts()}", headingTexts().contains(CommonVoice.appTitle))

        tap(HomeVoice.talkAction)
        assertTrue("对话页表头不是标题：${headingTexts()}", headingTexts().contains(TalkVoice.pageTitle))
        tapBack()

        tap(HomeVoice.playAction)
        assertTrue("游戏页表头不是标题：${headingTexts()}", headingTexts().contains(PlayVoice.title))
        tapBack()

        // 首页与设置页的表头都是「设置」这两个字，所以按「设置页的小节标题也在标题集里」
        // 来确认这一屏真的换了——只看「设置」在不在，首页那个入口行也能凑合对上。
        tap(HomeVoice.settingsAction)
        val settingsHeadings = headingTexts()
        assertTrue("设置页表头不是标题：$settingsHeadings", settingsHeadings.contains(SettingsVoice.title))
        assertTrue(
            "设置页的小节标题一个都不是标题（换屏可能没换成）：$settingsHeadings",
            settingsHeadings.size > 1,
        )
    }

    // ---- 2. 格子的行列 ----

    @Test
    fun 格子报得出自己在第几行第几列且铺满整张盘() {
        startApp()
        tap(HomeVoice.playAction)
        tap(SubtractSeven.game.name)
        tap(PlayVoice.startLabel)

        // 算一算一次只问一道，所以盘上只有 3 个格子，1 行 3 列。
        // （这里原来写成 3 行是错的：SubtractSeven 的 cells 是一道题的三个选项，不是九宫格。
        // 设备上按 3 行去数，一个都数不到——数不到的是这个假设，不是语义没挂。）
        assertGrid(columns = SubtractSeven.COLUMNS, rows = 1)

        tapBack() // 回到首页
        tap(HomeVoice.playAction)
        tap(FindPair.game.name)
        tap(PlayVoice.startLabel)
        assertGrid(columns = 4, rows = 4)

        tapBack() // 回到首页
        tap(HomeVoice.playAction)
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)
        assertGrid(columns = 5, rows = 5)
    }

    /**
     * 这张盘报的是 rows × columns，而且每一格的行列组合**一个不重不漏**。
     *
     * 不去查「写着 1 的那一格在第几行」：盘面是洗出来的，哪种摆法都对，只有「铺满」
     * 这件事与洗牌无关，所以这条断言在任何一局上都成立。
     */
    private fun assertGrid(rows: Int, columns: Int) {
        // 这里**不能**用 expectValue(CollectionInfo(rows, columns))：CollectionInfo 没有重写
        // equals/hashCode（javap 查过 ui-android 1.7.5 的 classes.jar），expectValue 拿新构造的
        // 实例去比是身份比较，永远为 false —— 那样的断言是「永远红」而不是「永远绿」。
        // 所以按字段读值：读得出来的行数列数对了，才算挂对了。
        val grid = SemanticsMatcher("集合信息是 $rows 行 $columns 列") { node ->
            val info = node.config.getOrNull(SemanticsProperties.CollectionInfo)
            info != null && info.rowCount == rows && info.columnCount == columns
        }
        assertEquals("$rows×$columns 的盘没有（或多于）一个：语义挂错了层", 1, countMatching(grid))

        val cells = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo))
            .fetchSemanticsNodes()
        assertEquals("$rows×$columns 的格子数不对", rows * columns, cells.size)

        val pairs = cells.map { node ->
            val info = valueOf(node, SemanticsProperties.CollectionItemInfo)
            checkNotNull(info) { "拿了格子列表却又读不到行列" }
            info.rowIndex to info.columnIndex
        }
        val expected = (0 until rows).flatMap { r -> (0 until columns).map { c -> r to c } }.toSet()
        assertEquals("行列组合没有铺满整张盘", expected, pairs.toSet())
    }

    // ---- 3. 播报区 ----

    @Test
    fun 题面提示和结束语是播报区而别处不是() {
        startApp()
        tap(HomeVoice.playAction)
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)

        // 题面与提示两行都在，而且都是播报区。提示这会儿是空的，但位置一直占着。
        assertEquals("题面没能报出来", 1, count("现在点 1"))
        assertEquals("玩的时候应当正好有两处播报区", 2, countMatching(politeLiveRegion()))

        // 点错之后提示那行有了字，播报区数不变——变的是字，不是结构。
        tap("25")
        assertEquals("点错没说话", 1, count(PlayHint.NOT_YET.text))
        assertEquals("点错把播报区结构改了", 2, countMatching(politeLiveRegion()))

        for (n in 1..25) tap(n.toString())
        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.leaveLabel) > 0 }

        // 结束卡上只剩结束语一处。
        assertEquals("结束语不是播报区", 1, countMatching(politeLiveRegion()))
    }

    private fun politeLiveRegion(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    // ---- 4. 平台层探针 ----

    /**
     * 把语义树读到**平台层**——`AccessibilityNodeInfo` 那一层，TalkBack 真正消费的东西。
     *
     * 这一条**不断言朗读声**：模拟器上没有可听的声音，本轮也没有人真的听过（见 `records/04`）。
     * 它断言的是更窄、但可以硬碰硬的一件事：这一轮加的三种语义，在平台层的节点上
     * 都变成了对应的属性。Compose 语义树里对得上、平台层丢掉，是现实中会发生的
     * （映射表漏了一项就是），所以这两层要各验一次。
     */
    @Test
    fun 平台层无障碍树里也看得到标题和格子的行列() {
        startApp()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) > 0 }

        val home = platformTree()
        assertTrue(
            "平台层看不到任何标题节点",
            home.any { it.isHeadingCompat() },
        )
        assertTrue(
            "平台层看不到首页表头这个标题",
            home.any { it.isHeadingCompat() && it.text?.toString() == CommonVoice.appTitle },
        )

        // 换到玩一会儿，看格子的行列有没有过这一层。
        tap(HomeVoice.playAction)
        tap(SchulteGrid.game.name)
        tap(PlayVoice.startLabel)
        assertEquals("准备阶段还没摆好格子", 1, count("现在点 1"))

        val playing = platformTree()
        val cellPairs = playing.mapNotNull { node ->
            node.collectionItemInfo?.let { it.rowIndex to it.columnIndex }
        }
        assertEquals("平台层看到的格子数不对", 25, cellPairs.size)
        assertEquals(
            "平台层的行列组合没有铺满整张盘",
            (0 until 5).flatMap { r -> (0 until 5).map { c -> r to c } }.toSet(),
            cellPairs.toSet(),
        )

        // 页码是挂在标题上的状态说明，只在引导页有；顺手在首页那一批里确认它没被到处乱挂。
        assertTrue(
            "首页上不该有引导页码这种状态说明",
            home.none { it.stateDescription?.toString().orEmpty().contains("第") },
        )
    }

    private fun AccessibilityNodeInfo.isHeadingCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isHeading

    /**
     * 拿活动窗口的根节点，连子孙一起摊平。
     *
     * 要等：`createComposeRule` 起的 Activity 与 UiAutomation 之间没有同步点，
     * 第一次问经常还没有窗口。等 20 次（约 5 秒）拿不到就直接失败——本次验证的前提
     * 不存在的时候，用例必须响，而不是悄悄跳过装作验过了。
     *
     * 不调 `recycle()`：它在 API 33 起已经是空实现，而本机是 API 35。
     */
    private fun platformTree(): List<AccessibilityNodeInfo> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var root: AccessibilityNodeInfo? = null
        for (attempt in 1..PLATFORM_ATTEMPTS) {
            root = automation.rootInActiveWindow
            if (root != null) break
            Thread.sleep(PLATFORM_POLL_MILLIS)
        }
        val start = checkNotNull(root) {
            "问了 $PLATFORM_ATTEMPTS 次都没拿到活动窗口的根节点，平台层无法验证"
        }

        val flat = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            flat += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::walk)
            }
        }
        walk(start)
        return flat
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val PLATFORM_ATTEMPTS = 20
        const val PLATFORM_POLL_MILLIS = 250L

        const val PAGE_COUNT = 3

        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-a11y.db"
    }
}
