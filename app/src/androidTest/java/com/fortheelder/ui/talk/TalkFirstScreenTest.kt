package com.fortheelder.ui.talk

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 「说两句」**首屏**能看到什么（第 61 轮，设备级）。
 *
 * 第 61 轮把五句现成的话从整页最后一张卡搬到了「说一句」那张卡里、输入框下面。理由是可达性：
 * 它要解决的场景是用户一时想不出说什么，而那一刻人最容易做的动作是放弃，不是往下滑。
 *
 * 源码形态由 `TalkScreenOrderTest`（JVM）钉住，但它证明不了**屏幕上真的看得见**——
 * 「摆在文档流的前面」与「落在这一屏的滚动区里」是两件事。这一条用例因此只在设备上做一件事：
 * `show()` 之后**一次滚动都不做**，直接问那几句话是不是 displayed。
 *
 * 两处必须交代的边界（`records/04-质量与验证记录.md` 里也记着同样的读数）：
 *
 * 1. **只断言本机量到的三句**（`我一直想学书法` / `想学用手机` / `最近老是睡不着`）。
 *    第 61 轮在本机（1080×2400、dpi 420、系统默认字号档）量到滚动区下沿 2337 px，
 *    第 4 句 `帮我找找附近能下棋的地方` 与第 5 句 `今天天气还行` 整条在这条线下面，
 *    uiautomator 的语义树里连节点都没有——**「五句都进首屏」这一轮没有达成**，
 *    要上滑才看得见。所以这里绝不断言「五句都在首屏」，也**不断言第 4、5 句不在**——
 *    换一块更高的屏它们就会露出来，那样写出来的是一条会随设备变红的假守卫。
 * 2. **这几句是脚本点出来的，不是真人**：设备上的操作由 `build/r61/probe61.py` 用
 *    `adb shell input tap` 发出，点的人始终是开发者本人。这条用例只代替「看得见」这一半，
 *    代替不了「老人会不会去点」。
 *
 * 方法名沿用这个目录的约定：中文、**不含 ASCII 空格**（minSdk 26 下 D8 只认 DEX 040，
 * 注释掉之后带空格的 backtick 方法名会让整个 androidTest 编不出来，第 60 轮真踩过）。
 */
@RunWith(AndroidJUnit4::class)
class TalkFirstScreenTest {

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
     * 容器与库文件都是这个用例自己的（[TEST_DB]），理由与 `TalkInputTest` 里写的一样：
     * 应用那份 `ElderApp.container` 用的是正式库 `elder.db`，同进程里别的数据用例会把它删掉重开。
     */
    private lateinit var container: AppContainer

    /** 两份偏好也归它自己，不传的话写的会是这台设备上应用真正的设置（见 `自己的存储`）。 */
    private lateinit var 存储: 自己的存储

    @Before
    fun 用一份空的库把这一页摆出来() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
    }

    private fun show() {
        compose.setContent {
            ForTheElderTheme {
                TalkScreen(container = container, onBack = {})
            }
        }
    }

    /**
     * 一次 `performScrollTo()` 都不发：这一条用例问的就是「不滚动能看见什么」。
     * 真要滑一下才能看见的节点在这里会红，这正是想要的摩擦。
     */
    private fun 不滚动就应该看得见(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun 首屏不滚动就看得见示例句的标题与本机容得下的那三句() {
        show()

        // 标题：没有它，下面那几行就只是几行莫名其妙的话。
        不滚动就应该看得见(TalkVoice.examplesSection)

        // 本机（1080×2400 / dpi 420 / 默认字号档）滚动区下沿 2337 px 之内的三句。
        for (sentence in 本机首屏容得下的三句) {
            不滚动就应该看得见(sentence)
        }
    }

    @Test
    fun 首屏不滚动就看得见自己写一句的输入框() {
        show()

        // 主干动作是「自己写一句」，示例句只是它的退路：输入框必须在首屏、且排在示例句之前。
        // 顺序由 JVM 的 `TalkScreenOrderTest` 在源码形态上钉住，这里只确认它真的在屏上。
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        不滚动就应该看得见(TalkVoice.submit)
    }

    private companion object {
        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-talk-first-screen.db"

        /**
         * 第 61 轮、本机默认字号档下量到**不用上滑**的那三句（顺序与 `TalkExamples.sentences`
         * 一致）。第 4、5 句要上滑——没达成的事不写成断言，写进 `records/04` 与 `docs/09`。
         */
        val 本机首屏容得下的三句 = listOf(
            "我一直想学书法",
            "想学用手机",
            "最近老是睡不着",
        )
    }
}
