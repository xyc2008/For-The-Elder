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
 * 判据 17 —— 给 `docs/00`、`docs/01`、`docs/02`、`docs/04`、`docs/05` 这五份**活文档**补一把「形状尺子」。
 *
 * 判据 14 盯 `docs/08-部署与构建说明.md`、判据 15 盯 `docs/09-用户手册.md`、判据 16 盯 `docs/07-风险管理计划.md`，
 * 三份各有专属的形状判据；而上面这五份到第 141 轮为止**一条都没有**。它们共同的缺口是同一件事：
 * **不带行号的短引用**（`` `docs/0X` `` / `` `records/0X` ``）没有任何机器看着。
 * [DocCrossRefGuardTest] 只认 `` `docs/0X:NNN` `` 这种**带行号**的写法；[DocCurrentReadingGuardTest] 只在它自己的冻结清单上比字节数；
 * [DocCodeLineClaimGuardTest] 只管 README 里写下的代码体量。于是「标题被改名、正文被追加一整段、短引用指向一份不存在的文档」
 * 这三件事在这五份上都不会触发任何红。
 *
 * 九个用例分工：
 * 1. [theFiveDocsAreActuallyReadAndHaveTheirKnownBaseShape] —— 五份真在、真读得进来，字节/行数不低于底线，
 *    标题条数、围栏行数（且成对、且围栏里没有标题）、表块数、表行数、末尾空行数都与登记值相符。
 * 2. [theFiveDocsNumberedSectionsAreExactlyTheKnownOnes] —— 每份的 `## ` 标题逐字、逐序相等。
 * 3. [theFiveDocsTailsArePinnedSoAnyAppendShowsUp] —— 末尾那一句与末尾空行数钉死，**这一条才是真正抓纯追加的**。
 * 4. [everyShortDocReferenceInTheFiveDocsPointsAtOneRealFile] —— 每一处短引用都得恰好对上一份真文件；
 *    每一处全名引用（除 `docs/` 目录下那个 `*.md` 通配 token）也得是真文件。
 * 5. [theTableRulerReportsItsFixtures] —— 尺子自检：表格普查函数在 8 组人造片段上的读数。
 * 6. [theFenceRulerReportsItsFixtures] —— 尺子自检：围栏行函数与「围栏内标题」函数在人造片段上的读数。
 * 7. [theTailRulerReportsAppendsInsteadOfStayingGreen] —— 尺子自检：尾部形状函数在 6 组正例与 5 组反例上的读数。
 * 8. [theShortRefRulerReportsItsFixtures] —— 尺子自检：短引用与全名引用两个抽取函数在人造片段上的读数。
 * 9. [theFloorsAreSaneAndTheKnownReadingsHold] —— 底线自检：底线不许宽到形同虚设，记下的读数不许被悄悄改掉。
 *
 * ## 它不保证什么
 *
 * - **刻意不钉这五份的精确字节数与 md5**。正文每改一个字都要人跟着滚常量，滚漏一次判据就退化成「一律通过」的假绿。
 *   这一轮只钉形状（标题序列、尾部、围栏数、表块表行、短引用清单）。
 * - **不核正文说得对不对**。「这句话对不对、这张表填得准不准」全靠人读；这条判据只管形状。
 * - 断言用的是 [readNormalized]（先把 CRLF 归一成 LF）。本仓库 `.gitattributes` 是 `* text=auto` 且 `core.autocrlf=true`，
 *   `git checkout` 会把盘上的 LF 转成 CRLF；此时若断言「`\r` 个数为 0」会造出假红。字节数底线用原始字节，两种行尾都过。
 * - **第 3 条的尾锚一定会变**。这五份将来每次修订都会往末尾追加内容，那一刻第 3 条就会红，得人跟着滚 [DocSpec.knownTailLine]。
 *   这是**刻意的**：主编不知道自己的尾部有没有被追加，才是这条判据要防的事。
 * - **第 1 条里的围栏只钉精确值、不设下限**。`docs/00`、`docs/01` 的合法值就是 0，
 *   给围栏设下限只会造出一条形同虚设的假垫子。
 * - **第 4 条只管不带行号的短形引用与全名引用，而且比的是文件名前缀**。
 *   「`docs/03` 里那句话说对了没有」它一个字都不管；同一编号下若出现两份 `03-*.md`，第 4 条会红（歧义一律红，不挑一份了事）。
 * - 「文件真被读了」只靠一条 `isFile` 加底线撑着：文档被删掉会红，被换成一份同样体量的别的文件不会红
 *   （不过 [DocCurrentReadingGuardTest] 那份冻结清单会同时红，两张网互不替代）。
 * - **表块/表行是形状不是内容**。一张表被整体删掉、或在别处新增一张表，表块数会变；但表里某一行被改一个字不会红。
 *
 * ## 结论
 *
 * 第 142 轮实测（判据落地时的读数）：`docs/00-项目章程与需求计划书.md` 13,606 字节 / 194 行 / 10 个 `## ` 标题 / 0 行围栏 / 11 表块 81 表行 / 22 处短引用落在 14 个键上；`docs/01-需求规格说明书.md` 285,367 字节 / 602 行 / 9 个 `## ` 标题 / 0 行围栏 / 27 表块 262 表行 / 372 处短引用落在 12 个键上；`docs/02-概要设计说明书.md` 42,844 字节 / 458 行 / 10 个 `## ` 标题 / 10 行围栏 / 17 表块 202 表行 / 23 处短引用落在 7 个键上；`docs/04-数据库设计说明书.md` 39,359 字节 / 554 行 / 14 个 `## ` 标题 / 22 行围栏 / 22 表块 133 表行 / 6 处短引用落在 4 个键上；`docs/05-接口设计说明书.md` 61,797 字节 / 1,023 行 / 9 个 `## ` 标题 / 58 行围栏 / 35 表块 224 表行 / 22 处短引用落在 5 个键上。五份末尾都恰好 1 个空行。
 *
 * ⇒ 结论：这五份从第 142 轮起**有了形状兜底**（标题序列、尾部、围栏、表块表行、短引用四处），
 * 但「这五份讲得对不对」这件事**仍然没有任何机器判据**，`docs/07` 的风险 R-10 也仍然是橙色。
 */
class DocSkeletonGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val textCache: MutableMap<String, String> = mutableMapOf()

    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    /** `split("\n")` 口径：末尾带换行的文本会多出一个空串，与 `wc -l` 差 1。 */
    private fun linesOf(text: String): List<String> = text.split("\n")

    private fun textOf(spec: DocSpec): String =
        textCache.getOrPut(spec.path) { readNormalized(File(repoRoot, spec.path)) }

    private fun headingsOf(text: String): List<String> =
        linesOf(text).filter { it.startsWith("## ") }

    /** 分隔行：整行只有竖线、空白、冒号、连字符。用 [Regex.matches]（全匹配），不用锚点。 */
    private val tableSeparator: Regex = Regex("""\|[\s:\-|]+\|""")

    private data class TableCensus(val blocks: Int, val rows: Int)

    /** 表格普查：以竖线开头的连续行算一个表块；分隔行不计入行数，表头行计入。 */
    private fun tableCensus(text: String): TableCensus {
        var blocks: Int = 0
        var rows: Int = 0
        var inBlock: Boolean = false
        linesOf(text).forEach { line ->
            val stripped: String = line.trim()
            if (stripped.startsWith("|")) {
                if (!inBlock) {
                    blocks += 1
                    inBlock = true
                }
                if (!tableSeparator.matches(stripped)) {
                    rows += 1
                }
            } else {
                inBlock = false
            }
        }
        return TableCensus(blocks, rows)
    }

    /** 围栏行：去掉左侧空白后以三个反引号开头的行（只看反引号，`~~~` 不算）。返回 1 起的行号。 */
    private fun backtickFenceLines(text: String): List<Int> =
        linesOf(text).mapIndexedNotNull { index, line ->
            if (line.trimStart().startsWith("```")) index + 1 else null
        }

    /** 落在未闭合围栏里的 `## ` 标题：五份的合法值是空清单。 */
    private fun fencedHeadings(text: String): List<String> {
        val found: MutableList<String> = mutableListOf()
        var inFence: Boolean = false
        linesOf(text).forEach { line ->
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
            } else if (inFence && line.startsWith("## ")) {
                found += line
            }
        }
        return found
    }

    private data class TailShape(val lastText: String?, val trailingBlanks: Int)

    /** 尾部形状：最后一句非空正文 + 末尾连续空白行的条数。 */
    private fun tailShapeOf(text: String): TailShape {
        val lines: List<String> = linesOf(text)
        return TailShape(
            lastText = lines.lastOrNull { it.isNotBlank() },
            trailingBlanks = lines.takeLastWhile { it.isBlank() }.size,
        )
    }

    /** 只认`` `docs/0X` ``与`` `records/0X` ``：反引号里**只有**目录名加两位编号，多一个字都不算。 */
    private fun shortDocRefs(text: String): List<String> =
        Regex("""`((?:docs|records)/\d\d)`""").findAll(text).map { it.groupValues[1] }.toList()

    /** 只认`` `docs/xxx.md` ``与`` `records/xxx.md` ``：反引号里是一个 .md 文件名，后面多一个字都不算。 */
    private fun namedDocRefs(text: String): List<String> =
        Regex("""`((?:docs|records)/[^/`\r\n]+\.md)`""").findAll(text).map { it.groupValues[1] }.toList()

    /** 形如 `docs/03` 的键要对上**恰好一份** `03-*.md`；对上零份或多份都不合格。 */
    private fun filesMatchingShortRef(key: String): List<String> {
        val dir: File = File(repoRoot, key.substringBefore('/'))
        val prefix: String = key.substringAfter('/') + "-"
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith(prefix) }
            .map { it.name }
            .sorted()
    }

    private data class DocSpec(
        val path: String,
        val byteFloor: Int,
        val lineFloor: Int,
        val shortRefFloor: Int,
        val knownBytes: Int,
        val knownSplitLines: Int,
        val knownHeadings: List<String>,
        val knownFenceLines: Int,
        val knownTableBlocks: Int,
        val knownTableRows: Int,
        val knownTrailingBlanks: Int,
        val knownTailLine: String,
        val knownShortRefCount: Int,
        val knownShortRefKeys: Set<String>,
        val knownNamedRefKeys: Set<String>,
    )

    private companion object {

        /** 全名引用里唯一豁免的形状 token：glob 通配，本来就不对应任何单一文件。 */
        val namedRefExemptions: Set<String> = setOf("docs/*.md")

        val docs: List<DocSpec> = listOf(
            DocSpec(
                path = "docs/00-项目章程与需求计划书.md",
                byteFloor = 12_500,
                lineFloor = 180,
                shortRefFloor = 19,
                knownBytes = 13_606,
                knownSplitLines = 194,
                knownHeadings = listOf(
                    "## 1. 立项背景",
                    "## 2. 项目目标",
                    "## 3. 范围",
                    "## 4. 目标用户与画像",
                    "## 5. 关键假定与约束",
                    "## 6. 技术基线（Round 2 已落地）",
                    "## 7. 里程碑与迭代计划",
                    "## 8. 角色与职责",
                    "## 9. 验收准则（项目级）",
                    "## 10. 风险概要",
                ),
                knownFenceLines = 0,
                knownTableBlocks = 11,
                knownTableRows = 81,
                knownTrailingBlanks = 1,
                knownTailLine = "| R-03 | 意图识别在无网/低算力设备上不准 | 全部推理可离线；无法判断时一律走「不提供帮助」的安全分支，宁可少做也不误判 |",
                knownShortRefCount = 24,
                knownShortRefKeys = setOf("docs/00", "docs/01", "docs/02", "docs/03", "docs/04", "docs/05", "docs/06", "docs/07", "docs/08", "docs/09", "records/01", "records/02", "records/03", "records/04"),
                knownNamedRefKeys = setOf("docs/03-详细设计说明书.md", "docs/07-风险管理计划.md", "records/01-迭代记录.md", "records/02-需求变更记录.md", "records/03-架构决策记录.md", "records/04-质量与验证记录.md", "records/CHANGELOG.md"),
            ),
            DocSpec(
                path = "docs/01-需求规格说明书.md",
                byteFloor = 271_000,
                lineFloor = 570,
                shortRefFloor = 334,
                knownBytes = 285_367,
                knownSplitLines = 602,
                knownHeadings = listOf(
                    "## 1. 术语表",
                    "## 2. 需求编号规则",
                    "## 3. 用户故事",
                    "## 4. 功能需求",
                    "## 5. 非功能需求",
                    "## 6. 数据需求",
                    "## 7. 需求到轮次的追溯矩阵",
                    "## 8. 需求变更规则",
                    "## 9. 遗留与已知缺口（诚实清单）",
                ),
                knownFenceLines = 0,
                knownTableBlocks = 29,
                knownTableRows = 272,
                knownTrailingBlanks = 1,
                knownTailLine = "| GAP-09 | ~~`domain/play/PlayGame.kt:93` 的 KDoc 把「不写只有一个实现的接口」这件事标成「docs/02 约束 C-04」，但 `docs/02` 的 **C-04 实际是「用户点头之前不提供帮助」**；准确出处是 `docs/02` 第 2.2 节正文与 `docs/01` 的 **NFR-14**~~ | **已关闭（第 12 轮）**：`domain/play/PlayGame.kt` 的类 KDoc（`:209-218`）已改为「`docs/02` 第 2.2 节、`docs/01` NFR-14」，并写明「新规则要动三处…第 12 轮加『找出一样的』走的正是这条路径」；`docs/03` 第 3.5 节的引用口径与之统一。证据见 `records/04` 第 12.4 节 |",
                knownShortRefCount = 375,
                knownShortRefKeys = setOf("docs/00", "docs/01", "docs/02", "docs/03", "docs/05", "docs/06", "docs/07", "docs/09", "records/01", "records/02", "records/03", "records/04"),
                knownNamedRefKeys = setOf("docs/*.md", "docs/00-项目章程与需求计划书.md", "docs/09-用户手册.md", "records/01-迭代记录.md", "records/02-需求变更记录.md", "records/04-质量与验证记录.md", "records/CHANGELOG.md"),
            ),
            DocSpec(
                path = "docs/02-概要设计说明书.md",
                byteFloor = 40_500,
                lineFloor = 430,
                shortRefFloor = 20,
                knownBytes = 42_844,
                knownSplitLines = 458,
                knownHeadings = listOf(
                    "## 1. 设计目标与约束",
                    "## 2. 架构总览",
                    "## 3. 模块构成",
                    "## 4. 数据设计",
                    "## 5. 领域层设计",
                    "## 6. UI 层设计",
                    "## 7. 构建与依赖",
                    "## 8. 测试策略",
                    "## 9. 设计取舍与遗留",
                    "## 10. 与需求的对应",
                ),
                knownFenceLines = 10,
                knownTableBlocks = 17,
                knownTableRows = 210,
                knownTrailingBlanks = 1,
                knownTailLine = "| DR-11 用户当天要求安静 | `settings` 这一个 DataStore 文件里的 `quiet_day` 键；库里**没有**新表、没有新列，见 `docs/04` §12。上面 `DR-01~DR-08` 一行写于第 12 轮，未含 DR-09/DR-10/DR-11，本轮**不回溯改写**、只在此追加 |",
                knownShortRefCount = 30,
                knownShortRefKeys = setOf("docs/00", "docs/01", "docs/03", "docs/04", "records/02", "records/03", "records/04"),
                knownNamedRefKeys = setOf("docs/00-项目章程与需求计划书.md", "docs/01-需求规格说明书.md", "docs/03-详细设计说明书.md", "records/04-质量与验证记录.md"),
            ),
            DocSpec(
                path = "docs/04-数据库设计说明书.md",
                byteFloor = 37_000,
                lineFloor = 520,
                shortRefFloor = 5,
                knownBytes = 39_359,
                knownSplitLines = 554,
                knownHeadings = listOf(
                    "## 1. 存储全景",
                    "## 2. 选型与三条红线",
                    "## 3. 逻辑模型",
                    "## 4. 表设计",
                    "## 5. 索引设计",
                    "## 6. 编码与时间约定",
                    "## 7. 迁移与版本演进",
                    "## 8. 数据生命周期",
                    "## 9. 并发与一致性",
                    "## 10. DataStore 键设计",
                    "## 11. 刻意不做的结构",
                    "## 12. 需求追溯",
                    "## 13. 校验与未决",
                    "## 附：本文与代码的对齐方式",
                ),
                knownFenceLines = 22,
                knownTableBlocks = 22,
                knownTableRows = 133,
                knownTrailingBlanks = 1,
                knownTailLine = "本文按**第 12 轮的代码现状**写成；代码里的列与本文冲突时，以代码为准，并且应当**先改本文再改代码**——`docs/03` 已经立过同一条规矩（新规则的改动要先改文档再动代码）。",
                knownShortRefCount = 6,
                knownShortRefKeys = setOf("docs/00", "docs/01", "docs/03", "records/04"),
                knownNamedRefKeys = setOf("docs/01-需求规格说明书.md", "docs/02-概要设计说明书.md", "docs/03-详细设计说明书.md", "records/03-架构决策记录.md", "records/04-质量与验证记录.md"),
            ),
            DocSpec(
                path = "docs/05-接口设计说明书.md",
                byteFloor = 58_500,
                lineFloor = 970,
                shortRefFloor = 19,
                knownBytes = 61_797,
                knownSplitLines = 1_023,
                knownHeadings = listOf(
                    "## 1. 文档目的与范围",
                    "## 2. 外部接口",
                    "## 3. 内部接口 · 数据层",
                    "## 4. 内部接口 · 领域层",
                    "## 5. 内部接口 · 界面层与 ViewModel",
                    "## 6. 错误与降级约定",
                    "## 7. 接口变更与兼容",
                    "## 8. 接口清单速查",
                    "## 修订记录",
                ),
                knownFenceLines = 58,
                knownTableBlocks = 35,
                knownTableRows = 224,
                knownTrailingBlanks = 1,
                knownTailLine = "代码里的签名与本文冲突时，以代码为准，并且应当**先改本文再改代码**。",
                knownShortRefCount = 22,
                knownShortRefKeys = setOf("docs/02", "docs/03", "docs/04", "docs/09", "records/03"),
                knownNamedRefKeys = setOf("docs/01-需求规格说明书.md", "docs/02-概要设计说明书.md", "docs/03-详细设计说明书.md", "docs/04-数据库设计说明书.md"),
            ),
        )

        val knownShortRefKeysOfAll: Set<String> = docs.flatMap { it.knownShortRefKeys }.toSet()

        val knownNamedRefKeysOfAll: Set<String> = docs.flatMap { it.knownNamedRefKeys }.toSet()

        /** 登记在 DocSpec 里的 knownBytes，第二遍写成字面表，防单点被改。 */
        val knownBytesByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 13_606,
            "docs/01-需求规格说明书.md" to 285_367,
            "docs/02-概要设计说明书.md" to 42_844,
            "docs/04-数据库设计说明书.md" to 39_359,
            "docs/05-接口设计说明书.md" to 61_797,
        )

        /** 登记在 DocSpec 里的 knownSplitLines。 */
        val knownSplitLinesByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 194,
            "docs/01-需求规格说明书.md" to 602,
            "docs/02-概要设计说明书.md" to 458,
            "docs/04-数据库设计说明书.md" to 554,
            "docs/05-接口设计说明书.md" to 1_023,
        )

        /** 登记在 DocSpec 里的标题条数。 */
        val knownHeadingCountByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 10,
            "docs/01-需求规格说明书.md" to 9,
            "docs/02-概要设计说明书.md" to 10,
            "docs/04-数据库设计说明书.md" to 14,
            "docs/05-接口设计说明书.md" to 9,
        )

        /** 登记在 DocSpec 里的围栏行数。 */
        val knownFenceLinesByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 0,
            "docs/01-需求规格说明书.md" to 0,
            "docs/02-概要设计说明书.md" to 10,
            "docs/04-数据库设计说明书.md" to 22,
            "docs/05-接口设计说明书.md" to 58,
        )

        /** 登记在 DocSpec 里的表块数。 */
        val knownTableBlocksByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 11,
            "docs/01-需求规格说明书.md" to 29,
            "docs/02-概要设计说明书.md" to 17,
            "docs/04-数据库设计说明书.md" to 22,
            "docs/05-接口设计说明书.md" to 35,
        )

        /** 登记在 DocSpec 里的表行数（表头行计入）。 */
        val knownTableRowsByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 81,
            "docs/01-需求规格说明书.md" to 272,
            "docs/02-概要设计说明书.md" to 210,
            "docs/04-数据库设计说明书.md" to 133,
            "docs/05-接口设计说明书.md" to 224,
        )

        /** 登记在 DocSpec 里的末尾空行数。 */
        val knownTrailingBlanksByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 1,
            "docs/01-需求规格说明书.md" to 1,
            "docs/02-概要设计说明书.md" to 1,
            "docs/04-数据库设计说明书.md" to 1,
            "docs/05-接口设计说明书.md" to 1,
        )

        /** 登记在 DocSpec 里的短引用键数。 */
        val knownShortRefKeyCountByDoc: Map<String, Int> = mapOf(
            "docs/00-项目章程与需求计划书.md" to 14,
            "docs/01-需求规格说明书.md" to 12,
            "docs/02-概要设计说明书.md" to 7,
            "docs/04-数据库设计说明书.md" to 4,
            "docs/05-接口设计说明书.md" to 5,
        )

    }

    @Test
    fun theFiveDocsAreActuallyReadAndHaveTheirKnownBaseShape() {
        assertEquals("登记的五份活文档", 5, docs.size)
        docs.forEach { spec ->
            val file: File = File(repoRoot, spec.path)
            assertTrue("${spec.path} 不在仓库里（文档被删或被改名）", file.isFile)
            val bytes: Int = file.readBytes().size
            assertTrue(
                "${spec.path} 只有 $bytes 字节，低于底线 ${spec.byteFloor} —— 内容被大幅删节了",
                bytes >= spec.byteFloor,
            )
            val text: String = textOf(spec)
            assertTrue("${spec.path} 读进来是空白的", text.isNotBlank())
            val lines: Int = linesOf(text).size
            assertTrue(
                "${spec.path} 只有 $lines 行（split 口径），低于底线 ${spec.lineFloor}",
                lines >= spec.lineFloor,
            )
            assertEquals(
                "${spec.path} 的 `## ` 标题条数变了（左=登记值，右=实际）",
                spec.knownHeadings.size,
                headingsOf(text).size,
            )
            val fences: Int = backtickFenceLines(text).size
            assertEquals("${spec.path} 的围栏行数变了", spec.knownFenceLines, fences)
            assertEquals(
                "${spec.path} 的围栏行数是奇数，说明有一处围栏没闭合",
                0,
                fences % 2,
            )
            assertTrue(
                "${spec.path} 有标题落在了未闭合的围栏里：${fencedHeadings(text)}",
                fencedHeadings(text).isEmpty(),
            )
            assertEquals(
                "${spec.path} 的表块数变了（左=登记值，右=实际）",
                spec.knownTableBlocks,
                tableCensus(text).blocks,
            )
            assertEquals(
                "${spec.path} 的表行数变了（表头行计入）",
                spec.knownTableRows,
                tableCensus(text).rows,
            )
            assertEquals(
                "${spec.path} 末尾的空行数变了",
                spec.knownTrailingBlanks,
                tailShapeOf(text).trailingBlanks,
            )
        }
    }

    @Test
    fun theFiveDocsNumberedSectionsAreExactlyTheKnownOnes() {
        docs.forEach { spec ->
            assertEquals(
                "${spec.path} 的 `## ` 标题序列与登记值不一致 —— 标题被改名、被删、被换序，或者新加了一节",
                spec.knownHeadings,
                headingsOf(textOf(spec)),
            )
        }
    }

    @Test
    fun theFiveDocsTailsArePinnedSoAnyAppendShowsUp() {
        docs.forEach { spec ->
            val shape: TailShape = tailShapeOf(textOf(spec))
            assertEquals(
                "${spec.path} 的末句与登记值不一致 —— 末尾被追加或被改写了",
                spec.knownTailLine,
                shape.lastText,
            )
            assertEquals(
                "${spec.path} 末尾空行数与登记值不一致",
                spec.knownTrailingBlanks,
                shape.trailingBlanks,
            )
        }
    }

    @Test
    fun everyShortDocReferenceInTheFiveDocsPointsAtOneRealFile() {
        docs.forEach { spec ->
            val text: String = textOf(spec)
            val shortRefs: List<String> = shortDocRefs(text)
            assertTrue(
                "${spec.path} 里的短引用只剩 ${shortRefs.size} 处，少于底线 ${spec.shortRefFloor}",
                shortRefs.size >= spec.shortRefFloor,
            )
            assertEquals(
                "${spec.path} 短引用出现次数与登记值不一致",
                spec.knownShortRefCount,
                shortRefs.size,
            )
            val keys: List<String> = shortRefs.distinct().sorted()
            assertEquals(
                "${spec.path} 点名的短引用键与登记清单不一致（左=登记值，右=实际）",
                spec.knownShortRefKeys.sorted(),
                keys,
            )
            assertEquals(
                "${spec.path} 登记清单与去重后的实际键数不一致",
                spec.knownShortRefKeys.size,
                keys.size,
            )
            keys.forEach { key ->
                val matched: List<String> = filesMatchingShortRef(key)
                assertEquals(
                    "${spec.path} 点名的 $key 在仓库里没有对上**恰好一份**文件（对上 ${matched.size} 份：$matched）",
                    1,
                    matched.size,
                )
            }
            val namedRefs: List<String> = namedDocRefs(text)
            val namedKeys: List<String> = namedRefs.distinct().sorted()
            assertEquals(
                "${spec.path} 点名的全名引用与登记清单不一致（左=登记值，右=实际）",
                spec.knownNamedRefKeys.sorted(),
                namedKeys,
            )
            namedKeys.forEach { token ->
                if (token in namedRefExemptions) {
                    return@forEach
                }
                assertTrue(
                    "${spec.path} 点名的 $token 在仓库里不是一份真文件",
                    File(repoRoot, token).isFile,
                )
            }
        }
        assertTrue("五份合起来的短引用清单是空的，尺子坏了", knownShortRefKeysOfAll.isNotEmpty())
        assertTrue("五份合起来的全名引用清单是空的，尺子坏了", knownNamedRefKeysOfAll.isNotEmpty())
    }

    @Test
    fun theTableRulerReportsItsFixtures() {
        assertEquals("空文本没有表", TableCensus(0, 0), tableCensus(""))
        assertEquals("没有竖线的散文不算表", TableCensus(0, 0), tableCensus("甲\n乙\n"))
        assertEquals(
            "表头 + 分隔 + 一行数据：1 个表块、2 行（表头计入、分隔行不计入）",
            TableCensus(1, 2),
            tableCensus("| a | b |\n| --- | --- |\n| 1 | 2 |\n"),
        )
        assertEquals(
            "只有表头与分隔行：1 个表块、1 行",
            TableCensus(1, 1),
            tableCensus("| a | b |\n| --- | --- |\n"),
        )
        assertEquals(
            "中间隔了散文的两张表：2 个表块、4 行",
            TableCensus(2, 4),
            tableCensus("| a |\n| --- |\n| 1 |\n\n散文\n\n| b |\n| --- |\n| 2 |\n"),
        )
        assertEquals(
            "缩进的表行也算表行",
            TableCensus(1, 2),
            tableCensus("  | a |\n  | --- |\n  | 1 |\n"),
        )
        assertEquals(
            "`|:--|--:|` 这种对齐写法仍是分隔行",
            TableCensus(1, 2),
            tableCensus("| a | b |\n|:--|--:|\n| 1 | 2 |\n"),
        )
        assertEquals(
            "以竖线开头但不闭合的行仍算表块与表行",
            TableCensus(1, 1),
            tableCensus("| a\n"),
        )
    }

    @Test
    fun theFenceRulerReportsItsFixtures() {
        assertEquals(
            "一个反引号代码块：第 1、3 行是围栏",
            listOf(1, 3),
            backtickFenceLines("```\n甲\n```\n"),
        )
        assertEquals("空文本没有围栏", emptyList<Int>(), backtickFenceLines(""))
        assertEquals(
            "两个代码块：第 1、3、5、7 行是围栏",
            listOf(1, 3, 5, 7),
            backtickFenceLines("```\n甲\n```\n\n```\n乙\n```\n"),
        )
        assertEquals("只开不合：只剩第 1 行", listOf(1), backtickFenceLines("```\n甲\n"))
        assertEquals("波浪号不算围栏", emptyList<Int>(), backtickFenceLines("~~~\n甲\n~~~\n"))
        assertEquals(
            "四个反引号也算围栏（只看前三个）",
            listOf(1, 3),
            backtickFenceLines("````\n甲\n````\n"),
        )
        assertEquals(
            "缩进三格仍算围栏",
            listOf(1, 3),
            backtickFenceLines("   ```\n甲\n   ```\n"),
        )
        assertEquals(
            "行内反引号不算围栏",
            emptyList<Int>(),
            backtickFenceLines("这是 `行内` 反引号\n"),
        )
        assertEquals(
            "围栏里的 `## ` 行会被抽出来",
            listOf("## 甲"),
            fencedHeadings("```\n## 甲\n```\n"),
        )
        assertEquals("围栏外的 `## ` 行不算", emptyList<String>(), fencedHeadings("## 甲\n"))
        assertEquals(
            "只开不合时，其后所有 `## ` 行都算在围栏里",
            listOf("## 甲", "## 乙"),
            fencedHeadings("```\n## 甲\n## 乙\n"),
        )
        assertEquals(
            "围栏合上之后的 `## ` 行不算",
            listOf("## 甲"),
            fencedHeadings("```\n## 甲\n```\n## 乙\n"),
        )
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
    fun theShortRefRulerReportsItsFixtures() {
        assertEquals(
            "`` `docs/03` `` 抽出一个键",
            listOf("docs/03"),
            shortDocRefs("见 `docs/03` 那一节"),
        )
        assertEquals(
            "`records/04` 也认（两个目录都算）",
            listOf("records/04"),
            shortDocRefs("写在 `records/04` 里"),
        )
        assertEquals(
            "同一行里两处短引用都抽出来、按出现次序",
            listOf("docs/07", "records/04"),
            shortDocRefs("`docs/07` 与 `records/04` 都记着"),
        )
        assertEquals(
            "`` `docs/05:757` `` 是带行号的写法，短形不认（归 DocCrossRefGuardTest）",
            emptyList<String>(),
            shortDocRefs("真值在 `docs/05:757`"),
        )
        assertEquals(
            "`` `docs/03-详细设计说明书.md` `` 是全名，短形不认",
            emptyList<String>(),
            shortDocRefs("见 `docs/03-详细设计说明书.md`"),
        )
        val fabricated: List<String> = shortDocRefs("见 `docs/99`")
        assertEquals("不存在的编号照样被抽出来（判据靠比对清单发现它）", listOf("docs/99"), fabricated)
        assertFalse(
            "尺子坏了：一个编造的 `` `docs/99` `` 竟然命中了登记清单",
            fabricated.all { it in knownShortRefKeysOfAll },
        )
        assertEquals(
            "`` `docs/03-详细设计说明书.md` `` 被全名抽取函数认出来",
            listOf("docs/03-详细设计说明书.md"),
            namedDocRefs("见 `docs/03-详细设计说明书.md`"),
        )
        assertEquals(
            "带行号的全名写法不认",
            emptyList<String>(),
            namedDocRefs("真值在 `docs/05-接口设计说明书.md:757`"),
        )
        assertEquals(
            "`` `docs/*.md` `` 这种 glob 会被抽出来，靠豁免清单放行",
            listOf("docs/*.md"),
            namedDocRefs("见 `docs/*.md` 任意一份"),
        )
        assertTrue("`docs/*.md` 必须在豁免清单里", "docs/*.md" in namedRefExemptions)
        assertTrue(
            "豁免清单里混进了一个真文件，那就不是形状豁免而是漏网",
            namedRefExemptions.none { File(repoRoot, it).isFile },
        )
    }

    @Test
    fun theFloorsAreSaneAndTheKnownReadingsHold() {
        assertEquals("登记的五份活文档", 5, docs.size)
        docs.forEach { spec ->
            assertTrue("${spec.path} 的 byteFloor 必须是正数", spec.byteFloor > 0)
            assertTrue("${spec.path} 的 byteFloor 高过了实测字节数", spec.byteFloor <= spec.knownBytes)
            assertTrue(
                "${spec.path} 的 byteFloor 低于实测字节数的一半，形同虚设",
                spec.byteFloor * 2 >= spec.knownBytes,
            )
            assertTrue("${spec.path} 的 lineFloor 必须是正数", spec.lineFloor > 0)
            assertTrue("${spec.path} 的 lineFloor 高过了实测行数", spec.lineFloor <= spec.knownSplitLines)
            assertTrue(
                "${spec.path} 的 lineFloor 低于实测行数的一半，形同虚设",
                spec.lineFloor * 2 >= spec.knownSplitLines,
            )
            assertTrue("${spec.path} 的 shortRefFloor 必须是正数", spec.shortRefFloor > 0)
            assertTrue(
                "${spec.path} 的 shortRefFloor 高过了实测短引用处数",
                spec.shortRefFloor <= spec.knownShortRefCount,
            )
            assertTrue(
                "${spec.path} 的 shortRefFloor 低于实测处数的一半，形同虚设",
                spec.shortRefFloor * 2 >= spec.knownShortRefCount,
            )
        }
        assertEquals(
            "knownBytes 字面表与 DocSpec 里的值不一致（左=字面表，右=DocSpec）",
            knownBytesByDoc,
            docs.associate { it.path to it.knownBytes },
        )
        assertEquals(
            "knownSplitLines 字面表与 DocSpec 里的值不一致",
            knownSplitLinesByDoc,
            docs.associate { it.path to it.knownSplitLines },
        )
        assertEquals(
            "knownHeadingCount 字面表与 DocSpec 里的值不一致",
            knownHeadingCountByDoc,
            docs.associate { it.path to it.knownHeadings.size },
        )
        assertEquals(
            "knownFenceLines 字面表与 DocSpec 里的值不一致",
            knownFenceLinesByDoc,
            docs.associate { it.path to it.knownFenceLines },
        )
        assertEquals(
            "knownTableBlocks 字面表与 DocSpec 里的值不一致",
            knownTableBlocksByDoc,
            docs.associate { it.path to it.knownTableBlocks },
        )
        assertEquals(
            "knownTableRows 字面表与 DocSpec 里的值不一致",
            knownTableRowsByDoc,
            docs.associate { it.path to it.knownTableRows },
        )
        assertEquals(
            "knownTrailingBlanks 字面表与 DocSpec 里的值不一致",
            knownTrailingBlanksByDoc,
            docs.associate { it.path to it.knownTrailingBlanks },
        )
        assertEquals(
            "knownShortRefKeyCount 字面表与 DocSpec 里的值不一致",
            knownShortRefKeyCountByDoc,
            docs.associate { it.path to it.knownShortRefKeys.size },
        )
    }
}
