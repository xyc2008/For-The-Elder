package com.fortheelder.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「[`records/run-evidence/`](../../../../../../records/run-evidence/) 里的运行证据日志，跟受管文档里写的读数
 * 对不对得上」的守卫。
 *
 * 第 112 轮把三条运行入口（`run.sh`、`run.sh --help`、`run.bat`）真跑了一遍，各留一份日志入库，并把
 * 每个日志的**字节数 / 行数 / md5** 写进六份受管文档（`docs/03`、`docs/06`、`records/01`、`records/04`、
 * `records/CHANGELOG.md`、`README.md` 一带有）。第 112 轮收尾时自挂了三笔账：**运行证据守卫没立**、
 * `run.sh` 自身分支没有自动化判据、日志与记录里的 md5「没有机器对账」。本条是前两笔里可机器判的部分
 * 的第一次收口，第 113 轮落成。
 *
 * 与 [DocByteMd5ClaimGuardTest] 的关系：那条管**产品码 `.kt`** 的字节/md5，且它的括号读数正则写死了
 * `\.kt` 后缀——若把 `records/run-evidence/` 下的 `.log` 也塞进去，会搅动它那 992 处读数与五道下限。所以
 * 本轮**另立一个类**，判据自己一套，口径与原普查脚本 `build/r113/probe113.py` 逐字相同。
 *
 * ## 一、判据（第 113 轮定案四道，第 117 轮补到五道）
 *
 * 1. **目录双向 + 三份日志对账**（[everyRegisteredLogMatchesDisk]）：`records/run-evidence/` 里多一个文件、
 *    少一个文件都报；[registered] 里每份日志的磁盘字节数 / 行数 / md5 必须等于登记值。
 * 2. **锚点窗口判据**（[readingsNearAnAnchorMatchRegistry]）：**锚点** = 某份受管文档里出现某个已登记
 *    日志**文件名**（basename）的位置。取锚点右侧**截到行尾、至多 [window] 个字符**为窗口，窗口里每一处
 *    读数都必须等于**该锚点对应的那份日志**的登记值——md5 对 md5、字节数对字节数（按去千分位后的数字
 *    比）、行数对行数。唯一例外是 [cotenantBytes] / [cotenantMd5] 这张「同句常客」白名单（见下）。
 * 3. **行级闭合**（[linesCarryingALogMd5AreClosed]）：受管文档里任一行出现某份日志的 md5 ⇒ **同一行**必须
 *    同时出现该日志的登记**字节数**与**行数**（按去千分位后的数字词元比）。防的是「只抄 md5、不抄读数」
 *    这种半截记录，也防「把 md5 抄到别处」。下限在**第 114 轮**由「全部受管文档加起来 ≥ 10 行」改成
 *    **逐份** `≥ [perLogClosedLineFloor]`（贴实测值、不留余量），理由见第三节的反向变异实测。
 * 4. **日志内容自证**（[logContentsShowRealRuns]）：三份日志的正文里必须有只有**真跑过**才会出现的东西
 *    （见该用例），外加各道覆盖下限。
 * 5. **「同句常客」的上下界**（[cotenantWhitelistIsBoundedPerAnchor]）：第 3 条的例外（[cotenantBytes] /
 *    [cotenantMd5]）不再是一张没有额度的通行证 —— 白名单按**（受管文档 × 日志）**逐对给额度
 *    （[cotenantCeilings]，表里没有的对按 0 算）。收第 113 轮那笔「把某处日志的 md5 误写成 APK 的 md5，
 *    窗口判据仍绿」的账，实测见第三节 M2。
 *
 * ## 二、为什么窗口定 W = 250（不是拍脑袋，是量出来的）
 *
 * 第 113 轮普查（`build/r113/probe113.py`，输出 `build/r113/probe113.log`）把 18 个锚点逐个量了「右侧第一处
 * 读数的偏移」与「各候选 W 下的违规数」：
 *
 * | W | 窗口内读数 | 同句常客命中 | 违规 |
 * | --- | --- | --- | --- |
 * | 80 | 14 | 0 | 0 |
 * | 100 | 19 | 0 | 0 |
 * | 120 / 150 | 20 | 0 | 0 |
 * | 180 | 21 | 1 | 0 |
 * | 200 | 23 | 3 | 0 |
 * | **250** | **26** | **5** | **0** |
 * | 300 | 27 | 6 | 0 |
 * | 400 | 30 | 6 | **3** |
 * | 600 | — | — | 12 |
 *
 * 最远的一处「本日志自己的读数」在 `docs/06-测试计划与用例.md:4007#1` 的 **+193**；第一次报红发生在
 * **W = 400**（`records/04` 与 `records/CHANGELOG.md` 里 run.log 的锚点，窗口一路够到了 help.log 的
 * `1,478 B / 20 行`，那是**另一份**日志的读数）。取 **250**：比 193 留 57 字符余量，离 400 留 150 字符
 * 缓冲；250 与 300 都是零违规，取小的。
 *
 * **「同句常客」为什么必须登记**：那一跑的 APK 读数是 `11,020,680` 字节 / md5 `8adbba…`，它**就在日志正文里**
 * （`[run] APK …（11020680 字节，md5 8adbba…）`），而记录里「日志 md5 与 APK md5」常写在同一句/同一张表里。
 * W = 250 下这个白名单被命中 **5** 次——它是在**真被用到**，不是摆设；若哪天命中数塌到 0，说明锚点或窗口
 * 出了变化，有人该来看一眼（[readingsNearAnAnchorMatchRegistry] 里有这道下限）。
 *
 * ## 三、它能不能抓住「有人改坏一处」（反向变异实测：第 113 / 114 / 117 轮，共八组）
 *
 * 前两例都在**先整体备份、事后逐字节还原**的前提下真跑过（脚本 `build/r113/mut113.py`，输出
 * `build/r113/mut-1.log` / `mut-2.log`；还原后 `docs/03` 与三份日志的 md5 全部等于冻结值）：
 *
 * - **把 `docs/03-详细设计说明书.md:5657` 那行表格里 `c4fe86…` 的末字符改掉**：窗口判据违规数
 *   **0 → 2**（那一行有两个锚点，各自窗口都照到了这个改坏的 md5，偏移 +50 / +18）。**这条能抓住。**
 *   第 113 轮当时以为第 3 道判据也会一起红（「含日志 md5 的行」11 → 10，撞在下限 10 上），**实测只有窗口
 *   判据红**：11 与 10 都是**落盘前**量的数，落盘本身又往六份文档里写进若干含日志 md5 的行，真实总数是
 *   **18**，改坏一行后是 **17**——离下限 10 还远；何况「总数」这个口径天生抓不住单点改坏。第 114 轮因此
 *   把下限换成**逐份** `≥ [perLogClosedLineFloor]`：同一次变异下被改坏那份的闭合行数 **8 → 7** ⇒ 当场报红。
 * - **把 `run.log` 中间一个字节（第 1570 字节的空格）改成 `Z`**：字节数不变（3,141）而 md5 变为
 *   `e86be8bb…`，第 1 道判据当场 MISMATCH。**这条能抓住。**
 *
 * 第 117 轮又量了六组（脚本 `build/r117/mutate.py` + `build/r117/runmut.sh`，输出 `build/r117/runmut.log`；
 * 每组的五份受管文档都从**字节快照** `build/r117/snap/` 还原并逐一核过 md5，因为 `core.autocrlf=true` 下
 * `git checkout` 会把 `.kt` 写成 CRLF、md5 会变）：
 *
 * | 组 | 这一刀砍在哪 | 报红处（实测） |
 * | --- | --- | --- |
 * | M1 | 把 `records/01` 那处 APK 读数用空格推出 250 窗口 | 判据 5：同句常客只落在 2 对，低于下限 3 |
 * | M2 | 把 `docs/03` 里 run 日志的 md5 写成 APK 的 md5 | 判据 5：`docs/03` × run 命中 2 次，上限 0；判据 3：run 闭合行 7 < 8；**判据 2 绿** |
 * | M3 | 在 `records/04` 那处同句读数后又补一处 APK 读数 | 判据 5：`records/04` × run 命中 3 次，上限 2 |
 * | M4 | 在 `docs/06` 一份 bat 日志锚点后又补一处 APK md5 | 判据 5：`docs/06` × bat / help / run 各命中 1 次，上限 0 |
 * | M5 | 往 `records/01` 顶部插 3 行 HTML 注释 | 七条全绿——判据吃的是**内容**，不吃行号 |
 * | M6 | 把 `docs/03` 里 run 日志 md5 的末字符改坏 | 判据 2：该行两个锚点各自窗口照到，偏移 +50 / +18；判据 3：run 闭合行 7 < 8；**判据 5 绿** |
 *
 * **M2 与 M6 是一对镜子**：M2 把值**换成另一份东西**，判据 2 看不见（值本身是合法的登记值，白名单吃掉了）、
 * 判据 5 接住；M6 把值**改坏**，判据 2 照红、判据 5 看不见（既不增也不减命中）。两条判据各管一面，这就是
 * 「同句常客的上下界」另立一道判据、而不是并进判据 2 的理由。M5 说明六组里插行不吃判据，只量内容。
 *
 * ## 四、它不保证什么（照实交代）
 *
 * - **这三份日志不可重跑重现**：时间戳、主机名、pid、`TotalTime`、Gradle 的任务计数每次都会变，md5 自然
 *   也变。它们钉住的是「**那一次**真跑的真实输出」，**不是**「任何人在任何机器上重跑都会得到这份字节」的
 *   断言。也正因如此，本守卫**只判已经入库的日志**，不重跑任何脚本。
 * - **`run.sh` / `run.bat` 每条分支仍然没有自动化判据**。第 103 轮交付的 `--emulator` / `--reinstall` /
 *   `--shot` / `--logcat` / `--avd` 这些分支，本守卫一个也不判（它读的是日志文本，不是脚本行为）。第 112 轮
 *   挂的那笔「`run.sh` 自身分支无判据」的账，本轮**只收口了其中「日志与记录能对账」这一面**，脚本行为面
 *   仍然挂着。第 6 道用例里 `bat.log` 的 `构建 :app:assembleDebug` 命中数必须为 **0**（`--no-build` 真透传的
 *   证据）、`--no-build` 命中 1，是「按文本间接判脚本行为」的两处——**间接，不是行为判据**。
 * - **同句常客这张白名单仍然照吃值**：若有人把某处日志的 md5 误写成 APK 的 md5，第 2 道窗口判据**还是**
 *   会绿（白名单吃掉了）——第 113 轮那笔账收在**第 5 道判据**里：额度按（受管文档 × 日志）逐对卡死，
 *   把日志 md5 写成 APK md5 的那一处会顶破额度。**第 2 道仍然看不见这种错，是第 5 道看见的**（见第三节 M2）。
 *   另注：额度表只卡「多抄」，不查「额度是否用得上」——某份文档合法删掉那句既讲日志又讲 APK 的话之后，
 *   命中掉到 0 也不报红，额度仍占着；文档改名会留成死额度。收紧要靠人重测，登记为新账。
 * - **它不改任何文档**。报红之后「该回改成现值」还是「按 `records/03` ADR-070 留作当轮快照」，本守卫不判。
 * - **逐份下限贴的是实测值（每份 8 行），刻意不留余量**：若哪一轮**合法地**把某份日志在文档里的冗余重述
 *   删掉（例如同一份日志原本在两处各写一遍，合并成一处），该份闭合行数会掉到 7 而报红。这是**预期**行为：
 *   删重述必须同时改 [perLogClosedLineFloor]，并把理由写进当轮记录，不能悄悄改文档。反过来说，若某份日志
 *   被整份从记录里删净，它的闭合行数会掉到 0——这正是第 114 轮换口径要买的性质。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。本轮补的是「运行证据日志 ↔ 记录里的读数」这一面的兜底。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这些用例**直接失败**
 * 而不是跳过。行数口径与 [DocCodeLineClaimGuardTest.lineCount] 逐字相同（数 `\n`，不以 `\n` 结尾再补一个），
 * 好让本条的读数与普查脚本 `build/r113/probe113.py` 的 `line_count()` 直接对账。
 */
class RunEvidenceGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 受管文档清单（与 [DocCodeLineClaimGuardTest] / [DocByteMd5ClaimGuardTest] 同一张表）。 */
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

    /** 运行证据日志目录（刻意**不用** `.md` 后缀：六把文档普查器只按 `.md` 枚举）。 */
    private val evidenceDir: File =
        File(repoRoot, "records" + File.separatorChar + "run-evidence")

    /** 三份日志的文件名（也是 [registered] 的键、文档里的**锚点**字面）。 */
    private val runLog = "2026-11-26-r112-run.log"
    private val helpLog = "2026-11-26-r112-help.log"
    private val batLog = "2026-11-26-r112-bat.log"

    /**
     * 登记的日志读数（冻结值，来自第 112 轮 commit `87f7122` 的入库内容）。
     *
     * 改动这张表之前先读类注释第四节：这些是**那一次**运行的真实输出，不是可重跑样本。
     */
    private val registered = mapOf(
        runLog to Evidence(3141, 69, "c4fe86c1768ec28ac13699570bf932ad"),
        helpLog to Evidence(1478, 20, "d94f50f8c235806d46a77277434abece"),
        batLog to Evidence(938, 20, "d230ff200d6dc5aba6f1b2b4d5cc01c8"),
    )

    /** 那一跑的 APK 读数，日志正文里就含它（见类注释第二节的「同句常客」）。 */
    private val apkBytes = 11020680
    private val apkMd5 = "8adbba63ca911252f98ed4f2b5f28bc6"

    /**
     * 「同句常客」白名单：[apkBytes] / [apkMd5] 出现在某份日志锚点的窗口里，不算这处读数的错。
     *
     * **但它是有限额的**（见 [cotenantCeilings] 与第 7 道判据 [cotenantWhitelistIsBoundedPerAnchor]）：
     * 第 113~116 轮那张「按值」的白名单没有任何额度，谁把某处日志的 md5 误写成 APK 的 md5 都照绿。
     */
    private val cotenantBytes = setOf(apkBytes)
    private val cotenantMd5 = setOf(apkMd5)

    /** 锚点窗口宽度（见类注释第二节：实测定案 250）。 */
    private val window = 250

    /** 让 `records/run-evidence/` 逐字节入库的那条 `.gitattributes` 规则。 */
    private val attributesRule = "records/run-evidence/** -text"

    /** 受管文档份数下限（现值 16）。 */
    private val managedFileFloor = 16

    /** 锚点总数下限（第 113 轮实测 18、第 117 轮实测 27，取值 16；下限不动，只把现值记在旁边）。 */
    private val anchorFloor = 16

    /** 单份日志的锚点数下限（第 113 轮 8 / 5 / 5、第 117 轮 11 / 8 / 8，取值 5）。 */
    private val perLogAnchorFloor = 5

    /** W = 250 下窗口内读数总数下限（第 113 轮实测 26、第 117 轮实测 35，取值 22）。 */
    private val windowReadingFloor = 22

    /** W = 250 下「同句常客」命中数下限（第 113 轮与第 117 轮实测同为 5，取值 3）。 */
    private val cotenantHitFloor = 3

    /** 用得上白名单的「受管文档 × 日志」对数下限（第 117 轮实测 3，取值 3）。 */
    private val cotenantPairFloor = 3

    /**
     * 「同句常客」白名单的**逐对上限**——取第 117 轮实测值，**不留余量**：`records/01` × run 1、
     * `records/04` × run 2、`CHANGELOG` × run 2（总命中 5、对数 3）。**表里没有的对，上限按 0 算**，
     * 这才让这张白名单从「一张纸糊的通行证」变成「登记在册、有额度的例外」。
     *
     * 键用「受管文档 × 日志」而**不是行号**：受管文档每轮都在追加，行号做键等于每落盘一次就要改一次表；
     * 追加不动既有行、既有行的窗口也不变，只有「文档 × 日志」这个键是稳的。
     *
     * 两点照实交代（写进类注释第四节）：① 某份文档若**合法地**把这处「既讲日志、又讲 APK」的句子删掉，
     * 该对的命中会掉到 0 或 1 —— 低于额度不报红，但**额度依然占着**（本判据不查「额度是不是用得上」，
     * 只查「有没有用超」），要重新收紧得靠人重测；② 若某份文档改名，它的键就成了永不命中的死额度，
     * 同样只报「对数下限」那一层。
     */
    private val cotenantCeilings = mapOf(
        ("records/01-迭代记录.md" to runLog) to 1,
        ("records/04-质量与验证记录.md" to runLog) to 2,
        ("records/CHANGELOG.md" to runLog) to 2,
    )

    /**
     * **逐份**日志的闭合行数下限（第 114 轮实测三份各 8 行，**取等实测值、不留余量**）。
     *
     * 第 113 轮用的是「全部受管文档加起来 ≥ 10 行」这个总数口径，真实总数是 18 行——单点改坏淹没在总数里
     * （见类注释第三节的反向变异实测），所以第 114 轮换成逐份下限。
     */
    private val perLogClosedLineFloor = 8

    /** 目录里已登记日志份数下限（现值 3）。 */
    private val evidenceFileFloor = 3

    /** 窗口里的三种读数共用一套正则，口径与 `build/r113/probe113.py` 逐字相同。 */
    private val md5Pattern = Regex("""(?<![0-9a-fA-F])[0-9a-f]{32}(?![0-9a-fA-F])""")
    private val bytesPattern = Regex("""([0-9][0-9,]*)\s*(?:B\b|字节)""")
    private val linesPattern = Regex("""([0-9][0-9,]*)\s*行""")

    /** 一份日志的三个登记读数。 */
    private data class Evidence(val bytes: Int, val lines: Int, val md5: String)

    /** 一处锚点：`rel:line#occ`，锚的是 [log]，窗口自 [end] 起，[text] 是该行原文（截到行尾用）。 */
    private data class Anchor(
        val rel: String,
        val line: Int,
        val occ: Int,
        val log: String,
        val end: Int,
        val text: String,
    )

    /** 窗口里的一处读数：[kind] 是 `md5` / `bytes` / `lines`，[value] 已归一（字节/行数去掉了千分位）。 */
    private data class Hit(val kind: String, val value: String)

    /**
     * 行数口径：数 `\n` 的个数，文件不以 `\n` 结尾再补一个。与 [DocCodeLineClaimGuardTest.lineCount]
     * 及普查脚本 `build/r113/probe113.py` 的 `line_count()` 逐字同口径。
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

    /** 一份日志在磁盘上的三个读数；文件不在时返回 `null`。 */
    private fun onDisk(name: String): Evidence? {
        val file = File(evidenceDir, name)
        if (!file.isFile) return null
        return Evidence(file.length().toInt(), lineCount(file), md5Of(file))
    }

    /** 全量锚点：逐份受管文档、逐行、逐份日志、逐个出现（同一行可以出现多次）。 */
    private fun anchors(): List<Anchor> {
        val out = mutableListOf<Anchor>()
        for ((rel, file) in managedFiles()) {
            if (!file.isFile) continue
            for ((offset, text) in file.readText().split("\n").withIndex()) {
                for (name in registered.keys) {
                    var start = 0
                    var occ = 0
                    while (true) {
                        val k = text.indexOf(name, start)
                        if (k < 0) break
                        occ++
                        out += Anchor(rel, offset + 1, occ, name, k + name.length, text)
                        start = k + 1
                    }
                }
            }
        }
        return out
    }

    /** 一段文字里的读数，按出现位置排好序。 */
    private fun readingsIn(segment: String): List<Pair<Int, Hit>> {
        val out = mutableListOf<Pair<Int, Hit>>()
        for (m in md5Pattern.findAll(segment)) out += m.range.first to Hit("md5", m.value)
        for (m in bytesPattern.findAll(segment)) {
            out += m.range.first to Hit("bytes", m.groupValues[1].replace(",", ""))
        }
        for (m in linesPattern.findAll(segment)) {
            out += m.range.first to Hit("lines", m.groupValues[1].replace(",", ""))
        }
        return out.sortedBy { it.first }
    }

    /** 去掉千分位逗号后的数字词元集合（避免 `1969` 里含 `69` 这类巧合）。 */
    private fun numTokens(text: String): Set<String> {
        val stripped = Regex("""(?<=\d),(?=\d)""").replace(text, "")
        return Regex("""\d+""").findAll(stripped).map { it.value }.toSet()
    }

    /**
     * 子串出现次数（**不重叠**）。`String.count` 在 stdlib 里只有「按字符谓词」那一个重载，没有「按子串」，
     * 这里补上，好让第 6 道判据写成 `run.count(marker)` 这种直白样子。
     */
    private fun String.count(needle: String): Int {
        if (needle.isEmpty()) return 0
        var from = 0
        var n = 0
        while (true) {
            val at = indexOf(needle, from)
            if (at < 0) break
            n += 1
            from = at + needle.length
        }
        return n
    }

    /**
     * 第 1 条：16 份受管文档一份不少地在。所有别的判据的地基（少读一份 = 它的锚点整片静默不扫）。
     */
    @Test
    fun everyManagedDocIsRead() {
        val present = managedFiles().filter { it.second.isFile }
        assertTrue(
            "只数到 ${present.size} 份受管文档，低于下限 $managedFileFloor：受管文档被删、被改名或被挪出仓库，" +
                "它的锚点就整片失去判据（不是报红，而是静默不扫）",
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
     * 第 2 条：`records/run-evidence/` **双向**对账——目录里多一个文件、少一个文件都报；[registered] 里每份
     * 日志的磁盘字节数 / 行数 / md5 必须等于登记值。
     *
     * 这条是第 1 道判据的根：日志被改一个字节（第 113 轮反向变异实测过：`run.log` 中间翻一个字节 ⇒
     * 字节数不变、md5 变 ⇒ 当场 MISMATCH），这里就会红。
     */
    @Test
    fun everyRegisteredLogMatchesDisk() {
        assertTrue("$evidenceDir 不是目录（运行证据日志整片没了？）", evidenceDir.isDirectory)
        val actual = evidenceDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
        assertTrue(
            "运行证据日志目录里只有 ${actual.size} 份文件，低于下限 $evidenceFileFloor：" +
                "目录被清空或挪走时，第 3 / 4 道判据会退化成「零锚点 ⇒ 一律通过」",
            actual.size >= evidenceFileFloor,
        )
        assertEquals(
            "运行证据日志目录与登记表**对不上**。目录里多出来的 = ${actual - registered.keys}；" +
                "登记了却在目录里找不到的 = ${registered.keys - actual.toSet()}。" +
                "新加一份真跑日志时，必须同步更新 [registered]，并想清楚它在受管文档里的锚点",
            registered.keys.sorted(),
            actual,
        )
        val bad = mutableListOf<String>()
        for ((name, reg) in registered) {
            val disk = onDisk(name)
            if (disk == null) {
                bad += "$name 在目录里找不到"
            } else if (disk != reg) {
                bad += "$name 磁盘 ${disk.bytes} 字节 / ${disk.lines} 行 / md5 ${disk.md5}" +
                    "，登记 ${reg.bytes} / ${reg.lines} / ${reg.md5}"
            }
        }
        assertTrue(
            "有 ${bad.size} 份运行证据日志与登记读数对不上：\n" + bad.sorted().joinToString("\n") { "  $it" } +
                "\n这些日志按 `.gitattributes` 里的 `$attributesRule` 逐字节入库，**不许**在事后编辑：" +
                "改一个字节，「证据」这两个字就不成立了。若确实要换一份新日志，走「新增 + 更新 [registered] + " +
                "更新受管文档里的锚点读数」三步，不要就地改旧日志",
            bad.isEmpty(),
        )
    }

    /**
     * 第 3 条：锚点窗口判据（见类注释第一、二节）。窗口 = 锚点右侧**截到行尾、至多 [window] 个字符**。
     */
    @Test
    fun readingsNearAnAnchorMatchRegistry() {
        val all = anchors()
        assertTrue(
            "只认到 ${all.size} 个锚点（受管文档里出现已登记日志文件名的位置），低于下限 $anchorFloor" +
                "（第 113 轮实测 18）：受管清单被换走、或文档里的日志名被改写，这道判据会静默消失",
            all.size >= anchorFloor,
        )
        val perLog = all.groupingBy { it.log }.eachCount()
        for ((name, count) in perLog) {
            assertTrue(
                "$name 只认到 $count 个锚点，低于下限 $perLogAnchorFloor（第 113 轮实测 8 / 5 / 5）：" +
                    "某份日志的读数整片掉出管辖，它就成了没人管的一行字",
                count >= perLogAnchorFloor,
            )
        }
        val violations = mutableListOf<String>()
        var readings = 0
        var cotenantHits = 0
        for (anchor in all) {
            val reg = registered.getValue(anchor.log)
            val segment = anchor.text.substring(anchor.end, minOf(anchor.text.length, anchor.end + window))
            for ((offset, hit) in readingsIn(segment)) {
                readings++
                var ok = false
                when (hit.kind) {
                    "md5" -> {
                        ok = hit.value == reg.md5
                        if (!ok && hit.value in cotenantMd5) {
                            ok = true
                            cotenantHits++
                        }
                    }
                    "bytes" -> {
                        ok = hit.value == reg.bytes.toString()
                        val n = hit.value.toIntOrNull()
                        if (!ok && n != null && n in cotenantBytes) {
                            ok = true
                            cotenantHits++
                        }
                    }
                    else -> ok = hit.value == reg.lines.toString()
                }
                if (!ok) {
                    violations += "${anchor.rel}:${anchor.line}#${anchor.occ} ${anchor.log} " +
                        "+$offset ${hit.kind} ${hit.value}（登记 ${reg.bytes} 字节 / ${reg.lines} 行 / ${reg.md5}）"
                }
            }
        }
        assertTrue(
            "W = $window 的窗口里只读到 $readings 处读数，低于下限 $windowReadingFloor（第 113 轮实测 26）：" +
                "读数正则（md5 / `4,110 B` / `4,110 行`）写歪或锚点整片消失时，这道判据会变成「零命中 ⇒ 一律通过」",
            readings >= windowReadingFloor,
        )
        assertTrue(
            "W = $window 下「同句常客」（APK 的 $apkBytes 字节 / $apkMd5）只命中 $cotenantHits 次，" +
                "低于下限 $cotenantHitFloor（第 113 轮实测 5）：这张白名单本该在真被用着；命中数塌了说明锚点或" +
                "窗口出了变化——顺带说，白名单本身也是一道口子（见类注释第四节）",
            cotenantHits >= cotenantHitFloor,
        )
        assertTrue(
            "有 ${violations.size} 处读数落在某份日志锚点的 ${window} 字符窗口里，却对不上那份日志的登记值：\n" +
                violations.sorted().joinToString("\n") { "  $it" } +
                "\n锚点窗口里的数字就是在讲「这份日志是什么样」，一漂就是文档假话" +
                "（第 113 轮反向变异实测：把 `docs/03:5657` 那行表格里的日志 md5 末字符改掉，违规数 0 → 2）。" +
                "请回改到登记值；若那处确实在讲**当轮当时**的另一份东西，请把它写成不落在窗口内的形态，" +
                "或先把理由记进 `records/01-迭代记录.md`，再把它补进「同句常客」",
            violations.isEmpty(),
        )
    }

    /**
     * 第 4 条：行级闭合——受管文档里任一行出现某份日志的 md5 ⇒ **同一行**必须同时出现该日志的登记
     * **字节数**与**行数**（按去千分位后的数字词元比）；且**逐份**日志的闭合行数不得低于
     * [perLogClosedLineFloor]。
     *
     * 与第 3 条互补：第 3 条管「窗口里出现的数字对不对」，这条管「一行里 md5 与两个读数是否成套」。
     * 只抄 md5、不抄字节/行数，读者就没法拿 `md5sum` 复核——记录也就不完整。
     *
     * 第 113 轮只有「总数 ≥ 10」这一道（真实总数 18 行），抓不住单点改坏；第 114 轮补上逐份下限。
     */
    @Test
    fun linesCarryingALogMd5AreClosed() {
        val violations = mutableListOf<String>()
        val closedPerLog = registered.keys.associateWith { 0 }.toMutableMap()
        var closedLines = 0
        for ((rel, file) in managedFiles()) {
            if (!file.isFile) continue
            for ((offset, text) in file.readText().split("\n").withIndex()) {
                val tokens = numTokens(text)
                var hasLogMd5 = false
                for ((name, reg) in registered) {
                    if (reg.md5 !in text) continue
                    hasLogMd5 = true
                    val hasBytes = reg.bytes.toString() in tokens
                    val hasLines = reg.lines.toString() in tokens
                    if (hasBytes && hasLines) closedPerLog[name] = closedPerLog.getValue(name) + 1
                    if (!hasBytes) {
                        violations += "$rel:${offset + 1} $name 缺字节数 ${reg.bytes}"
                    }
                    if (!hasLines) {
                        violations += "$rel:${offset + 1} $name 缺行数 ${reg.lines}"
                    }
                }
                if (hasLogMd5) closedLines++
            }
        }
        val thin = closedPerLog.filterValues { it < perLogClosedLineFloor }
        val thinDetail = thin.entries.sortedBy { it.key }
            .joinToString("、") { "${it.key} ${it.value} 行" }
        assertTrue(
            "有 ${thin.size} 份日志的闭合行数低于逐份下限 $perLogClosedLineFloor：$thinDetail" +
                "（全部受管文档加起来共 $closedLines 行）。第 113 轮的「总数 ≥ 10」口径抓不住单点改坏——" +
                "真实总数 18 行，改坏一处还剩 17 行，离下限还远；某份日志若被整份从记录里删净，它自己的" +
                "闭合行数会直接掉到 0，那才是这道判据要买的性质。第 114 轮改成逐份贴合实测值" +
                "（$perLogClosedLineFloor）",
            thin.isEmpty(),
        )
        assertTrue(
            "有 ${violations.size} 处「md5 与读数不成套」的行：\n" +
                violations.sorted().joinToString("\n") { "  $it" } +
                "\n同一行里既然给了 md5，就必须给出该日志的字节数与行数——否则读者拿不到 `md5sum` 之外的第二道" +
                "核对（第 114 轮实测：$closedLines 行含日志 md5 的行全部闭合，0 违规）",
            violations.isEmpty(),
        )
    }

    /**
     * 第 5 条：`.gitattributes` 里必须留着让 `records/run-evidence/` **逐字节入库**的那条规则。
     *
     * 这些日志是 Git Bash 文本模式写出的、**含 CR**（Gradle 那几十行是 CRLF、头尾几行是 LF，一行里混合）。
     * 仓库原有规则是 `* text=auto`，在 `core.autocrlf=true` 的机器上会把它们归一化成 LF 再入库 —— 那样 blob
     * 的字节数与 md5 就跟记录里写的对不上，而「证据」这两个字恰恰落在「逐字节」上。
     */
    @Test
    fun evidenceLogsAreNotTextNormalizedInGitattributes() {
        val file = File(repoRoot, ".gitattributes")
        assertTrue(".gitattributes 不在仓库根（${file.path}）：日志的行尾归一化规则整片失效", file.isFile)
        val lines = file.readText().split("\n").map { it.trim() }
        assertTrue(
            ".gitattributes 里没有 `$attributesRule` 这条规则：`* text=auto` 会把这三份日志按 LF 归一化入库，" +
                "工作区与 blob 就不再逐字节相同，记录里的字节数 / md5 也就对不上实际入库的东西。" +
                "当前相关行：\n" + file.readText().split("\n")
                .filter { "run-evidence" in it || "text" in it }
                .joinToString("\n") { "  | $it" },
            attributesRule in lines,
        )
    }

    /**
     * 第 6 条：三份日志的**正文自证**——里面必须有只有真跑过才会出现的东西，外加各道「防零命中」的下限。
     *
     * 这条与第 3 / 4 条是两条腿：第 3 / 4 条保证「记录里的数字与日志对得上」，这条保证「被对账的那些日志
     * **本身**是某次真运行的输出」，而不是三份手写捏造的文本。
     */
    @Test
    fun logContentsShowRealRuns() {
        val run = File(evidenceDir, runLog).readText()
        val help = File(evidenceDir, helpLog).readText()
        val bat = File(evidenceDir, batLog).readText()
        val runHeader = run.split("\n").take(3).map { it.trimEnd('\r') }

        assertEquals("$runLog 的头三行应当是「时间 / 命令 / 主机」三行脚标", 3, runHeader.size)
        assertTrue(
            "$runLog 第一行不是 `[run] 时间 …`（实为 ${runHeader.firstOrNull()?.take(40)}）：run.sh 的运行脚标" +
                "被人改动或这份文件被替换过",
            runHeader[0].startsWith("[run] 时间 "),
        )
        assertTrue(
            "$runLog 第二行不是记「本次用了什么命令」的脚标（实为 ${runHeader.getOrNull(1)?.take(60)}）",
            runHeader[1].startsWith("[run] 命令 ") && runLog in runHeader[1] && "--log" in runHeader[1],
        )
        assertTrue(
            "$runLog 第三行不是 `[run] 主机 …`（实为 ${runHeader.getOrNull(2)?.take(60)}）",
            runHeader[2].startsWith("[run] 主机 "),
        )

        val runMarkers = mapOf(
            "BUILD SUCCESSFUL" to 1,
            "Performing Streamed Install" to 1,
            "Success" to 1,
            "Status: ok" to 1,
            "LaunchState: COLD" to 1,
            "构建 :app:assembleDebug" to 1,
            apkBytes.toString() to 1,
            apkMd5 to 1,
        )
        for ((marker, expected) in runMarkers) {
            assertEquals(
                "$runLog 里 `$marker` 出现次数不是 $expected 次：入口 1（`bash run.sh --log …`）的日志必须" +
                    "同时留下「Gradle 构建成功」「adb 安装成功」「am start 成功」「装的是哪个 APK」四类痕迹" +
                    "——少一类，这份日志就撑不起「真跑过」",
                expected,
                run.count(marker),
            )
        }

        assertEquals(
            "$runLog 里 `构建 :app:assembleDebug` 的次数变了：这条是入口 1 **真的构建了**的证据",
            1,
            run.count("构建 :app:assembleDebug"),
        )
        assertEquals(
            "$batLog 里出现了 `构建 :app:assembleDebug`：入口 3 用的是 `run.bat --no-build`，本该跳过构建。" +
                "命中数必须为 **0** —— 这是「`--no-build` 真的透传到了子进程」的证据（第 112 轮修的就是" +
                "`\"$@\"` 转发丢参数那个缺陷）",
            0,
            bat.count("构建 :app:assembleDebug"),
        )
        assertEquals(
            "$batLog 里没有 `--no-build`：这条命令行脚标记的就是「跳过构建」，少了它就看不出这次跑的是哪条分支",
            1,
            bat.count("--no-build"),
        )
        assertEquals(
            "$batLog 里没有那一跑的 APK 读数：`--no-build` 分支要复用现有的 `app-debug.apk`，日志必须记下" +
                "它到底是哪一个（字节数与 md5 各一次）",
            1,
            bat.count(apkBytes.toString()),
        )
        assertEquals("$batLog 里没有那一跑的 APK md5", 1, bat.count(apkMd5))

        val helpLogLines = help.split("\n").map { it.trimEnd('\r') }
        assertTrue(
            "$helpLog 以 BOM 开头：`bash run.sh --help > … 2>&1` 写出来的是本机控制台的字节，不该有 BOM；" +
                "多一个 BOM 就多三个字节，记录里的字节数与 md5 也会跟着变",
            !help.startsWith("\uFEFF"),
        )
        assertTrue(
            "$helpLog 第一行不是「常伴（For-The-Elder）一键运行脚本…」（实为 ${helpLogLines.firstOrNull()?.take(40)}）",
            helpLogLines.first().startsWith("常伴（For-The-Elder）一键运行脚本"),
        )
        assertEquals(
            "$helpLog 里含 `--log` 的行数不是 2：`run.sh` 的帮助里该有两条 `--log` 用法——`--logcat`（启动后" +
                "跟随日志）与 `--log FILE`（把整次运行完整写进 FILE）。第 112 轮修 `usage()` 时把行号区间改成" +
                "了 `awk`，正是为了此后头部注释随便加行、帮助都不会漏（实为 " +
                "${helpLogLines.count { "--log" in it }} 行）",
            2,
            helpLogLines.count { "--log" in it },
        )
        val logFileHelp = helpLogLines.filter { it.contains("--log FILE") }
        assertEquals("$helpLog 里 `--log FILE` 的说明不是一行", 1, logFileHelp.size)
        assertTrue(
            "$helpLog 里 `--log FILE` 那一行没写清它是干什么的（实为 `${logFileHelp.firstOrNull()?.trim()}`）：" +
                "解说漏了，下一个人就会以为它只管写不打印",
            logFileHelp.first().contains("同时照常打印到屏幕"),
        )
    }

    /**
     * 第 7 条：「同句常客」白名单**有上下界**——第 3 条管「窗口里的数对不对」（例外有白名单），这条管
     * 「白名单本身有没有被用超」。额度按**（受管文档 × 日志）**逐对给（[cotenantCeilings]），**表里没有
     * 的对上限按 0 算**。
     *
     * 为什么另立一条、而不把上限塞进第 3 条：两者报红的**语义不同**——一处是「数字写错了」，一处是
     * 「数字写的是**另一份**东西，而那份东西本不该出现在这里（或不该出现这么多）」。混在一条里，读者
     * 从报错文本看不出该改哪边、还是该改额度表。
     *
     * 这条收的是第 113 轮自挂的那笔账：**「把某处日志的 md5 误写成 APK 的 md5，窗口判据仍然绿」**。
     * 实测（第 117 轮反向变异，逐字节还原后核 md5）：`docs/03` 表格里 run 日志的 md5 改成 APK 的 md5 ⇒
     * 第 3 条**绿**（白名单吃掉）、**这条红**（`docs/03` × run 额度 0，命中 1）。
     */
    @Test
    fun cotenantWhitelistIsBoundedPerAnchor() {
        val perPair = mutableMapOf<Pair<String, String>, Int>()
        var total = 0
        for (anchor in anchors()) {
            val reg = registered.getValue(anchor.log)
            val segment = anchor.text.substring(anchor.end, minOf(anchor.text.length, anchor.end + window))
            for ((_, hit) in readingsIn(segment)) {
                val isCotenant = when (hit.kind) {
                    "md5" -> hit.value != reg.md5 && hit.value in cotenantMd5
                    "bytes" -> hit.value != reg.bytes.toString() &&
                        (hit.value.toIntOrNull() ?: -1) in cotenantBytes
                    else -> false
                }
                if (!isCotenant) continue
                total++
                val key = anchor.rel to anchor.log
                perPair[key] = (perPair[key] ?: 0) + 1
            }
        }
        val over = perPair.filter { (key, n) -> n > (cotenantCeilings[key] ?: 0) }
        assertTrue(
            "有 ${over.size} 处「同句常客」用超了额度（上限按「受管文档 × 日志」逐对给，表里没有的对按 0 算）：\n" +
                over.entries.sortedBy { it.key.first + it.key.second }
                    .joinToString("\n") { (key, n) ->
                        "  ${key.first} × ${key.second} 命中 $n 次（上限 ${cotenantCeilings[key] ?: 0}）"
                    } +
                "\n白名单只认那一跑的 APK 读数（$apkBytes 字节 / $apkMd5），它本该只用在「同一句里既讲这份" +
                "日志、又讲 APK」的地方。命中超额度最常见的一种成因就是——**把某份日志的 md5 或字节数写成了" +
                "APK 的那一份**：值落在白名单里，第 3 条不会报，这条才报。请回改成该日志的登记值；若那处确实" +
                "在讲 APK，请把它挪到不落在日志锚点窗口里的位置（或先把理由记进 `records/01-迭代记录.md`，" +
                "再重测这张额度表——它取的是实测值、不留余量）",
            over.isEmpty(),
        )
        assertTrue(
            "「同句常客」只落在 ${perPair.size} 对（受管文档 × 日志）里，低于下限 $cotenantPairFloor（第 117 轮" +
                "实测 3 对）：额度是逐对给的，对数塌了说明锚点或窗口出了变化，这张额度表该重审",
            perPair.size >= cotenantPairFloor,
        )
        assertTrue(
            "「同句常客」总命中 $total 次，低于下限 $cotenantHitFloor（第 117 轮实测 5）：这张白名单本该在真" +
                "被用着；命中数塌了说明锚点或窗口出了变化",
            total >= cotenantHitFloor,
        )
    }
}
