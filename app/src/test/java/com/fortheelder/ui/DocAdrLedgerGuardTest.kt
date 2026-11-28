package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「架构决定台账（`records/03-架构决策记录.md`）的本体还立着没有」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 这份文件是本仓**唯一一处用 `## ADR-NNN` 块来记「哪一轮定了什么」**的地方：编号就是块序，标题后面缀着
 * 「（第 NNN 轮）」，块内按「背景 / 决策 / 理由与代价 / 验证 / 已知边界」五段写。它从第 24 轮起就在长，
 * 一直长到 ADR-089；**第 112 轮起断了十四轮**——第 112 ~ 125 轮每一轮都往 `records/01`、`records/04`、
 * `records/CHANGELOG` 与两份设计文档里写了正文，**唯独没往这份台账里开一个编号**，而这件事没有任何一把
 * 尺子看着：九把文档尺子量的是格子形状、标题次序、字节与行数、交叉引用、行号越界与否、符号声称、屏幕文案、
 * 修订记录表的行集自洽——没有一把读这份台账。
 *
 * 第 126 轮把断掉的那十四条补齐（ADR-090 ~ ADR-103），并顺手立起这份守卫，让「台账本体」从此有机器兜底。
 *
 * ## 它做什么
 *
 * 认块的方式：把文件按行切好，**每一条 h2（`^## ` 开头的行）都该是一条 `## ADR-NNN…` 决定块**，
 * 块体从该标题行起、到下一个 h2 行或文件末尾止。取编号的正则是 `^## ADR-(\d+)(?:\s|：)`——
 * **分隔符必须是「空白或全角冒号」两者皆可**：第 111 轮及更早的四条（ADR-059 ~ ADR-062）的标题
 * 当年是用空格分隔的，若把「：」当必需分隔符，这四块会被漏掉，于是「编号连号」那条会把它们当成缺号
 * 报红——一个假警报。这是第 126 轮量台账时当场发现的坑。
 *
 * 然后七条用例钉住：
 *
 * 1. [theAdrLedgerIsActuallyReadAndHasAtLeastTheKnownBlocks]：文件读得到、每一条 h2 都认得出编号、
 *    块数不低于 [adrBlockFloor]，且下限常量是正数、不超过当轮实测值（防「解析器坏了 ⇒ 零条目 ⇒ 全绿」）。
 * 2. [everyAdrNumberIsContinuousWithoutGapsOrDuplicates]：编号从 1 起**逐块 +1**——跳号、重号、乱序
 *    都当场红。删掉一整块、把 `095` 改成 `094`、末尾塞一条 `ADR-999`，红的都是这条。
 * 3. [everyAdrSinceTheNinetiethCarriesItsRoundSuffix]：[adrLabelFloor]（90）及以后的每一块，标题必须带
 *    「（第 NNN 轮）」尾标。更早的块不表态——第 111 轮之前那八十九条里，尾标与标题体例都没统一过。
 * 4. [theNewestAdrRoundNeverRunsAheadOfTheRoundRecords]：标题尾标里最大的轮次号不低于
 *    [adrNewestRoundFloor]，且**不许超前**于 `records/01-迭代记录.md` 里已经写下的最大轮次号——
 *    **只判上限，不要求相等**。
 * 5. [everyAdrSinceTheNinetiethHasTheFiveKnownSections]：[adrLabelFloor] 及以后的每一块，块体内必须
 *    逐字含有 [SECTIONS] 里那五个 `###` 小标题（按前缀认，「验证」那一段带轮次后缀）。
 * 6. [everyAdrSinceTheNinetiethHasEnoughBody]：[adrLabelFloor] 及以后的每一块，非空行数不低于
 *    [adrBodyFloor]。
 * 7. [theRealRepositoryStillShowsTheKnownShape]：拿真仓库现值对照（块数、首末编号、最新轮次号、
 *    五段小标题的条数），并把「下限不许为零、不许超过实测」这几条一起收在这里。
 *
 * ## 它不保证什么
 *
 * - **不核内容**。决定写得对不对、理由充不充分、代价交代没交代，一个字也管不了；块体够长只说明有人写了。
 * - **只对 ADR-090 及以后表态**。第 5、6 两条拿五段与体量量的是本轮新补的那十四条。更早那八十九条的形状
 *   五花八门（块内 `###` 小标题的条数从 0 条到 24 条都有），拿一把尺子去量它们只会造出一堆假警报，
 *   本仓按 ADR-070「历史叙述不回改」不去回改，也就没有资格拿新尺子去量旧块。
 * - **不核「该不该有这一条」**。某一轮明明开了新决定却忘了往台账里写编号，机器看不出来——它只认得
 *   「台账自己连号」。这与修订记录表那把尺子同一族：**行集连续不等于行集完整**。第 112 ~ 125 轮断档
 *   十四轮这件事之所以要人发现，正是这个残余面的现场凭据。
 * - **不核尾标里的轮次对不对**。第 3 条只要求带上「（第 NNN 轮）」这个形态，第 4 条只在**最新一条**上
 *   判下限与上限；中间某一条把轮次号写错一位，机器不表态。
 * - **不管别处的 h2、别的文档**。别的文档里的标题归 [DocHeadingStructureGuardTest]；本守卫只读这一份。
 * - **不管行尾**。读之前把 CRLF 拉平成 LF，工作区是哪种行尾都不影响判据。
 * - **第 7 条是当轮读数，会随这份文件一起长大**。块数、首末编号与最新轮次号都是第 128 轮的实测值；
 *   哪一轮真往台账里开新编号，得顺手把这一条同步过来（下限 [adrBlockFloor] 不会自动跟着长——第 128 轮
 *   开 ADR-105 时它是人工从 104 一并提到 105 的，这一步机器不代劳）。
 *
 * ## 结论
 *
 * **R-10 仍然是橙色**，残余面又少一块：从第 126 轮起，「台账本体还在不在、编号连不连号、新补的那些块
 * 是不是都按五段写」在**这一族之内**有机器兜底。这一族之外（内容对不对、某一轮该不该开编号、尾标里的
 * 轮次准不准）照旧只能靠人工。
 *
 * 源码目录由 `app/build.gradle.kts:99` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。
 */
class DocAdrLedgerGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val textCache = mutableMapOf<String, String>()
    private val blockCache = mutableMapOf<String, List<AdrBlock>>()

    /** 去掉 CRLF 再切行：文档是 LF、仓库里也有 CRLF 的份，判据读同一份文本才不会被行尾带跑。 */
    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun docText(doc: String): String = textCache.getOrPut("doc:$doc") {
        val file = File(repoRoot, doc)
        assertTrue("文档不存在：$doc", file.isFile)
        readNormalized(file)
    }

    /**
     * 台账里的决定块：`编号（认不出时是 `null`）`、`标题所在行号（1 起）`、`标题原文`、`块体各行`。
     * 块体从标题行起到下一个 h2 行或文件末尾止；标题认不出编号时照样成块（编号记 `null`），
     * 由第 1 条用例报红，而不是在这里抛异常。
     */
    private fun adrBlocks(): List<AdrBlock> = blockCache.getOrPut(ADR_LEDGER_DOC) {
        val lines = docText(ADR_LEDGER_DOC).split("\n")
        val heads = lines.withIndex().filter { H2.containsMatchIn(it.value) }
        heads.mapIndexed { index, (lineIndex, raw) ->
            val end = if (index + 1 < heads.size) heads[index + 1].index else lines.size
            AdrBlock(
                number = ADR_HEADING.find(raw)?.groupValues?.get(1)?.toInt(),
                line = lineIndex + 1,
                heading = raw,
                body = lines.subList(lineIndex, end),
            )
        }
    }

    /** 只对 [adrLabelFloor] 及以后表态的那一族；第 3、4、5、6 条都先取这一段再判。 */
    private fun labeledTail(): List<AdrBlock> =
        adrBlocks().filter { (it.number ?: 0) >= adrLabelFloor }

    private fun roundOf(block: AdrBlock): Int? =
        ROUND_SUFFIX.find(block.heading)?.groupValues?.get(1)?.toInt()

    private data class AdrBlock(val number: Int?, val line: Int, val heading: String, val body: List<String>)

    @Test
    fun theAdrLedgerIsActuallyReadAndHasAtLeastTheKnownBlocks() {
        val blocks = adrBlocks()
        assertTrue(
            "$ADR_LEDGER_DOC 里读不到任何决定块 ⇒ 这份守卫失去了判据对象",
            blocks.isNotEmpty(),
        )

        val unnumbered = blocks.filter { it.number == null }
        assertTrue(
            "这份文件里每一条 h2 都该是一条 `## ADR-NNN…` 决定块（编号与标题之间用空格或全角冒号分隔都认）；" +
                "现认不出编号的有 ${unnumbered.size} 条，头几条：\n" +
                unnumbered.take(OFFENDER_PREVIEW).joinToString("\n") { "第 ${it.line} 行：${it.heading}" },
            unnumbered.isEmpty(),
        )

        assertTrue(
            "台账块数下限是 $adrBlockFloor（第 128 轮实测 $knownBlockCount 条）；现 ${blocks.size} 条" +
                "⇒ 整块决定被删了，或者文件被截断了",
            blocks.size >= adrBlockFloor,
        )
        assertTrue(
            "下限常量不许是零或负数，否则「零条目 ⇒ 全绿」；现 adrBlockFloor=$adrBlockFloor",
            adrBlockFloor > 0,
        )
        assertTrue(
            "下限不许超过当轮实测值，否则第 1、2 条永远红；现 adrBlockFloor=$adrBlockFloor、实测 $knownBlockCount",
            adrBlockFloor <= knownBlockCount,
        )
    }

    @Test
    fun everyAdrNumberIsContinuousWithoutGapsOrDuplicates() {
        val numbers = adrBlocks().mapNotNull { it.number }
        assertTrue(
            "编号要判「从 1 起逐条 +1」，块数下限同样取 $adrBlockFloor（台账被删空时这条不许变成空转）；" +
                "现 ${numbers.size} 条",
            numbers.size >= adrBlockFloor,
        )

        val duplicates = numbers.groupingBy { it }.eachCount()
            .filter { it.value > 1 }
            .keys
            .sorted()
        assertTrue(
            "编号不许重号；现重号的有 $duplicates（每处出现两次以上）",
            duplicates.isEmpty(),
        )

        val sorted = numbers.toSortedSet()
        val highest = sorted.max()
        val missing = (1..highest).filter { it !in sorted }
        assertTrue(
            "$ADR_LEDGER_DOC 的决定编号必须从 1 起连续排到 $highest，中间不许缺号、不许重号、不许乱序。" +
                "第 128 轮实测是 1 … $knownNewestNumber，共 $knownBlockCount 条；现 ${sorted.size} 条里有 " +
                "${missing.size} 处缺号，头几处：${missing.take(OFFENDER_PREVIEW)}" +
                "（缺号意味着整块决定被删、或者某一条被改了编号）",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyAdrSinceTheNinetiethCarriesItsRoundSuffix() {
        val tail = labeledTail()
        assertTrue(
            "第 $adrLabelFloor 条及以后一条也没有 ⇒ 这条判据失去了判据对象",
            tail.isNotEmpty(),
        )

        val missing = tail.filter { roundOf(it) == null }
            .map { "ADR-${it.number}（第 ${it.line} 行）标题末尾没有「（第 NNN 轮）」尾标：${it.heading}" }
        assertTrue(
            "第 $adrLabelFloor 条及以后的每一条决定，标题末尾都必须带「（第 NNN 轮）」尾标——" +
                "它记的是这条决定是哪一轮开出来的，去掉之后台账就读不出先后了。" +
                "现 ${tail.size} 条里有 ${missing.size} 条没带，头几条：\n" +
                missing.take(OFFENDER_PREVIEW).joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun theNewestAdrRoundNeverRunsAheadOfTheRoundRecords() {
        val tail = labeledTail()
        val rounds = tail.mapNotNull { roundOf(it) }
        assertTrue(
            "第 $adrLabelFloor 条及以后一条带轮次尾标的也没有 ⇒ 这条判据失去了左侧的参照物",
            rounds.isNotEmpty(),
        )

        val newest = rounds.max()
        assertTrue(
            "台账里最新一条决定的轮次号下限是 $adrNewestRoundFloor（第 128 轮实测 $knownNewestRound）；" +
                "现最大是 $newest ⇒ 要么新补的那些决定被删了，要么尾标里的轮次号被改小了",
            newest >= adrNewestRoundFloor,
        )

        val recorded = ROUND_HEADING.findAll(docText(ROUND_RECORDS_DOC))
            .map { it.groupValues[1].toInt() }
            .toList()
        assertTrue(
            "$ROUND_RECORDS_DOC 里找不到任何「## 第 N 轮」标题 ⇒ 这条判据失去了右侧的参照物",
            recorded.isNotEmpty(),
        )
        assertTrue(
            "台账里最大的轮次号（现 $newest）不许**超前**于迭代记录里已经写下的最大轮次号" +
                "（现 ${recorded.max()}）：台账里出现一个记录里还没有的轮次，意味着有人先开了编号、后补的正文。" +
                "这条**只判上限、不要求相等** —— 有些轮次没往台账里开编号，台账落在记录后面是既成事实。",
            newest <= recorded.max(),
        )
    }

    @Test
    fun everyAdrSinceTheNinetiethHasTheFiveKnownSections() {
        val tail = labeledTail()
        assertTrue(
            "第 $adrLabelFloor 条及以后一条也没有 ⇒ 这条判据失去了判据对象",
            tail.isNotEmpty(),
        )

        val offenders = tail.mapNotNull { block ->
            val missing = SECTIONS.filter { section -> block.body.none { it.startsWith(section) } }
            if (missing.isEmpty()) null
            else "ADR-${block.number}（第 ${block.line} 行）缺 ${missing.size} 段：$missing"
        }
        assertTrue(
            "第 $adrLabelFloor 条及以后的每一条决定，块体内都必须按 ${SECTIONS.size} 段写：" +
                SECTIONS.joinToString("、") + "（「验证」那一段带轮次后缀，按前缀认）。" +
                "现 ${tail.size} 条里有 ${offenders.size} 条不全，头几条：\n" +
                offenders.take(OFFENDER_PREVIEW).joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun everyAdrSinceTheNinetiethHasEnoughBody() {
        val tail = labeledTail()
        assertTrue(
            "第 $adrLabelFloor 条及以后一条也没有 ⇒ 这条判据失去了判据对象",
            tail.isNotEmpty(),
        )

        val short = tail.map { it.line to (it.number to it.body.count { line -> line.isNotBlank() }) }
            .filter { (_, pair) -> pair.second < adrBodyFloor }
            .map { (line, pair) -> "ADR-${pair.first}（第 $line 行）非空行只有 ${pair.second} 行" }
        assertTrue(
            "第 $adrLabelFloor 条及以后的每一条决定，非空行数下限是 $adrBodyFloor 行" +
                "（第 126 轮实测最小 $knownThinnestBody 行，下限保守取 $adrBodyFloor）。" +
                "现 ${tail.size} 条里有 ${short.size} 条不够，头几条：\n" +
                short.take(OFFENDER_PREVIEW).joinToString("\n"),
            short.isEmpty(),
        )
    }

    @Test
    fun theRealRepositoryStillShowsTheKnownShape() {
        val blocks = adrBlocks()
        assertEquals(
            "第 128 轮实测这份台账是 $knownBlockCount 条决定；往台账里开新编号的轮次要顺手把这一条与下限一起同步",
            knownBlockCount,
            blocks.size,
        )
        assertEquals(knownFirstNumber, blocks.first().number)
        assertEquals(knownNewestNumber, blocks.last().number)
        assertEquals(
            "第 128 轮实测最新一条决定的轮次尾标是第 $knownNewestRound 轮",
            knownNewestRound,
            labeledTail().mapNotNull { roundOf(it) }.max(),
        )

        assertTrue(
            "五段小标题的条数与用例里那个常量必须一致，否则第 5 条量的是另一套段；" +
                "现 SECTIONS=${SECTIONS.size}、adrSectionFloor=$adrSectionFloor",
            SECTIONS.size == adrSectionFloor,
        )
        assertTrue(
            "三条与块数有关的下限都必须是正数、且不超过当轮实测值，否则「零条目 ⇒ 全绿」或者永远红；" +
                "现 adrBlockFloor=$adrBlockFloor、adrLabelFloor=$adrLabelFloor、adrBodyFloor=$adrBodyFloor、" +
                "实测 $knownBlockCount",
            adrBlockFloor > 0 && adrLabelFloor > 0 && adrBodyFloor > 0 &&
                adrBlockFloor <= knownBlockCount && adrLabelFloor <= knownBlockCount,
        )
        assertTrue(
            "最新轮次号下限必须是正数、且不超过当轮实测的最新轮次号，否则第 4 条永远红；" +
                "现 adrNewestRoundFloor=$adrNewestRoundFloor、实测 $knownNewestRound",
            adrNewestRoundFloor > 0 && adrNewestRoundFloor <= knownNewestRound,
        )
    }

    private companion object {
        /** 受管文档：架构决定台账。 */
        const val ADR_LEDGER_DOC = "records/03-架构决策记录.md"

        /** 右侧参照物：迭代记录里的轮次标题。 */
        const val ROUND_RECORDS_DOC = "records/01-迭代记录.md"

        /**
         * 一条 h2（`## ` 开头）——h3 是 `### `，第三个字符是井号，匹配不上。
         * 匹配时用 `containsMatchIn` 而不是 `matches`：`matches` 要求**整行**都能被模式吃掉，
         * 而 `^##\s` 只吃掉三个字符，于是整份台账会被认成「零条决定」。第 126 轮第一遍跑就栽在这里。
         */
        val H2 = Regex("""^##\s""")

        /**
         * 一条决定块的标题：编号与标题之间**用空格或全角冒号分隔都认**。
         * 第 111 轮及更早的 ADR-059 ~ ADR-062 用空格，只认冒号会把它们漏成「缺号」。
         */
        val ADR_HEADING = Regex("""^## ADR-(\d+)(?:\s|：)""")

        /** 标题末尾的轮次尾标。 */
        val ROUND_SUFFIX = Regex("""（第\s*(\d+)\s*轮）""")

        /** 迭代记录里的一轮标题。 */
        val ROUND_HEADING = Regex("""(?m)^## 第\s*(\d+)\s*轮""")

        /** 第 126 轮补的十四条决定按这五段写；「验证」那一段带轮次后缀，按前缀认。 */
        val SECTIONS = listOf(
            "### 背景",
            "### 决策",
            "### 理由与代价",
            "### 验证（",
            "### 已知边界（不得外推）",
        )

        /** 台账块数下限 = 第 145 轮的实测值（下限只能写在落盘之前，落盘留下的余量如实记在 `records/01`）。 */
        const val adrBlockFloor = 122

        /** 「标题必须带轮次尾标」的起点编号 = 第 126 轮补的那十四条的第一条。 */
        const val adrLabelFloor = 90

        /** 最新一条决定的轮次号下限 = 第 126 轮量到的 125（跨轮兜底，不当轮同步：当轮实测值由 [knownNewestRound] 走在前头）。 */
        const val adrNewestRoundFloor = 125

        /** 第 5 条量的段数。 */
        const val adrSectionFloor = 5

        /** 第 6 条量的非空行下限：第 126 轮实测最小 25 行，保守取 20。 */
        const val adrBodyFloor = 20

        /** 第 7 条对照用的当轮读数。 */
        const val knownBlockCount = 122
        const val knownFirstNumber = 1
        const val knownNewestNumber = 122
        const val knownNewestRound = 145
        const val knownThinnestBody = 25

        /** 报红时最多列几处不合，免得一条消息里塞进几十行。 */
        const val OFFENDER_PREVIEW = 3
    }
}
