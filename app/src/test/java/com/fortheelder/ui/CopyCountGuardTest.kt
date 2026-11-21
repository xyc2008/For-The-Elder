package com.fortheelder.ui

import com.fortheelder.data.prefs.FontSize
import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.play.PlayCatalog
import com.fortheelder.ui.onboard.OnboardVoice
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文案里写死的个数」与代码事实的一致性守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * R-10 的原文缺口是：**「游戏有几款」属于会随功能漂移的文案**——每加一个游戏，引导页、
 * 用户手册、速查卡都要回头改一遍；第 12 轮与第 17 轮各漏过一次，两次都是靠人工比对发现的。
 * 到第 24 轮之前，只有 [UiCopyPlacementTest] 一道守卫，而它管的是「文案放在哪个文件」，
 * 不管「文案里的数字对不对」。这条用例补的正是后半截。
 *
 * ## 它做什么
 *
 * 把每一处**已经登记的**「文案里的个数」写在 [declared] 表里，每一条都带着自己的事实来源：
 *
 * - 事实来自**运行中的代码**时，直接用代码问（`PlayCatalog.games.size`、`FontSize.entries.size`、
 *   `OnboardVoice.doesBullets.size`），这样加一个游戏、加一档字号，守卫自己就会追上来；
 * - 事实来自**源码里的常量**时，就地读源码（引导页数读 `OnboardingScreen.kt` 的 `PAGE_COUNT`）。
 *
 * 扫的是 `app/src/main/java` 下全部 `.kt` 文件，**注释一起扫**。这是有意的：`docs/01` 的
 * GAP-08/GAP-09 就是「注释与事实不符」的账，而这一轮抓到的第一处陈旧说法
 * （`OnboardVoice.kt` 把三档字号写成「四档排版表」）也长在 KDoc 里。单看用户可见的那几行
 * 字符串，根本发现不了它。
 *
 * ## 它不保证什么
 *
 * 它**只盯登记在 [declared] 表里的这几条宣称**，不是「所有数字都自动对得上」。没进表的
 * 说法（例如文档里的个数、速查卡里的个数、文案里没有明写「N 个」而是列举出来的东西），
 * 这条守卫一律看不见，仍然要靠人工比对。同理，它只核个数，不核个数以外的措辞。
 *
 * 表里的每条宣称还带一个 [DeclaredCount.minHits]：某条宣称在某次改动后**一句都扫不到了**，
 * 说明它已经被改得面目全非（或者对应的文案被删了），此时守卫必须报错而不是安静放过——
 * 一条永远扫不到东西的规则，和没有规则一样。这个纪律是从 [UiCopyPlacementTest] 抄来的。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过。
 */
class CopyCountGuardTest {

    /** 一处「文案里的个数」及其事实来源。 */
    private data class DeclaredCount(
        /** 这条宣称叫什么，用于报错信息与命中计数。同名会互相覆盖，所以每条各不相同。 */
        val what: String,
        /** 抓这个个数的正则，**第一个捕获组必须是数字本身**（中文数字或阿拉伯数字）。 */
        val pattern: Regex,
        /** 事实出处的说明，报错时告诉人该去看哪儿。 */
        val fact: String,
        /** 在这份源码里至少要命中几处；少于此数说明这条守卫已经失效。 */
        val minHits: Int,
        /** 问一次事实。 */
        val truth: () -> Int,
    )

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val numeral = "[一二三四五六七八九十两\\d]+"

    /**
     * 登记在册的宣称。加一条要同时想清楚三件事：**文案在哪儿**（正则要找得到）、
     * **事实在哪儿**（truth 要问得出）、**凭什么它不会再变**（不会变的就别登记）。
     */
    private val declared: List<DeclaredCount> = listOf(
        DeclaredCount(
            what = "小游戏款数",
            pattern = Regex("($numeral)个小游戏"),
            fact = "PlayCatalog.games 的条数",
            minHits = 2,
            truth = { PlayCatalog.games.size },
        ),
        DeclaredCount(
            what = "字号档数（排版表）",
            pattern = Regex("($numeral)档排版表"),
            fact = "FontSize 枚举的档位数",
            minHits = 1,
            truth = { FontSize.entries.size },
        ),
        DeclaredCount(
            what = "字号档数（档间区分度）",
            pattern = Regex("($numeral)档之间"),
            fact = "FontSize 枚举的档位数",
            minHits = 1,
            truth = { FontSize.entries.size },
        ),
        DeclaredCount(
            what = "同一件事下的起点条数",
            pattern = Regex("($numeral)个起点各配"),
            fact = "StarterCatalog.all 按 term 分组后每组的条数",
            minHits = 2,
            truth = { starterCountPerTopic() },
        ),
        DeclaredCount(
            what = "引导页数（引导N页）",
            pattern = Regex("引导($numeral)页"),
            fact = "OnboardingScreen.kt 的 PAGE_COUNT",
            minHits = 1,
            truth = { onboardingPageCountFromSource() },
        ),
        DeclaredCount(
            what = "引导页数（N页上的字）",
            pattern = Regex("($numeral)页上的字"),
            fact = "OnboardingScreen.kt 的 PAGE_COUNT",
            minHits = 1,
            truth = { onboardingPageCountFromSource() },
        ),
        DeclaredCount(
            what = "「会做的」条数",
            pattern = Regex("「会做的」($numeral)条"),
            fact = "OnboardVoice.doesBullets 的条数",
            minHits = 1,
            truth = { OnboardVoice.doesBullets.size },
        ),
        DeclaredCount(
            what = "「不会做的」条数",
            pattern = Regex("($numeral)条边界"),
            fact = "OnboardVoice.wontBullets 的条数",
            minHits = 1,
            truth = { OnboardVoice.wontBullets.size },
        ),
    )

    @Test
    fun declaredCountsInCopyMatchTheCode() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()

        // 防止「扫了个空目录于是零违规」这种假绿（纪律同 UiCopyPlacementTest）。
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)
        assertTrue(
            "一个 *Voice.kt 都没扫到，用户可见文案那条宣称会失效",
            files.any { it.name.endsWith("Voice.kt") },
        )

        val hits = mutableMapOf<String, Int>()
        val mismatched = mutableListOf<String>()
        val unparsable = mutableListOf<String>()
        var scannedLines = 0

        for (file in files) {
            val relative = file.relativeTo(mainSrc).path
            file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                scannedLines++
                for (claim in declared) {
                    for (found in claim.pattern.findAll(line)) {
                        hits[claim.what] = (hits[claim.what] ?: 0) + 1
                        val at = "$relative:${index + 1}  「${found.value}」"
                        val written = parseCount(found.groupValues[1])
                        if (written == null) {
                            unparsable += "$at  ——${claim.what}：认不出「${found.groupValues[1]}」这个数"
                            continue
                        }
                        val truth = claim.truth()
                        if (written != truth) {
                            mismatched += "$at  ——${claim.what}：文案说 $written，${claim.fact} 是 $truth"
                        }
                    }
                }
            }
        }

        assertTrue(
            "扫描到的 main 源码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 6000,
        )

        val silent = declared
            .filter { (hits[it.what] ?: 0) < it.minHits }
            .map { "${it.what}：只扫到 ${hits[it.what] ?: 0} 处，至少要 ${it.minHits} 处（事实出处：${it.fact}）" }
        assertTrue(
            "下面这些宣称已经扫不到足够多的句子了，守卫对它们失效。" +
                "要么是文案被改写／删掉了，需要更新本用例的 pattern；" +
                "要么是这条宣称本来就不该登记：\n" + silent.joinToString("\n"),
            silent.isEmpty(),
        )

        assertTrue(
            "有 ${unparsable.size} 处个数认不出来，守卫对它们等于没查：\n" + unparsable.joinToString("\n"),
            unparsable.isEmpty(),
        )

        assertTrue(
            "文案里的个数与代码事实对不上，共 ${mismatched.size} 处。" +
                "改文案，或者改代码事实——两边必须一样：\n" + mismatched.joinToString("\n"),
            mismatched.isEmpty(),
        )
    }

    /** `一`~`十`、`两`、`十X`、`X十`、`X十Y`，以及阿拉伯数字。别的写法一律返回 null（判失败，不静默跳过）。 */
    private fun parseCount(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        fun digit(c: Char): Int? = when (c) {
            '一' -> 1
            '二', '两' -> 2
            '三' -> 3
            '四' -> 4
            '五' -> 5
            '六' -> 6
            '七' -> 7
            '八' -> 8
            '九' -> 9
            else -> null
        }
        return when {
            raw.length == 1 -> digit(raw[0])
            raw[0] == '十' -> digit(raw[1])?.let { 10 + it }
            raw[1] == '十' && raw.length == 2 -> digit(raw[0])?.let { it * 10 }
            raw.length == 3 && raw[1] == '十' -> {
                val tens = digit(raw[0])
                val ones = digit(raw[2])
                if (tens == null || ones == null) null else tens * 10 + ones
            }
            else -> null
        }
    }

    /**
     * 每个「想做的事」下面有几条起点。
     *
     * 文案里那句「N 个起点各配两个按钮」只有在这**唯一一个数**成立时才说得通，
     * 所以这里先要求各组条数一致，不一致就直接判失败而不是取一个凑合的值。
     */
    private fun starterCountPerTopic(): Int {
        val sizes = StarterCatalog.all.groupBy { it.term }.values.map { it.size }.distinct()
        check(sizes.size == 1) {
            "起点目录里每个「想做的事」下的条数已经不一致（$sizes），" +
                "「N 个起点各配两个按钮」这句话失去了唯一的数（见 ui/talk/StarterCard.kt）"
        }
        return sizes.single()
    }

    /** 引导页数的事实来源是 `OnboardingScreen.kt` 里的 `PAGE_COUNT`；找不到就直接失败。 */
    private fun onboardingPageCountFromSource(): Int {
        val screen = File(mainSrc, "com/fortheelder/ui/onboard/OnboardingScreen.kt")
        check(screen.isFile) { "找不到 $screen，引导页数无从核对" }
        val found = Regex("private const val PAGE_COUNT\\s*=\\s*(\\d+)")
            .find(screen.readText(Charsets.UTF_8))
        checkNotNull(found) {
            "OnboardingScreen.kt 里没有「private const val PAGE_COUNT = N」了，" +
                "引导页数守卫失去事实来源（是重命名了，还是页数改成别的形式了？）"
        }
        return found.groupValues[1].toInt()
    }
}
