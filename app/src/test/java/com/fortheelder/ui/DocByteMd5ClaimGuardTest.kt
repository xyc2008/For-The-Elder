package com.fortheelder.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档里写的『`某文件.kt`（N 行 / M B / md5 H）』还对不对得上磁盘」的守卫。
 *
 * [DocCodeLineClaimGuardTest]（第 110 轮）管 `文件名 + 行数`，[DocLineRefGuardTest] 管 `文件.kt:行号`，
 * [DocCrossRefGuardTest] 管 `docs/0X:NNN`，[DocTableShapeGuardTest] / [DocHeadingStructureGuardTest]
 * 管表格与标题的形状。同一句话里往往还写着**字节数**与 **md5**（`` `CopyRules.kt`（**4,284 B / 66 行 /
 * md5 `616477b5…`**） ``），这一类从第 1 轮到第 110 轮**没有任何机器判据**——第 110 轮当场抓到一处真假读数
 * （五处副本把磁盘实测 `23,843 B` 写成 `23,945 B`）之后，把「字节数/md5 无判据」挂成了旧账，本条即该笔账
 * 的第一次收口。
 *
 * ## 判据（第 111 轮定案，实现在 [judge]）
 *
 * 一条**括号读数** = `` `X.kt` `` 后随（至多四个非括号字符 +）左括号，括号内至多 220 字符。若括号里同时有：
 *
 * 1. 行数 N，**且** N 恰好等于该 `.kt` 今天在磁盘上的行数（同名多份任一份相等即算），**且**
 * 2. 字节数 M 或 md5 H，
 *
 * 那么这一处就在讲「**今天**这份文件是什么样」，M / H 必须等于磁盘实测值。
 *
 * 「N 对得上」这一条是**锚**：它把「讲今天的读数」与「讲当轮当时的快照」分开。让它当锚是因为
 * 它是**机械可判**的（行数是磁盘事实），不依赖任何语义判断。
 *
 * ## 为什么不用「历史叙述」那条词表当锚（第 111 轮的实测发现）
 *
 * 本条守卫的设计稿原本打算照抄 [DocCodeLineClaimGuardTest] 的三条历史规则（本行/标题链含「第 N 轮」等），
 * 但**实测把它推翻了**：按 `build/r111/design111.py` 的普查，行数对得上且带字节/md5 的共 **74** 处，
 * 其中对不上的 **5** 处，**5 处全是历史快照**（第 37 轮的 4,110 字节、第 94 轮的 4,284 字节、
 * 第 95 轮的 21,347 字节等），只是**后续几轮只改了字节数、没改行数**，行数才与今天巧合相等。更关键的是，
 * 这 5 处里的 `docs/01-需求规格说明书.md` 那处在 `## 7. 需求到轮次的追溯矩阵` 一节下——**标题链里没有
 * 「轮」字**，那条历史词表根本认不出它是历史叙述。所以：**历史词表既会漏（R110 那处新写的假读数就写在
 * 「第 110 轮」小节里），又会错（追溯矩阵这种不写「轮」字的历史小节认不出来）**，不能拿它当判据的锚。
 *
 * 本条改用的办法：**锚只认「行数与磁盘相等」，少数巧合相等的历史快照显式登记在
 * [coincidentalSnapshot] 里，且那张表有条数上限 [exemptCap]**——不许拿它当消音器。
 *
 * ## 它到底能不能抓住第 110 轮那处假读数（照实交代）
 *
 * 第 110 轮那处 `23,945 B / 23,843 B` 在文档体系里共有**五处副本**，形态**不一样**：
 *
 * - **三处在括号里**（`records/01-迭代记录.md`、`records/04-质量与验证记录.md`、`records/CHANGELOG.md`
 *   各一处，形态 `` 新增守卫类 `DocCodeLineClaimGuardTest`（**422 行 / 23,843 B / md5 `27a813a3…`**） ``）：
 *   **本条判据覆盖得到**。它写的是 422 行、磁盘 422 行 ⇒ 进「被判的一群」⇒ 字节数一位不对就报红。
 *   换句话说：**这套判据若在第 110 轮之前就存在，那处假读数在写下的当轮就会红**，而不用等到下一轮普查。
 * - **两处不在括号里**（`docs/06-测试计划与用例.md`、`docs/07-风险管理计划.md`，形态 `` …（**422 行**、23,843 B、
 *   md5 `27a813a3…`） ``，字节数与 md5 是**顿号分隔**、落在括号之外）：**本条判据根本覆盖不到**，
 *   因为括号读数正则要求字节数/md5 出现在**同一个括号内**。这是本条判据的一处已知缺口，登记为旧账。
 *
 * 结论：**「括号三连」这一类覆盖到了，「顿号三连」那一类没有**——不许把本条说成「抓住过第 110 轮那处」。
 *
 * ## 五条用例
 *
 * 1. [everyManagedDocIsRead]：16 份受管文档一份不少地在（10 份 `docs/00`~`09` + `README.md` +
 *    `records/` 下 5 份）。少读一份 = 它的读数整片掉出管辖，是这条守卫最想防的假绿。
 * 2. [everyReconciledReadingMatchesDisk]：上面判据的正经面——「被判的一群」里每一处的字节数/md5
 *    都必须等于磁盘实测，只有 [coincidentalSnapshot] 登记在案的例外。
 * 3. [everyReadingIsCounted]：五道覆盖下限——括号读数总数、含字节处数、含 md5 处数、
 *    **被判的一群**处数、被判的一群涉及的不同文件数。正则写歪或受管清单被换走会退化成「零命中 ⇒ 一律通过」。
 * 4. [coincidentalSnapshotListStaysHonest]：豁免表**双向**判据 + 条数上限——表里每一条都必须**真的**
 *    对不上磁盘（否则这条豁免过期了），且条数不得超过 [exemptCap]。
 * 5. [snapshotReadingsStaySnapshots]：反向面下限——行数**对不上**磁盘（= 当轮快照）的处数与其带字节/md5
 *    的处数不许塌。防的是「有人为了让第 2 条变绿，把两百多处历史快照整片回改成今天的数字」——
 *    那与 `records/03` ADR-070 正相反，记录会毁掉。第 3 条钉「被判的一群没缩水」，这条钉它的对偶。
 *
 * ## 它不保证什么
 *
 * - **不判行数**。N 对不上的（252 处）、括号里没行数的（477 处）一律不看行数对不对——那是
 *   [DocCodeLineClaimGuardTest] 的地盘。本条只在「行数已经对了」的前提下判字节/md5。
 * - **不判文件是否存在**。文件名找不到的读数（第 111 轮实测 48 处）整片跳过：字节/md5 无从比。文件名
 *   存在性是 [DocCodeLineClaimGuardTest] 第 2 条的判据（它维护 `deletedFileExempt`）。
 * - **括号外的数字一个不判**。`…（**422 行**、23,843 B、md5 `…`）` 这种顿号写法（见上）不覆盖。
 * - **只取括号里第一处字节数/第一处 md5**。`` `X.kt`（447 行 19,138 字节 → 467 行 20,543 字节） `` 这种
 *   「改前 → 改后」对写、且**改后在前**的写法会被误判成现读（因为取到的是改后的行数与别处的字节数）。
 *   第 111 轮普查里没有这种形态；将来若出现，要么调整写法，要么登记进 [coincidentalSnapshot]。
 * - **不改文档**。它只报红；哪一处该回改成现值、哪一处该按 ADR-070 留着，没有机器判据。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。第 111 轮补的是「字节数/md5」这一类里**括号三连**这一面的兜底。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。行数口径与 [DocCodeLineClaimGuardTest.lineCount] 逐字相同（数 `\n`，不以
 * `\n` 结尾再补一个），好让本条的读数与普查脚本 `build/r111/design111.py` 直接对账。
 */
class DocByteMd5ClaimGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 受管文档清单（与 [DocCodeLineClaimGuardTest] 同一张表）。 */
    private val managedPaths = listOf(
        "docs/00-项目章程与需求计划书.md",
        "docs/01-需求规格说明书.md",
        "docs/02-概要设计说明书.md",
        "docs/03-详细设计说明书.md",
        "docs/04-数据库设计说明书.md",
        "docs/05-接口设计说明书.md",
        "docs/06-测试计划与用例.md",
        "docs/07-风险管理计划.md",
        "docs/08-部署与构建说明.md",
        "docs/09-用户手册.md",
        "README.md",
        "records/01-迭代记录.md",
        "records/02-需求变更记录.md",
        "records/03-架构决策记录.md",
        "records/04-质量与验证记录.md",
        "records/CHANGELOG.md",
    )

    /** 收 `.kt` 的三个根。 */
    private val srcRoots = listOf(
        "app/src/main/java",
        "app/src/test/java",
        "app/src/androidTest/java",
    )

    /**
     * 括号读数：文件名 + （至多四个间隔字符）+ 左括号 + 括号内至多 220 字符。
     * 与普查脚本 `build/r111/design111.py` 的 `BRACKET` 逐字同口径。
     */
    private val bracketPattern =
        Regex("""([A-Za-z0-9_]+\.kt)[^（(\n]{0,4}[（(]([^）)\n]{0,220})""")

    /** 括号里的行数（允许千分位逗号）。 */
    private val linesPattern = Regex("""([0-9][0-9,]*)\s*行""")

    /** 括号里的字节数：`4,110 B` 或 `4,110 字节`。`\b` 挡住把 `16,384 的整数倍` 这类当读数。 */
    private val bytesPattern = Regex("""([0-9][0-9,]*)\s*(?:B\b|字节)""")

    /** 括号里的 md5（小写 32 位十六进制）。 */
    private val md5Pattern = Regex("""\b[0-9a-f]{32}\b""")

    /** 受管文档份数下限（现值 16）。 */
    private val managedFileFloor = 16

    /** 括号读数总处数下限（第 111 轮实测 992 处，取值 952 留余量）。 */
    private val totalBracketFloor = 952

    /** 含字节数的处数下限（第 111 轮实测 173 处，取值 165）。 */
    private val withBytesFloor = 165

    /** 含 md5 的处数下限（第 111 轮实测 129 处，取值 123）。 */
    private val withMd5Floor = 123

    /**
     * **被判的一群**处数下限（第 111 轮实测 74 处，取值 59 留余量）。这一群是**唯一会被判对错**的读数，
     * 少一批就该有人来看一眼，而不是被「还有九百多处读数」糊过去。
     */
    private val reconciledFloor = 59

    /** 被判的一群涉及的**不同 `.kt` 文件数**下限（第 111 轮实测 33 个，取值 31）。 */
    private val distinctReconciledFileFloor = 31

    /**
     * **行数对不上磁盘**（= 当轮快照）的处数下限（第 111 轮实测 252 处，取值 240）。这是第 3 条的**对偶**：
     * 防「为了让第 2 条变绿而把历史快照整片回改成今天的数字」。
     */
    private val snapshotFloor = 240

    /** 当轮快照里**带字节/md5** 的处数下限（第 111 轮实测 97 处，取值 90）。同上，是同一层假绿的另一个面。 */
    private val snapshotWithBytesFloor = 90

    /**
     * [coincidentalSnapshot] 的**条数上限**（第 111 轮实测需 5 条，取值 11）。
     *
     * 这条上限是这张表的良心：豁免是给「行数与今天巧合相等的**历史**快照」开的，正常数量级是个位数。
     * 一旦有人往里塞几十条来把第 2 条刷绿，红点会先落在这条上限上。
     */
    private val exemptCap = 11

    /**
     * 「行数与磁盘今天巧合相等、但这一处在讲**当轮当时**的尺寸」的显式登记表。
     *
     * 第 111 轮普查（`build/r111/design111.py`）把被判的一群里 74 处逐条比过，只有 **5** 处对不上，
     * 5 处全是历史快照，按 `records/03` ADR-070 **不回改**；第 144 轮添第六个游戏时再核，又发现一处同类
     * （`records/04` 第 48 轮那处 `PlayCatalogTest.kt` 读数），总数成 **6**；第 144 轮收尾复跑定向 13 类时再核，
     * 又添一处同类（`records/CHANGELOG.md` 里 `DocUserManualGuardTest.kt` 那处，只有 md5 漂了），总数成 **7**，理由同上：
     *
     * - `CopyRules.kt` 的 66 行 / `CopyRulesPlacementTest.kt` 的 404 行，在第 37 / 94 / 95 轮写下之后
     *   **行数一直没再变过**（后续几轮只动了字节数与 KDoc），所以行数锚在这几处失效；
     * - 其中 `docs/01-需求规格说明书.md` 那处在 `## 7. 需求到轮次的追溯矩阵` 下——那是历史小节，
     *   但**标题里没有「轮」字**（正是本条判据不拿历史词表当锚的原因，见类注释）。
     *
     * 键的格式：`受管文档相对路径 | .kt 文件名 | 行数 | 字节数原文 | md5 或 -`（字节数保留千分位原文）。
     * 这张表是**双向**判据（见 [coincidentalSnapshotListStaysHonest]）：表外的对不上 ⇒ 红；
     * 表里这条**现在对得上磁盘了**（或那处读数已被改写/删掉）⇒ 也红，逼着人来重新判定一次。
     */
    private val coincidentalSnapshot = mapOf(
        "docs/01-需求规格说明书.md|CopyRules.kt|66|4,110|-" to
            "`## 7. 需求到轮次的追溯矩阵` 第 37 轮那一行：当时 CopyRules.kt 是 4,110 字节 / 66 行；" +
            "后来字节长到 4,325 而行数一直是 66，行数锚巧合相等。历史小节按 ADR-070 不回改",
        "docs/06-测试计划与用例.md|CopyRules.kt|66|4,284|616477b5527b520174964dbb65eb7f6e" to
            "`### 3.70 第 94 轮新增读数` 一节：那时是 4,284 B / 66 行，第 94 轮的 KDoc 改动之后" +
            "行数没变（66）而字节变成 4,325；当轮快照不回改",
        "docs/06-测试计划与用例.md|CopyRulesPlacementTest.kt|404|21,347|-" to
            "`### 3.71 第 95 轮新增读数` 一节：那时是 21,347 B / 404 行，第 95 轮之后行数没变而" +
            "字节变成 21,374；当轮快照不回改",
        "records/04-质量与验证记录.md|CopyRules.kt|66|4,110|-" to
            "`## 三十六、第 37 轮` 之下的「本轮新建」那一处：当轮 4,110 字节 / 66 行，同上，行数锚巧合相等",
        "records/CHANGELOG.md|CopyRulesPlacementTest.kt|404|21,347|f5fe9d7f4c36dfe03ae1d9f4ff90235a" to
            "`## 第 95 轮` 条目：「测试码（唯一改动文件）」那一处，当轮 21,347 B / 404 行，同上",
        "records/04-质量与验证记录.md|PlayCatalogTest.kt|97|4,348|-" to
            "`## 第四十七、第 48 轮` 一节「改动的既有单测」表：当轮 4,348 字节 / 97 行；第 144 轮为" +
            "第六个游戏改它两处 KDoc，字节长到 4,434 而行数仍是 97，行数锚巧合相等，按 ADR-070 不回改",
        "records/CHANGELOG.md|DocUserManualGuardTest.kt|410|23,374|fec1c30c2382966fe0a18ed8e4ef8d9d" to
            "`## 第 139 轮` 条目里那一处：当轮 23,374 B / 410 行 / md5 fec1c30c…；此后各轮只做**等长**滚动" +
            "（第 144 轮把 `docs/07` 的尾部钉行回改就是一处），字节数与行数至今巧合相同，md5 已变成" +
            "303053cef51b1f75f51b73e3f45ebbf0（第 144 轮复核时实测）。当轮快照按 ADR-070 不回改",
    )

    /** 一份 `.kt` 在磁盘上的三个读数。 */
    private data class Kt(val bytes: Int, val md5: String, val lines: Int)

    /** 一处括号读数。 */
    private data class Reading(
        /** 受管文档相对路径（如 `docs/01-需求规格说明书.md`），登记键的第一段。 */
        val rel: String,
        /** 该读数在源文件里的行号（1 起）。 */
        val sourceLine: Int,
        /** 读到的 `.kt` 文件名。 */
        val ktName: String,
        /** 括号里写下的行数；括号里没有行数时为 `null`。 */
        val claimedLines: Int?,
        /** 括号里写下的字节数**原文**（含千分位，如 `4,110`）；没有时为 `null`。 */
        val bytesLabel: String?,
        /** 括号里写下的 md5（小写 32 位）；没有时为 `null`。 */
        val md5: String?,
        /** 括号内原文，报错时给人看。 */
        val inner: String,
    ) {
        /** 登记 / 报错用的键，与 [coincidentalSnapshot] 的键同格式。 */
        val key: String
            get() = "$rel|$ktName|${claimedLines ?: "-"}|${bytesLabel ?: "-"}|${md5 ?: "-"}"

        /** 报错时点位置用。 */
        val where: String get() = "$rel:$sourceLine -> $ktName"
    }

    /** 一处的判定结果。 */
    private data class Judged(
        val reading: Reading,
        /** `missing` / `noAnchor` / `lineOnly` / `snapshot` / `ok` / `bad`。 */
        val kind: String,
        /** `bad` 时的差异说明。 */
        val why: String,
    )

    /**
     * 行数口径：数 `\n` 的个数，文件不以 `\n` 结尾再补一个。与 [DocCodeLineClaimGuardTest.lineCount]
     * 及普查脚本 `build/r111/design111.py` 的 `klines()` 逐字同口径。
     */
    private fun lineCount(file: File): Int {
        val text = file.readText()
        var n = 0
        for (c in text) if (c == '\n') n++
        if (text.isNotEmpty() && !text.endsWith("\n")) n++
        return n
    }

    /** 文件 md5（小写十六进制）。 */
    private fun md5Of(file: File): String =
        MessageDigest.getInstance("MD5").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    /** 受管文档清单（按 [managedPaths] 顺序）。 */
    private fun managedFiles(): List<Pair<String, File>> =
        managedPaths.map { it to File(repoRoot, it.replace('/', File.separatorChar)) }

    /** `app/src` 三个根下全部 `.kt` 的索引：文件名 → 各份的（字节数、md5、行数）。 */
    private fun ktIndex(): Map<String, List<Kt>> {
        val index = LinkedHashMap<String, MutableList<Kt>>()
        for (root in srcRoots) {
            val dir = File(repoRoot, root.replace('/', File.separatorChar))
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .forEach {
                    index.getOrPut(it.name) { mutableListOf() }
                        .add(Kt(it.length().toInt(), md5Of(it), lineCount(it)))
                }
        }
        return index
    }

    /** 逐份受管文档抽括号读数（同一行可以有多处）。 */
    private fun readingsIn(rel: String, file: File): List<Reading> {
        val out = mutableListOf<Reading>()
        for ((offset, text) in file.readText().split("\n").withIndex()) {
            for (match in bracketPattern.findAll(text)) {
                val inner = match.groupValues[2]
                out += Reading(
                    rel = rel,
                    sourceLine = offset + 1,
                    ktName = match.groupValues[1],
                    claimedLines = linesPattern.find(inner)?.groupValues?.get(1)
                        ?.replace(",", "")?.toInt(),
                    bytesLabel = bytesPattern.find(inner)?.groupValues?.get(1),
                    md5 = md5Pattern.find(inner)?.value,
                    inner = inner,
                )
            }
        }
        return out
    }

    /**
     * 判一处的归类与对错。归类口径（见类注释）：
     * `missing`（磁盘上没这个文件名）→ `noAnchor`（括号里没行数）→ `snapshot`（行数对不上磁盘）
     * → `lineOnly`（行数对了但括号里没字节/md5）→ `ok` / `bad`（**被判的一群**）。
     */
    private fun judge(reading: Reading, index: Map<String, List<Kt>>): Judged {
        val copies = index[reading.ktName] ?: return Judged(reading, "missing", "")
        val lines = reading.claimedLines ?: return Judged(reading, "noAnchor", "")
        if (copies.none { it.lines == lines }) return Judged(reading, "snapshot", "")
        if (reading.bytesLabel == null && reading.md5 == null) return Judged(reading, "lineOnly", "")
        val why = mutableListOf<String>()
        reading.bytesLabel?.let { label ->
            val claimed = label.replace(",", "").toInt()
            if (copies.none { it.bytes == claimed }) {
                why += "文档写 $label 字节，磁盘实测 " + copies.joinToString("/") { "${it.bytes} 字节" }
            }
        }
        reading.md5?.let { claimed ->
            if (copies.none { it.md5 == claimed }) {
                why += "文档写 md5 $claimed，磁盘实测 " + copies.joinToString("/") { it.md5 }
            }
        }
        return if (why.isEmpty()) Judged(reading, "ok", "")
        else Judged(reading, "bad", why.joinToString("；"))
    }

    /** 全量扫描：每处读数的判定 + `.kt` 索引。 */
    private fun scan(): Pair<List<Judged>, Map<String, List<Kt>>> {
        val index = ktIndex()
        val judged = mutableListOf<Judged>()
        for ((rel, file) in managedFiles()) {
            if (!file.isFile) continue
            for (reading in readingsIn(rel, file)) judged += judge(reading, index)
        }
        return judged to index
    }

    /**
     * 第 1 条：16 份受管文档一份不少地在。所有别的判据的地基（少读一份 = 整片静默不扫）。
     */
    @Test
    fun everyManagedDocIsRead() {
        val present = managedFiles().filter { it.second.isFile }
        assertTrue(
            "只数到 ${present.size} 份受管文档，低于下限 $managedFileFloor：受管文档被删、被改名、" +
                "或被挪出仓库，它的读数就整片失去判据（不是报红，而是静默不扫）",
            present.size >= managedFileFloor,
        )
        val missing = managedPaths.filterNot { File(repoRoot, it.replace('/', File.separatorChar)).isFile }
        assertTrue(
            "受管文档里找不到这几份：$missing：16 份是这套文档体系的全集（10 份 docs + README + " +
                "records 下 5 份），少一份就等于给它整个豁免",
            missing.isEmpty(),
        )
    }

    /**
     * 第 2 条：**被判的一群**（行数等于磁盘 ⇒ 在讲今天）里，写下的字节数与 md5 必须对得上磁盘。
     * 唯一例外是 [coincidentalSnapshot] 登记在案的那几处历史快照。
     */
    @Test
    fun everyReconciledReadingMatchesDisk() {
        val (judged, _) = scan()
        val unreconciled = judged
            .filter { it.kind == "bad" && it.reading.key !in coincidentalSnapshot }
            .map { "${it.reading.where}：${it.why}\n        括号内原文：${it.reading.inner}" }
        assertTrue(
            "有 ${unreconciled.size} 处读数在讲「今天这份文件」，但字节数/md5 与磁盘对不上：\n" +
                unreconciled.sorted().joinToString("\n") { "  $it" } +
                "\n现读读数的字节数/md5 一漂就是文档假话（第 110 轮抓到过一处：`DocCodeLineClaimGuardTest` " +
                "的 23,945 B 其实磁盘是 23,843 B）。请回改到上面的实测值；若那一处确实在记录" +
                "「当轮当时多少字节」而只是行数与今天巧合相等，请把它**连同理由**补进 [coincidentalSnapshot]" +
                "（那张表有上限 $exemptCap，不是消音器）",
            unreconciled.isEmpty(),
        )
    }

    /**
     * 第 3 条：五道覆盖下限。不判任何一处的对错，只问「同一条尺子还是不是一个量级」。
     */
    @Test
    fun everyReadingIsCounted() {
        val (judged, _) = scan()
        val total = judged.size
        assertTrue(
            "16 份受管文档里只抓到 $total 处括号读数，低于下限 $totalBracketFloor（第 111 轮实测 992）：" +
                "括号读数正则失效、受管清单被换走、或文档被删空，这条守卫都会退化成「零命中 ⇒ 一律通过」",
            total >= totalBracketFloor,
        )
        val withBytes = judged.count { it.reading.bytesLabel != null }
        assertTrue(
            "只认出 $withBytes 处带**字节数**的括号读数，低于下限 $withBytesFloor（第 111 轮实测 173）：" +
                "字节数正则（`4,110 B` / `4,110 字节`）写歪会让整条判据失去被判对象",
            withBytes >= withBytesFloor,
        )
        val withMd5 = judged.count { it.reading.md5 != null }
        assertTrue(
            "只认出 $withMd5 处带 **md5** 的括号读数，低于下限 $withMd5Floor（第 111 轮实测 129）：" +
                "md5 正则写歪同上",
            withMd5 >= withMd5Floor,
        )
        val reconciled = judged.count { it.kind == "ok" || it.kind == "bad" }
        assertTrue(
            "**被判的一群**只认出 $reconciled 处，低于下限 $reconciledFloor（第 111 轮实测 74）：" +
                "这一群是唯一会被判对错的读数；「行数锚」整片失效（比如 `.kt` 索引扫不到文件、行数口径" +
                "改成 wc -l 那种）时，第 2 条会静默变成零命中 ⇒ 假绿",
            reconciled >= reconciledFloor,
        )
        val files = judged.filter { it.kind == "ok" || it.kind == "bad" }.map { it.reading.ktName }.toSet()
        assertTrue(
            "**被判的一群**只涉及 ${files.size} 个不同的 `.kt` 文件，低于下限 $distinctReconciledFileFloor" +
                "（第 111 轮实测 33）：同一层假绿的另一个面",
            files.size >= distinctReconciledFileFloor,
        )
    }

    /**
     * 第 4 条：[coincidentalSnapshot] 的**双向**判据 + 条数上限。
     *
     * 表里每一条都必须在**今天**仍然是一处「对不上磁盘」的被判读数：若某一条现在对得上磁盘了，或那处
     * 读数已被改写/删掉，说明这张豁免过期了——它会在下一次出现同名同数的假读数时把红点吃掉。
     */
    @Test
    fun coincidentalSnapshotListStaysHonest() {
        assertTrue(
            "[coincidentalSnapshot] 有 ${coincidentalSnapshot.size} 条，超过上限 $exemptCap：" +
                "这张表是给「行数与今天巧合相等的历史快照」开的（第 111 轮全量 74 处里只有 5 处），" +
                "正常数量级是个位数；一次多出几十条，通常是为了把第 2 条刷绿而大范围豁免",
            coincidentalSnapshot.size <= exemptCap,
        )
        val stillBad = scan().first.filter { it.kind == "bad" }.map { it.reading.key }.toSet()
        val stale = coincidentalSnapshot.filterKeys { it !in stillBad }.keys
        assertTrue(
            "[coincidentalSnapshot] 里有 ${stale.size} 条**今天已经用不上**（那处读数现在对得上磁盘了，" +
                "或已被改写/删掉）：\n" + stale.sorted().joinToString("\n") { "  $it" } +
                "\n过期豁免会吃掉下一次的真报红：要么把它删掉，要么重新判定它现在是不是还该留着",
            stale.isEmpty(),
        )
        assertTrue(
            "[coincidentalSnapshot] 里有理由空白的条目：" +
                coincidentalSnapshot.filterValues { it.isBlank() }.keys.sorted().joinToString("、") +
                "：每一条豁免都要写清「它在讲哪一轮、当时是多少」，否则没人能复查",
            coincidentalSnapshot.none { it.value.isBlank() },
        )
    }

    /**
     * 第 5 条：反向面下限——行数**对不上**磁盘（= 当轮快照）的读数不许塌。
     *
     * 与第 3 条是一对：第 3 条钉「被判的一群没缩水」，这一条钉它的对偶。真正的危险是有人为了让第 2 条
     * 变绿而把两百多处历史快照整片回改成今天的数字——按 `records/03` ADR-070，那些**一律不回改**。
     */
    @Test
    fun snapshotReadingsStaySnapshots() {
        val (judged, _) = scan()
        val snapshots = judged.filter { it.kind == "snapshot" }
        assertTrue(
            "行数对不上磁盘（= 当轮快照）的读数只剩 ${snapshots.size} 处，低于下限 $snapshotFloor" +
                "（第 111 轮实测 252）：这一群是历史记录该有的样子，不该被整片回改成今天的数字" +
                "（那与 records/03 ADR-070 相反）",
            snapshots.size >= snapshotFloor,
        )
        val withBytes = snapshots.count { it.reading.bytesLabel != null || it.reading.md5 != null }
        assertTrue(
            "当轮快照里只剩 $withBytes 处带字节/md5，低于下限 $snapshotWithBytesFloor（第 111 轮实测 97）：" +
                "同上，这是同一层假绿的另一个面",
            withBytes >= snapshotWithBytesFloor,
        )
    }
}
