package com.fortheelder.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「`run.sh` 的设备分支与开机路径（`--no-build` / `--shot` / `--reinstall` / `--logcat` / `--emulator` /
 * `--avd`）**真跑过**，而且真跑的证据能跟脚本自身对账」的守卫。证据在
 * [`records/branch-evidence/`](../../../../../../records/branch-evidence/)。
 *
 * ## 一、它收的是哪笔账
 *
 * 第 112 轮把三条运行入口各真跑一遍留证（`records/run-evidence/` 三份日志），第 113~117 轮给那三份立了
 * [RunEvidenceGuardTest] 五道判据。但第 103 轮交付的 `run.sh` **分支**（`--no-build` / `--reinstall` /
 * `--shot` / `--logcat` / `--avd` / `--emulator`）一直没有自动化判据：旧守卫读的是**日志文本**，判不到
 * **脚本行为**。第 116 轮交付的 [RunScriptBranchGuardTest] 判的是「坏参数、早退路径」，那些**要连上设备
 * 才能走通**的分支仍然没人判。第 118 轮把其中四条真跑了一遍（`--no-build` / `--shot` / `--reinstall` /
 * `--logcat`），各留一份证据日志入库；第 130 轮又把 `--emulator` 冷启动那一条真跑了一遍，留下第五份；
 * 第 131 轮再把 `--avd` 递一个**非默认 AVD 名**（`TripWear_API35`）的开机路真跑了一遍，留下第六份。
 * 第 132 轮把**同一条路的另一个入口**补上 —— 不传 `--avd`、只给环境变量 `AVD=TripWear_API35`，再走一遍冷启动，
 * 留下第七份（4,426 字节 / 88 行 / 判定 19 项）；第 133 轮再把这条路上**两个入口都不给**的缺省臂补上 —— 命令行
 * 不传 `--avd`、进程环境不给 `AVD`，走 `AVD_NAME="${AVD:-Elder_API35}"` 的默认值，留下第八份（4,415 字节 / 88 行 /
 * 判定 20 项）；第 134 轮再把这条路上**两个入口同时给出**这个组合真跑一遍 —— 命令行 `--avd Elder_API35` 与环境变量
 * `AVD=TripWear_API35` 一起给，看命令行那个臂盖不盖得住环境变量，留下第九份（4,774 字节 / 92 行 / 判定 23 项）。
 * 本条把「证据 ↔ 脚本自身」这一面立成机器判据。
 *
 * **方案为什么不是「新开一个 Gradle 源集，把探针搬进去当测试跑」**：那条路要 AGP 为**非 variant 源集**
 * 生成编译任务，本项目的 AGP 不为它生成，探针放进去根本不会被编译、更不会被执行——等于把一句不信的话
 * 写成判据。所以改成「**探针真跑 + 证据入库 + 小守卫读证据**」：探针是工装（`build/` 下，不入库），
 * 入库的是它产出的日志（第 118 轮四份 + 第 130 轮一份 + 第 131 轮一份 + 第 132 轮一份 + 第 133 轮一份 +
 * 第 134 轮一份），判据落在证据上。
 *
 * **证据为什么另放 `records/branch-evidence/`、不塞进 `records/run-evidence/`**：[RunEvidenceGuardTest]
 * 的第 2 条判据是「目录里的文件名集合 == 登记表键集合」这个**全集相等**。往那个目录里多扔文件，
 * 这条当场报红；要把新文件登记进去，又会撞上「每份日志 ≥ 5 个锚点」「每份 ≥ 8 行闭合」另外两条。
 * 换个目录，旧守卫七道判据**一行都不用动**，新证据也各按各的口径判。
 *
 * ## 二、判据（五条）
 *
 * 1. **目录双向 + 逐份日志对账**（[everyBranchLogMatchesDisk]）：`records/branch-evidence/` 里多一个文件、
 *    少一个文件都报；[registered] 里每份日志的磁盘字节数 / 行数 / md5 必须等于登记值。
 * 2. **`run.sh` 自身对账**（[everyBranchLogSelfReportsTheSameRunSh]）：每份日志的表头里都有一行
 *    `[branch] run.sh <md5> 字节 <字节数> 行 <行数>`，那是**跑那一刻**探针量到的 `run.sh` 读数。这条把
 *    它与磁盘上 `run.sh` 的实测值比——**改了 `run.sh` 却没重跑探针，这里当场报红**。这是本类里唯一一处
 *    「证据跟**当前的**源码对账」而不是「证据跟冻结值对账」的判据。
 * 3. **分支痕迹自证**（[eachLogCarriesItsOwnBranchTrace]）：每份证据里必须有**只有那条分支真跑过**才会
 *    出现的字样（见 [BranchLog.mustHave]），不许出现不该出现的字样（见 [BranchLog.mustNotHave]，例如
 *    `--no-build` 的证据里不许有 `构建 :app:assembleDebug`、`--logcat` 的证据里不许有 `完成：`）。
 * 4. **判定行与退出码自证**（[everyBranchLogRecordsItsVerdict]）：每份证据的表头里，`判定` 行必须是
 *    `PASS 全部 N 项`（N 逐份登记，贴实测值），`退出码` 行必须是那一跑的实测值（`0`，或 `--logcat` 的
 *    `截断`）。这判的是「探针当年自己认定这条分支过了」，不是「现在还是全过」——现在是判据 1~3 在判。
 * 5. **`.gitattributes` 逐字节规则 + 证据规模下限**（[branchEvidenceIsKeptByteExact]）：这两条一起判。
 *    `--logcat` 那一份带 **583 个 `\r` 字节**（本目录里带 CR 最多的一份，`adb.exe` 在 Windows 上吐 CRLF；
 *    第 130 ~ 134 轮那五份各另有 49 个、`--shot` 那份另有 1 个），若被 `* text=auto`
 *    归一化成 LF，字节数与 md5 全变、判据 1 当场报红，所以 `records/branch-evidence/` 一棵树的
 *    `-text` 规则（字面量在 [attributesRule]）是硬需求，不是装饰。
 *
 * ## 三、它能不能抓住「有人改坏一处」（下一轮反向变异的候选）
 *
 * - 改某份证据中间一个字节（字节数不变、md5 变）⇒ 判据 1 红。
 * - 改 `run.sh` 一个字（不动证据）⇒ 判据 2 红：证据表头里的 md5/字节数/行数是**跑那一刻**的，对不上。
 *   反过来说，**若挪动 `run.sh` 后重跑了探针却没更新证据**，判据 1 也会红。两条各管一面。
 * - 删掉 `--logcat` 证据里 `跟随 logcat` 那行 ⇒ 判据 3 红。
 * - 把 `--reinstall` 证据里的 `先卸载 com.fortheelder.debug` 改掉 ⇒ 判据 3 红。
 * - 把第 130 轮证据表头里的 `run.sh <md5>` 末位改一位 ⇒ 判据 2 红（第 130 轮真验过这一档）。
 *
 * ## 四、它不保证什么（照实交代）
 *
 * - **这批日志不可重跑重现**：设备序列号、`pm path` 里的那串 `~~PM8rGTT7Bk_n7K2FSK822A==`、截图字节数
 *   （第 118 轮那一跑是 22,064 B，第 130 轮是 24,797 B，第 131 轮是 19,248 B，第 132 轮是 836,177 B，
 *   第 133 轮是 63,252 B）、pid、
 *   `OnTime` 每次都会变，
 *   md5 自然也变。它们钉住的是「**那一次**真跑的真实输出」，**不是**「任何人在任何机器上重跑都会得到这份
 *   字节」的断言。也正因如此，本守卫**只判已经入库的证据**，不重跑任何脚本、不连任何设备——**unit test
 *   里不许有设备依赖**，这是本类的底线。
 * - **`--emulator` 的开机路径：第 130 轮已补上**。第 118 轮挂的那半笔——「那条路只在『**没有任何已连接
 *   设备**』时才会走到，本机模拟器一直在跑、验不了」——第 130 轮用「先杀掉本机模拟器、凑出零设备」的
 *   办法真跑了一遍，证据是 `2026-11-27-r130-emulator.log`（4,072 字节 / 86 行 / 判定 17 项）。它「找不到
 *   `emulator` 二进制就 die」的早退路另在第 116 轮的 [RunScriptBranchGuardTest] 里。**「那条参数有没有
 *   证据」这一面另见第 130 轮的 [RunScriptRealActionGuardTest]**，本条只判「这份证据对不对得上」。
 * - **`--avd` 的开机路径：第 131 轮已补上**。它改的是开机路径挑哪个 AVD。第 130 轮只验了默认 AVD 名
 *   `Elder_API35` 这一条路；第 131 轮把命令行里递一个**非默认 AVD 名**（`TripWear_API35`）的开机路真跑了
 *   一遍，证据是 `2026-11-27-r131-avd.log`（4,222 字节 / 87 行 / 判定 18 项），表头明写「默认 AVD 名
 *   `Elder_API35` 本轮**未**被用到（脚本没有退回默认名）」。这半笔已在
 *   [RunScriptRealActionGuardTest] 的判据 6 里落地（递进去的名字必须原样到达模拟器）。
 * - **环境变量入口 `AVD=`：第 132 轮已补上**。它和 `--avd` 是**同一条路的两个入口**（脚本第 37 行
 *   `AVD_NAME="${AVD:-Elder_API35}"` 与第 58 行的参数臂），第 131 轮只证了参数臂那条。第 132 轮**不传
 *   `--avd`、只给 `AVD=TripWear_API35`**，再杀一次模拟器凑零设备，冷启动真跑并留证
 *   `2026-11-27-r132-avd-env.log`（4,426 字节 / 88 行 / 判定 19 项）。这一跑比第 131 轮多一样东西：它在
 *   设备上现读了一次 `getprop ro.boot.qemu.avd_name`，把**模拟器自己认的名字**记进了表头 ——
 *   「脚本打印对了」与「真的开进了这个 AVD」是两件事，这一路证据才顶得上后者。落地在
 *   [RunScriptRealActionGuardTest] 的判据 8。
 * - **默认 AVD 名臂（两个入口都不给）：第 133 轮已补上**。它是 `AVD_NAME="${AVD:-Elder_API35}"` 的**缺省分支**——
 *   命令行不传 `--avd`、进程环境也不给 `AVD`，名字只能来自脚本自带那个默认值。第 133 轮同样先杀模拟器凑零设备，
 *   冷启动真跑并留证 `2026-11-27-r133-shot.log`（4,415 字节 / 88 行 / 判定 20 项）：表头记着默认臂
 *   `AVD_NAME="${AVD:-Elder_API35}"`、`冷启动 AVD Elder_API35`，设备上现读的 `ro.boot.qemu.avd_name` 正是
 *   `Elder_API35`，并钉着「非默认 AVD 名 `TripWear_API35` 本轮**未**被用到（既没走参数臂也没走环境变量臂）」。
 *   这一臂**没有单独新立判据**（判据 8 压的是同一条路的源码面 `AVD_NAME="${AVD:-Elder_API35}"`，只是入口给法
 *   不同），它同时是第 133 轮立的判据 9 里「最新那一份」的认领者。
 * - **`--logcat` 的证据是「上限 600 行的原样前缀」，不是完整流**：这条分支 `exec` 进 `adb logcat` 之后
 *   **永不返回**，探针只能截取。**600 这个上限是拍的**（量到 42,207 B / 600 行，比原来那份 29 MB 的写法
 *   小三个数量级），不是从哪条要求推出来的。判据 3 只要求「有 brief 日志」，判据 5 压的是**保留下来的
 *   证据规模下限**（[briefLineFloor]）——**两头的上限不判**：真流有多长，探针自己也不知道。
 * - **判据 3 判的是文本，不是行为**：它证明的是「这份证据里写着那条分支才有的字样」，不是「现在重跑那条
 *   分支还会这样」。真行为的那一面在第 116 轮的 [RunScriptBranchGuardTest]、第 130 轮的
 *   [RunScriptRealActionGuardTest] 与探针本身里；本条把「跑过什么、结果如何」固定下来，好让后来人
 *   **改没改过 `run.sh`** 这件事有据可查（判据 2）。
 * - **它不改任何文档、不改 `run.sh`**。报红之后「该重跑探针」还是「该回改文档」，本守卫不判。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。本条补的是「`run.sh` 的分支与开机路径 ↔ 真跑证据」这一面的兜底。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这些用例**直接失败**
 * 而不是跳过。行数口径与 [RunEvidenceGuardTest.lineCount] / [DocCodeLineClaimGuardTest.lineCount] 逐字相同
 * （数 `\n`，不以 `\n` 结尾再补一个），好让本条读数与 `wc -l` 直接对账。
 */
class RunScriptBranchEvidenceGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 分支证据日志目录（刻意**不用** `.md` 后缀：六把文档普查器只按 `.md` 枚举）。 */
    private val evidenceDir: File =
        File(repoRoot, "records" + File.separatorChar + "branch-evidence")

    /** `run.sh` 本体：判据 2 要拿它跟证据表头里的读数比。 */
    private val runSh: File = File(repoRoot, "run.sh")

    /** 那条让这些证据逐字节入库的规则（判据 5）。 */
    private val attributesRule = "records/branch-evidence/** -text"

    /**
     * 九份证据的登记读数（冻结值：前四份来自第 118 轮、第五份来自第 130 轮、第六份来自第 131 轮真跑、
     * 第七份来自第 132 轮真跑、第八份来自第 133 轮真跑、第九份来自第 134 轮真跑）。
     *
     * 改动这张表之前先读类注释第四节：这些是**那一次**运行的真实输出，不是可重跑样本。
     */
    private val registered = mapOf(
        "2026-11-27-r118-nobuild.log" to BranchLog(
            bytes = 1070,
            lines = 25,
            md5 = "846786982fa2270a6c8ba212385908a7",
            verdictCount = 6,
            exitCode = "0",
            mustHave = listOf(
                "APK  ",
                "11020680",
                "8adbba63ca911252f98ed4f2b5f28bc6",
                "已在 emulator-5554 上跑起来",
            ),
            mustNotHave = listOf("构建 :app:assembleDebug"),
        ),
        "2026-11-27-r118-shot.log" to BranchLog(
            bytes = 1337,
            lines = 30,
            md5 = "8a12345ec7505e02104ce456fc1e1bcb",
            verdictCount = 8,
            exitCode = "0",
            mustHave = listOf(
                "截图",
                "build/run-shot.png",
                "尺寸 1080x2400",
            ),
            mustNotHave = listOf("构建 :app:assembleDebug"),
        ),
        "2026-11-27-r118-reinstall.log" to BranchLog(
            bytes = 1132,
            lines = 26,
            md5 = "9cc3fcda2cc2d1de20a1af59c94a058a",
            verdictCount = 4,
            exitCode = "0",
            mustHave = listOf(
                "先卸载 com.fortheelder.debug",
                "Success",
                "已在 emulator-5554 上跑起来",
            ),
            mustNotHave = listOf("构建 :app:assembleDebug"),
        ),
        "2026-11-27-r118-logcat.log" to BranchLog(
            bytes = 42781,
            lines = 611,
            md5 = "eb1c47a5957bd7d5a20afabb6dcc763e",
            verdictCount = 4,
            exitCode = "截断",
            mustHave = listOf(
                "跟随 logcat",
                "brief 行数 582",
                "保留 600 行",
            ),
            mustNotHave = listOf("完成：", "构建 :app:assembleDebug"),
        ),
        "2026-11-27-r130-emulator.log" to BranchLog(
            bytes = 4072,
            lines = 86,
            md5 = "520fabd21be62c994dc0b69d0cca008a",
            verdictCount = 17,
            exitCode = "0",
            mustHave = listOf(
                "开模拟器 Elder_API35（日志",
                "设备 emulator-5554",
                "（本来就没装，跳过）",
                "冷启动 AVD Elder_API35",
                "构建 :app:assembleDebug",
            ),
            mustNotHave = listOf("跟随 logcat（Ctrl-C 退出）", "没有已连接设备"),
        ),
        "2026-11-27-r131-avd.log" to BranchLog(
            bytes = 4222,
            lines = 87,
            md5 = "d80fa4f59644a210be912d47d682aa19",
            verdictCount = 18,
            exitCode = "0",
            mustHave = listOf(
                "开模拟器 TripWear_API35（日志",
                "设备 emulator-5554",
                "（本来就没装，跳过）",
                "冷启动 AVD TripWear_API35",
                "构建 :app:assembleDebug",
                "默认 AVD 名 Elder_API35 本轮**未**被用到",
            ),
            mustNotHave = listOf("开模拟器 Elder_API35（日志", "跟随 logcat（Ctrl-C 退出）", "没有已连接设备"),
        ),
        "2026-11-27-r132-avd-env.log" to BranchLog(
            bytes = 4426,
            lines = 88,
            md5 = "5e690f93bda11d3de0eac8c3aaf215f4",
            verdictCount = 19,
            exitCode = "0",
            mustHave = listOf(
                "环境变量入口 AVD=TripWear_API35",
                "开模拟器 TripWear_API35（日志",
                "设备 emulator-5554",
                "先卸载 com.fortheelder.debug",
                "冷启动 AVD TripWear_API35",
                "构建 :app:assembleDebug",
                "默认 AVD 名 Elder_API35 本轮**未**被用到",
                "设备属性 ro.boot.qemu.avd_name TripWear_API35",
            ),
            mustNotHave = listOf("开模拟器 Elder_API35（日志", "跟随 logcat（Ctrl-C 退出）", "没有已连接设备"),
        ),
        "2026-11-27-r133-shot.log" to BranchLog(
            bytes = 4415,
            lines = 88,
            md5 = "0c003e0fd3d5af65b0f9239b585c68d9",
            verdictCount = 20,
            exitCode = "0",
            mustHave = listOf(
                "默认臂 AVD_NAME=\"\${AVD:-Elder_API35}\"",
                "开模拟器 Elder_API35（日志",
                "设备 emulator-5554",
                "先卸载 com.fortheelder.debug",
                "冷启动 AVD Elder_API35",
                "构建 :app:assembleDebug",
                "非默认 AVD 名 TripWear_API35 本轮**未**被用到",
                "设备属性 ro.boot.qemu.avd_name Elder_API35",
            ),
            mustNotHave = listOf("开模拟器 TripWear_API35（日志", "跟随 logcat（Ctrl-C 退出）", "没有已连接设备"),
        ),
        "2026-11-27-r134-avd-vs-flag.log" to BranchLog(
            bytes = 4774,
            lines = 92,
            md5 = "99e6c4bab710696702bc54ad1b4b74eb",
            verdictCount = 23,
            exitCode = "0",
            mustHave = listOf(
                "环境变量入口 AVD=TripWear_API35",
                "命令行参数入口 --avd Elder_API35",
                "两个入口同时给出时 --avd 赢",
                "开模拟器 Elder_API35（日志",
                "设备 emulator-5554",
                "先卸载 com.fortheelder.debug",
                "冷启动 AVD Elder_API35",
                "构建 :app:assembleDebug",
                "设备属性 ro.boot.qemu.avd_name Elder_API35",
                "非默认名 TripWear_API35 本轮**未**被用到",
            ),
            mustNotHave = listOf("开模拟器 TripWear_API35（日志", "跟随 logcat（Ctrl-C 退出）", "没有已连接设备"),
        ),
    )

    /** 一条分支的证据里该有什么、不该有什么。 */
    private data class BranchLog(
        val bytes: Int,
        val lines: Int,
        val md5: String,
        /** 表头 `判定 PASS 全部 N 项` 里的 N。 */
        val verdictCount: Int,
        /** 表头 `退出码` 行的值（`--logcat` 是 `截断`，因为它永不返回）。 */
        val exitCode: String,
        val mustHave: List<String>,
        val mustNotHave: List<String>,
    )

    /** 目录里已登记证据份数下限（现值 9）。 */
    private val branchLogFloor = 9

    /** 判据 3 三类痕迹（须有 + 不许有）的总条数下限（实测 69 条，取值 60）。 */
    private val traceNeedleFloor = 60

    /** `--logcat` 证据里 brief 格式日志行数下限（实测 582 行，取值 100）。 */
    private val briefLineFloor = 100

    /** 表头 `run.sh` 那行的形状：`[branch] run.sh <md5> 字节 <b> 行 <l>`。 */
    private val runShHeader = Regex("""^\[branch] run\.sh ([0-9a-f]{32}) 字节 (\d+) 行 (\d+)$""")

    /** 表头分隔行：它以下是 `run.sh` 的**原样**输出。 */
    private val separator = "---- 以下为 run.sh 原始输出 ----"

    /** adb brief 格式的形状：`<优先级单字母>/<TAG>(<PID>): <msg>`。**没有**日期前缀。 */
    private val briefLine = Regex("""^[VDIWEF]/""")

    /** 第 1 条：九份证据逐字节对账 + 目录双向。 */
    @Test
    fun everyBranchLogMatchesDisk() {
        assertTrue("$evidenceDir 不是目录（分支证据整片没了？）", evidenceDir.isDirectory)
        val actual = evidenceDir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted()
            ?: emptyList()
        assertTrue(
            "分支证据目录里只有 ${actual.size} 份文件，低于下限 $branchLogFloor：" +
                "目录被清空或挪走时，第 2 / 3 / 4 道判据会退化成「零份 ⇒ 一律通过」",
            actual.size >= branchLogFloor,
        )
        assertEquals(
            "分支证据目录与登记表**对不上**。目录里多出来的 = ${actual - registered.keys}；" +
                "登记了却在目录里找不到的 = ${registered.keys - actual.toSet()}。" +
                "新加一份真跑证据时，必须同步更新 [registered]，并想清楚它的痕迹该有哪些",
            registered.keys.sorted(),
            actual,
        )
        val bad = mutableListOf<String>()
        for ((name, reg) in registered) {
            val disk = onDisk(name)
            if (disk == null) {
                bad += "$name 在目录里找不到"
            } else if (disk != Triple(reg.bytes, reg.lines, reg.md5)) {
                val (b, l, m) = disk
                bad += "$name 磁盘 $b 字节 / $l 行 / md5 $m，登记 ${reg.bytes} / ${reg.lines} / ${reg.md5}"
            }
        }
        assertTrue(
            "有 ${bad.size} 份分支证据与登记读数对不上：\n" + bad.sorted().joinToString("\n") { "  $it" } +
                "\n这些证据按 `.gitattributes` 里的 `$attributesRule` 逐字节入库，**不许**在事后编辑：" +
                "改一个字节，「证据」这两个字就不成立了。若确实要换一份新证据，走「重跑探针 + 更新 [registered] + " +
                "更新类注释里的读数」三步，不要就地改旧证据",
            bad.isEmpty(),
        )
    }

    /** 第 2 条：每份证据的表头读数必须等于磁盘上 `run.sh` 的实测值。 */
    @Test
    fun everyBranchLogSelfReportsTheSameRunSh() {
        assertTrue("$runSh 不在（`run.sh` 被挪走或改名了？）", runSh.isFile)
        val shBytes = runSh.length().toInt()
        val shLines = lineCount(runSh)
        val shMd5 = md5Of(runSh)
        val bad = mutableListOf<String>()
        for (name in registered.keys.sorted()) {
            val m = runShHeader.find(headLineOf(name, "run.sh"))
            if (m == null) {
                bad += "$name 的表头里找不到形状为 `[branch] run.sh <md5> 字节 <b> 行 <l>` 的那行"
                continue
            }
            val (md5, bytes, lines) = m.destructured
            if (md5 != shMd5 || bytes.toInt() != shBytes || lines.toInt() != shLines) {
                bad += "$name 表头记的是 $md5 / $bytes 字节 / $lines 行，" +
                    "磁盘上的 `run.sh` 现在是 $shMd5 / $shBytes 字节 / $shLines 行"
            }
        }
        assertTrue(
            "有 ${bad.size} 份分支证据的 `run.sh` 读数与磁盘对不上：\n" +
                bad.sorted().joinToString("\n") { "  $it" } +
                "\n这条判据是**证据跟当前源码对账**：`run.sh` 改过之后，必须重跑探针" +
                "（第 118 轮四份走 `build/r118/branchprobe.py`、第 130 轮那份走 `build/r130/emuprobe.py`、" +
                "第 131 轮那份走 `build/r131/avdprobe.py`、第 132 轮那份走 `build/r132/avdprobe.py`、" +
                "第 133 轮那份走 `build/r133/shotprobe.py`、第 134 轮那份走 `build/r134/avdprobe.py`）" +
                "让这九份证据重新落地，并同步更新 [registered]。旧证据对不上新脚本时，它证明的就不是**现在**的" +
                "`run.sh`——留着它只会误导",
            bad.isEmpty(),
        )
    }

    /** 第 3 条：每份证据里必须有那条分支才有的痕迹，且不许有不该有的字样。 */
    @Test
    fun eachLogCarriesItsOwnBranchTrace() {
        val needles = registered.values.sumOf { it.mustHave.size + it.mustNotHave.size }
        assertTrue(
            "痕迹条数只有 $needles 条，低于下限 $traceNeedleFloor：痕迹表被清空时本判据等于不判",
            needles >= traceNeedleFloor,
        )
        val bad = mutableListOf<String>()
        for (name in registered.keys.sorted()) {
            val reg = registered.getValue(name)
            val text = normalizedOf(name)
            for (needle in reg.mustHave) {
                if (!text.contains(needle)) bad += "$name 里找不到 `$needle`"
            }
            for (needle in reg.mustNotHave) {
                if (text.contains(needle)) bad += "$name 里出现了不该出现的 `$needle`"
            }
            if (name.endsWith("-logcat.log")) {
                val brief = text.split('\n').count { briefLine.containsMatchIn(it) }
                if (brief < briefLineFloor) {
                    bad += "$name 里 brief 格式日志只有 $brief 行，低于下限 $briefLineFloor" +
                        "（`^[VDIWEF]/`；adb brief **没有**日期前缀，别按 `^[0-9][0-9]-` 数）"
                }
            }
        }
        assertTrue(
            "有 ${bad.size} 处分支痕迹对不上：\n" + bad.sorted().joinToString("\n") { "  $it" } +
                "\n每份证据分「表头」（探针自己写的读数与判定）与「正文」（`run.sh` 的原样输出）两段，" +
                "判据吃的是**内容**，不吃行号",
            bad.isEmpty(),
        )
    }

    /** 第 4 条：每份证据的判定行与退出码行必须是那一跑的实测值。 */
    @Test
    fun everyBranchLogRecordsItsVerdict() {
        val bad = mutableListOf<String>()
        for (name in registered.keys.sorted()) {
            val reg = registered.getValue(name)
            val verdict = headLineOf(name, "判定")
            val want = "[branch] 判定 PASS 全部 ${reg.verdictCount} 项"
            if (verdict != want) bad += "$name 的判定行是「$verdict」，登记的是「$want」"
            val exit = headLineOf(name, "退出码")
            val wantExit = "[branch] 退出码 ${reg.exitCode}"
            if (exit != wantExit) bad += "$name 的退出码行是「$exit」，登记的是「$wantExit」"
            if (!normalizedOf(name).contains(separator)) {
                bad += "$name 里找不到表头分隔行 `$separator`——表头与正文的边界没了"
            }
        }
        assertTrue(
            "有 ${bad.size} 处判定对不上：\n" + bad.sorted().joinToString("\n") { "  $it" } +
                "\n`--logcat` 的退出码登记为「截断」是**照实写**：那条分支 `exec` 进 `adb logcat` 后永不返回，" +
                "探针按「前 600 行或前 20 秒，先到为准」收手。这不是 pass，是「这条分支本来就收不了尾」",
            bad.isEmpty(),
        )
    }

    /** 第 5 条：`.gitattributes` 里有逐字节规则，且证据规模在合理量级。 */
    @Test
    fun branchEvidenceIsKeptByteExact() {
        val attrs = File(repoRoot, ".gitattributes")
        assertTrue("$attrs 不在", attrs.isFile)
        val text = attrs.readText().replace("\r\n", "\n")
        assertTrue(
            "`.gitattributes` 里找不到 `$attributesRule`。`--logcat` 那一份里有 583 个 `\\r` 字节（`adb.exe` 在 Windows " +
                "上吐 CRLF），若被 `* text=auto` 归一化成 LF，字节数与 md5 全变、第 1 条判据当场报红——" +
                "这条规则是硬需求",
            text.lineSequence().any { it.trim() == attributesRule },
        )
        val logcat = evidenceDir.listFiles()?.firstOrNull { it.name.endsWith("-logcat.log") }
        assertTrue(
            "分支证据目录里找不到 `*-logcat.log`：那一份是本目录里带 CR 最多的一份（583 个），判据 5 的 583 这个值靠它",
            logcat != null,
        )
        val withCr = logcat!!.readBytes().count { it == '\r'.code.toByte() }
        assertTrue(
            "`--logcat` 证据里的 `\\r` 字节数是 $withCr，不再是登记那时的 583：" +
                "要么 `-text` 规则失效把 CR 归一化掉了，要么换了一份新证据（那就同步更新本条与 [registered]）",
            withCr == 583,
        )
    }

    /** 表头段里以 [prefix] 起头的那一行（去掉行尾 `\r`、去掉探针自己加的行） 。 */
    private fun headLineOf(name: String, prefix: String): String {
        val want = "[branch] $prefix"
        return normalizedOf(name).split('\n')
            .firstOrNull { it.startsWith(want) }
            ?: error("$name 的表头里找不到以「$want」起头的行")
    }

    /** 证据正文按 `\n` 切、逐行去掉行尾 `\r`（`--logcat` 那份带 583 个 CR）。 */
    private fun normalizedOf(name: String): String =
        File(evidenceDir, name).readText().split('\n').joinToString("\n") { it.trimEnd('\r') }

    /** 一份证据在磁盘上的三个读数；文件不在时返回 `null`。 */
    private fun onDisk(name: String): Triple<Int, Int, String>? {
        val file = File(evidenceDir, name)
        if (!file.isFile) return null
        return Triple(file.length().toInt(), lineCount(file), md5Of(file))
    }

    /** 行数口径与 [RunEvidenceGuardTest.lineCount] 逐字相同：数 `\n`，不以 `\n` 结尾再补一个。 */
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
}
