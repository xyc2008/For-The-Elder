package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「键盘让位只有一个归属地」的机械守卫（第 55 轮）。
 *
 * ## 起因
 *
 * 第 53 轮留下一条没查明的账：设备上量滚动区矩形时，`top` 出现 269 与 338 两类值，机制不明。
 * 第 55 轮用 `dumpsys window displays` 把 insets 本身量了出来（键盘弹起时 IME inset = 883px、
 * 落下时 0），才发现真正的问题不是布局，而是**同一件事被写了两遍**：
 *
 * - `AppRoot.kt` 在根层挂了 `Modifier.safeDrawingPadding()`，而 `safeDrawing` **已经包含 IME**；
 * - `ElderComponents.kt` 的 `ElderScreen` 又在滚动内容列与 footer 列上各挂了一次 `imePadding()`。
 *
 * 后果是那两次 `imePadding()` 读到的 inset 恒为 0（根层已经消费过），**是死代码**；更糟的是
 * 两处 KDoc 互相矛盾——`AppRoot` 说「safeDrawing 已含 IME，不需要再单独加 imePadding」，
 * `ElderScreen` 却说「两个容器各带一次 imePadding() 是有意的」。注释与代码各自自洽，
 * 只有把人放到设备前量一遍才能发现对不上。这条守卫就是那一次实测的机械落点。
 *
 * ## 它盯什么
 *
 * 1. 主源码里**一个** `imePadding` 调用都不许有（`.` 前缀的代码调用形式）；
 * 2. 主源码里**一个** `imePadding` / `windowInsetsPadding` 的 import 都不许有；
 * 3. 「读 insets 并让位」的那一组修饰符在整个主源码里**只许出现 1 次**，且必须落在
 *    `AppRoot.kt`——根层的 `safeDrawingPadding()`。
 *
 * 第 3 条是这条守卫真正的价值：新人加屏时最容易顺手写一句 `modifier.imePadding()` 或
 * `systemBarsPadding()`，那句话在**当前**根层下往往看不出区别（因为根层已经处理过），
 * 但一旦哪天真去掉了根层那一句，两处就会同时生效、让位两次。守卫把「归属地唯一」这件事
 * 变成一条会红的断言，而不是一段没人复读的注释。
 *
 * ## 它不保证什么
 *
 * 它只认**源码文本形式**，认不出等价写法（比如 `Modifier.then(WindowInsets.ime.asPaddingValues()
 * .let { Modifier.padding(it) })`）。它也不保证键盘行为真的对——那是设备级用例
 * `RootInsetsAnchorTest` 与人工走查的活儿。这里挡的是「又一次悄悄写第二遍」。
 *
 * 主源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失或
 * 目录空得离谱时**直接失败**而不是跳过（纪律同 [CopyCountGuardTest]）。
 */
class InsetsOwnerGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** 让位归属地：整个应用里只许 [rootOwner] 挂一次的那一句。 */
    private val rootOwner = "com/fortheelder/ui/AppRoot.kt"

    /**
     * 「消费 insets、把自己挪开」的一组修饰符。写的是**调用形式**（前面带 `.`、后面带 `(`），
     * 所以 KDoc 里用反引号包起来的提及不会被算进去——上文的注释本身就要提 `imePadding`，
     * 扫注释是这套守卫生效的前提（第 24 轮 `CopyCountGuardTest` 已确立这个口径）。
     */
    private val insetsConsumers: List<String> = listOf(
        "imePadding",
        "safeDrawingPadding",
        "safeContentPadding",
        "safeGesturesPadding",
        "systemBarsPadding",
        "statusBarsPadding",
        "navigationBarsPadding",
        "displayCutoutPadding",
        "windowInsetsPadding",
        "consumeWindowInsets",
    )

    /** 这些是「已经彻底不许再用」的写法，出现一次就是回归。 */
    private val forbiddenAlways: List<String> = listOf("imePadding")

    @Test
    fun insetsAreConsumedOnlyOnceAndAtTheRoot() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()

        // 防止「扫了个空目录于是零违规」这种假绿。
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)

        var scannedLines = 0
        val calls = mutableListOf<String>()
        val imports = mutableListOf<String>()

        for (file in files) {
            // 一定要用 invariantSeparatorsPath：Windows 上 `.path` 给的是反斜杠，
            // 和下文写死的 「com/fortheelder/ui/AppRoot.kt」比不相等——第 55 轮这里真红过一次。
            val relative = file.relativeTo(mainSrc).invariantSeparatorsPath
            file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                scannedLines++
                val at = "$relative:${index + 1}"
                for (name in insetsConsumers) {
                    if (Regex("\\." + Regex.escape(name) + "\\s*\\(").containsMatchIn(line)) {
                        calls += "$at  「.$name(」 ——${line.trim()}"
                    }
                }
                if (line.trimStart().startsWith("import ") && line.contains("imePadding")) {
                    imports += "$at  「${line.trim()}」"
                }
            }
        }

        assertTrue(
            "扫描到的 main 源码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 6000,
        )

        val forbidden = calls.filter { hit -> forbiddenAlways.any { hit.contains(".$it(") } }
        assertTrue(
            "主源码里又出现了 ${forbidden.size} 处让位修饰符，而键盘让位只能有一个归属地" +
                "（根层 `AppRoot.safeDrawingPadding()`）。第 55 轮实测确认：根层消费过 ime inset 之后，" +
                "屏幕层再挂一次读到的恒为 0，是死代码；而它一旦哪天有了非零值，就会让位两次。\n" +
                forbidden.joinToString("\n"),
            forbidden.isEmpty(),
        )

        assertTrue(
            "主源码里出现了 imePadding 的 import（${imports.size} 处）。这台 App 不使用 imePadding，" +
                "见上一条的理由：\n" + imports.joinToString("\n"),
            imports.isEmpty(),
        )

        assertEquals(
            "「消费 insets 并让位」的修饰符在主源码里应当**只出现 1 次**（根层的 safeDrawingPadding），" +
                "实际 ${calls.size} 次：\n" + calls.joinToString("\n"),
            1,
            calls.size,
        )
        assertTrue(
            "唯一那一处让位不在 $rootOwner 里，而在「${calls.single().substringBefore("  「")}」。\n" +
                "它必须在根层：屏幕层在组合树上更靠内，那里再消费一次等于同一份 inset 让两遍；\n" +
                "而且只有根层能同时罩住表头、内容区、钉底动作区与弹窗。",
            calls.single().startsWith(rootOwner),
        )
        assertTrue(
            "唯一那一处让位不是 safeDrawingPadding：${calls.single()}\n" +
                "这里要的是「状态栏/导航栏/刘海/键盘」四类一起处理的那个修饰符；" +
                "换成只处理其中几类的（systemBarsPadding 不含键盘）会让键盘盖住输入框。",
            calls.single().contains(".safeDrawingPadding("),
        )
    }
}
