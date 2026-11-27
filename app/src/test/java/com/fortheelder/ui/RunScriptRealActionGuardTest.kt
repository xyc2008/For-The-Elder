package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「`run.sh` 的**真动作**分支，每一个都有真跑证据兜着」的守卫 —— 补的是第 118 轮留下的第一笔账
 * （`--emulator` 冷启动那条开机路径一直没有判据）。证据在
 * [`records/branch-evidence/`](../../../../../../records/branch-evidence/)。
 *
 * ## 一、它收的是哪笔账，跟另外三把「运行脚本」守卫的分工
 *
 * `run.sh` 的三面已经各有一把尺子，**本条不去重判那三面**：
 *
 * - [RunScriptBranchGuardTest]（第 116 轮）判**解析期与早退路**：坏参数报什么、`--help` 打不打用法、
 *   `--log` 包装层怎么透传退出码、头注释参数集与解析分支是不是一一对应、README 有没有列全参数。
 * - [RunScriptBranchEvidenceGuardTest]（第 118 轮）判**四份设备分支证据逐字节对账**：目录 ↔ 登记表全集
 *   相等、每份的字节/行/md5、**表头里的 `run.sh` 读数等于磁盘现值**（`everyBranchLogSelfReportsTheSameRunSh`
 *   —— 这一面本条**不重复判**）、判定行与退出码、`.gitattributes` 的 `-text` 规则。
 * - [RunEvidenceGuardTest]（第 113 轮）判 `records/run-evidence/` 里那三份**运行入口**日志（`--help` /
 *   直跑 / `run.bat`），与分支证据是两个目录、两套登记。
 *
 * 第 118 轮自己在类注释第四节里挂的账写着：「**`--emulator` 的开机路径仍然没有判据**：那条路只在
 * 『**没有任何已连接设备**』时才会走到，本机模拟器一直在跑、验不了」「**`--avd` 也没有判据**」。
 * 本轮（第 130 轮）**先杀掉本机模拟器、凑出「一台设备都没有」这个前置条件**，把 `--emulator` 冷启动
 * 真跑了一遍并留证（`records/branch-evidence/2026-11-27-r130-emulator.log`，4,072 字节 / 86 行），
 * 把这一半销掉；`--avd` 那半**继续挂着**（本轮只验默认 AVD 名 `Elder_API35`，没验换一个非默认的 AVD 名
 * 会怎样）—— 旧守卫类注释里那一句话也按此改成了「已补一半」。
 *
 * ## 二、判据（五条），以及每一条**独有的那一面**
 *
 * 1. **真动作分支闭集**（[everyRealActionFlagHasEvidence]）：从 `run.sh` 的解析 `case` 块里抠出全部参数臂，
 *    去掉三条**不是真动作**的（下节说明为什么），剩下的每个参数**至少要有一份证据的分支行覆盖它**。
 *    第 118 轮的登记表是**按文件名**钉的，这条是**按参数名**钉的 —— 补的是「新增一个真动作参数却不留
 *    证据」这个缺口，两把尺子互补而不重叠。
 * 2. **各份证据说的是同一份安装包，且等于磁盘现值**（[everyEvidenceHeaderApkMatchesTheBuildOnDisk]）：
 *    表头 `[branch] apk <md5> 字节 <b>` 各行之间必须一致（五份证据都该指着同一个 APK），且
 *    `app/build/outputs/apk/debug/app-debug.apk` **若在盘上**就必须与它逐位相等。`build/` 被 gitignore，
 *    产物不一定在盘上，所以「不在盘上」时只压「内部一致」，**不做硬断言**。
 * 3. **冷启动那份证据记着冷启动的真事实**（[theEmulatorBranchEvidenceRecordsAColdBoot]）：表头要有
 *    「开跑前 adb devices 无设备」「冷启动 AVD Elder_API35 端口 5554」，正文要有「开模拟器 …（日志 …」
 *    「设备 emulator-5554」「先卸载 …」「Success」「完成：… 已在 emulator-5554 上跑起来」；截图读数若
 *    对得上盘上的 PNG（`build/run-shot.png` 在盘上时）则必须逐字对得上。
 * 4. **`run.sh` 里冷启动那一段的**顺序**（[runShBootsTheEmulatorBeforeItWaitsForBoot]）：源码里必须
 *    `开模拟器` < `wait-for-device` < `sys.boot_completed` < `say "安装"` < `完成：`，且 `--emulator` 那一段
 *    里必须真的把模拟器**后台**起起来（`&` + `>"$ELOG" 2>&1` + `echo $! >` 写 pid 文件）再等开机，并且
 *    带着 `-no-snapshot`、`-gpu swiftshader_indirect`、`240` 秒的等机上限。这条钉的是「**先起进程、再等
 *    开机**」这个顺序 —— 顺序反了就成了「等一个根本没起来的设备」，而它**不看证据**，看的是脚本现值。
 * 5. **冷启动证据的规模与形状下限**（[emulatorEvidenceKeepsTheColdStartFactsBounded]）：表头行数 ≥ 10、
 *    字节 ≥ 1,500、`退出码 0`、`判定 PASS 全部 N 项`（N ≥ 8）、正文里有 `BUILD SUCCESSFUL` 与
 *    `up-to-date`（这一跑**带构建**，与第 118 轮那四份都不同，那是它区别于那四份的形状）、两个产物
 *    路径字面量（`build/run-emulator.log` / `build/run-emulator.pid`）必须在表头里露过面。
 *    **上限不判**：真流有多长不判，照第 118 轮的口径。
 *
 * ## 三、为什么这三条参数被排除在「真动作」之外（写死，照实交代）
 *
 * - `--log`：它不改脚本做什么，只**包一层**把子进程的输出重定向到文件再把退出码原样透传 —— 这一面已经在
 *   [RunScriptBranchGuardTest.logWrapperCapturesTheFailureAndPropagatesItsExitCode] 里真跑判过了，
 *   不必也不该要求它单独留一份「分支证据」。
 * - `--avd`：它只改一个修饰量（开机挑哪个 AVD 名）。语义上它**确实会影响行为**，所以这条排除是本条的
 *   **已知缺口**，不是「它不算动作」的结论 —— `--avd` 的半笔账照实挂着（见 [documentedGaps]）。
 * - `-h | --help`：打印用法即退出，不做任何动作，[RunScriptBranchGuardTest.helpPrintsTheHeaderCommentAndDoesNoWork]
 *   已判。
 *
 * 除这三条以外的参数（`--emulator` / `--no-build` / `--reinstall` / `--shot` / `--logcat`）都算真动作。
 * `--no-build` 算真动作的理由：它「跳过构建」本身就是可观察的行为，它的证据必须证明**构建行没出现**
 * （那正是第 118 轮 `mustNotHave` 里钉的 `构建 :app:assembleDebug`）。
 *
 * ## 四、它不保证什么（照实交代）
 *
 * - **不重跑任何脚本、不连任何设备**：unit test 里不许有设备依赖（[RunScriptBranchEvidenceGuardTest] 的底线，
 *   本条照守）。判据 1 / 3 / 5 读的是**已经入库的证据**，判据 2 / 4 读的是**当下的源码**，两者各管一面。
 * - **判据 1 判的是「有没有证据」，不是「证据对不对」**：一份证据落在目录里、分支行里点了某个参数，
 *   这条就算它覆盖了；内容对不对是 [RunScriptBranchEvidenceGuardTest] 判据 1~4 的事。
 * - **`--avd` 仍然没有判据**（[documentedGaps] 里那条）：本轮只验了默认 AVD 名这一条路，
 *   换一个非默认 AVD 名的开机路径**没验**。
 * - **冷启动会弹一个可见窗口**：`run.sh` 的冷启动那一段**没有传 `-no-window`**（脚本里就这一处，第 158~175
 *   行），所以它起的模拟器在桌面上是**看得见**的。这是脚本的真行为，本轮照实记，**没有为此改 `run.sh`**：
 *   改它会让第 118 轮那四份证据表头里的 `run.sh` 读数全部过期、四份证据一起作废，代价远大于收益。
 * - **判据 3 的截图那一半是软的**：`build/run-shot.png` 被 gitignore，别人机器上多半不在盘上，那时这一半
 *   自动跳过（只判表头里有没有那行读数），所以它不算硬判据。
 * - **它不改任何文档、不改 `run.sh`**。报红之后「该重跑探针」还是「该回改文档」，本守卫不判。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。本轮补的是「`run.sh` 的真动作分支 ↔ 真跑证据」这一面的兜底。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这些用例**直接失败**
 * 而不是跳过。
 */
class RunScriptRealActionGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（与另两把运行脚本守卫逐字相同）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 分支证据日志目录（刻意**不用** `.md` 后缀：文档普查器只按 `.md` 枚举）。 */
    private val evidenceDir: File =
        File(repoRoot, "records" + File.separatorChar + "branch-evidence")

    /** `run.sh` 本体：判据 1 抠参数臂、判据 4 判顺序。 */
    private val runSh: File = File(repoRoot, "run.sh")

    /** 安装包产物（gitignore 覆盖，可能不在盘上）。 */
    private val apk: File = File(
        repoRoot,
        listOf("app", "build", "outputs", "apk", "debug", "app-debug.apk").joinToString(File.separator),
    )

    /** 冷启动那份证据的文件名（第 130 轮真跑）。 */
    private val emulatorLog = "2026-11-27-r130-emulator.log"

    /** 那一跑顺手落下的截图（gitignore 覆盖，可能不在盘上）。 */
    private val shot: File = File(repoRoot, "build" + File.separatorChar + "run-shot.png")

    /** 本条**主动排除**在「真动作」之外的三个参数，以及排除它的理由（理由写在类注释第三节）。 */
    private val nonAction = linkedMapOf(
        "--log" to "包一层重定向 + 透传退出码，动作在子进程里，已在 RunScriptBranchGuardTest 真跑判过",
        "--avd" to "只改修饰量（挑哪个 AVD 名），它的开机路径本轮仍未验 —— 这是本条的已知缺口",
        "-h" to "打印用法即退出；`--help` 与它同臂，已在 RunScriptBranchGuardTest 判过",
        "--help" to "同上",
    )

    /** 本条自认仍挂着的缺口（照实登记；判据 1 的下限压着这张表不许被清空）。 */
    private val documentedGaps = listOf(
        "--avd：非默认 AVD 名的开机路径没有判据（本轮只验了默认 Elder_API35）",
    )

    /** 真动作参数个数下限（现值 5：`--emulator` / `--no-build` / `--reinstall` / `--shot` / `--logcat`）。 */
    private val realActionFloor = 5

    /**
     * 解析 `case` 块里的参数臂：行首空白 + `-x` 或 `--xyz`（可用 `|` 并列）+ `)`。
     *
     * 只吃参数臂这一种形状（`--emulator)`、`-h | --help)`），不吃 `*)`、`*Success*)` 这类通配臂。
     */
    private val flagArm = Regex(
        """^[ \t]+(-{1,2}[A-Za-z][A-Za-z0-9-]*(?:[ \t]*\|[ \t]*-{1,2}[A-Za-z][A-Za-z0-9-]*)*)\)[ \t]*(.*)$""",
    )

    /**
     * 表头里的分支行：`[branch] 分支 --emulator --reinstall --shot`。
     *
     * 四个表头正则都带 `MULTILINE`：喂进来的是一整块表头（多行），
     * 不带它则 `^` / `$` 只认整串首尾，`find` 会一处都找不着。
     */
    private val branchHeader = Regex("""^\[branch] 分支 (\S.*)$""", RegexOption.MULTILINE)

    /** 表头里的安装包行：`[branch] apk <md5> 字节 <b>`。 */
    private val apkHeader = Regex("""^\[branch] apk ([0-9a-f]{32}) 字节 (\d+)$""", RegexOption.MULTILINE)

    /** 表头里的判定行：`[branch] 判定 PASS 全部 <n> 项`。 */
    private val verdictHeader = Regex("""^\[branch] 判定 PASS 全部 (\d+) 项$""", RegexOption.MULTILINE)

    /** 冷启动证据表头里的截图行：`[branch] 截图 <path> PNG magic ok 尺寸 <w>x<h> 字节 <n>`。 */
    private val shotHeader = Regex(
        """^\[branch] 截图 (\S+) PNG magic ok 尺寸 (\d+)x(\d+) 字节 (\d+)$""",
        RegexOption.MULTILINE,
    )

    /** 表头与正文的边界（与另两把守卫逐字相同）。 */
    private val separator = "---- 以下为 run.sh 原始输出 ----"

    /** 判据 1：`run.sh` 里每个真动作参数都必须至少被一份证据的分支行覆盖。 */
    @Test
    fun everyRealActionFlagHasEvidence() {
        val parsed = parseFlagArms(runSh.readText().replace("\r\n", "\n"))
        assertTrue(
            "从 $runSh 的解析 `case` 块里一个参数臂都没抠出来（抠臂的正则失效了？）：" +
                "抠出 0 个参数时本条会退化成「零个真动作 ⇒ 一律通过」",
            parsed.isNotEmpty(),
        )
        val realAction = parsed.filterNot { it in nonAction.keys }.sorted()
        assertTrue(
            "真动作参数只有 ${realAction.size} 个（$realAction），低于下限 $realActionFloor：" +
                "要么参数集被抽掉了，要么 [nonAction] 里多排除了不该排除的",
            realAction.size >= realActionFloor,
        )
        assertTrue(
            "自认缺口的登记表被清空了（[documentedGaps]，现值 ${documentedGaps.size} 条）：" +
                "缺口消失要么是补了判据、要么是有人顺手删了 —— 两种情况都该在本条里留痕迹",
            documentedGaps.isNotEmpty(),
        )

        val covered = coveredFlags()
        assertTrue(
            "分支证据目录 $evidenceDir 里一份日志都没有，抠不出任何分支行",
            covered.isNotEmpty(),
        )
        val missing = realAction.filterNot { it in covered }
        assertTrue(
            "有 ${missing.size} 个真动作参数没有任何证据覆盖：$missing\n" +
                "证据里出现过的参数：${covered.sorted()}\n" +
                "本条按**参数名**钉闭集（第 118 轮那把是按**文件名**钉的，互补）。新加一个真动作参数时，" +
                "必须真跑它一次并把证据落进 `records/branch-evidence/`（同时更新 [RunScriptBranchEvidenceGuardTest]" +
                " 的 [registered] 与 `branchLogFloor`），否则这里当场报红。若它确实不该单独留证，" +
                "把理由写进类注释第三节的 [nonAction] 里 —— 别默认排除",
            missing.isEmpty(),
        )
    }

    /** 判据 2：五份证据指着同一个安装包，且（产物在盘上时）等于磁盘现值。 */
    @Test
    fun everyEvidenceHeaderApkMatchesTheBuildOnDisk() {
        val logs = evidenceFiles()
        assertTrue("$evidenceDir 里没有证据文件", logs.isNotEmpty())
        val seen = mutableMapOf<String, Pair<Int, String>>() // name -> (bytes, md5)
        val bad = mutableListOf<String>()
        for (file in logs) {
            val m = apkHeader.find(headerOf(file))
            if (m == null) {
                bad += "${file.name} 的表头里找不到形状为 `[branch] apk <md5> 字节 <b>` 的那行"
                continue
            }
            val (md5, bytes) = m.destructured
            seen[file.name] = bytes.toInt() to md5
        }
        val distinct = seen.values.toSet()
        assertTrue(
            "五份证据表头里的安装包读数**不是同一个**（出现 ${distinct.size} 套）：" +
                seen.entries.joinToString("；") { "${it.key} = ${it.value.first} 字节 / ${it.value.second}" } +
                "\n它们该指着同一份 APK —— 分叉说明有人只重跑了其中一条分支，或者换了一份产物",
            distinct.size == 1,
        )
        if (apk.isFile) {
            val diskBytes = apk.length().toInt()
            val diskMd5 = md5Of(apk)
            val (logBytes, logMd5) = seen.values.firstOrNull() ?: (diskBytes to diskMd5)
            assertTrue(
                "证据表头记的是 $logBytes 字节 / md5 $logMd5，而盘上的 ${apk.name} 现在是" +
                    " $diskBytes 字节 / md5 $diskMd5\n" +
                    "这条判的是「证据说的那份产物，就是现在盘上这份」。产物在盘上时必须逐位相等；" +
                    "不在盘上时（`build/` 被 gitignore）本条只压「五份之间一致」，不做硬断言",
                logBytes == diskBytes && logMd5 == diskMd5,
            )
        }
    }

    /** 判据 3：冷启动那份证据记着「从零设备开始冷启动」的真事实。 */
    @Test
    fun theEmulatorBranchEvidenceRecordsAColdBoot() {
        val file = File(evidenceDir, emulatorLog)
        assertTrue(
            "$emulatorLog 不在 $evidenceDir 里。这一份是第 130 轮真跑 `--emulator` 冷启动留下的证据，" +
                "它被删掉时本条报红（第 118 轮那把的目录全集相等判据也会一起报）",
            file.isFile,
        )
        val text = normalizedOf(file)
        val head = headerOf(file)
        for (needle in listOf("冷启动 AVD Elder_API35", "端口 5554", "开跑前 adb devices 无设备")) {
            assertTrue(
                "$emulatorLog 的表头里找不到 `$needle`：\n$head\n" +
                    "这三处是这一跑区别于其它四份的形状 —— 它必须是**从一台设备都没有**开始、" +
                    "冷启动默认 AVD `Elder_API35`、落在 5554 端口",
                head.contains(needle),
            )
        }
        val body = text.substringAfter(separator)
        for (needle in listOf(
            "开模拟器 Elder_API35（日志",
            "设备 emulator-5554",
            "先卸载 com.fortheelder.debug",
            "Success",
            "完成：com.fortheelder.debug 已在 emulator-5554 上跑起来",
        )) {
            assertTrue(
                "$emulatorLog 的正文里找不到 `$needle` —— 冷启动那一段要走通的字样不全",
                body.contains(needle),
            )
        }
        // 截图那一半是软的：产物被 gitignore，不在盘上时只要求表头里有那行读数
        val shotLine = head.lineSequence().firstOrNull { it.startsWith("[branch] 截图") }
        assertTrue("$emulatorLog 的表头里没有 `[branch] 截图 …` 那一行", shotLine != null)
        if (shot.isFile) {
            val m = shotHeader.find(shotLine!!)
            assertTrue(
                "表头里的截图行形状对不上 `[branch] 截图 <path> PNG magic ok 尺寸 <w>x<h> 字节 <n>`：$shotLine",
                m != null,
            )
            val raw = shot.readBytes()
            val w = intFromBigEndian(raw, 16)
            val h = intFromBigEndian(raw, 20)
            val (_, logW, logH, logBytes) = m!!.destructured
            assertTrue(
                "表头记的截图是 ${logW}x${logH} / $logBytes 字节，而盘上的 ${shot.name} 实测 ${w}x$h /" +
                    " ${raw.size} 字节（这一半只在截图还在盘上时判）",
                logW.toInt() == w && logH.toInt() == h && logBytes.toInt() == raw.size,
            )
        }
    }

    /** 判据 4：`run.sh` 里冷启动那一段的顺序是「先后台起进程，再等开机」。 */
    @Test
    fun runShBootsTheEmulatorBeforeItWaitsForBoot() {
        val src = runSh.readText().replace("\r\n", "\n")
        val marks = listOf(
            "开模拟器" to "脚本报「开模拟器 …（日志 …）」",
            "wait-for-device" to "`adb wait-for-device`",
            "sys.boot_completed" to "轮询 `getprop sys.boot_completed`",
            "say \"安装\"" to "`say \"安装\"`（装那一段的起头）",
            "完成：" to "末行 `say \"完成：…`",
        )
        val idx = marks.map { (needle, why) -> needle to (why to src.indexOf(needle)) }
        val missing = idx.filter { it.second.second < 0 }
        assertTrue(
            "`run.sh` 里找不到这些字样（顺序判据的前提没了）：${missing.map { it.first }}",
            missing.isEmpty(),
        )
        val order = idx.map { it.first to it.second.second }
        var prev = -1
        val broken = mutableListOf<String>()
        for ((needle, at) in order) {
            if (at <= prev) broken += "`$needle`（第 ${at + 1} 个字符处）没有排在上一处之后"
            prev = maxOf(prev, at)
        }
        assertTrue(
            "冷启动那一段的顺序不对：${broken.joinToString("；")}\n" +
                "顺序必须是 开模拟器 < wait-for-device < sys.boot_completed < say \"安装\" < 完成：。" +
                "反了就成了「等一个根本没起来的设备」——注意这条看的是**脚本现值**，不看证据",
            broken.isEmpty(),
        )

        val block = src.substringAfter("BOOT_EMULATOR\" -eq 1", "")
            .substringBefore("say \"设备")
        assertTrue(
            "从 `run.sh` 里抠不出 `--emulator` 那一段（`BOOT_EMULATOR\" -eq 1` 到 `say \"设备` 之间）：" +
                "抠不出时下面几条会对着空串判、等于不判",
            block.length > 200,
        )
        for (needle in listOf(
            "&",
            ">\"\$ELOG\" 2>&1",
            "echo \$! >",
            "-no-snapshot",
            "-gpu swiftshader_indirect",
            "240",
        )) {
            assertTrue(
                "`--emulator` 那一段里找不到 `$needle`：模拟器必须**后台**起（`&`）、输出重定向进日志、" +
                    "把 pid 写进文件，再等开机；并带 `-no-snapshot` / `-gpu swiftshader_indirect` / 240 秒上限",
                block.contains(needle),
            )
        }
    }

    /** 判据 5：冷启动证据的规模与形状下限（上限不判）。 */
    @Test
    fun emulatorEvidenceKeepsTheColdStartFactsBounded() {
        val file = File(evidenceDir, emulatorLog)
        assertTrue("$emulatorLog 不在 $evidenceDir 里", file.isFile)
        val bytes = file.length().toInt()
        val head = headerOf(file)
        val headLines = head.split('\n').count { it.trim().isNotEmpty() }
        assertTrue(
            "$emulatorLog 的表头只有 $headLines 行，低于下限 10 行：" +
                "表头被截断时后面几条「表头里必须有什么」等于不判",
            headLines >= 10,
        )
        assertTrue(
            "$emulatorLog 只有 $bytes 字节，低于下限 1500 字节：" +
                "它是一整条真跑链路（构建 + 冷启动 + 安装 + 启动 + 截图）的原始输出，不该这么小",
            bytes >= 1500,
        )
        assertTrue(
            "$emulatorLog 的表头里没有 `[branch] 退出码 0` 那一行：\n$head",
            head.contains("[branch] 退出码 0"),
        )
        val m = verdictHeader.find(head)
        assertTrue("$emulatorLog 的表头里找不到 `[branch] 判定 PASS 全部 <n> 项`：\n$head", m != null)
        val n = m!!.groupValues[1].toInt()
        assertTrue("$emulatorLog 的判定记的是 $n 项，低于下限 8 项", n >= 8)

        val body = normalizedOf(file).substringAfter(separator)
        for (needle in listOf("BUILD SUCCESSFUL", "up-to-date")) {
            assertTrue(
                "$emulatorLog 的正文里找不到 `$needle`：这一跑是**带构建**的（没有传 `--no-build`），" +
                    "它区别于第 118 轮那四份的形状就在这里 —— 本轮产品码零改动，所以应报 up-to-date",
                body.contains(needle),
            )
        }
        for (needle in listOf("build/run-emulator.log", "build/run-emulator.pid")) {
            assertTrue(
                "$emulatorLog 的表头里没提过 `$needle`：冷启动顺手落下的两样产物" +
                    "（日志 / pid 文件）应该在 extra 行里露过面",
                head.contains(needle),
            )
        }
    }

    // ---------- 读盘与解析 ----------

    /** 目录里全部证据文件（按文件名排序）。 */
    private fun evidenceFiles(): List<File> =
        evidenceDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    /** 表头段（分隔行以上），逐行去掉行尾 `\r`。 */
    private fun headerOf(file: File): String =
        file.readText().split('\n').joinToString("\n") { it.trimEnd('\r') }.substringBefore(separator)

    /** 整份证据按 `\n` 切、逐行去掉行尾 `\r`（与另两把守卫同一口径）。 */
    private fun normalizedOf(file: File): String =
        file.readText().split('\n').joinToString("\n") { it.trimEnd('\r') }

    /** 从 `run.sh` 的 `case` 块里抠出全部参数臂里的参数名（`|` 并列的逐个展开，去重）。 */
    private fun parseFlagArms(src: String): List<String> {
        val flags = mutableListOf<String>()
        for (line in src.split('\n')) {
            val m = flagArm.find(line) ?: continue
            for (part in m.groupValues[1].split('|')) {
                val flag = part.trim()
                if (flag.startsWith("-") && flag.length > 1) flags += flag
            }
        }
        return flags.distinct()
    }

    /** 证据里出现过的参数名（各份表头 `[branch] 分支 …` 行取并集）。 */
    private fun coveredFlags(): Set<String> {
        val out = mutableSetOf<String>()
        for (file in evidenceFiles()) {
            for (line in headerOf(file).split('\n')) {
                val m = branchHeader.find(line) ?: continue
                m.groupValues[1].split(' ').map { it.trim() }.filter { it.startsWith("-") }.forEach { out += it }
            }
        }
        return out
    }

    /** 大端读 4 字节（PNG 的 IHDR 宽高就在这两个偏移上）。 */
    private fun intFromBigEndian(raw: ByteArray, at: Int): Int {
        if (raw.size < at + 4) return -1
        return ((raw[at].toInt() and 0xff) shl 24) or ((raw[at + 1].toInt() and 0xff) shl 16) or
            ((raw[at + 2].toInt() and 0xff) shl 8) or (raw[at + 3].toInt() and 0xff)
    }

    /** 文件 md5（小写十六进制，与另两把守卫逐字相同）。 */
    private fun md5Of(file: File): String =
        java.security.MessageDigest.getInstance("MD5").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
