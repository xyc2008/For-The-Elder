package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无障碍语义摆放位置的机械守卫（第 18 轮，关闭 `docs/01` 的 GAP-05）。
 *
 * 第 18 轮之前，`ui/` 下的语义信息只有一个来源：三处 `contentDescription`。当时的判断是
 * 「缺的不是标签，是**结构**」——朗读一屏文字时，听得出来的是「这是一页的标题」「这是卡片
 * 标题」「这是第 3 行第 2 列的格子」这三件事，而它们全都不是靠单点补 `contentDescription`
 * 能补上的。所以这一轮加的是 [com.fortheelder.ui.common] 里的那组修饰符。
 *
 * 语义标签有个和文案一样的性质：**漏掉一处，肉眼看不出来**。版式、颜色、点击全都正常，
 * 只是朗读少念了一句。所以这里把它变成可以机械判定的三条：
 *
 * 1. `ui/` 下每个 `*Screen.kt` 都必须走 `ElderScreen(`。这是「每屏表头都是标题」这条
 *    保证的**唯一**支点：标题的 `heading()` 挂在 `ElderHeader` 的 `Text` 上，
 *    哪一屏自己画表头，那一屏就整层丢掉标题语义，而屏幕上完全看不出区别。
 * 2. `ui/` 下每个 `Icon(` 都必须显式写 `contentDescription =`。写成 `null` 是允许的——
 *    装饰性图标就该不念——但必须在本行或前几行留下 `a11y-decorative: 理由`，
 *    把理由写在代码里，而不是留一个没人知道为什么的 `null`。
 * 3. 语义只从 `ui/common/A11y.kt` 出去。就地写 `semantics {` 的必须留 `a11y-ok: 理由`。
 *    这条挡的是「下一轮顺手在某个屏里手搓一个 `heading()`」，那样子孙后代就再也数不清
 *    这个项目到底往语义树里放了什么。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过：守卫悄悄失效，比守卫报错难发现得多。
 */
class A11yPlacementTest {

    private val uiRoot: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui",
    )

    /** 语义修饰符的唯一出口。规则 3 只放它一条路。 */
    private val semanticsModule = "common/A11y.kt"

    private val uiFiles: List<File> = uiRoot.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") }
        .sortedBy { it.path }
        .toList()

    @Test
    fun everyScreenUsesTheSharedScreenShell() {
        val screens = uiFiles.filter { it.name.endsWith("Screen.kt") }

        // 防止「一个 *Screen.kt 都没扫到于是零违规」这种假绿。
        assertTrue("扫到的 *Screen.kt 太少（${screens.size} 个），守卫形同虚设", screens.size >= 5)

        val offenders = screens.filter { !it.readText(Charsets.UTF_8).contains("ElderScreen(") }
            .map { it.relativeTo(uiRoot).path }

        assertTrue(
            "这些屏没有走 ElderScreen(，表头标题与返回按钮的无障碍语义会整层丢掉：" +
                offenders.joinToString("、"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun everyIconSaysWhatItIsOrWhyItIsSilent() {
        val offenders = mutableListOf<String>()
        var scannedIcons = 0

        for (file in uiFiles) {
            val lines = file.readLines(Charsets.UTF_8)
            lines.forEachIndexed { index, raw ->
                if (!raw.contains("Icon(")) return@forEachIndexed
                // IconButton( 的字符是 I-c-o-n-B，不会被 Icon( 匹配到；这里再挡一次手滑。
                if (raw.contains("IconButton(")) return@forEachIndexed
                if (raw.trimStart().startsWith("*")) return@forEachIndexed
                scannedIcons++

                val from = (index - 4).coerceAtLeast(0)
                val to = (index + 8).coerceAtMost(lines.lastIndex)
                val window = lines.subList(from, to + 1).joinToString("\n")
                val where = "${file.relativeTo(uiRoot).path}:${index + 1}"

                if (!window.contains("contentDescription")) {
                    offenders += "$where  没有显式写 contentDescription"
                } else if (Regex("contentDescription\\s*=\\s*null").containsMatchIn(window) &&
                    !window.contains("a11y-decorative:")
                ) {
                    offenders += "$where  contentDescription = null，但没有 a11y-decorative: 理由"
                }
            }
        }

        assertTrue("扫到的 Icon( 太少（$scannedIcons 处），守卫形同虚设", scannedIcons >= 3)

        assertTrue(
            "有 ${offenders.size} 处图标的无障碍说明没交代清楚：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun semanticsOnlyLeaveThroughTheSharedModule() {
        val offenders = mutableListOf<String>()

        for (file in uiFiles) {
            val relative = file.relativeTo(uiRoot).path.replace('\\', '/')
            if (relative == semanticsModule) continue
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                if (raw.contains("a11y-ok:")) return@forEachIndexed
                if (raw.trimStart().startsWith("*") || raw.trimStart().startsWith("//")) {
                    return@forEachIndexed
                }
                if (Regex("\\bsemantics\\s*[{(]").containsMatchIn(raw.substringBefore("//"))) {
                    offenders += "$relative:${index + 1}  ${raw.trim()}"
                }
            }
        }

        assertTrue(
            "语义修饰符应当只在 $semanticsModule 里定义（就地写请加「a11y-ok: 理由」）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
