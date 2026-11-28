package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档里写的 `docs/0X:NNN` 到底还指不指得着东西」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * [DocLineRefGuardTest] 那七条用例管的全是 `文件.kt:行号`——指向**源码**的位置宣称。文档之间还有
 * 另一类位置宣称，一个字都不在它的管辖里：`` `docs/05:757` `` 这种**文档自引用**（短形：`docs/` 加
 * 两位编号加冒号加行号）。它跟源码行号一样是最容易过期的一类宣称（被引的那份文档正文一动，写下的
 * 数字就漂），而它从第 1 轮到第 107 轮**从来没有过任何机器判据**——十份活文档里现存 67 处这样的引用，
 * 全靠人读。这条用例是**第一次把「文档之间的短形引用」当输入**。
 *
 * ## 它做什么
 *
 * 把 `docs/` 下十份活文档（`00`~`09`）正文里**所有** `docs/0X:NNN` 一股脑抓出来，逐条比
 * 「那个目标行还存不存在、上面有没有字」，用**四条**用例交叉钉住：
 *
 * 1. [everyLiveDocIsRead]：十份活文档一份不少都在，`00`~`09` 十个编号一个不缺。少读一份文档
 *    就等于把指向它的所有引用整个豁免掉——那是这条用例最想防的假绿。
 * 2. [everyDocCrossRefPointsAtANonBlankLineInsideItsTarget]：每一处 `docs/0X:NNN` 的目标行号
 *    都必须落在目标文档的行数之内（**越界一律红，没有豁免余地**），而且那一行必须**有字**
 *    （`.isBlank()` 为假的才合格）；指着空行的、**不在 [exempt] 里的当场红**。
 * 3. [everyDocCrossRefIsCounted]：覆盖面下限三道一道不少——命中**处数**、有引用的**文档数**、
 *    互不相同的**键数**。正则写歪、扫描目录被换、文档被删空，都会退化成「零命中 ⇒ 一律通过」，
 *    这三把下限把那种假绿挡住。**第 108 轮实测：67 处 / 5 份文档 / 67 个互不相同的键。**
 * 4. [everyExemptDocCrossRefIsStillBroken]： [exempt] 是**双向**断言——有坏键没记进来 ⇒ 红；
 *    记着的那条**已经不再是坏键**（目标文档被增行、那一行又有字了）⇒ 也红，逼着人来重新判定一次，
 *    而不是让一条过期豁免悄悄躺在那儿。
 *
 * ## 这条用例的由来（第 108 轮的真事件）
 *
 * 第 107 轮给 [DocTableShapeGuardTest] 补第七格「孤立单行块」时，顺手把这条守卫的普查脚本
 * （`build/r108/xref.py`）第一次跑起来，当场查出**六处漂了的文档自引用**：
 *
 * - `docs/03` 三处 + `docs/06` 一处，写的都是 `` `docs/05:758` ``，而 `docs/05` 的 758 是**空行**。
 *   真值在 **757**：那一行正是「谁用」表里 `CopyRules` 那一格的现值。错因是**第 101 轮**在 `docs/05`
 *   里删掉了 IF-14h 代码块中 `fun byLabel(label: String): AnchorSlot?` **一行**（1,023 → 1,022 行），
 *   其后所有行号上移一位，这四处引用无人复核。
 * - `docs/09` 两处（正文一处 + 版本记录 1.0 那行一处），写的都是 `` `docs/07:538` ``，而 538 是空行。
 *   那是**第 15 轮**写下的旧行号（`docs/09-用户手册` 落地的当轮），此后 `docs/07` 长了 198 行，
 *   那串数字一直没动。第 108 轮当时的真值是 **604**（第 144 轮实测 `docs/07` 增行后漂到 **640**，
 *   `docs/09` 那两处随之改到 640）——`docs/07` §3.1 那张「需立即处理（红）」表里 R-09 那一行，
 *   原文是「…落点：第 15 轮的 `docs/09-用户手册` 要把「怎么用」讲得比界面更清楚一点…」
 *   （全文件 `grep "讲得比界面更清楚"` 唯一命中处）。
 *
 * 六处都在本轮**逐处读过上下文之后**回改成现值（`docs/03` ×3、`docs/06` ×1、`docs/09` ×2），
 * 回改后复扫 `BADCOUNT` 只剩 1 处，就是 [exempt] 里那一条。**这条用例就是把那次人工普查固化下来。**
 *
 * ## 它不保证什么
 *
 * - **不核语境**。目标行有字不等于那段话说对了地方——可能引错章节、引错语义。这条用例只比「那个数字
 *   还在不在范围里、那一行有没有字」，这是唯一不必看语境就能机械判定的两种错。
 * - **只管十份活文档**。`records/` 下五份过程记录与仓库根的 `README.md` **一个字不扫**：那里面的行号
 *   大多写的是「当时那份文档多少行、哪一行写着什么」，属**历史叙述**（当轮当时的快照），按
 *   `records/03` ADR-070 不回改、也就不用机器判据兜。这与 [DocLineRefGuardTest] 第 7 条把 `records/`
 *   一起扫是**两回事**——那一条只查「行号有没有超出我方源码文件的行数」，越界是物理事实、与语境无关。
 * - **不核指向的写法形状**。只认 `docs/0X:NNN`（两位编号、一个十进制行号）。`docs/03:1348-1360`
 *   这种区间、`docs/03 §7.9` 这种按节引用、`records/01` 这种跨目录引用都看不见——按节引用没有可机检的
 *   行号，跨目录引用在 ADR-070 之外。
 * - **不改文档**。它只报红，不替你回改；六处坏键是本轮人读上下文后逐处改的，哪一处该回改成现值、
 *   哪一处该按 ADR-070 留着，**没有机器判据**，这条用例只是把「改完之后不许再有坏键」钉住。
 * - **不是「文档与代码一致」的证明**。源码那一侧归 [DocLineRefGuardTest] 七条，屏幕上的字归
 *   [DocScreenCopyGuardTest]，表格形状归 [DocTableShapeGuardTest]——各管一段，谁也不替谁作保。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。第 108 轮只是把「文档之间的短形引用会漂」这句话从**完全没有兜底**
 * 变成**有了兜底**（十份活文档之内、只判两种机械错），并修掉了当场查出的六处坏键。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。下限自检的纪律抄自 [DocLineRefGuardTest] 与 [CopyCountGuardTest]。
 */
class DocCrossRefGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 十份活文档所在目录。`records/` 与 `README.md` 不在管辖内（见类注释）。 */
    private val docsRoot: File = File(repoRoot, "docs")

    /**
     * 短形引用：`docs/` 加**两位**编号（只收 `00`~`09`）加冒号加十进制行号。
     * 两位这个约束是刻意的：`docs/10-…` 将来若出现，它接的是另一套编号，不该被这条静默纳入。
     */
    private val crossRef = Regex("docs/(0[0-9]):(\\d+)")

    /** 十份活文档的编号下限（`00`~`09`，现值 10）：少读一份就等于把指向它的引用整个豁免。 */
    private val liveDocFloor = 10

    /**
     * 命中**处数**下限（第 108 轮实测 67 处，取值 60 留一截余量）。「处」是**一段** `docs/0X:NNN`
     * 写法，同一行上写两串算两处。正则写歪、扫描目录被换走、或十份活文档被删空，都会退化成
     * 「零命中 ⇒ 一律通过」。
     */
    private val hitFloor = 60

    /**
     * 有引用的**文档份数**下限（第 108 轮实测 5 份：`docs/01` 3 处、`docs/03` 31 处、`docs/06` 21 处、
     * `docs/07` 8 处、`docs/09` 4 处；其余五份活文档一处也不写短形引用，是正常的）。
     * 取 5 与实测齐平，是**刻意**的：某一份文档里的引用被整片删走（`docs/03` 那 31 处是全仓最多的
     * 一族）就该有人来看一眼，而不是被「反正还有别的文档」糊过去。
     */
    private val hitFileFloor = 5

    /**
     * 互不相同的**键数**下限（第 108 轮实测 67 个，取值 60）。键是「源文件:源行 → 目标串」三元组，
     * 与 [hitFloor] 是两个量：67 处正好展开成 67 个互不相同的键（暂无一键两处的写法）。
     * 它与 [hitFloor] 一起挡住同一层假绿的另一个面。
     */
    private val distinctKeyFloor = 60

    /**
     * 允许「指着空行」的那些引用：键是 `源文件名:源行 -> 目标串`，值是「为什么它指着空行仍然正当」。
     *
     * **第 108 轮没过第二遍**：全量 67 处逐条比过目标行，指着空行的只有 **1** 处（就是表里这条），
     * 其余 66 处目标行上都有字；越界的一处也没有。表里这条是**历史叙述**——`docs/03` §8.5x 那段
     * 在讲第 101 轮查出的文档假话，原文逐字写着「**`docs/05:762`（改前）** 写「三处消费方一律调
     * `CopyRules.hit`」」，同段紧接着写「改成…」，明确标了「改前」，是当轮快照。那一行现在（第 101 轮
     * 删掉 IF-14h 里一行之后）是空行，按 `records/03` ADR-070 **不回改**。
     * 它的邻居 `docs/03:5306` 引的是 `docs/05:553（改前）`，同一族，但那一行自己还有正文、
     * 不是空行，所以进不了这张表——这就是表里只有一条的原因。
     *
     * 第 108 轮写下这条豁免时，它的**源行**是 `docs/03` 第 5281 行；此后 `docs/03` 一路增行，
     * 第 144 轮实测漂到第 **5307** 行——键跟着改，理由文字一字未动。第 148 轮 `docs/03`
     * 中段又插进一行，源行再漂一格到第 **5308** 行，键照改，理由文字仍是一字未动。
     *
     * 这张表是**双向**判据（见 [everyExemptDocCrossRefIsStillBroken]）：有坏键没记进来 ⇒ 红；
     * 记着的这条不再指着空行了 ⇒ 也红。
     */
    private val exempt = mapOf(
        "03-详细设计说明书.md:5308 -> docs/05:762" to
            "第 101 轮的历史叙述：那一行逐字写着「`docs/05:762`（改前）写「三处消费方一律调 " +
            "`CopyRules.hit`」」，同段已写明改成什么，属当轮快照，按 ADR-070 不回改",
    )

    /** 一次扫描里的**一处**命中。 */
    private data class Hit(
        /** 源文档文件名（`docs/` 下的名字，如 `03-详细设计说明书.md`）。 */
        val source: String,
        /** 源文档里的行号（1 起）。 */
        val sourceLine: Int,
        /** 逐字命中的那一串（如 `docs/05:757`）。 */
        val text: String,
        /** 目标文档的编号（`00`~`09`）。 */
        val targetKey: String,
        /** 目标行号。 */
        val targetLine: Int,
    ) {
        /** 登记 / 报错用的键：`源文件:源行 -> 目标串`。 */
        val key: String get() = "$source:$sourceLine -> $text"
    }

    /**
     * 十份活文档的文件清单（`docs/` 下所有 `.md`，按名字排）。
     *
     * 行数一律按 `readText().split("\n")` 数、并丢掉末尾那个空元素——与第 108 轮那次普查脚本
     * （`build/r108/xref.py` 的 `raw.split("\n")` 加 `pop()`）逐字同口径，好让两边读数可以直接对账。
     * EOL 是 CRLF 还是 LF 都不影响行数；CRLF 时行尾会留一个 `\r`，所以判「这行有没有字」用的是
     * `isBlank()`（`\r` 算空白）而不是 `isEmpty()`。
     */
    private fun liveDocs(): List<File> =
        (docsRoot.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".md") }
            .sortedBy { it.name }

    /** 按上面那条口径取一份文档的每一行。 */
    private fun linesOf(file: File): List<String> {
        val parts = file.readText().split("\n")
        return if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
    }

    /** 全量扫描：返回（每份活文档的行、全部命中处）。 */
    private fun scan(): Pair<Map<String, List<String>>, List<Hit>> {
        val docs = liveDocs()
        val linesByKey = LinkedHashMap<String, List<String>>()
        for (doc in docs) linesByKey[doc.name.substring(0, 2)] = linesOf(doc)

        val hits = mutableListOf<Hit>()
        for (doc in docs) {
            for ((offset, line) in linesOf(doc).withIndex()) {
                for (match in crossRef.findAll(line)) {
                    hits += Hit(
                        source = doc.name,
                        sourceLine = offset + 1,
                        text = match.value,
                        targetKey = match.groupValues[1],
                        targetLine = match.groupValues[2].toInt(),
                    )
                }
            }
        }
        return linesByKey to hits
    }

    /**
     * 第 1 条：十份活文档一份不少地在，`00`~`09` 十个编号一个不缺。
     *
     * 这是**所有别的判据的地基**：`docs/` 被换成只剩一份、或某一份被改名成 `10-…`（两位数字约束
     * 会让它整个掉出管辖），指向它的引用就都「查无目标」或「静默不扫」——前者会红，后者不会。
     * 所以这里正面要求十份都在、编号正好是那十个。
     */
    @Test
    fun everyLiveDocIsRead() {
        val docs = liveDocs()
        assertTrue(
            "`docs/` 下只数到 ${docs.size} 份 `.md`，低于下限 $liveDocFloor：活文档被删、被移走、" +
                "或被改成了别的后缀，指向它们的引用就会整片失去判据（不是报红，而是静默不扫）",
            docs.size >= liveDocFloor,
        )
        val keys = docs.map { it.name.substring(0, 2) }.toSet()
        val missing = (0..9).map { "%02d".format(it) } - keys
        assertTrue(
            "`docs/` 下找不到编号 $missing 的活文档：十份 00~09 是这套文档体系的全集，" +
                "少一份就等于给它整个豁免",
            missing.isEmpty(),
        )
    }

    /**
     * 第 2 条：每一处 `docs/0X:NNN` 的目标行号都落在目标文档的行数之内（越界**一律红**），
     * 且那一行**有字**（指着空行的、不在 [exempt] 里的当场红）。
     *
     * 两种判据的「硬」不一样，是刻意的：**越界**是物理事实——目标文档根本没有那一行，写下的数字
     * 一定错，没有任何语境能翻案；**空行**则要先看上下文（有可能是刻意引「改前」的行号，见 [exempt]），
     * 所以空行留了豁免口，越界没留。
     */
    @Test
    fun everyDocCrossRefPointsAtANonBlankLineInsideItsTarget() {
        val (linesByKey, hits) = scan()

        val outOfRange = mutableListOf<String>()
        val blank = linkedSetOf<String>()
        for (hit in hits) {
            val target = linesByKey[hit.targetKey]
            if (target == null) {
                outOfRange += "${hit.key}：`docs/` 下没有编号 ${hit.targetKey} 的活文档"
                continue
            }
            if (hit.targetLine < 1 || hit.targetLine > target.size) {
                outOfRange += "${hit.key}：目标文档只有 ${target.size} 行，第 ${hit.targetLine} 行不存在"
            } else if (target[hit.targetLine - 1].isBlank()) {
                blank += hit.key
            }
        }
        assertTrue(
            "有 ${outOfRange.size} 处 `docs/0X:NNN` 越出了目标文档的行数（越界没有豁免口）：\n" +
                outOfRange.joinToString("\n") { "  $it" } +
                "\n把那个数字回改到现值；若是要引「改动之前」的行号，请在正文里写明「（改前）」，" +
                "本用例仍会报红一次，那时再按 ADR-070 决定是回改还是登记进 [exempt]",
            outOfRange.isEmpty(),
        )

        val unregistered = blank - exempt.keys
        assertTrue(
            "有 ${unregistered.size} 处 `docs/0X:NNN` 指着的目标行是**空行**，而 [exempt] 里没有记账：\n" +
                unregistered.sorted().joinToString("\n") { "  $it" } +
                "\n指着空行的引用等于没指——要么把数字回改到那一行真正在的现值，" +
                "要么（只有当它确实在引「改动之前」的行号时）补进 [exempt] 并写清理由",
            unregistered.isEmpty(),
        )
    }

    /**
     * 第 3 条：三道覆盖面下限。命中处数 / 有引用的文档份数 / 互不相同的键数，任何一道退化成 0
     * 或大幅缩水都红。
     *
     * 这一条不判任何一处引用的对错，只问「同一条尺子还是不是一个量级」。它与第 2 条是一对：
     * 第 2 条在「扫到了的」里面挑错，这一条保证「扫到的」不是空集。
     */
    @Test
    fun everyDocCrossRefIsCounted() {
        val (_, hits) = scan()

        assertTrue(
            "十份活文档里只抓到 ${hits.size} 处 `docs/0X:NNN`，低于下限 $hitFloor" +
                "（第 108 轮实测 67 处）：正则失效、`docs/` 被换走、或十份活文档被删空，" +
                "这条用例都会退化成「零命中 ⇒ 一律通过」——不许这么绿",
            hits.size >= hitFloor,
        )
        val filesWithRefs = hits.map { it.source }.toSet()
        assertTrue(
            "十份活文档里只有 ${filesWithRefs.size} 份写着短形引用，低于下限 $hitFileFloor" +
                "（第 108 轮实测 5 份：01 / 03 / 06 / 07 / 09）：某一份文档里那族引用被整片删走" +
                "（`docs/03` 那 31 处是全仓最多的一族）就该有人来看一眼",
            filesWithRefs.size >= hitFileFloor,
        )
        val distinct = hits.map { it.key }.toSet()
        assertTrue(
            "只认出 ${distinct.size} 个互不相同的「源文件:源行 -> 目标串」，低于下限 $distinctKeyFloor" +
                "（第 108 轮实测 67 个）：同上，这是同一层假绿的另一个面",
            distinct.size >= distinctKeyFloor,
        )
    }

    /**
     * 第 4 条：[exempt] 的**反向**断言——记在表里的每一条，现在**还必须**是坏键（指着空行）。
     *
     * 一个豁免口一旦留下就不再被人看：目标文档长了几行、或被引的那一行又有字了，这条豁免就变成
     * 「替一处已经不坏的引用作保」；更糟的是它会**掩盖下一次**同键的真坏（同一对行号再被引到时，
     * 第 2 条的报错会被这张表静静吃掉）。所以这里正面要求：表里的键要真的出现在本轮的坏键集合里。
     * 与第 2 条的 `blank - exempt.keys` 合起来，这张表就是双向的。
     */
    @Test
    fun everyExemptDocCrossRefIsStillBroken() {
        val (linesByKey, hits) = scan()

        val blank = linkedSetOf<String>()
        for (hit in hits) {
            val target = linesByKey[hit.targetKey] ?: continue
            if (hit.targetLine in 1..target.size && target[hit.targetLine - 1].isBlank()) {
                blank += hit.key
            }
        }
        val stale = exempt.keys - blank
        assertTrue(
            "[exempt] 里有 ${stale.size} 条已经不是坏键了（被引的那一行又有字了、或那处引用被改掉了），" +
                "表却还在替它们作保——过期豁免会吃掉下一次同键的真报红：\n" +
                stale.sorted().joinToString("\n") { "  $it：${exempt.getValue(it)}" } +
                "\n要么把这条从表里删掉，要么重新判定它现在是不是还该留着",
            stale.isEmpty(),
        )
    }
}
