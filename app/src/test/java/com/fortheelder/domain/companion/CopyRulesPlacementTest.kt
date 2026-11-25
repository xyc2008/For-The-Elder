package com.fortheelder.domain.companion

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一条规矩只有一张表」的机械守卫（第 37 轮新增，第 94 轮补了第 4 条「谁在用它」，
 * 第 95 轮补了第 5 条「那份名单自己是靠什么数出来的」）。
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
 * 五条断言分别对应这五件事：
 *
 * 1. 每条规矩的表在 `main/` 里有且只有一处，名字与文件都要与规格一致；
 * 2. 除了那一处，`main/` 里**任何**一张字符串表都不许与它重合 2 个词以上——
 *    这一条是防「换个名字再长一张」的；
 * 3. 表里的词一个都不许漏（第 37 轮收口的方向是取并集，只会更严）；
 * 4. 把文案交给这张表扫的产出方只有登记的那三个（第 94 轮补的）——
 *    第 1~3 条管的是「表有几张、词对不对」，没有一条管「谁在用它」；
 * 5. 「那份产出方名单」本身是**怎么数出来的**：真调用点算，两种注释里的写法都不算
 *    （第 95 轮补的）——第 4 条只会说名单对不对，指不出毛病是在「数」这一步。
 *    这一条分两头钉：先拿人造片段钉住 [submitsCopyToTable] 算什么、不算什么，
 *    再拿一个人造目录钉住 [producersIn] 那行接线**真的在调它**。后半头是第 95 轮
 *    反向变异逼出来的（`records/04` 第九十五轮 M2）：把那行接线改回原始写法，
 *    全量 582 例一个都不红。
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
 * - 第 4、5 条只在**调用点**这一层看名单，不看那一页的文案是不是真的被扫干净了。
 *   第 5 条盯的是「剥注释」这一步，剥法仍旧是**弱的**：按行尾把 `//` 之后整段去掉，
 *   分不出这个 `//` 是在字符串里还是在代码里。`main/` 今天没有这样的行（第 95 轮探针
 *   实测 0 行），所以没误伤；哪天有了，这里会先红。点号前带空格的 `CopyRules . violations(x)`
 *   认不出来，是**故意不收**的（理由见 [submitsCopyToTable] 的说明）。
 * - [callSite] 里的 `hit` 那一支，`main/` 今天**一次都没被真人用过**：三个产出方调的都是
 *   `violations(`（第 95 轮实测）。所以删掉 `|hit` 时，红的是第 5 条的人造断言，第 4 条照旧绿。
 *   这一支现在只有人造例在守——留着是因为 `CopyRules.hit` 是公开 API。
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

    /** 「把文案交给纪律表扫」的写法：`CopyRules.violations(` 或 `CopyRules.hit(`。 */
    private val callSite = Regex("""CopyRules\.(violations|hit)\s*\(""")

    /** `/* … */` 块注释，用来先把说明文字剥掉再找调用点。 */
    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    /**
     * `// …` 行注释，同样要剥掉。
     *
     * 第 95 轮实测：不剥就会把注释里的写法当成调用点——往 `main/` 放一个只有
     * `// 旧写法：CopyRules.hit(` 这一行的文件，第 4 条当场报出第四个消费者
     * （读数见 `records/04` 第九十五轮）。`main/` 里没有任何字符串字面量含 `//`
     * （第 95 轮探针实测），所以按行尾剥不会误伤真代码。
     */
    private val lineComment = Regex("""//[^\n]*""")

    /** 宽松的写法：`val 名字: List<String> = listOf("…", "…")`，跨行，不处理转义。 */
    private val definition = Regex(
        """val\s+(\w+)\s*:\s*List<String>\s*=\s*listOf\(([^)]*)\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun at(found: Found) = "${found.file}:${found.line}  ${found.name}（${found.words.size} 词）"

    /**
     * 一段源码里有没有**真**的调用点：先剥块注释、再剥行注释，在剩下的代码里找 [callSite]。
     *
     * 第 95 轮把这一步从 [producersIn] 里抽出来，好让人造文本直接喂进来——否则「剥注释」
     * 这步本身没有任何用例在盯（见 [theProducerScannerCountsRealCallSitesAndIgnoresBothKindsOfComment]）。
     *
     * 已知的宽松处，第 95 轮探针实测、**故意不收**：点号前带空格的写法
     * `CopyRules . violations(x)` 不匹配、会被漏掉。Kotlin 里没人这么写；要收就得连换行
     * 一起容进来，得不偿失。
     */
    private fun submitsCopyToTable(code: String): Boolean =
        callSite.containsMatchIn(code.replace(blockComment, "").replace(lineComment, ""))

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

    @Test
    fun onlyTheRegisteredProducersSubmitCopyToTheTable() {
        // 第 94 轮补的第四层：前三条管「表有几张、词对不对、扫没扫到」，没有一条管「谁在用它」。
        // `CopyRules` 的说明文字原先写的是「谁产出的文案，都拿同一张表」——那是句做不到的
        // 全称承诺：第 94 轮实测，把文案交给它扫的只有下面三处。
        // 这条把名单**写死**：多一个消费者（新页面直接调它），或者少一个（调用点被删、
        // 某一页的文案从此不过表），都会红。
        val expected = setOf(
            "com/fortheelder/domain/companion/CompanionVoice.kt",
            "com/fortheelder/domain/companion/Encouragement.kt",
            "com/fortheelder/domain/companion/StarterOptions.kt",
        )
        val actual = producersIn()
        assertEquals(
            "把文案交给 `CopyRules` 扫的产出方与登记名单对不上。多一个消费者就得把它同时" +
                "写进 `CopyRules` 的 KDoc 名单与 docs/05 的 IF-14j；少一个就要说清这一页的" +
                "文案现在归谁管。\n登记：${expected.sorted()}\n实际：${actual.sorted()}",
            expected,
            actual,
        )
    }

    @Test
    fun theProducerScannerCountsRealCallSitesAndIgnoresBothKindsOfComment() {
        // 第 95 轮补的第五层。第 4 条管的是「谁在用它」，可它自己赖以成立的那一步——
        // 「先剥注释再找调用点」——原先没有任何用例在盯：这一步被改松（不再剥行注释），
        // 或者改到把真调用点一起剥没，第 4 条都只会在**名单**上红或绿，指不出毛病在这一步。
        // 这里拿人造文本直接喂同一个提取函数，逐条钉住「算什么、不算什么」。
        assertTrue(
            "真调用点就是调用点：CopyRules.violations(text)",
            submitsCopyToTable("val v = CopyRules.violations(text)"),
        )
        assertTrue(
            "真调用点就是调用点：CopyRules.hit(text, words)",
            submitsCopyToTable("val v = CopyRules.hit(text, words)"),
        )
        assertTrue(
            "真调用点后面跟一句行注释，调用点还在（剥行注释不许把这一行本身剥掉）",
            submitsCopyToTable("val v = CopyRules.hit(t, w) // 只扫这两类"),
        )
        assertFalse(
            "块注释里抄一遍旧调用，不算调用点",
            submitsCopyToTable("/* 旧写法：CopyRules.hit(text, words) */"),
        )
        assertFalse(
            "行注释里的写法不算调用点——第 94 轮留下的待办，第 95 轮实测原口径会误判",
            submitsCopyToTable("// 旧写法：CopyRules.hit(text, words)"),
        )
        assertFalse(
            "缩进的行注释同理",
            submitsCopyToTable("            // CopyRules.violations("),
        )
        assertFalse(
            "一行里没有调用点形状就是没有（TalkViewModel.kt 里那种带 `// copy-ok:` 的行）",
            submitsCopyToTable("\"我一直想学书法\", // copy-ok: 示例句子表"),
        )
        assertFalse(
            "`SubtractSeven.kt` 那行说明文字本来就不匹配调用点正则：第 94 轮把它说成" +
                "「不剥块注释就会被误当成第四个消费者」，第 95 轮实测那句话不成立",
            callSite.containsMatchIn(
                "* 「连续」两个字在文案纪律表 `CopyRules.streakWords` 里是禁词，措辞上也就一并避开了。",
            ),
        )

        // 上面几条喂的都是单个片段，钉住的是 [submitsCopyToTable] 这个函数本身。
        // 「名单那一侧真的调了它」还得单独钉：第 95 轮反向变异实测，把 [producersIn] 里
        // 那一行退回不剥注释的原始写法，全量 582 例**一个都不红**（`records/04` M2）。
        // 所以这里造一个小目录当 `main/` 的替身，直接看名单。
        val scratch = Files.createTempDirectory("copyRulesProducers95").toFile()
        try {
            File(scratch, "Real.kt").writeText(
                "package p\n\nval a = CopyRules.hit(t, w)\n",
                Charsets.UTF_8,
            )
            File(scratch, "LineComment.kt").writeText(
                "package p\n\n// 旧写法：CopyRules.violations(x)\n",
                Charsets.UTF_8,
            )
            File(scratch, "BlockComment.kt").writeText(
                "package p\n\n/* 旧写法：CopyRules.hit(t, w) */\n",
                Charsets.UTF_8,
            )
            assertEquals(
                "名单那一侧必须真的走 [submitsCopyToTable]：三种注释/字符串里的写法一个都不该进名单",
                setOf("Real.kt"),
                producersIn(scratch),
            )
        } finally {
            scratch.deleteRecursively()
        }
    }

    /**
     * 扫 [root] 全部 `.kt`，返回**调过**文案纪律表的文件（相对路径，`/` 分隔）。
     *
     * 「调过」= 剥掉两种注释之后还剩下 [callSite] 这个形状，判据是 [submitsCopyToTable]。
     * 第 95 轮实测把这一步原先的说法改了两处：
     *
     * - 剥**块注释**这一步，第 94 轮的 KDoc 说成是「为了不把 `SubtractSeven.kt` 里的说明文字
     *   误当成调用点」。这句是错的：那行写的是 `CopyRules.streakWords`，[callSite] 从来不匹配它；
     *   而 `main/` 里**块注释内部**含调用点形状的地方有 **0 处**（第 95 轮探针实测），所以这一步
     *   今天没在挡任何人。留着是为了防以后有人在注释里抄一遍旧调用——人造例已立。
     * - 剥**行注释**这一步是第 95 轮补的。第 94 轮的 KDoc 自己写着「行注释里写一句
     *   `CopyRules.hit(` 会不会被误当成调用点，第 94 轮没有实测」；第 95 轮实测：**会**——
     *   往 `main/` 放一个只含那一行的文件，第 4 条当场报出第四个消费者（读数见 `records/04`）。
     *
     * [root] 开成参数是第 95 轮的事：第 4 条只会说「名单对不对」，看不出「这名单是数出来的
     * 还是抄来的」；要钉住下面这行接线，只能让第 5 条喂进一个人造目录。文件数下限那条断言
     * 只对真 `main/` 生效，人造目录不适用。
     */
    private fun producersIn(root: File = mainSrc): Set<String> {
        val files = root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
        if (root == mainSrc) {
            assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)
        }

        val producers = mutableSetOf<String>()
        for (file in files) {
            if (submitsCopyToTable(file.readText(Charsets.UTF_8))) {
                producers += file.relativeTo(root).path.replace('\\', '/')
            }
        }
        return producers
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
