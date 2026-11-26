package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「Markdown 标题的层级结构」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 到第 108 轮为止，文档这一族已经有四把尺子：[DocLineRefGuardTest]（源码行号）、
 * [DocCrossRefGuardTest]（文档之间的短形引用）、[DocTableShapeGuardTest]（表格的**形状**）、
 * [DocScreenCopyGuardTest]（屏幕上的字）。它们读的都是**字与格**，没有一把读过**标题的层级**。
 *
 * 而本仓的标题层级是**有立法意义的**：`records/01` 用 `## 第 N 轮：…` 划轮次、`### NNN.x` 划条目，
 * `records/03` 用 `## ADR-NNN：…` 划决策，`docs/0X` 用 `## 大节` / `### 小节` 划章节。
 * 层级一旦跳级（`##` 直接下到 `####`），GitHub 的目录树（TOC）会把被跳过的那些层**空着**——
 * 读起来仍然通顺（每个标题自己都写对了），但整份文档在侧栏里塌成一张对不上号的清单。
 * 这类错误**没有一行文字是错的**，靠人读一个字也读不出来。
 *
 * ## 它做什么
 *
 * 把「一行算不算标题、算几级、在不在围栏里」抽成一个 [census]，再用七条用例钉住：
 *
 * 1. [everyManagedFileIsReadAndHasExactlyOneTopHeading]：16 份受管文件一份不少都在、没有重复路径、
 *    每份都不是空文件，而且每份**恰好一个 h1**、**第一个真标题必须是那个 h1**。
 *    少读一份文件就等于把它整份的层级豁免掉——那是这一族最想防的假绿。
 * 2. [noHeadingLevelJumpWithoutSkippingFences]：**去围栏之后**，16 份合起来一处跳级都不许有
 *    （判据是 `b.level > a.level + 1`），并用标题总数下限挡住「零跳级是因为零标题」。
 * 3. [fencedHashLinesAreNotCountedAsHeadings]：**钉陷阱**——围栏里的 `#` 起头行逐份都要落进
 *    `fenced` 那一档，`naive == real + fenced` 且两者行号不相交；围栏内 `#` 的总数有下限。
 * 4. [theFenceTrackerIsActuallyDoingWork]：**尺子自检**——那把**朴素**尺子（不认围栏）在本语料上
 *    必须报出 ≥8 处假跳级、≥2 份文件，而真尺子必须报 0 处。两把尺子的读数**必须不同**：
 *    哪一天它们又变得一样了，说明围栏跟踪已经不起作用、这一族守卫整片退化成朴素尺子。
 * 5. [theHeadingScannerReadsSnippetsTheWayCommonMarkDoes]：9 条人造片段钉死 [census] 本身
 *    （跳级怎么算、围栏怎么开怎么合、`#` 后面的空格、1~3 空格缩进、4 空格缩进、`>` 引用块、
 *    `~~~` 与 ```` ``` ```` 混用时的**记号配对**）。第 2 条绿而这一条红，说明是尺子的问题，不是文档的问题。
 * 6. [roundHeadingsGovernTheirOwnEntries]：**轮次的归属与编号**——`records/01` 里每个 `### NNN.x`
 *    条目都必须落在一个**编号相同**的 `## 第 NNN 轮…` 之下，且轮次标题的编号 1..最大**一个不缺**。
 *    这条是**第 114 轮补的**：第 113 轮的轮次标题漏写，前面五条一条也读不出来（详见下面那段）。
 * 7. [roundHeadingsAreRegisteredAndContiguousWhereverTheyAppear]：**轮次标题的全仓覆盖、连号、
 *    杂项 h2 与正文体量**——16 份受管文件里「含 `## 第 NNN 轮` 标题」的集合必须与
 *    [roundHeadingRegistry] **相等**；每个登记文件里轮次编号从它自己的最小号连到最大号不漏号；
 *    第一个轮次标题之后不得再出现不是轮次标题的 h2；轮次正文逐块有下限、每份的合计也有下限。
 *    这条是**第 115 轮补的**：第 6 条只认 `records/01` 一份，`records/CHANGELOG.md` 里那 51 个
 *    轮次标题当时**一个字的判据都没有**（详见下面第 115 轮那段）。
 *
 * ## 这一轮的由来（第 109 轮的真事件）
 *
 * 第 108 轮给短形引用补判据时，普查脚本（`build/r109/census109d.py`）顺手按**朴素**口径
 * （不认围栏，凡 `^#{1,6}\s` 一律算标题）数了一遍层级，当场报出 **8 处跳级**，全在
 * `docs/08`（L65→L73、L78→L84）与 `records/04`（L244→L247、L385→L391、L481→L488、
 * L635→L638、L1185→L1252、L2320→L2343）。逐处读过原文之后，**8 处的成因一模一样**：
 * 那些 `#` 起头的行**全在围栏里**，是 shell / Gradle 的注释（`# 这是注释`），
 * CommonMark 一个字都不会把它当标题。
 *
 * 换成**认围栏**的口径（`build/r109/census109h.py`，逐份 `real / fenced / naive`）之后，
 * 16 份文件 **16 份 `jReal = 0`**，跳级一处不剩；而那 8 处假跳级原样躺在 `naive` 那一列里。
 * ⇒ 本轮**没有回改一个字的文档**：标题结构**当前没有真缺陷**，真风险是「**那把会误报的尺子**」。
 * 所以本轮的交付物是「钉住不变量 + 钉住陷阱」，不是「修文档」——这与第 107、108 轮「先让尺子红、
 * 再修文档」的顺序**不同**，如实写出来：本轮是**普查下来无缺陷**，于是只加尺子。
 *
 * ## 第 115 轮为什么又补一条（同一族缺陷的第二次露面）
 *
 * 第 114 轮补完第 6 条，留下三笔账，其中一笔是：**第 6 条只认 `records/01` 一份**。
 * 第 115 轮先做普查（`build/r115/verify115.py`）：16 份受管文件里出 `## 第 NNN 轮` 标题的
 * **只有两份**——`records/01`（114 个，1..114 连号）与 `records/CHANGELOG.md`（51 个，64..114
 * 连号）；`records/02` / `03` / `04` 用的是 `## ADR-NNN` / `## 第X节 ·` 那套体例，**一个轮次标题
 * 都没有**，所以对它们「扩第 6 条」是做不到的，如实记下。
 *
 * ⇒ 于是本轮的补法不是「再挑一份文件管起来」，而是**把「谁出轮次标题」这件事本身变成判据**：
 * 出一份登记表 [roundHeadingRegistry]，再要「受管文件里实际出现轮次标题的集合」与它**相等**。
 * 这样将来**任何**一份受管文件长出轮次标题（或轮次标题被整体改名/删净），管辖范围都不会**静默**
 * 变化——那正是第 113 轮那处「文档不在任务输入里 ⇒ 守卫静默不跑」的同一类缺陷。第 7 条是在第 6 条
 * 之外**另起一条**：第 6 条那条「归属」（`### NNN.x` 挂在谁底下）仍只在 `records/01` 上有，
 * 因为只有它按 `### NNN.x` 划条目的体例。
 *
 * ## 它不保证什么
 *
 * - **不管标题的编号与命名体例**。`### 8.70` 后面接的是 8.71 还是 9.1，别的 `records/0X` 里
 *   有没有漏号，这条用例一个字也不说——那是 [DocHeadingStructureGuardTest] 管不着的一族，
 *   由 `build/r109/census109.py` / `census109c.py` 那样的普查脚本人工跑。**例外是第 6、7 条**：
 *   第 6 条管 `records/01` 的轮次编号连续与归属，第 7 条管「哪两份文件出轮次标题」以及它们各自的
 *   连号、杂项 h2 与正文体量；两条都仍不管 `docs/0X` 的小节编号（`### 8.70` 后接的是 8.71
 *   还是 9.1），也不管 `records/CHANGELOG.md` 里**没有** `### NNN.x` 条目这件事。
 * - **不管「标题下面有没有正文」**。`## 甲` 紧跟 `## 乙`（空节）不会红。本仓正文区里没有空节，
 *   但那是**当时**的事实，不是这条用例判出来的。
 * - **不代替渲染器**。这里实现的是「ATX 标题 + 围栏」的一个**近似**：setext 标题（下划线式
 *   `===` / `---`）、HTML 块 `<h1>`、缩进式代码块（4 空格）里的 `#`、列表项里的标题、
 *   引用块里的 `> # 甲`，一概不认。[theHeadingScannerReadsSnippetsTheWayCommonMarkDoes] 第 8 条
 *   把 `> # 甲` 如实钉成「不算标题」这个边界，而不是假装它是对的。
 * - **围栏只认「第一行的前三字符」**。CommonMark 说围栏的闭合记号必须与开启记号**同类且不短于**
 *   开启记号（```` ``` ```` 开、` ````` ` 可以合）。这里与普查脚本同口径，只比**前三字符是否相同**
 *   （```` ``` ```` 对 ```` ```` ````、`~~~` 对 `~~~`），比规范**宽**。本语料里没有更长的围栏，
 *   如实记下这条残余边界。
 * - **只管 16 份受管文件**。与 [DocTableShapeGuardTest] 同范围（`docs/` 十份 + `records/` 五份 +
 *   `README.md`），别的 Markdown（`build/` 下的碎片、将来可能有的 `.github/`）不在管辖内。
 * - **只管层级跳级，不管「层用错」**。把 `## ADR-087` 写成 `### ADR-087`（层级合法但体例不对）
 *   不会红——那是体例，体例没有机器判据，只能靠人。
 * - **数量下限是第 109 轮的读数**。落盘会把 `real` 那个数顶高（本轮自己的条目），
 *   所以下限写实测值、余量来自本轮自己的落盘，与 [DocTableShapeGuardTest] 的下限纪律一致。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。第 109 轮只是把「标题的层级会不会跳」这句从**完全没有兜底**
 * 变成**有兜底**（16 份受管文件之内、只判两层：h1 唯一与不跳级），并把「围栏里的 `#` 不是标题」
 * 这条陷阱钉成了机器判据；风险等级不因此下降。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。下限自检的纪律抄自 [DocLineRefGuardTest] 与 [DocCrossRefGuardTest]。
 */
class DocHeadingStructureGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 扫描范围与 [DocTableShapeGuardTest] 逐字相同：两个目录加拉在仓库根的那份。 */
    private val docDirs = listOf("docs", "records")
    private val extraDocFiles = listOf("README.md")

    /**
     * ATX 标题：0~3 个空格缩进、1~6 个 `#`、**至少要一个空白**、然后至少一个非空白字符。
     *
     * 三个约束都是刻意的：
     * - `{0,3}` 空格——CommonMark 允许标题缩进 0~3 格，4 格起就是缩进式**代码块**（第 5 条片段钉住）。
     * - `\s+` 必填——`#甲` 不是标题，是正文（第 6 条片段钉住）。
     * - `(\S.*)` 非空白起头——`#` 后面只剩空白不算标题。
     */
    private val heading = Regex("""^ {0,3}(#{1,6})\s+(\S.*)$""")

    /**
     * 围栏：行首（**不允许缩进**）三个及以上反引号、或三个及以上波浪号。
     * 与 `build/r109/census109h.py` 的 `^(```+|~~~+)` 逐字同口径。
     */
    private val fence = Regex("""^(```+|~~~+)""")

    /**
     * 受管文件数下限（第 109 轮实测 16：`docs/` 十份 + `records/` 五份 + `README.md`）。
     * 少读一份就等于把那份文件的层级整片豁免掉。
     */
    private val managedFileFloor = 16

    /**
     * 真标题**总数**下限（第 109 轮落盘前实测 2707）。落盘会把这个数顶高，
     * 所以这里取的是落盘前的值、余量来自本轮自己的条目——见类注释。
     */
    private val headingFloor = 2707

    /** 每份受管文件的 h1 个数（一份文档一个总标题，16 份就是 16 个）。 */
    private val h1PerFile = 1

    /**
     * 围栏内 `#` 起头行的**总数**下限（第 109 轮实测 42：`docs/04` 3、`docs/05` 3、`docs/06` 9、
     * `docs/08` 4、`records/04` 18、`README.md` 5）。
     */
    private val fencedHashFloor = 42

    /** 含围栏 `#` 的**文件数**下限（第 109 轮实测 6 份）。 */
    private val fencedFileFloor = 6

    /** 朴素尺子报出的**假跳级**处数下限（第 109 轮实测 8：`docs/08` 2 处、`records/04` 6 处）。 */
    private val naiveJumpFloor = 8

    /** 朴素尺子报出假跳级的**文件数**下限（第 109 轮实测 2 份）。 */
    private val naiveJumpFileFloor = 2

    /** 尺子自检用的人造片段条数（第 109 轮共 9 条）。 */
    private val snippetFloor = 9

    /**
     * `## 第 NNN 轮…` 的轮次标题正则（第 6 条用例用）。**只认编号、不要求冒号**——
     * `records/01` 的第 64／65／66 轮那三个标题后面就没有冒号，要求冒号会把它们漏掉、
     * 连带把它们下面的条目判成「找不到归属的轮次」。
     */
    private val roundHeading = Regex("""^第 (\d+) 轮""")

    /** `### NNN.x …` 的轮次内条目正则（第 6 条用例用）。 */
    private val roundEntry = Regex("""^(\d+)\.\d+[\s：:]""")

    /**
     * `records/01` 的轮次标题数下限（第 114 轮落盘前实测 113——第 113 轮那个标题是
     * 第 114 轮补上的，补上之后才数到 113）。落盘会把这个数顶高，余量来自本轮自己的条目。
     */
    private val roundHeadingFloor = 113

    /** `records/01` 的 `### NNN.x` 条目数下限（第 114 轮落盘前实测 543）。 */
    private val roundEntryFloor = 543

    /**
     * 一个「出轮次标题的受管文件」的登记项（第 7 条用例用）。
     *
     * 两个下限都取**登记当时的实测值**：落盘只会把它们顶高，与 [DocTableShapeGuardTest] 的
     * 下限纪律一致。
     */
    private data class RoundHeadingFile(
        /** 相对仓库根的路径。 */
        val rel: String,
        /** 轮次标题的**个数**下限。 */
        val totalFloor: Int,
        /** 轮次正文（各块内非空行）的**合计**下限。 */
        val bodyTotalFloor: Int,
    )

    /**
     * 出轮次标题的受管文件登记表（第 115 轮普查实测，`build/r115/verify115.py`）。
     *
     * `records/01-迭代记录.md`：114 个标题（1..114 连号）、轮次正文合计 9244 行非空。
     * `records/CHANGELOG.md`：51 个标题（64..114 连号）、轮次正文合计 4943 行非空。
     * 16 份受管文件里出轮次标题的**只有这两份**；`records/02` / `03` / `04` 用的是
     * `## ADR-NNN` / `## 第X节 ·` 那套体例，一个轮次标题都没有。
     *
     * **这份表是手写的**：将来哪份受管文件长出 `## 第 NNN 轮` 却不登记，第 7 条会红着要人来登记
     * ——判据能报红，但它不会替人做决定（第 115 轮如实记的账）。
     */
    private val roundHeadingRegistry = listOf(
        RoundHeadingFile("records/01-迭代记录.md", totalFloor = 114, bodyTotalFloor = 9244),
        RoundHeadingFile("records/CHANGELOG.md", totalFloor = 51, bodyTotalFloor = 4943),
    )

    /**
     * 轮次正文**逐块**的非空行下限（第 7 条用例用），只挡空块与近空块。
     *
     * 第 115 轮实测的逐块最小值是 11（`records/01`）与 8（`records/CHANGELOG.md`），但这里刻意
     * 只取 3：8 是那一次写作的**偶然**，不是立法下限；贴着实测值立法，将来某一轮写一条**合理的
     * 短条目**就会误报——第 114 轮在「逐份闭合行数下限」上已经踩过这个两难。逐块下限只管
     * 「块是空的」；挡「成片丢」的是登记表里每份的 [RoundHeadingFile.bodyTotalFloor]。
     */
    private val perRoundBodyFloor = 3

    /** 一次普查里的**一个**标题。 */
    private data class Heading(
        /** 行号（1 起）。 */
        val line: Int,
        /** 层级（1~6）。 */
        val level: Int,
        /** `#` 与空白之后的那段文字，末尾的空行符已由 [readNormalized] 收走。 */
        val text: String,
    )

    /**
     * 一份文件的三档读数。
     *
     * `naive` 不是「另一种实现」，是**第 109 轮那场普查里先跑出来的那把坏尺子**——
     * 刻意保留在同一个 data class 里，好让第 4 条用例能正面断言「两把尺子的读数确实不同」。
     */
    private data class HeadingCensus(
        /** 围栏**之外**的标题，按行号升序。这是真读数。 */
        val real: List<Heading>,
        /** 围栏**之内**的 `#` 起头行，按行号升序。它们是注释，不是标题。 */
        val fenced: List<Heading>,
    ) {
        /** 不认围栏的朴素读数——把所有 `#` 起头行一律当标题，按行号升序。 */
        val naive: List<Heading> get() = (real + fenced).sortedBy { it.line }

        /** 真读数里的跳级处（`b.level > a.level + 1`）。 */
        val realJumps: List<Pair<Heading, Heading>> get() = jumps(real)

        /** 朴素读数里的跳级处。 */
        val naiveJumps: List<Pair<Heading, Heading>> get() = jumps(naive)

        /**
         * 相邻两级之间跨层的那些处。判据是 `b.level > a.level + 1`：**上升**才算跳级，
         * 下降（`####` 之后回到 `##`）是正常的收束，不算。与 `build/r109/census109h.py`
         * 的 `jumps()` 逐条同口径。
         */
        private fun jumps(seq: List<Heading>): List<Pair<Heading, Heading>> =
            seq.zipWithNext().filter { (a, b) -> b.level > a.level + 1 }
    }

    private val censusCache = mutableMapOf<String, HeadingCensus>()

    /**
     * 16 份受管文件的相对路径清单（与 [DocTableShapeGuardTest.scannedDocs] 同口径）。
     * 目录或点名文件缺失时当场红——扫描范围塌了比扫出问题更糟。
     */
    private fun scannedDocs(): List<String> {
        val fromDirs = docDirs.flatMap { dir ->
            val dd = File(repoRoot, dir)
            assertTrue("扫描目录不存在：$dir（扫描范围可疑）", dd.isDirectory)
            (dd.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension == "md" }
                .map { "$dir/${it.name}" }
        }
        val extras = extraDocFiles.map { rel ->
            assertTrue("点名要扫的文件不存在：$rel（扫描范围可疑）", File(repoRoot, rel).isFile)
            rel
        }
        return (fromDirs + extras).sorted()
    }

    /**
     * 取一份文件的正文。CRLF 一律收成 LF：行数不受影响（两种行尾都是换行符前的那一个字节），
     * 但标题文字里不再残留尾部 `\r`，报错时能读。与 `build/r109/census109h.py` 的**行号口径**一致。
     */
    private fun readNormalized(rel: String): String =
        File(repoRoot, rel).readText().replace("\r\n", "\n")

    private fun censusOf(rel: String): HeadingCensus =
        censusCache.getOrPut(rel) { census(readNormalized(rel)) }

    /**
     * 逐行扫一份正文，把 `#` 起头行分成**围栏之外**（[HeadingCensus.real]）与**围栏之内**
     * （[HeadingCensus.fenced]）两档。围栏跟踪是**记号配对**的：只有前三字符与开启记号相同的
     * 那一行才算闭合，```` ``` ```` 与 `~~~` 不互相闭合——与 `build/r109/census109h.py` 逐条对齐。
     *
     * 围栏行自己**先判先 continue**：围栏那一行不会同时是标题（` ``` ` 起头的行本来就过不了
     * [heading] 的 `#` 那一关，这里的顺序只是让意图明确）。
     */
    private fun census(text: String): HeadingCensus {
        val real = mutableListOf<Heading>()
        val fenced = mutableListOf<Heading>()
        var inFence = false
        var mark = ""
        for ((offset, line) in text.split("\n").withIndex()) {
            val mf = fence.find(line)
            if (mf != null) {
                val token = if (mf.groupValues[1].startsWith("`")) "```" else "~~~"
                if (!inFence) {
                    inFence = true
                    mark = token
                } else if (token == mark) {
                    inFence = false
                    mark = ""
                }
                continue
            }
            val m = heading.find(line) ?: continue
            val h = Heading(offset + 1, m.groupValues[1].length, m.groupValues[2])
            if (inFence) fenced += h else real += h
        }
        return HeadingCensus(real, fenced)
    }

    /** 把一串跳级处印成多行报文（含两侧行号、层级、标题前 60 字）。 */
    private fun jumpReport(where: String, pairs: List<Pair<Heading, Heading>>): String =
        pairs.joinToString("\n") { (a, b) ->
            "${where} 第 ${a.line} 行(h${a.level}) → 第 ${b.line} 行(h${b.level})，" +
                "上升 ${b.level - a.level} 级：「${a.text.take(60)}」⇒「${b.text.take(60)}」"
        }

    /**
     * 第 1 条：16 份受管文件一份不少、无重复路径、都不是空文件，且每份**恰好一个 h1**、
     * **第一个真标题必须是那个 h1**。
     *
     * 这是**所有别的判据的地基**：一份文件被删、被改名成别的后缀，它整份的层级就掉出管辖，
     * 不是报红而是静默不扫。h1 那两条则是「一份文档一个总标题」这条体例的机器落点——
     * 第 2 条只判「相邻两级不跳」，把总标题写成 `##`（出现两个 h1、或干脆没有 h1）它读不出来。
     */
    @Test
    fun everyManagedFileIsReadAndHasExactlyOneTopHeading() {
        val docs = scannedDocs()
        assertTrue(
            "受管文件只数到 ${docs.size} 份，低于下限 $managedFileFloor" +
                "（`docs/` 十份 + `records/` 五份 + `README.md`）：文件被删、被移走、" +
                "或被改成了别的后缀，它整份的层级就静默掉出管辖",
            docs.size >= managedFileFloor,
        )
        assertTrue(
            "受管清单里有重复路径：${docs.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}" +
                "——同一份文件被数两次，分母就不对了",
            docs.distinct().size == docs.size,
        )

        val empty = docs.filter { readNormalized(it).isBlank() }
        assertTrue(
            "有 ${empty.size} 份受管文件是空的（或只有空白）：$empty——空文件没有标题，" +
                "这一族判据对它一句话也说不出来",
            empty.isEmpty(),
        )

        val badH1 = mutableListOf<String>()
        val badFirst = mutableListOf<String>()
        var h1Total = 0
        var headingTotal = 0
        for (rel in docs) {
            val c = censusOf(rel)
            val h1 = c.real.count { it.level == 1 }
            h1Total += h1
            headingTotal += c.real.size
            if (h1 != h1PerFile) {
                badH1 += "$rel：$h1 个 h1（应恰好 $h1PerFile 个）；" +
                    "真标题 ${c.real.size} 个，" +
                    "h1 在 " + c.real.filter { it.level == 1 }.joinToString("、") { "第 ${it.line} 行" }
            }
            val first = c.real.firstOrNull()
            if (first == null || first.level != 1) {
                badFirst += "$rel：第一个真标题是${first?.let { "第 ${it.line} 行的 h${it.level}「${it.text.take(60)}」" } ?: "不存在"}"
            }
        }
        assertTrue(
            "有 ${badH1.size} 份受管文件的 h1 个数不对：\n" + badH1.joinToString("\n") { "  $it" } +
                "\n一份文档一个总标题：h1 多了说明有人把最外层标题重复了一遍（或把总标题降级又被别处补了 h1），" +
                "少了说明总标题被降成了 `##`——第 2 条只判相邻两级不跳，这两种它都读不出来",
            badH1.isEmpty(),
        )
        assertTrue(
            "有 ${badFirst.size} 份受管文件的第一个真标题不是 h1：\n" + badFirst.joinToString("\n") { "  $it" } +
                "\n正文最上面那一行该是 h1；排在它前面的任何标题都会让侧栏目录在根部先长出一层",
            badFirst.isEmpty(),
        )
        assertTrue(
            "16 份受管文件里只数到 $h1Total 个 h1（应等于文件数 ${docs.size}：一份一个）；" +
                "这个数与每份那两条是同一件事的两个面",
            h1Total == docs.size,
        )
        assertTrue(
            "16 份受管文件里只数到 $headingTotal 个真标题（围栏之外），低于下限 $headingFloor" +
                "（第 109 轮落盘前实测 2707）：[census] 写歪、扫描目录被换走、或受管文件被删空，" +
                "上面那几条与第 2 条都会退化成「零标题 ⇒ 一律通过」——不许这么绿",
            headingTotal >= headingFloor,
        )
    }

    /**
     * 第 2 条：**去围栏之后**，16 份文件合起来一处跳级都不许有（`b.level > a.level + 1`）。
     *
     * 跳级的代价不在源文本上（每个标题自己都写对了），而在 GitHub 侧栏：被跳过的那一层会**空着**，
     * 整份文档在目录里塌成一张对不上号的清单。这是本族文档体例的硬约束。
     *
     * 与第 1 条分工：第 1 条盯**端点**（顶层必须唯一且在最前），这一条盯**相邻两级之间的关系**。
     * 两条都用 [headingFloor] 那一道覆盖面下限护着，免得「零跳级」是因为「零标题」。
     */
    @Test
    fun noHeadingLevelJumpWithoutSkippingFences() {
        val docs = scannedDocs()
        val offenders = mutableListOf<String>()
        var headingTotal = 0
        for (rel in docs) {
            val c = censusOf(rel)
            headingTotal += c.real.size
            offenders += c.realJumps.map { jumpReport(rel, listOf(it)) }
        }
        assertTrue(
            "去围栏之后，16 份受管文件里数到 $headingTotal 个真标题，低于下限 $headingFloor" +
                "（第 109 轮落盘前实测 2707）：这一条判的是「跳级处为零」，" +
                "零标题也会让它变绿，所以要正面钉住分母",
            headingTotal >= headingFloor,
        )
        assertTrue(
            "去围栏之后仍有 ${offenders.size} 处标题跳级（相邻两级之间不许跨层，`b > a + 1` 即红）：\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n被跳过的那一层会在 GitHub 侧栏里空着，整份文档的目录对不上号——" +
                "要么把子标题降一级接上、要么在被跳过的那一层补一个真标题。" +
                "注意：`docs/08` 与 `records/04` 里那些围栏中的 shell 注释（`# 说明`）**不是标题**，" +
                "本用例不认它们；若报出来的位置正好在围栏里，那是 [census] 的围栏跟踪坏了，看第 4 条",
            offenders.isEmpty(),
        )
    }

    /**
     * 第 3 条：**钉陷阱**——围栏里的 `#` 起头行逐份都要落进 `fenced` 那一档，不许混进 `real`。
     *
     * 第 109 轮那场普查最先跑出来的就是这一格坏了：朴素口径在 `docs/08` 与 `records/04` 里
     * 报出 8 处跳级，逐处读原文发现**全是围栏里的注释**（`# 这是注释`）。所以这一条反过来正面要求：
     * 每份文件 `naive == real + fenced` 且两档行号不相交，同时围栏内的 `#` 要**足够多**——
     * 围栏跟踪被删掉、或围栏正则写歪，[fencedHashFloor] 与 [fencedFileFloor] 两道下限会先红。
     */
    @Test
    fun fencedHashLinesAreNotCountedAsHeadings() {
        val docs = scannedDocs()
        var realTotal = 0
        var fencedTotal = 0
        var filesWithFenced = 0
        val broken = mutableListOf<String>()
        for (rel in docs) {
            val c = censusOf(rel)
            realTotal += c.real.size
            fencedTotal += c.fenced.size
            if (c.fenced.isNotEmpty()) filesWithFenced++
            if (c.naive.size != c.real.size + c.fenced.size) {
                broken += "$rel：naive ${c.naive.size} ≠ real ${c.real.size} + fenced ${c.fenced.size}"
            }
            val overlap = c.real.map { it.line }.toSet() intersect c.fenced.map { it.line }.toSet()
            if (overlap.isNotEmpty()) broken += "$rel：有些行号同时出现在 real 与 fenced 两档里：$overlap"
        }
        assertTrue(
            "三档读数自相矛盾（${broken.size} 份）：\n" + broken.joinToString("\n") { "  $it" } +
                "\nnaive 必须**恰好**是真读数与围栏档的并集（按行号排序），两档的行号不许相交——" +
                "这是 [census] 的内部一致性，也是第 4 条那把朴素尺子的前提",
            broken.isEmpty(),
        )
        assertTrue(
            "16 份受管文件里只数到 $fencedTotal 行「围栏之内的 `#` 起头行」" +
                "（真标题 $realTotal 行，两者之和应等于朴素读数），低于下限 $fencedHashFloor" +
                "（第 109 轮实测 42：`docs/04` 3、`docs/05` 3、`docs/06` 9、`docs/08` 4、" +
                "`records/04` 18、`README.md` 5）：围栏跟踪失效时这些行会混进真读数，" +
                "下限一红就说明包围栏这一格整个没起作用",
            fencedTotal >= fencedHashFloor,
        )
        assertTrue(
            "含围栏 `#` 的受管文件只有 $filesWithFenced 份，低于下限 $fencedFileFloor（第 109 轮实测 6 份）" +
                "：这些 `#` 是 shell / Gradle 的注释，与标题只差「在不在围栏里」这一件事——" +
                "它们所在的文件正是判别力唯一的来源",
            filesWithFenced >= fencedFileFloor,
        )
    }

    /**
     * 第 4 条：**尺子自检**——那把**朴素**尺子（不认围栏）必须仍然报出假跳级，且与真尺子读数不同。
     *
     * 这一条不判文档的对错，只判**这把尺子还有没有分辨力**。本语料里围栏内的 `#` 全部是注释，
     * 它们顶出来的假跳级是「围栏跟踪确实在工作」的**唯一证据**：哪天有人把 `docs/08` / `records/04`
     * 的围栏注释整片清掉、或把那两份文件的层级重排得恰好让朴素口径也不再报警，这条用例就会红——
     * 那不是文档坏了，是**这一族守卫失去判别力**了，必须重新找一份带围栏 `#` 的文件来当基准。
     *
     * 与第 3 条分工：第 3 条正面钉住「围栏档里有 42 行」，这一条钉住「那 42 行真的会骗到一把坏尺子」。
     */
    @Test
    fun theFenceTrackerIsActuallyDoingWork() {
        val docs = scannedDocs()
        val naiveOffenders = mutableListOf<String>()
        val realOffenders = mutableListOf<String>()
        var naiveJumpTotal = 0
        val filesWithNaiveJumps = mutableSetOf<String>()
        for (rel in docs) {
            val c = censusOf(rel)
            naiveJumpTotal += c.naiveJumps.size
            if (c.naiveJumps.isNotEmpty()) {
                filesWithNaiveJumps += rel
                naiveOffenders += c.naiveJumps.map { jumpReport(rel, listOf(it)) }
            }
            realOffenders += c.realJumps.map { jumpReport(rel, listOf(it)) }
        }
        assertTrue(
            "真尺子的跳级处应为 0（第 2 条的同一条判据，这里再钉一次好让两把尺子的读数摆在一起）：\n" +
                realOffenders.joinToString("\n") { "  $it" },
            realOffenders.isEmpty(),
        )
        assertTrue(
            "朴素尺子（不认围栏）只报出 $naiveJumpTotal 处假跳级，低于下限 $naiveJumpFloor" +
                "（第 109 轮实测 8：`docs/08` 2 处、`records/04` 6 处）：这说明本语料里" +
                "「围栏内的 `#` 骗得动一把坏尺子」这件事已经不再成立，本条与第 3 条同时失去意义，" +
                "必须重新挑一份带围栏 `#` 的文件当基准",
            naiveJumpTotal >= naiveJumpFloor,
        )
        assertTrue(
            "朴素尺子报出假跳级的文件只有 ${filesWithNaiveJumps.size} 份，低于下限 $naiveJumpFileFloor" +
                "（第 109 轮实测 2 份：`docs/08`、`records/04`）",
            filesWithNaiveJumps.size >= naiveJumpFileFloor,
        )
        assertTrue(
            "真尺子与朴素尺子的跳级读数完全一样（都 $naiveJumpTotal 处）：围栏跟踪已经不起作用，" +
                "这一族守卫整片退化成朴素尺子——这正是第 109 轮那场普查最先跑出来的坏结果。\n" +
                "朴素尺子报出的 ${naiveOffenders.size} 处假跳级（全部应落在围栏里）：\n" +
                naiveOffenders.joinToString("\n") { "  $it" },
            naiveJumpTotal != realOffenders.size,
        )
    }

    /**
     * 第 5 条：9 条人造片段钉死 [census] 本身（与 [DocTableShapeGuardTest.theCellSplitterSplitsRowsTheWayGitHubDoes]
     * 同体例）。第 2 条绿而这一条红，说明是**尺子**的问题而不是文档的问题，两者能分开。
     *
     * 每条片段声明「期望的真标题数 / 围栏档数 / 真跳级数 / 朴素跳级数」，四个数与 [census] 的读数
     * 逐条对齐。片段里的 `# 注释` 与 `# 甲` 那些字是**故意**的：靠 `#` 的位置而不是靠文字判断。
     */
    @Test
    fun theHeadingScannerReadsSnippetsTheWayCommonMarkDoes() {
        /** 一条人造片段：把若干行连起来喂给 [census]，再比四个数。 */
        data class Snippet(
            val why: String,
            val lines: List<String>,
            val real: Int,
            val fenced: Int,
            val realJumps: Int,
            val naiveJumps: Int,
        )

        val snippets = listOf(
            Snippet(
                "最普通的三级递降：h1 → h2 → h3，一处跳级也没有",
                listOf("# 甲", "## 乙", "### 丙"),
                real = 3, fenced = 0, realJumps = 0, naiveJumps = 0,
            ),
            Snippet(
                "h1 直接下到 h3：这就是「跳级」，真尺子必须报 1 处",
                listOf("# 甲", "### 丙"),
                real = 2, fenced = 0, realJumps = 1, naiveJumps = 1,
            ),
            Snippet(
                "围栏里的 `# 注释` 不是标题：真读数 0 跳级，朴素尺子被它骗出 1 处假跳级" +
                    "（h2 → 围栏里的 h1 → 围栏外的 h3）",
                listOf("# 甲", "## 乙", "```", "# 注释", "```", "### 丙"),
                real = 3, fenced = 1, realJumps = 0, naiveJumps = 1,
            ),
            Snippet(
                "1~3 个空格缩进的 `#` **算**标题（CommonMark 允许缩进 0~3 格）",
                listOf("   # 甲", "## 乙"),
                real = 2, fenced = 0, realJumps = 0, naiveJumps = 0,
            ),
            Snippet(
                "4 个空格缩进的 `#` **不算**标题（那是缩进式代码块）——夹在中间也不影响层级",
                listOf("# 甲", "    # 乙", "## 丙"),
                real = 2, fenced = 0, realJumps = 0, naiveJumps = 0,
            ),
            Snippet(
                "`#` 后面没有空格不算标题（`#乙` 是正文）——所以 h1 之后接 `## 丙` 仍然不跳级",
                listOf("# 甲", "#乙", "## 丙"),
                real = 2, fenced = 0, realJumps = 0, naiveJumps = 0,
            ),
            Snippet(
                "波浪号围栏同论：`~~~` 之内的 `#` 也落进围栏档",
                listOf("# 甲", "~~~", "### 注释", "~~~", "## 乙"),
                real = 2, fenced = 1, realJumps = 0, naiveJumps = 1,
            ),
            Snippet(
                "围栏**记号必须配对**：` ``` ` 开着的时候 `~~~` 不闭合它，所以 `### 注释` 仍在围栏里",
                listOf("# 甲", "```", "~~~", "### 注释", "```", "## 乙"),
                real = 2, fenced = 1, realJumps = 0, naiveJumps = 1,
            ),
            Snippet(
                "引用块里的标题 `> # 甲` **不算**标题（本仓不这么写；如实钉成边界，不假装它对）",
                listOf("# 甲", "> # 乙", "## 丙"),
                real = 2, fenced = 0, realJumps = 0, naiveJumps = 0,
            ),
        )

        assertTrue(
            "人名片段的条数（${snippets.size}）低于下限 $snippetFloor：尺子自检被削短了，" +
                "它就不再覆盖上面那几格",
            snippets.size >= snippetFloor,
        )

        val wrong = mutableListOf<String>()
        for (s in snippets) {
            val c = census(s.lines.joinToString("\n"))
            val actual = listOf(c.real.size, c.fenced.size, c.realJumps.size, c.naiveJumps.size)
            val expected = listOf(s.real, s.fenced, s.realJumps, s.naiveJumps)
            if (actual != expected) {
                wrong += "「${s.why}」\n      期望 real/fenced/真跳级/朴素跳级 = $expected，" +
                    "实测 = $actual（片段：${s.lines.joinToString(" ⏎ ")}）"
            }
        }
        assertTrue(
            "有 ${wrong.size} 条人造片段的读数与期望不符——是 [census] 这把尺子歪了，" +
                "不是文档的问题：\n" + wrong.joinToString("\n") { "  $it" },
            wrong.isEmpty(),
        )
    }

    /**
     * 第 6 条：`records/01` 的轮次结构——每个 `### NNN.x` 条目都必须落在一个**编号相同**的
     * `## 第 NNN 轮…` 之下，且轮次标题的编号 1..最大不许漏号。
     *
     * 第 114 轮的由来（真事件）：第 113 轮落盘时 `records/01` 那片段的**第一行是空的**，轮次标题
     * `## 第 113 轮：…` 从来没进过文件，八个 `### 113.x` 条目于是直接挂在 `## 第 112 轮` 底下。
     * 整份文件里一个字都是对的：条目自己写对了、层级没有跳级（第 2 条读不出来）、标题总数只少 1
     * （第 1 条的下限读不出来）。前面五条没有一条看「谁管着谁」，所以这处整整一轮没被发现，
     * 直到第 114 轮做普查才露面。
     *
     * 覆盖面：只管 `records/01` 一份；`docs/0X` 的小节编号（`### 8.70` 后接 8.71 还是 9.1）
     * 仍然没有判据。
     */
    @Test
    fun roundHeadingsGovernTheirOwnEntries() {
        val rel = "records/01-迭代记录.md"
        val c = censusOf(rel)
        val rounds = c.real.filter { it.level == 2 }
            .mapNotNull { h -> roundHeading.find(h.text)?.groupValues?.get(1)?.toInt() }
        val entries = c.real.filter { it.level == 3 && roundEntry.find(it.text) != null }
        assertTrue(
            "`$rel` 里只数到 ${rounds.size} 个轮次标题、${entries.size} 个 `### NNN.x` 条目" +
                "（下限分别是 $roundHeadingFloor / $roundEntryFloor）：整份读漏、或轮次体例被换掉时，" +
                "这一条会退化成「零标题 ⇒ 一律通过」",
            rounds.size >= roundHeadingFloor && entries.size >= roundEntryFloor,
        )

        var lastRound: Int? = null
        val orphans = mutableListOf<String>()
        for (h in c.real) {
            if (h.level <= 2) {
                lastRound = roundHeading.find(h.text)?.groupValues?.get(1)?.toInt()
            } else if (h.level == 3) {
                val m = roundEntry.find(h.text) ?: continue
                val n = m.groupValues[1].toInt()
                if (lastRound != n) {
                    orphans += "第 ${h.line} 行 `### ${h.text.take(40)}` 的编号是 $n，它上面最近的轮次标题是" +
                        (lastRound?.let { "第 $it 轮" } ?: "「没有」（不在任何 `## 第 N 轮` 之下）")
                }
            }
        }
        assertTrue(
            "`$rel` 里有 ${orphans.size} 个条目的编号与管辖它的轮次标题对不上：\n" +
                orphans.joinToString("\n") { "  $it" } +
                "\n每个 `### NNN.x` 都必须落在编号相同的 `## 第 NNN 轮…` 之下——轮次标题没写、" +
                "或写成别轮编号，条目就挂到别人名下，按轮次翻记录会翻错轮。第 114 轮实测那处正是" +
                "「片段第一行是空的」",
            orphans.isEmpty(),
        )

        val maxRound = rounds.maxOrNull() ?: 0
        val missing = (1..maxRound).filter { it !in rounds.toSet() }
        assertTrue(
            "`$rel` 里从第 1 轮到第 $maxRound 轮有 ${missing.size} 个轮次标题缺号：$missing" +
                "——本仓逐轮追加，轮次标题编号必须一个不缺；整轮连标题带条目一起丢时「归属」那半" +
                "读不出来",
            missing.isEmpty(),
        )
    }

    /**
     * 第 7 条：**轮次标题的全仓覆盖、逐份连号、杂项 h2 与正文体量**。
     *
     * 第 6 条只管 `records/01` 一份；第 115 轮做普查时数出 `records/CHANGELOG.md` 里也有 51 个
     * `## 第 NNN 轮…`（64..114 连号），而它当时**一个字的判据都没有**。「哪份文件里有轮次标题」
     * 只存在于人的记忆里——将来哪份文件长出轮次标题或被整体改名，管辖范围就**静默**变了，
     * 与第 113 轮那处「文档不在任务输入里 ⇒ 守卫静默不跑」是同一类缺陷。
     *
     * 这一条做四件事：
     * 1. **覆盖**：受管文件里「含轮次标题」的集合必须与 [roundHeadingRegistry] **相等**
     *    （多一份＝没登记，少一份＝登记了却读不到）。
     * 2. **逐份连号**：每个登记文件里，轮次编号从它**自己的最小号**连到最大号，不漏号。
     * 3. **杂项 h2**：第一个轮次标题**之后**不得再出现不是轮次标题的 h2
     *    （`records/CHANGELOG.md` 的 `## [Unreleased]` 在第一个轮次标题**之前**，合规）。
     * 4. **正文体量**：逐块 ≥ [perRoundBodyFloor]、每份合计 ≥ 登记表里的下限。
     *
     * 覆盖面：只管这 16 份受管文件（`app/`、`.github/` 仍不在管辖内）；登记表是手写的，加了新
     * 文件得人来改；连号只看「不缺号」，**不看顺序**（编号乱序不报红）。
     */
    @Test
    fun roundHeadingsAreRegisteredAndContiguousWhereverTheyAppear() {
        val registered = roundHeadingRegistry.map { it.rel }
        val found = scannedDocs().filter { rel ->
            censusOf(rel).real.any { it.level == 2 && roundHeading.find(it.text) != null }
        }
        val unregistered = found.filter { it !in registered.toSet() }
        val vanished = registered.filter { it !in found.toSet() }
        assertTrue(
            "受管文件里出现轮次标题的有 ${found.size} 份（$found），登记表里是 ${registered.size} 份" +
                "（$registered）——两边必须相等。" +
                (if (unregistered.isEmpty()) "" else
                    "\n  没登记却出现了轮次标题：$unregistered——轮次标题一出，它就该被管着；" +
                        "请把它登记进 roundHeadingRegistry，并按体例补齐编号与正文") +
                (if (vanished.isEmpty()) "" else
                    "\n  登记了却读不到轮次标题：$vanished——整份读漏、路径改了、或体例被换掉，" +
                        "管辖范围正在静默缩水"),
            unregistered.isEmpty() && vanished.isEmpty(),
        )

        val problems = mutableListOf<String>()
        for (rf in roundHeadingRegistry) {
            val rel = rf.rel
            val lines = readNormalized(rel).split("\n")
            // 该文件的全部 h2（含在轮次体例之外的），按行号升序。
            val h2 = censusOf(rel).real.filter { it.level == 2 }

            val rounds = h2.mapNotNull { roundHeading.find(it.text)?.groupValues?.get(1)?.toInt() }
            // 个数下限先查：轮次标题被整片读漏、或体例被换掉时，下面的连号／杂项／正文下限
            // 会全部退化成「对空集合提要求」，一个都不报。
            if (rounds.size < rf.totalFloor) {
                problems += "  `$rel` 里只数到 ${rounds.size} 个轮次标题，低于登记表下限 " +
                    "${rf.totalFloor}——整份读漏、或体例被换掉时，这一份的连号／杂项／正文下限" +
                    "会全部退化成零命题"
            }
            if (rounds.isEmpty()) continue

            // 逐块正文：块 = 该 h2 行到下一个真 h2 行之间，数非空行；最后一块数到 EOF。
            var bodyTotal = 0
            for ((i, h) in h2.withIndex()) {
                val endExclusive = h2.getOrNull(i + 1)?.line ?: (lines.size + 1)
                val body = lines.subList(h.line, endExclusive - 1).count { it.isNotBlank() }
                bodyTotal += body
                if (body < perRoundBodyFloor) {
                    problems += "  `$rel` 第 ${h.line} 行「${h.text.take(40)}」下面只有 $body 行非空" +
                        "正文，低于逐块下限 $perRoundBodyFloor（空节／近空节：「轮次标题写了就算记录了」" +
                        "是这一族最想防的假绿）"
                }
            }

            val firstRoundLine = h2.first { roundHeading.find(it.text) != null }.line
            problems += h2.filter { roundHeading.find(it.text) == null && it.line > firstRoundLine }
                .map {
                    "  `$rel` 第 ${it.line} 行有个不是轮次标题的 h2：「${it.text.take(40)}」——" +
                        "第一个轮次标题之后，h2 这一层只用来划轮次，别的东西挂在这一层会让" +
                        "「按轮次翻记录」翻到一段不是轮次的东西"
                }

            val sorted = rounds.sorted()
            val missing = (sorted.first()..sorted.last()).filter { it !in sorted.toSet() }
            if (missing.isNotEmpty()) {
                problems += "  `$rel` 的轮次编号 ${sorted.first()}..${sorted.last()} 里缺 ${missing.size} 个：" +
                    "$missing——本仓逐轮追加，编号必须一个不缺"
            }
            if (bodyTotal < rf.bodyTotalFloor) {
                problems += "  `$rel` 的轮次正文合计只有 $bodyTotal 行非空，低于登记表下限 " +
                    "${rf.bodyTotalFloor}——成片正文丢了，「逐块下限」读不出来（块还在、里面几乎空了）"
            }
        }
        assertTrue(
            "轮次体的结构有 ${problems.size} 处不对：\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }
}
