package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ## 它做什么
 *
 * 判据 15 —— 盯住 `docs/09-用户手册.md`（下文简称「手册」）这份**面向使用者**的说明书。
 *
 * 判据 14 是第一条落在纯文档上的判据（盯 `docs/08-部署与构建说明.md`），它的由来是第 137 轮的一次实证：
 * 往 `docs/08` 末尾纯追加 293 字节散文，定向集红数与基线同为 6、**零新增**。手册是同一类文件里**最该被看着的一份**
 * （`docs/00`~`docs/09` 十份活文档里，只有它是写给「拿手机的那位」看的，其余九份写给做工程的人），
 * 而它到第 138 轮为止**一条形状判据都没有**：`DocHeadingStructureGuardTest` 只扫标题层级与轮次标题连号，
 * `DocCrossRefGuardTest` 只认 `` `docs/0X:NNN` `` 这种带行号的短引用，`DocScreenCopyGuardTest` 只核**引文内容**
 * 与屏幕一致，`DocCurrentReadingGuardTest` 只在它自己的九份冻结清单上比字节数（手册在里面，110,581 字节）。
 * 于是「手册的小节被改名、被删节、被追加一整段、代码块被吃掉」这四件事，一件都无人报警。这一轮把这条盲区关掉。
 *
 * 九个用例分工：
 * 1. [theUserManualIsActuallyReadAndHasTheKnownBaseShape] —— 文件真在、真读得进来、字节数与行数不低于底线。
 * 2. [theElevenNumberedSectionsAndTheRevisionRecordAreExactlyTheKnownOnes] —— 十二个 `## ` 标题逐字、逐序相等，
 *    编号 0..10 连号不漏，末条是无编号的 `## 修订记录`。
 * 3. [theTailOfTheManualIsPinnedSoAnyAppendShowsUp] —— 末尾那一句与「末尾空行数」钉死，**这一条才是真正抓纯追加的**。
 * 4. [theFenceLinesArePinnedSoAppendingACodeBlockShowsUp] —— 围栏行数与对数钉死，追加或删除代码块会被看见。
 * 5. [everyShortDocReferenceInTheManualPointsAtOneRealFile] —— 手册里每一处 `` `docs/0X` `` / `` `records/0X` ``
 *    都得恰好对上一份真文件（**带行号的那种归 [DocCrossRefGuardTest]，这一条管不带行号的短形**）。
 * 6. [theTailRulerReportsAppendsInsteadOfStayingGreen] —— 尺子自检：尾部形状函数在 6 组人造片段上的读数。
 * 7. [theFenceRulerReportsItsFixtures] —— 尺子自检：围栏普查函数在 8 组人造片段上的读数。
 * 8. [theShortRefRulerReportsItsFixtures] —— 尺子自检：短引用抽取函数在 5 组人造片段上的读数。
 * 9. [theFloorsAreSaneAndTheKnownReadingsHold] —— 底线自检：底线不许宽到形同虚设，记下的读数不许被悄悄改掉。
 *
 * ## 它不保证什么
 *
 * - **刻意不钉手册的精确字节数与 md5**。正文每改一个字都要人跟着滚常量，滚漏一次判据就退化成「一律通过」的假绿；
 *   这一轮只钉形状（标题、尾部、围栏数、短引用清单）。第 139 轮**实测过**：往手册末尾纯追加一行散文，
 *   第 3 条当场红（该轮反向变异第五档，见 `records/04` 第一百四十一节）。
 * - **不核正文说得对不对**。手册里「这句话是什么意思、那一步该点哪儿」全靠人读；这条判据只管形状。
 *   引文与屏幕是否一致归 [DocScreenCopyGuardTest]，行号类引用是否还指得着归 [DocCrossRefGuardTest]。
 * - 断言用的是 [readNormalized]（先把 CRLF 归一成 LF）。本仓库 `.gitattributes` 是 `* text=auto` 且 `core.autocrlf=true`，
 *   `git checkout` 会把盘上的 LF 转成 CRLF；此时若断言「`\r` 个数为 0」会造出假红。字节数底线用原始字节，两种行尾都过。
 * - **第 3 条的尾部锚是「修订记录表的最后一行」，它将来一定会变**。手册每次更新都会往 `## 修订记录` 那张表**最上面**加一行，
 *   最后一行现在是 `| 1.0 | 2026-11-21 | …`（第 15 轮首版那条），它已经不再变；可一旦有人真要动那一条，
 *   这条判据就会红，得人跟着滚 [expectedTailLine]。这是**刻意的**：主编不知道自己的尾部有没有被追加，
 *   才是这条判据要防的事。
 * - **第 4 条的底线形同虚设**。手册全篇只有**一对**围栏（第 62、83 行那一块），
 *   所以 [fenceLineFloor] 只能是 2 —— 它钉的是**形状**（那一对还在、还闭合），**不是体量**。
 *   不要把它当成「代码块够多」的证明。
 * - **第 5 条只管不带行号的短形引用，而且比的是文件名前缀**。「`docs/03` 里那句话说对了没有」它一个字都不管；
 *   同一编号下若出现两份 `03-*.md`，第 5 条会红（歧义一律红，不挑一份了事）。
 * - 「文件真被读了」只靠一条 `isFile` 加底线撑着：手册被删掉会红，被换成一个 110KB 的别的文件不会红
 *   （不过 [DocCurrentReadingGuardTest] 那份冻结清单会同时红，两张网互不替代）。
 *
 * ## 结论
 *
 * 第 139 轮实测（判据落地时的读数）：110,581 字节、`split("\n")` 1,035 行、十二个 `## ` 标题（编号 0..10 十一个小节
 * ＋ `## 修订记录`）、2 行围栏 / 1 对 / 闭合、54 处短引用落在 7 个键上、末尾 1 个空行、末句为 [expectedTailLine]。
 *
 * ⇒ 结论：手册从第 139 轮起**有了形状兜底**（标题、尾部、围栏、短引用四处），但「手册讲得对不对」这件事
 * **仍然没有任何机器判据**，`docs/07` 的风险 R-10 也仍然是橙色。
 */
class DocUserManualGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val doc: File = File(repoRoot, "docs/09-用户手册.md")

    private val textCache: MutableMap<String, String> = mutableMapOf()

    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun manualText(): String = textCache.getOrPut(doc.path) { readNormalized(doc) }

    /** `split("\n")` 口径：末尾带换行的文本会多出一个空串，与 `wc -l` 差 1。 */
    private fun linesOf(text: String): List<String> = text.split("\n")

    private data class TailShape(val lastText: String?, val trailingBlanks: Int)

    /** 尾部形状：最后一句非空正文 + 末尾连续空白行的条数。 */
    private fun tailShapeOf(text: String): TailShape {
        val lines: List<String> = linesOf(text)
        return TailShape(
            lastText = lines.lastOrNull { it.isNotBlank() },
            trailingBlanks = lines.takeLastWhile { it.isBlank() }.size,
        )
    }

    private data class FenceCensus(val lines: List<Int>, val closed: Boolean, val pairs: Int)

    /** 围栏普查：缩进 0~3 格的 ``` 或 ~~~ 算围栏行，记号只比前三字符，相邻两个配对。 */
    private fun fenceCensus(text: String): FenceCensus {
        val fence: Regex = Regex("""^ {0,3}(`{3,}|~{3,})""")
        val fenceLines: MutableList<Int> = mutableListOf()
        val markers: MutableList<String> = mutableListOf()
        linesOf(text).forEachIndexed { index, line ->
            val hit: MatchResult = fence.find(line) ?: return@forEachIndexed
            fenceLines += index + 1
            markers += hit.groupValues[1].substring(0, 3)
        }
        val pairs: Int = (0 until markers.size / 2).count { markers[2 * it] == markers[2 * it + 1] }
        return FenceCensus(
            lines = fenceLines,
            closed = markers.size % 2 == 0 && pairs * 2 == markers.size,
            pairs = pairs,
        )
    }

    /** 只认`` `docs/0X` ``与`` `records/0X` ``：反引号里**只有**目录名加两位编号，多一个字都不算。 */
    private fun shortDocRefs(text: String): List<String> {
        val call: Regex = Regex("""`((?:docs|records)/\d\d)`""")
        return call.findAll(text).map { it.groupValues[1] }.toList()
    }

    /** 形如 `docs/03` 的键要对上**恰好一份** `03-*.md`；对上零份或多份都不合格。 */
    private fun filesMatchingShortRef(key: String): List<String> {
        val dir: File = File(repoRoot, key.substringBefore('/'))
        val prefix: String = key.substringAfter('/') + "-"
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith(prefix) }
            .map { it.name }
            .sorted()
    }

    private fun fenceBones(text: String): Triple<Int, Boolean, Int> {
        val census: FenceCensus = fenceCensus(text)
        return Triple(census.lines.size, census.closed, census.pairs)
    }

    @Test
    fun theUserManualIsActuallyReadAndHasTheKnownBaseShape() {
        assertTrue("手册不存在：${doc.path}", doc.isFile)
        val rawBytes: Int = doc.readBytes().size
        assertTrue(
            "手册只有 $rawBytes 字节，低于底线 $byteFloor —— 文件被截断、或者被换成了别的东西",
            rawBytes >= byteFloor,
        )
        val text: String = manualText()
        assertTrue("手册读进来是空的", text.isNotBlank())
        val lineCount: Int = linesOf(text).size
        assertTrue(
            "手册只有 $lineCount 行（split 口径），低于底线 $splitLineFloor",
            lineCount >= splitLineFloor,
        )
    }

    @Test
    fun theElevenNumberedSectionsAndTheRevisionRecordAreExactlyTheKnownOnes() {
        val headings: List<String> = linesOf(manualText()).filter { it.startsWith("## ") }
        assertEquals(
            "手册的 `## ` 标题与登记值不一致（左=登记值，右=手册里实际的）；新增、删除、改名或调序都会红",
            knownHeadings,
            headings,
        )
        val numbers: List<Int> = headings.mapNotNull { line ->
            Regex("""^## (\d+)\. """).find(line)?.groupValues?.get(1)?.toInt()
        }
        assertEquals(
            "十二个标题里能抠出编号的只有 ${numbers.size} 条（应为 ${knownNumberedSections.size} 条）",
            knownNumberedSections.size,
            numbers.size,
        )
        assertEquals("小节编号应当从 0 连到 10、不漏不重", knownNumberedSections, numbers)
        assertEquals(
            "手册最后一条标题应当是无编号的那条修订记录",
            expectedLastHeading,
            headings.last(),
        )
    }

    @Test
    fun theTailOfTheManualIsPinnedSoAnyAppendShowsUp() {
        val shape: TailShape = tailShapeOf(manualText())
        assertEquals(
            "手册的最后一句非空正文与登记值不一致 —— 末尾被追加或改写了" +
                "（第 139 轮实测：往末尾纯追加一行散文，只有本判据与 DocCurrentReadingGuardTest 的字节数会红）",
            expectedTailLine,
            shape.lastText,
        )
        assertEquals(
            "手册末尾的空行数为 ${shape.trailingBlanks}（登记值 $knownTrailingBlanks）—— 末尾被追加了换行或空行",
            knownTrailingBlanks,
            shape.trailingBlanks,
        )
    }

    @Test
    fun theFenceLinesArePinnedSoAppendingACodeBlockShowsUp() {
        val census: FenceCensus = fenceCensus(manualText())
        assertEquals(
            "手册里的围栏行数为 ${census.lines.size}（登记值 $knownFenceLines）—— 追加或删除代码块会改变它",
            knownFenceLines,
            census.lines.size,
        )
        assertEquals(
            "手册里的围栏对数为 ${census.pairs}（登记值 $knownFencePairs）—— 代码块数量变了",
            knownFencePairs,
            census.pairs,
        )
        assertTrue(
            "手册的围栏没有闭合（成对的 ``` 或 ~~~）—— 围栏行号：${census.lines}",
            census.closed,
        )
        assertTrue(
            "手册里的围栏行数 ${census.lines.size} 低于底线 $fenceLineFloor",
            census.lines.size >= fenceLineFloor,
        )
    }

    @Test
    fun everyShortDocReferenceInTheManualPointsAtOneRealFile() {
        val found: List<String> = shortDocRefs(manualText())
        assertTrue(
            "手册里一处 `` `docs/0X` `` / `` `records/0X` `` 都没抽到，或者抽出来的比登记值 $knownShortRefCount 处还少" +
                "（实际 ${found.size} 处）",
            found.size >= shortRefFloor,
        )
        val keys: List<String> = found.distinct().sorted()
        assertEquals(
            "手册里点名的受管文件名与登记清单不一致（左=登记值，右=手册里实际的）；" +
                "手册新点名一份文档、或登记过的那份从手册里消失，都会红，逼着人来看一眼",
            knownShortRefKeys.sorted(),
            keys,
        )
        keys.forEach { key ->
            val matched: List<String> = filesMatchingShortRef(key)
            assertEquals(
                "手册里点名的 $key 在仓库里没有对上**恰好一份**文件（对上 ${matched.size} 份：$matched）" +
                    " —— 要么那份文档被改名或删掉，要么同一编号下出现了两份",
                1,
                matched.size,
            )
        }
        assertTrue("登记清单是空的，尺子坏了", knownShortRefKeys.isNotEmpty())
    }

    @Test
    fun theTailRulerReportsAppendsInsteadOfStayingGreen() {
        val clean = TailShape("乙", 1)
        assertEquals("`甲\\n乙\\n` 的末句应为 乙、末尾 1 个空行", clean, tailShapeOf("甲\n乙\n"))
        assertEquals("`甲\\n乙\\n丙\\n` 的末句应为 丙", TailShape("丙", 1), tailShapeOf("甲\n乙\n丙\n"))
        assertEquals("`甲\\n乙\\n\\n` 末尾算 2 个空行", TailShape("乙", 2), tailShapeOf("甲\n乙\n\n"))
        assertEquals("`甲\\n乙\\n   \\n` 末尾的空白行也算空行", TailShape("乙", 2), tailShapeOf("甲\n乙\n   \n"))
        assertEquals("`甲\\n乙`（不带尾换行）末尾空行数为 0", TailShape("乙", 0), tailShapeOf("甲\n乙"))
        assertEquals("空文本没有末句，末行按 1 个空行算", TailShape(null, 1), tailShapeOf(""))
        listOf(
            "甲\n乙\n丙\n" to "末尾追加了一行",
            "甲\n乙\n\n" to "末尾多了一个空行",
            "甲\n乙\n   \n" to "末尾多了一个空白行",
            "甲\n乙" to "尾换行被吃掉了",
            "" to "整篇被清空",
        ).forEach { (fixture, what) ->
            assertNotEquals(
                "尺子坏了：$what 这种情形读出来的尾部形状与干净文本相同，等于恒绿",
                clean,
                tailShapeOf(fixture),
            )
        }
    }

    @Test
    fun theFenceRulerReportsItsFixtures() {
        assertEquals("一个反引号代码块：2 行围栏、闭合、1 对", Triple(2, true, 1), fenceBones("```\n甲\n```\n"))
        assertEquals(
            "两个反引号代码块：4 行围栏、闭合、2 对",
            Triple(4, true, 2),
            fenceBones("```\n甲\n```\n\n```\n乙\n```\n"),
        )
        assertEquals("只开不合：1 行围栏、未闭合、0 对", Triple(1, false, 0), fenceBones("```\n甲\n"))
        assertEquals("开合记号不同（``` 开 ~~~ 合）：不闭合、0 对", Triple(2, false, 0), fenceBones("```\n甲\n~~~\n"))
        assertEquals("两个波浪号不算围栏", Triple(0, true, 0), fenceBones("~~\n甲\n"))
        assertEquals("四个反引号也算围栏（只比前三字符）", Triple(2, true, 1), fenceBones("````\n甲\n````\n"))
        assertEquals("缩进四格不再算围栏", Triple(0, true, 0), fenceBones("    ```\n甲\n    ```\n"))
        assertEquals("缩进三格仍算围栏", Triple(2, true, 1), fenceBones("   ```\n甲\n   ```\n"))
    }

    @Test
    fun theShortRefRulerReportsItsFixtures() {
        assertEquals(
            "`` `docs/03` `` 抽出一个键",
            listOf("docs/03"),
            shortDocRefs("见 `docs/03` 那一节"),
        )
        assertEquals(
            "`records/03` 也认（两个目录都算）",
            listOf("records/03"),
            shortDocRefs("写在 `records/03` 里"),
        )
        assertEquals(
            "同一行里两处短引用都抽出来、按出现次序",
            listOf("docs/07", "records/04"),
            shortDocRefs("`docs/07` 与 `records/04` 都记着"),
        )
        assertEquals(
            "带行号的 `docs/05:757` 不算短引用（那种归 DocCrossRefGuardTest）",
            emptyList<String>(),
            shortDocRefs("真值在 `docs/05:757`"),
        )
        assertEquals(
            "带文件名的 `docs/03-详细设计说明书.md` 不算短引用",
            emptyList<String>(),
            shortDocRefs("见 `docs/03-详细设计说明书.md`"),
        )
        val fabricated: List<String> = shortDocRefs("见 `docs/99`")
        assertEquals("虚构的编号也会被原样抽出来", listOf("docs/99"), fabricated)
        assertFalse(
            "尺子坏了：虚构的 docs/99 应当落在登记清单之外，否则清单形同虚设",
            fabricated.all { it in knownShortRefKeys },
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
        assertTrue("fenceLineFloor 必须为正", fenceLineFloor > 0)
        assertTrue("fenceLineFloor=$fenceLineFloor 高于实测的 $knownFenceLines 行围栏", fenceLineFloor <= knownFenceLines)
        assertTrue("fenceLineFloor=$fenceLineFloor 连实测的一半都不到", fenceLineFloor * 2 >= knownFenceLines)
        assertTrue("shortRefFloor 必须为正", shortRefFloor > 0)
        assertTrue("shortRefFloor=$shortRefFloor 高于实测的 $knownShortRefCount 处", shortRefFloor <= knownShortRefCount)
        assertTrue("shortRefFloor=$shortRefFloor 连实测的一半都不到", shortRefFloor * 2 >= knownShortRefCount)

        assertEquals("第 139 轮实测的字节数", 110_581, knownBytes)
        assertEquals("第 139 轮实测的 split 行数", 1_035, knownSplitLines)
        assertEquals("第 139 轮实测的围栏行数", 2, knownFenceLines)
        assertEquals("第 139 轮实测的围栏对数", 1, knownFencePairs)
        assertEquals("第 139 轮实测的短引用处数", 54, knownShortRefCount)
        assertEquals("第 139 轮实测的末尾空行数", 1, knownTrailingBlanks)
        assertEquals("手册有十二个 `## ` 标题", 12, knownHeadings.size)
        assertEquals("其中带编号的是十一个（0..10）", 11, knownNumberedSections.size)
        assertEquals("手册点名的受管文件是七份", 7, knownShortRefKeys.size)
    }

    private companion object {
        /** 底线：第 139 轮实测 110,581 字节，留出余量到 105,000。 */
        const val byteFloor: Int = 105_000

        /** 底线：第 139 轮实测 `split("\n")` 1,035 行，留出余量到 1,000。 */
        const val splitLineFloor: Int = 1_000

        /**
         * 底线：手册全篇只有**一对**围栏，所以这条只能与读数齐平 ——
         * 它钉的是「那一对还在、还闭合」，**不是**「代码块够多」。
         */
        const val fenceLineFloor: Int = 2

        /** 底线：第 139 轮实测 54 处短引用，留出余量到 52。 */
        const val shortRefFloor: Int = 52

        const val knownBytes: Int = 110_581

        const val knownSplitLines: Int = 1_035

        const val knownFenceLines: Int = 2

        const val knownFencePairs: Int = 1

        const val knownShortRefCount: Int = 54

        const val knownTrailingBlanks: Int = 1

        /** 第 139 轮实测的末句（原文逐字，含反引号与表格竖线）。 */
        const val expectedTailLine: String =
            """| 1.0 | 2026-11-21 | **第 15 轮首版。** 为兑现 `docs/07` 对风险 R-09 的承诺而写（`docs/07:604`）。写作方式是**先采证据再动笔**：在 AVD `Elder_API35`（Android 15 / API 35，1080×2400，密度 420，时区 GMT）上用 `uiautomator dump` 逐屏采集，共 **16 屏**，`TAP MISS` = 0、`DUMP FAILED` = 0，采集耗时 498 秒，日志 207 行 / 12,993 B（脚本在 `build/`，一次性使用后即弃）。据此写出第 1~7 节；第 8 节「做不到的事」依据 `docs/01` 的 GAP 表（含行号）、`records/04` 的验证数字与现场观测。本轮**未改动任何代码、未重跑任何测试**——所以文中引用的测试数字（JVM 342 例 / 设备 96 例 / APK 字节数）**仍是第 12 轮的**，第 13~15 轮三轮未复核。本轮记录的新事实：设备时区为 GMT（首页 07:31 显示「早上好」是正确的）；`requiredDays = 3`；打招呼间隔实测「30 分钟」；`docs/01:268` 关于「示例句按钮点不动」的悬案**已查明并作废**（上一轮脚本点中了说明文字）；起点卡「做完了」祝贺语存在**双句号瑕疵**（第 16 轮修）；首页第三行锚点只在「该说话」的两个分支出现，两个「安静」分支不读锚点（设备 + 源码双向确认）。 |"""

        /** 手册最后一条标题：无编号的那条修订记录。 */
        const val expectedLastHeading: String = "## 修订记录"

        /** 第 139 轮实测的十二个 `## ` 标题，逐字逐序。 */
        val knownHeadings: List<String> = listOf(
            "## 0. 给谁看，怎么对照屏幕读",
            "## 1. 第一次打开：三页引导",
            "## 2. 首页：打开就是这里",
            "## 3. 说两句：您说一句，它把听懂的念给您",
            "## 4. 玩一会儿",
            "## 5. 设置",
            "## 6. 它刻意不会做的事",
            "## 7. 常见问题",
            "## 8. 做不到的事（诚实清单）",
            "## 9. 本手册与其他文件的关系",
            "## 10. 一页速查对照卡",
            "## 修订记录",
        )

        /** 十一个带编号的小节编号，0 连到 10、不漏不重。 */
        val knownNumberedSections: List<Int> = (0..10).toList()

        /** 第 139 轮实测：手册点名的受管文件 = 这七份，双向相等。 */
        val knownShortRefKeys: Set<String> = setOf(
            "docs/01",
            "docs/03",
            "docs/06",
            "docs/07",
            "records/02",
            "records/03",
            "records/04",
        )
    }
}
