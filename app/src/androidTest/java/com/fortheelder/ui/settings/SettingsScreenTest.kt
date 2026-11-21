package com.fortheelder.ui.settings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.BuildConfig
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.data.rhythm.CapturedSignal
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.rhythmDayOf
import com.fortheelder.ui.AppRoot
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.home.HomeVoice
import com.fortheelder.ui.onboard.OnboardVoice
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「设置」这一页的**渲染级**验证（FR-13 的四条验收标准）。
 *
 * 这也是**第一条把 `AppRoot` 整个渲出来**的用例。别的 18 个类都是从 `MainNav` 或单独某一页
 * 起渲的，于是「应用一打开先看引导页、引导走过之后才进主界面」这条**最外层的分支**，
 * 以及 `settings.onboardingDone` 这个开关，一直没被真的读过。设置页有两条承诺恰恰只能
 * 在这个高度验：
 *
 * 1. 改完称呼回到首页，首页**立刻不再问**「怎么称呼您」（AC-13.1）；
 * 2. 确认「清空设置」之后真的**回到引导页**，而不是停在原地（AC-13.4）。
 *
 * 三处必须交代的做法：
 *
 * 1. **AC-13.1 在这里只验「首页重新读了设置」**。「首页那句话里出现新称呼」那一半在
 *    `HomeViewModelTest` 里验——那个类能注入时钟。屏幕级用例固定不了时钟
 *    （`AppContainer.clock` 写死是系统钟），而「说什么」同时取决于钟点（深夜换安静语）
 *    与节流状态，硬断言「这句话里有名字」会变成一条随运行时刻忽红忽绿的用例。
 *    （第 29 轮之前这里还多一层不干净：连节流状态读的都是应用那份存储。）
 * 2. **时钟这条限制本身被列成了一条被测行为**（`安静时段里首页换成安静语而称呼不会冒出来`）：
 *    用例先往库里摆一个把「现在」裹在中间的作息画像，于是不管几点跑，首页都该走进安静分支。
 * 3. **本类不碰这台设备上应用真正的设置**（第 29 轮改）。三样都自己一份：库文件走
 *    `databaseName`，两份偏好走 `AppContainer` 后来补的 `settingsData` / `interventionData`
 *    （见 `自己的存储`）。在此之前本类靠 `@After` 里的 `resetToDefaults()` 收尾，等于跑一遍
 *    设备测试就把用户自己设的称呼、字号清掉。
 *
 * 页面上一切文案都取自 `SettingsVoice` / `CommonVoice` / `HomeVoice` / `OnboardVoice`
 * 的当前措辞：改措辞要同时改这些断言，这是有意的摩擦。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

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
     * 这个用例自己的库文件与两份偏好，三样都不跟应用共用。所以本类跑完之后，这台设备上
     * 应用的设置一个字节都没动过（第 29 轮之前不是这样，见类注释第 3 条）。
     */
    private lateinit var container: AppContainer

    /** 两份偏好，见 `自己的存储`。 */
    private lateinit var 存储: 自己的存储

    /**
     * 从「引导已经走过」的地方起步，但**不在这里渲染**：第二条用例必须在组合之前先往库里
     * 摆好作息画像，所以渲染挪到各例自己的 `show()` 里。
     *
     * 这里不 `resetToDefaults()`：这一份存储是刚建出来的空文件，出厂状态本来就是它现在的样子。
     */
    @Before
    fun 摆一个引导已走过的起点() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
        runBlocking(Dispatchers.IO) {
            container.settings.completeOnboarding()
            container.settings.setFontSize(FontSize.STANDARD)
        }
    }

    @Test
    fun 改称呼之后首页立刻不再问称呼() {
        show()

        // 等首页真的到了 Ready 再看「问不问称呼」：Loading 那一帧上三个入口一个都没有，
        // 直接断言 count(askAddress) == 1 会在还没渲染完的时候假红，断言 == 0 会假过。
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.askAddress) == 1 }

        tap(HomeVoice.settingsAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }

        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(NEW_ADDRESS)
        tap(SettingsVoice.saveAddress)

        // 「已保存」这句话就是 setAddress 写完之后置上的，所以它出现即视为已经落盘。
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.addressSaved) == 1 }
        assertEquals(NEW_ADDRESS, 当前设置().address)

        tapBack()

        // 回首页同样先等 Ready 出来，再断言那句话没了。
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
        assertEquals(0, count(HomeVoice.askAddress))
    }

    @Test
    fun 安静时段里首页换成安静语而称呼不会冒出来() {
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }
        摆一个把现在裹在中间的安静时段()

        show()

        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
        // 文案取自 `InterventionVoice` 里 `QuietReason.SLEEP_HOURS` 那一条的当前措辞。
        assertEquals(1, count("这会儿是您休息的时间。"))
        assertEquals(0, countContaining(NEW_ADDRESS))
        assertEquals(0, count(HomeVoice.askAddress))
    }

    @Test
    fun 点更大那一行字号立刻落库() {
        show()
        从首页进设置页()

        tap(FontSize.EXTRA.label)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.fontSizeChanged(FontSize.EXTRA.label)) == 1 }
        assertEquals(FontSize.EXTRA, 当前设置().fontSize)
    }

    @Test
    fun 设置页上显示的是构建配置里的版本号() {
        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.versionLine(BuildConfig.VERSION_NAME)) == 1 }
    }

    @Test
    fun 点清空设置先问一次点不清了什么都不会发生() {
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }
        show()
        从首页进设置页()

        tap(SettingsVoice.resetButton)
        assertEquals(1, count(CommonVoice.confirmClearTitle))

        tapInDialog(CommonVoice.dismissClear)
        compose.waitUntil(TIMEOUT_MILLIS) { count(CommonVoice.confirmClearTitle) == 0 }
        assertTrue(当前设置().onboardingDone)
        assertEquals(NEW_ADDRESS, 当前设置().address)
    }

    @Test
    fun 确认清空设置之后就回到引导页() {
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }
        show()
        从首页进设置页()

        tap(SettingsVoice.resetButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(CommonVoice.confirmClearTitle) == 1 }
        tapInDialog(CommonVoice.confirmClear)

        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.nextButton) == 1 }
        assertEquals(0, count(HomeVoice.talkAction))
        assertFalse(当前设置().onboardingDone)
        assertNull(当前设置().address)
    }

    @Test
    fun 清掉作息数据之后那一行立刻变成零() {
        runBlocking(Dispatchers.IO) {
            container.rhythmRepository.record(
                listOf(
                    CapturedSignal(LocalDateTime.of(2026, 11, 21, 9, 0), RhythmKind.SCREEN_ON),
                    CapturedSignal(LocalDateTime.of(2026, 11, 21, 9, 1), RhythmKind.SCREEN_OFF),
                    CapturedSignal(LocalDateTime.of(2026, 11, 21, 9, 2), RhythmKind.USER_PRESENT),
                ),
            )
        }
        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.rhythmCount(3)) == 1 }

        tap(SettingsVoice.clearRhythm)
        compose.waitUntil(TIMEOUT_MILLIS) { count(CommonVoice.confirmClearTitle) == 1 }
        tapInDialog(CommonVoice.confirmClear)

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.rhythmNone) == 1 }
        assertEquals(0, runBlocking(Dispatchers.IO) { container.rhythmRepository.signalCountOnce() })
    }

    /**
     * 把整个应用渲出来。外层主题由 `AppRoot` 自己套，这里不要再套一层 `ForTheElderTheme`。
     */
    private fun show() {
        compose.setContent { AppRoot(container = container) }
    }

    private fun 当前设置() = runBlocking(Dispatchers.IO) { container.settings.settings.first() }

    /** 屏幕上正好有几个节点带这段文字。整串相等，不做子串匹配。 */
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countContaining(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun tap(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun tapBack() {
        compose.onNodeWithContentDescription(CommonVoice.back).performClick()
    }

    /** 对话框里的按钮：它在弹窗自己的层里，对整页的滚动容器 `performScrollTo` 会失败。 */
    private fun tapInDialog(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performClick()
    }

    /** 进设置页，并且等到「保存称呼」这一行真的在了才算到了。 */
    private fun 从首页进设置页() {
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
        tap(HomeVoice.settingsAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }
    }

    /**
     * 往库里摆一个**把此刻裹在中间**的安静时段：睡下时刻取「现在减十分钟」，起床时刻取
     * 「现在加十分钟」。落库会把秒截掉，所以实际跨度是 [整分-10min, 整分+10min)，等号两端
     * 都落在里面；跨子夜时 `TimeSpan.contains` 走的是另一支，照样为真。于是不管几点跑这条
     * 用例，首页都该判成 SLEEP_HOURS。
     */
    private fun 摆一个把现在裹在中间的安静时段() {
        runBlocking(Dispatchers.IO) {
            val now = container.clock.now()
            container.rhythmRepository.saveProfile(
                rhythmDayOf(now, RhythmConfig.DEFAULT_CUTOVER_HOUR).toEpochDay(),
                DailyRhythm(
                    wakeAt = now.toLocalTime().plusMinutes(10),
                    sleepAt = now.toLocalTime().minusMinutes(10),
                    activeSpans = emptyList(),
                    idleSpans = emptyList(),
                    nightChargingAt = null,
                    sampleDays = 7,
                    confidence = 0.8f,
                ),
            )
        }
    }

    private companion object {
        /** 等界面反应的上限。超时就是红，宁可慢一点也不要飘。 */
        const val TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件：应用那份 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-settings-screen.db"

        const val NEW_ADDRESS = "老熊"
    }
}
