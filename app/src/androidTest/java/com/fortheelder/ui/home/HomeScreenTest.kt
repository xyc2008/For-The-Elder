package com.fortheelder.ui.home

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.core.time.AppClock
import com.fortheelder.ui.AppRoot
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「首页顶上那张卡」的**渲染级**验证：AC-13.1 里「问候语里出现用户设的称呼」那一半。
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
 * 3. `还没填称呼时首页问一次而且那句话不带名字` 钉住没给称呼时首页的样子。
 *
 * 两处必须交代的取舍：
 *
 * 1. **屏幕级拿不到 `refresh()`**。首页的「现在」只在打开、以及之后每 60 秒的轮询里被读一次，
 *    用例做不到「改完时钟再等界面自己变」，所以每条用例的 `now` 都在 `show()` 之前定死、
 *    之后不再改。「跨时段自动改口」这条仍只在 `HomeViewModelTest` 里验。
 * 2. **跟 `SettingsScreenTest.安静时段里首页换成安静语而称呼不会冒出来` 不是同一件事**。
 *    那一条往库里摆一个把现在裹在中间的作息画像，证明的是**画像被读到了**；本类第 2 条直接
 *    固定钟点，证明的是**时刻本身就够决定这件事**（无画像时走 `fallbackQuietSpan`）。
 *    两条都留着：前者哪天挂了说明画像那条路断了，后者挂了说明兜底那条路断了。
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
