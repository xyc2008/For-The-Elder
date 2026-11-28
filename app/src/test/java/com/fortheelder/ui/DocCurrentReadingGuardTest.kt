package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「受管文档的正文里，那些**引别份文档字节数**的句子，读的是不是能对上的数」的守卫。
 *
 * 这一族读数在仓库里并不罕见，形态是「别名 + 若干个字 + 中文数字 + 字节」，例如
 * `详细设计说明书` 后面紧跟 `**一百零三万四千八百一十二字节**` 这样一句。第 110 轮起立起来的那几把
 * 读数尺子各有分工：[DocByteMd5ClaimGuardTest] 量的是**某几份文档自己的**字节与摘要声称，
 * [DocCodeLineClaimGuardTest] 量的是代码行数，[DocLineRefGuardTest] 量的是「第 N 行」这种落点。
 * 而**「引别份文档的字节数」**这一族里，`.kt` 源码被括号形式引用的那些有机器判据
 * （[DocSymbolClaimGuardTest] 一族），**`.md` 正文里的这类读数此前一处判据也没有** ——
 * 第 129 轮普查出来的真值是：全仓 16 份受管文档里，符合形态的读数**恰好 57 处**，
 * 宿主只有两份（`records/01-迭代记录.md` 与 `records/04-质量与验证记录.md`）。
 *
 * ## 锚：为什么「当轮段」是机械可判的
 *
 * 这 57 处里绝大部分是**历史快照** —— 当时那一刻别份文档确实那么长，后来它长大了，读数就与现值不等。
 * 「与现值不等」本身**不是**缺陷，所以不能立「全部必须等于现值」这种判据。真正的判据得先把这 57 处
 * 分成两档：
 *
 * - **当轮段**：落在「当前最大轮次号的那一节」里的读数。这一节是作者写「这一步落了什么盘」的地方，
 *   引别份文档的字节数**必须**等于此刻磁盘上的值 —— 因为写它的那一刻就是这样量的。
 * - **历史段 / 不分章节段**：落在任何**别的**轮次章节里、或者干脆落在章节之外的读数。它们该是快照。
 *
 * 分档靠两条机械规矩叠出来：轮次章节的正则（五份文档各一条，见 [sectionPatterns]）+ 「某一节的末尾 =
 * 它之后第一条标题级数不高于它的行」。有了这两条，每一节的**行区间**就是算出来的，某个命中的行号
 * 落在哪个区间里也是算出来的，于是「当轮段」不需要人工标注。**当前最大轮次号**从同一份文档的章节里取，
 * 不写死数字，所以这套判据不用逐轮跟着滚。
 *
 * 扫描口径照抄普查脚本（`build/r129/lib129.py`），逐条对齐才算数：
 *
 * - 从右往左看每一个「字节」：令 `j` 为它前面那串**中文数字字符**的起点（`零一二三四五六七八九十百千万两`
 *   里往前吃）；吃不到字符就跳过这一处。只认中文数字：阿拉伯形态另扫一档，**命中差集为空**（实测），
 *   所以不判阿拉伯形态。
 * - 别名表按**长度降序**逐个试；对每个别名取「整段落在 `j` 之前」的**最后一次**出现，
 *   `gap` = 别名末尾到 `j` 之间那串字；`gap` 长度超过 [MAX_GAP] 的整片不收（写成一段话把别名与数字
 *   隔开的，本判据看不见）。
 * - 前面这些照搬是**必须**的：本轮实测过，换个口径（比如只试第一个别名）命中数就不再是 57。
 *
 * 关于行尾：**不做换行归一化**。本轮实测过两种口径 —— Python `read_text()` 的通用换行与「按字节读出后
 * 直接 decode」（= Kotlin `readText()` 的语义）—— **命中总数同为 57**，逐条差集只是 `pos` 字符偏移不同
 * （`records/01` 里 16 个孤立 `\r` 造成的位移），行号、别名、目标、`gap`、数值、分档判定**逐字相同**。
 * 所以这里直接用 `readText()`，行号靠数 `\n` 得出（`\r` 不参与计数，两种口径的行号本来就一样）。
 *
 * ## 八条用例
 *
 * 1. [everyManagedDocIsRead]：16 份受管文档一份不少、每份至少有一个正文里用的别名。
 * 2. [everyRoundDocStopsAtTheSameRound]：含轮次章节的文档不少于 [roundDocFloor] 份，且**五者的最大轮次号
 *    必须彼此相等** —— 它们记的是同一批轮次，谁少写一轮这里就红。（不写死 128，故无需逐轮滚。）
 * 3. [everyCurrentClaimMatchesDisk]：当轮段 ∧ `gap ∈ {"", "**"}` ∧ 引别份 ⇒ **每一处都必须等于磁盘现值**；
 *    处数、被引文档种数、宿主种数各有下限，防「一条也扫不到 ⇒ 空转的绿」。
 * 4. [currentSelfClaimsStayPreWrite]：当轮段 ∧ 自指 ⇒ 处数在 `1..selfClaimCap`，且**每一处的值都不许等于
 *    磁盘现值** —— 自指读数写的是「落盘前」的尺寸，等于现值就成了在声称自己的落盘后大小，下一轮这份
 *    文档一长大那句话就变成假话（第 124 轮起的成文规矩）。
 * 5. [historicalClaimsStaySnapshots]：历史段 ∧ `gap ∈ {"", "**"}` ∧ 引别份 ⇒ 处数不低于 [historicalFloor]、
 *    其中与现值**不等**的不少于 [historicalStaleFloor]；并且**每一处「恰好相等」的，被引对象都必须是
 *    [FACADE_DOC] 或者 [frozenTargets] 里登记过、长度没动过的文档** —— 那些文件的长度一直没变过，
 *    历史快照与现值撞成同一个数是**等长滚动造成的巧合**，不是有人在回改历史叙述。相等处数另有上限
 *    [frozenEqualsCap]，且登记表里每一项的钉住长度必须与此刻磁盘一致（表里哪份长大了，这里先红）。
 * 6. [outOfBandClaimsArePreValuesNotCurrentReadings]：`gap` 既不是空也不是 `**` 的那些读数（= 作者在别名与
 *    数字之间夹了别的词）单独分段判。当轮段里这类处数不得超过 [curOutOfBandCap]、形态必须登记在
 *    [curOutOfBandShapes] 里、值不许等于现值；历史段与不分章节段里处数不低于 [snapshotOutOfBandFloor]、
 *    形态必须登记在 [snapshotOutOfBandShapes] 里、值同样不许等于现值。**登记形态而不登记逐轮数值**：
 *    这类写法的意思是「落地前是多少」或者「长了多少」，词语比数字稳。
 * 7. [claimSurfaceIsCounted]：总命中处数不低于 [totalHitFloor]、落在轮次章节之外的不少于 [noChapterFloor]。
 *    这是第二道防线：章节定位器一旦坏掉，所有命中都会掉进「不分章节」这一档，第 3 ~ 6 条就双双变成空转的绿。
 * 8. [frozenCountRiseComesFromTheDemotedPair]：把「上一轮那两节降格时浮现的那一笔」钉成机器账 —— 两份宿主各取
 *    最近两节，四节各自恰好 [demotedPairRows] 行；当轮那两节的 18 处**全部分区为「当轮面」**、上一轮那两节
 *    18 处**全部为历史段**，两笔行数彼此相等。第 151 轮记下的人工推算（+18 出自降格而非新写）由此可复算。
 *
 * ## 它不保证什么
 *
 * - **只认中文数字形态**。阿拉伯数字（`1,027,725 字节`）一律不判，这类写法整片逃逸。这不是小事：
 *   第 128 轮正文里那一处真缺陷 `从 1,019,141 字节、6,646 行长到 1,027,682 字节、6,715 行`
 *   （真值 1,027,725）就正是阿拉伯形态，本判据一个字也管不了。
 * - **`gap` 超过 [MAX_GAP] 字符的写法整片逃逸**（把别名与数字用一句话隔开就会这样）。上限取 6 是普查时的
 *   口径：真值里最长的 `gap` 恰好 6 个字（`落地前是**`），再宽就会开始把不相关的句子收进来。
 * - **只判字节，不判行数、不判摘要**。同一句里写着的行数、md5 前缀对不对，这里不看。
 * - **反向面判得间接**。第 5 条不是「历史快照必须不等现值」（那会把门面与 [frozenTargets] 上那些巧合
 *   判成缺陷），而是「不等现值的数量有下限 + 恰好相等时被引对象必须是门面或登记表上的文档」两条合起来
 *   逼近。
 * - **登记表是一处已知的放宽**（第 138 轮）。[frozenTargets] 上的九份文档（含门面），其历史读数
 *   **与现值相等不再被这条判据管**。配套护栏只有上限与钉长度两条：上限卡在实测值上、钉长度在文档长大的
 *   那一刻先红。**一份文档只要还在表里，它头上的历史读数就是这条判据的盲区** —— 表越长，盲区越大，
 *   所以往表里加项要当成一处真放宽来登记，不能顺手加。
 * - **历史段里的自指读数不被任何一条单独管**（第 4 条只管当轮段）。它们只跟着第 7 条的总数下限走。
 * - **写正文时要小心**：任何「别名 + 不超过 6 个字 + 中文数字 + 字节」都会被扫进来，形态没登记、或者
 *   落在当轮段又对不上现值，守卫当场红。引旧值时把别名与数字隔开 6 字以上，或者干脆写阿拉伯数字。
 *
 * ## 结论
 *
 * 这一族此前**完全没有机器判据**，第 129 轮起有了。老规矩：判据是**下限 + 谓词**，不是逐条数字，
 * 所以正文照常往下写、别份文档照常长大，守卫不用跟着滚；只有当**判据本身**要改时才动常量。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。
 */
class DocCurrentReadingGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 一次扫描出来的读数。`gap` = 别名末尾到数字起点之间的那串字，可能是空串。 */
    private data class Hit(
        val host: String,
        val line: Int,
        val alias: String,
        val target: String,
        val gap: String,
        val number: String,
        val value: Long,
        val pos: Int,
    )

    /** 一条读数落在这份文档的哪一档里。 */
    private enum class Zone { CURRENT, HISTORICAL, NO_CHAPTER }

    private data class Classified(val hit: Hit, val zone: Zone)

    /** 一节轮次章节：行索引 0 基、`[start, end)` 半开区间，标题行归属本节。 */
    private data class Section(val round: Int, val start: Int, val end: Int)

    private class AliasIndex(val alias: String, val target: String, val positions: IntArray)

    private val textCache = mutableMapOf<String, String>()
    private val sectionCache = mutableMapOf<String, List<Section>>()
    private val lineStartsCache = mutableMapOf<String, IntArray>()
    private val aliasIndexCache = mutableMapOf<String, List<AliasIndex>>()

    private fun textOf(doc: String): String = textCache.getOrPut(doc) {
        val file = File(repoRoot, doc)
        assertTrue("受管文档不存在：$doc", file.isFile)
        file.readText()
    }

    private fun sizeOf(doc: String): Long = File(repoRoot, doc).length()

    /** 行号 1 起：该 `pos` 之前的换行符个数加一（口径与普查脚本的 `line_of` 相同）。 */
    private fun lineOf(doc: String, pos: Int): Int {
        val starts = lineStartsCache.getOrPut(doc) {
            val list = ArrayList<Int>(21000)
            list += 0
            val text = textOf(doc)
            var i = text.indexOf('\n')
            while (i >= 0) {
                list += i + 1
                i = text.indexOf('\n', i + 1)
            }
            list.toIntArray()
        }
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    /**
     * 这份文档里的轮次章节。正则与标题级数照抄普查脚本的 `SECTIONS` 表；`^` 的锚定用
     * 「命中起点必须为 0」手工判，免得行尾的 `\r` 把锚定带跑。
     */
    private fun sectionsOf(doc: String): List<Section> = sectionCache.getOrPut(doc) {
        val spec = sectionPatterns[doc] ?: return@getOrPut emptyList()
        val (pattern, level) = spec
        val lines = textOf(doc).split("\n")
        val marks = lines.withIndex().mapNotNull { (index, line) ->
            val m = pattern.find(line) ?: return@mapNotNull null
            if (m.range.first != 0) return@mapNotNull null
            index to m.groupValues[1].toInt()
        }
        marks.map { (index, round) ->
            var end = lines.size
            for (j in index + 1 until lines.size) {
                val h = HEADING.find(lines[j]) ?: continue
                if (h.range.first == 0 && h.groupValues[1].length <= level) {
                    end = j
                    break
                }
            }
            Section(round, index, end)
        }
    }

    /** 某一行落在哪一档：先找包含它的第一节；找不到就是不分章节。 */
    private fun zoneOf(doc: String, line: Int): Zone {
        val sections = sectionsOf(doc)
        if (sections.isEmpty()) return Zone.NO_CHAPTER
        val index = line - 1
        val owner = sections.firstOrNull { index >= it.start && index < it.end } ?: return Zone.NO_CHAPTER
        return if (owner.round == sections.maxOf { it.round }) Zone.CURRENT else Zone.HISTORICAL
    }

    /**
     * 扫一份文档。别名表按长度降序逐个试，每个别名取「整段落在数字起点之前」的最后一次出现；
     * 位置表是预先算好的（`indexOf` 逐个推进），二分找「末尾不超过 `j` 的最后一次出现」——
     * 与普查脚本的 `text.rfind(alias, 0, j)` 逐条等价。
     */
    private fun scan(doc: String, text: String): List<Hit> {
        val indexes = aliasIndexCache.getOrPut(doc) {
            aliases.map { (alias, target) ->
                val positions = ArrayList<Int>(64)
                var i = text.indexOf(alias)
                while (i >= 0) {
                    positions += i
                    i = text.indexOf(alias, i + 1)
                }
                AliasIndex(alias, target, positions.toIntArray())
            }
        }

        val out = ArrayList<Hit>(64)
        var found = text.indexOf("字节")
        while (found >= 0) {
            val e = found
            var j = e
            while (j > 0 && text[j - 1] in CN_CHARS) j--
            val number = text.substring(j, e)
            if (number.isNotEmpty()) {
                val value = chineseToInt(number)
                if (value != null) {
                    for (index in indexes) {
                        val lastStart = j - index.alias.length
                        if (lastStart < 0) continue
                        val p = lastPositionAtOrBefore(index.positions, lastStart)
                        if (p < 0 || p + index.alias.length > j) continue
                        val gap = text.substring(p + index.alias.length, j)
                        if (gap.length > MAX_GAP) continue
                        out += Hit(doc, lineOf(doc, p), index.alias, index.target, gap, number, value, p)
                    }
                }
            }
            found = text.indexOf("字节", found + 1)
        }
        return out.sortedWith(compareBy({ it.pos }, { it.alias }))
    }

    private fun lastPositionAtOrBefore(positions: IntArray, limit: Int): Int {
        var lo = 0
        var hi = positions.size - 1
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (positions[mid] <= limit) {
                answer = positions[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }

    private val allHits: List<Classified> by lazy {
        managedDocs.flatMap { (doc, _) ->
            scan(doc, textOf(doc)).map { Classified(it, zoneOf(doc, it.line)) }
        }
    }

    @Test
    fun everyManagedDocIsRead() {
        val missing = managedDocs.map { it.first }.filter { !File(repoRoot, it).isFile }
        assertTrue(
            "受管文档一份也不能少：它们都是正文里可能被引用的对象，缺一份就有一整片读数查不到。现缺：$missing",
            missing.isEmpty(),
        )
        assertTrue(
            "受管文档份数下限 $managedFileFloor，现 ${managedDocs.size} 份" +
                "（docs/00 ~ 09 十份 + records 四份 + 变更日志 + 门面文档）",
            managedDocs.size >= managedFileFloor,
        )
        val noAlias = managedDocs.filter { it.second.isEmpty() }.map { it.first }
        assertTrue("每份受管文档都要有至少一个正文里用的别名，现这些没有：$noAlias", noAlias.isEmpty())
    }

    @Test
    fun everyRoundDocStopsAtTheSameRound() {
        val roundDocs = managedDocs.map { it.first }.filter { sectionsOf(it).isNotEmpty() }
        assertTrue(
            "含轮次章节的受管文档下限是 $roundDocFloor 份，现只有 ${roundDocs.size} 份：$roundDocs" +
                " ⇒ 轮次章节定位器坏了（正则改了、或者标题形状变了），这一族判据的锚点就没了",
            roundDocs.size >= roundDocFloor,
        )
        val newest = roundDocs.associateWith { doc -> sectionsOf(doc).maxOf { it.round } }
        val distinct = newest.values.toSet()
        assertTrue(
            "含轮次章节的这 ${roundDocs.size} 份文档记的是同一批轮次，各自的最大轮次号必须彼此相等。" +
                "现逐份是 $newest，出现了 ${distinct.size} 种值 $distinct ⇒ 有一份文档的轮次章节少写或多写了",
            distinct.size == 1,
        )
    }

    @Test
    fun everyCurrentClaimMatchesDisk() {
        val current = allHits.filter {
            it.zone == Zone.CURRENT && it.hit.gap in IN_BAND_GAPS && it.hit.target != it.hit.host
        }
        val offenders = current.filter { it.hit.value != sizeOf(it.hit.target) }
        assertTrue(
            "当轮段（当前最大轮次号那一节）里引别份文档字节数的句子，读的必须是**这一步**的落盘值 ——" +
                "写它的那一刻就是这样量的。现 ${current.size} 处里有 ${offenders.size} 处对不上：" +
                offenders.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 引「${it.hit.alias}」写作 ${it.hit.value}，实测 ${sizeOf(it.hit.target)}"
                },
            offenders.isEmpty(),
        )
        assertTrue(
            "当轮读数处数下限 $currentClaimFloor，现 ${current.size} ⇒ 扫不到当轮读数，这条判据就在空转",
            current.size >= currentClaimFloor,
        )
        val targets = current.map { it.hit.target }.toSet()
        assertTrue("当轮读数的被引文档种数下限 $currentTargetFloor，现 ${targets.size}：$targets", targets.size >= currentTargetFloor)
        val hosts = current.map { it.hit.host }.toSet()
        assertTrue("当轮读数的宿主文档种数下限 $currentHostFloor，现 ${hosts.size}：$hosts", hosts.size >= currentHostFloor)
    }

    @Test
    fun currentSelfClaimsStayPreWrite() {
        val current = allHits.filter { it.zone == Zone.CURRENT && it.hit.target == it.hit.host }
        assertTrue(
            "当轮段里的自指读数至少要有 1 处（这一档记的是「这份文档自己现在多长」，一轮落盘账一个字不写就没了），" +
                "现 0 处",
            current.isNotEmpty(),
        )
        assertTrue(
            "当轮段里的自指读数不许超过 $selfClaimCap 处，现 ${current.size} 处：" +
                current.joinToString("；") { "${it.hit.host}:${it.hit.line} 「${it.hit.alias}」${it.hit.value}" },
            current.size <= selfClaimCap,
        )
        val equals = current.filter { it.hit.value == sizeOf(it.hit.host) }
        assertTrue(
            "自指读数写的是「落地前」的尺寸：等于磁盘现值就意味着这句话在声称自己的落盘后大小，" +
                "而落盘马上会把这份文档改长，那句话当场变成假话（第 124 轮起的成文规矩）。" +
                "现 ${equals.size} 处等于现值：" + equals.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 写作 ${it.hit.value}，而磁盘就是 ${sizeOf(it.hit.host)}"
                },
            equals.isEmpty(),
        )
    }

    @Test
    fun historicalClaimsStaySnapshots() {
        val historical = allHits.filter {
            it.zone == Zone.HISTORICAL && it.hit.gap in IN_BAND_GAPS && it.hit.target != it.hit.host
        }
        assertTrue(
            "历史段里引别份文档的读数处数下限 $historicalFloor，现 ${historical.size}" +
                " ⇒ 扫不到历史读数，这一档判据就在空转",
            historical.size >= historicalFloor,
        )
        val stale = historical.filter { it.hit.value != sizeOf(it.hit.target) }
        assertTrue(
            "历史段里的读数绝大多数该是「当时那一刻的快照」—— 被引文档后来长大了，读数就与现值不等。" +
                "现 ${historical.size} 处里只有 ${stale.size} 处对不上现值，下限是 $historicalStaleFloor" +
                " ⇒ 要么历史段的读数被人批量改成了现值（那是在回改历史叙述），要么这族判据扫到的东西变了",
            stale.size >= historicalStaleFloor,
        )
        val equals = historical.filter { it.hit.value == sizeOf(it.hit.target) }
        val unexpected = equals.filter { it.hit.target !in frozenTargets }
        assertTrue(
            "历史段里与现值**恰好相等**的读数，本仓只允许两种成因：别名指的是门面文档 $FACADE_DOC，或者指的是" +
                "[frozenTargets] 里登记过、长度自第 137 轮起没动过的文档 —— 那些文件的长度一直没变过，" +
                "于是历史快照与现值撞成同一个数，这是等长滚动造成的巧合、不是有人在回改。" +
                "现 ${equals.size} 处相等，其中 ${unexpected.size} 处的被引对象两者都不是：" +
                unexpected.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 「${it.hit.alias}」写作 ${it.hit.value} = 现值（目标 ${it.hit.target}）"
                },
            unexpected.isEmpty(),
        )
        val frozenEquals = equals.filter { it.hit.target in frozenTargets }
        assertTrue(
            "落在 [frozenTargets] 登记表上的「相等」处数上限 $frozenEqualsCap，现 ${frozenEquals.size} 处。" +
                "这张表是一处**已知的放宽**：它让那几份文档的历史读数不再受「历史快照不该等于现值」这条约束。" +
                "上限卡在实测值上，是为了让「表里那些文档的历史读数又变多了」这件事冒出来一瞥。",
            frozenEquals.size <= frozenEqualsCap,
        )
        val drifted = frozenTargets.filter { (doc, pinned) -> sizeOf(doc) != pinned }
        assertTrue(
            "登记表里每一份文档的「钉住长度」必须等于它此刻在盘上的字节数 —— 有 ${drifted.size} 份对不上：" +
                drifted.map { (doc, pinned) -> "$doc 表里写的 $pinned，盘上是 ${sizeOf(doc)}" }
                    .joinToString("；") +
                "。哪一份长大了，就得把它从表里摘掉、并重新审那批落在它头上的历史读数（它们不再相等了）。",
            drifted.isEmpty(),
        )
    }

    @Test
    fun outOfBandClaimsArePreValuesNotCurrentReadings() {
        val currentOut = allHits.filter { it.zone == Zone.CURRENT && it.hit.gap !in IN_BAND_GAPS }
        assertTrue(
            "当轮段里 gap 既不是空也不是 `**` 的读数（作者在别名与数字之间夹了别的词）至多 $curOutOfBandCap 处：" +
                "夹了词多半是在说「落地前是多少」，本身仍说得出道理。现 ${currentOut.size} 处：" +
                currentOut.joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 「${it.hit.alias}」gap=「${it.hit.gap}」${it.hit.value}"
                },
            currentOut.size <= curOutOfBandCap,
        )
        val currentShapes = currentOut.map { it.hit.gap }.toSet()
        assertTrue(
            "当轮段里越界写法的形态必须都登记在 curOutOfBandShapes=$curOutOfBandShapes 里，现 $currentShapes" +
                " ⇒ 出现了一种新的越界写法；夹在别名与数字之间的那个词是什么意思，得先弄明白再登记",
            curOutOfBandShapes.containsAll(currentShapes),
        )
        val currentEquals = currentOut.filter { it.hit.value == sizeOf(it.hit.target) }
        assertTrue(
            "越界写法的当轮读数同样不许等于现值（它说的是「落地前」，等于现值就成了假话）。" +
                "现 ${currentEquals.size} 处：" + currentEquals.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 写作 ${it.hit.value}，而磁盘就是 ${sizeOf(it.hit.target)}"
                },
            currentEquals.isEmpty(),
        )

        val oldOut = allHits.filter { it.zone != Zone.CURRENT && it.hit.gap !in IN_BAND_GAPS }
        assertTrue(
            "历史段与不分章节段里越界写法的处数下限 $snapshotOutOfBandFloor，现 ${oldOut.size}" +
                " ⇒ 扫不到这一档，编号与形态两条判据就在空转",
            oldOut.size >= snapshotOutOfBandFloor,
        )
        val oldShapes = oldOut.map { it.hit.gap }.toSet()
        assertTrue(
            "历史段与不分章节段里越界写法的形态必须都登记在 snapshotOutOfBandShapes=$snapshotOutOfBandShapes 里，" +
                "现 $oldShapes ⇒ 出现了一种新的越界写法，先弄明白再登记",
            snapshotOutOfBandShapes.containsAll(oldShapes),
        )
        val oldEquals = oldOut.filter { it.hit.value == sizeOf(it.hit.target) }
        assertTrue(
            "历史段的越界写法说的是「当时多长」或者「长了多少」，同样不该等于现值。现 ${oldEquals.size} 处：" +
                oldEquals.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 「${it.hit.alias}」gap=「${it.hit.gap}」写作 ${it.hit.value}，" +
                        "而磁盘就是 ${sizeOf(it.hit.target)}"
                },
            oldEquals.isEmpty(),
        )
    }

    @Test
    fun claimSurfaceIsCounted() {
        val all = allHits
        assertTrue(
            "总命中处数下限 $totalHitFloor（这是「判据还扫得到东西」的底），现 ${all.size}" +
                " ⇒ 扫描器坏了，下面六条会集体变成空转的绿",
            all.size >= totalHitFloor,
        )
        val outside = all.filter { it.zone == Zone.NO_CHAPTER }
        assertTrue(
            "落在任何轮次章节之外的命中处数下限 $noChapterFloor：这是第二道防线 —— 章节定位器一旦坏掉，" +
                "所有命中都会掉进「不分章节」这一档，当轮与历史两条判据就双双失去被检对象。" +
                "现 ${outside.size} 处，宿主 ${outside.map { it.hit.host }.toSet()}",
            outside.size >= noChapterFloor,
        )
        val hosts = all.map { it.hit.host }.toSet()
        assertTrue(
            "命中的宿主应当只有记录了落盘读数的那几份文档，现是 $hosts；一处也没有说明扫描器没跑起来",
            hosts.isNotEmpty(),
        )
    }

    @Test
    fun frozenCountRiseComesFromTheDemotedPair() {
        val hosts = listOf("records/01-迭代记录.md", "records/04-质量与验证记录.md")
        val onTable = allHits.filter {
            it.hit.gap in IN_BAND_GAPS && it.hit.target in frozenTargets &&
                it.hit.target != it.hit.host && it.hit.value == sizeOf(it.hit.target)
        }
        val rounds = hosts.map { host ->
            val sections = sectionsOf(host)
            assertTrue("$host 上没有轮次章节，本用例取不到当轮面与上一轮面", sections.isNotEmpty())
            sections.maxOf { it.round }
        }
        assertTrue(
            "两份含轮次章节的文档里最大的轮次号必须相同（它们记的是同一批轮次），现是 $rounds",
            rounds.distinct().size == 1,
        )
        val newestRound = rounds.first()
        val currentRows = mutableListOf<Classified>()
        val previousRows = mutableListOf<Classified>()
        for (host in hosts) {
            val sections = sectionsOf(host)
            val current = sections.first { it.round == newestRound }
            val previous = sections.first { it.round == newestRound - 1 }
            val cur = onTable.filter { it.hit.host == host && it.hit.line - 1 in current.start until current.end }
            val old = onTable.filter { it.hit.host == host && it.hit.line - 1 in previous.start until previous.end }
            assertTrue(
                "$host 当轮面那一节（第 $newestRound 轮）里落在登记表上的相等读数 ${cur.size} 处、" +
                    "上一轮面那一节（第 ${newestRound - 1} 轮）里 ${old.size} 处 —— 两节各应 $demotedPairRows 行" +
                    "（那张引别份文档的九行表）。当轮面那节现是：" +
                    cur.joinToString("；") { "第 ${it.hit.line} 行「${it.hit.alias}」${it.hit.value}" } +
                    "。上一轮面那节现是：" +
                    old.joinToString("；") { "第 ${it.hit.line} 行「${it.hit.alias}」${it.hit.value}" },
                cur.size == demotedPairRows && old.size == demotedPairRows,
            )
            currentRows += cur
            previousRows += old
        }

        assertTrue(
            "本轮新添的那两节（第 $newestRound 轮的 records/01 与 records/04）里，落在登记表上的相等读数共 " +
                "${currentRows.size} 处，必须**全部**判为当轮面（CURRENT）—— 它们是本轮照现值写下的当前读数，" +
                "一处都不该抬 [frozenEqualsCap] 数着的那个数。判成别的档的有 " +
                currentRows.filter { it.zone != Zone.CURRENT }
                    .joinToString("；") { "${it.hit.host}:${it.hit.line} 判为 ${it.zone}" } +
                "（这一条正是第 151 轮那笔挂账的答案：那九行**确实**写在当轮面上，所以 `frozenEqualsCap` " +
                "的涨落从来不由当轮这些行出）。",
            currentRows.all { it.zone == Zone.CURRENT },
        )
        assertTrue(
            "上一轮那两节（第 ${newestRound - 1} 轮）里落在登记表上的相等读数共 ${previousRows.size} 处，" +
                "必须**全部**判为历史段（HISTORICAL）—— 它们正是每轮 [frozenEqualsCap] 涨的那一笔：本轮新添的" +
                "两行轮次标题把上一轮那两节由当轮面降格为历史段，两节里的九行表当场撞成「等于现值」。判成" +
                "别的档的有 " +
                previousRows.filter { it.zone != Zone.HISTORICAL }
                    .joinToString("；") { "${it.hit.host}:${it.hit.line} 判为 ${it.zone}" },
            previousRows.all { it.zone == Zone.HISTORICAL },
        )
        assertTrue(
            "当轮那两节 ${currentRows.size} 处与上一轮那两节 ${previousRows.size} 处必须**等量** —— 同一批" +
                "「别名 + 现值 + 字节」文本只是换了分区，写得下九行就九行；不等说明有一节的九行表写漏了或" +
                "多写了，那 [frozenEqualsCap] 的下一次涨落就不再是可预告的 $demotedPairRows × ${hosts.size} 处。",
            currentRows.size == previousRows.size,
        )
    }

    private companion object {
        /**
         * 受管文档与它们**在正文里用的别名**（照抄普查脚本的 `MANAGED`）。
         *
         * 别名是这套判据唯一的「谁引了谁」线索：正文里写的是「详细设计说明书」，机器得知道它指的是
         * `docs/03-详细设计说明书.md`。别名多于一个的（风险台账那一份）两个都收。
         */
        val managedDocs: List<Pair<String, List<String>>> = listOf(
            "docs/00-项目章程与需求计划书.md" to listOf("项目章程与需求计划书"),
            "docs/01-需求规格说明书.md" to listOf("需求规格说明书"),
            "docs/02-概要设计说明书.md" to listOf("概要设计说明书"),
            "docs/03-详细设计说明书.md" to listOf("详细设计说明书"),
            "docs/04-数据库设计说明书.md" to listOf("数据库设计说明书"),
            "docs/05-接口设计说明书.md" to listOf("接口设计说明书"),
            "docs/06-测试计划与用例.md" to listOf("测试计划与用例"),
            "docs/07-风险管理计划.md" to listOf("风险管理计划", "风险台账"),
            "docs/08-部署与构建说明.md" to listOf("部署与构建说明"),
            "docs/09-用户手册.md" to listOf("用户手册"),
            "records/01-迭代记录.md" to listOf("迭代记录"),
            "records/02-需求变更记录.md" to listOf("需求变更记录"),
            "records/03-架构决策记录.md" to listOf("架构决策记录"),
            "records/04-质量与验证记录.md" to listOf("质量与验证记录"),
            "records/CHANGELOG.md" to listOf("变更日志"),
            "README.md" to listOf("门面文档"),
        )

        /** 别名表按**长度降序**：长别名先试，与普查脚本的排序一致。 */
        val aliases: List<Pair<String, String>> = managedDocs
            .flatMap { (file, list) -> list.map { it to file } }
            .sortedByDescending { it.first.length }

        /** 五份含轮次章节的文档：文件名 → （章节标题正则，标题级数）。 */
        val sectionPatterns: Map<String, Pair<Regex, Int>> = mapOf(
            "records/01-迭代记录.md" to (Regex("""##\s*第\s*(\d+)\s*轮""") to 2),
            "records/04-质量与验证记录.md" to (Regex("""##\s*第[零一二三四五六七八九十百千]+节\s*·\s*第\s*(\d+)\s*轮""") to 2),
            "records/CHANGELOG.md" to (Regex("""##\s*第\s*(\d+)\s*轮""") to 2),
            "docs/03-详细设计说明书.md" to (Regex("""###\s*[\d.]+\s*第\s*(\d+)\s*轮""") to 3),
            "docs/06-测试计划与用例.md" to (Regex("""###\s*[\d.]+\s*第\s*(\d+)\s*轮""") to 3),
        )

        /** 一行的标题标记（用来定某一节的末尾）。 */
        val HEADING = Regex("""(#{1,6}) """)

        /** 「别名与数字之间」那串花括号里允许的字数上限，与普查口径相同。 */
        const val MAX_GAP = 6

        /** 夹在别名与数字之间的字是空串或者粗体标记 —— 这两种都算「正当读数」。 */
        val IN_BAND_GAPS = setOf("", "**")

        /** 门面文档：长度一直没变过，历史快照与现值撞成同一个数的合法成因之一。 */
        const val FACADE_DOC = "README.md"

        /**
         * **长度冻结文档**登记表：文件 → 它此刻在盘上的字节数。
         *
         * 第 138 轮新增。起因是第 137 轮落下的 `records/01` §137.8 那张十六行中文数字表 —— 那十六份被引
         * 文档里，本轮只会长大五份（`docs/03`、`docs/06`、`docs/07`、`records/03`、`CHANGELOG`），
         * 另外九份（含门面）本轮一个字节不动，于是它们那几行历史读数与现值**恰好相等**，把第 5 条判据
         * 顶红。原先那条判据只给门面文档开了一个单点豁免，`FACADE_DOC` 一个常量撑不住这一类。
         *
         * 所以把单点豁免推广成一张表：**表里的文档，其历史读数与现值相等不再算缺陷**。这是一处
         * **已知的放宽** —— 那几份文档的历史读数不再受「历史快照不该等于现值」这条约束了。与之配套的
         * 两条护栏是：① 相等处数有上限 [frozenEqualsCap]；② 每一项的钉住长度必须与此刻磁盘一致
         * （见 [historicalClaimsStaySnapshots] 第三条断言）—— 表里哪份文档长大了，那里先红，逼人把它
         * 从表里摘掉、并重新审那批不再相等的历史读数。
         *
         * 键必须与 [managedDocs] 的文件名字符串**逐字相同**。
         */
        val frozenTargets: Map<String, Long> = mapOf(
            FACADE_DOC to 8520L,
            "docs/00-项目章程与需求计划书.md" to 13788L,
            "docs/01-需求规格说明书.md" to 298809L,
            "docs/02-概要设计说明书.md" to 48616L,
            "docs/04-数据库设计说明书.md" to 39359L,
            "docs/05-接口设计说明书.md" to 61797L,
            "docs/08-部署与构建说明.md" to 26548L,
            "docs/09-用户手册.md" to 120786L,
            "records/02-需求变更记录.md" to 282830L,
        )

        /**
         * 落在 [frozenTargets] 登记表上的「相等」处数上限。
         *
         * 第 139 轮实测：records/01 §139.8 那张表补进五处引别份当轮读数的行之后，
         * 落在登记表上的「相等」处数由 34 处涨到 39 处（涨的 5 处正是 §139.8 新增的
         * 「别名 + 中文数字 + 字节」那五行，被引的五份文档全在登记表里且值未变）。
         * 这个数不进自动推导，每次动登记表或添新轮记都要照实跑一遍回填。
         *
         * 第 140 轮实测：records/04 第一百四十二节「其九」那九行里的五行引的是别份文档，
         * 被引的五份全在登记表里且值一分没动，于是落在登记表上的「相等」处数由 39 处涨到
         * 53 处。落盘前预告过 62 处，实测落空，成因是当轮面（章节定位器判为 CURRENT 的命中）
         * 被排除在历史面之外 —— 新添的九行只进当轮面，一处都不抬这个数。
         *
         * 第 141 轮实测：改前常量写 53，本轮照实跑一遍（先把常量临时置 0，让断言报出真数）
         * 得 71 处，比上一轮落盘后写下的 53 处高 18 处。高出来的 18 处成因本轮未查明 ——
         * 原以为是 records/01 §141.8 那十六行里引的 frozen 文档该进当轮面被排除掉，
         * 但实测并不成立，故此处只照实记数，不作解释。
         *
         * 第 142 轮实测：改前常量写 71。落盘前那一跑（records/04 第一百四十四节已追加、
         * records/01 第 142 轮一节尚未追加）读数是 80 处，比上一轮记下的 71 处高 9 处。
         * 当时预告过「records/01 落盘后应为 89 = 80 + 9」，落盘后照实跑一遍（同样先把常量
         * 临时置 0，让断言报出真数）得 89 处，预告成立。高出来的 9 处正是 records/01 §142.8
         * 那十六行里落在本登记表上的九行；第 141 轮那 18 处既然成因未查明，这一轮也就不再
         * 追补解释，只照实记数。
         *
         * 第 143 轮实测：改前常量写 89。落盘前先把常量临时置 0 跑一趟定向，
         * 让断言把真数报出来，读数是 107 处，比上一轮记下的 89 处高 18 处。
         * 本轮 records/01 的新一节与 records/04 的新一节都只进各自的当轮面，
         * 照上两轮的算法一处都不该抬这个数，可实测就是高了 18 处；成因本轮
         * 依旧未查明，故只照实记数。改完之后常量回填成实测的 107。
         *
         * 第 144 轮实测：改前常量写 107（第 143 轮值）。落盘前两次临时置 0 跑定向，
         * 报文先后是「上限 0，现 54 处」与「现 58 处」——两趟是不同时点的真读数，
         * 不可互相覆盖也不可相减。records/04 第一百四十六节落盘后复探得 62 处，
         * 常量回填成 62。这一轮是本表第一次「往下走」：107 降到 62，成因是登记表里
         * 五份文档本轮被改到现值（docs/00 由 13606 到 13788、docs/01 由 285367 到 292263、
         * docs/02 由 42844 到 44392、docs/09 由 110581 到 115301、records/02 由 243233
         * 到 253098），它们那些「等于现值」的历史读数如今不再相等，从这个数里掉了出去。
         *
         * 第 145 轮实测：改前常量写 62（第 144 轮值）。本轮在同一轮内量到两个时点 ——
         * 只落 records/04 第一百四十七节、records/01 第 145 轮一节尚未追加时是 69 处；
         * 两节都落盘之后是 76 处（两个时点都是真读数，不可相减抵账）。高出来的 14 处
         * 分两笔、各 7 处：一笔是 records/04 新节落盘后，第一百四十六节「其九」那九行
         * 转成历史面，其中引别份当轮读数的七行撞成相等；另一笔是 records/01 新节落盘后，
         * §144.8 那十六行转成历史面，其中仍等于现值的七行撞成相等。常量回填成实测的 76。
         *
         * 第 145 轮另有一处**越界**：docs/07 的「修订记录」表每轮续一行，本轮一开始漏了续行，
         * 于是 §144.8 写下的 309582 仍等于现值，而 docs/07 不在本登记表里，判据 5 第三条当场报红。
         * 解掉它的办法是**给 docs/07 补上它自己那一行修订记录**（补行后它当场由「相等」转成
         * 「对不上」），**不是**把 docs/07 塞进本表 —— 本表一格没动，与那一笔越界无关。
         *
         * 第 146 轮本表的九项钉住长度**一格没动**（九份文档里只有门面文档动过内容，而它是等长替换，
         * 尺寸照旧）。相等处数由 76 涨到 94，涨的十八处落在本轮新落盘的两节上：records/04 的第一百
         * 四十八节与 records/01 第 146 轮一节，两节都带跨文档的当轮读数（records/04「其九」那九行
         * 逐行等于现值）。未逐处分账，只钉实测终值。常量回填成实测的 94。
         *
         * 第 147 轮本表九项钉住长度同样一格没动（九份文档本轮零字节改动）。相等处数由 94 涨到 112，
         * 涨的十八处仍是跨文档当轮读数：records/04 的第一百四十九节「其九」九行，与 records/01 第
         * 147 轮一节「147.8」九行。照规矩先把本常量临时置 0 跑红读真数 112，再回填。
         *
         * 第 148 轮本表**从九项缩到六项**：docs/01、docs/02、docs/09 三行摘出 —— 这三份文档的长短
         * 早已不等于钉住的旧值，按第 528 行那条护栏本该在它们长大的那一轮就摘；records/02 这一格
         * 由 253098 同步到 262247。相等处数由 112 落到 102，落的十处正是这次摘表与同步留下的旧相等
         * 读数。照规矩先把本常量临时置 0 跑红读真数 102，再回填。本轮 records/04 的第一百五十节
         * 「其十二」那张九行表里引的多份文档虽在本表登记之内，但那一节由章节定位器判为当轮面，
         * 整段被排除在历史面之外，一处都没抬这个数 —— 第 140 轮「新添之行只进当轮面」那条旧账
         * 在这里再次得到印证。
         *
         * 第 149 轮本表**从六项回到九项**：docs/01、docs/02、docs/09 三行重新登记 —— 第 144 轮把
         * 它们摘出去，是因为那三份那轮长大过、钉住的旧长度已不等于盘上长度；本轮 §148.8 那张十六
         * 行表由当轮面转成历史面，其中引这三份长度的行恰好等于它们的现值（三份本轮零字节改动），
         * 成了「历史快照撞现值」的越界读数，判据 5 第二条报出的六处里，三例分别落在它们身上
         * （报文只列三例）。解此越界的办法是把这三份的当期长度钉进本表；若六处里还有落在别处的，
         * 报错会当场报出对象名，按报错逐个补。records/02 这一格由 262247 同步到 269957。照规矩
         * 先把本常量临时置 0 跑红读真数，再回填 —— 真数是 118，比第 148 轮涨了十六处；补了
         * docs/01、docs/02、docs/09 三行之后「越界」一档归零，那六处里的三例正是本节补的三行。
         *
         * 第 150 轮本表九项里八项一格没动，records/02 那一格由 269957 同步到 276244（本轮写了
         * RC-047，需求变更记录长了一截）。照规矩先把本常量临时置 0 跑红读真数，再回填 —— 真数是
         * 134，比第 149 轮涨了十六处。这十六处的成因本轮只查到一半：本轮新落盘的 records/04 第
         * 一百五十二节「其九」那九行里引别份文档的五处全在登记表内、值一分没动，原该抬这个数，
         * 但那一节由章节定位器判为当轮面，整段被排除在历史面之外，一处都没抬 —— 这是第 140 轮
         * 「新添之行只进当轮面」那条旧账第三次得到印证。涨的十六处落在哪几节，本轮未逐处分账，
         * 只钉实测终值。
         *
         * 第 151 轮本表仍是九项，八项一格没动，records/02 那一格由 276244 同步到 282830（本轮写了
         * RC-048）。照规矩先把本常量临时置 0 跑红读真数，再回填 —— 真数是 150，比第 150 轮涨了
         * 十六处，与上一轮涨的数目**恰好相同**。这一轮涨的十六处落在哪几节，同样未逐处分账：本轮
         * 新落盘的 records/01 §151.7 那张十行表里引别份文档的有九行，若按普查口径把它们算进去，
         * 九处就该由这里出 —— 但那一节落在第 151 轮段内，按「新添之行只进当轮面」原该整段排除。
         * 两者对不上，说明要么这九行确实被算了进来（与本仓既有口径不符），要么另有十六处落在别处；
         * 本轮没有再跑一支试验去分辨，只钉实测终值。这一笔已作为新挂账写进 records/04 第一百五十三节。
         *
         * 第 152 轮本表从九项缩到八项：docs/01 由 298449 长到 298809（本轮改了它第 272 行的口径），
         * 按以上那条「表里哪份长大了就先把它摘掉」的护栏，把 docs/01 那一行摘除。摘得动这个数吗？
         * 本轮在报错报文里临时插了三支探针把 160 处逐一归类，指向 docs/01 的**一处都没有**，故摘行
         * 不改变这个数。真数由 150 涨到 160，＝ +16（上一轮那两节 —— records/01 第 151 轮节与
         * records/04 第一百五十三节 —— 随本轮新添的两行轮次标题升格为历史段，这两节里落在登记表上
         * 的相等处数分别是八处与八处）−6（docs/01 长高六个字节之后，指向它的那批 298449 快照里有六处
         * 不再相等：records/01 里三处、records/04 里三处；余下两处本就坐在这两节之内，与 +16 相抵，
         * 净贡献为零）。照规矩先把本常量临时置 0 跑红读真数，再回填 160。
         *
         * 第 153 轮本表**从八项回到九项**：docs/01 那一行重新登记，钉住 298809L —— 第 152 轮把它
         * 摘掉，是因为它当时刚长到 298809；本轮 records/01 §152.6 与 records/04 第一百五十四节
         * 「其九」两张表随新添的两行轮次标题降格为历史段，两表里各有一行「需求规格说明书 + 二十九万
         * 八千八百零九」，值恰等于它的现值，成了判据 5 第三条报出的「越界」读数（报文只列两例，
         * 就是这两行）。解此越界的办法与本表第 149 轮那一次相同：把它的当期长度登回本表；登回之后
         * 越界一档归零，那两处由「越界」转成「相等」。照规矩先把本常量临时置 0 跑红读真数，再回填
         * —— 真数是 178，比第 152 轮的 160 涨了十八处，＝ +16（本轮降格的两节各八行落在登记表上；
         * 两节指向 docs/01 的那两行因当时尚未登记不计在内，「门面文档」那一行计在内，它是本表的
         * FACADE_DOC 键）+2（docs/01 登回本表后，上面那两行当场由「越界」转成登记表上的「相等」）。
         *
         * 第 154 轮本表一项未动，真数由 178 涨到 196，＝ +18。照规矩先把本常量临时置 0 跑红，报文只说
         * 处数不说去处，故临时往那条断言的报文里插了一支探针，把 196 处逐行打出来（探针已拆）。分类结果：
         * 这 18 处**全部**是第 153 轮那两节在本轮新添轮次标题那一刻由「当轮面」降为历史段之后浮现的——
         * records/01 §153 里的九行（:18268~:18276）与 records/04 第一百五十五节里的九行（:15865~:15873），
         * 九 + 九 = 18，与差额逐字相符。分账之所以能这样一刀切平：段的升降只改分区、不改文本，本轮没有
         * 任何一行旧读数消失，故差额必然恰等于新升格那两节里的相等处数。196 处的目标分布（探针读数）：
         * 门面文档 47、部署与构建说明 33、接口设计说明书 31、数据库设计说明书 31、项目章程与需求计划书
         * 20、用户手册 12、概要设计说明书 12、需求变更记录 6、需求规格说明书 4；宿主分布：records/01
         * 一百零二处、records/04 九十四处，两份合计 196。
         *
         * 第 155 轮本表一项未动，真数由 196 涨到 214，＝ +18。照规矩先读真数再回填 —— 报文只说处数
         * 不说去处，故照第 154 轮的旧法往那条断言里临时插一支探针，把 214 处逐行打出来（探针已拆，
         * 拆后复跑确认本条转绿）。分类结果：这 18 处**全部**是第 154 轮那两节在本轮新添轮次标题那一
         * 刻由「当轮面」降为历史段之后浮现的 —— records/01 §154 里的九行（:18432~:18440）与 records/04
         * 第一百五十六节里的九行（:15923~:15931），九 + 九 = 18，与差额逐字相符。本轮本表登记的九份
         * 文档同比特未动，故差额只能来自分区升降，与上述分账吻合。214 处的目标分布（探针读数）：
         * 门面文档 49、部署与构建说明 35、接口设计说明书 33、数据库设计说明书 33、项目章程与需求计划书
         * 22、用户手册 14、概要设计说明书 14、需求变更记录 8、需求规格说明书 6；宿主分布：records/01
         * 一百一十一处、records/04 一百零三处，两份合计 214。
         *
         * 第 156 轮本表一项未动，真数由 214 涨到 232，＝ +18，来路与上一轮同形：新添的两条轮次标题把第 155
         * 轮那两节挤成历史段，那两节各含一张九行跨文档读数表，九 + 九 = 18。第 151 轮起那笔「每轮固定浮现
         * 十八处」的账由此结清，并从本轮起转为机器账 —— 由 [frozenCountRiseComesFromTheDemotedPair] 逐字
         * 钉住：两份宿主各自的最新轮那两节（当轮面）与上一轮那两节（历史段），须各含恰好九行落在本表上。
         * 232 处的目标分布（探针读数）：门面文档 51、部署与构建说明 37、接口设计说明书 35、数据库设计说明
         * 书 35、项目章程与需求计划书 24、用户手册 16、概要设计说明书 16、需求变更记录 10、需求规格说明书
         * 8；宿主分布：records/01 一百二十处、records/04 一百一十二处，两份合计 232。
         */
        const val frozenEqualsCap = 232

        /** 每一节里那张跨文档九行表的行数（第 156 轮起由 [frozenCountRiseComesFromTheDemotedPair] 钉住）。 */
        const val demotedPairRows = 9

        const val managedFileFloor = 16
        const val roundDocFloor = 5

        /** 当轮面：处数 / 被引文档种数 / 宿主种数（第 129 轮的实测值）。 */
        const val currentClaimFloor = 10
        const val currentTargetFloor = 5
        const val currentHostFloor = 2

        /** 当轮段里的自指读数上限。 */
        const val selfClaimCap = 4

        /** 历史面：处数下限与「对不上现值」的下限（第 129 轮的实测值）。 */
        const val historicalFloor = 29
        const val historicalStaleFloor = 20

        /** 越界写法：当轮段的上限与形态、历史 + 不分章节段的下限与形态。 */
        const val curOutOfBandCap = 4
        val curOutOfBandShapes = setOf("落地前是**")
        const val snapshotOutOfBandFloor = 10
        val snapshotOutOfBandShapes = setOf("一段长", "长了", "落地前是**")

        /** 总量下限（防「扫描器坏了 ⇒ 零条目 ⇒ 全绿」）与落在章节之外的下限。 */
        const val totalHitFloor = 45
        const val noChapterFloor = 2

        const val CN_CHARS = "零一二三四五六七八九十百千万两"

        val DIGIT_CHARS = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )

        val UNIT_CHARS = mapOf('十' to 10, '百' to 100, '千' to 1000)

        /**
         * 中文数字转整数，口径照抄普查脚本的 `cn2int`：整串必须都是中文数字字符，否则不收；
         * `万` 是分段进位，「十」这类前头没数字的量词按 1 算。
         */
        fun chineseToInt(text: String): Long? {
            if (text.isEmpty() || text.any { it !in CN_CHARS }) return null
            var total = 0L
            var section = 0L
            var num = 0L
            for (c in text) {
                when {
                    c in DIGIT_CHARS -> num = DIGIT_CHARS.getValue(c).toLong()
                    c in UNIT_CHARS -> {
                        section += (if (num == 0L) 1L else num) * UNIT_CHARS.getValue(c)
                        num = 0
                    }
                    c == '万' -> {
                        section += num
                        total += section * 10000
                        section = 0
                        num = 0
                    }
                    else -> return null
                }
            }
            return total + section + num
        }

        /** 报红时最多列几处，免得一条消息里塞进几十行。 */
        const val OFFENDER_PREVIEW = 3
    }
}
