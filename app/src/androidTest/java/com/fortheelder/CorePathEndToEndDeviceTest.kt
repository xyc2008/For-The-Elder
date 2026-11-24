package com.fortheelder

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.AppSettings
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.home.HomeVoice
import com.fortheelder.ui.onboard.OnboardVoice
import com.fortheelder.ui.play.PlayVoice
import com.fortheelder.ui.settings.SettingsVoice
import com.fortheelder.ui.talk.TalkVoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * 从**真入口**走一遍核心链路（AC-01.1 / 完成判据）。
 *
 * ## 这一条补的是哪一块缺口
 *
 * 全仓三十个 `androidTest` 都自己挂 `createComposeRule()`，自己把 `MainNav(container = 自己的容器)`
 * 摆进组合里。这带来一个看不见的盲区：**没有任何一例走过真 `MainActivity`、真 `ElderApp` 容器、
 * 真 `AppRoot`、真 `MainNav` 这一条装配线**。把 `AndroidManifest.xml` 里的 Activity 改名、
 * 把 `MainActivity` 里的 `AppRoot(container)` 换成别的根、把 `ElderApp.onCreate` 里
 * `startProcessWork()` 删掉——726 个单测加 172 个设备用例全绿，装到手机上却是打不开或者空屏。
 *
 * 本类只用 `createAndroidComposeRule<MainActivity>()`：Activity 由系统按清单拉起来，
 * 组合树是应用自己那份，容器是应用 `Application.onCreate` 里建的那个。
 *
 * ## 代价必须说清楚
 *
 * 它**故意**用这台设备上**应用真正的那份库与偏好**（`elder.db` 与两份 DataStore），
 * 不像其它三十个类那样各自注入文件名与 DataStore。这是"证明真入口"的代价，不是疏忽：
 * 换了名字就证明不了清单与 `Application` 那一层。
 *
 * 收尾用**产品自己的** `AllDataEraser`（与设置页「清空全部数据」同一个对象）把真存储抹回起点。
 * 这条兜底不是事务：用例**中途崩掉**（进程被杀、断言抛出后收尾也抛）就会在设备上留下半截状态。
 * 这是真实残余，写在这里而不是假装没有。R28/R29 那条「屏幕级用例不许动真数据」的口径仍然成立，
 * 本类是有理由的例外——它要证的东西只有真入口能给。
 *
 * ## 三例都从"刚装好"起步
 *
 * 外层规则 `真存储从零起步` 在 Activity 起来**之前**抹一次真存储，并核 `onboardingDone == false`；
 * 用例跑完再抹一次并核干净。所以三例互不依赖、也不依赖执行顺序。
 * 摆起点只能放在**外层规则**里，不能放 `@Before`：JUnit 的规则 `before` 跑在 `@Before` 之前，
 * 而 `createAndroidComposeRule` 在它自己的 `before` 里就把 Activity 拉起来了——
 * 放进 `@Before` 就变成"先起界面、再擦数据"，界面还停在旧状态上。
 */
@RunWith(AndroidJUnit4::class)
class CorePathEndToEndDeviceTest {

    /** 它自己不挂 `@get:Rule`：由下面的 `rules` 包住，收尾顺序才由那条链说了算。 */
    private val compose = createAndroidComposeRule<MainActivity>()

    private val 真应用: ElderApp
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ElderApp

    private val 真容器: AppContainer
        get() = 真应用.container

    /**
     * 摆起点那一步排在组合规则**外面**：真存储先被抹干净，之后才轮到 Activity 起来。
     * 顺序反了这一例就失去意义（界面会按旧数据渲染）。
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(真存储从零起步 { 真容器 }).around(compose)

    // ---- 第一例：清单里那个启动项真的是这份应用的入口 ----

    /**
     * 从系统那里问出来：这个包点开之后拿到的到底是不是 [MainActivity]。
     *
     * 问的是 `PackageManager`，不是自己 `new` 一个：清单改名、`intent-filter` 被删、
     * 多出一个转发用的壳 Activity，都只有从系统这一侧看得见。
     */
    private fun 清单里的启动项(): List<String> {
        val 仪器 = InstrumentationRegistry.getInstrumentation()
        val 包名 = 仪器.targetContext.packageName
        val 意图 = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(包名)
        return 仪器.targetContext.packageManager
            .queryIntentActivities(意图, 0)
            .map { it.activityInfo.name }
    }

    @Test
    fun 真入口是清单里那个启动项() {
        val 名单 = 清单里的启动项()
        assertTrue(
            "清单里没把 MainActivity 登记成启动项，问系统拿到的名单是 $名单",
            名单.contains(MainActivity::class.java.name),
        )
        assertEquals(
            "拉起来的不该是别的 Activity",
            MainActivity::class.java,
            compose.activity.javaClass,
        )

        // 对话页与游戏页的表头分别是「说两句」「玩一会儿」，首页那两条入口的按钮文字也长得像，
        // 但整串相等才算命中（`onAllNodesWithText` 默认不做子串），所以下面数出来的是它们各自的根。
        等首屏出现()
        assertTrue("引导第一页该有应用名表头", count(CommonVoice.appTitle) > 0)
        assertTrue("引导第一页该讲它现在会做的", count(OnboardVoice.doesSection) > 0)
        assertTrue("引导第一页该讲它不会做的", count(OnboardVoice.wontSection) > 0)
        assertTrue("引导第一页该有往下走的按钮", count(OnboardVoice.nextButton) > 0)

        // 同一帧上，首页三个入口**一个都不该在**：闸门是 `AppRoot` 按 onboardingDone 分的，
        // 而这一刻真偏好里的 onboardingDone 是被外层规则核过的 false。
        assertEquals("引导没走完，首页的第一个入口不该在", 0, count(HomeVoice.talkAction))
        assertEquals("引导没走完，首页的第二个入口不该在", 0, count(HomeVoice.playAction))
        assertEquals("引导没走完，首页的设置入口不该在", 0, count(HomeVoice.settingsAction))
    }

    // ---- 第二例：引导走完之后，称呼与字号真的躺在真偏好里 ----

    /**
     * 走完三页之后不看界面，直接去读**真 DataStore**。
     *
     * 界面翻到首页只说明 `onboardingDone` 落盘了；称呼、字号、采集开关是在它**之前**的四笔写入。
     * 四笔都走同一个 `SettingsStore`，而首页要靠 `completeOnboarding()` 那最后一笔才翻过去——
     * 所以首页一出现，前三笔必然已在同一份快照里。这里把这条不变量当断言写下来。
     */
    @Test
    fun 走完引导三页称呼和字号落进真存储() {
        val 称呼 = "老王"

        等首屏出现()
        tapPinned(OnboardVoice.nextButton)

        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.page2Title) > 0 }
        assertTrue("第二页该问怎么称呼您", count(CommonVoice.addressQuestion) > 0)
        assertTrue("第二页该有字号那一栏", count(CommonVoice.fontSection) > 0)

        // 第二页只有这一个输入框（预览卡里没有），所以按「有没有 SetText 动作」找是唯一的。
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(称呼)
        compose.onNodeWithText(FontSize.EXTRA.label).performScrollTo().performClick()

        tapPinned(OnboardVoice.nextStep)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.page3Title) > 0 }
        assertTrue("第三页该有同意那一条", count(OnboardVoice.accept) > 0)
        assertTrue("第三页该有先不用那一条", count(OnboardVoice.decline) > 0)

        tapPinned(OnboardVoice.accept)

        // 首页第一个入口出现 = 引导真的走完了。在此之前只等 idle 不管用：
        // 首页状态来自 Room 与 DataStore，那两条线不归 Compose 的 idling 管。
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) > 0 }

        val 落盘 = 等真设置("首页都出来了，引导那四笔却还没落进真偏好") { it.onboardingDone }
        assertEquals("称呼没落进真偏好", 称呼, 落盘.address)
        assertEquals("字号没落进真偏好", FontSize.EXTRA, 落盘.fontSize)
        assertTrue("点了「同意」却没把作息采集打开", 落盘.collectRhythm)
    }

    // ---- 第三例：从真入口按用户的路走完核心链路四段 ----

    /**
     * 作息感知（首页）→ 意愿发现 / 兴趣陪伴（说两句）→ 脑力互动（玩一会儿）→ 回首页。
     *
     * 四段里每一段都断两句：**这一屏独有的在场**，并且**上一屏独有的已经不在**。
     * 只断"新东西出现了"会漏掉一种情形——两屏叠在一起（导航写错、返回没生效）。
     *
     * 到设置页那一步不能靠「设置」这两个字：首页那条入口的文字就是「设置」，
     * 而设置页表头也是「设置」，两处各一个，数出来分不清在哪一屏。改用两张独有文案一起断。
     */
    @Test
    fun 从真入口按用户的路走完核心链路四段() {
        走完引导("老李")

        assertTrue("首页该有说话那个入口", count(HomeVoice.talkAction) > 0)
        assertTrue("首页该有玩一会儿那个入口", count(HomeVoice.playAction) > 0)
        assertTrue("首页该有设置那条", count(HomeVoice.settingsAction) > 0)

        // 段一 → 段二：意愿发现 / 兴趣陪伴
        tap(HomeVoice.talkAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(TalkVoice.pageTitle) > 0 }
        assertEquals("说话页上不该还留着首页那句", 0, count(HomeVoice.sectionTalk))

        tapBack()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.sectionTalk) > 0 }
        assertEquals("退回首页之后不该还留着说话页的表头", 0, count(TalkVoice.pageTitle))

        // 段二 → 段三：脑力互动
        tap(HomeVoice.playAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(PlayVoice.chooseTitle) > 0 }
        assertEquals("游戏页上不该还留着首页那句", 0, count(HomeVoice.sectionPlay))

        tapBack()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.sectionPlay) > 0 }
        assertEquals("退回首页之后不该还留着挑游戏那句", 0, count(PlayVoice.chooseTitle))

        // 段三 → 段四：设置页（`AppRoot` 之外唯一能改这几样的地方）
        tap(HomeVoice.settingsAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) > 0 }
        assertEquals("到了设置页，首页那句独有文案就该消失", 0, count(HomeVoice.sectionSettings))
        assertTrue("设置页该有「关于常伴」那一栏", count(SettingsVoice.aboutSection) > 0)
        assertTrue("设置页该有「重新来一遍」那一栏", count(SettingsVoice.resetSection) > 0)

        tapBack()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.sectionSettings) > 0 }
        assertEquals("退回首页之后不该还留着设置页的行头", 0, count(SettingsVoice.saveAddress))
    }

    // ---- 走法与读数 ----

    /** 引导第一页（它独有「它现在会做的」那一栏）。首页首帧还在读盘，所以必须等。 */
    private fun 等首屏出现() {
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.doesSection) > 0 }
    }

    /** 自己走完引导三页，停在首页上。三例都从"刚装好"起步，所以每例各走一遍。 */
    private fun 走完引导(称呼: String) {
        等首屏出现()
        tapPinned(OnboardVoice.nextButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.page2Title) > 0 }
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(称呼)
        compose.onNodeWithText(FontSize.EXTRA.label).performScrollTo().performClick()
        tapPinned(OnboardVoice.nextStep)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.page3Title) > 0 }
        tapPinned(OnboardVoice.accept)
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) > 0 }
    }

    /** 屏幕上正好有几个节点带这段文字。整串相等，不做子串匹配。 */
    private fun count(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * 点一个写在滚动区里的控件。
     *
     * 先等它出现再点，不是可有可无的礼貌（首页第一帧是 `HomeUiState.Loading`，三个入口一个都不在），
     * 之后 `performScrollTo` 把它滚进视线，再点。
     */
    private fun tap(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    /** 点一个钉在底部的按钮：它不在滚动区里，`performScrollTo` 找不到可滚的祖先，会直接失败。 */
    private fun tapPinned(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    private fun tapBack() {
        compose.onNodeWithContentDescription(CommonVoice.back).performClick()
    }

    /** 读一次**真偏好**。用的是应用自己那份 DataStore，不是临时注入的。 */
    private fun 真设置(): AppSettings = runBlocking { 真容器.settings.settings.first() }

    /**
     * 等到真偏好满足条件为止，拿最后一次读到的值去断。
     *
     * 界面翻页与四笔写入之间隔着几条协程，硬读一次可能读到上一份快照；
     * 这里给到 [TIMEOUT_MILLIS] 的宽限，超了就把**实际读到的东西**写进失败信息里。
     */
    private fun 等真设置(说明: String, 条件: (AppSettings) -> Boolean): AppSettings {
        val 期限 = System.currentTimeMillis() + TIMEOUT_MILLIS
        var 末次 = 真设置()
        while (!条件(末次) && System.currentTimeMillis() < 期限) {
            Thread.sleep(50)
            末次 = 真设置()
        }
        assertTrue("$说明；实际读到的是 $末次", 条件(末次))
        return 末次
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * 让真存储从"刚装好"起步，并在用例之后把它抹回去。
 *
 * 用的是**产品自己的** `AllDataEraser`——设置页「清空全部数据」走的也是它。
 * 抹完还要核一次 `onboardingDone`：抹不动就当场红，不把"起点没摆正"留到用例里去当谜案。
 *
 * 收尾那句为什么不能简单塞进 `finally`：用例本身已经抛了异常时，`finally` 里再抛会把原来那条
 * 顶掉，现场就只剩"收尾失败"。这里改成把收尾的异常挂到原来那条上（`addSuppressed`）。
 */
private class 真存储从零起步(private val 取容器: () -> AppContainer) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            runBlocking { 取容器().allDataEraser.erase() }
            assertTrue(
                "起点没摆正：真偏好里 onboardingDone 已经是 true，本类要求从「刚装好」起步",
                !runBlocking { 取容器().settings.settings.first() }.onboardingDone,
            )

            try {
                base.evaluate()
            } catch (用例失败: Throwable) {
                try {
                    抹干净()
                } catch (收尾失败: Throwable) {
                    用例失败.addSuppressed(收尾失败)
                }
                throw 用例失败
            }
            抹干净()
        }

        private fun 抹干净() {
            runBlocking { 取容器().allDataEraser.erase() }
            assertTrue(
                "收尾没擦干净：真偏好里 onboardingDone 还是 true",
                !runBlocking { 取容器().settings.settings.first() }.onboardingDone,
            )
        }
    }
}
