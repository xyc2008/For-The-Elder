package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档体检那份探针工具自己有没有被人拆掉」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * ## 它管的对象
 *
 * 第 143 轮往仓库里新落了一个 `tools/` 目录，里面两份 Python 脚本：`tools/probe/probes.py`
 * （文档体检探针，四个观测面各一条 spec）与 `tools/probe/compare.py`（两份探针留档的差额对账）。
 * 这两份脚本的存在意义是：它们**不是我方构建的一部分**，`./gradlew :app:testDebugUnitTest`
 * 不会顺带跑它们；一份脚本被删掉、被改成只剩一个空壳、或者那条管着十六份文档的清单被悄悄砍短，
 * 既有十一个判据一个都不会响 —— 因为没有一个判据读得到 `tools/` 目录。这是「工具不被看着」
 * 这一族的缺口，本条裁决把它补上。
 *
 * ## 它做什么（八条用例）
 *
 * 1. [theManagedFileListIsParsedAndEveryEntryIsARealFile]：把 `probes.py` 文本里那张字面数组
 *    `MANAGED_FILES` 解出来，核条目数不低于已知的十六份，再逐条核**磁盘上真有这份文件**。
 *    清单是探针的管辖范围本身，清单里的条目指向一份不存在的文件，探针会当场崩而不是报红。
 * 2. [everyMarkdownDocAndRecordIsCoveredByTheManagedList]：把磁盘上 `docs/` 与 `records/` 下的
 *    全部 Markdown 文件列出来，逐个要求**都被那张清单覆盖**。漏一份就等于那份文档从此不受探针
 *    管辖，而库里没有任何一把尺子会为此响 —— 这条用例专治「新落一份文档、忘了进清单」。
 * 3. [theRepoRootIsDerivedByWalkingUpThreeLevels]：`probes.py` 靠「从自身路径往上走三层」定位
 *    仓库根，这条用例既在文本上钉住那种写法（三个 `os.path.dirname`），也在文件系统上核一遍
 *    「从 `tools/probe/probes.py` 往上三层**确实**是仓库根」—— 脚本被挪到别的层级就红。
 * 4. [theFourSpecNamesArePinned]：探针的四个观测面名字逐字钉住（`tables` / `refs` / `eol` / `xref`，
 *    恰好四个、不多不少）。名字被改掉、被合并、被删一个，这里立刻红。
 * 5. [theFourTotalLineFormatsArePinned]：四条汇总行的**格式串**逐字钉住。这四条是给人读的输出契约，
 *    也是 `compare.py` 解析留档的前提；格式串一改，任何按旧格式写下的留档都对不上了。
 * 6. [theCompareToolPinsPrePostDiffAndItsVerdict]：`compare.py` 的三件事逐字钉住 —— 逐项打印
 *    `PRE` / `POST` / `DIFF` 那行的格式串、末行的总判定串、以及退出码标记。另外核一句
 *    「总判定 PASS 不等于零差异」的口径说明**真的写在脚本里**，免得后来的读者把 PASS 误读成「没差别」。
 * 7. [theManagedListRulerReportsItsFixtures]：尺子自证。拿构造样本喂给上面那把解清单的尺子：
 *    正常两行的样本必须解出两行；**根本没有那张数组**的样本必须报错而不是悄悄返回空表 ——
 *    后者是这条用例真正要防的：一把「找不到就返回空」的尺子会让第 1、2 两条恒真。
 * 8. [theFloorsAreSaneAndTheKnownReadingsHold]：各类底线常数自身要合理（不许写成零或负数、不许
 *    超过当轮实测），并复核此刻真读到的那几条读数落在登记值上。
 *
 * ## 它不保证什么（照实挂账）
 *
 * - **不真的起 Python 进程**。八条用例全是**文本面**的：它证明的是「这两份脚本的形状还在、清单还全、
 *   契约串还没被改」，**不证明**探针跑起来还出得来同样的读数。探针真跑出来的四个汇总读数归
 *   `records/04-质量与验证记录.md` 那一轮的对账节，不归这条用例。
 * - **不核探针的判法对不对**。`probes.py` 怎么切表格、怎么算越界、怎么认活文档区，本用例一个字
 *   都不过问。口径分歧（例如它与 [DocTableShapeGuardTest] 在「分隔行怎么认」上用的是两种写法）
 *   记在那一轮的质量记录里，不在这一条。
 * - **不核留档 JSON 的内容**。探针落下的两份 JSON 归档、差额怎么解释，全在质量记录那一侧。
 * - **不管 `tools/` 下别的东西**。`tools/` 里将来再落别的脚本、别的目录，本用例既不要求登记也不
 *   禁止 —— 它只认这两份探针与那张清单。
 * - **两处 `os.path.dirname` 的写法是文本匹配**：把 `dirname` 换成等价的 `os.path.split` 会假红。
 *   这是有意为之（宁可假红也别让定位方式悄悄换掉），代价照实记在那一轮的质量记录里。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。仓库根定位的写法抄自 [DocCrossRefGuardTest]。
 */
class ProbeToolGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val probesPy: File = File(repoRoot, "tools/probe/probes.py")
    private val comparePy: File = File(repoRoot, "tools/probe/compare.py")

    /** 读全文并把 `\r\n` 归一成 `\n`：盘上哪一份是 CRLF 不该影响这里的任何一条断言。 */
    private fun readNormalized(file: File): String {
        assertTrue("这份文件真必须存在：${file.absolutePath}", file.isFile)
        return file.readText().replace("\r\n", "\n")
    }

    /**
     * 从一段文本里解出形如 `名字 = [ "甲", "乙", ]` 的字面数组。找不到那张数组、或数组没有收尾的
     * `]`，**当场报错**而不是返回空表 —— 返回空表会让「清单不全」那两条断言退化成恒真。
     */
    private fun parseStringArray(text: String, name: String): List<String> {
        val head = "$name = ["
        val start = text.indexOf(head)
        if (start < 0) error("在这段文本里找不到 `$head`")
        val open = text.indexOf('[', start)
        val close = text.indexOf(']', open)
        if (close < 0) error("`$name` 那张数组没有收尾的 `]`")
        return Regex("\"([^\"]+)\"").findAll(text.substring(open + 1, close))
            .map { it.groupValues[1] }
            .toList()
    }

    /** 探针文本里出现过的观测面名字（`[spec 名字]` 那一族），去重。 */
    private fun specNamesOf(text: String): List<String> =
        Regex("\\[spec ([a-z]+)\\]").findAll(text).map { it.groupValues[1] }.distinct().sorted().toList()

    /** 某个目录下**当层**的全部 Markdown 文件，返回「目录名/文件名」形式的相对路径。 */
    private fun markdownUnder(dirName: String): List<String> {
        val dir = File(repoRoot, dirName)
        assertTrue("这个目录真必须存在：${dir.absolutePath}", dir.isDirectory)
        return (dir.listFiles() ?: error("列不出目录：${dir.absolutePath}"))
            .filter { it.isFile && it.name.endsWith(".md") }
            .map { "$dirName/${it.name}" }
            .sorted()
    }

    private fun managedFiles(): List<String> = parseStringArray(readNormalized(probesPy), "MANAGED_FILES")

    @Test
    fun theManagedFileListIsParsedAndEveryEntryIsARealFile() {
        val managed = managedFiles()
        assertTrue(
            "清单里的条目下限是 $managedFileFloor（第 143 轮实测 $knownManagedCount 份）；现 ${managed.size} 份。" +
                "清单被砍短，探针的管辖范围就跟着缩水，而库里没有别的尺子会响。",
            managed.size >= managedFileFloor,
        )
        assertEquals("清单里不许出现重复条目", managed.size, managed.distinct().size)
        val missing = managed.filterNot { File(repoRoot, it).isFile }
        assertTrue(
            "清单里每一条都必须指向一份**真存在**的文件；这些没有：$missing",
            missing.isEmpty(),
        )
        assertTrue("仓库根的 `README.md` 也在清单里（门面那份也在体检范围）", managed.contains("README.md"))
    }

    @Test
    fun everyMarkdownDocAndRecordIsCoveredByTheManagedList() {
        val managed = managedFiles().toSet()
        val onDisk = markdownUnder("docs") + markdownUnder("records")
        assertTrue(
            "`docs/` 与 `records/` 下当层的 Markdown 下限是 $markdownFloor 份（第 143 轮实测 $knownMarkdownCount 份）；" +
                "现 ${onDisk.size} 份 —— 扫描目录被换走或文档被抽空都会退化成零命中。",
            onDisk.size >= markdownFloor,
        )
        val uncovered = onDisk.filterNot { it in managed }
        assertTrue(
            "这些文档在盘上、却不在探针的 `MANAGED_FILES` 清单里：$uncovered。" +
                "漏登记一份就等于那份文档从此不受探针管辖 —— 新落一份文档时要顺手进清单。",
            uncovered.isEmpty(),
        )
        assertTrue(
            "`docs/` 下那份文件必须有一份（下限 $docsFileFloor，实测 $knownDocsCount）",
            markdownUnder("docs").size >= docsFileFloor,
        )
        assertTrue(
            "`records/` 下那份文件必须有一份（下限 $recordsFileFloor，实测 $knownRecordsCount）",
            markdownUnder("records").size >= recordsFileFloor,
        )
    }

    @Test
    fun theRepoRootIsDerivedByWalkingUpThreeLevels() {
        val text = readNormalized(probesPy)
        val rootLine = Regex("(?m)^REPO_ROOT = .*$").find(text)?.value
            ?: error("`probes.py` 里找不到以 `REPO_ROOT = ` 起头的那一行 —— 仓库根定位被挪走或改了写法。")
        assertEquals(
            "`probes.py` 靠「从自身路径往上走三层」定位仓库根，`REPO_ROOT = ` 这一行上必须正好有三个 `os.path.dirname(`",
            3,
            Regex("os\\.path\\.dirname\\(").findAll(rootLine).count(),
        )
        assertTrue(
            "「往上三层」的那一行必须从 `os.path.abspath(__file__)` 出发（换成别的起点就不再是自身路径了）",
            rootLine.contains("os.path.abspath(__file__)"),
        )
        assertEquals(
            "文件系统上也要核一遍：`tools/probe/probes.py` 往上三层必须**就是**仓库根",
            repoRoot.absoluteFile,
            probesPy.absoluteFile.parentFile!!.parentFile!!.parentFile!!,
        )
        assertEquals(
            "`compare.py` 与探针同层（两份脚本都落在 `tools/probe/` 下）",
            "probe",
            comparePy.absoluteFile.parentFile!!.name,
        )
    }

    @Test
    fun theFourSpecNamesArePinned() {
        val names = specNamesOf(readNormalized(probesPy))
        assertEquals(
            "探针的观测面必须恰好是登记过的这四个（多一个少一个、改一个字都红）；现 $names",
            KNOWN_SPECS,
            names,
        )
    }

    @Test
    fun theFourTotalLineFormatsArePinned() {
        val text = readNormalized(probesPy)
        val missing = TOTAL_FORMATS.filterNot { it in text }
        assertTrue(
            "四条汇总行的格式串逐字都必须还在 `probes.py` 里；这些找不到了：$missing",
            missing.isEmpty(),
        )
        assertTrue(
            "探针必须在末行打出 `PROBES-EXIT=0`（退出码标记是对账脚本与人的共同锚点）",
            "PROBES-EXIT=0" in text,
        )
        assertTrue(
            "探针必须打出落档 JSON 的路径标记 `PROBES-JSON=`（没有它，两份留档无从指认）",
            "PROBES-JSON=" in text,
        )
        assertTrue(
            "探针必须留 `--label` 这个开关（两份留档靠它区分标签），且默认标签是 `run`",
            "--label" in text && "default=\"run\"" in text,
        )
        assertTrue(
            "探针必须留 `--json` 这个开关（`PROBES-JSON=` 打的就是它给的路径），且默认为空串",
            "--json" in text && "default=\"\"" in text,
        )
    }

    @Test
    fun theCompareToolPinsPrePostDiffAndItsVerdict() {
        val text = readNormalized(comparePy)
        assertTrue(
            "逐项差额那行必须打印 `PRE` / `POST` / `DIFF` 三个数（格式串逐字钉住）",
            "PRE=%d POST=%d DIFF=%+d" in text,
        )
        assertTrue(
            "末行必须是一条可机读的总判定（`COMPARE143` 加判定词）",
            "COMPARE143 总判定" in text,
        )
        assertTrue(
            "对账脚本必须打出 `COMPARE-EXIT=0` 这个退出码标记",
            "COMPARE-EXIT=0" in text,
        )
        assertTrue(
            "脚本里必须写明「总判定 PASS 不等于零差异」这句口径 —— " +
                "不然 PASS 会被后来的读者误读成「两份一模一样」",
            "不代表差额为零" in text,
        )
        assertTrue(
            "对账脚本要按 18 条标量指标打差额（清单被砍短就等于少看几面）",
            Regex("(?m)^METRICS = \\[").containsMatchIn(text),
        )
    }

    @Test
    fun theManagedListRulerReportsItsFixtures() {
        val twoEntry = """
            头部文字
            MANAGED_FILES = [
                "docs/00-甲.md",
                "records/01-乙.md",
            ]
            尾部文字
        """.trimIndent()
        assertEquals(
            "正常样本必须解出两行（顺序也要保住）",
            listOf("docs/00-甲.md", "records/01-乙.md"),
            parseStringArray(twoEntry, "MANAGED_FILES"),
        )
        assertEquals(
            "**根本没有那张数组**的样本必须报错，不许悄悄返回空表 —— " +
                "返回空表会让「清单不全」那两条断言恒真",
            "throw",
            runCatching { parseStringArray("这里没有那张数组", "MANAGED_FILES") }
                .fold(onSuccess = { "no-throw" }, onFailure = { "throw" }),
        )
        assertEquals(
            "数组开了没合的样本同样必须报错",
            "throw",
            runCatching { parseStringArray("MANAGED_FILES = [", "MANAGED_FILES") }
                .fold(onSuccess = { "no-throw" }, onFailure = { "throw" }),
        )
        assertEquals(
            "观测面名字那把尺子只认 `[spec 名字]` 这种写法，散文里的同名不算",
            listOf("refs", "tables"),
            specNamesOf("散文里写 tables 与 refs 不算数。[spec tables] … [spec refs] … [spec tables]"),
        )
    }

    @Test
    fun theFloorsAreSaneAndTheKnownReadingsHold() {
        assertTrue(
            "四类底线都不许是零或负数（写成零就等于把对应的那条断言豁免掉）：" +
                "managedFileFloor=$managedFileFloor markdownFloor=$markdownFloor " +
                "docsFileFloor=$docsFileFloor recordsFileFloor=$recordsFileFloor",
            managedFileFloor > 0 && markdownFloor > 0 && docsFileFloor > 0 && recordsFileFloor > 0,
        )
        assertTrue(
            "下限不许超过当轮实测值，否则那几条永远红：$managedFileFloor / $markdownFloor / " +
                "$docsFileFloor / $recordsFileFloor 对 $knownManagedCount / $knownMarkdownCount / " +
                "$knownDocsCount / $knownRecordsCount",
            managedFileFloor <= knownManagedCount && markdownFloor <= knownMarkdownCount &&
                docsFileFloor <= knownDocsCount && recordsFileFloor <= knownRecordsCount,
        )
        assertEquals("清单份数复核（当轮读数）", knownManagedCount, managedFiles().size)
        assertEquals("`docs/` 当层 Markdown 份数复核（当轮读数）", knownDocsCount, markdownUnder("docs").size)
        assertEquals("`records/` 当层 Markdown 份数复核（当轮读数）", knownRecordsCount, markdownUnder("records").size)
        assertEquals(
            "四个观测面份数复核（当轮读数）",
            KNOWN_SPECS.size,
            specNamesOf(readNormalized(probesPy)).size,
        )
        assertEquals("四条汇总格式串份数复核（当轮读数）", 4, TOTAL_FORMATS.size)
    }

    private companion object {
        /** 第 143 轮登记：探针的四个观测面（表格 / 引用 / 行尾 / 互引），恰好这四个。 */
        val KNOWN_SPECS = listOf("eol", "refs", "tables", "xref")

        /** 四条汇总行的格式串，逐字抄自 `probes.py`。 */
        val TOTAL_FORMATS = listOf(
            "[spec tables] TOTAL tables=%d rows=%d issues=%d",
            "[spec refs] TOTAL refs=%d blank-or-out=%d",
            "[spec eol] TOTAL %d HITFILES %d",
            "[spec xref] TOTAL=%d LIVE=%d HIST=%d LIVE_BAD=%d HIST_NOFILE=%d",
        )

        /** 清单条目下限 = 第 143 轮实测的 16 份（下限只能写在落盘之前，余量如实记在那一轮的质量记录里）。 */
        const val managedFileFloor = 16

        /** `docs/` 与 `records/` 当层 Markdown 合计下限 = 第 143 轮实测的 15 份。 */
        const val markdownFloor = 15

        /** 其中 `docs/` 那一侧的下限 = 实测的 10 份。 */
        const val docsFileFloor = 10

        /** 其中 `records/` 那一侧的下限 = 实测的 5 份。 */
        const val recordsFileFloor = 5

        /** 第 8 条对照用的当轮读数。 */
        const val knownManagedCount = 16
        const val knownMarkdownCount = 15
        const val knownDocsCount = 10
        const val knownRecordsCount = 5
    }
}
