package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「各份文档写下的**当前轮次**，是不是同一轮」的守卫。
 *
 * 仓库里「当前轮次」这类读数，此前只有一处有机器判据：
 * [DocCurrentReadingGuardTest.everyRoundDocStopsAtTheSameRound] 盯着五份含轮次章节的文档 ——
 * `records/01-迭代记录.md`、`records/04-质量与验证记录.md`、`records/CHANGELOG.md`、
 * `docs/03-详细设计说明书.md`、`docs/06-测试计划与用例.md` —— 它们的最大轮次号必须彼此相等。
 * 可**门面文档 `README.md` 不在这五份里头**：它第 122 行写着「迭代轮次：**第 N 轮**已完成」、
 * 第 125 行写着「最近一次实测读数（**第 N 轮**…）」，这两处口径**一把尺子也没有**。
 *
 * 后果在第 150 ~ 152 轮现过形：那两处一直停在「第 150 轮」，而五份文档早已写到 152 —— 每 10 轮开一次
 * 推送窗口，读者第一眼看到的就是一句过期的话。这不是「谁忘了改」，是**没有判据**。
 *
 * 所以这里补上：把 README 那两处轮次号解析出来，跟五份文档的最大轮次号对账。四条用例：
 *
 * 1. [readmeClaimsAgreeWithEachOther]：README 自己那两处必须相等（它们说的是同一轮），处数有下限。
 * 2. [readmeClaimsAgreeWithRoundDocs]：README 的轮次号必须等于五份文档最大轮次号的**唯一**值。
 * 3. [locatorStaysAlive]：定位器哨兵 —— 五份文档一份不少、每条正则各自至少命中一次，否则报
 *    「定位器坏了」，免得一条也扫不到时判据空转成绿。
 * 4. [readmeWordingStaysVerbatim]：README 那两句的**措辞**逐字还在 —— 把「迭代轮次：」改宽成别的字，
 *    这里先红，而不是让上面三条悄悄地把锚点丢了。
 *
 * 轮次号一律从盘上读，**不写死数字**，所以这套判据不用逐轮跟着滚。
 */
class DocRoundAgreementGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 一份含轮次章节的文档，配它那一节标题的正则（与 [DocCurrentReadingGuardTest] 逐字一致）。 */
    private data class RoundDoc(val doc: String, val pattern: Regex)

    private val textCache = mutableMapOf<String, String>()

    private fun textOf(doc: String): String = textCache.getOrPut(doc) {
        val file = File(repoRoot, doc)
        assertTrue("受管文档不存在：$doc", file.isFile)
        file.readText()
    }

    /**
     * 该文档里每一条轮次标题的轮次号。标题必须**起于列首** —— 行首的 `\r` 不带进来，免得锚定被带跑
     * （口径同 [DocCurrentReadingGuardTest] 的 `sectionsOf`）。
     */
    private fun rounds(doc: RoundDoc): List<Int> =
        textOf(doc.doc).split("\n").mapNotNull { line ->
            val m = doc.pattern.find(line) ?: return@mapNotNull null
            if (m.range.first != 0) null else m.groupValues[1].toInt()
        }

    /** README 里那两处「当前轮次」：标签 → 解析出来的轮次号（解析不到就不在这张表里）。 */
    private fun readmeClaims(): Map<String, Int> =
        README_CLAIM_PATTERNS.mapNotNull { (label, pattern) ->
            pattern.find(textOf(README_DOC))?.groupValues?.get(1)?.toInt()?.let { label to it }
        }.toMap()

    @Test
    fun readmeClaimsAgreeWithEachOther() {
        val claims = readmeClaims()
        assertTrue(
            "README 里带「当前轮次」的句子下限是 $readmeRoundClaimFloor 处，现只解析到 ${claims.size} 处：$claims" +
                " ⇒ 措辞形状变了（见 [README_CLAIM_PATTERNS]），这一族判据的锚点就没了",
            claims.size >= readmeRoundClaimFloor,
        )
        assertTrue(
            "README 那几处说的都是同一轮，必须彼此相等。现逐处是 $claims ⇒ 改了一处漏了另一处",
            claims.values.toSet().size == 1,
        )
    }

    @Test
    fun readmeClaimsAgreeWithRoundDocs() {
        val newest = ROUND_DOCS.associate { it.doc to rounds(it).maxOrNull() }
        val finite = newest.filterValues { it != null }.mapValues { it.value!! }
        assertTrue(
            "五份含轮次章节的文档（[ROUND_DOCS]）记的是同一批轮次，各自的最大轮次号必须彼此相等。" +
                "现逐份是 $newest，出现了 ${finite.values.toSet().size} 种值 ${finite.values.toSet()}",
            finite.values.toSet().size == 1,
        )
        val docRound = finite.values.toSet().single()
        val claims = readmeClaims()
        val offenders = claims.filterValues { it != docRound }
        assertTrue(
            "README 的两处「当前轮次」必须与五份文档的最大轮次号（现为第 $docRound 轮）相等 ——" +
                "它们是同一句话在两个地方说。现这几处对不上：$offenders" +
                " ⇒ 迭代往前走了，README 这页忘了跟着走（第 150~152 轮就是这一种）",
            offenders.isEmpty(),
        )
    }

    @Test
    fun locatorStaysAlive() {
        assertTrue(
            "含轮次章节的文档下限是 $roundDocFloor 份，现只有 ${ROUND_DOCS.size} 份" +
                " ⇒ 轮次章节定位器坏了，这一族判据的锚点就没了",
            ROUND_DOCS.size >= roundDocFloor,
        )
        val empty = ROUND_DOCS.filter { rounds(it).isEmpty() }.map { it.doc }
        assertTrue(
            "每份含轮次章节的文档都要至少命中一次标题正则，现这些一条也没命中：$empty" +
                " ⇒ 该文档的标题形状变了，它的最大轮次号取不到，对账就悄悄少了一份",
            empty.isEmpty(),
        )
    }

    @Test
    fun readmeWordingStaysVerbatim() {
        val text = textOf(README_DOC)
        val missing = README_WORDINGS.filter { !text.contains(it) }
        assertTrue(
            "README 这两句的措辞是上面三条判据的锚点，必须逐字还在。现这些片段找不到了：$missing" +
                " ⇒ 措辞被改写时，请一并改 [README_CLAIM_PATTERNS] 与这里，别让锚点静默失效",
            missing.isEmpty(),
        )
    }

    private companion object {
        const val README_DOC = "README.md"

        /** 含轮次章节的文档下限：`docs/03`、`docs/06` + `records` 三份。 */
        const val roundDocFloor = 5

        /** README 里带「当前轮次」的句子下限。 */
        const val readmeRoundClaimFloor = 2

        /** 五份含轮次章节的文档与它们那一节标题的正则（照抄 [DocCurrentReadingGuardTest] 的口径）。 */
        val ROUND_DOCS: List<RoundDoc> = listOf(
            RoundDoc("records/01-迭代记录.md", Regex("""##\s*第\s*(\d+)\s*轮""")),
            RoundDoc("records/04-质量与验证记录.md", Regex("""##\s*第[零一二三四五六七八九十百千]+节\s*·\s*第\s*(\d+)\s*轮""")),
            RoundDoc("records/CHANGELOG.md", Regex("""##\s*第\s*(\d+)\s*轮""")),
            RoundDoc("docs/03-详细设计说明书.md", Regex("""###\s*[\d.]+\s*第\s*(\d+)\s*轮""")),
            RoundDoc("docs/06-测试计划与用例.md", Regex("""###\s*[\d.]+\s*第\s*(\d+)\s*轮""")),
        )

        /** README 两处「当前轮次」的标签与正则：一处是「迭代轮次」行，一处是「最近一次实测读数」行。 */
        val README_CLAIM_PATTERNS: List<Pair<String, Regex>> = listOf(
            "迭代轮次行" to Regex("""迭代轮次：\*\*第\s*(\d+)\s*轮\*\*已完成"""),
            "最近实测读数行" to Regex("""最近一次实测读数（\*\*第\s*(\d+)\s*轮\*\*"""),
        )

        /** 上面那两条正则以外的措辞骨架：它们必须逐字还在，锚点才不会静默失效。 */
        val README_WORDINGS: List<String> = listOf(
            "迭代轮次：**第",
            "轮**已完成，每 10 轮向本仓库推一次，历史见",
            "最近一次实测读数（**第",
            "轮**，逐条留档在",
        )
    }
}
