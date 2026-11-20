package com.fortheelder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import com.fortheelder.data.prefs.FontSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * 排版表按档位缩放的算术验证。
 *
 * 这一层能在纯 JVM 上测，是因为「换一套排版表」把字号缩放变成了纯数值计算，
 * 不再依赖密度、平台或渲染管线。屏幕上的真实放大另有仪器化测试（`ThemeFontScaleTest`）负责。
 */
class TypographyScaleTest {

    private fun Typography.styles(): List<Pair<String, TextStyle>> = listOf(
        "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge,
        "headlineMedium" to headlineMedium,
        "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge,
        "titleMedium" to titleMedium,
        "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge,
        "bodyMedium" to bodyMedium,
        "bodySmall" to bodySmall,
        "labelLarge" to labelLarge,
        "labelMedium" to labelMedium,
        "labelSmall" to labelSmall,
    )

    @Test
    fun 标准档的排版表就是基准值() {
        val standard = elderTypography(FontSize.STANDARD.scale)
        assertEquals(20f, standard.bodyLarge.fontSize.value, 0f)
        assertEquals(30f, standard.headlineMedium.fontSize.value, 0f)
        assertEquals(30f, standard.bodyLarge.lineHeight.value, 0f)
        assertEquals(42f, standard.headlineMedium.lineHeight.value, 0f)
        assertEquals(15f, standard.labelSmall.fontSize.value, 0f)
    }

    @Test
    fun 每一档都让全部字号与行高同步乘上倍率() {
        val base = elderTypography(1f)
        for (fontSize in FontSize.entries) {
            val scaled = elderTypography(fontSize.scale)
            for ((name, baseStyle) in base.styles()) {
                val actual = scaled.styles().first { it.first == name }.second
                val expectedSize = baseStyle.fontSize.value * fontSize.scale
                val expectedLine = baseStyle.lineHeight.value * fontSize.scale
                assertEquals(
                    "$name 在 ${fontSize.label} 档的字号不对",
                    expectedSize,
                    actual.fontSize.value,
                    0.001f,
                )
                assertEquals(
                    "$name 在 ${fontSize.label} 档的行高不对",
                    expectedLine,
                    actual.lineHeight.value,
                    0.001f,
                )
            }
        }
    }

    @Test
    fun 字重与字距不随档位变化() {
        // 字重是语义（标题要粗），字距是排版微调；跟着字号放大会让中文显得松散。
        val base = elderTypography(1f)
        val extra = elderTypography(FontSize.EXTRA.scale)
        for ((name, baseStyle) in base.styles()) {
            val actual = extra.styles().first { it.first == name }.second
            assertEquals("$name 的字重不该变化", baseStyle.fontWeight, actual.fontWeight)
            assertEquals("$name 的字距不该变化", baseStyle.letterSpacing, actual.letterSpacing)
        }
    }

    @Test
    fun 大字号档的行高必须容纳下放大后的文字() {
        // 行高如果没跟着乘，文字会被行框裁掉下半截——这是适老应用里最刺眼的排版事故。
        val extra = elderTypography(FontSize.EXTRA.scale)
        for ((name, style) in extra.styles()) {
            assertEquals(
                "$name 在「更大」档下每行容不下一个汉字",
                true,
                style.lineHeight.value >= style.fontSize.value * 1.3f,
            )
        }
    }

    @Test
    fun 相同档位重复调用得到等价但独立的排版表() {
        // 不是同一个对象：主题按档位缓存，不同档位必须是不同实例，
        // 否则换档时 Compose 看不出变化。
        val large = elderTypography(FontSize.LARGE.scale)
        val largeAgain = elderTypography(FontSize.LARGE.scale)
        assertNotSame(large, largeAgain)
        assertEquals(large.bodyLarge.fontSize, largeAgain.bodyLarge.fontSize)
    }
}
