package com.fortheelder.ui.home

import com.fortheelder.ui.play.PlayVoice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页那句话的出处（JVM，纯常量与源码扫描）。
 *
 * 这一条补的是 `docs/03` 第 8 节的 **GAP-04** 剩下的那半：`PlayVoice.title` 与首页入口
 * 起初是**两处**字面量，取值相同却各写一遍，而且**没有任何用例把两者钉在一起**——
 * 「文案集中化」在 `ui/` 一层有守卫（[com.fortheelder.ui.UiCopyPlacementTest]），
 * 可那一条只管「中文别散落在界面代码里」，**管不到「同一句话被写了两遍」**。
 * 第 36 轮把首页那一处改成指向 `PlayVoice.title`，于是字面量在全项目只剩一处；
 * 这个类就是盯着这件事的两条用例：
 *
 * 1. **相等断言**：首页入口的文字与游戏页标题相等。这是 GAP-04 里写明的那条；它红了
 *    说明有人又给首页写了一句自己的话；
 * 2. **出处唯一**：扫 `main` 全部 `.kt`，那个字面量**只允许出现一次**，且必须在
 *    `ui/play/PlayVoice.kt` 里。它红了说明有人把这句话抄了第二份。
 *
 * 两条都留着是有意的：相等断言只看结构与值，抓不到「两处字面量恰好取同一个值」；扫描只看
 * 出处，抓不到「唯一那一处被改成了别的话」。合起来才是 GAP-04 要的那个不变量。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（照 [com.fortheelder.ui.UiCopyPlacementTest] 的写法）：
 * 守卫悄悄失效，比守卫报错难发现得多。
 */
class HomeVoiceTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** 带引号的原文，也就是「字面量」本身；扫描与报错信息都用它。 */
    private val entryLiteral = "\"${PlayVoice.title}\""

    /** 唯一允许写这个字面量的文件。 */
    private val homeOfTheLiteral = "com/fortheelder/ui/play/PlayVoice.kt"

    /**
     * GAP-04 里写明的那条相等断言。
     *
     * 首页那个入口与游戏页表头说的是同一件事，就该是同一句话——`HomeVoice.playAction` 现在
     * 由 `PlayVoice.title` 赋值，这条用例把这件事写成一个会失败的条件，而不是一句评语。
     */
    @Test
    fun `首页的游戏入口和游戏页标题是同一句`() {
        assertEquals(
            "首页入口「${HomeVoice.playAction}」与游戏页标题「${PlayVoice.title}」不是同一句了；" +
                "首页那一处应当指向 PlayVoice.title，不另写一句",
            PlayVoice.title,
            HomeVoice.playAction,
        )
    }

    /**
     * 那个字面量在 `main` 源码里只写一遍（机械守卫）。
     *
     * 只看**代码行**：整行注释不看（KDoc 里正当地提到这句话，说的就是「这两处是同一句」），
     * 含 `copy-ok:` 的行跳过（给将来真的有理由时留一个必须写理由的口子）——两条豁免照
     * [com.fortheelder.domain.play.PlayStaysClocklessTest] 的写法。
     */
    @Test
    fun `游戏入口那句话在 main 源码里只写了一遍`() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()

        // 防止「扫了个空目录于是零违规」这种假绿。
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 60)
        assertTrue(
            "没扫到 PlayVoice.kt，这条守卫找不到那句话的唯一出处",
            files.any { it.name == "PlayVoice.kt" },
        )

        val hits = mutableListOf<String>()
        var scannedLines = 0
        for (file in files) {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                val trimmed = raw.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                if (raw.contains("copy-ok:")) return@forEachIndexed
                scannedLines++
                if (raw.substringBefore("//").contains(entryLiteral)) {
                    hits += "${file.relativeTo(mainSrc).invariantSeparatorsPath}:${index + 1}"
                }
            }
        }

        assertTrue("扫描到的源码行数异常（$scannedLines 行），守卫可能没真的读到源码", scannedLines > 500)

        assertTrue(
            "$entryLiteral 在 main 源码里出现了 ${hits.size} 次，应当只有一次（$homeOfTheLiteral：" +
                "PlayVoice.title 那一行）。别处要用这句话，请指向那个常量，不要再写一遍：\n" +
                hits.joinToString("\n"),
            hits.size == 1 && hits.single().startsWith("$homeOfTheLiteral:"),
        )
    }
}
