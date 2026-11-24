package com.fortheelder.ui.home

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.core.time.AppClock
import com.fortheelder.ui.AppRoot
import com.fortheelder.ui.theme.ElderDimens
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import java.time.LocalDateTime
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
 * 首页的**渲染级**验证：顶上那张卡说什么（AC-13.1 里「问候语里出现用户设的称呼」那一半），
 * 以及两条主干入口渲染出来之后的位置与尺寸（第 81 轮起，见下面第 4 条）。
 *
 * 这是 `HomeViewModelTest` 类注释里「这里验的**不是界面长得对不对**（那是 `HomeScreenTest`
 * 的活）」欠下的账——那句话从第 26 轮起就挂在那里，而那个类一直不存在。补上它的前置条件是
 * **屏幕级能固定「现在」**：第 31 轮之前 `AppContainer.clock` 写死成系统钟，而「说什么」同时
 * 取决于钟点（深夜换安静语）与节流状态，屏幕级就只能断言「那句话不是空的」这种弱条件。
 * 第 31 轮 `AppContainer` 收下了 `clock` 参数（见 ADR-029），这里立刻把它兑现：
 *
 * 1. `早上九点首页那句话带刚存下的称呼` 与 `换个钟点那句话就跟着换` 两例**只差时钟**，断言的
 *    是两句不同的话。这是「那句话不是写死的字面量」最直接的证据；
 * 2. `深夜首页不迎上来说话也不冒出称呼` 落在另一支判定上（安静语），证明钟点本身就够决定
 *    说不说、说什么；
 * 3. `还没填称呼时首页问一次而且那句话不带名字` 钉住没给称呼时首页的样子；
 * 4. `两条主干入口各占满卡片高度而且中间留着页面那一档` 换一条轴：上面三条问的都是「说什么」，
 *    这一条量的是**渲染出来之后的位置与尺寸**（第 81 轮）。它是第 80 轮那套位置量法在这一页的
 *    第二次落地——第 80 轮只在设置页量了一张卡，`records/04` 第八十轮 §8 第 4 条记的那笔账
 *    「探针只覆盖那一张卡」就是被这一条关掉的。
 *
 * 三处必须交代的取舍：
 *
 * 1. **屏幕级拿不到 `refresh()`**。首页的「现在」只在打开、以及之后每 60 秒的轮询里被读一次，
 *    用例做不到「改完时钟再等界面自己变」，所以每条用例的 `now` 都在 `show()` 之前定死、
 *    之后不再改。「跨时段自动改口」这条仍只在 `HomeViewModelTest` 里验。
 * 2. **跟 `SettingsScreenTest.安静时段里首页换成安静语而称呼不会冒出来` 不是同一件事**。
 *    那一条往库里摆一个把现在裹在中间的作息画像，证明的是**画像被读到了**；本类第 2 条直接
 *    固定钟点，证明的是**时刻本身就够决定这件事**（无画像时走 `fallbackQuietSpan`）。
 *    两条都留着：前者哪天挂了说明画像那条路断了，后者挂了说明兜底那条路断了。
 * 3. **第 4 条量的是屏外的节点**。首页在第 63 轮之后内容高过了视口，所以它全程走
 *    `getUnclippedBoundsInRoot()` 而不是 `boundsInRoot`——屏外的节点会被祖先裁成零矩形
 *    （实测与理由见 `SettingsScreenTest` 第 80 轮那条用例）。另外两张主干的间距**当前**来自
 *    页面列自己的 `gapMd`（与 `minTouchGap` 同值），不是 `TargetStack`；断言因此同时钉住
 *    「正好是那一档」与「不小于下限」——哪一档被改窄都会红。
 *
 * 页面上一切文案都取自 `HomeVoice` / `InterventionVoice` 的当前措辞：改措辞要同时改这些断言，
 * 这是有意的摩擦。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

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
     * 这个用例自己的库文件与两份偏好，三样都不跟应用共用，于是跑完之后这台设备上应用的设置
     * 一个字节都没动过。
     */
    private lateinit var container: AppContainer

    /** 两份偏好。 */
    private lateinit var 存储: 自己的存储

    /**
     * 屏幕级用例手里的「现在」。
     *
     * 时钟是一个读这个字段的闭包，所以容器建好之后再改它也来得及——但**只来得及到 `show()`
     * 之前**：组合起来之后首页不会再自己读一次时钟（见类注释第 1 条）。
     */
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /**
     * 从「引导已经走过」的地方起步，但**不在这里渲染**：各例要先把「现在」和设置摆好。
     *
     * 时钟走的是第 31 轮新开的那个口子：默认值是 `SystemAppClock`，正式应用因此一个字节
     * 都没变，而这里给的是一个定死的钟点。
     */
    @Before
    fun 摆一个引导已走过的起点() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(
            context = context,
            databaseName = TEST_DB,
            settingsData = 存储.settingsData,
            interventionData = 存储.interventionData,
            clock = AppClock { now },
        )
        runBlocking(Dispatchers.IO) { container.settings.completeOnboarding() }
    }

    /**
     * 两句话都由时钟决定：主话来自 `GreetingEngine.headlineFor`（9 点落在「上午」），
     * 补充话是「第一次见面」+ `InterventionVoice` 在**没有画像**时补的那句尾巴。
     *
     * `ensureFirstLaunchRecorded()` 写的是**这个时钟那天**，所以天数算出来正好是 1 天，
     * 于是那半句是「第一次见面」而不是「已经认识 N 天了」。
     */
    @Test
    fun 早上九点首页那句话带刚存下的称呼() {
        now = LocalDateTime.of(2026, 3, 10, 9, 0)
        runBlocking(Dispatchers.IO) {
            container.settings.setAddress(NEW_ADDRESS)
            container.settings.ensureFirstLaunchRecorded()
        }

        show()
        等首页到Ready()

        assertEquals(1, count("$NEW_ADDRESS，上午好"))
        assertEquals(1, count(NOTE_FIRST_MEETING + NOTE_ASSUMED_SUFFIX))
        assertEquals(0, count(HomeVoice.askAddress))
    }

    /**
     * 跟上一例**只差时钟**：同一台设备、同一份设置、同一段代码，14:30 那句话就该是「下午好」。
     *
     * 这条是「那句话不是写死的字面量」最直接的证据。改坏时钟注入（比如让 `AppContainer` 又
     * 用回系统钟）时，两例必有一例红——除非跑测试的那一刻恰好是同一个时段，那时它会飘。
     */
    @Test
    fun 换个钟点那句话就跟着换() {
        now = LocalDateTime.of(2026, 3, 10, 14, 30)
        runBlocking(Dispatchers.IO) {
            container.settings.setAddress(NEW_ADDRESS)
            container.settings.ensureFirstLaunchRecorded()
        }

        show()
        等首页到Ready()

        assertEquals(1, count("$NEW_ADDRESS，下午好"))
        assertEquals(0, count("$NEW_ADDRESS，上午好"))
    }

    /**
     * 23:30 落在兜底的安静时段里（无画像时 `InterventionConfig.fallbackQuietSpan` 是
     * 22:30–07:00），所以首页换成安静语，一个字都不提称呼。
     *
     * 称呼是**存下来的**：它没出现在屏幕上不是因为没设，是因为这一支压根不读问候语。
     * 这也是安静语的设计口径——判定为安静时还说称呼，就等于换个法子把招呼打出来了。
     */
    @Test
    fun 深夜首页不迎上来说话也不冒出称呼() {
        now = LocalDateTime.of(2026, 3, 10, 23, 30)
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }

        show()
        等首页到Ready()

        assertEquals(1, count(QUIET_HEADLINE_SLEEP))
        assertEquals(1, count(QUIET_EARLIEST_NEXT_MORNING))
        assertEquals(0, countContaining(NEW_ADDRESS))
        assertEquals(0, count(HomeVoice.askAddress))
    }

    /**
     * 称呼还没填时，首页多一行入口问一次，而那句话里当然没有名字。
     *
     * 那一行提示的在场与否由 `needsAddress` 决定，跟「这句话说什么」是两条独立的线：
     * 上面几条把称呼存进去了，这一条没有，于是同一套渲染出来的界面差别只在这两处。
     */
    @Test
    fun 还没填称呼时首页问一次而且那句话不带名字() {
        now = LocalDateTime.of(2026, 3, 10, 9, 0)

        show()
        等首页到Ready()

        assertEquals(1, count(HomeVoice.askAddress))
        assertEquals(1, count("上午好"))
        assertEquals(0, count("$NEW_ADDRESS，上午好"))
    }

    /**
     * 两条主干入口的**几何**：高度、上下顺序、那一档空隙、左右页边距，四样都是从渲染结果里量的。
     *
     * 上面三条问的都是「说什么」，这一条换了一条轴。它量的正是首页上最要紧的那两件事的可点区：
     * `ElderDimens` 把 `entryCardMinHeight` 定成 112dp 的依据是「主入口该比 64dp 的下限宽裕得多」，
     * 而这条口径此前只有**常量层**（`TouchSpacingTest` 数那个数）与**源码层**（`EntryCard` 里那一行
     * `heightIn`）把关——两者都读不到「渲染出来之后到底多高」，字号、行高、内边距任何一处把它压薄，
     * 两层都不会响。
     *
     * 期望值一律从 `ElderDimens` 现取，不写死像素：写死等于把「屏幕长什么样」抄进用例。
     * `getUnclippedBoundsInRoot()` 回的是 **dp**，与 `ElderDimens` 同单位，连密度都不用乘。
     *
     * 顺带把第 80 轮那笔账关掉（`records/04` 第八十轮 §8 第 4 条）：那套位置量法当时只落在设置页
     * 一张卡上，这里在同一套量法上换一页、换一条轴（那边量上下顺序与空隙，这里补上水平方向）。
     */
    @Test
    fun 两条主干入口各占满卡片高度而且中间留着页面那一档() {
        now = LocalDateTime.of(2026, 3, 10, 9, 0)
        // 称呼填上：那一行「告诉我您希望被怎么称呼」会夹在问候卡与第一张入口卡之间，
        // 它在场时「两张卡是相邻兄弟」这个前提就不成立了。
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }

        show()
        等首页到Ready()
        compose.waitForIdle()

        assertEquals(
            "前置条件：称呼已填时那一行入口该收起来，否则两张卡之间夹着一位，相邻就无从谈起",
            0,
            count(HomeVoice.askAddress),
        )
        assertEquals("锚点：说两句那张卡得在场上", 1, count(HomeVoice.talkAction))
        assertEquals("锚点：玩一会儿那张卡得在场上", 1, count(HomeVoice.playAction))

        // 位置一律走**未被祖先裁剪**的那条路：首页在第 63 轮之后内容高过了视口，
        // `boundsInRoot` 会把屏外的节点读成零矩形，整条断言就变成在量一个假的东西。
        fun 框(text: String): DpRect {
            assertEquals("「$text」在屏上正好该有一个节点", 1, count(text))
            return compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        }

        // `DpRect` 的 `width` / `height` 是 `androidx.compose.ui.unit` 里的**扩展属性**，
        // 少一个 import 就编不过；这里按 R80 那条用例的办法自己减差值，少一处外部依赖。
        fun 宽(b: DpRect) = b.right.value - b.left.value
        fun 高(b: DpRect) = b.bottom.value - b.top.value

        val 说框 = 框(HomeVoice.talkAction)
        val 玩框 = 框(HomeVoice.playAction)

        // 先把「零矩形」这种典型读数排掉：它是「被裁过」或「压根没渲出来」的样子，
        // 若不排掉，下面拿两个零矩形比较也会一路绿过去。
        assertTrue(
            "两张主干的框都该有实际尺寸 [说 ${宽(说框)}x${高(说框)}dp]" +
                " [玩 ${宽(玩框)}x${高(玩框)}dp]",
            宽(说框) > 100f && 高(说框) > 50f &&
                宽(玩框) > 100f && 高(玩框) > 50f,
        )

        assertTrue(
            "「说两句」该在「玩一会儿」上面 [说顶 ${说框.top.value}dp 玩顶 ${玩框.top.value}dp]",
            说框.top < 玩框.top,
        )
        assertTrue(
            "两张卡不该叠在一起 [说底 ${说框.bottom.value}dp 玩顶 ${玩框.top.value}dp]",
            说框.bottom <= 玩框.top,
        )

        val 一档卡高 = ElderDimens.entryCardMinHeight.value
        assertTrue(
            "「说两句」这张卡得占满一个主入口的高度 [实测 ${高(说框)}dp，下限 ${一档卡高}dp]",
            高(说框) + 0.5f >= 一档卡高,
        )
        assertTrue(
            "「玩一会儿」这张卡得占满一个主入口的高度 [实测 ${高(玩框)}dp，下限 ${一档卡高}dp]",
            高(玩框) + 0.5f >= 一档卡高,
        )
        assertEquals(
            "两条主干该一样高：它们同属一档，谁高出一截就看不出「主干与设置那一行不等重」的分工了",
            高(说框),
            高(玩框),
            1f,
        )

        // 相邻可点目标之间的净间隙（「Mind the Gap」那一条，见 `ElderDimens.minTouchGap`）。
        // 这一对在当前版式里由页面列自己的 `gapMd` 排开，与 `minTouchGap` 同值——所以两条都钉：
        // 「正好是页面那一档」管住版式口径，「不小于下限」管住可点性口径，改窄哪一档都会红。
        val 量到的间隙 = 玩框.top.value - 说框.bottom.value
        assertEquals(
            "两张卡之间该留着页面列那一档 [实测 ${量到的间隙}dp，应该是 ${ElderDimens.gapMd.value}dp]",
            ElderDimens.gapMd.value,
            量到的间隙,
            1f,
        )
        assertTrue(
            "两张卡之间那一档不得小于相邻可点目标的下限 [实测 ${量到的间隙}dp，" +
                "下限 ${ElderDimens.minTouchGap.value}dp]",
            量到的间隙 + 0.5f >= ElderDimens.minTouchGap.value,
        )

        // 水平方向是第 80 轮那条没量过的：两张卡同宽同边，且各留一档页边距。
        // 页边距这一条同时是量法自证——裁剪出问题或量错坐标系时，读数不会正好落在 20dp 上。
        val 根框 = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals("两张卡的左边该对齐", 说框.left.value, 玩框.left.value, 1f)
        assertEquals("两张卡的右边该对齐", 说框.right.value, 玩框.right.value, 1f)
        assertEquals(
            "两张卡左边该留一档页边距 [实测 ${说框.left.value - 根框.left.value}dp，" +
                "应该是 ${ElderDimens.screenPadding.value}dp]",
            ElderDimens.screenPadding.value,
            说框.left.value - 根框.left.value,
            1f,
        )
        assertEquals(
            "两张卡右边该留一档页边距 [实测 ${根框.right.value - 玩框.right.value}dp，" +
                "应该是 ${ElderDimens.screenPadding.value}dp]",
            ElderDimens.screenPadding.value,
            根框.right.value - 玩框.right.value,
            1f,
        )

        // 交叉比对：同一页上**另一对**相邻可点目标——「您自己的节奏」那一组里的两条入口行，
        // 它们的间隙由 `TargetStack` 直接钉在 `minTouchGap` 上。同一套量法在同一页读第二处，
        // 读数也落在该落的那一档上，上面量到的才说明得了「这是渲染出来的几何」。
        val 安静框 = 框(HomeVoice.quietAction)
        val 设置框 = 框(HomeVoice.settingsAction)
        assertEquals(
            "同一页另一对相邻可点目标之间该正好是 minTouchGap 那一档，否则上面量到的说明不了什么",
            ElderDimens.minTouchGap.value,
            设置框.top.value - 安静框.bottom.value,
            1f,
        )
        assertTrue(
            "设置那一行自己也得占满一个触摸高度 [实测 ${高(设置框)}dp，" +
                "下限 ${ElderDimens.minTouchTarget.value}dp]",
            高(设置框) + 0.5f >= ElderDimens.minTouchTarget.value,
        )

        // 读数照 `DocScreenCopyGuardTest` 那套办法留一条：断言全绿时消息不会出现在报告里，
        // 只有主动打成标准输出才留得下「这一轮渲染出来究竟多高、空了多少」的原始数值。
        // 它从测试 XML 的 `<system-out>` 读，Gradle 的日志里不带这一段。
        println(
            "[home-entry-geometry] " +
                "说=left${说框.left.value}/top${说框.top.value}/right${说框.right.value}" +
                "/bottom${说框.bottom.value} 宽${宽(说框)} 高${高(说框)}；" +
                "玩=left${玩框.left.value}/top${玩框.top.value}/right${玩框.right.value}" +
                "/bottom${玩框.bottom.value} 宽${宽(玩框)} 高${高(玩框)}；" +
                "根=left${根框.left.value}/right${根框.right.value}；" +
                "卡间净间隙=${量到的间隙}（期望 gapMd=${ElderDimens.gapMd.value} / " +
                "下限 minTouchGap=${ElderDimens.minTouchGap.value}）；" +
                "左页边距=${说框.left.value - 根框.left.value} " +
                "右页边距=${根框.right.value - 玩框.right.value}" +
                "（期望 screenPadding=${ElderDimens.screenPadding.value}）；" +
                "交叉比对：设置行上间隙=${设置框.top.value - 安静框.bottom.value}" +
                "（期望 minTouchGap=${ElderDimens.minTouchGap.value}）、" +
                "设置行高=${高(设置框)}（下限 minTouchTarget=${ElderDimens.minTouchTarget.value}）",
        )
    }

    /** 把整个应用渲出来。外层主题由 `AppRoot` 自己套，这里不要再套一层 `ForTheElderTheme`。 */
    private fun show() {
        compose.setContent { AppRoot(container = container) }
    }

    /**
     * 等首页真的到 Ready。
     *
     * 用三个入口里最靠上的那个当哨兵：`Loading` 那一帧上一个入口都没有，直接断言话语会在
     * 还没渲染完的时候假红，断言「那句话不在」则更容易假过。
     */
    private fun 等首页到Ready() {
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
    }

    /** 屏幕上正好有几个节点带这段文字。整串相等，不做子串匹配。 */
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countContaining(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private companion object {
        /** 等界面反应的上限。超时就是红，宁可慢一点也不要飘。 */
        const val TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件：应用那份 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-home-screen.db"

        const val NEW_ADDRESS = "老熊"

        /** 措辞取自 `GreetingEngine.noteFor` 的「第一次见面」那一支。 */
        const val NOTE_FIRST_MEETING = "第一次见面，慢慢来。"

        /** 措辞取自 `InterventionVoice` 在 `ASSUMED_NOT_ENOUGH_DATA` 上补的尾巴。 */
        const val NOTE_ASSUMED_SUFFIX = "说话的时机，我先按普通的作息来。"

        /** 措辞取自 `InterventionVoice` 里 `QuietReason.SLEEP_HOURS` 那一支。 */
        const val QUIET_HEADLINE_SLEEP = "这会儿是您休息的时间。"

        /**
         * 措辞取自 `InterventionVoice.earliestLineOf` 拼出来的那一行（第 78 轮起）。
         * 23:30 落在兜底安静时段里，最近一次恢复是次日 07:00，所以带「明天」。
         */
        const val QUIET_EARLIEST_NEXT_MORNING = "下一次迎来说话最早在明天 07:00 之后。"
    }
}
