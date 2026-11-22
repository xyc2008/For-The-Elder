package com.fortheelder.ui.a11y

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.home.HomeVoice
import com.fortheelder.ui.nav.MainNav
import com.fortheelder.ui.onboard.OnboardVoice
import com.fortheelder.ui.onboard.OnboardingScreen
import com.fortheelder.ui.play.PlayVoice
import com.fortheelder.ui.settings.SettingsVoice
import com.fortheelder.ui.talk.TalkVoice
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 六个真实屏的**溢出期望表**（第 53 轮）。
 *
 * ## 这一轮要治的病
 *
 * 第 51 轮给 `ElderScreen` 加了滚动兜底提示（[com.fortheelder.ui.common.ElderScreen] 里的
 * `ScrollMoreHint`），第 52 轮用反向变异证明了装机走查那两条判据有牙齿。但第 52 轮同时暴露出
 * 一处**空格**：走查的登记表里 `home` / `play` 两格记的是 `0/0`——没看到提示、短滑之后内容
 * 也没动。这两格**没有独立真值**：`uiautomator dump` 出来的节点树上，能看到的只有「提示在不在」
 * 与「内容动没动」，而「这一屏**本该**不该出提示」问的是**内容高度与视口高度的关系**，
 * 那件事在 dump 里没有任何一格记着（第 52 轮实测：装机后 `uiautomator dump` 里 24 个节点
 * 全是 `scrollable="false"`，A 态 25 个也全是 false——平台层给不出独立于滚动的溢出信号）。
 *
 * 所以 `0/0` 有两种读法都成立：「内容放得下，本来就不该有提示」（好），
 * 与「提示坏了，一直没出来」（坏）。第 52 轮的 MUT-A（把判定改成常显）让这两屏红了，
 * **间接**说明是前者；但那是从一个**假的**产品态倒推真态该是什么样，不是直接量。
 *
 * ## 这一轮的办法
 *
 * 换一层去量：在**进程内**读 Compose 的语义树。`Modifier.verticalScroll` 会给滚动容器挂上
 * `SemanticsProperties.VerticalScrollAxisRange`，而它的两个量都是**活lambda**——
 * 反编译 `foundation-android:1.7.5` 的 `ScrollSemanticsModifierNode` 查实：
 * `value` 那个 lambda 调的是 `ScrollState.getValue()`、`maxValue` 那个调的是
 * `ScrollState.getMaxValue()`（`applySemantics$accessibilityScrollState$1` / `$2`），
 * 读取时才求值。于是「内容真的溢出吗」= `maxValue() > value()`，与 `ElderScreen` 内部
 * 那条 `scroll.canScrollForward` 是**两条不同的读取路径**——一条走组件持有的 `ScrollState`
 * 属性，一条走语义树里挂着的 lambda。产品那条判定被改动（第 52 轮的两种变异）时，
 * 这里量到的真值**不受影响**，所以两边一对就有牙齿。
 *
 * **这条独立性的边界要说清**（这是本组用例最容易被高估的地方）：两条路径读的**是同一个
 * `ScrollState` 对象**。所以它能抓的是「判定表达式写错」，抓不了「`ScrollState` 自己算错」
 * ——后者是 framework 的事，本组用例不作任何宣称。这不是全面的独立性，是**够用的**独立性：
 * 本轮要钉的那件事（六个真屏上提示出不出，与内容溢不溢出是否一致）恰好落在它盖住的范围内。
 *
 * 与 [ScrollMoreHintTest] 的分工：[ScrollMoreHintTest] 用**自造屏**问「判定本身对不对」
 * （40 行长内容 / 1 行短内容，把输入摆成确定的）；这一组用**真屏**问「六屏上判定跑出来的
 * 结果，与我在语义树里量到的溢出是否一致」。两边都不再互相「假称」——这正是第 51 轮那份
 * 类注释里「不在这组用例里假称」那句话的兑现。
 *
 * ## 摆屏必须带上应用根那层 inset 处理（第 53 轮踩过的坑）
 *
 * 第一条绿线在这里翻过车：本组用例最初直接 `ForTheElderTheme { MainNav(…) }`，而真应用走的是
 * [com.fortheelder.ui.AppRoot]，它外面套着 `Surface(fillMaxSize().safeDrawingPadding())`。
 * 少这一层，**测试宿主的可用高度比真应用高出一整个系统栏**，于是同一屏在测试里「放得下」、
 * 在真机上「溢出」——第 53 轮实测的对照就是这个：真机上 onboard1 看得见提示，而测试宿主量到
 * `maxValue == value()`。本机（1080x2400 / density 420）状态栏与刘海都是 128 px，
 * 真机 dump 里 onboard1 首行文字的 `top` 是 149 px、正好等于 128 + 屏内 8 dp 的 `gapSm`。
 * 所以 [从首页起步] / [从引导起步] 都套上 `Surface(fillMaxSize().safeDrawingPadding())`，
 * 与 [com.fortheelder.ui.AppRoot] 对齐；**这是补上测试的保真度，不是把期望改成迁就测量**。
 *
 * ## 期望表逐行的真值从哪来
 *
 * 六个屏名与顺序照抄走查登记表（`build/r51/probe51.py` 的
 * `hint-onboard1` / `hint-onboard2` / `hint-home` / `hint-play` / `hint-talk` / `hint-settings`），
 * 不自编。每行的「期望溢出」各有来路，写在 [期望行.真值来源] 里，都是一句话能核的：
 *
 * - `onboard1` / `onboard2` / `talk` / `settings`：第 52 轮登记表记的提示节点数都是 1
 *   （`build/r52/probe51_pristine.txt` 的 `:17-22` 六行），也就是走查**看到**了提示。
 * - `home` / `play`：登记表记的是 0。这一行的真值**不**来自登记表（它就是那个待定的空格），
 *   来自第 52 轮的反向变异：把判定改成常显（MUT-A）之后，这两屏的走查用例红了
 *   ——**提示本来就不该在**。本组用例第一次对这两格给出**直接**量到的真值。
 *
 * ## 这一组用例证明不了的事
 *
 * 1. 它量的是**这一台设备**的版式（1080x2400、density 420）、**应用内 `STANDARD` 字号档**。
 *    系统字号档、应用内「更大」档下六屏各自的溢出与提示，本组用例一格都没量，也不作宣称。
 * 2. 它读的是 Compose 语义树，不是 `uiautomator` 那棵平台树。走查那条 `0/0` 的空格
 *    **在本组用例里被填上了**，但走查自己那一格仍是 `0/0`——两边是两层证据，不互相替代。
 * 3. 「提示出不出」与「老人因此少漏看内容」是两件事。后者要用户测试，本轮没有。
 */
@RunWith(AndroidJUnit4::class)
class ScrollOverflowExpectationTest {

    /**
     * 它自己不挂 `@get:Rule`：由下面的 `rules` 包住，收尾顺序才由那条链说了算。
     * 这里要渲染 [MainNav] 与 [OnboardingScreen]，两者都要一个真的 container，
     * 所以收尾规则照 `A11ySemanticsTest` 那一套来。
     */
    private val compose = createComposeRule()

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
    }

    // ---- 摆屏：图上应用根那两层（主题 + inset），只把「引导闸门」换成显式选屏 ----

    /** 首页起步，之后靠点入口换屏——`MainNav` 的起点就是首页（见它的 route 表）。 */
    private fun 从首页起步() {
        compose.setContent {
            ForTheElderTheme(fontSize = FontSize.STANDARD) {
                AppRootLayers { MainNav(container = container) }
            }
        }
    }

    /** 引导页直接渲染 [OnboardingScreen]：`MainNav` 里够不着它（见 `AppRoot` 的闸门说明）。 */
    private fun 从引导起步() {
        compose.setContent {
            ForTheElderTheme(fontSize = FontSize.STANDARD) {
                AppRootLayers {
                    OnboardingScreen(
                        container = container,
                        initialAddress = null,
                        initialFontSize = FontSize.STANDARD,
                    )
                }
            }
        }
    }

    /**
     * [com.fortheelder.ui.AppRoot] 里包着内容的那两层：`Surface(fillMaxSize().safeDrawingPadding())`。
     * 逐字照抄它的 modifier 链，只省掉 `AppContainer` 与引导闸门——理由见类注释里那一节。
     *
     * 这个名字必须保持 PascalCase：lint 的 `ComposableNaming` 要求返回 `Unit` 的可组合函数首字母大写。
     * 本文件其它助手都是中文名，唯独可组合的这一个不行（第 54 轮改名，不用 `@Suppress` 抹平）。
     */
    @androidx.compose.runtime.Composable
    private fun AppRootLayers(内容: @androidx.compose.runtime.Composable () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
            content = 内容,
        )
    }

    private fun 有几处(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun 点(text: String) {
        compose.waitUntil(超时毫秒) { 有几处(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    /**
     * 点一个**钉在页面底部**的动作（引导页的「我看看怎么用」/「下一步」）。
     * 它落在 [com.fortheelder.ui.common.ElderScreen] 的 `footer` 里、在滚动容器之外，
     * 所以不做 `performScrollTo`，改成要求它**真的显示在屏幕上**。理由见 `A11ySemanticsTest` 里的同名助手。
     */
    private fun 点钉底(text: String) {
        compose.waitUntil(超时毫秒) { 有几处(text) > 0 }
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    private fun 点返回() {
        compose.onNodeWithContentDescription(CommonVoice.back).performClick()
    }

    // ---- 量账：写进应用自己的内部目录，跑完用 run-as 取走 ----

    /**
     * 这一轮的量账：一行一屏，append 到 `targetContext.filesDir/$量账文件`。
     *
     * 两条被实测堵掉的路，记在这儿免得下一轮再走一遍：
     * 1. **`println` 不行**：`connectedAndroidTest` 写出来的 JUnit 报告里没有 `<system-out>` 段
     *    ——第 53 轮实测，报告 XML 通篇只有 `<failure>`，通过用例打出来的行一行都没留下。
     * 2. **`UiAutomation.executeShellCommand` 也不行**：它不经 shell 解析，`echo x >> 文件`
     *    这类重定向不生效（第 53 轮实测：写前写后 `wc -c` 都读成空，量账一个字没落盘）。
     *
     * 还有一条**环境事实**决定了取账方式：`:app:connectedDebugAndroidTest` 跑完会把被测应用
     * 与测试应用**一起卸载**（第 53 轮实测：跑完 `pm list packages -3` 是空的），应用自己的目录
     * 随之消失。所以量账不走 Gradle 任务取，而是**手工**跑
     * `adb shell am instrument -w …`（不经 Gradle、不触发卸载），再用
     * `adb exec-out run-as <包名> cat files/$量账文件` 取走。Gradle 那条路只用来判「这一轮绿不绿」。
     *
     * 写完立刻回读长度核对：**写不进去要当场红**，不能等取账时才发现是空的——
     * 那是第 52 轮那条教训（B 态下「到底后提示自己消失」判据空过、照样打 OK）的直接应用。
     * 这条守卫第 53 轮真的响过一次（那次是 shell 通道写不进去），不是摆设。
     */
    private fun 记一笔(文本: String) {
        val 文件 = File(context.filesDir, 量账文件)
        val 前 = if (文件.exists()) 文件.length() else -1L
        文件.appendText(文本 + "\n")
        val 后 = 文件.length()
        if (后 <= 前) {
            throw AssertionError("量账没写进 ${文件.absolutePath}（写前 $前 字节，写后 $后 字节），这一轮的数取不出来")
        }
    }

    /** 一处不符记一处，别在第一处就停下——一轮跑完得能把六屏的表看全。 */
    private val 不符 = mutableListOf<String>()

    @After
    fun 报账() {
        assertTrue(
            "第 53 轮溢出期望表有 ${不符.size} 处不符：\n" + 不符.joinToString("\n"),
            不符.isEmpty(),
        )
    }

    /**
     * 这一屏的滚动区到底溢出了没有。**取不到真值就当场红**，绝不静默返回一个默认值——
     * 那是第 52 轮那条教训的直接应用（B 态下「到底后提示自己消失」那条判据空过、照样打 OK）。
     */
    private fun 滚动溢出真值(屏: String): Boolean {
        compose.waitForIdle()

        val 滚动区 = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
        if (滚动区.size != 1) {
            throw AssertionError(
                "$屏：带纵向滚动语义的节点有 ${滚动区.size} 个，期望正好 1 个 —— " +
                    "这一屏的溢出真值取不到，期望表这一格什么都没测（见 ScrollOverflowExpectationTest.kt）",
            )
        }
        val 滚动节点 = 滚动区.single()
        val 量程 = 滚动节点.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?: throw AssertionError(
                "$屏：节点有这个键、却读不出量程，说明读取方式跟 compose-ui 的版本漂了（见 ScrollOverflowExpectationTest.kt）",
            )

        val 最大 = 量程.maxValue()
        val 当前 = 量程.value()
        val 区 = 滚动节点.boundsInRoot
        val 根 = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // 文字节点的版式范围（px，未裁剪的版式位置）。拿它跟滚动区的上下沿一比，就知道差异在哪：
        // 是视口矮了、还是内容高了。第 53 轮 onboard1 在测试宿主里量到「不溢出」，而真 app 上
        // 看得见提示——这几格就是用来把这个不一致钉到具体一项上的。
        val 文字节点 = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
        val 内容顶 = 文字节点.minOfOrNull { it.boundsInRoot.top } ?: -1f
        val 内容底 = 文字节点.maxOfOrNull { it.boundsInRoot.bottom } ?: -1f
        val 提示数 = 有几处(CommonVoice.scrollMore)

        val 配置 = context.resources.configuration
        val 度量 = context.resources.displayMetrics

        // 先记账再断言：断言一红，这一屏量到的数也得留在文件里，不然红完什么都查不到。
        记一笔(
            "$屏 溢出=${最大 > 当前} maxValue=$最大 value=$当前 提示=$提示数 " +
                "滚动区[${区.left},${区.top}][${区.right},${区.bottom}] 区高=${区.height} " +
                "内容顶=$内容顶 内容底=$内容底 文字节点=${文字节点.size} " +
                "根[${根.left},${根.top}][${根.right},${根.bottom}] 根高=${根.height} " +
                "fontScale=${配置.fontScale} density=${度量.density} scaledDensity=${度量.scaledDensity} " +
                "densityDpi=${配置.densityDpi} 屏高dp=${配置.screenHeightDp} 屏宽dp=${配置.screenWidthDp}",
        )

        // `ScrollState` 的 maxValue 初值是 Int.MAX_VALUE，要等第一次布局量完才落回真值
        // （第 51 轮反编译 1.7.5 的构造器确认过，也是 `ElderScreen` 那条判定带 viewportSize 门槛的原因）。
        // 读到那个初值就说明这一屏还没量过版式，此时「溢出」这个判断根本没有输入。
        assertTrue(
            "$屏：量程 maxValue=$最大 还是 ScrollState 的初值量级（Int.MAX_VALUE），" +
                "说明第一次布局还没量完——这一格什么都没测，不许当成「溢出了」",
            最大 < 初值哨兵,
        )

        return 最大 > 当前
    }

    private data class 期望行(
        /** 屏名，与走查登记表（`build/r51/probe51.py`）用的六个名字逐字一致。 */
        val 屏: String,
        /** 这一屏**本该**不该溢出。真值的来路见下一行。 */
        val 期望溢出: Boolean,
        /** 这个期望从哪来的一句话，都是能照着核的。 */
        val 真值来源: String,
        /** 这一屏起手就该看得见的文案：它不在，说明屏根本没换过来，后面的判定也不作数。 */
        val 必须出现的文案: String,
    )

    /**
     * 期望表一行的核对：先坐实屏换过来了，再量真值、再对提示。
     *
     * 三条断言分别管三件事，缺一条这一格就变成空过：
     * 1. `必须出现的文案` 在 —— 屏真的换过来了（否则量的是上一屏）；
     * 2. 量到的溢出 = 登记的期望 —— 这一行的期望写对了（不对说明是我记错，不是产品变了）；
     * 3. 提示数 = 溢出 ? 1 : 0 —— **产品这条判定与真值一致**，这才是本轮要钉的那件事。
     */
    private fun 核对(行: 期望行) {
        compose.waitUntil(超时毫秒) { 有几处(行.必须出现的文案) > 0 }
        val 真值 = 滚动溢出真值(行.屏)
        // 不符的不当场抛：一处就抛，一轮跑完只能看见第一处。收进 `不符`，`报账` 里一并报。
        if (行.期望溢出 != 真值) {
            不符 += "${行.屏}：语义树量到的溢出（$真值）与期望表登记的（${行.期望溢出}）不一致。" +
                "这一行的真值来源写的是「${行.真值来源}」——先核那一句，别急着改产品"
        }
        val 提示数 = 有几处(CommonVoice.scrollMore)
        if (提示数 != if (真值) 1 else 0) {
            不符 += "${行.屏}：提示出现与否和「内容真的溢出」不一致（溢出=$真值，提示=$提示数）。" +
                "提示应当**恰好在溢出时**出现一次、放得下时一次都不出现"
        }
    }

    // ---- 六屏，两轮走完 ----

    @Test
    fun 引导两页的提示与内容是否真的溢出对得上() {
        从引导起步()

        // onboard2 也要核对：上一版在 onboard1 一处不符就整例红掉，onboard2 **从未被量过**。
        // 现在 `核对` 不抛、只记账，一轮跑完六屏全都能落到账本里。
        核对(
            期望行(
                屏 = "onboard1",
                期望溢出 = true,
                真值来源 = "第 52 轮走查登记表：这一屏量到 1 个提示节点（build/r52/probe51_pristine.txt 的 :17-22）",
                必须出现的文案 = CommonVoice.appTitle,
            ),
        )

        点钉底(OnboardVoice.nextButton)

        核对(
            期望行(
                屏 = "onboard2",
                期望溢出 = true,
                真值来源 = "第 52 轮走查登记表：这一屏量到 1 个提示节点；同轮的整屏走查也记过它的内容落到屏幕外",
                必须出现的文案 = CommonVoice.addressQuestion,
            ),
        )
    }

    @Test
    fun 首页对话页玩页设置页的提示与内容是否真的溢出对得上() {
        从首页起步()
        compose.waitUntil(超时毫秒) { 有几处(HomeVoice.talkAction) > 0 }

        // 首页这一格是走查里那个 `0/0` 空格的一半。期望「不溢出」的来路不是登记表
        // （登记表只能告诉你「没看到提示」），而是第 52 轮的反向变异：
        // 强制常显之后这一屏的走查用例红了 ⇒ 提示本来就不该在。
        核对(
            期望行(
                屏 = "home",
                期望溢出 = false,
                真值来源 = "第 52 轮反向变异 MUT-A（判定改成常显）后这一屏的走查用例变红 ⇒ 内容放得下、本来就不该有提示",
                必须出现的文案 = HomeVoice.talkAction,
            ),
        )

        点(HomeVoice.talkAction)
        核对(
            期望行(
                屏 = "talk",
                期望溢出 = true,
                真值来源 = "第 52 轮走查登记表：这一屏量到 1 个提示节点",
                必须出现的文案 = TalkVoice.pageTitle,
            ),
        )
        点返回()

        点(HomeVoice.playAction)
        核对(
            期望行(
                屏 = "play",
                期望溢出 = false,
                真值来源 = "第 52 轮反向变异 MUT-A 后这一屏的走查用例变红 ⇒ 内容放得下、本来就不该有提示",
                必须出现的文案 = PlayVoice.title,
            ),
        )
        点返回()

        点(HomeVoice.settingsAction)
        核对(
            期望行(
                屏 = "settings",
                期望溢出 = true,
                真值来源 = "第 52 轮走查登记表：这一屏量到 1 个提示节点",
                必须出现的文案 = SettingsVoice.title,
            ),
        )
    }

    private companion object {
        /** 与同包其它屏幕级用例取同一个量级：等动画收尾，不等环境慢。 */
        const val 超时毫秒 = 5_000L

        /**
         * `maxValue` 读到这个量级就是 `Int.MAX_VALUE`（2147483647）那个初值。
         * 真值不会接近这个数：本机屏高 2400 px，任何一屏的 maxValue 顶天几千。
         */
        const val 初值哨兵 = 1_000_000f

        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-scrolloverflow.db"

        /**
         * 六屏量账落在应用内部目录的这个文件名下，跑完用
         * `adb exec-out run-as <包名> cat files/$量账文件` 取走；不入库（它在设备上，不在仓库里）。
         */
        const val 量账文件 = "scrolloverflow53.txt"
    }
}
