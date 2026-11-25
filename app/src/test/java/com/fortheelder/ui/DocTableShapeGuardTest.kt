package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「Markdown 表格的形状」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 此前七道文档守卫读的都是**字**（反引号引文、「」引文、行号、点名的符号），没有一道读过**形状**。
 * 而这一族文档里散着上千张表：一个格子里的字面量竖线（`` `a|b` ``）会把那一行**劈开**，
 * 于是 GFM 渲染出来比它旁边那些行多出一格（或反过来少一格）——**读起来仍然通顺**，因为源文本
 * 本来就是一句完整的话；只有当渲染器去对齐列宽时才看得出来。这正是第 96 / 97 轮两处口径纠偏的成因：
 * 「表格里有几处跨格」这件事，靠人眼读是数不准的（第 96 轮记成 4 行，实测 11 行）。
 *
 * 第 98 轮对 `docs` 与 `records` 两个目录下的全部 15 份 `.md` 做了第一次集成电路式的表格普查：
 * 1081 张表、7365 个数据行、**18 处跨格**（多一格 16 处、少一格 2 处），本轮把它们全部修掉；
 * 本文档守卫就是那次普查的产物。普查脚本与逐文件读数留档在 `build/r98/`。
 *
 * ## 它做什么
 *
 * 把「一行怎么切成格」这件事抽成可单独喂的 [splitCells]，再用四条用例钉住：
 *
 * 1. [everyMarkdownTableRowHasExactlyAsManyCellsAsItsHeader]：全仓 15 份 Markdown 逐张表逐行，
 *    数据行的格数必须**等于**表头格数——判据是 `!=`，**多一格与少一格都算红**。
 * 2. [theCellSplitterSplitsRowsTheWayGitHubDoes]：8 条人造片段钉死切分器本身（未转义的 `|` 照切、
 *    `\|` 不切、**反引号代码里的 `|` 照样切**、首尾竖线不算格、行尾多一个竖线就多一格……）。
 *    第 1 条用例绿而这一条红，说明是「切分器把不该切的切了／该切的不切」，两者能分开。
 * 3. [theScannerActuallyFoundTheWholeRepository]：下限自检——文件数、表数、行数、每份至少一张表。
 *    扫描范围塌成一份空文件也会当场红，而不是「零处跨格 ⇒ 全绿」。
 * 4. [noUnescapedPipeAppearsInsideADocRevisionTable]：定点反例——`docs/07` 的修订记录表（42 行起）
 *    每行恰 3 格，外加一张「已修掉的旧串不许回来」登记（[revisionTableLegacy]）。
 *
 * 下限纪律抄自 [DocLineRefGuardTest] 与 [DocSymbolClaimGuardTest]。
 *
 * ## 它不保证什么
 *
 * - **不代替渲染器**。这里实现的是「GFM 行内竖线切法」的一个**近似**：真正的 CommonMark 还要处理
 *   表格里的 HTML 实体、嵌套行内容器等情形，本切分器一概不认。它只覆盖本仓文档实际用到的那几种写法。
 * - **不管列宽、不管对齐**。格数对了但某一格写了个 4000 字的句子（`docs/07` 的 1.37 / 1.38 就是这个样子），
 *   本文档守卫一句话也不说。表格「好不好读」照旧只由人工兜着。
 * - **不管 `records/` 之外的 Markdown**。`README.md`、`.github/`、`app/` 下那些 `.md` 不在扫描范围内；
 *   第 98 轮选择范围的依据是「这一族文档是交付物」，不是「其余文件里没有表格」。
 * - **数量下限是当时的读数**。1081 / 7365 是 2026-11-25 第 98 轮的实测值，只作**下限**；文档继续写下去
 *   这两个数只会往上走，不会往下走。
 *
 * ## 结论
 *
 * **R-10 仍然是橙色**：从第 98 轮起，「文档里那张表的形状对不对」在**这一族之内**有机器兜底；
 * 这一族之外（列宽、可读性、非本仓 Markdown）照旧只能靠人工。
 *
 * 源码目录由 `app/build.gradle.kts:98` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。
 */
class DocTableShapeGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val textCache = mutableMapOf<String, String>()
    private val censusCache = mutableMapOf<String, TableCensus>()

    /** 去掉 CRLF 再切行：本仓这 15 份文档里 `docs/00/01/02`、`records/01/02` 是 CRLF，其余是 LF。 */
    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun docText(rel: String): String = textCache.getOrPut(rel) {
        val file = File(repoRoot, rel)
        assertTrue("文档不存在：$rel", file.isFile)
        readNormalized(file)
    }

    /** 一份 Markdown 里扫到的表格块、数据行、以及跨格的行。 */
    private data class TableCensus(val tables: Int, val rows: Int, val issues: List<String>)

    /**
     * 扫描范围：`docs` 与 `records` 两个目录下的 `.md`（按路径排序，读起来与 `build/r98/census98.py` 的顺序一致）。
     * 目录不存在时当场红——扫描范围没了比扫出问题更糟。
     */
    private fun scannedDocs(): List<String> = SCAN_DIRS.flatMap { dir ->
        val dd = File(repoRoot, dir)
        assertTrue("扫描目录不存在：$dir（扫描范围可疑）", dd.isDirectory)
        (dd.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension == "md" }
            .map { "$dir/${it.name}" }
    }.sorted()

    /**
     * 把一行切成格。判据（与 `build/r98/census98.py` 的 `split_cells` 逐条对齐）：
     *
     * - `\|` 是**转义**：不切，且反斜杠原样留在格里（GFM 渲染时才把 `\|` 显示成 `|`）。
     * - 其余每一个 `|` 都是分隔符，**反引号代码跨里的 `|` 也不例外**——这一条正是 `docs/07` 那一族
     *   被劈开的成因，故选它做一个独立用例。
     * - 行首 / 行尾那个竖线造成的空串各剥一次，**只剥一次**（行尾多写一个竖线就会真的多出一格）。
     */
    private fun splitCells(row: String): List<String> {
        val s = row.trim()
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                cur.append(ch).append(s[i + 1])
                i += 2
                continue
            }
            if (ch == '|') {
                cells += cur.toString()
                cur.setLength(0)
                i++
                continue
            }
            cur.append(ch)
            i++
        }
        cells += cur.toString()
        if (cells.first().isBlank()) cells.removeAt(0)
        if (cells.isNotEmpty() && cells.last().isBlank()) cells.removeAt(cells.size - 1)
        return cells
    }

    /** 分隔行：`| --- | :---: | ---: |` 这一类，只由首尾竖线、空白、`-`、`:` 组成。 */
    private fun isSeparatorRow(row: String): Boolean = SEPARATOR.matches(row)

    /**
     * 与普查脚本同形的扫描：跳过 ``` / ~~~ 围栏；表格块 = 连续的首字符（去空白后）为 `|` 的行；
     * 表头是块内第一行；分隔行（只在第二行位置上认）算一个数据行但不比格数。
     */
    private fun census(text: String): TableCensus {
        val lines = text.split("\n")
        var tables = 0
        var rows = 0
        val issues = mutableListOf<String>()
        var inFence = false
        var fenceMark = ""
        var i = 0
        while (i < lines.size) {
            val st = lines[i].trim()
            if (st.startsWith("```") || st.startsWith("~~~")) {
                val mark = st.take(3)
                if (!inFence) {
                    inFence = true
                    fenceMark = mark
                } else if (mark == fenceMark) {
                    inFence = false
                    fenceMark = ""
                }
                i++
                continue
            }
            if (inFence) {
                i++
                continue
            }
            if (!st.startsWith("|")) {
                i++
                continue
            }
            val block = mutableListOf<Pair<Int, String>>()
            var j = i
            while (j < lines.size && lines[j].trim().startsWith("|")) {
                block += (j + 1) to lines[j]
                j++
            }
            tables++
            val header = splitCells(block[0].second).size
            for (k in 1 until block.size) {
                rows++
                val (lineno, rowText) = block[k]
                if (k == 1 && isSeparatorRow(rowText)) continue
                val cells = splitCells(rowText)
                if (cells.size != header) {
                    val detail = if (cells.size > header) {
                        "，多出的格：[" + cells.drop(header).joinToString(" / ") { it.trim().take(60) } + "]"
                    } else {
                        "，少了 ${header - cells.size} 格"
                    }
                    issues += "第 $lineno 行：表头 $header 格，本行 ${cells.size} 格$detail"
                }
            }
            i = j
        }
        return TableCensus(tables = tables, rows = rows, issues = issues)
    }

    private fun censusOf(rel: String): TableCensus = censusCache.getOrPut(rel) { census(docText(rel)) }

    @Test
    fun everyMarkdownTableRowHasExactlyAsManyCellsAsItsHeader() {
        val docs = scannedDocs()
        assertTrue("扫到的 Markdown 只有 ${docs.size} 份（下限 $docFileFloor），扫描范围可疑", docs.size >= docFileFloor)
        assertEquals("扫描结果里有重复路径", docs.size, docs.toSet().size)

        val complaints = mutableListOf<String>()
        for (rel in docs) {
            for (issue in censusOf(rel).issues) complaints += "$rel $issue"
        }
        assertEquals(
            "表格里有 ${complaints.size} 处格数与表头对不上（多一格与少一格都算）：\n" +
                complaints.joinToString("\n"),
            0,
            complaints.size,
        )

        assertCensusFloors(docs)
    }

    @Test
    fun theCellSplitterSplitsRowsTheWayGitHubDoes() {
        assertTrue("切分样本被删空了（下限 $splitCaseFloor 条）：${splitCases.size}", splitCases.size >= splitCaseFloor)
        val complaints = mutableListOf<String>()
        for ((label, row, expected) in splitCases) {
            val actual = splitCells(row).map { it.trim() }
            if (actual != expected) complaints += "$label：$row 切成 $actual，期望 $expected"
        }
        assertEquals("切分器与 GFM 的切法不一致：\n" + complaints.joinToString("\n"), 0, complaints.size)

        val separators = listOf("| --- | --- | --- |", "| :--- | :---: | ---: |")
        val notSeparators = listOf("| 1.0 | 2026-11-21 | 第 14 轮首版 |", "| --- | 甲 | --- |")
        val sepComplaints = mutableListOf<String>()
        for (row in separators) if (!isSeparatorRow(row)) sepComplaints += "分隔行没认出来：$row"
        for (row in notSeparators) if (isSeparatorRow(row)) sepComplaints += "把普通行认成了分隔行：$row"
        assertEquals("分隔行判据不对：\n" + sepComplaints.joinToString("\n"), 0, sepComplaints.size)
    }

    @Test
    fun theScannerActuallyFoundTheWholeRepository() {
        val docs = scannedDocs()
        val complaints = mutableListOf<String>()
        if (docs.size < docFileFloor) complaints += "只扫到 ${docs.size} 份 Markdown（下限 $docFileFloor）"
        var tables = 0
        var rows = 0
        for (rel in docs) {
            val c = censusOf(rel)
            if (c.tables == 0) complaints += "$rel 里一张表都没扫到"
            tables += c.tables
            rows += c.rows
        }
        if (tables < tableFloor) complaints += "只扫到 $tables 张表（下限 $tableFloor）"
        if (rows < rowFloor) complaints += "只扫到 $rows 个数据行（下限 $rowFloor）"
        assertEquals("普查范围塌了：\n" + complaints.joinToString("\n"), 0, complaints.size)

        assertTrue(
            "扫描范围里必须有 docs/07-风险管理计划.md，现在只有 $docs",
            REVISION_TABLE_DOC in docs,
        )
    }

    @Test
    fun noUnescapedPipeAppearsInsideADocRevisionTable() {
        val text = docText(REVISION_TABLE_DOC)
        val lines = text.split("\n")
        val header = lines.indexOfFirst { it.trim() == REVISION_TABLE_HEADER }
        assertTrue("找不到修订记录表的表头：$REVISION_TABLE_HEADER", header >= 0)

        val block = mutableListOf<Pair<Int, String>>()
        var i = header
        while (i < lines.size && lines[i].trim().startsWith("|")) {
            block += (i + 1) to lines[i]
            i++
        }
        assertTrue(
            "修订记录表只剩 ${block.size} 行（下限 $revisionRowFloor）——这张表的行就是各轮的账，不该变短",
            block.size >= revisionRowFloor,
        )

        val complaints = mutableListOf<String>()
        val expected = splitCells(block[0].second).size
        for ((lineno, row) in block) {
            val cells = splitCells(row)
            if (cells.size != expected) {
                complaints += "第 $lineno 行：${cells.size} 格（应为 $expected）"
            }
        }
        assertEquals(
            "修订记录表里有未转义的竖线把行劈开了：\n" + complaints.joinToString("\n"),
            0,
            complaints.size,
        )

        val stale = mutableListOf<String>()
        var fixedHits = 0
        for ((broken, escaped) in revisionTableLegacy) {
            if (text.contains(broken)) stale += "已经修掉的旧串又回来了：$broken"
            if (text.contains(escaped)) fixedHits++ else stale += "转义后的形态不见了，登记无从核对：$escaped"
        }
        assertEquals(
            "修订记录表的旧串登记没过：\n" + stale.joinToString("\n"),
            0,
            stale.size,
        )
        assertEquals(
            "旧串登记表被删空了（下限 $revisionLegacyFloor 条）：${revisionTableLegacy.size}",
            revisionLegacyFloor,
            revisionTableLegacy.size,
        )
        assertTrue("登记在册的转义形态没有一条逐字出现在 $REVISION_TABLE_DOC 里", fixedHits >= 1)

        // 只钉「旧串不回来」会漏掉一种假绿：把整句删掉。所以再钉住那句共用证据的**行数下限**没变。
        // 这里刻意用下限而不是恰好值：第 98 轮刚给 1.40 写同一句时，恰好值（10）当场就被撞红了——
        // 而它的本意只是「修格时不许把这十行的说明句弄丢」。下限保住本意，也不会把「照旧写下去」判成错。
        val evidence = block.count { (_, row) -> row.contains(legacyEvidenceNeedle) }
        assertTrue(
            "修订记录表里那句共用证据「$legacyEvidenceNeedle」原本至少 $evidenceRowFloor 行各一处，现在只剩 $evidence 处",
            evidence >= evidenceRowFloor,
        )
    }

    /** 全局下限自检：文件数、表数、行数。读数就是 2026-11-25 第 98 轮的实测值，只作下限。 */
    private fun assertCensusFloors(docs: List<String>) {
        val tables = docs.sumOf { censusOf(it).tables }
        val rows = docs.sumOf { censusOf(it).rows }
        assertTrue("表数从 $tables 掉到了下限 $tableFloor 以下，扫描判据本身失效了", tables >= tableFloor)
        assertTrue("数据行数从 $rows 掉到了下限 $rowFloor 以下，扫描判据本身失效了", rows >= rowFloor)
    }

    /**
     * 8 条人造片段。第 3 条是**故意**的：GFM 不认反引号代码跨里的竖线，照样切——
     * `docs/07` 被劈开的那一族成因就在这一条。
     */
    private val splitCases: List<Triple<String, String, List<String>>> = listOf(
        Triple("未转义的竖线照切", "| 甲 | 乙 |", listOf("甲", "乙")),
        Triple("转义的竖线不切、反斜杠原样留着", """| 甲\|乙 | 丙 |""", listOf("""甲\|乙""", "丙")),
        Triple("反引号代码里的竖线照样切（GFM 的坑）", "| `a|b` | 丙 |", listOf("`a", "b`", "丙")),
        Triple("行首行尾的竖线不算格", "| 甲 |", listOf("甲")),
        Triple("行尾多写一个竖线就多出一格", "| 甲 | 乙 ||", listOf("甲", "乙", "")),
        Triple("缩进行首的竖线仍算表格行", "    | 甲 | 乙 |", listOf("甲", "乙")),
        Triple("格内的转义竖线原样保留", """| `*` `\|` 与反引号 |""", listOf("""`*` `\|` 与反引号""")),
        Triple("分隔行也走同一个切分器", "| --- | :---: | ---: |", listOf("---", ":---:", "---:")),
    )

    /**
     * 第 98 轮从修订记录表里修掉的旧串登记：`broken` 不许再出现，`escaped` 必须还在
     * （只钉「旧的不回来」会让「把整句删掉」也变绿，所以两头都要钉）。
     *
     * 第 3 条是 10 行共用的一句话（1.28 ~ 1.39 十行各一处，` | **已完成**：证据见` → `；**已完成**：证据见`），
     * 所以它的 `escaped` 形态在文件里命中 10 处——这个数由 [evidenceRowFloor] 作**下限**兜着（≥10 即过）。
     * 原来钉的是「恰好 10」：第 98 轮给 1.40 写同一句时当场被撞红，于是本轮把它放宽成下限。
     */
    private val revisionTableLegacy: List<Pair<String, String>> = listOf(
        "`|hit`" to """`\|hit`""",
        "testDebugUnitTest/*.xml | wc -l" to """testDebugUnitTest/*.xml \| wc -l""",
        " | **已完成**：证据见" to "；**已完成**：证据见",
    )

    private val legacyEvidenceNeedle = "；**已完成**：证据见"
    private val evidenceRowFloor = 10

    private val splitCaseFloor = 8
    private val docFileFloor = 15
    private val tableFloor = 1081
    private val rowFloor = 7365
    private val revisionRowFloor = 42
    private val revisionLegacyFloor = 3

    private companion object {
        val SCAN_DIRS = listOf("docs", "records")
        val SEPARATOR = Regex("""^\s*\|[\s:\-|]+\|\s*$""")
        const val REVISION_TABLE_DOC = "docs/07-风险管理计划.md"
        const val REVISION_TABLE_HEADER = "| 版本 | 日期 | 说明 |"
    }
}
