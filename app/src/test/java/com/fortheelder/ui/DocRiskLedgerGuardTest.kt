package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ## 它做什么
 *
 * 判据 16 —— 盯住 `docs/07-风险管理计划.md`（下文简称「台账」）的**风险登记册与汇总表是否互相对得上**。
 *
 * 由来是第 140 轮的一次实测：`docs/07` §3 汇总表里有 **R-14** 这一行、§3.2 橙档里有它、§4 触发信号里也有它，
 * 而 §2 风险登记册里**根本没有 R-14 这一节**。三处引用一个不存在的条目，从第 49 轮新登记那天起一直没人看见 ——
 * 这正属 `docs/07` 自己登记的 R-10「文档与代码脱节」那一类：**文档内部对不上，却没有任何机器判据会红**。
 * 此前的几条文档类判据都不管这件事：`DocHeadingStructureGuardTest` 只扫标题层级与轮次标题连号（`### R-NN` 是小节标题，
 * 它按 `## ` 那一档看，够不着）；`DocCrossRefGuardTest` 只认带行号的 `docs/0X:NNN` 短引用；`DocLineRefGuardTest`
 * 只核设置页那一族的行号；`DocScreenCopyGuardTest` 只核引文与屏幕是否一致。于是「汇总表登记了、登记册没有」
 * 这种`内部账不平`的形态，一条判据都抓不住。这一轮把这条盲区关掉 —— 而且它**首跑就抓到一笔真错账**（就是 R-14）。
 *
 * 九个用例分工：
 * 1. [theRiskLedgerIsActuallyReadAndHasTheKnownBaseShape] —— 文件真在、真读得进来、字节数与行数不低于底线。
 * 2. [everyRiskRowInTheSummaryHasExactlyOneRegisteredSection] —— **本判据的核心**：§3 汇总表的编号集合与 §2 的
 *    `### R-NN` 小节集合**双向相等**（左=有汇总行却没有登记册小节；右=有登记册小节却没有汇总行）。
 * 3. [theRegisteredSectionsAreStrictlyIncreasingWithoutDuplicatesOrGaps] —— 编号抠成整数后 1..N 连续、不漏不重。
 * 4. [theSummaryRowsAreStrictlyIncreasingAndOnlyTheKnownIds] —— 汇总表的行序递增，且编号集合等于登记清单。
 * 5. [everyRiskSectionHasTheNineItemLedgerTable] —— 每节第一张 `| 项 | 内容 |` 表的项名逐个、逐序相等（九项）。
 * 6. [theLevelColumnInTheLedgerAndTheSummaryAgreeForEveryRisk] —— 同一编号在登记册里写的等级与汇总表里写的等级一致。
 * 7. [eachRiskSitsInTheBucketItsLevelSays] —— 红进 §3.1、橙进 §3.2、黄/绿进 §3.3，逐条点名；§3.4 里不许冒编号。
 * 8. [theLedgerRulerReportsUnregisteredRowsInsteadOfStayingGreen] —— 尺子自检：拿人造文本证明「汇总有、登记册没有」
 *    这个差集真会报红，而不是恒绿。
 * 9. [theFloorsAreSaneAndTheKnownReadingsHold] —— 底线自检：底线不许宽到形同虚设，记下的读数不许被悄悄改掉。
 *
 * ## 它不保证什么
 *
 * - **刻意不钉台账的精确字节数与 md5**。正文每改一个字都要人跟着滚常量，滚漏一次判据就退化成「一律通过」的假绿。
 *   字节数只进 [DocCurrentReadingGuardTest] 那份冻结清单，本判据只钉形状（编号集合、分档、九项台账、等级一致）。
 * - **只读这一份文档**。`docs/07` 与 `docs/01` ~ `docs/06`、`records` 下各份记录之间的交叉引用（例如 §4 触发信号里说的
 *   `PaletteContrastTest` 是否真在仓库里）**本判据一概不管** —— 它只管台账自己前后自洽。文档之间的互指照旧靠人。
 * - **不核正文说得对不对**。「这条风险的描述准不准、等级定得对不对」全靠人读；第 6 条只比两处写的等级字面量是否一致，
 *   它防的是「改了登记册忘了改汇总表」，**不是**「等级定错了」。
 * - 断言用的是 [readNormalized]（先把 CRLF 归一成 LF）。本仓库 `.gitattributes` 是 `* text=auto` 且 `core.autocrlf=true`，
 *   `git checkout` 会把盘上的 LF 转成 CRLF；此时若断言「`\r` 个数为 0」会造出假红。字节数底线用原始字节，两种行尾都过。
 * - **第 7 条只认三种档位与三个小节标记**。若将来有人把橙档拆成两个小节、或新增一个「紫」档，第 7 条会红，得人跟着改
 *   [bucketMarkers] 与 [knownOrangeIds] —— 这是刻意的：分档规则改了却没人来看，才是要防的事。
 * - **第 5 条只认每节的第一张九项表、且靠「第二个分隔行」止步**。R-10 那节的九项表后面**没有空行**，紧跟着另一张
 *   `| # | 宣称 | 事实出处 | … |` 表；第 5 条在遇到第二行全 `-` 的分隔行时停，因此**不会**把后面那张表抽进来。
 *   代价是：若某节的九项表后面紧接着再放一张同样是 `| 项 | 内容 |` 表头且没有分隔行，第 5 条会把它一并读进去。
 * - 「文件真被读了」只靠一条 `isFile` 加底线撑着：台账被删掉会红，被换成一个 285KB 的别的文件不会红
 *   （不过 [DocCurrentReadingGuardTest] 那份冻结清单会同时红，两张网互不替代）。
 *
 * ## 结论
 *
 * 第 140 轮实测（判据落地时的读数，那时候 R-14 一节已经补进 §2）：**309,101 字节**、`split("\n")` **804 行**、
 * §2 十五条 `### R-NN` 小节（R-01..R-15 连号）、§3 汇总表十五行、分档 1 / 9 / 5、每节九项台账齐、等级两处一致。
 * 判据落地**之前**的那一趟真跑是**四红**（第 2、3、6、7 条），红的就是「汇总表有 R-14、登记册没有」这一笔真错账，
 * 见 `build/r140/guard_pre_risk.log`；补上 R-14 一节后转绿。
 *
 * ⇒ 结论：台账的内部账目从第 140 轮起**有机器看着**（登记册 ↔ 汇总表 ↔ 分档 ↔ 触发信号四处的编号集合互校），
 * 但「等级定得对不对」「与别的文档对不对得上」仍然没有机器判据，`docs/07` 的风险 R-10 也仍然是橙色。
 */
class DocRiskLedgerGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val doc: File = File(repoRoot, "docs/07-风险管理计划.md")

    private val textCache: MutableMap<String, String> = mutableMapOf()

    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    /** `split("\n")` 口径：末尾带换行的文本会多出一个空串，与 `wc -l` 差 1。 */
    private fun linesOf(text: String): List<String> = text.split("\n")

    private fun ledgerText(): String = textCache.getOrPut(doc.path) { readNormalized(doc) }

    private fun sliceBetween(text: String, fromMarker: String, toMarker: String): String {
        val from: Int = text.indexOf(fromMarker)
        if (from < 0) return ""
        val to: Int = text.indexOf(toMarker, from)
        return if (to < 0) text.substring(from) else text.substring(from, to)
    }

    /** §2 登记册里的小节编号，按出现次序。全篇只有 §2 用 `### R-NN` 做小节标题。 */
    private fun sectionIds(text: String): List<String> =
        linesOf(text).mapNotNull { line -> Regex("""^### (R-\d+)""").find(line)?.groupValues?.get(1) }

    /** §3 汇总表的数据行编号（从 `## 3. 风险汇总` 起、到 `### 3.1` 止）。 */
    private fun summaryIds(text: String): List<String> = idsIn(
        sliceBetween(text, summaryMarker, bucketMarkers[0]),
    )

    /** 某一档小节里的编号（从该档标记起、到下一档标记或 §4 止）。 */
    private fun bucketIds(text: String, fromMarker: String, toMarker: String): List<String> =
        idsIn(sliceBetween(text, fromMarker, toMarker))

    private fun idsIn(slice: String): List<String> =
        Regex("""^\| (R-\d+) \|""", RegexOption.MULTILINE)
            .findAll(slice)
            .map { it.groupValues[1] }
            .toList()

    /** 某个风险小节里第一张 `| 项 | 内容 |` 表的项名；遇到第二个全 `-` 的分隔行就停。 */
    private fun ledgerItemsOf(text: String, id: String): List<String> {
        val heading: MatchResult = Regex("""^### ${Regex.escape(id)}(?:\s|$)""", RegexOption.MULTILINE)
            .find(text)
            ?: return emptyList()
        val after: List<String> = linesOf(text.substring(text.indexOf('\n', heading.range.first) + 1))
        val headerAt: Int = after.indexOfFirst { it.trim() == "| 项 | 内容 |" }
        if (headerAt < 0) return emptyList()
        val items: MutableList<String> = mutableListOf()
        var separators: Int = 0
        for (line in after.drop(headerAt + 1)) {
            if (!line.startsWith("|")) break
            val name: String = line.split("|").getOrElse(1) { "" }.trim()
            if (name.isNotEmpty() && name.all { it == '-' || it == ' ' }) {
                separators += 1
                if (separators >= 2) break
                continue
            }
            items += name
        }
        return items
    }

    /** 某个风险小节里 `| 等级 | … |` 那一格的原样文字；小节不存在时返回 `null`。 */
    private fun ledgerLevelOf(text: String, id: String): String? {
        val heading: MatchResult = Regex("""^### ${Regex.escape(id)}(?:\s|$)""", RegexOption.MULTILINE)
            .find(text)
            ?: return null
        val rest: String = text.substring(text.indexOf('\n', heading.range.first) + 1)
        val nextHeading: MatchResult? = Regex("""^### R-\d+|^## """, RegexOption.MULTILINE).find(rest)
        val section: String = if (nextHeading == null) rest else rest.substring(0, nextHeading.range.first)
        return Regex("""^\| 等级 \| (.+?) \|$""", RegexOption.MULTILINE).find(section)?.groupValues?.get(1)
    }

    /** 汇总表某一行里 `等级` 那一格的原样文字；该行不存在时返回 `null`。 */
    private fun summaryLevelOf(text: String, id: String): String? {
        val line: String = sliceBetween(text, summaryMarker, bucketMarkers[0])
            .split("\n")
            .firstOrNull { it.startsWith("| $id |") }
            ?: return null
        return line.split("|").getOrNull(5)?.trim()
    }

    /** 把 `**橙**（理由…）` 归一成 `橙`：剥加粗记号、去掉括注。 */
    private fun normLevel(raw: String?): String? =
        raw?.replace("*", "")?.substringBefore("（")?.trim()?.ifEmpty { null }

    @Test
    fun theRiskLedgerIsActuallyReadAndHasTheKnownBaseShape() {
        assertTrue("台账不存在：${doc.path}", doc.isFile)
        val rawBytes: Int = doc.readBytes().size
        assertTrue(
            "台账只有 $rawBytes 字节，低于底线 $byteFloor —— 文件被截断、或者被换成了别的东西",
            rawBytes >= byteFloor,
        )
        val text: String = ledgerText()
        assertTrue("台账读进来是空的", text.isNotBlank())
        val lineCount: Int = linesOf(text).size
        assertTrue(
            "台账只有 $lineCount 行（split 口径），低于底线 $splitLineFloor",
            lineCount >= splitLineFloor,
        )
    }

    @Test
    fun everyRiskRowInTheSummaryHasExactlyOneRegisteredSection() {
        val text: String = ledgerText()
        val registered: Set<String> = sectionIds(text).toSet()
        val summarized: Set<String> = summaryIds(text).toSet()
        assertFalse("§2 登记册一个小节都没抽到，尺子坏了", registered.isEmpty())
        assertFalse("§3 汇总表一行都没抽到，尺子坏了", summarized.isEmpty())
        assertEquals(
            "§3 汇总表登记了、§2 风险登记册里却没有对应小节的编号（左=孤儿汇总行）——" +
                "第 140 轮到第 49 轮的 R-14 就是积在这里的一笔真错账",
            emptySet<String>(),
            summarized - registered,
        )
        assertEquals(
            "§2 风险登记册有小节、§3 汇总表里却没有对应行的编号（左=孤儿小节）——" +
                "登记册加了新风险却忘了进汇总表",
            emptySet<String>(),
            registered - summarized,
        )
    }

    @Test
    fun theRegisteredSectionsAreStrictlyIncreasingWithoutDuplicatesOrGaps() {
        val ids: List<String> = sectionIds(ledgerText())
        assertTrue("登记册里一个小节都没抽到", ids.isNotEmpty())
        val numbers: List<Int> = ids.map { it.removePrefix("R-").toInt() }
        assertEquals(
            "登记册的小节编号应当从 1 连到 N、不漏不重（实际 $ids）——" +
                "缺号就是「汇总表引了一个没登记的风险」，重号就是有人复制粘贴时忘了改编号",
            (1..numbers.size).toList(),
            numbers,
        )
    }

    @Test
    fun theSummaryRowsAreStrictlyIncreasingAndOnlyTheKnownIds() {
        val ids: List<String> = summaryIds(ledgerText())
        val numbers: List<Int> = ids.map { it.removePrefix("R-").toInt() }
        assertEquals(
            "§3 汇总表的行序应当严格递增（实际 $ids）—— 行序被打乱会让「档内比大小」的人看错",
            numbers.sorted(),
            numbers,
        )
        assertEquals(
            "§3 汇总表里的编号集合与登记清单不一致（左=登记值，右=表里实际的）；" +
                "汇总表新增或删除一行都会红，逼着人来看一眼",
            knownSummaryIds.sorted(),
            ids.distinct().sorted(),
        )
    }

    @Test
    fun everyRiskSectionHasTheNineItemLedgerTable() {
        val text: String = ledgerText()
        val ids: List<String> = sectionIds(text)
        assertTrue("登记册里一个小节都没有，尺子没在用", ids.isNotEmpty())
        ids.forEach { id ->
            assertEquals(
                "$id 那一节里第一张 `| 项 | 内容 |` 表的项名与登记值不一致（左=登记值，右=该节实际的）；" +
                    "少一行就是台账缺项（比如忘了写「监控信号」），多一行就是有人往台账里塞了别的表",
                ledgerItemNames,
                ledgerItemsOf(text, id),
            )
        }
    }

    @Test
    fun theLevelColumnInTheLedgerAndTheSummaryAgreeForEveryRisk() {
        val text: String = ledgerText()
        knownSummaryIds.forEach { id ->
            val inSection: String? = normLevel(ledgerLevelOf(text, id))
            val inSummary: String? = normLevel(summaryLevelOf(text, id))
            assertTrue("$id 在 §3 汇总表里有行，§2 登记册里却没有 `| 等级 | … |` 那一格", inSection != null)
            assertTrue("$id 在 §2 登记册里有小节，§3 汇总表里却读不出等级那一格", inSummary != null)
            assertEquals(
                "$id 在登记册里写的等级与汇总表里写的不一致（左=登记册，右=汇总表）——" +
                    "改了登记册忘了改汇总表（或反过来）就会这样",
                inSection,
                inSummary,
            )
        }
    }

    @Test
    fun eachRiskSitsInTheBucketItsLevelSays() {
        val text: String = ledgerText()
        val red: List<String> = bucketIds(text, bucketMarkers[0], bucketMarkers[1])
        val orange: List<String> = bucketIds(text, bucketMarkers[1], bucketMarkers[2])
        val yellowGreen: List<String> = bucketIds(text, bucketMarkers[2], bucketMarkers[3])
        assertEquals("§3.1（红）里的编号与登记值不一致", knownRedIds.sorted(), red.distinct().sorted())
        assertEquals("§3.2（橙）里的编号与登记值不一致", knownOrangeIds.sorted(), orange.distinct().sorted())
        assertEquals(
            "§3.3（黄 / 绿）里的编号与登记值不一致",
            knownYellowGreenIds.sorted(),
            yellowGreen.distinct().sorted(),
        )
        (red + orange + yellowGreen).forEach { id ->
            val level: String? = normLevel(ledgerLevelOf(text, id))
            assertTrue("$id 被分进了某一档，登记册里却读不出它的等级", level != null)
            val expectedBucket: List<String> = when (level) {
                "红" -> red
                "橙" -> orange
                "黄", "绿" -> yellowGreen
                else -> emptyList()
            }
            assertTrue(
                "$id 的等级是「$level」，却不在它该在的那一档里 —— 档位是 [红→§3.1、橙→§3.2、黄/绿→§3.3]",
                id in expectedBucket,
            )
        }
        assertEquals(
            "§3.4（与需求缺口的对应）里冒出了 `| R-NN |` 行 —— 那一节列的是 GAP 编号，不该夹风险编号",
            emptyList<String>(),
            bucketIds(text, bucketMarkers[3], sectionFourMarker),
        )
    }

    @Test
    fun theLedgerRulerReportsUnregisteredRowsInsteadOfStayingGreen() {
        val fixture: String = buildString {
            append("## 3. 风险汇总\n")
            append("| 编号 | 风险 | P | I | 等级 | 策略 | 状态 |\n")
            append("| --- | --- | --- | --- | --- | --- | --- |\n")
            append("| R-01 | 甲 | 高 | 中 | 橙 | 减轻 | 已落实 |\n")
            append("| R-99 | 乙 | 高 | 中 | 橙 | 减轻 | 已落实 |\n")
            append("\n### 3.1 需立即处理（红）\n\n")
            append("| R-01 | 甲 |\n")
            append("\n### R-01 甲（登记册里只有这一节）\n\n")
            append("| 项 | 内容 |\n| --- | --- |\n| 等级 | **橙** |\n")
        }
        val registered: Set<String> = sectionIds(fixture).toSet()
        val summarized: Set<String> = summaryIds(fixture).toSet()
        assertEquals("人造文本里 §2 只该抽到 R-01", setOf("R-01"), registered)
        assertEquals("人造文本里 §3 汇总表只该抽到 R-01 与 R-99", setOf("R-01", "R-99"), summarized)
        assertEquals(
            "尺子坏了：人造的「汇总表有 R-99、登记册没有」这个差集读出来是空的，等于恒绿",
            setOf("R-99"),
            summarized - registered,
        )
    }

    @Test
    fun theFloorsAreSaneAndTheKnownReadingsHold() {
        assertTrue("byteFloor 必须为正", byteFloor > 0)
        assertTrue("byteFloor=$byteFloor 高于实测的 $knownBytes 字节，底线比现实还高，会立刻假红", byteFloor <= knownBytes)
        assertTrue("byteFloor=$byteFloor 连实测的一半都不到，宽到形同虚设", byteFloor * 2 >= knownBytes)
        assertTrue("splitLineFloor 必须为正", splitLineFloor > 0)
        assertTrue("splitLineFloor=$splitLineFloor 高于实测的 $knownSplitLines 行", splitLineFloor <= knownSplitLines)
        assertTrue("splitLineFloor=$splitLineFloor 连实测的一半都不到", splitLineFloor * 2 >= knownSplitLines)

        assertEquals("第 140 轮实测的字节数", 299_377, knownBytes)
        assertEquals("第 140 轮实测的 split 行数", 804, knownSplitLines)
        assertEquals("每节九项台账", 9, ledgerItemNames.size)
        assertEquals("§2 登记册有十五个小节", 15, knownSectionIds.size)
        assertEquals("§3 汇总表有十五行", 15, knownSummaryIds.size)
        assertEquals("红档一条", 1, knownRedIds.size)
        assertEquals("橙档九条", 9, knownOrangeIds.size)
        assertEquals("黄 / 绿档五条", 5, knownYellowGreenIds.size)
        assertEquals("三档加起来正好覆盖十五条", 15, knownRedIds.size + knownOrangeIds.size + knownYellowGreenIds.size)
    }

    private companion object {
        /** 底线：第 140 轮实测 299,377 字节，留出余量到 290,000。 */
        const val byteFloor: Int = 290_000

        /** 底线：第 140 轮实测 `split("\n")` 804 行，留出余量到 760。 */
        const val splitLineFloor: Int = 760

        const val knownBytes: Int = 299_377

        const val knownSplitLines: Int = 804

        /** 每张九项台账表的项名，逐序；第 5 条按这个清单逐项比对。 */
        val ledgerItemNames: List<String> = listOf(
            "来源",
            "类别",
            "描述",
            "触发条件",
            "P",
            "I",
            "等级",
            "应对",
            "监控信号",
        )

        /** §2 登记册的小节编号，第 140 轮实测十五条（R-01..R-15 连号）。 */
        val knownSectionIds: List<String> = (1..15).map { "R-%02d".format(it) }

        /** §3 汇总表的行编号，与登记册一致。 */
        val knownSummaryIds: List<String> = (1..15).map { "R-%02d".format(it) }

        val knownRedIds: List<String> = listOf("R-09")

        val knownOrangeIds: List<String> = listOf(
            "R-01", "R-02", "R-03", "R-05", "R-10", "R-11", "R-12", "R-14", "R-15",
        )

        val knownYellowGreenIds: List<String> = listOf("R-04", "R-06", "R-07", "R-08", "R-13")

        const val summaryMarker: String = "## 3. 风险汇总"

        const val sectionFourMarker: String = "## 4. 风险触发信号"

        /** §3 的四个小节标记，按序：第 7 条靠它们切档。 */
        val bucketMarkers: List<String> = listOf(
            "### 3.1 需立即处理（红）",
            "### 3.2 计划处理（橙）",
            "### 3.3 接受并记录（黄 / 绿）",
            "### 3.4 与需求缺口的对应",
        )
    }
}
