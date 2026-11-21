package com.fortheelder.domain.companion

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一条规矩只有一张表」的机械守卫（第 37 轮新增）。
 *
 * ## 它盯的是什么
 *
 * `records/01` 第十二轮治过一次「同一个规则有两个出处」：`domain/play/PlayWords.kt` 与
 * `StarterRules.medicalWords` 各有一张医疗宣称表，15 词与 9 词重叠 8 个。到第 37 轮，
 * 同一种病在另外两条规矩上又长了出来：
 *
 * - **「不许出现累计或施压的说法」**在 `Encouragement.gameWords`（10 词）与
 *   `StarterRules.streakWords`（10 词）各有一张，重叠 9 个，前者多「勋章」、后者多「可得」；
 * - **「不许夹带要花钱的内容」**在 `Encouragement.moneyWords`（9 词）与
 *   `StarterRules.payToJoinWords`（14 词）各有一张，只重合「付费」「收费」两个词，
 *   `moneyWords` 里另外 7 个（下单、优惠、特价、折扣、购买、套餐、推荐商品）在起点侧
 *   根本不设防。
 *
 * 于是「这句话能不能上屏幕」取决于它被哪张表扫到——这正是第十二轮记下的那种自相矛盾。
 *
 * ## 它怎么判
 *
 * [registry] 是**规格**：每一条规矩叫什么、表叫什么名字、只许在哪一处定义、内容是哪些词。
 * 三条断言分别对应这三件事：
 *
 * 1. 每条规矩的表在 `main/` 里有且只有一处，名字与文件都要与规格一致；
 * 2. 除了那一处，`main/` 里**任何**一张字符串表都不许与它重合 2 个词以上——
 *    这一条是防「换个名字再长一张」的；
 * 3. 表里的词一个都不许漏（第 37 轮收口的方向是取并集，只会更严）。
 *
 * 第 3 条是存心的：这个词表是规格，改它就得同时改这里。谁要是把 `streakWords` 退回旧的
 * 10 词（少一个「可得」），或者把 `spendWords` 退回 `moneyWords` 那 9 词（少一个「下单」），
 * 这条用例都会红。
 *
 * ## 它不保证什么
 *
 * - 只认 `val 名字: List<String> = listOf("…")` 这一种写法。写成 `buildList`、从 `res/` 读、
 *   或者把禁词拆成两个字分别判，它都看不见。
 * - 只比**词**，不比**理由措辞**。同一个词在两个消费者那里跟在不同的说明文字后面，它不管。
 * - 它管的是「表有几张」，不是「扫得对不对」——后者归 [CopyRulesTest]。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（纪律同 `ui/UiCopyPlacementTest.kt`）。
 */
class CopyRulesPlacementTest {

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** 一条规矩的规格：规矩叫什么、表叫什么名字、只许在哪儿定义、内容是哪些词。 */
    private data class Registry(
        val rule: String,
        val name: String,
        val where: String,
        val words: List<String>,
    )

    private val registry: List<Registry> = listOf(
        Registry(
            rule = "累计与施压的说法（AC-08.4 / AC-10.2）",
            name = "streakWords",
            where = "com/fortheelder/domain/companion/CopyRules.kt",
            words = listOf(
                "打卡", "签到", "连续", "坚持", "积分", "等级", "徽章", "排行", "奖励", "可得", "勋章",
            ),
        ),
        Registry(
            rule = "要花钱的说法（AC-08.3 / AC-10.3）",
            name = "spendWords",
            where = "com/fortheelder/domain/companion/CopyRules.kt",
            words = listOf(
                "报名", "付费", "收费", "交费", "学费", "入会", "会员", "续费", "办卡", "押金",
                "优惠", "折扣", "购买", "下单", "推荐商品", "特价", "套餐",
            ),
        ),
        Registry(
            rule = "要留联系方式的说法（AC-08.3）",
            name = "contactWords",
            where = "com/fortheelder/domain/companion/CopyRules.kt",
            words = listOf("加微信", "公众号", "留个电话", "留下联系"),
        ),
        Registry(
            rule = "没法照做的空话（AC-08.1 的反面）",
            name = "vagueWords",
            where = "com/fortheelder/domain/companion/CopyRules.kt",
            words = listOf("了解一下", "了解一下就好", "感兴趣的话", "有机会可以"),
        ),
        Registry(
            rule = "放到哪件事上都成立的空话（AC-10.1 的反面）",
            name = "emptyPraiseWords",
            where = "com/fortheelder/domain/companion/CopyRules.kt",
            words = listOf("你真棒", "真棒", "加油", "继续努力", "厉害", "了不起", "点赞"),
        ),
        Registry(
            rule = "医疗与功效宣称（AC-11.1）",
            name = "banned",
            where = "com/fortheelder/domain/voice/ClaimWords.kt",
            words = listOf(
                "训练", "改善", "提升", "延缓", "评估", "认知能力", "疗效", "疗程",
                "治疗", "预防", "诊断", "康复", "大脑退化", "记忆力", "变聪明",
            ),
        ),
    )

    /** 扫到的一张表。 */
    private data class Found(val file: String, val line: Int, val name: String, val words: List<String>)

    /** 宽松的写法：`val 名字: List<String> = listOf("…", "…")`，跨行，不处理转义。 */
    private val definition = Regex(
        """val\s+(\w+)\s*:\s*List<String>\s*=\s*listOf\(([^)]*)\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun at(found: Found) = "${found.file}:${found.line}  ${found.name}（${found.words.size} 词）"

    @Test
    fun everyRuleHasExactlyOneTableAndItSitsWhereTheSpecSays() {
        val (found, _) = scan()
        val problems = mutableListOf<String>()

        for (rule in registry) {
            val named = found.filter { it.name == rule.name }
            if (named.size != 1) {
                problems += "「${rule.rule}」的表 `${rule.name}` 在 main/ 里出现了 ${named.size} 处，" +
                    "应该只有 1 处" +
                    named.joinToString(separator = "\n", prefix = "：\n") { "    " + at(it) }
                continue
            }
            val only = named.single()
            if (only.file != rule.where) {
                problems += "「${rule.rule}」的表 `${rule.name}` 在 ${only.file}:${only.line}，" +
                    "规格说的是 ${rule.where}"
            }
            val missing = rule.words.filter { it !in only.words }
            val extra = only.words.filter { it !in rule.words }
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                problems += "「${rule.rule}」的词与规格对不上（${only.file}:${only.line}）：" +
                    (if (missing.isEmpty()) "" else "少了 ${missing.joinToString("、")}；") +
                    (if (extra.isEmpty()) "" else "多了 ${extra.joinToString("、")}")
            }
        }

        assertTrue(
            "禁词表与规格对不上，共 ${problems.size} 条：\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun noSecondTableGrowsBackUnderAnotherName() {
        val (found, _) = scan()
        val canonical = registry.mapNotNull { rule -> found.singleOrNull { it.name == rule.name } }
        val offenders = mutableListOf<String>()

        for (rule in registry) {
            val shared = rule.words.toSet()
            for (table in found) {
                if (canonical.contains(table)) continue
                val overlap = table.words.toSet() intersect shared
                if (overlap.size >= 2) {
                    offenders += "${at(table)}  与「${rule.rule}」重合 ${overlap.size} 个词：" +
                        overlap.sorted().joinToString("、")
                }
            }
        }

        assertTrue(
            "main/ 里又长出了与已登记禁词表重合的表，共 ${offenders.size} 处。" +
                "一条规矩只能有一张表：把词并进 `CopyRules`（或 `ClaimWords`），别再抄一份：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScannerReallyReadTheSources() {
        // 防假绿：扫了个空目录、或者正则根本没匹配上，上面两条就会「零违规」地通过。
        val (found, scannedLines) = scan()
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)
        assertTrue("main 源码行数只有 $scannedLines 行，守卫可能没真的读到源码", scannedLines > 6000)
        assertTrue(
            "只扫到 ${found.size} 张字符串表，正则多半已经失效",
            found.size >= 10,
        )
        for (rule in registry) {
            assertTrue(
                "规格里的 `${rule.name}` 一张都没扫到，这条规矩的守卫形同虚设",
                found.any { it.name == rule.name },
            )
        }
    }

    /** 扫 `main/` 下全部 `.kt`，返回（≥2 个汉字的字符串表，扫过的行数）。 */
    private fun scan(): Pair<List<Found>, Int> {
        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)

        val found = mutableListOf<Found>()
        var scannedLines = 0
        for (file in files) {
            val text = file.readText(Charsets.UTF_8)
            val relative = file.relativeTo(mainSrc).path.replace('\\', '/')
            scannedLines += text.split("\n").size
            for (match in definition.findAll(text)) {
                val words = Regex("\"([^\"\\n]*)\"").findAll(match.groupValues[2])
                    .map { it.groupValues[1] }
                    .filter { cjk.containsMatchIn(it) }
                    .toList()
                if (words.size < 2) continue
                val line = text.take(match.range.first).count { it == '\n' } + 1
                found += Found(relative, line, match.groupValues[1], words)
            }
        }
        return found to scannedLines
    }
}
