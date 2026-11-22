package com.fortheelder.ui.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.onboard.OnboardVoice
import com.fortheelder.ui.theme.ForTheElderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 滚动兜底提示的**渲染级**验证（第 51 轮）。
 *
 * 第 50 轮把引导页的动作区钉到了屏幕底部，但内容区本身仍会裁：那一轮的整屏首屏实测里，
 * 引导第 2 页有一整块「之后还有内容」落在屏幕外，而滚动对 55 岁以上用户约等于不存在。
 * 这一轮给 `ElderScreen` 加了一层浮在滚动区底部的提示（`ScrollMoreHint`），
 * 这一组用例要钉住的是五件事——**放不下才出现、最后一行确实得靠滚动才看得见、
 * 提示不吞掉它压住那一带的点击、起手落在提示身上也推得动内容、首帧不闪**。
 *
 * 第三条是第 51 轮**首跑的红**，也是这一版产品码改动的由来。首跑时浮标整块可点（点一下滚到底），
 * 同一次 L3 有 11 条既有用例点不动（`SettingsScreenTest` / `TalkInputTest` / `TalkStarterTest`，
 * 都是 `performScrollTo().performClick()` —— `performScrollTo` 只做最小滚动，目标停在视口底边，
 * 中心正好落进浮标盖住的那一条带）。**但「根因＝浮标可点」这个结论后来被实测推翻了**：
 * 只把 `clickable` 去掉，这 11 条一条没好，本条用例（专门守这一带的点击）仍然红。
 * 反编译 `material3-android:1.3.1` 的 `SurfaceKt` 才查实机制——**非可点的 `Surface` 重载
 * 也在触摸链上挂了 `SuspendingPointerInputFilterKt.pointerInput`**（`SurfaceKt$Surface$1` 里调用，
 * lambda 是 `SurfaceKt$Surface$1$3`），官方 KDoc 称之为「blocking touch propagation behind the surface」。
 * 所以修法是**底色不再用 `Surface`**，改成 `Box` 的 `background`，一个触摸节点都不留在链上。
 * 这一层的 `heightIn(min = )` 保留：遮挡量还是 64dp（本机 168 px），与上一版同量、跨轮可比。
 * **「一键到底」这个便利是被主动放弃的**，不作为没发生过。
 *
 * 为什么用自造的屏而不是直接渲染引导页：这几条断言问的是**判定本身**，判定的全部输入
 * 就是「内容高度 vs 视口高度」，自造屏能把这两个量摆成确定的（40 行长内容 / 1 行短内容），
 * 不受字号档、称呼、引导进度这些外部变量影响。真实几屏上到底出不出提示，
 * 由同轮的装机走查（`build/probe51.py` 的 uiautomator 节点树）去量，不在这组用例里假称。
 *
 * 五条用例覆盖不了的事，照实记在这里：
 * 1. 首帧那条只能证明「第一帧的语义树里没有提示」；它证明不了像素上没闪——
 *    截图与帧的对应关系这台环境里量不了（见 `records/04` 的诚实边界）。
 * 2. 「最后一行的底边进了屏幕」证明的是**滚动位置**到了头，不是「老人因此少漏看内容」。
 *    后者要用户测试，本轮没有，不做任何可用性宣称。
 * 3. 去掉手势之后，浮层**仍然盖住滚动区底部 64dp 的内容**（本机 168 px、屏高 2400 px），
 *    它只是不再吞点击；那一段的视觉遮挡量与上一版相同，对阅读的影响本轮也没量。
 * 4. 「起手落在提示身上也推得动」量的是**这一屏、这一个起手点**：起手点取的是提示文字自己的
 *    中心，浮层左右两角与更靠下的边沿没有逐点扫过；探针那边（`build/`，不进版本库）做了
 *    五组同距离、只换落点的对照，也都动，但它同样不是全带穷举。
 */
@RunWith(AndroidJUnit4::class)
class ScrollMoreHintTest {

    @get:Rule
    val compose = createComposeRule()

    /** 屏上还剩几行这种长内容。40 行在任何字号档下都撑不过一屏（最矮的一屏也放不下）。 */
    private val 长内容行数 = 40

    private fun 有几处(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * 屏幕底边，取**像素**（`SemanticsNode.boundsInRoot` 的单位）。
     *
     * 第 51 轮首跑这条用例红过一次，原因就在单位：`SemanticsNode.boundsInRoot` 是像素，
     * 而 `SemanticsNodeInteraction.getBoundsInRoot()` 是 dp（本机 1 dp = 2.625 px）。
     * 两个数直接比大小必然误判——**2316 px 与 914.29 dp** 一比就成了「压在屏外」，
     * 可那 2316 px 其实在 2400 px 的屏里、好好的。产品那条链当时是对的
     * （同一条用例里「点完提示它就消失」先过了，说明确实滚到了底），错的是这条判定。
     * 两边统一到像素之后，断言才是它字面上的意思。
     */
    private fun 屏幕底边(): Float = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom

    private fun 摆一屏(行数: Int) {
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    repeat(行数) { Text(OnboardVoice.nextButton) }
                }
            }
        }
    }

    /**
     * 用真实手势往上滑到底。
     *
     * 手势落在**根节点**上、不是落在浮层上：浮层不含手势，触摸会穿透到下面的滚动内容。
     * 滑的次数取一个够用的常数——一次快滑带 40 行的惯性，几下滑到头的余量不需要精确，
     * 到位之后的空滑不产生位移。
     */
    private fun 滑到底() {
        repeat(10) {
            compose.onRoot().performTouchInput { swipeUp(durationMillis = 100) }
        }
    }

    @Test
    fun 内容一屏放得下时不出现滚动提示() {
        摆一屏(行数 = 1)

        compose.onNodeWithText(OnboardVoice.nextButton).assertIsDisplayed()
        assertEquals("内容都放得下，提示不该出现", 0, 有几处(CommonVoice.scrollMore))
    }

    @Test
    fun 内容放不下时出现提示而且最后一行得靠滚动才看得见() {
        val 末尾一条文案 = "末尾那一条"
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    repeat(长内容行数 - 1) { Text(OnboardVoice.nextButton) }
                    Text(末尾一条文案, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        compose.onNodeWithText(CommonVoice.scrollMore).assertIsDisplayed()
        assertEquals("提示文字在树里出现了不止一处", 1, 有几处(CommonVoice.scrollMore))

        // 先把标尺取出来：同文的那些行里总有完整可见的，取最大高度就是**完整行高**。
        val 完整行高 = compose.onAllNodesWithText(OnboardVoice.nextButton)
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.height }

        // 「确实放不下」这条要说成两种都算：这一行要么被视口裁掉一部分、要么整行落在屏外。
        // 视口裁剪报零高还是报裁过的矩形，由 Compose 决定，两种都意味着这一刻用户看不全它，
        // 这里不替它选一种，也不把两种混成一句「压在外」——那正是第 51 轮首跑误判的来源。
        val 滚之前 = compose.onNodeWithText(末尾一条文案).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "滚之前最后一行应当是残缺的（被裁掉一部分或整行在屏外），实际 $滚之前，" +
                "完整行高 $完整行高，屏幕底边 ${屏幕底边()}——这一屏根本没溢出，用例证明不了任何事",
            滚之前.height < 完整行高 - 0.5f || 滚之前.bottom > 屏幕底边() + 0.5f,
        )

        // 用真实手势滑到底，**不是**去点浮标：那一层不含手势也不许拦触摸（理由见类注释）。
        // 这条同时守着「滑动手势没被浮层挡掉」。
        滑到底()

        // 滚到底之后 canScrollForward 为假，提示自己就从组合里退出了——这既是「滚动确实发生了」
        // 的证据，也是「提示只在该在的时候在」。
        compose.waitUntil(超时毫秒) { 有几处(CommonVoice.scrollMore) == 0 }

        val 滚之后 = compose.onNodeWithText(末尾一条文案).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "滑到底之后最后一行还是残缺的（实际 $滚之后，完整行高 $完整行高）：没滚到头",
            滚之后.height >= 完整行高 - 0.5f,
        )
        assertTrue(
            "滑到底之后最后一行（底边 ${滚之后.bottom}）还压在屏幕（底边 ${屏幕底边()}）之外：没滚到底",
            滚之后.bottom <= 屏幕底边() + 0.5f,
        )
    }

    /**
     * 提示**不许**吞掉它盖住那一带的点击。这是第 51 轮那次首跑红的回归守卫。
     *
     * 造法照着那次红的形态来：一整屏长内容，末尾放一条**通栏可点**的行。`performScrollTo()`
     * 会把目标推到视口的底边，也就是浮层正盖着的那一带——首跑时这一次点击落不上，
     * 末尾这条行收不到；同一次 L3 里 11 条既有用例就是这么点不动的。
     *
     * 这条在改法上出过一次错，留在这里当证据：把浮标的 `clickable` 去掉之后**它还是红的**
     * （首跑改码后的真机消息是 `落在滚动区底部那一带的点击被浮层吃掉了 expected:<1> but was:<0>`）。
     * 真正的原因是底色用了 `Surface`——**非可点的 `Surface` 重载也会往触摸链上挂
     * `pointerInput`**（反编译 `material3-android:1.3.1` 的 `SurfaceKt$Surface$1` 查实），
     * 改成 `Box` 的 `background` 之后这条才绿。这一条守的就是那个换了底色的行为。
     */
    @Test
    fun 提示不吞掉它盖住那一带的点击() {
        val 点到了 = mutableIntStateOf(0)
        val 末尾文案 = "末尾那一条"
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    repeat(长内容行数) { Text(OnboardVoice.nextButton) }
                    Text(
                        text = 末尾文案,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 点到了.intValue++ },
                    )
                }
            }
        }

        // 先坐实浮层确实压在这一带上：它不出现的话，这条用例什么都没测。
        compose.onNodeWithText(CommonVoice.scrollMore).assertIsDisplayed()

        compose.onNodeWithText(末尾文案).performScrollTo().performClick()

        assertEquals("落在滚动区底部那一带的点击被浮层吃掉了", 1, 点到了.intValue)
    }

    /**
     * 起手点落在**提示自己身上**的上滑，也要推得动下面的内容。
     *
     * 上一条用例的 `滑到底()` 用的是 `swipeUp()` 的默认落点（根节点底边那一条线），
     * 只擦到浮层的一条下沿，是个**弱形态**：它挡不住「浮层中心附近吞滑动」这一种坏法。
     * 而这一格在 `build/probe51.py` 里是量过的（同一屏、同距离，只换起手落点，五组对照都动），
     * 可 `build/` 下的探针不进版本库，所以这里要留一条**跟着代码走**的强守卫：
     * 起手点取提示文字自己的中心，滑 300 px，看内容有没有被推上去。
     *
     * 后面那条「滑完提示还在」是顺手的：只滑了 300 px，40 行的屏离底还早，
     * 「推得动」不等于「该消失」——两条一起才说明这层只是个看得见的浮标。
     *
     * **标尺从头一版末尾那一行换成屏幕中部那一行，是第 51 轮第二次真机红留下的教训。**
     * 头一版量的是末尾那一行的 `top`，真机报文是「滚之前 top=0.0，滚之后 top=0.0」——
     * 末尾行在整段滑动里一直处于「被视口裁掉」的状态，而**完全被裁剪 / 滚出视口的节点，
     * Compose 报的是全零矩形**（同一份文件另一条用例早就依赖这个性质：`height` 报 0 才算「放不下」）。
     * 也就是说 `top` 这个量对滚动**根本不敏感**，拿它判「推不推得动」必然误判成「没推动」——
     * 首跑那条红是**标尺坏**，不是产品坏。
     * 换成第 `锚点行号` 行（独立文案）之后，这一行在起手前后都是**完整可见**的，
     * 报出来的矩形是真的、滑一下就会变。
     *
     * **这条用例量的是这一屏、这一个起手点**：起手点取的是提示文字自己的中心；
     * 浮层的左右两角与更靠上的边沿没有逐点扫过（探针那边做了五组同距离、只换落点的对照，
     * 也都动，但它同样不是全带穷举）。
     */
    @Test
    fun 起手落在提示自己身上的上滑也推得动内容() {
        val 锚点文案 = "中间那一条"
        val 锚点行号 = 8
        val 滑动距离 = 300f
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    repeat(长内容行数) { 行 ->
                        Text(
                            text = if (行 == 锚点行号) 锚点文案 else OnboardVoice.nextButton,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 浮层在这儿才有的测：它不出现，这条用例什么都没测。
        val 提示 = compose.onNodeWithText(CommonVoice.scrollMore).fetchSemanticsNode().boundsInRoot

        /** 起手点滑上去之后，中部那一行被推上去多少像素（正数 = 内容真的动了）。 */
        fun 滑一次(起手: Offset, 标签: String): Float {
            val 滚之前 = compose.onNodeWithText(锚点文案).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$标签：起手之前第 $锚点行号 行就没被报道出真实矩形（$滚之前）——标尺本身不成立，" +
                    "这一格量不到任何东西",
                滚之前.height > 0f,
            )
            compose.onRoot().performTouchInput {
                swipe(start = 起手, end = 起手 - Offset(0f, 滑动距离), durationMillis = 400)
            }
            compose.waitForIdle()
            val 滚之后 = compose.onNodeWithText(锚点文案).fetchSemanticsNode().boundsInRoot
            println(
                "SCROLLHINT51 $标签 起手=$起手 上滑=$滑动距离 提示=$提示 屏底=${屏幕底边()} " +
                    "锚点前=$滚之前 锚点后=$滚之后",
            )
            return 滚之前.top - 滚之后.top
        }

        val 位移 = 滑一次(提示.center, "起手落在提示中心")
        assertTrue(
            "起手落在提示身上（${提示.center}）的上滑没推动内容：中部那一行只被推上去 $位移 px" +
                "（提示 $提示，屏幕底边 ${屏幕底边()}）——这一层吞掉了滑动",
            位移 > 1f,
        )
        compose.onNodeWithText(CommonVoice.scrollMore).assertIsDisplayed()
    }

    /**
     * 首帧不许先闪一下提示。
     *
     * `ScrollState` 的 maxValue 初值是 `Int.MAX_VALUE`（反编译 1.7.5 的构造器确认过），
     * 要等第一次布局量完才落回真值；在那之前 `canScrollForward` 在**任何**屏上都是真。
     * 所以 `ElderScreen` 里那条判定还带了 `viewportSize > 0`（它的初值是 0）。这条用例把
     * 时钟停住、只推一帧，看的就是「判定用的量第一次布局之后有没有落到真值」。
     */
    @Test
    fun 首帧不会先闪一下滚动提示() {
        compose.mainClock.autoAdvance = false
        摆一屏(行数 = 1)

        compose.mainClock.advanceTimeByFrame()

        assertEquals("首帧就出现了提示：判定没等第一次布局", 0, 有几处(CommonVoice.scrollMore))
    }

    private companion object {
        /** 与同包其它屏幕级用例取同一个量级：等动画收尾，不等环境慢。 */
        const val 超时毫秒 = 5_000L
    }
}
