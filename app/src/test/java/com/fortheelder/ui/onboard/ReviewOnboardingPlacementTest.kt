package com.fortheelder.ui.onboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「重看引导是只读的」这条性质的机械守卫（第 47 轮）。
 *
 * ## 它守的是什么
 *
 * 第 47 轮在设置页加了一条「再看一遍开头那几页」，让用户**不丢设置**就能复习引导里那几句
 * 边界说明。引导原本是全项目唯一会写盘的三页（称呼、字号、采集开关、`onboardingDone`
 * 四项全在第三页的 `finish` 里落下），所以「重看」这件事的全部风险集中在一点：
 * **重看那条路绝对不能碰到 `finish`**。
 *
 * 这条性质靠**结构**保证，不靠自觉：`OnboardingScreen` 的重看入口是一个可空参数
 * `onReviewExit: (() -> Unit)?`，非空即重看；而 `finish` 只在 `onReviewExit == null`
 * 的 `else` 分支里被调用。本用例把这个结构钉成可执行的断言——以后谁把重看那条路接到
 * `finish` 上（哪怕只是顺手加一个「重看时也保存一下」），它会红。
 *
 * ## 它做的三件事
 *
 * 1. [reviewPathNeverWritesAnything]：在 `OnboardingScreen.kt` 里定位 `finish` lambda 的
 *    函数体（朴素花括号配对），要求四处写盘 `setAddress` / `setFontSize` / `setCollectRhythm` /
 *    `completeOnboarding` **在整份文件里各只出现一次、且全落在 lambda 体内**，
 *    同时 `onReviewExit` 在该 lambda 体内**零次出现**。四处里少一处也不行：那意味着某次改动
 *    让写盘漏了一步（例如重看时忘了写 `onboardingDone`）。
 * 2. [onboardingGateHasOnlyOneExit]：`AppRoot.kt` 里 `OnboardingScreen(` 与 `MainNav(` 各只出现
 *    一次。引导是**闸门**（ADR-002），闸门只允许有一个出口通往主界面；谁以后再往闸门里塞
 *    第二条路，这一例红。
 * 3. 防假绿：文件行数、锚点是否真的找到、`onReviewExit` 是否真的存在于页面里。
 *
 * ## 它不保证什么
 *
 * 它读的是**源码文本**，不是运行中的界面。所以「重看页上确实没有可输入的框」这件事它证明不了——
 * 那由 `app/src/androidTest` 的 `SettingsScreenTest` 在真机上证明（那条用例用
 * `hasSetTextAction()` 断言整个页面没有一个可输入节点）。这里只证明**写盘入口不存在**。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失、
 * 文件缺失、锚点找不到，一律 `error(...)` 直接失败而不是跳过——守卫悄悄失效比守卫报错难发现。
 */
class ReviewOnboardingPlacementTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val screenFile = File(mainSrc, "com/fortheelder/ui/onboard/OnboardingScreen.kt")

    private val appRootFile = File(mainSrc, "com/fortheelder/ui/AppRoot.kt")

    /** 第三页 `finish` lambda 的起点。锚点写完整签名，免得撞上别的 `val finish`。 */
    private val finishAnchor = "val finish: (Boolean) -> Unit"

    /** 四种写盘。`onboardingDone` 也在内：漏写它会让重看退出后又被闸门抓回引导。 */
    private val writers = listOf(
        "setAddress(",
        "setFontSize(",
        "setCollectRhythm(",
        "completeOnboarding(",
    )

    @Test
    fun reviewPathNeverWritesAnything() {
        val source = readSource(screenFile)
        val lines = source.lines()

        assertTrue(
            "OnboardingScreen.kt 只有 ${lines.size} 行，短得不正常：守卫可能读错了文件",
            lines.size > 250,
        )

        val anchorLine = lines.indexOfFirst { it.contains(finishAnchor) }
        assertTrue(
            "在 OnboardingScreen.kt 里找不到「$finishAnchor」，重看不落盘这条守卫失去锚点。" +
                "是 `finish` 被改名了，还是第三页的写盘不再集中在一个 lambda 里了？",
            anchorLine >= 0,
        )

        val body = lambdaBodyAfter(source, anchorLine, finishAnchor)

        // 每一处写盘在整份文件里只能出现一次，而且必须落在 finish 的体内。
        // 「只出现一次」这一半是防漂移：多出来的那一处几乎必然是另一条路（比如重看）在写盘。
        val escaping = mutableListOf<String>()
        for (writer in writers) {
            val hits = lines.count { it.contains(writer) }
            assertTrue(
                "OnboardingScreen.kt 里 `$writer` 出现了 $hits 次，应当恰好 1 次。" +
                    "引导的三页里只有第三页的 `finish` 该写盘；多出来的一处很可能属于重看路径。",
                hits == 1,
            )
            if (!body.contains(writer)) {
                escaping += writer
            }
        }
        assertTrue(
            "下面这些写盘调用落在了 `finish` lambda 之外：${escaping.joinToString("、")}。" +
                "重看引导那条路上一个字节都不该写——见 docs/01 的 AC-13.5。",
            escaping.isEmpty(),
        )

        val reviewInsideFinish = Regex("onReviewExit").findAll(body).count()
        assertTrue(
            "`onReviewExit` 在 `finish` lambda 体内出现了 $reviewInsideFinish 次，应当是 0 次。" +
                "它一旦进了这个 lambda，重看就会顺着同意/不同意那条路把设置写下去。" +
                "重看的出口只做一件事：回设置页。",
            reviewInsideFinish == 0,
        )
    }

    @Test
    fun onboardingGateHasOnlyOneExit() {
        val source = readSource(appRootFile)

        for (entry in listOf("OnboardingScreen(", "MainNav(")) {
            val hits = Regex(Regex.escape(entry)).findAll(source).count()
            assertTrue(
                "AppRoot.kt 里 `$entry` 出现了 $hits 次，应当恰好 1 次。引导是闸门（ADR-002），" +
                    "闸门只有一个出口通往主界面；第 47 轮的「重看」是 `MainNav` 里的普通目的地，" +
                    "不该在闸门这里多开一道口子。",
                hits == 1,
            )
        }
    }

    @Test
    fun theReviewEntryIsActuallyWired() {
        val source = readSource(screenFile)

        val mentions = Regex("onReviewExit").findAll(source).count()
        assertTrue(
            "OnboardingScreen.kt 里 `onReviewExit` 只出现 $mentions 次，少于 3 次。" +
                "它至少要在签名、`val reviewing`、以及重看的出口上各出现一次；" +
                "出现次数过少说明重看这条入口没有真的接上，后两例就成了空转的守卫。",
            mentions >= 3,
        )
        assertTrue(
            "OnboardingScreen.kt 里没有 `val reviewing`，" +
                "页面分不出「第一次走引导」和「回来重看」两种模式",
            source.contains("val reviewing"),
        )
    }

    private fun readSource(file: File): String {
        check(file.isFile) { "找不到 $file，重看引导的守卫无从核对" }
        return file.readText(Charsets.UTF_8)
    }

    /**
     * 从 `anchor` 所在行开始，取它后面第一个 `{` 配到的那对花括号之间的内容。
     *
     * 朴素配对：逐字符走，跳过 `//` 到行尾、跳过 `"..."` 字面量（含 `\` 转义）、
     * 跳过 `/* */` 块注释。`OnboardingScreen.kt` 里没有跨行的字符串字面量，
     * 所以这个朴素实现是够用的；**配对失败就直接报错**，绝不返回一个凑合的结果——
     * 返回半截正文会让下面三条断言全部变成假绿。
     */
    private fun lambdaBodyAfter(source: String, anchorLine: Int, anchor: String): String {
        val anchorAt = source.lines().take(anchorLine).sumOf { it.length + 1 } + source.lines()[anchorLine].indexOf(anchor)
        val open = source.indexOf('{', anchorAt)
        check(open >= 0) { "「$anchor」所在行之后没有找到 `{`，配对失败" }

        var depth = 0
        var index = open
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == '"' -> index = skipString(source, index)
                ch == '/' && source.getOrNull(index + 1) == '/' -> index = skipLineComment(source, index)
                ch == '/' && source.getOrNull(index + 1) == '*' -> index = skipBlockComment(source, index)
                ch == '{' -> {
                    depth++
                    index++
                }
                ch == '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                    index++
                }
                else -> index++
            }
        }
        error("从「$anchor」开始的 `{` 没有找到配对的 `}`，配对失败")
    }

    /** 跳过一段字符串字面量，返回闭合引号之后的位置。未闭合就报错（配对会跟着错）。 */
    private fun skipString(source: String, quoteAt: Int): Int {
        var index = quoteAt + 1
        while (index < source.length) {
            when (source[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                '\n' -> error("第 ${source.take(quoteAt).count { it == '\n' } + 1} 行的字符串字面量没有闭合")
                else -> index++
            }
        }
        error("字符串字面量没有闭合")
    }

    private fun skipLineComment(source: String, at: Int): Int {
        val end = source.indexOf('\n', at)
        return if (end < 0) source.length else end
    }

    private fun skipBlockComment(source: String, at: Int): Int {
        val end = source.indexOf("*/", at + 2)
        check(end >= 0) { "块注释没有闭合" }
        return end + 2
    }
}
