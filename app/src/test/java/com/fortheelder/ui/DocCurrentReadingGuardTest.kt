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
 * ## 七条用例
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
 *    其中与现值**不等**的不少于 [historicalStaleFloor]；并且**每一处「恰好相等」的，被引对象都必须是门面
 *    文档** [FACADE_DOC] —— 那份文件的长度一直没变过，历史快照与现值撞成同一个数是**等长滚动造成的巧合**，
 *    不是有人在回改历史叙述。本仓现存的 9 处相等全落在这一种成因上。
 * 6. [outOfBandClaimsArePreValuesNotCurrentReadings]：`gap` 既不是空也不是 `**` 的那些读数（= 作者在别名与
 *    数字之间夹了别的词）单独分段判。当轮段里这类处数不得超过 [curOutOfBandCap]、形态必须登记在
 *    [curOutOfBandShapes] 里、值不许等于现值；历史段与不分章节段里处数不低于 [snapshotOutOfBandFloor]、
 *    形态必须登记在 [snapshotOutOfBandShapes] 里、值同样不许等于现值。**登记形态而不登记逐轮数值**：
 *    这类写法的意思是「落地前是多少」或者「长了多少」，词语比数字稳。
 * 7. [claimSurfaceIsCounted]：总命中处数不低于 [totalHitFloor]、落在轮次章节之外的不少于 [noChapterFloor]。
 *    这是第二道防线：章节定位器一旦坏掉，所有命中都会掉进「不分章节」这一档，第 3 ~ 6 条就双双变成空转的绿。
 *
 * ## 它不保证什么
 *
 * - **只认中文数字形态**。阿拉伯数字（`1,027,725 字节`）一律不判，这类写法整片逃逸。这不是小事：
 *   第 128 轮正文里那一处真缺陷 `从 1,019,141 字节、6,646 行长到 1,027,682 字节、6,715 行`
 *   （真值 1,027,725）就正是阿拉伯形态，本判据一个字也管不了。
 * - **`gap` 超过 [MAX_GAP] 字符的写法整片逃逸**（把别名与数字用一句话隔开就会这样）。上限取 6 是普查时的
 *   口径：真值里最长的 `gap` 恰好 6 个字（`落地前是**`），再宽就会开始把不相关的句子收进来。
 * - **只判字节，不判行数、不判摘要**。同一句里写着的行数、md5 前缀对不对，这里不看。
 * - **反向面判得间接**。第 5 条不是「历史快照必须不等现值」（那会把门面文档那 9 处巧合判成缺陷），
 *   而是「不等现值的数量有下限 + 恰好相等时被引对象必须是门面文档」两条合起来逼近。
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
        val unexpected = equals.filter { it.hit.target != FACADE_DOC }
        assertTrue(
            "历史段里与现值**恰好相等**的读数，本仓只允许一种成因：别名指的是门面文档 $FACADE_DOC ——" +
                "那份文件的长度一直没变过，于是历史快照与现值撞成同一个数，这是等长滚动造成的巧合、" +
                "不是有人在回改。现 ${equals.size} 处相等，其中 ${unexpected.size} 处的被引对象不是门面文档：" +
                unexpected.take(OFFENDER_PREVIEW).joinToString("；") {
                    "${it.hit.host}:${it.hit.line} 「${it.hit.alias}」写作 ${it.hit.value} = 现值（目标 ${it.hit.target}）"
                },
            unexpected.isEmpty(),
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

        /** 门面文档：长度一直没变过，历史快照与现值撞成同一个数的**唯一**合法成因。 */
        const val FACADE_DOC = "README.md"

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
