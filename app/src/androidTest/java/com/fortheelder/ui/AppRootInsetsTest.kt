package com.fortheelder.ui

import android.app.Activity
import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.fortheelder.core.AppContainer
import com.fortheelder.ui.onboard.OnboardVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 键盘让位「真的发生在根层」的设备级验证（第 55 轮）。
 *
 * ## 为什么要有这一条
 *
 * 第 55 轮在设备上量出来：键盘弹起时 IME inset 是 883px（`dumpsys window displays` 的
 * `InsetsSource type=ime`），而根层 `AppRoot` 的 `Modifier.safeDrawingPadding()` 已经把它
 * 消费掉了——`ElderScreen` 里那两次 `imePadding()` 读到的恒为 0，是死代码（第 55 轮已删）。
 *
 * 这条用例要钉住的不是「删得对不对」（删的是死代码，删前删后屏幕上每个像素都一样），
 * 而是**根层那一句到底管不管键盘**。它是整个模型的地基：如果哪天有人把
 * `AppRoot` 的 `safeDrawingPadding()` 换成只处理状态栏的写法、或者干脆挪到某个屏幕去，
 * 屏幕上就会有人被键盘盖住，而 JVM 侧那条源码守卫（`InsetsOwnerGuardTest`）只数了「几次」，
 * 认不出「管不管键盘」。所以这里**派发一份合成的 ime inset** 到宿主窗口上，再量底部动作区
 * 挪了多少。
 *
 * ## 这条用例的性质：它是「变异可红」的
 *
 * 合成 inset 是**替换**而不是追加：只声明系统栏与键盘，其余类型一律为 0。于是
 * 「键盘弹起」这一刻的安全区底边从「导航栏 63px」变成「键盘 800px」，[ElderScreen] 的
 * 钉底动作区（引导第 1 页的「下一步」）应当整体上移 `800 − 63 = 737px`——**正好一次**。
 * 把 `AppRoot` 的 `.safeDrawingPadding()` 摘掉，这条断言当场变红（上移 0）；这正是红态证据。
 *
 * ## 它不保证什么
 *
 * 1. **它认不出「让位两次」**。第 55 轮删掉的那两次 `imePadding()` 是死代码，删前删后
 *    位移量一模一样，所以这条用例在旧代码上也会绿。「同一份 inset 只许消费一次」这条
 *    不变量由源码级守卫 [com.fortheelder.ui.InsetsOwnerGuardTest] 承担（数调用次数），
 *    两条守卫一个管「管不管」、一个管「几次」，缺一不可。
 * 2. **它测的是合成 inset，不是真键盘**。真实 IME 会让 `windowSoftInputMode`、
 *    `insetsHint`、窗口是否 resize 一起参与，这里一个都没有。真实的键盘链路是人工走查
 *    （`records/04-质量与验证记录.md` 的第 55 轮）量的。
 * 3. **它只覆盖本机分辨率**（1080×2400 / dpi 420），且断言里的 `737` 不是写死的：
 *    期待值由运行时的安全区底边算出来，换设备仍然自洽。
 */
@RunWith(AndroidJUnit4::class)
class AppRootInsetsTest {

    private val compose = createComposeRule()

    /** 关库这一步在 compose 规则**外面**，收尾顺序的理由见 [关库排在组合之后]。 */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)

    private lateinit var container: AppContainer

    @Before
    fun 摆好() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val 存储 = 自己的存储(context, TEST_DB)
        // 引导未完成 ⇒ AppRoot 直接进第 1 页，这一页有钉底动作区，正好当尺子用。
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
    }

    /**
     * 合成一份只换键盘的 inset：系统栏与刘海照抄真实值，键盘换成给定高度。
     *
     * 不抄真实值的话，「上移多少」就会被导航栏的变化污染——那样算出来的位移虽然还是对得上，
     * 但不再能说明「挪的正是键盘那一份」。
     */
    private fun 造合成inset(真: WindowInsets, 键盘高: Int): WindowInsets =
        WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), 真.getInsets(WindowInsets.Type.systemBars()))
            .setInsets(
                WindowInsets.Type.displayCutout(),
                真.getInsets(WindowInsets.Type.displayCutout()),
            )
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 键盘高))
            .build()

    /** 前台的宿主 Activity（compose 规则启的那个）。拿不到就当场失败，不静默跳过。 */
    private fun 前台宿主(): Activity {
        var 名单: List<Activity> = emptyList()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            名单 = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .toList()
        }
        assertTrue(
            "前台上一个 Activity 都没有（${名单.size} 个），拿不到窗口就把合成 inset 派不进去",
            名单.size == 1,
        )
        return 名单.single()
    }

    @Test
    fun 键盘弹起时底部动作区只上移一份键盘高度() {
        compose.setContent { AppRoot(container) }

        val 动作区 = compose.onNodeWithText(OnboardVoice.nextButton)
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(OnboardVoice.nextButton).fetchSemanticsNodes().size == 1
        }
        val 改动前 = 动作区.fetchSemanticsNode().boundsInRoot

        val 宿主 = 前台宿主()
        val 根视图 = 宿主.window.decorView
        val 真inset = rootWindowInsetsOf(根视图)

        val 键盘高 = 800
        val 安全区改动前底边 = 安全区底边(真inset)
        val 安全区改动后底边 = maxOf(安全区改动前底边, 键盘高)

        compose.runOnUiThread {
            根视图.dispatchApplyWindowInsets(造合成inset(真inset, 键盘高))
        }
        compose.waitForIdle()

        val 改动后 = 动作区.fetchSemanticsNode().boundsInRoot
        val 实际上移 = 改动前.bottom - 改动后.bottom
        val 期待上移 = (安全区改动后底边 - 安全区改动前底边).toFloat()

        assertEquals(
            "派发合成键盘 inset（$键盘高 px）之后，钉底动作区应当正好上移一份「键盘高度 − 导航栏」=" +
                "$期待上移 px。上移 0 说明根层的 safeDrawingPadding 没在管键盘（或者被挪走了）；" +
                "上移约两倍说明同一份 inset 被消费了两次。",
            期待上移,
            实际上移,
            1f,
        )
        assertEquals(
            "动作区应该是整块上移：上下两边的位移必须一样，这里上边挪了 " +
                "${改动前.top - 改动后.top} px、下边挪了 $实际上移 px，说明它是被压扁了而不是被抬起来。",
            实际上移,
            改动前.top - 改动后.top,
            1f,
        )
    }

    /** `rootWindowInsets` 可能是 null（窗口还没收到过任何 inset）；那时要报清楚，不要报 NPE。 */
    private fun rootWindowInsetsOf(视图: android.view.View): WindowInsets {
        val 值 = 视图.rootWindowInsets
        assertTrue(
            "宿主窗口还没有任何 insets（rootWindowInsets 是 null），合成 inset 无处安放，" +
                "这条用例此刻等于没测",
            值 != null,
        )
        return 值!!
    }

    /**
     * 安全区底边 = 「系统栏 / 刘海 / 键盘」三类里最靠上的那一道。
     *
     * 平台上**没有** `WindowInsets.Type.safeDrawing()`——`safeDrawing` 是 androidx 与 Compose
     * 的概念，这里按定义自己取最大值。第 55 轮第一次写这条用例时就是照着 Compose 的命名
     * 去调平台 API，编译不过（`Unresolved reference 'safeDrawing'`），记在
     * `records/04-质量与验证记录.md`。
     */
    private fun 安全区底边(inset: WindowInsets): Int = maxOf(
        inset.getInsets(WindowInsets.Type.systemBars()).bottom,
        inset.getInsets(WindowInsets.Type.displayCutout()).bottom,
        inset.getInsets(WindowInsets.Type.ime()).bottom,
    )

    private companion object {
        const val TEST_DB = "elder-test-approot-insets.db"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
