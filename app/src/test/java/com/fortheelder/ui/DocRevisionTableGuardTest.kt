package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「风险台账那张修订记录表，**行集本身**是不是还立着」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 修订记录这张表是本仓**唯一一处用行来记「哪一轮动过这份文件」**的地方：版本列（1.0、1.1、…）就是行序，
 * 日期列是那天，说明格开头写着「第 N 轮：」。第 98 轮立起的 [DocTableShapeGuardTest] 只管**形状**
 * （格数、分隔行、格内不许有裸竖线），第 110 轮立起的那两把读数尺子只管**字节 / 行数**；
 * 而**行集本身**——多没多一行、少没少一行、版本号有没有跳号或重号、日期会不会倒退、说明里的轮次号
 * 是不是排着队——一直没有机器看过一眼。
 *
 * 这笔账是第 122 轮亲手试出来的：那一轮往表里补了第 112 ~ 120 轮缺的**九行**，收尾时做反向变异，
 * 把九行里的一行删掉，**八把守卫一齐全绿**——八把尺子量的都是别的东西，行集掉了一行谁也不说话。
 * 本文档守卫就是这笔账的收口。
 *
 * ## 它做什么
 *
 * 认表的方式：全文件里逐字等于 `| 版本 | 日期 | 说明 |` 的行**恰好一行**，它下一行是分隔行，
 * 再往下连续的竖线行就是数据行；每行按**未被转义的竖线**切成五段（首尾两个空段 + 版本 / 日期 / 说明），
 * 所以格内那个转义竖线不会被当成格边界（切分口径与 [DocTableShapeGuardTest] 的切分器相同）。
 * 然后八条用例钉住：
 *
 * 1. [theRevisionTableHeaderAppearsExactlyOnceAndHasAtLeastTheKnownRows]：表头恰好一处、下面紧跟分隔行、
 *    数据行数不低于 [revisionRowFloor]、每行都切得出五段。
 * 2. [everyVersionCellContinuesOneStepFromThePrevious]：版本列从 `1.0` 起**逐行 +1**——跳号、重号、
 *    乱序都当场红。第 122 轮那次反向变异（删掉一行）红的就是这条。
 * 3. [everyDateCellIsAnIsoDateAndDatesNeverGoBackwards]：日期列逐字 `YYYY-MM-DD`，且**不许倒退**。
 * 4. [roundLabelsNeverGoBackwardsAndOnlyTheKnownGapLacksOne]：说明格里的「第 N 轮」——带标签的行数不低于
 *    [labeledRowFloor]、轮次号不许倒退、**缺标签的版本号集合必须是豁免表** [exemptUnlabeledVersions]
 *    **的子集**。豁免的版本 `1.40` 那一行的历史叙述本来就没写轮次号，本仓按 ADR-070「历史叙述不回改」
 *    不回补；但**豁免表只封「允许缺」，不封「必须缺」**——日后真给那一行补上标签，判据照样绿，
 *    豁免表里那个版本号留着也无妨（第 124 轮把口径从「集合相等」放宽成「子集」）。
 * 5. [theNewestRoundLabelNeverRunsAheadOfTheRoundRecords]：表里最大的轮次号**不许超前**于
 *    `records/01-迭代记录.md` 里已经写下的最大轮次号——**只判上限**。第 127 轮（ADR-104）把这道上限收紧
 *    成第 7 条的「相等」，这一条留着不拆：它排在前面，报红消息里两边现值都摆出来，读的人先看清差在哪。
 * 6. [theRealRepositoryStillShowsTheKnownShape]：拿真仓库现值对照（行数、首末版本、首末日期、
 *    下限常量不许是零或负），防「解析器坏了 ⇒ 零条目 ⇒ 全部通过」；并断言豁免表 [exemptTrailingRounds]
 *    **当场是空的**——留了出口，就要留一句证明出口没被用上。
 * 7. [theNewestRoundLabelKeepsUpWithTheRoundRecords]：表里最大的轮次号必须**等于**记录里已经写下的最大
 *    轮次号，除非那个轮次号被登记进 [exemptTrailingRounds]（ADR-104）。第 127 轮立这条之前，表尾停在旧
 *    标签上也能全绿：那张表的尾巴断了五笔（停在第 121 轮、记录已到第 126 轮），三把旧尺子一声不响。
 * 8. [everyRoundLabelPointsAtARecordedRound]：表里每一条带轮次标签的行，那个轮次号都必须在
 *    `records/01-迭代记录.md` 里有对应的「## 第 N 轮」标题（ADR-104）。封的是「标签写着一个记录里根本
 *    没有的轮次」这一族凭空记账。
 *
 * ## 它不保证什么
 *
 * - **不核内容**。说明格那一段长散文对不对（有没有讲清那一轮干了什么、证据链全不全）一个字也管不了。
 * - **豁免表是单向的**。第 124 轮起它只封「允许缺标签」，不封「必须缺」：给豁免那行补齐标签也算绿。
 *   代价是「那一行到底补没补」机器不再表态。这**不是**让步：补标签是好事、不回改也是好事，机器只该拦
 *   「别的行掉了标签」这一种坏法。放宽之前那条断言是「集合恰好相等」，于是补标签反而报红——一个假警报。
 * - **看不见表「中部」的缺笔**。第 127 轮立起「尾部当期」与「写下的轮次必须真实」两条，但**中间**漏掉
 *   一整轮（那一轮动了这份文件却没记）机器照样看不出来——尾部当期、写下的轮次都真，两把新尺子一声
 *   不响。本轮再次把「修订表行集 ↔ 轮次集合」的全表对账**判成不可行**：版本号与轮次号从 `1.0` / 第 14
 *   轮起就不是一对一（跨轮有跳号，第 49 轮之后直接到第 67 轮就是一处），所以判据只能落成「版本列连续 +
 *   日期列形态与单调 + 轮次列不倒退 + 上限不超前 + 尾部当期 + 轮次真实」这六条。
 * - **同轮只保证「两边最大轮次号相等」**，不等于每一轮都在表里留过行；上一条说的中部缺笔就落在这一层。
 * - **不管别处的表格**。这份文件里其余的表、别的文档里的表归 [DocTableShapeGuardTest]；
 *   本文档守卫只读这一张。
 * - **不管行尾**。读之前把 CRLF 拉平成 LF，工作区是哪种行尾都不影响判据。
 * - **表头必须逐字**。表头改一个字（多一个空格、换个词）这份守卫会认不出表而报「命中 0 处」，
 *   那是**故意的**：这类改法该有人来看一眼。
 * - **第 6 条是当轮读数，会随这份文件一起长大**。行数、首末版本与首末日期都是第 128 轮的实测值；
 *   哪一轮真去改这份文件，得顺手把这一条与四条下限同步过来（下限 [revisionRowFloor] 不会自动跟着长）。
 *
 * ## 结论
 *
 * **R-10 仍然是橙色**，残余面又少一块：从第 123 轮起，「修订记录表的行集连续不连续」在**这一族之内**
 * 有机器兜底；第 124 轮把这一族里唯一的假警报（豁免表双向绑死 ⇒ 补标签反而红）拆掉，口径收成「只许
 * 有据地缺」；第 127 轮又补上两条（ADR-104）——**尾部必须当期**、**写下的轮次必须真实存在**，并把当轮
 * 那一笔写进表里。这一族之外（说明格的内容、表中部的缺笔、别的表）照旧只能靠人工。
 *
 * 源码目录由 `app/build.gradle.kts:99` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。
 */
class DocRevisionTableGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val textCache = mutableMapOf<String, String>()
    private val blockCache = mutableMapOf<String, List<Pair<Int, String>>>()

    /** 去掉 CRLF 再切行：文档是 LF、仓库里也有 CRLF 的份，判据读同一份文本才不会被行尾带跑。 */
    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun docText(doc: String): String = textCache.getOrPut("doc:$doc") {
        val file = File(repoRoot, doc)
        assertTrue("文档不存在：$doc", file.isFile)
        readNormalized(file)
    }

    /**
     * 修订记录表的数据行：`行号（1 起）` 到 `该行原文`。
     * 认表的方式是「表头那一行往下隔一行开始、直到第一个不以竖线起头的行」；表头找不到时返回空表，
     * 由第 1 条用例报红，而不是在这里抛异常。
     */
    private fun revisionBlock(): List<Pair<Int, String>> = blockCache.getOrPut(REVISION_TABLE_DOC) {
        val lines = docText(REVISION_TABLE_DOC).split("\n")
        val header = lines.indexOf(REVISION_TABLE_HEADER)
        val out = mutableListOf<Pair<Int, String>>()
        if (header >= 0) {
            var index = header + 2
            while (index < lines.size && lines[index].startsWith("|")) {
                out += (index + 1) to lines[index]
                index++
            }
        }
        out
    }

    /** 按未被转义的竖线切行：`| a | b | c |` 切出五段（首尾各一个空段）。 */
    private fun cells(raw: String): List<String> = UNESCAPED_PIPE.split(raw)

    /** 取「版本 / 日期 / 说明」三格；切不出五段的行走第 1 条用例报红，这里的断言是兜底。 */
    private fun requireCells(raw: String): List<String> {
        val parts = cells(raw)
        assertTrue(
            "修订记录表的每一行都该切成五段（首尾空段 + 版本 / 日期 / 说明），这一行切出 ${parts.size} 段：$raw",
            parts.size == CELL_COUNT,
        )
        return parts
    }

    private fun versionOf(raw: String): String = requireCells(raw)[1].trim()

    private fun dateOf(raw: String): String = requireCells(raw)[2].trim()

    /** 说明格里第一个「第 N 轮」；没有就是 `null`（豁免表之外的行出现 `null` 由第 4 条用例报红）。 */
    private fun roundOf(raw: String): Int? =
        ROUND_LABEL.find(requireCells(raw)[3])?.groupValues?.get(1)?.toInt()

    @Test
    fun theRevisionTableHeaderAppearsExactlyOnceAndHasAtLeastTheKnownRows() {
        val lines = docText(REVISION_TABLE_DOC).split("\n")
        val headerLines = lines.withIndex()
            .filter { it.value == REVISION_TABLE_HEADER }
            .map { it.index + 1 }
        assertEquals(
            "这份文件里只该有一张修订记录表，表头那一行逐字是「$REVISION_TABLE_HEADER」；现命中 ${headerLines.size} 处，" +
                "行号：$headerLines ⇒ 表头被改了字样、或者文件里多了第二张修订表",
            1,
            headerLines.size,
        )

        val separator = lines[headerLines[0]]
        assertTrue(
            "表头下面一行必须是分隔行（形如「| --- | --- | --- |」），现是：$separator",
            SEPARATOR.matches(separator),
        )

        val block = revisionBlock()
        assertTrue(
            "修订记录表的数据行下限是 $revisionRowFloor 行（第 128 轮实测 $knownRowCount 行）；现 ${block.size} 行" +
                "⇒ 表被删空了，或者整块掉出了表格",
            block.size >= revisionRowFloor,
        )
        block.forEach { (line, raw) ->
            assertEquals(
                "第 $line 行切出来的段数不对：$raw",
                CELL_COUNT,
                cells(raw).size,
            )
        }
    }

    @Test
    fun everyVersionCellContinuesOneStepFromThePrevious() {
        val block = revisionBlock()
        assertTrue(
            "版本列要判「逐行 +1」，行数下限同样取 $revisionRowFloor（表被删空时这条不许变成空转）；现 ${block.size} 行",
            block.size >= revisionRowFloor,
        )

        val offenders = block.withIndex()
            .map { (index, row) -> Triple(row.first, versionOf(row.second), "1.$index") }
            .filter { (_, actual, expected) -> actual != expected }
            .map { (line, actual, expected) -> "第 $line 行的版本是「$actual」，按行序该是「$expected」" }

        assertTrue(
            "版本列必须从 1.0 起逐行 +1（跳号、重号、乱序都算坏）。第 128 轮实测是 1.0 … 1.${knownRowCount - 1}" +
                "，共 $knownRowCount 行；现 ${block.size} 行里有 ${offenders.size} 处不合，" +
                "头三处：\n" + offenders.take(OFFENDER_PREVIEW).joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun everyDateCellIsAnIsoDateAndDatesNeverGoBackwards() {
        val block = revisionBlock()
        assertTrue(
            "日期列要判形态与单调，行数下限同样取 $revisionRowFloor；现 ${block.size} 行",
            block.size >= revisionRowFloor,
        )

        val dated = block.map { it.first to dateOf(it.second) }
        val malformed = dated.filter { !ISO_DATE.matches(it.second) }
            .map { (line, value) -> "第 $line 行的日期格是「$value」" }
        assertTrue(
            "日期列每一格都必须逐字是四位年-两位月-两位日（`YYYY-MM-DD`）；现 ${malformed.size} 处不合：" +
                malformed.take(OFFENDER_PREVIEW).joinToString("；"),
            malformed.isEmpty(),
        )

        val backwards = (1 until dated.size)
            .filter { dated[it].second < dated[it - 1].second }
            .map { "第 ${dated[it].first} 行的 ${dated[it].second} 早于上一行的 ${dated[it - 1].second}" }
        assertTrue(
            "日期列不许倒退（表是按时间往下写的；补记旧轮次时若插错位置，这里就会红）；现 ${backwards.size} 处：" +
                backwards.take(OFFENDER_PREVIEW).joinToString("；"),
            backwards.isEmpty(),
        )
    }

    @Test
    fun roundLabelsNeverGoBackwardsAndOnlyTheKnownGapLacksOne() {
        val block = revisionBlock()
        val rows = block.map { Triple(it.first, versionOf(it.second), roundOf(it.second)) }

        val labeled = rows.filter { it.third != null }
        assertTrue(
            "带「第 N 轮」标签的行数下限是 $labeledRowFloor（第 128 轮实测 $labeledRowFloor / $knownRowCount）；" +
                "现 ${labeled.size} 行 ⇒ 说明格里的轮次标签被整片拿掉了",
            labeled.size >= labeledRowFloor,
        )

        val unlabeled = rows.filter { it.third == null }.map { it.second }.toSet()
        val unexpected = (unlabeled - exemptUnlabeledVersions).sorted()
        assertTrue(
            "缺「第 N 轮」标签的版本号必须都在豁免表里。豁免的是 $exemptUnlabeledVersions —— 那一行的历史叙述" +
                "当年就没写轮次号，本仓按 ADR-070「历史叙述不回改」不回补。这条**只封「允许缺」、不封「必须缺」**：" +
                "日后真给豁免那行补上标签，判据照样绿（第 124 轮之前这里断言的是「集合恰好相等」，" +
                "补标签反而会红，那是个假警报）。现缺标签的是 $unlabeled，其中不在豁免表里的有 $unexpected" +
                " ⇒ 这些行的说明格掉了标签，请补回来；若确是又一处有据豁免，再往 exemptUnlabeledVersions 里加。" +
                "（带标签的行数同样要够 $labeledRowFloor 行，现 ${labeled.size} 行。）",
            unexpected.isEmpty(),
        )

        val backwards = (1 until labeled.size)
            .filter { labeled[it].third!! < labeled[it - 1].third!! }
            .map { "第 ${labeled[it].first} 行标着第 ${labeled[it].third} 轮，却在第 ${labeled[it - 1].first} 行" +
                "（第 ${labeled[it - 1].third} 轮）后面" }
        assertTrue(
            "说明格里的轮次号不许倒退（补记旧轮次时要插在对应位置上，直接往表尾追加就会红）；" +
                "现 ${backwards.size} 处：" + backwards.take(OFFENDER_PREVIEW).joinToString("；"),
            backwards.isEmpty(),
        )
    }

    @Test
    fun theNewestRoundLabelNeverRunsAheadOfTheRoundRecords() {
        val rounds = revisionBlock().mapNotNull { roundOf(it.second) }
        assertTrue(
            "表里一行轮次标签也没有 ⇒ 这条判据失去了左侧的参照物",
            rounds.isNotEmpty(),
        )

        val recorded = recordedRounds()
        assertTrue(
            "$ROUND_RECORDS_DOC 里找不到任何「## 第 N 轮」标题 ⇒ 这条判据失去了右侧的参照物",
            recorded.isNotEmpty(),
        )

        assertTrue(
            "修订记录表里最大的轮次号（现 ${rounds.max()}）不许**超前**于迭代记录里已经写下的最大轮次号" +
                "（现 ${recorded.max()}）：表里出现一个记录里还没有的轮次，意味着有人先写了账、后补的正文。" +
                "这条**只判上限**；「尾部必须当期」那道更紧的口子由 theNewestRoundLabelKeepsUpWithTheRoundRecords " +
                "管着（第 127 轮 ADR-104 立），这一条留着不拆是让它先开火、两边现值一起摆出来。",
            rounds.max() <= recorded.max(),
        )
    }

    /**
     * 「尾部必须当期」（ADR-104，第 127 轮立）：表里最大的轮次号要**等于** `records/01-迭代记录.md` 里
     * 已经写下的最大轮次号。第 127 轮立这条之前，那张表的尾巴断了五笔（停在第 121 轮、记录已到第 126 轮）
     * 而既有的三把旧尺子一声不响 —— 它们量的是形状，没有一个量时效。
     *
     * 留了出口 [exemptTrailingRounds]，但 [theRealRepositoryStillShowsTheKnownShape] 里断言它**当场是空的**：
     * 某一轮真改了这份文件又确实不打算在表里留行时，得显式把那一轮的轮次号登记进去，把口头理由变成
     * 一行源码。出口越方便越容易变成绕过判据的默认通道，所以焊一句「现在没人用它」。
     */
    @Test
    fun theNewestRoundLabelKeepsUpWithTheRoundRecords() {
        val rounds = revisionBlock().mapNotNull { roundOf(it.second) }
        assertTrue(
            "表里一行轮次标签也没有 ⇒ 这条判据失去了左侧的参照物",
            rounds.isNotEmpty(),
        )

        val recorded = recordedRounds()
        assertTrue(
            "$ROUND_RECORDS_DOC 里找不到任何「## 第 N 轮」标题 ⇒ 这条判据失去了右侧的参照物",
            recorded.isNotEmpty(),
        )

        val newest = rounds.max()
        val newestRecorded = recorded.max()
        assertTrue(
            "修订记录表的**尾部必须当期**：表里最大的轮次号（现第 $newest 轮）要等于迭代记录里已经写下的" +
                "最大轮次号（现第 $newestRecorded 轮）。表落在记录后面就是一笔没还的账 —— 第 127 轮立这条之前，" +
                "那张表的尾巴断了五笔（停在第 121 轮、记录已到第 126 轮）而三把旧尺子全绿。" +
                "确有一轮改了这份文件又不打算在表里留行时，把那一轮的轮次号登记进 exemptTrailingRounds" +
                "（现是 $exemptTrailingRounds，为空）—— 豁免要留成一行源码给人看，不能只写在正文里。",
            newest == newestRecorded || newestRecorded in exemptTrailingRounds,
        )
    }

    /**
     * 「写下的轮次必须真实存在」（ADR-104，第 127 轮立）：表里每一条带轮次标签的行，那个轮次号都必须在
     * `records/01-迭代记录.md` 里有对应的「## 第 N 轮」标题。封的是「标签写着一个记录里根本没有的轮次」
     * 这一族凭空记账。只认带标签的行；那张表里一直有一行刻意不带标签（版本 `1.40`），它由
     * [roundLabelsNeverGoBackwardsAndOnlyTheKnownGapLacksOne] 的豁免表管着，两条各管一面、互不代劳。
     */
    @Test
    fun everyRoundLabelPointsAtARecordedRound() {
        val rows = revisionBlock().map { Triple(it.first, versionOf(it.second), roundOf(it.second)) }
        val labeled = rows.filter { it.third != null }
        assertTrue(
            "表里一条带轮次标签的行也没有 ⇒ 这条判据失去了被检对象",
            labeled.isNotEmpty(),
        )

        val recorded = recordedRounds().toSet()
        assertTrue(
            "$ROUND_RECORDS_DOC 里找不到任何「## 第 N 轮」标题 ⇒ 这条判据失去了参照物",
            recorded.isNotEmpty(),
        )

        val phantom = labeled.filter { it.third !in recorded }
        assertTrue(
            "表里每一条带轮次标签的行，那个轮次号都必须在 $ROUND_RECORDS_DOC 里有对应的「## 第 N 轮」标题：" +
                "写了一个记录里根本没有的轮次，就是一笔凭空的账（第 127 轮立这条之前三把旧尺子查不出来）。" +
                "现 ${labeled.size} 条带标签的行里有 ${phantom.size} 条指着不存在的轮次，逐条是：" +
                phantom.take(OFFENDER_PREVIEW).joinToString("；") { (line, version, round) ->
                    "第 $line 行（版本 $version）标着第 $round 轮，而记录里没有第 $round 轮"
                },
            phantom.isEmpty(),
        )
    }

    /** 迭代记录里已经写下的轮次号（保持文件里的出现顺序，不保证唯一）。 */
    private fun recordedRounds(): List<Int> =
        ROUND_HEADING.findAll(docText(ROUND_RECORDS_DOC)).map { it.groupValues[1].toInt() }.toList()

    @Test
    fun theRealRepositoryStillShowsTheKnownShape() {
        val block = revisionBlock()
        assertEquals(
            "第 128 轮实测这张表是 $knownRowCount 行；改这份文件的轮次要顺手把这一条与下限一起同步",
            knownRowCount,
            block.size,
        )
        assertEquals(knownFirstVersion, versionOf(block.first().second))
        assertEquals(knownNewestVersion, versionOf(block.last().second))
        assertEquals(knownFirstDate, dateOf(block.first().second))
        assertEquals(knownNewestDate, dateOf(block.last().second))

        assertTrue(
            "两条下限都必须是正数，否则「零条目 ⇒ 全绿」；现 revisionRowFloor=$revisionRowFloor、labeledRowFloor=$labeledRowFloor",
            revisionRowFloor > 0 && labeledRowFloor > 0,
        )
        assertTrue(
            "下限不许超过当轮实测值，否则前四条永远红；现 revisionRowFloor=$revisionRowFloor、" +
                "labeledRowFloor=$labeledRowFloor、实测 $knownRowCount",
            revisionRowFloor <= knownRowCount && labeledRowFloor <= knownRowCount,
        )
        assertEquals(
            "豁免表里只该有已知那一个版本；第 128 轮实测缺标签的恰好是 $knownExemptVersions",
            knownExemptVersions,
            exemptUnlabeledVersions,
        )
        assertTrue(
            "尾部豁免表 [exemptTrailingRounds] 留了就得当场是空的：它一被填上，「尾部必须当期」这条判据对" +
                "那一轮就作废了。现是 $exemptTrailingRounds —— 用它就得在这里看见它，不许悄悄塞进去。",
            exemptTrailingRounds.isEmpty(),
        )
    }

    private companion object {
        /** 受管文档：风险台账（修订记录表在这里）。 */
        const val REVISION_TABLE_DOC = "docs/07-风险管理计划.md"

        /** 右侧参照物：迭代记录里的轮次标题。 */
        const val ROUND_RECORDS_DOC = "records/01-迭代记录.md"

        const val REVISION_TABLE_HEADER = "| 版本 | 日期 | 说明 |"

        /** `| --- | --- | --- |` 这类分隔行。 */
        val SEPARATOR = Regex("""^\s*\|[\s:\-|]+\|\s*$""")

        /**
         * 未被转义的竖线 = 格边界。前面那个反斜杠的负向后顾与 [DocTableShapeGuardTest] 的切分器同口径，
         * 所以格内写的转义竖线不会被当成格边界。
         */
        val UNESCAPED_PIPE = Regex("""(?<!\\)\|""")

        /** 一行切出来的段数：首尾两个空段 + 版本 / 日期 / 说明。 */
        const val CELL_COUNT = 5

        val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

        /** 说明格里的轮次标签：取第一个命中。 */
        val ROUND_LABEL = Regex("""第\s*(\d+)\s*轮""")

        /** 迭代记录里的一轮标题。 */
        val ROUND_HEADING = Regex("""(?m)^## 第\s*(\d+)\s*轮""")

        /** 数据行下限 = 第 151 轮的实测值（下限只能写在落盘之前，落盘留下的余量如实记在 `records/01`）。 */
        const val revisionRowFloor = 94

        /** 带轮次标签的行数下限 = 第 151 轮的实测值（第 152 轮实测 95 / 94 行，下限照旧不动，余量如实记在 `records/01`）。 */
        const val labeledRowFloor = 93

        /** 有据豁免：这一行的历史叙述本来就没有轮次号，按 ADR-070 不回改。 */
        val exemptUnlabeledVersions = setOf("1.40")

        /**
         * 「尾部可以不当期」的豁免轮次号：空集就是「一轮都不许断档」。
         *
         * 留着它是为了给「某一轮真改了这份文件却不打算在表里留行」一个**要写进源码**的出口 ——
         * 口头理由不会留下痕迹，源码里的一个轮次号会（`theRealRepositoryStillShowsTheKnownShape`
         * 里断言它当场是空的，谁要用它，谁就得同时看见这一句）。
         */
        val exemptTrailingRounds = emptySet<Int>()

        /** 第 6 条对照用的当轮读数。 */
        const val knownRowCount = 97
        const val knownFirstVersion = "1.0"
        const val knownNewestVersion = "1.96"
        const val knownFirstDate = "2026-11-21"
        const val knownNewestDate = "2026-11-29"
        val knownExemptVersions = setOf("1.40")

        /** 报红时最多列几处不合，免得一条消息里塞进六十几行。 */
        const val OFFENDER_PREVIEW = 3
    }
}
