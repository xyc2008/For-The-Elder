package com.fortheelder.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortheelder.data.prefs.FontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * 字号系统的**渲染**验证。
 *
 * 纯 JVM 单测（`TypographyScaleTest`）只能证明排版表里的 sp 数值算对了，
 * 证明不了「屏幕上的字真的变大了」，也证明不了主题嵌套时不会累乘。这里做的是端到端测量。
 *
 * **为什么量像素而不是量 dp。** 第一版这个测试用 `getUnclippedBoundsInRoot()` 拿 dp 宽度做比值，
 * 量出来「大一点」档的倍率是 1.097、标称 1.15，headlineMedium 的倍率恒等于 1.0。
 * 原因不是主题坏了，而是**这个接口把像素换算成 dp 时用的 density 本身就带 fontScale**：
 * 文字放大 1.15 倍、分母也跟着放大 1.15 倍，比值就被部分抵消了。用 dp 做被试的比值，被试和量尺是同一个东西。
 *
 * 所以这里用 `TextLayoutResult.size` —— 排版结果的原始像素宽度，不做任何 dp 换算。
 */
@RunWith(AndroidJUnit4::class)
class ThemeFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * 6 个字。刻意不用更长的样本：正文「更大」档是 26sp，12 个字要 819px，
     * 而标题档会顶到屏幕宽度、被迫换行，换行后的宽度就不再反映字号了。
     */
    private val sample = "常伴常伴常伴"

    /** 一次测量的现场：排版结果的像素宽度。 */
    private class Probe {
        var px: Int = -1

        val ready: Boolean get() = px > 0
    }

    @Composable
    private fun ProbeText(probe: Probe, style: TextStyle) {
        Text(text = sample, style = style, onTextLayout = { probe.px = it.size.width })
    }

    private fun assertRatio(expected: Float, base: Int, actual: Int, what: String) {
        val ratio = actual.toFloat() / base.toFloat()
        // 容差 3%：每个汉字的推进宽度会被取整到整像素，6 个字累计约 1% 的抖动。
        assertTrue(
            "$what：期望约 ${expected}×，实测 ${ratio}×（基准 ${base}px，实测 ${actual}px）",
            abs(ratio - expected) < 0.03f,
        )
    }

    @Test
    fun 三档字号逐级真实放大正文() {
        val probe = Probe()
        var fontSize by mutableStateOf(FontSize.STANDARD)

        compose.setContent {
            ForTheElderTheme(fontSize = fontSize) {
                ProbeText(probe, MaterialTheme.typography.bodyLarge)
            }
        }

        assertTrue("初次排版应当已产出测量值", probe.ready)
        val standardPx = probe.px

        compose.runOnUiThread { fontSize = FontSize.LARGE }
        compose.waitForIdle()
        assertRatio(FontSize.LARGE.scale, standardPx, probe.px, "正文「大一点」")

        compose.runOnUiThread { fontSize = FontSize.EXTRA }
        compose.waitForIdle()
        assertRatio(FontSize.EXTRA.scale, standardPx, probe.px, "正文「更大」")
    }

    @Test
    fun 标题小标题正文都跟着放大() {
        // 之前出现过「正文变大了、标题没变」的真实缺陷（≥30sp 完全不跟档位），
        // 只量正文的用例发现不了，所以三档字号里最显眼的三个 style 各量一遍。
        val headline = Probe()
        val title = Probe()
        val body = Probe()
        var fontSize by mutableStateOf(FontSize.STANDARD)

        compose.setContent {
            ForTheElderTheme(fontSize = fontSize) {
                Column {
                    ProbeText(headline, MaterialTheme.typography.headlineMedium)
                    ProbeText(title, MaterialTheme.typography.titleMedium)
                    ProbeText(body, MaterialTheme.typography.bodyLarge)
                }
            }
        }

        assertTrue("三个 style 都应当已产出测量值", headline.ready && title.ready && body.ready)
        val headlineStd = headline.px
        val titleStd = title.px
        val bodyStd = body.px

        compose.runOnUiThread { fontSize = FontSize.EXTRA }
        compose.waitForIdle()

        assertRatio(FontSize.EXTRA.scale, headlineStd, headline.px, "headlineMedium")
        assertRatio(FontSize.EXTRA.scale, titleStd, title.px, "titleMedium")
        assertRatio(FontSize.EXTRA.scale, bodyStd, body.px, "bodyLarge")
    }

    @Test
    fun 主题嵌套不累乘字号() {
        // 引导页要在保存之前用嵌套主题预览字号。如果内层拿外层的倍率再乘一次，
        // 三层「更大」就会变成 1.3 × 1.3 × 1.3。
        val probe = Probe()
        var layers by mutableStateOf(1)

        compose.setContent {
            ForTheElderTheme(fontSize = FontSize.EXTRA) {
                when (layers) {
                    1 -> ProbeText(probe, MaterialTheme.typography.bodyLarge)
                    2 -> ForTheElderTheme(fontSize = FontSize.EXTRA) {
                        ProbeText(probe, MaterialTheme.typography.bodyLarge)
                    }
                    else -> ForTheElderTheme(fontSize = FontSize.EXTRA) {
                        ForTheElderTheme(fontSize = FontSize.EXTRA) {
                            ProbeText(probe, MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        val oneLayer = probe.px
        assertTrue("一层「更大」应当已产出测量值", probe.ready)

        compose.runOnUiThread { layers = 2 }
        compose.waitForIdle()
        assertEquals("两层「更大」的像素宽度不该变化", oneLayer, probe.px)

        compose.runOnUiThread { layers = 3 }
        compose.waitForIdle()
        assertEquals("三层「更大」的像素宽度不该变化", oneLayer, probe.px)
    }

    @Test
    fun 嵌套一层标准档会覆盖外层的更大档() {
        // 引导页的预览卡片正是这个用法：外层是用户当前设置，内层是正在试选的档位。
        // 内层选「标准」时，预览必须真的回到标准大小，而不是「比外层小一点」。
        val probe = Probe()
        var nested by mutableStateOf(false)

        compose.setContent {
            ForTheElderTheme(fontSize = FontSize.EXTRA) {
                if (nested) {
                    ForTheElderTheme(fontSize = FontSize.STANDARD) {
                        ProbeText(probe, MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    ProbeText(probe, MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val outerExtra = probe.px
        assertTrue("外层「更大」应当已产出测量值", probe.ready)

        compose.runOnUiThread { nested = true }
        compose.waitForIdle()

        assertRatio(1f / FontSize.EXTRA.scale, outerExtra, probe.px, "内层标准档相对外层更大档")
    }

    @Test
    fun 系统字号会被保留且应用内档位仍然有效() {
        // 用户把系统字号调到 1.5 说明他确实看不清，应用内再选「更大」应当**继续变大**。
        //
        // 这个用例的第一版断言「系统 1.5 与应用内 1.3 线性相乘」，在 API 35 上实测不成立：
        // 平台对字体换算做了非线性压缩——字号越大、放大被压制得越狠（原始矩阵见 records/04）。
        // 所以这里不假设平台行为，只断言两条真正要保证的性质：
        //   1) 应用不抹平系统设置：系统 1.5 环境下的标准档必须比纯标准档更大；
        //   2) 应用内「更大」档在该环境里仍然显著放大，不是摆设。
        // 实测倍率一并打印，供 records 归档。
        val plain = Probe()
        val syntheticStandard = Probe()
        val syntheticExtra = Probe()
        val systemFontScale = 1.5f
        var synthetic by mutableStateOf(false)
        var fontSize by mutableStateOf(FontSize.STANDARD)

        compose.setContent {
            val rawDensity = LocalDensity.current.density
            // 必须 remember：DensityImpl 没有 equals，每次重组新建实例会让下游读取者反复失效。
            val syntheticDensity = remember(rawDensity) { Density(rawDensity, systemFontScale) }
            if (!synthetic) {
                ForTheElderTheme(fontSize = FontSize.STANDARD) {
                    ProbeText(plain, MaterialTheme.typography.bodyLarge)
                }
            } else {
                CompositionLocalProvider(LocalDensity provides syntheticDensity) {
                    ForTheElderTheme(fontSize = fontSize) {
                        ProbeText(
                            if (fontSize == FontSize.STANDARD) syntheticStandard else syntheticExtra,
                            MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        assertTrue("基准排版应当已产出测量值", plain.ready)
        val plainPx = plain.px

        compose.runOnUiThread { synthetic = true }
        compose.waitForIdle()
        assertTrue("合成系统字号后应当已产出测量值", syntheticStandard.ready)

        assertTrue(
            "系统字号应当被保留而不是被应用抹平（标准档 ${plainPx}px → 系统 1.5 下 ${syntheticStandard.px}px）",
            syntheticStandard.px > plainPx,
        )
        val systemOnlyRatio = syntheticStandard.px.toFloat() / plainPx.toFloat()

        compose.runOnUiThread { fontSize = FontSize.EXTRA }
        compose.waitForIdle()
        assertTrue("切换到「更大」档后应当已产出测量值", syntheticExtra.ready)

        val appTierRatio = syntheticExtra.px.toFloat() / syntheticStandard.px.toFloat()
        println(
            "FONTSCALE-PROBE plain=$plainPx syntheticStandard=${syntheticStandard.px} " +
                "syntheticExtra=${syntheticExtra.px} systemOnlyRatio=$systemOnlyRatio appTierRatio=$appTierRatio",
        )
        assertTrue(
            "系统字号 1.5 之上再选「更大」仍应显著放大，实测 $appTierRatio×" +
                "（标称 ${FontSize.EXTRA.scale}×，被平台的非线性换算压缩）",
            appTierRatio >= 1.08f,
        )
    }
}
