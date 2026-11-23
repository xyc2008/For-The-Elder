package com.fortheelder.ui.talk

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点完一句，回应卡自己走进视口」那条算术的**纯 JVM** 守卫（第 62 轮）。
 *
 * ## 为什么这轮的判据能落在 JVM 上
 *
 * 第 61 轮的设备探针量出一条难看的读数：点了一句现成的话之后，新出现的「我听见的」
 * 整张都在滚动区下沿以下——按滚动位移倒推，它的上沿在视口内 y≈2822，而滚动区是
 * `[0,338][1080,2337]`，**低了 485 像素**；那一屏的语义树里连它的节点都没有。
 * 用户点了一句话，屏幕上一点变化都看不见。
 *
 * 修法是把「该滚到哪里」抽成 [revealScrollTarget] 这样的纯算术：坐标进、像素出。
 * 于是每一档边界都能在这一层几毫秒钉一遍，不必每次都把模拟器拖起来问一遍；
 * 设备那一层只剩一个责任——**回答这根绳子接对了没有**（内置坐标口径对不对、
 * 滚动有没有真的发生），由 `TalkRevealTest` 隔着渲染去问。
 *
 * ## 分工写在明处
 *
 * 这里钉的是算术本身：真值表、夹取、取整、以及「什么时候不该动」。
 * [revealScrollTarget] 拿到的坐标对不对，**这一层钉不住**——那要设备上量。
 * 反过来，设备用例也钉不住逐像素的边界（造一次 1 px 的偏差要摆一屏精确的假内容），
 * 那是这一层的事。两层各管一半，缺一半就会留下「公式错了但设备用例照样绿」或者
 * 「公式对、接的线错了」的口子。
 *
 * ## 后四条是**结构**守卫
 *
 * 纯函数最容易被读漏的坏法有三种：**没人调用**（公式改了、屏里照旧不滚）、
 * **只接了一个分支**（听懂了会滚、没听懂不会，用户遇到的是后者就不灵）、
 * **接线接错对象**（屏里自持一个 `ScrollState`、把另一个交给了外壳，滚的是看不见的那个）。
 * 三条都在后面那几条源码形态用例里堵掉。
 *
 * ## 本轮最初两条用例是我自己写错的
 *
 * 这轮开工时先写了两条「已经滑过一段再点」的用例，都拿**已经整张看得见的卡**去问
 * 「该滚到哪」，期望一个非零目标——被 [isFullyVisibleInViewport] 的早退规则当场判红
 * （真实返回值是「原地不动」）。错在把「位置不为零」当成了「需要滚动」。
 * 两条都改成「下沿真出界」才作数；写错这件事本身留在 records/04 里。
 */
class TalkRevealTargetTest {

    // ---- 判定：这张卡是不是已经整张在视口里 ----

    @Test
    fun 整张在视口里算看得见() {
        assertTrue(
            "卡的上沿在视口顶部、高 800、视口高 1200，整张都在里面，却判成看不见：" +
                "这个函数是「不用动画面」的唯一入口，它一红，用户每次点话都会被硬滚一下",
            isFullyVisibleInViewport(cardTopInViewport = 0f, cardHeight = 800, viewportHeight = 1200),
        )
        assertTrue(
            "下沿正好压在视口底边（400 + 800 == 1200）也应当算看得见：边界取闭区间，" +
                "差 0 px 就判看不见会让贴合底边的内容平白无故被滚一次",
            isFullyVisibleInViewport(cardTopInViewport = 400f, cardHeight = 800, viewportHeight = 1200),
        )
    }

    @Test
    fun 下沿超出视口一像素就算看不见() {
        assertFalse(
            "下沿超出 1 px（401 + 800 > 1200）却判成看得见：第 61 轮那张卡实测只露 32 px、" +
                "更早一次只露 1 px，「露一点」正是要治的那种假可见",
            isFullyVisibleInViewport(cardTopInViewport = 401f, cardHeight = 800, viewportHeight = 1200),
        )
    }

    @Test
    fun 上沿被顶到视口上面就算看不见() {
        assertFalse(
            "上沿在 -1（已经滑过卡片头顶）却判成看得见：只看下沿的写法会放过这一格，" +
                "用户看到的是半张卡悬在标题栏后面",
            isFullyVisibleInViewport(cardTopInViewport = -1f, cardHeight = 800, viewportHeight = 1200),
        )
    }

    @Test
    fun 比视口还高的卡永远算看不见() {
        // 老人在系统字号最大档时，一张卡超过视口高是可能的：2500 > 1999。
        // 这种卡无论滚到哪都不可能「整张可见」，所以每个位置都返回 false 是正确的——
        // 目标位置由夹取负责，不会被这个 false 带进死循环（滚一次就置 revealed）。
        assertFalse(
            "比视口还高的卡被判成看得见：它不可能整张进视口，判 true 会让首帧那次对齐直接跳过",
            isFullyVisibleInViewport(cardTopInViewport = 0f, cardHeight = 2500, viewportHeight = 1999),
        )
    }

    @Test
    fun 视口没量完一律算看不见() {
        assertFalse(
            "viewportHeight 为 0 是 ScrollState 的初值（第一次布局还没量完），却判成看得见：" +
                "拿 0 高的视口迟早会算出荒唐的目标值",
            isFullyVisibleInViewport(cardTopInViewport = 0f, cardHeight = 800, viewportHeight = 0),
        )
        assertFalse(
            "卡片高度为 0、恰好落在视口顶部：这时「看得见」与「还没量出来」分不开，" +
                "一律按看不见处理，等下一帧量出来再说",
            isFullyVisibleInViewport(cardTopInViewport = 0f, cardHeight = 0, viewportHeight = 0),
        )
    }

    // ---- 目标：该滚到哪一像素 ----

    @Test
    fun 卡在视口下方时滚到卡的上沿() {
        // 第 61 轮读数的原型：滚动位置 0、卡片上沿在视口内 2822、视口高 1999、内容还能滚到 5000。
        assertEquals(
            "卡片上沿在视口内 2822、当前滚动 0，应当滚到 2822 把它对齐到视口上沿",
            2822,
            revealScrollTarget(
                cardTopInViewport = 2822f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 0,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 已经滑过一段时按内容坐标相加() {
        // 卡片在内容里的位置 = 视口内位置 + 当前滚动量。用户先自己滑到 700，再点一句话，
        // 新卡上沿落在视口内 1500（视口高 1999、卡高 800，下沿 2300 已经出到视口外面）
        // ⇒ 内容坐标 2200，就滚到 2200。
        assertEquals(
            "用户已经滑到 700、新卡上沿在视口内 1500（这张卡的下沿已经出到视口外面）：" +
                "目标该是 2200（内容坐标），写成「滚到 1500」会把卡片顶到标题栏后面去",
            2200,
            revealScrollTarget(
                cardTopInViewport = 1500f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 700,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 目标越过后界时夹到底() {
        assertEquals(
            "算出来 7322、内容最多只能滚到 5000：夹取后停在 5000，而不是去追一个到不了的位置" +
                "（animateScrollTo 追不到值会一直等，画面就卡在滚动动画里）",
            5000,
            revealScrollTarget(
                cardTopInViewport = 2822f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 4500,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 目标落在零以上时夹回顶端() {
        assertEquals(
            "卡片被顶到视口上方（视口内 -500）、当前滚动 100，算出来是 -400：夹回 0。" +
                "负的滚动位置在 ScrollState 上不成立，交出去只会被 framework 悄悄吞掉",
            0,
            revealScrollTarget(
                cardTopInViewport = -500f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 100,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 小数位置四舍五入而不是截断() {
        assertEquals(
            "视口内位置 1200.6（下沿 2000.6 已出界）应当进位成 1201，" +
                "截断成 1200 会稳定少滚 1 px——每一轮都少 1 px，" +
                "累积起来就是「点了没反应」的第一颗种子",
            1201,
            revealScrollTarget(
                cardTopInViewport = 1200.6f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 0,
                maxScroll = 5000,
            ),
        )
    }

    // ---- 三种「不该动画面」的情形 ----

    @Test
    fun 整张已经看得见时一像素都不动() {
        assertEquals(
            "卡（视口内 50、高 400）整张都在视口里，位置却没保持原样：" +
                "用户自己停的地方不该因为点了一句话被强行对齐——那等于把画面从他手里抢走",
            300,
            revealScrollTarget(
                cardTopInViewport = 50f,
                cardHeight = 400,
                viewportHeight = 1999,
                currentScroll = 300,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 视口没量完时不滚() {
        assertEquals(
            "视口还没量出来（0）就要滚：这时没有任何可信的坐标口径，" +
                "唯一正确的动作是原地等下一帧",
            120,
            revealScrollTarget(
                cardTopInViewport = 2822f,
                cardHeight = 800,
                viewportHeight = 0,
                currentScroll = 120,
                maxScroll = 5000,
            ),
        )
    }

    @Test
    fun 内容本来就放得下时不滚() {
        assertEquals(
            "内容一屏放得下（maxScroll 为 0）却算出要滚：短内容屏上滚动位置恒为 0，" +
                "任何非零目标都会被 framework 吞掉，或者更糟——把残留动画挂在那里",
            0,
            revealScrollTarget(
                cardTopInViewport = 2822f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 0,
                maxScroll = 0,
            ),
        )
        assertEquals(
            "maxScroll 为负（理论上不该出现）也不许算出非零目标",
            200,
            revealScrollTarget(
                cardTopInViewport = 2822f,
                cardHeight = 800,
                viewportHeight = 1999,
                currentScroll = 200,
                maxScroll = -1,
            ),
        )
    }

    // ---- 结构守卫：抽出来的算术得真的被界面用上，而且接在对的地方 ----

    private fun 主源码(相对路径: String): File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        相对路径,
    )

    private val 判据文件 = 主源码("com/fortheelder/ui/talk/TalkReveal.kt")
    private val 屏文件 = 主源码("com/fortheelder/ui/talk/TalkScreen.kt")
    private val 外壳文件 = 主源码("com/fortheelder/ui/common/ElderComponents.kt")

    @Test
    fun 判据文件仍是纯_Kotlin() {
        val 依赖 = 判据文件.readText(Charsets.UTF_8).lines()
            .map { it.trim() }
            .filter { it.startsWith("import ") && !it.startsWith("import kotlin.") }

        assertTrue(
            "TalkReveal.kt 引入了 Compose / Android 的依赖：${依赖.joinToString("、")}。" +
                "这个文件必须是纯 Kotlin，否则上面那些逐像素的用例就不可能跑在这一层——" +
                "把它抽出来的全部理由就是「算术能在没有设备的地方被钉住」",
            依赖.isEmpty(),
        )
    }

    @Test
    fun 屏里真的把滚动状态交了出去() {
        val 源码 = 屏文件.readText(Charsets.UTF_8)
        assertTrue(
            "TalkScreen.kt 里没有 `scrollState = scroll`：屏自己持有一个 ScrollState 却把外壳" +
                "原来的那个留在里面，就会滚一个看不见的滚动区——外面那条线接对的假象最难查",
            源码.contains("scrollState = scroll"),
        )
        assertTrue(
            "TalkScreen.kt 里没有 `revealScrollTarget(`：算术抽出来了，屏里却没拿它算目标值",
            源码.contains("revealScrollTarget("),
        )
        assertTrue(
            "TalkScreen.kt 里没有 `animateScrollTo(`：算出了目标却不滚，等于什么都没做" +
                "（只写 `scroll.value = target` 也行，但那样就没有动画，本轮的取舍用的是动画）",
            源码.contains("animateScrollTo("),
        )
    }

    @Test
    fun 两个分支都要把回应带进视口() {
        val 源码 = 屏文件.readText(Charsets.UTF_8)
        val 次数 = Regex(Regex.escape("BringResponseIntoView(scroll)")).findAll(源码).count()

        assertEquals(
            "TalkScreen.kt 里 `BringResponseIntoView(scroll)` 出现 $次数 次，期望 2 次" +
                "（听懂了、没听懂两个分支各一次）。只包住一个分支的坏法是这一轮最容易犯的：" +
                "「没听懂」那张卡同样是刚冒出来的，同样可能落在视口下面",
            2,
            次数,
        )
    }

    @Test
    fun 外壳留了传入口且默认行为不变() {
        val 源码 = 外壳文件.readText(Charsets.UTF_8)
        assertTrue(
            "ElderComponents.kt 的 ElderScreen 没有 `scrollState: ScrollState? = null` 这个参数：" +
                "没有入口，上面那条 `scrollState = scroll` 根本编译不过——" +
                "或者更坏：有了入口但写成非空必需参数，其余六个屏全要改",
            源码.contains("scrollState: ScrollState? = null"),
        )
        assertTrue(
            "ElderScreen 里没有 `scrollState ?: fallbackScroll`：传进来的滚动状态被丢了，" +
                "屏和外壳各滚各的",
            源码.contains("val scroll = scrollState ?: fallbackScroll"),
        )
        assertTrue(
            "ElderScreen 里没有 `val fallbackScroll = rememberScrollState()`：为 null 时至少" +
                "要有一个自持的滚动状态，其余六个屏的行为才和过去逐项一致",
            源码.contains("val fallbackScroll = rememberScrollState()"),
        )
    }
}
