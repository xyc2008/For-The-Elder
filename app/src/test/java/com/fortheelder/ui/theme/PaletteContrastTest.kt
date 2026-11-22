package com.fortheelder.ui.theme

import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色的对比度守卫。
 *
 * 这一层是适老化的硬指标，而不是审美偏好：老年人的晶状体透光率下降、对比敏感度变低，
 * 「看着还行」在真实使用里就是看不清。颜色又是全项目里唯一会**悄悄**变得不好用的东西——
 * 改一个十六进制值不会让任何界面报错，只会让某一类人看不清。所以每一条配色关系都在这里有断言。
 *
 * ## 断言的口径
 *
 * - 正文与容器上的文字按 WCAG 2.1 **1.4.6（AAA）** 要求 ≥ **7:1**（WebAIM《Contrast and Color
 *   Accessibility》给的阈值表；本项目的选色理由见 `records/03-架构决策记录.md` 的 ADR-044）。
 * - 界定范围的描边按 **1.4.11 非文本对比度（AA）** 要求 ≥ **3:1**，且是「与**相邻**底色」
 *   各自达标，所以同一条描边对好几种底色逐个断言。
 * - 比值取「不小于阈值」这个下界方向，不是等值断言：略微加深是安全的（只会更清楚），
 *   前提是同时别把它推过下一条关系的阈值。
 *
 * ## 取值来源：直接读 `Color.kt` 的源码文本
 *
 * 断言用的十六进制值是从 `Color.kt` 里正则抓出来的，**不是**在这里再抄一份，也不是从
 * `MaterialTheme.colorScheme` 取。理由有两条：一是这样「改了颜色忘了改断言」必然构建失败，
 * 而这正是想要的；二是纯文本解析让这条用例完全不依赖 Compose 运行时而能跑在 JVM 单测里。
 * 代价是格式一旦写成别的构造方式（例如 `Color(red = …)`）就会抓不到——
 * 抓不到会**直接报错**（见 `colorKtNamedColorsAreParsable`），不会静默放过。
 *
 * ## 这条用例不保证什么（照实写在代码里）
 *
 * - **不测真实渲染。** 它算的是按 sRGB 分量套 WCAG 公式得到的数，屏幕上看到的是不是这样，
 *   这条用例管不了；真机截图只能靠人眼看，而本项目当前没有做这一层核验。
 * - **不测半透明与渐变合成。** 本轮把描边改成不透明正是为了消掉这一类的坑，但用例本身
 *   只按不透明色算；半透明那一路靠下面那条「不许再出现 `outline.copy(alpha`」的源码级断言挡。
 * - **不测字重与字号对可读性的影响。** AAA 的 7:1 与 4.5:1 分档对应字号，这里一律按最严的 7:1 卡。
 * - **不管填充色与页面背景之间的 1.0x:1。** 卡片填充对暖白底只有 1.0x:1，这是允许的：
 *   1.4.11 管的是「用来界定范围的边界」，界定卡片的是描边不是填充（见 `ElderComponents.kt`）。
 */
class PaletteContrastTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val themeDir = File(mainSrc, "com/fortheelder/ui/theme")
    private val uiDir = File(mainSrc, "com/fortheelder/ui")

    /** `val 名字 = Color(0xAARRGGBB)`。只认这一种写法，认不出会报错而不是当没看见。 */
    private val colorLiteral = Regex("val\\s+(\\w+)\\s*=\\s*Color\\(0x([0-9A-Fa-f]{8})\\)")

    private val palette: Map<String, Int> by lazy {
        colorLiteral.findAll(File(themeDir, "Color.kt").readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2].substring(2).toInt(16) }
    }

    private val uiSources: List<File> by lazy {
        uiDir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.sortedBy { it.path }.toList()
    }

    // ---- WCAG 2.1 相对亮度与对比度 ------------------------------------------------

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(hex: Int): Double {
        val r = (hex shr 16) and 0xFF
        val g = (hex shr 8) and 0xFF
        val b = hex and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun color(name: String): Int =
        palette[name] ?: error("Color.kt 里没有名为 $name 的具名色值，这条守卫失去意义")

    private fun contrast(fg: String, bg: String): Double {
        val a = luminance(color(fg))
        val b = luminance(color(bg))
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun ratioText(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun assertAtLeast(need: Double, fg: String, bg: String) {
        val got = contrast(fg, bg)
        assertTrue(
            "$fg 落在 $bg 上要求 ≥ ${ratioText(need)}:1，实际 ${ratioText(got)}:1" +
                "（WCAG 2.1：" + (if (need >= 7.0) "1.4.6 AAA 正文" else "1.4.11 AA 非文本") + "）",
            got >= need,
        )
    }

    // ---- 0. 守卫自身是否真的读到了东西 ---------------------------------------------

    @Test
    fun colorKtNamedColorsAreParsable() {
        assertTrue("Color.kt 不存在：${File(themeDir, "Color.kt")}", themeDir.isDirectory)
        assertTrue(
            "只从 Color.kt 解析出 ${palette.size} 个具名色值，太少，守卫可能没真的读到文件",
            palette.size >= 18,
        )
        assertTrue("暖白底没解析出来，说明解析方式对不上当前写法", palette.containsKey("WarmBackground"))
        assertTrue("描边色没解析出来", palette.containsKey("OutlineStrong") && palette.containsKey("OutlineStrongDark"))
        assertTrue("界面源码太少（${uiSources.size} 个文件），源码级断言失去意义", uiSources.size >= 10)
        assertTrue(
            "界面的 Kotlin 源码总行数异常，源码级断言可能扫了空目录",
            uiSources.sumOf { it.readLines(Charsets.UTF_8).size } > 500,
        )
    }

    // ---- 1. 正文与容器上的文字：≥ 7:1 ------------------------------------------------

    @Test
    fun bodyTextReachesAaaOnEveryLightBackground() {
        for (ink in listOf("InkPrimary", "InkSecondary")) {
            for (bg in listOf("WarmSurface", "WarmBackground", "WarmSurfaceVariant")) {
                assertAtLeast(7.0, ink, bg)
            }
        }
    }

    @Test
    fun lightContainerPairsReachAaa() {
        assertAtLeast(7.0, "OnElderGreenContainer", "ElderGreenContainer")
        assertAtLeast(7.0, "OnWarmBrownContainer", "WarmBrownContainer")
    }

    @Test
    fun darkTextReachesAaaOnEveryDarkBackground() {
        for (ink in listOf("InkOnDark", "InkOnDarkSecondary")) {
            for (bg in listOf("SurfaceDark", "BackgroundDark", "SurfaceVariantDark")) {
                assertAtLeast(7.0, ink, bg)
            }
        }
    }

    @Test
    fun darkContainerPairsReachAaa() {
        assertAtLeast(7.0, "OnElderGreenLight", "ElderGreenLight")
        assertAtLeast(7.0, "WarmBrownContainer", "ElderGreenContainerDark")
        // 暗色下这一格不再借 surfaceVariant，而是专用的暖容器：两张入口卡在深色下也要能分开。
        assertAtLeast(7.0, "WarmBrownContainer", "WarmContainerDark")
    }

    // ---- 2. 主色同时当按钮标签与正文：两种用途都要过 --------------------------------

    @Test
    fun primaryWorksAsLabelAndAsBodyText() {
        assertAtLeast(7.0, "WarmSurface", "ElderGreen")
        assertAtLeast(7.0, "ElderGreen", "WarmBackground")
        assertAtLeast(7.0, "ElderGreen", "WarmSurface")
        assertAtLeast(7.0, "WarmSurface", "WarmBrown")
        assertAtLeast(7.0, "WarmBrown", "WarmBackground")
        assertAtLeast(7.0, "WarmBrown", "WarmSurface")
        assertAtLeast(7.0, "ElderGreenLight", "BackgroundDark")
    }

    // ---- 3. 描边：对每一种相邻底色都要 ≥ 3:1 ----------------------------------------

    @Test
    fun lightBordersReachNonTextContrast() {
        for (bg in listOf("WarmSurface", "WarmBackground", "WarmSurfaceVariant", "WarmBrownContainer")) {
            assertAtLeast(3.0, "OutlineStrong", bg)
        }
    }

    @Test
    fun darkBordersReachNonTextContrast() {
        for (bg in listOf("SurfaceDark", "BackgroundDark", "SurfaceVariantDark", "WarmContainerDark")) {
            assertAtLeast(3.0, "OutlineStrongDark", bg)
        }
    }

    /**
     * 已知欠账，钉住数字而不是假装达标：绿色容器的底色太亮/太深，描边压不过 3:1
     * （`OutlineStrong` 2.98:1、`OutlineStrongDark` 2.86:1）。
     *
     * 当前**不算违规**，因为这两个容器只当无边框徽章的圆底用（首页两张入口卡的 `iconContainer`），
     * 没有任何一条描边挨着它们。但这是笔明账：谁哪天给绿色容器加描边，就会踩到这里。
     * 下面按略低的下界断言，既能拦住「悄悄变得更糟」，也不把 2.98 说成达标。
     */
    @Test
    fun greenContainerRemainsABorderlessShell() {
        assertTrue(contrast("OutlineStrong", "ElderGreenContainer") >= 2.90)
        assertTrue(contrast("OutlineStrongDark", "ElderGreenContainerDark") >= 2.80)
    }

    // ---- 4. 源码级：描边不许再退回半透明 --------------------------------------------

    /**
     * 本轮修掉的那个缺陷的机械回归守卫。
     *
     * 原来四处描边写成 `outline.copy(alpha = 0.35f)`，合成出来对白底只有 1.42:1，
     * 而屏幕上看不出任何毛病——这种错误只能靠源码级断言挡，不能靠眼睛。
     */
    @Test
    fun noTranslucentOutlineAnywhereInUi() {
        val offenders = uiSources.filter { it.readText(Charsets.UTF_8).contains("outline.copy(alpha") }
        assertEquals(
            "界面里还有半透明描边，合成后过不了 1.4.11 的 3:1：${offenders.map { it.name }}",
            0,
            offenders.size,
        )
    }

    /** 两套 scheme 都要真的把 `outline` 接上新常量——颜色对了但没接上，界面照样看不见边界。 */
    @Test
    fun bothSchemesWireTheStrongOutlines() {
        val text = File(themeDir, "Theme.kt").readText(Charsets.UTF_8)
        assertEquals("亮色 scheme 没有恰好接一次 OutlineStrong", 1, countOccurrences(text, "outline = OutlineStrong,"))
        assertEquals(
            "暗色 scheme 没有恰好接一次 OutlineStrongDark",
            1,
            countOccurrences(text, "outline = OutlineStrongDark,"),
        )
    }

    /** 换掉的旧名字不该留下（死代码会让人以为那套配色还在用）。 */
    @Test
    fun replacedPaletteNamesAreGone() {
        for (name in listOf("OutlineSoft", "ElderGreenDark")) {
            val offenders = uiSources.filter { Regex("\\b$name\\b").containsMatchIn(it.readText(Charsets.UTF_8)) }
            assertEquals("$name 已经不再使用，但还留在这些文件里：${offenders.map { it.name }}", 0, offenders.size)
        }
    }

    // ---- 5. 触控目标下限：1cm 的物理度量 --------------------------------------------

    /**
     * 1cm × 1cm 出自 Nielsen Norman Group 引用的 Parhi、Karlson、Bederson（MobileHCI 2006）
     * ——一个**物理**度量。换算成 dp 用的是 Android 的 160dpi 名义基准：1in = 160dp，1cm ≈ 63.0dp。
     *
     * 照实说：这是名义换算，不是拿尺子量真实设备。真机上 `dp` 与物理毫米的偏差取决于厂商
     * 是否如实上报 `xdpi`，这一点这条用例测不了。
     */
    @Test
    fun touchTargetsReachOneCentimetre() {
        val text = File(themeDir, "Dimens.kt").readText(Charsets.UTF_8)
        val values = Regex("val\\s+(\\w+)\\s*=\\s*([0-9.]+)\\.dp")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }

        val floor = 160.0 / 2.54 // ≈ 62.99dp
        for (name in listOf("minTouchTarget", "buttonHeight")) {
            val value = values[name] ?: error("Dimens.kt 里没有 $name，这条守卫失去意义")
            assertTrue("$name = ${value}dp，小于 1cm 的名义换算值 ${ratioText(floor)}dp", value >= floor)
        }
        val entryCard = values["entryCardMinHeight"] ?: error("Dimens.kt 里没有 entryCardMinHeight")
        assertTrue("入口卡最小高度 $entryCard dp 太矮，两张主干入口卡撑不出明显的主次", entryCard >= 100.0)
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }
}
