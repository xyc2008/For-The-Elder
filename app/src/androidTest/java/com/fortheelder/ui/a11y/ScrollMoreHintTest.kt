package com.fortheelder.ui.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
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
 * 滚动兜底提示的**渲染级**验证（第 51 轮建立，第 58 轮跟着出现规则改过一次）。
 *
 * 第 50 轮把引导页的动作区钉到了屏幕底部，但内容区本身仍会裁：那一轮的整屏首屏实测里，
 * 引导第 2 页有一整块「之后还有内容」落在屏幕外，而滚动对 55 岁以上用户约等于不存在。
 * 这一轮给 `ElderScreen` 加了一层浮在滚动区底部的提示（`ScrollMoreHint`），
 * 这一组用例要钉住的是六件事——**放不下才出现、最后一行确实得靠滚动才看得见、
 * 提示不吞掉它压住那一带的点击、起手落在提示身上也推得动内容、
 * 一滑动就退场、滑回顶端又回来**（后两件是第 58 轮新加的）、**首帧不闪**。
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
     * 造法照着那次红的形态来：一整屏长内容，每一行都是**通栏可点**的，而且各有唯一文案
     * （`行 $行`），这样报文里能指出到底点的是哪一行。
     *
     * **第 58 轮这条改过两版，两版红的证据都留在这里。** 最早那版走的是
     * `onNodeWithText(末尾文案).performScrollTo().performClick()`——`performScrollTo()`
     * 把目标推到视口底边，也就是浮层正盖着的那一带，首跑时这一次点击落不上，
     * 同一次 L3 里 11 条既有用例就是这么点不动的。可按第 58 轮的新规则，`performScrollTo()`
     * 一滚浮标就退场了，点击自然落得上：那条写法会**退化成空守卫**（绿，却什么都没守）。
     * 所以第二版改成先取浮标的矩形，在它盖住的那一带里**挑一条现成的行**、不滚动直接点它，
     * 挑不到就当场红（**不许退化成「先滚动再点」**：按新规则那条路走下来一定绿，
     * 可浮层挡不挡点击就没人管了）。
     *
     * **第二版首跑就红**，报文是「浮标带 `Rect.fromLTRB(278.0, 2279.0, 802.0, 2354.0)` 里
     * 没有任何一整行的框心落进去（屏底 2400.0）」。红的是**这条用例的摆法**，不是产品：
     * 停在顶端时每一行的顶由容器给的起点、行高与行距（`ElderScreen` 的 `spacedBy(gapMd)`）
     * 一起决定，而浮标带只有 75 px 高——带心落进了行与行之间的**空档**。
     * 「挑一条现成的行」这一步在全屏是**离散取点**，凑不凑巧纯看这一屏的像素，这是概率型写死。
     * 第三版（现在这版）把这件事改成**算出来**：先量出 `行 0` 的顶、整行高、行距，
     * 再解一个小于一个周期的「顶垫」，让某一行的框心**正落在浮标带心上**；
     * 顶垫用一个 `Spacer` 垫在内容最前面，`spacedBy` 会在它与 `行 0` 之间再插一格间距，
     * 所以行顶的实际位移是 `行距 + 顶垫`（下面单独断言这个位移，算式对不上就当场红）。
     * 这条是**判定性**的：只要行高与行距量得准，那一行必然存在，不再赌像素。
     *
     * 这条在改法上还出过一次错，也留在这里当证据：把浮标的 `clickable` 去掉之后**它还是红的**
     * （首跑改码后的真机消息是 `落在滚动区底部那一带的点击被浮层吃掉了 expected:<1> but was:<0>`）。
     * 真正的原因是底色用了 `Surface`——**非可点的 `Surface` 重载也会往触摸链上挂
     * `pointerInput`**（反编译 `material3-android:1.3.1` 的 `SurfaceKt$Surface$1` 查实），
     * 改成 `Box` 的 `background` 之后这条才绿。这一条守的就是那个换了底色的行为。
     */
    @Test
    fun 提示不吞掉它盖住那一带的点击() {
        val 点到了 = mutableIntStateOf(0)
        // 顶垫取**像素**：`SemanticsNode.boundsInRoot` 是像素，算式与量都留在同一套单位里。
        val 顶垫 = mutableFloatStateOf(0f)
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    val 本地密度 = LocalDensity.current
                    if (顶垫.floatValue > 0f) {
                        Spacer(Modifier.height(with(本地密度) { 顶垫.floatValue.toDp() }))
                    }
                    repeat(长内容行数) { 行 ->
                        Text(
                            text = "行 $行",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 点到了.intValue++ },
                        )
                    }
                }
            }
        }

        // 先坐实浮层确实压在这一带上：它不出现的话，这条用例什么都没测。
        val 提示 = compose.onNodeWithText(CommonVoice.scrollMore)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        // 量出这一屏的节奏：整行高、相邻两行顶的差（= 行高 + `spacedBy` 那一格间距）。
        val 行零 = compose.onNodeWithText("行 0").fetchSemanticsNode().boundsInRoot
        val 行一 = compose.onNodeWithText("行 1").fetchSemanticsNode().boundsInRoot
        val 行高 = 行零.height
        val 行距 = 行一.top - 行零.top - 行高
        val 周期 = 行高 + 行距
        assertTrue(
            "量不出正的节奏（行 0 的框 $行零、行 1 的框 $行一 ⇒ 行高 $行高 / 行距 $行距）：" +
                "下面那个算式的输入不成立，这条用例不许当成通过",
            行高 > 0f && 周期 > 0f,
        )

        // 解一个小于「周期」的顶垫，让某一行的框心（框顶 + 半个行高）落到浮标带心上。
        // **要挪的是位移，不是顶垫**：`Spacer` 是内容里新加的第一个孩子，`spacedBy` 会在它与
        // `行 0` 之间再插一格间距，所以行顶的实际位移是 `行距 + 顶垫`，顶垫得把那一格减掉。
        // 这一版第一稿漏减了 `行距`，真机报文是「行高 79.0 / 行距 42.0 / 周期 121.0 /
        // 顶垫 58.0px / 行 0 的顶 262.0」：58 + 42 = 100 的位移把行 0 的框心推到 301.5，
        // 而 301.5 与带心 2316.5 对 121 取模正好差 42——差的就是漏掉的那一格。
        // 取正模：Kotlin 的 `%` 对负数给负值，这里要的是 [0, 周期) 里的那个解。
        val 需要位移 = 提示.center.y - 行零.top - 行高 / 2f
        val 模过 = (((需要位移 - 行距) % 周期) + 周期) % 周期
        // 模出来贴着 0 的话 `if (顶垫.floatValue > 0f)` 会把 Spacer 挡掉、白等一帧；
        // 往下整一格周期是同一个解（行的框心本来就是一个周期一个），不至于空转。
        val 顶垫像素 = if (模过 < 1f) 模过 + 周期 else 模过
        顶垫.floatValue = 顶垫像素
        compose.waitForIdle()

        // 顶垫进组合之后，每一行的顶应当多挪「行距 + 顶垫」：这一条同时守着「垫真的生效了」。
        val 行零之后 = compose.onNodeWithText("行 0").fetchSemanticsNode().boundsInRoot
        val 位移 = 行零之后.top - 行零.top
        assertTrue(
            "加了 ${顶垫像素}px 顶垫之后行 0 的顶只从 ${行零.top} 挪到 ${行零之后.top}" +
                "（位移 $位移，预期至少 ${行距 + 顶垫像素}）：顶垫没进组合，下面的算式全部落空",
            位移 >= 行距 + 顶垫像素 - 1f,
        )

        // 在浮标盖住的那一带里挑那一整行：要求它的框心真的落在浮标矩形内、且高度不为零
        // （完全滚出视口的节点 Compose 报全零矩形——同一份文件另一条用例早就依赖这个性质，
        // 那种行点了说明不了任何事）。挑不到就是算式与实测对不上，当场红。
        val 目标行 = (0 until 长内容行数).firstOrNull { 行 ->
            val 框 = compose.onNodeWithText("行 $行").fetchSemanticsNode().boundsInRoot
            框.height > 0f && 提示.contains(框.center)
        } ?: throw AssertionError(
            "浮标带 $提示 里没有任何一整行的框心落进去（屏底 ${屏幕底边()}）：行高 $行高 / 行距 $行距 / " +
                "周期 $周期 / 顶垫 ${顶垫像素}px / 行 0 的顶 ${行零之后.top}——算式与实测对不上，" +
                "这条用例证明不了浮层挡不挡点击",
        )

        compose.onNodeWithText("行 $目标行").performClick()

        assertEquals(
            "落在滚动区底部那一带的点击被浮层吃掉了（点的是「行 $目标行」，它的框心落在浮标带 $提示 里）",
            1,
            点到了.intValue,
        )
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
     * 末尾那条断言第 58 轮跟着规则一起**反过来**了：只滑了 300 px，40 行的屏离底还早
     * （「下面确实还有内容」由下一条用例用滚动区自报的量程独立证明），所以浮标退场只可能
     * 因为新规则多要的那条「停在顶端」不成立了——一头守着「滑动推得动内容」，
     * 另一头守着「浮标不再永久押住底部那一条带」。
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
        // 第 58 轮起这条断言反过来了：滑走之后浮标**该退场**（新规则要求停在顶端）。
        // 只滑了 300 px、远不到底，所以「没了」不可能是「滑到底了所以该没了」；
        // 下一条用例还会用滚动区自报的量程把这一句独立证一遍。
        compose.waitUntil(超时毫秒) { 有几处(CommonVoice.scrollMore) == 0 }
    }

    /**
     * 滑走就退场、滑回顶端又回来（第 58 轮新加，跟着出现规则一起加）。
     *
     * 上一条用例只顾着「滑了之后浮标没了」，可「没了」在旧规则下本来就成立不了、
     * 在新规则下又有一个**竞争解释**：万一这一滑正好滑到底，浮标退场是因为
     * `canScrollForward` 变假，跟「停在顶端」这条新条件无关。所以这条用例走三步：
     * 起手断言浮标在、且滚动区位置确实是 0；滑动之后**先证明内容还有没露的**
     * （滚动区自报的量程比当前位置大），再断言浮标退场；最后拖回顶端，断言它回来。
     *
     * 判定量取的是**滚动区自报的滚动量程**（`VerticalScrollAxisRange`），不是浮标自己的可见性
     * ——浮标是待验证的东西，拿它当证据就成了自证。滑动距离也从量程推出来，
     * 不引入「一行多高」这类额外假设；`endVelocity = 0` 掐掉惯性滑行，
     * 免得手指抬起后还会滑一段、把「滑了多少」变成说不准的事。
     *
     * **三条边界写在这里，别把它们读成已经验证过的事**：
     * ① 只在这一屏、这一个字号档上量过，别的屏幕尺寸与字号档未量；
     * ② 「老人更容易发现下面还有内容」**没有任何用户测试**支撑，这条只证明行为如设计所写；
     * ③ 「滑回顶端会回来」只在本机走查过（`build/r58/walk58.py`），未在真实用户身上验证。
     */
    @Test
    fun 一滑动提示就退场滑回顶端又回来() {
        compose.setContent {
            ForTheElderTheme {
                ElderScreen(title = CommonVoice.appTitle) {
                    repeat(长内容行数) {
                        Text(OnboardVoice.nextButton, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        /** 这一屏里带纵向滚动语义的节点。有且只有一个才量得准，多一个就当场红。 */
        fun 滚动节点() = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes()
            .let { 们 ->
                if (们.size != 1) {
                    throw AssertionError("带纵向滚动语义的节点有 ${们.size} 个，期望正好 1 个：这一格量不到滚动量程")
                }
                们.single()
            }

        /** 滚动区自报的 (量程, 当前位置)。它跟浮标用的是同一个 `ScrollState`。 */
        fun 滚动量程() = 滚动节点().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?.let { it.maxValue() to it.value() }
            ?: throw AssertionError("读不出纵向滚动量程：读取方式跟 compose-ui 的版本漂了")

        compose.onNodeWithText(CommonVoice.scrollMore).assertIsDisplayed()
        val (起始量程, 起始位置) = 滚动量程()
        assertTrue(
            "这一屏压根没溢出（量程 $起始量程）：浮标在场也证明不了「滑走会退场」",
            起始量程 > 0f,
        )
        assertEquals("浮标出现在顶端以外的位置（$起始位置）：新规则的前提就不成立", 0f, 起始位置, 0.001f)

        val 滚动区 = 滚动节点().boundsInRoot
        val 中列 = 滚动区.left + 滚动区.width / 2f
        val 起手点 = Offset(中列, 滚动区.top + 滚动区.height * 0.4f)
        val 滑动距离 = (起始量程 * 0.3f).coerceAtLeast(1f)
        compose.onRoot().performTouchInput {
            swipeWithVelocity(
                start = 起手点,
                end = 起手点 - Offset(0f, 滑动距离),
                endVelocity = 0f,
                durationMillis = 300L,
            )
        }
        compose.waitForIdle()

        // 独立证据：内容确实还有没露的，不然这一格退化成「滑到底了所以没提示」。
        val (滑动后量程, 滑动后位置) = 滚动量程()
        assertTrue("滑动没推动滚动区（位置还是 $滑动后位置，量程 $滑动后量程）", 滑动后位置 > 0f)
        assertTrue(
            "滑动之后离底只剩 ${滑动后量程 - 滑动后位置} px（量程 $滑动后量程）：" +
                "这一格退化成「滑到底所以没提示」了",
            滑动后量程 > 滑动后位置 + 0.5f,
        )

        compose.waitUntil(超时毫秒) { 有几处(CommonVoice.scrollMore) == 0 }

        // 往下拖 = 往顶部滚。起落点取滚动区自己的坐标，免得落进页头 / 页脚那两条不可滚的带。
        var 回顶次数 = 0
        while (滚动量程().second > 0f && 回顶次数 < 12) {
            回顶次数++
            compose.onRoot().performTouchInput {
                swipe(
                    start = Offset(中列, 滚动区.top + 滚动区.height * 0.15f),
                    end = Offset(中列, 滚动区.top + 滚动区.height * 0.85f),
                    durationMillis = 100L,
                )
            }
            compose.waitForIdle()
        }
        assertEquals(
            "拖了 $回顶次数 次还没回到顶端（位置 ${滚动量程().second}）：这一格验证不了「滑回顶端会回来」",
            0f,
            滚动量程().second,
            0.001f,
        )
        compose.waitUntil(超时毫秒) { 有几处(CommonVoice.scrollMore) == 1 }
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
