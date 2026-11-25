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
 * 第 99 轮把**仓库门面** `README.md` 也纳入同一套管辖：它此前是一个 15 字节的存根
 * （`# For-The-Elder` 加一个换行），98 轮下来一字节未动，而它是别人在 GitHub 上唯一会先看到的那一页。
 * 本轮把门面补成真文档（3 张表、19 个数据行），于是它必须过同一道表格形状关；同时补一条新用例
 * [everyPathMentionedInTheReadmeExists]，钉住「门面点名的路径真的在仓库里」。
 *
 * 第 100 轮补上**换行**这一格，并把一条记了多轮的旧账结清。本仓 `.gitattributes` 是 `* text=auto`、
 * 本机 `core.autocrlf=true`，于是**仓库存 LF、检出成 CRLF**：工作区里 `docs/00`（193 行）、
 * `docs/01`（625 行）、`docs/02`（457 行）是整份 CRLF，`records/02` 是 1,934 行里 1,730 行 CRLF，
 * `records/01` 是 11,252 行里**只有 16 行**（L6224~L6239，就是第 54 轮那一节）CRLF；而
 * **提交进仓库的 blob 一个 CR 都没有**（`git show HEAD:<路径> | tr -dc '\r' | wc -c` 对
 * docs/00 / docs/01 / docs/02 / records/01 / records/02 / records/04 / README 全为 0）。
 * ⇒ 旧账里那句「`records/01` 16 处 CRLF」记错了两次：它**不是仓库缺陷**（是检出与编辑产物），
 * 而且 `records/01` **不是整份 CRLF**（是混合）。本轮**刻意不去改工作区的行尾**——改了也不进仓库、
 * 下次检出就回来，把检出产物当缺陷去「修」才是假功夫。真正该立的是 git 覆盖不到的那一格：
 * `text=auto` 只把 `\r\n` 收成 `\n`，**裸 CR（后面不跟 LF）会原样进 blob**，于是有了第 6 条用例
 * [noDocContainsALoneCarriageReturn]；判据对平台免疫——检出只会「在 LF 前插 CR」、不会凭空造裸 CR，
 * 所以「工作区裸 CR 数 ≡ blob 裸 CR 数」。同时把普查下限抬到第 99 轮末态实测值（1099 / 7517）。
 *
 * ## 它做什么
 *
 * 把「一行怎么切成格」这件事抽成可单独喂的 [splitCells]，再用六条用例钉住：
 *
 * 1. [everyMarkdownTableRowHasExactlyAsManyCellsAsItsHeader]：全仓 16 份 Markdown 逐张表逐行，
 *    数据行的格数必须**等于**表头格数——判据是 `!=`，**多一格与少一格都算红**。
 * 2. [theCellSplitterSplitsRowsTheWayGitHubDoes]：8 条人造片段钉死切分器本身（未转义的 `|` 照切、
 *    `\|` 不切、**反引号代码里的 `|` 照样切**、首尾竖线不算格、行尾多一个竖线就多一格……）。
 *    第 1 条用例绿而这一条红，说明是「切分器把不该切的切了／该切的不切」，两者能分开。
 * 3. [theScannerActuallyFoundTheWholeRepository]：下限自检——文件数、表数、行数、每份至少一张表。
 *    扫描范围塌成一份空文件也会当场红，而不是「零处跨格 ⇒ 全绿」。
 * 4. [noUnescapedPipeAppearsInsideADocRevisionTable]：定点反例——`docs/07` 的修订记录表（42 行起）
 *    每行恰 3 格，外加一张「已修掉的旧串不许回来」登记（[revisionTableLegacy]）。
 * 5. [everyPathMentionedInTheReadmeExists]：门面向——`README.md` 里**反引号点名**的每一类路径
 *    （具体文件、目录、`docs/00` 这种族引用、`app/src/main` 这种无扩展名路径）逐个与仓库核对，
 *    并把四类各自的下限钉住，免得「一个都没认出来」也判绿。
 * 6. [noDocContainsALoneCarriageReturn]：**换行**向——16 份文档**一个裸 CR 都不许有**，
 *    并钉住「真读进了多少字节」（[totalByteFloor]），免得「一个字节都没读」也判绿。
 *
 * 下限纪律抄自 [DocLineRefGuardTest] 与 [DocSymbolClaimGuardTest]。
 *
 * ## 它不保证什么
 *
 * - **不代替渲染器**。这里实现的是「GFM 行内竖线切法」的一个**近似**：真正的 CommonMark 还要处理
 *   表格里的 HTML 实体、嵌套行内容器等情形，本切分器一概不认。它只覆盖本仓文档实际用到的那几种写法。
 * - **不管列宽、不管对齐**。格数对了但某一格写了个 4000 字的句子（`docs/07` 的 1.37 / 1.38 就是这个样子），
 *   本文档守卫一句话也不说。表格「好不好读」照旧只由人工兜着。
 * - **不管 CRLF，只管裸 CR**。工作区是 CRLF 还是 LF 由 `.gitattributes` 与检出配置决定，**按设计如此**，
 *   本文档守卫刻意不判它——判了就会「在 Windows 上红、在 Linux 上绿」，那种尺子比没有更坏。
 * - **只管 Markdown 正文里能核对的那部分**。`build/` 下的临时碎片（普查过程中生成的 `.md`，gitignored、
 *   约四百份）、本仓并不存在的 `.github/`、以及非 Markdown 的东西都不管；第 98 轮选择范围的依据是
 *   「这一族文档是交付物」，不是「其余文件里没有表格」。
 * - **门面只用正则读反引号**。`README.md` 里**没有**写进反引号的路径（比如 `local.properties` 那种
 *   由正文声明的例外，见 [README_PATH_EXCEPTIONS]）不参与核对；核对也只断言「这个路径存在」，
 *   不管它指向的东西内容对不对——内容对不对由 `docs` 那一族守卫管。
 * - **数量下限是当时的读数，且总有本轮自己的余量**。1099 / 7517 是 2026-11-25 第 100 轮开轮时的实测值
 *   （第 99 轮末态），只作**下限**；下限只能写在落盘**之前**，所以本轮自己新增的表和行会**再次**留下
 *   同量的余量——这是顺序带来的固有性质，如实写出来，不假装封死。
 *
 * ## 结论
 *
 * **R-10 仍然是橙色**：从第 98 轮起，「文档里那张表的形状对不对」在**这一族之内**有机器兜底；
 * 从第 99 轮起，仓库门面也在这一族之内（形状 + 路径两层）；从第 100 轮起，**换行**也在这一族之内
 * （裸 CR 一条）。这一族之外（列宽、可读性、CRLF/LF 之争、门面之外的非本仓 Markdown）照旧只能靠人工。
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

    /**
     * 去掉 CRLF 再切行，好让逐行判据在 Windows 与 Linux 上读同一份文本。
     *
     * 这里**不假定**哪些文件是 CRLF——那由 `.gitattributes`（`* text=auto`）与检出配置决定，
     * 同一份 blob 在不同机器上会检出成不同行尾。本机 2026-11-25 的实测是：`docs/00`（193 行）、
     * `docs/01`（625）、`docs/02`（457）整份 CRLF；`records/02`（1,934 行里 1,730 行）大半 CRLF；
     * `records/01`（11,251 行里**只有 16 行**）是混合；其余 11 份含 `README.md` 全是 LF。
     * 这些数只是**当时的检出产物、不是仓库事实**——提交进仓库的 blob 里一个 CR 都没有
     * （见 [noDocContainsALoneCarriageReturn] 与类注释里的取证命令）。
     */
    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun docText(rel: String): String = textCache.getOrPut(rel) {
        val file = File(repoRoot, rel)
        assertTrue("文档不存在：$rel", file.isFile)
        readNormalized(file)
    }

    /** 一份 Markdown 里扫到的表格块、数据行、以及跨格的行。 */
    private data class TableCensus(val tables: Int, val rows: Int, val issues: List<String>)

    /**
     * 扫描范围：`docs` 与 `records` 两个目录下的 `.md`，外加 [EXTRA_DOC_FILES] 点名的仓库根文件
     * （第 99 轮起含 `README.md`）。按路径排序，读起来与 `build/r98/census98.py` 的顺序一致。
     * 目录或点名文件缺失时当场红——扫描范围塌了比扫出问题更糟。
     */
    private fun scannedDocs(): List<String> {
        val fromDirs = SCAN_DIRS.flatMap { dir ->
            val dd = File(repoRoot, dir)
            assertTrue("扫描目录不存在：$dir（扫描范围可疑）", dd.isDirectory)
            (dd.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension == "md" }
                .map { "$dir/${it.name}" }
        }
        val extras = EXTRA_DOC_FILES.map { rel ->
            assertTrue("点名要扫的文件不存在：$rel（扫描范围可疑）", File(repoRoot, rel).isFile)
            rel
        }
        return (fromDirs + extras).sorted()
    }

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

    /**
     * 反引号点名的一类路径。四类分开核，是为了让「某一类路径一个都没认出来」也能当场红——
     * 第 98 轮的教训是「过滤器写歪了会静默变绿」。
     */
    private enum class MentionKind(val label: String) {
        FILE("具体文件"),
        DIR("目录"),
        FAMILY("`docs/00` 这种族引用"),
        BARE("`app/src/main` 这种无扩展名路径"),
    }

    private fun extensionOf(token: String): String {
        val tail = token.substringAfterLast('/')
        return if ('.' in tail) tail.substringAfterLast('.') else ""
    }

    /**
     * 把一个反引号 token 归类；认出「像仓库内路径」的才核，`compileSdk = 35` / `sdk.dir=<路径>`
     * 这类行内代码必须放过（返回 null）。
     */
    private fun classifyMention(token: String): MentionKind? {
        if (token.isEmpty() || token.any { it.isWhitespace() || it == '=' }) return null
        if (token.endsWith("/")) return if (REPO_RELATIVE.matches(token)) MentionKind.DIR else null
        if (FAMILY_PREFIX.matches(token)) return MentionKind.FAMILY
        if (extensionOf(token) in README_PATH_EXTENSIONS) {
            val inRepo = if ('/' in token) {
                README_PATH_PREFIXES.any { token.startsWith(it) }
            } else {
                !token.startsWith(".")
            }
            if (inRepo) return MentionKind.FILE
        }
        return if (REPO_RELATIVE.matches(token)) MentionKind.BARE else null
    }

    @Test
    fun everyPathMentionedInTheReadmeExists() {
        val text = docText(README_DOC)
        assertTrue(
            "README.md 只有 ${text.length} 字符（下限 $readmeCharFloor）——门面被掏空了？",
            text.length >= readmeCharFloor,
        )

        val tokens = README_INLINE_CODE.findAll(text).map { it.groupValues[1].trim() }.toList()
        val mentions = tokens.mapNotNull { token -> classifyMention(token)?.let { it to token } }.distinct()
        val counts = MentionKind.values().associateWith { kind -> mentions.count { it.first == kind } }
        assertTrue(
            "README 里认出来的路径只剩 ${mentions.size} 条（下限 $readmePathFloor）——核对范围塌了",
            mentions.size >= readmePathFloor,
        )
        for ((kind, count) in counts) {
            assertTrue(
                "README 里「${kind.label}」这一类只剩 $count 条（下限 $mentionKindFloor）——这一类没被真的核到",
                count >= mentionKindFloor,
            )
        }
        assertTrue("$README_DOC 必须在扫描范围内，表格形状也要管门面", README_DOC in scannedDocs())

        val complaints = mutableListOf<String>()
        var verifiedFiles = 0
        for ((kind, token) in mentions) {
            val target = File(repoRoot, token)
            when (kind) {
                MentionKind.FILE -> when {
                    token in README_PATH_EXCEPTIONS -> Unit
                    target.isFile -> verifiedFiles++
                    else -> complaints += "点名的文件不存在：$token"
                }
                MentionKind.DIR -> if (!target.isDirectory) complaints += "点名的目录不存在：$token"
                MentionKind.BARE -> if (!target.isFile && !target.isDirectory) {
                    complaints += "点名的路径既不是文件也不是目录：$token"
                }
                MentionKind.FAMILY -> {
                    val dir = File(repoRoot, token.substringBeforeLast('/'))
                    val prefix = token.substringAfterLast('/')
                    val hit = (dir.listFiles() ?: emptyArray()).count { it.isFile && it.name.startsWith(prefix) }
                    if (hit == 0) complaints += "族引用 $token 在 ${dir.name}/ 下对不上任何一个文件"
                }
            }
        }
        assertEquals("README 指向了不存在的东西：\n" + complaints.joinToString("\n"), 0, complaints.size)
        assertTrue(
            "被真核到存在的具体文件只剩 $verifiedFiles 个（下限 $readmeFileFloor）",
            verifiedFiles >= readmeFileFloor,
        )

        // 再堵一种假绿：像路径的反引号引用一条都没被认出来（比如正则被改歪了）。
        val suspicious = tokens.filter { token ->
            '/' in token && token.none { it.isWhitespace() } && '=' !in token &&
                !token.startsWith(".") && !token.startsWith("-") &&
                classifyMention(token) == null
        }.distinct().sorted()
        assertEquals(
            "README 里有像路径、又没被任何一类认出来的反引号引用：" +
                "漏认会让上面的核对悄悄变松\n" + suspicious.joinToString("\n"),
            0,
            suspicious.size,
        )
    }

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

    /**
     * 第 6 条：**换行**向。全仓 16 份文档**一个裸 CR 都不许有**（裸 CR = `\r` 后面不跟 `\n`）。
     *
     * 为什么只判裸 CR、不判 CRLF：`.gitattributes` 的 `* text=auto` 会在**提交时**把 `\r\n` 收成 `\n`，
     * 但**裸 CR 不是行尾**，git 原样存进 blob；于是「工作区里有裸 CR」等价于「仓库里有裸 CR」，
     * 判据对平台免疫（检出只在 LF 前插 CR，不会凭空造裸 CR）。反过来，「工作区是 CRLF」是**设计**，
     * 判它就会变成「在 Windows 上红、在 Linux 上绿」，那种尺子比没有更坏——所以这里刻意不判。
     *
     * 读的是**原始字节**（不是 [readNormalized] 之后的文本）：CRLF 规范化会把要找的东西抹掉。
     * 另加一条非空性下限（[docFileFloor] / [totalByteFloor]），免得「一份都没读进来」也判绿。
     */
    @Test
    fun noDocContainsALoneCarriageReturn() {
        val docs = scannedDocs()
        val complaints = mutableListOf<String>()
        var totalBytes = 0L
        for (rel in docs) {
            val bytes = File(repoRoot, rel).readBytes()
            totalBytes += bytes.size
            var lone = 0
            var i = 0
            while (i < bytes.size) {
                if (bytes[i] == CR_BYTE && (i + 1 >= bytes.size || bytes[i + 1] != LF_BYTE)) lone++
                i++
            }
            if (lone > 0) complaints += "$rel 里有 $lone 个裸 CR（\\r 后面不跟 \\n）"
        }
        assertEquals(
            "文档里出现了裸 CR（git 的 text=auto 会把它原样提交进仓库）：\n" + complaints.joinToString("\n"),
            0,
            complaints.size,
        )
        assertTrue("扫描只读到 ${docs.size} 份文档，下限是 $docFileFloor", docs.size >= docFileFloor)
        assertTrue(
            "扫描只读到 $totalBytes 字节，下限是 $totalByteFloor——判据几乎没读到东西",
            totalBytes >= totalByteFloor,
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
    private val docFileFloor = 16

    /**
     * 第 100 轮把这几个数抬到**第 99 轮落盘后的末态真值**（原来分别是 1092 / 7458，抄自第 98 轮）。
     * 抬它们是为了封掉第 99 轮留下的一条缝：那次新增门面与几节文档后，末态已是 1099 表 / 7517 行，
     * 而旧下限还停在 1092 / 7458——于是「悄悄删掉 7 张表、59 行」不会报红。
     * 注意：下限只能写在**本轮落盘之前**，所以本轮自己新增的表和行会**再次**留下同量余量——
     * 这是顺序带来的固有性质，如实写出来，不假装封死。
     */
    private val tableFloor = 1099
    private val rowFloor = 7517

    /** 16 份文档的**原始字节**总量下限（2026-11-25 第 100 轮开轮实测 7,702,389）。 */
    private val totalByteFloor = 7_700_000L

    private val revisionRowFloor = 42
    private val revisionLegacyFloor = 3

    /**
     * 第 99 轮门面读数的下限：README 的字符数、认出来的路径条数、被核到**存在**的具体文件数、
     * 以及四类里最少那一类的条数。四个数都是 2026-11-25 第 99 轮的实测值（依次 5380 / 29 / 18 / 2），
     * 只作下限——文档继续写下去只会往上走。
     */
    private val readmeCharFloor = 2000
    private val readmePathFloor = 26
    private val readmeFileFloor = 14
    private val mentionKindFloor = 2

    private companion object {
        val SCAN_DIRS = listOf("docs", "records")

        /** 目录扫不到、但要一并管起来的仓库根文件（第 99 轮起：门面 `README.md`）。 */
        val EXTRA_DOC_FILES = listOf("README.md")
        val SEPARATOR = Regex("""^\s*\|[\s:\-|]+\|\s*$""")

        /** 裸 CR 判据用的两个字节：`\r` 与 `\n`。 */
        const val CR_BYTE: Byte = 0x0D
        const val LF_BYTE: Byte = 0x0A
        const val REVISION_TABLE_DOC = "docs/07-风险管理计划.md"
        const val REVISION_TABLE_HEADER = "| 版本 | 日期 | 说明 |"
        const val README_DOC = "README.md"

        /** README 里的行内代码：一对反引号，不跨行。 */
        val README_INLINE_CODE = Regex("`([^`\n]+)`")

        /** 算「具体文件」的扩展名白名单——进来核存在的必须是仓库里真有的那一族。 */
        val README_PATH_EXTENSIONS = setOf("md", "kt", "kts", "xml", "properties", "json", "gradle")
        val README_PATH_PREFIXES = listOf("docs/", "records/", "app/")

        /** `docs/00` / `records/03` 这种「某一族文档」的引用：那个目录下必须有至少一个以它开头的文件。 */
        val FAMILY_PREFIX = Regex("""^(docs|records)/\d{2}$""")

        /** 仓库相对路径的粗判：以三个目录名之一开头、不含空白。 */
        val REPO_RELATIVE = Regex("""^(docs|records|app)/[^\s]*$""")

        /**
         * 点名了、但**按设计**不在仓库里的文件。`local.properties` 是 README 第 35 行自己写明的：
         * 「不入库，本地没有时先写上 `sdk.dir=<…>`」——它是本机配置，不是交付物，所以不核存在性。
         */
        val README_PATH_EXCEPTIONS = setOf("local.properties")
    }
}
