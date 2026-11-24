package com.fortheelder.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.BuildConfig
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.rhythm.CapturedSignal
import com.fortheelder.domain.engine.intervention.asHumanGap
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
import java.io.File
import java.nio.charset.StandardCharsets
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
 * 四处必须交代的做法：
 *
 * 1. **AC-13.1 在这里只验「首页重新读了设置」**。「首页那句话里出现新称呼」那一半现在由
 *    `HomeScreenTest` 在**同一个高度**验（第 31 轮补，见下面的第 2 条），在此之前它只在
 *    `HomeViewModelTest` 里验。本类这一半仍然值得留着：它钉住的是另一件事——称呼一存下来，
 *    「怎么称呼您」那行入口就当场没了（`needsAddress`）。
 *    （第 29 轮之前这里还多一层不干净：连节流状态读的都是应用那份存储。）
 * 2. **屏幕级固定时钟这件事，第 31 轮起做得到了**。本条原来写的是「屏幕级用例固定不了时钟
 *    （`AppContainer.clock` 写死是系统钟），硬断言『这句话里有名字』会变成一条随运行时刻
 *    忽红忽绿的用例」——那个限制本身被第 31 轮消掉了：`AppContainer` 收下了 `clock` 参数
 *    （ADR-029），`HomeScreenTest` 于是可以给一个定死的钟点。
 *    下面 `安静时段里首页换成安静语而称呼不会冒出来` 这条**照旧保留**，但它证的已经不是
 *    「只好这么绕」，而是另一件事：**画像里的安静时段被读到了**（本类往库里摆一个把现在裹在
 *    中间的画像）。`HomeScreenTest` 那条固定钟点的安静语用例走的是无画像时的兜底时段，
 *    两条路各断各的。
 * 3. **本类不碰这台设备上应用真正的设置**（第 29 轮改）。三样都自己一份：库文件走
 *    `databaseName`，两份偏好走 `AppContainer` 后来补的 `settingsData` / `interventionData`
 *    （见 `自己的存储`）。在此之前本类靠 `@After` 里的 `resetToDefaults()` 收尾，等于跑一遍
 *    设备测试就把用户自己设的称呼、字号清掉。
 * 4. **改动的回执不再有倒计时**（第 30 轮改，见 ADR-028）。`称呼已保存。` 与
 *    `字号已改成「…」。` 现在挂到用户下一次动手为止，而且各住在自己那张卡里，所以本类里
 *    等它的 `waitUntil` 只负责「等它出现」，不再是从一个两秒窗口里抢时间。
 *    代价写在 `docs/03` §7.11：回执会一直留到下一次打字或换档位（离开这一页也会丢掉）。
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
        // 它不再自己消失（第 30 轮拿掉了那 2 秒计时器，见 ADR-028），于是这句只是「等它出现」，
        // 不再是和时间赛跑——改之前这里必须在这句话过期前抢到它。
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.addressSaved) == 1 }
        assertEquals(NEW_ADDRESS, 当前设置().address)

        tapBack()

        // 回首页同样先等 Ready 出来，再断言那句话没了。
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
        assertEquals(0, count(HomeVoice.askAddress))
    }

    /**
     * GAP-02 的**设备级那一半**：多字节中文称呼从输入框进去，经校验、落盘，再冷读回来，
     * 全程一个字节都不许丢。
     *
     * 这条用例是第 56 轮补的，补的理由很具体：在这之前「中文称呼」只有两层证据，都不够硬。
     * JVM 侧的 `SettingsStoreTest` 走的是内存里的假 DataStore，中文只活在内存里；本类既有的
     * [改称呼之后首页立刻不再问称呼] 读的也是 DataStore 的**内存值**（`settings.first()`），
     * 同样没看过磁盘上的字节。于是「UTF-8 编码的 CJK 文本落盘之后读回来还是原样」这件事，
     * 在第 56 轮之前没有任何用例管。
     *
     * 三层断言，一层比一层深：
     *
     * 1. 内存里读回来的等于输入的那串；
     * 2. **磁盘字节里真的含有这串的 UTF-8 编码**（「熊勇春」是 9 个字节，见 [等磁盘上出现]）；
     * 3. 把落盘文件**抄一份到新路径**，用全新的 DataStore 起一个 `SettingsStore` 冷读，
     *    读出来还是这串。
     *
     * 边界必须写清，不然这条用例会被当成它证不了的东西：
     *
     * - `performTextInput` 走的是 `InputConnection`，跟输入法提交文字是同一条通路，但它
     *   **不是输入法**。所以这里证的是「多字节文本进了 App 之后，经过输入框 → 校验 → 落盘 →
     *   冷读全程无损」，**不证**真实输入法的组合、候选、上屏那一段。这台机器的实情写在
     *   `docs/01` 的 GAP-02 行里：模拟器上只有 Gboard 与 Google 语音，没有可用的真中文输入法，
     *   所以那一段今天验不了。
     * - 第 3 层读的是**文件的副本**，路径与原件不同（换路径是为了避开 DataStore
     *   「同一个文件被两个实例打开」的 `IllegalStateException`）。它与真重启的唯一差别就是路径。
     */
    @Test
    fun 中文称呼落盘之后冷读回来仍然一字不差() {
        show()

        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.askAddress) == 1 }
        tap(HomeVoice.settingsAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }

        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(多字节称呼)
        tap(SettingsVoice.saveAddress)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.addressSaved) == 1 }

        // 第一层：内存里读回来就是输入的那串。
        assertEquals(多字节称呼, 当前设置().address)

        // 第二层：磁盘上的字节里真的有这串 UTF-8。理论上第一轮就命中——`setAddress` 的
        // `dataStore.edit` 是写完盘才返回的，上面等到「已保存」时字节已经在文件里了。
        // 那个重试循环是安全网，不是必需。
        val 期望字节 = 多字节称呼.toByteArray(StandardCharsets.UTF_8)
        val 落盘字节 = 等磁盘上出现(存储.settingsFile, 期望字节)
        assertTrue(
            "磁盘上 ${存储.settingsFile.name} 共 ${落盘字节.size} 字节，" +
                "里面找不到「$多字节称呼」的 UTF-8 编码（${期望字节.size} 字节）",
            containsBytes(落盘字节, 期望字节),
        )

        // 这条判据自己能不能说「没有」：拿一个同长度的邻串去问，必须是 false。
        // 少了这一句，「`containsBytes` 恒为真」这种坏法就没人管（ADR-047 的单边判据纪律）。
        assertFalse(
            "字节搜索对邻串也返回了真，说明这条判据没有分辨力",
            containsBytes(落盘字节, "熊勇秋".toByteArray(StandardCharsets.UTF_8)),
        )

        // 第三层：抄一份到新路径，用全新的 DataStore 冷读。
        val 冷读文件 = File(context.cacheDir, "elder-cjk-cold-${System.nanoTime()}.preferences_pb")
        冷读文件.writeBytes(落盘字节)
        val 冷读设置 = SettingsStore(
            PreferenceDataStoreFactory.create { 冷读文件 },
            container.clock,
        )
        assertEquals(
            多字节称呼,
            runBlocking(Dispatchers.IO) { 冷读设置.settings.first().address },
        )

        // 冷读这条同样要有反例：一样这么建一份 DataStore，只是文件是空的，称呼就该是 null。
        // 反过来说，这一层断的才真是「文件里的字节」，不是一个没人清的全局缓存。
        assertNull(
            runBlocking(Dispatchers.IO) {
                SettingsStore(
                    PreferenceDataStoreFactory.create {
                        File(context.cacheDir, "elder-cjk-empty-${System.nanoTime()}.preferences_pb")
                    },
                    container.clock,
                ).settings.first().address
            },
        )
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

    /**
     * 回执挂到「下一次动手」为止，而那一次动手**不落库**。
     *
     * 这是第 30 轮那条改动的正面口径：没有计时器之后，「回执还在不在」不再取决于这一跑有多快。
     * 断言分三步：保存后回执在场 → 再往输入框里打一个字 → 回执立刻不在场；而两次读库都是
     * 刚保存的那个称呼。后一件同时钉住另一件事：清回执发生在 `onValueChange` 里，
     * 不是「一打字就顺手存一下」——写不写库只由「保存称呼」那一下决定。
     */
    @Test
    fun 再打一个字回执就撤而且没有偷偷落库() {
        show()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.askAddress) == 1 }
        tap(HomeVoice.settingsAction)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }

        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput(NEW_ADDRESS)
        tap(SettingsVoice.saveAddress)

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.addressSaved) == 1 }
        assertEquals(NEW_ADDRESS, 当前设置().address)

        // 再动一次手。这一下打的字是任意的——这条用例断言的是「回执撤了、库没动」，
        // 不关心光标落在哪儿，所以不写成 `NEW_ADDRESS + 新字` 那种依赖插入位置的整串。
        compose.onNode(hasSetTextAction()).performTextInput("啊")

        assertEquals("再动一次手之后回执就该撤了", 0, count(SettingsVoice.addressSaved))
        assertEquals("回执撤掉不等于改了库：这一步一个字节都没写", NEW_ADDRESS, 当前设置().address)
        assertEquals("刚打字的那一下没有落库，输入框里还是刚保存的那个称呼", 1, countContaining(NEW_ADDRESS))

        // 回首页的口径与第一条用例同源：「怎么称呼您」这个入口由存下来的称呼决定（`needsAddress`），
        // 于是它依然不在。
        tapBack()
        compose.waitUntil(TIMEOUT_MILLIS) { count(HomeVoice.talkAction) == 1 }
        assertEquals(0, count(HomeVoice.askAddress))
    }

    /**
     * 点「更大」那一行的三件事：字号落库、屏幕上出现整串相等的回执、**回执长在字号卡里**。
     *
     * 第三条是本用例第 30 轮补的。改之前两张卡的回执共用一个状态、都渲染在**称呼卡**那一行，
     * 于是点完「更大」之后那句话出现在上方、可能已经滚出视口的地方——用户会以为没改成功
     * （见 ADR-028）。位置断言比「屏幕上有没有这句话」更能钉住这件事：`ElderScreen` 的内容区
     * 是普通滚动 `Column`（不是 `LazyColumn`），没进视口的子项也**已经组合**，所以
     * `boundsInRoot` 照样给出真实坐标，不必先滚动再量。
     */
    @Test
    fun 点更大那一行字号立刻落库() {
        show()
        从首页进设置页()

        tap(FontSize.EXTRA.label)
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.fontSizeChanged(FontSize.EXTRA.label)) == 1 }
        assertEquals(FontSize.EXTRA, 当前设置().fontSize)

        /** 某个整串文字节点的顶边（相对根布局）。找不到、或者不止一个，当场红得看得见。 */
        fun 顶边(text: String): Float {
            val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
            assertEquals("屏幕上整串为「$text」的节点应当正好 1 个", 1, nodes.size)
            return nodes[0].boundsInRoot.top
        }

        val 回执 = 顶边(SettingsVoice.fontSizeChanged(FontSize.EXTRA.label))
        val 称呼卡尾注 = 顶边(SettingsVoice.addressHint)
        val 字号卡标题 = 顶边(CommonVoice.fontSection)
        val 字号卡尾注 = 顶边(SettingsVoice.fontStackNote)
        assertTrue(
            "字号回执应当夹在字号卡标题与字号卡尾注之间、且在称呼卡尾注之下" +
                "（实际 回执=$回执 卡标题=$字号卡标题 卡尾注=$字号卡尾注 称呼卡尾注=$称呼卡尾注）",
            回执 > 字号卡标题 && 回执 < 字号卡尾注 && 回执 > 称呼卡尾注,
        )
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
     * 「说话的时机」这张卡不会哑掉（第 38 轮补）。
     *
     * 起因是一条 Android Lint 的 error：`ProduceStateDoesNotAssignValue` 指着这一页读画像的那三行，
     * 说「lambda 从不赋值、State 永不变」，也就是断言**卡片会永远停在初始那句「正在看您最近的作息。」**。
     * 第 38 轮在模拟器上量到的恰好相反（进设置页之后屏上读到的是「作息还在了解中：已经看了 0 天…」，
     * 初始那句不在场，见 `records/04-质量与验证记录.md` 第三十七节），所以那条 error 是**误报**；
     * 但那三行仍然换成了 lint 认得的等价写法（ADR-036）。这条用例把误报预测的那个症状反过来钉住：
     * **不许停在初始那句**。
     *
     * 三句结论里到底是哪一句，这条用例**故意不断言**：本类不往库里摆作息信号，于是当前是
     * `Gathering`（`Ready` 要凑满 `RhythmConfig` 里的那几天）。「恰有一句、且不是初始那句」
     * 已经够钉住「卡片拿到了画像」这件事，多断言只会让它随运行时刻忽红忽绿。
     *
     * 两句带参数的结论按**子串**计数（参数值取决于库里的天数），两句的前缀在 main 源码里各只出现过
     * 一次（`SettingsVoice.timingReady` / `timingGathering`），所以数不到别的节点身上。
     */
    @Test
    fun 说话的时机卡片会从正在看换成一句结论() {
        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { 结论句数() == 1 }

        assertEquals("卡片的初始那句应当已经不在场", 0, count(SettingsVoice.timingLoading))
        assertEquals("三句结论里应当恰有一句在场", 1, 结论句数())
    }

    /**
     * 第 47 轮：设置页「重新来一遍」卡里的**只读重看**，一个设置都不该动。
     *
     * 这条用例存在的理由是最实在的那种：在这之前，唯一能再见到引导三页的办法是清空设置，
     * 而清空会连称呼和字号一起丢掉。现在多了一条只看不写的路，那么「只看」就得分四个字段
     * 逐项钉住——称呼、字号、采集开关、引导完成标记，一样都不能变（AC-13.5）。
     *
     * 它同时是**唯一**能证「重看不落盘」的地方：JVM 那三条守卫
     * （`ReviewOnboardingPlacementTest`）只能证明「重看那条路上没有写盘的调用点」，
     * 证明不了跑起来真的没写。两边的分工写在 `docs/03` §8.18。
     *
     * 前置：称呼先存成「老熊」，所以库里的值本来就和出厂值不同——重看若偷偷写了
     * 一个空字符串或默认称呼，这里的第一个断言就会红。
     */
    @Test
    fun 看一遍开头那几页不会丢掉任何设置() {
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }
        show()
        从首页进设置页()

        tap(SettingsVoice.reviewGuideButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.nextButton) == 1 }
        tapPinned(OnboardVoice.nextButton)
        tapPinned(OnboardVoice.nextStep)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.reviewDone) == 1 }
        tapPinned(OnboardVoice.reviewDone)

        // 回到设置页才算这一趟走完（「看完了」走的是 popBackStack）。
        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }
        val 重看之后 = 当前设置()
        assertEquals("称呼不该被重看改动", NEW_ADDRESS, 重看之后.address)
        assertEquals("字号不该被重看改动", FontSize.STANDARD, 重看之后.fontSize)
        assertFalse("采集开关不该被重看改动", 重看之后.collectRhythm)
        assertTrue("引导完成标记不该被重看改动", 重看之后.onboardingDone)
    }

    /**
     * 第 47 轮：重看时第二页**一个能打字的地方都不能有**（AC-13.5 的后半句）。
     *
     * 三层断言各钉一件事，缺一层都会漏掉一类回归：
     * 1. 称呼以**整串相等的文字**在场——它还在，只是不再是输入框里的内容；
     * 2. 那个节点自己**不可用**（`assertIsNotEnabled`）——直接证明只读，
     *    比「点了没反应」强：后者分不清是只读还是事件被吞了；
     * 3. 整页**没有任何既带 set-text 动作、又处于可用状态的节点**——这是把
     *    「这一页上没有可输入的东西」当成一条全页性质来断，而不是只盯着称呼框那一个。
     *
     * 这里**不能用** `countContaining` 数称呼：预览卡那句问候语里也带着称呼
     * （`FontPreviewCard` 用真问候引擎渲染），子串匹配会数到 2 个。
     */
    @Test
    fun 重看时页面上没有可以打字的地方() {
        runBlocking(Dispatchers.IO) { container.settings.setAddress(NEW_ADDRESS) }
        show()
        从首页进设置页()
        进重看的第二页()

        assertEquals("称呼应当以文字在场", 1, count(NEW_ADDRESS))
        compose.onNodeWithText(NEW_ADDRESS).assertIsNotEnabled()
        assertEquals("重看的第二页不该有任何可编辑的节点", 0, 可编辑节点数())
        assertEquals(1, count(OnboardVoice.reviewReadOnlyNote))
    }

    /**
     * 第 47 轮：重看第一页的返回（表头那个箭头）回的是**设置页**，不是「引导还没走完」。
     *
     * 这条钉住的是那个最容易写错的细节：重看的目的地是普通目的地，退出用 `popBackStack()`
     * 就够；如果哪天有人把它改成「先写 `onboardingDone` 再退」，或者用 `popUpTo` 弹栈，
     * 这条与上面第一条会一起红——第一条从数据侧、这一条从界面侧。
     *
     * 第一页之所以有箭头，正是因为重看时 `onReviewExit` 不为空；首次引导时它是 null，
     * 所以这条用例同时从反面证了「首次引导第一页没有返回箭头」这个既有行为没被破坏。
     */
    @Test
    fun 重看第一页点返回就回到设置页() {
        show()
        从首页进设置页()

        tap(SettingsVoice.reviewGuideButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.nextButton) == 1 }
        tapBack()

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.saveAddress) == 1 }
        assertEquals("退回设置页之后不该还留着引导第一页", 0, count(OnboardVoice.nextButton))
    }

    /**
     * 第 47 轮：重看第三页只有「看完了」一个出口（AC-13.5）。
     *
     * 「同意 / 先不用」这一对在重看时**必须一个字都不出现**：它们不是过时文案的问题，
     * 而是这一点上用户根本没有可做的选择——采集与否当时已经问过、也已经答过了。
     * 留着它们会诱使用户以为可以在这里重答一次（底下那句 `bothChoiceNote` 讲的正是
     * 「两个按钮」，所以它也得换成重看版）。
     *
     * 这条与第一条**有意只各断各的一半**：第一条不断言按钮（只看数据），
     * 这一条不点「看完了」（只看这一页长什么样），两条加起来才是完整的一趟。
     */
    @Test
    fun 重看第三页只有看完了这一个出口() {
        show()
        从首页进设置页()
        进重看第三页()

        assertEquals("重看时只该有「看完了」这一个按钮", 1, count(OnboardVoice.reviewDone))
        assertEquals("重看时不该出现「同意」", 0, count(OnboardVoice.accept))
        assertEquals("重看时不该出现「先不用」", 0, count(OnboardVoice.decline))
        assertEquals("第三页末尾那句应当换成重看版", 1, count(OnboardVoice.reviewClosingNote))
        assertEquals("重看时不该再出现讲两个按钮的那句", 0, count(OnboardVoice.bothChoiceNote))
    }

    /**
     * 第 76 轮：设置页「说话的时机」卡要**看得见用户自己那条安静指令**，并且能当场撤销。
     *
     * 这张卡自己写下的存在理由就是「把 FR-05 的判定依据摆到台面上」，而「今天先安静一会儿」
     * 正是判定窗口的五个入参之一（AC-05.4）。补这条用例之前，用户按了首页那颗按钮、
     * 回这一页想核「它今天为什么不来搭话」，是一个字都看不到的。
     *
     * 它同时是唯一在**渲染层**证「撤销真的落到库上」的地方：Cancel 那句回执长在
     * `if (quietToday)` 外面（放里面会跟着自己报的那件事一起消失），所以这里既要读到回执，
     * 也要读到「上面那一整段收起来了」。
     */
    @Test
    fun 今天说过先安静一会儿的设置页上看得见也撤得掉() {
        runBlocking(Dispatchers.IO) {
            container.settings.setQuietOn(container.clock.now().toLocalDate().toEpochDay())
        }
        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.quietTodayLine) == 1 }
        assertEquals(1, count(SettingsVoice.quietCancel))

        tap(SettingsVoice.quietCancel)

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.quietCancelled) == 1 }
        assertEquals("撤销之后那一段该收起来", 0, count(SettingsVoice.quietTodayLine))
        assertEquals("撤销之后按钮也该收起来", 0, count(SettingsVoice.quietCancel))
        assertNull("撤销要真的落到库上", 当前设置().quietEpochDay)
    }

    /**
     * 第 77 轮：「说话的时机」卡上那行间隔被推到最长之后，用户要有一条**看得见、按得动**的回头路。
     *
     * 补这条用例之前那扇门是只进不出的：连续九次开口都没等到人接，间隔就退到 24 小时，
     * 而唯一能让它回落的途径是「接到一次招呼」——那恰恰是 24 小时之后才会发生的事。
     * 用户没有任何办法把它拉回来，卡上却一直写着「24 小时」。
     *
     * 这里在**渲染层**证三件事：① 计数大于 0 时说明句与出口按钮都在；② 按下去之后那一行
     * 间隔当场变成最短那一档、说明句与按钮一起收起来、回执留下；③ 这不是摆样子，库里那
     * 三个键真的被改了（`ignoredStreak` 归零）。
     *
     * 两行间隔的字**必须**用 `SettingsVoice.gapLine(...)` 现拼，不写死中文：卡片印的就是
     * 这两句，写死等于把「卡上印什么」这件事抄进用例，改坏了也照绿。
     */
    @Test
    fun 间隔被推到最长时卡上给出出口按下去就回到最短() {
        // 九次开口、一次都没被接话：头一次没有可比的上一次，不算被忽略，之后每次加一。
        val 造出来的streak = runBlocking(Dispatchers.IO) {
            repeat(9) { container.interventionStore.recordSpoken() }
            container.interventionStore.stateOnce().ignoredStreak
        }
        val 长间隔那句 = SettingsVoice.gapLine(
            container.interventionConfig.gapMinutes(造出来的streak).asHumanGap(),
        )
        val 最短那句 = SettingsVoice.gapLine(
            container.interventionConfig.minGapMinutes.asHumanGap(),
        )

        assertEquals(8, 造出来的streak)
        assertEquals(
            "这一档要是没顶到上限，下面那条「上限」的断言就白测了",
            container.interventionConfig.maxGapMinutes,
            container.interventionConfig.gapMinutes(造出来的streak),
        )
        assertFalse("两句话必须是两句不同的话，否则下面的计数没有分辨力", 最短那句 == 长间隔那句)

        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { count(长间隔那句) == 1 }
        assertEquals(1, count(SettingsVoice.gapStretchedNote))
        assertEquals(1, count(SettingsVoice.presentNow))
        assertEquals(0, count(最短那句))

        tap(SettingsVoice.presentNow)

        compose.waitUntil(TIMEOUT_MILLIS) { count(SettingsVoice.presentNowDone) == 1 }
        assertEquals("按下去之后那一行要当场变成最短那一档", 1, count(最短那句))
        assertEquals("说明句该跟着收起来", 0, count(SettingsVoice.gapStretchedNote))
        assertEquals("按钮也该跟着收起来", 0, count(SettingsVoice.presentNow))
        assertEquals(
            "清零要真的落到库上",
            0,
            runBlocking(Dispatchers.IO) { container.interventionStore.stateOnce() }.ignoredStreak,
        )
    }

    /**
     * 第 79 轮：补上面那条用例只关掉的一半——**没被忽略过时，那段出口一个字都不占**。
     *
     * 上面那条只证了「计数大于 0 ⇒ 说明句与按钮都在场」。反过来的那一半（计数为 0 ⇒ 两样都不在）
     * 此前没有任何用例：判据写成 `>= 0` 之类的错法，屏幕上会多出一颗按下去什么都不变的死按钮，
     * 而所有既有用例照绿。门控这一轮已经整段抽到 `SettingsVoice.gapExitOf`，边界在
     * `SettingsVoiceTest.出口只在计数大于零时才开门` 里逐一钉过；这一条留在渲染层是为了一件
     * 纯函数层证不了的事：**卡上印的那行间隔确实是最短那一档**——也就是「整段收起来」发生在
     * 正确的处境里，而不是「卡片压根没渲出来」。
     *
     * ## 为什么起点得先按一下首页那颗「今天先安静一会儿」
     *
     * 「计数为 0」不是开局就有的形状：首页每次露面只要判成要说话，就会记一次
     * `InterventionStore.recordSpoken`（`HomeViewModel.open()`），计数当场变成 1、出口当场出现。
     * 所以这条用例先摆一个「今天安静」，让首页那一次开口本来就不该发生——这正是
     * `open()` 自己写的口径（用户明确说了先别说，这一次开口没发生，不该计入）。
     * 计数于是停在 0，出口该整段不在。
     *
     * ## 为什么第一条断言不是 `== 0`
     *
     * 锚点：拿最短那一档的间隔句先等到它 `== 1`。卡片真在屏上、上面那个数真是最短那一档——
     * 没有这个锚点，后面每一句 `== 0` 都可能是因为卡片根本没渲出来而假绿。
     * 同一处还有第二层前置条件：库里那个计数**真的是 0**，不是「没读出来所以当 0 用」。
     *
     * 两句间隔（最短那一档、最长那一档）都必须用 `SettingsVoice.gapLine(...)` 现拼，不写死中文：
     * 卡片印的就是这两句，写死等于把「卡上印什么」抄进用例，改坏了也照绿。
     */
    @Test
    fun 没被忽略过时卡上那扇出口一个字都不占() {
        val 最短那句 = SettingsVoice.gapLine(
            container.interventionConfig.minGapMinutes.asHumanGap(),
        )
        val 最长那句 = SettingsVoice.gapLine(
            container.interventionConfig.maxGapMinutes.asHumanGap(),
        )

        assertFalse(
            "最短那一档与最长那一档说出了同一句话，下面「印的是最短那句」就没有分辨力",
            最短那句 == 最长那句,
        )
        // 先让首页那一次开口不该发生，计数才有可能停在 0。
        runBlocking(Dispatchers.IO) {
            container.settings.setQuietOn(container.clock.now().toLocalDate().toEpochDay())
        }

        show()
        从首页进设置页()

        compose.waitUntil(TIMEOUT_MILLIS) { count(最短那句) == 1 }
        assertEquals("锚点：卡上那行间隔是最短那一档，说明卡片真的在屏上", 1, count(最短那句))
        assertEquals("锚点：安静那一行也在场，说明今天那一次开口确实没发生", 1, count(SettingsVoice.quietTodayLine))
        assertEquals(
            "前置条件：这次要走的是「一次都没被忽略过」那条路，计数得真的是 0",
            0,
            runBlocking(Dispatchers.IO) { container.interventionStore.stateOnce() }.ignoredStreak,
        )
        assertEquals("卡上印的是最短那一档，反过来也说明最长那句不在", 0, count(最长那句))
        assertEquals("计数为 0 时，上面那句说明不该在", 0, count(SettingsVoice.gapStretchedNote))
        assertEquals("计数为 0 时，那颗出口按钮也不该在——它按下去什么都不会变", 0, count(SettingsVoice.presentNow))
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

    /**
     * 「说话的时机」那张卡的三句结论里，现在在场的有几句。
     *
     * 前两句带参数（`SettingsVoice.timingReady` / `timingGathering`），参数值取决于库里攒了几天的
     * 信号，所以按各自的前缀做子串计数；第三句是常量，整串相等地数。
     */
    private fun 结论句数(): Int =
        countContaining("按您自己的作息：") +
            countContaining("作息还在了解中：") +
            count(SettingsVoice.timingInconsistent)

    private fun tap(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    /**
     * 点一个**钉在页面底部**的动作。第 50 轮起引导页的动作区搬进了 `ElderScreen` 的 `footer`，
     * 它落在滚动容器之外，对这类节点做 `performScrollTo` 会直接抛
     * 「Semantic Node has no parent layout with a Scroll SemanticsAction」（同 [tapInDialog] 的处境）。
     * 钉底的动作本来就该一直看得见，所以这里不是不滚，而是要求它**真的显示在屏幕上**。
     */
    private fun tapPinned(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { count(text) > 0 }
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
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
     * 从设置页点进「再看一遍开头那几页」，把引导第一页**看完**再按「下一步」，
     * 停在重看的第二页上（等到「下一步」在场才回来）。
     *
     * 两处等待都不是多余的：`tapPinned()` 自己只等「这个字在场」，而重看是导航出来的新目的地，
     * 不等它渲染完就往下点，`onNodeWithText` 会找不到节点。第二处等的是**第二页**的特征
     * （「下一步」是第一、二页共有的；第二页独有的是「下一步」下面那句「回去看上一页」，
     * 但用「下一步」在场 + 一次点击后的重组就够了，重读更细的特征在这里没有额外信息）。
     */
    private fun 进重看的第二页() {
        tap(SettingsVoice.reviewGuideButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.nextButton) == 1 }
        tapPinned(OnboardVoice.nextButton)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.nextStep) == 1 }
    }

    /** 再往下走一步，停在重看的第三页上（等到「看完了」在场）。 */
    private fun 进重看第三页() {
        进重看的第二页()
        tapPinned(OnboardVoice.nextStep)
        compose.waitUntil(TIMEOUT_MILLIS) { count(OnboardVoice.reviewDone) == 1 }
    }

    /**
     * 屏幕上**既带 set-text 动作、又处于可用状态**的节点有几个。
     *
     * 这是「这一页没有可以打字的地方」那条断言的口径。两个条件都必要：只看动作的话，
     * 一个 `enabled = false` 的输入框照样带动作（重看时的称呼框正是这样）；
     * 只看可用的话，会数上一堆按钮。
     */
    private fun 可编辑节点数() =
        compose.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size

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

    /**
     * 等 [文件] 里出现 [标记] 这段字节，返回**那一刻**读到的整份内容。
     *
     * DataStore 落盘是**整份重写**，同一份 `Preferences` 序列化出来的字节序是稳定的，所以
     * 「出现过」之后不会再变。等到就返回；超时返回最后一次读到的内容（读不到就是空数组），
     * 让调用方的断言去报红——这里不抛，免得把「字节丢了」这个失败报成「超时」。
     */
    private fun 等磁盘上出现(文件: File, 标记: ByteArray): ByteArray {
        val 截止 = System.currentTimeMillis() + TIMEOUT_MILLIS
        var 内容 = 读一份(文件)
        while (!containsBytes(内容, 标记) && System.currentTimeMillis() < 截止) {
            Thread.sleep(20)
            内容 = 读一份(文件)
        }
        return 内容
    }

    /** 文件还没被建出来的时候当成空内容，别让 `readBytes()` 抛 `FileNotFoundException`。 */
    private fun 读一份(文件: File): ByteArray = if (文件.exists()) 文件.readBytes() else ByteArray(0)

    /** [整份] 里有没有连着的一段等于 [标记]。 */
    private fun containsBytes(整份: ByteArray, 标记: ByteArray): Boolean {
        if (标记.size > 整份.size) return false
        for (起点 in 0..(整份.size - 标记.size)) {
            var 命中 = true
            for (位 in 标记.indices) {
                if (整份[起点 + 位] != 标记[位]) {
                    命中 = false
                    break
                }
            }
            if (命中) return true
        }
        return false
    }

    private companion object {
        /** 等界面反应的上限。超时就是红，宁可慢一点也不要飘。 */
        const val TIMEOUT_MILLIS = 5_000L

        /** 这个用例自己的库文件：应用那份 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-settings-screen.db"

        const val NEW_ADDRESS = "老熊"

        /**
         * 三个 CJK 字符、UTF-8 下 9 个字节，跟 [NEW_ADDRESS]（两个字符、6 字节）故意区分开：
         * 这个常量是为 [中文称呼落盘之后冷读回来仍然一字不差] 准备的，要能一眼看出
         * 「磁盘上那段字节就是这 9 个字节」，而不是碰巧跟别的串对上。
         */
        const val 多字节称呼 = "熊勇春"
    }
}
