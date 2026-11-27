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
 * - [RunScriptBranchEvidenceGuardTest]（第 118 轮）判**十三份设备分支证据逐字节对账**：目录 ↔ 登记表全集
 *   相等、每份的字节/行/md5、**表头里的 `run.sh` 读数等于磁盘现值**（`everyBranchLogSelfReportsTheSameRunSh`
 *   —— 这一面本条**不重复判**）、判定行与退出码、`.gitattributes` 的 `-text` 规则。
 * - [RunEvidenceGuardTest]（第 113 轮）判 `records/run-evidence/` 里那三份**运行入口**日志（`--help` /
 *   直跑 / `run.bat`），与分支证据是两个目录、两套登记。
 *
 * 第 118 轮自己在类注释第四节里挂的账写着：「**`--emulator` 的开机路径仍然没有判据**：那条路只在
 * 『**没有任何已连接设备**』时才会走到，本机模拟器一直在跑、验不了」「**`--avd` 也没有判据**」。
 * 第 130 轮**先杀掉本机模拟器、凑出「一台设备都没有」这个前置条件**，把 `--emulator` 冷启动真跑了一遍
 * 并留证（`records/branch-evidence/2026-11-27-r130-emulator.log`，4,072 字节 / 86 行），销掉了这一半。
 * 第 131 轮**再凑一次同样的前置条件**（开跑前 `adb devices` 无设备），把 `--avd` 那半也真跑了一遍：
 * 命令行递一个**非默认**的 AVD 名进去，留证
 * `records/branch-evidence/2026-11-27-r131-avd.log`（4,222 字节 / 87 行），证明脚本**确实把这个名字原样
 * 递给了模拟器**、而且**没有退回默认名** —— 这一半也销掉了，`--avd` 随之从「非真动作」挪回真动作（判据 6）。
 * 第 132 轮把**同一条路的另一个入口**补上：那个名字在 `run.sh` 里有两个来源 —— 参数臂 `--avd`
 * （第 131 轮，判据 6）与**环境变量** `AVD=`（脚本第 37 行 `AVD_NAME="${AVD:-Elder_API35}"`）。第 132 轮
 * **不传 `--avd`、只给环境变量**，再杀一次模拟器凑零设备，真跑并留证
 * `records/branch-evidence/2026-11-27-r132-avd-env.log`（4,426 字节 / 88 行 / 判定 19 项），销掉
 * [documentedGaps] 里挂了整轮的「环境变量入口没真跑过」那一笔（判据 8）。这一跑还多带了一样**设备侧的
 * 独立取证**：`adb shell getprop ro.boot.qemu.avd_name` 读回来的是 `TripWear_API35` —— 这个名字是
 * **模拟器自己吐出来的**，不是脚本打印出来的，所以它才顶得上「真的开进了这个 AVD，而不只是打印对了」。
 *
 * 第 133 轮把这条路上**除两个入口之外的那个默认值**也真跑了一遍：命令行**不传** `--avd`、进程环境**不给**
 * `AVD`，走 `AVD_NAME="${AVD:-Elder_API35}"` 的缺省分支，再杀一次模拟器凑零设备，留证
 * `records/branch-evidence/2026-11-27-r133-shot.log`（4,415 字节 / 88 行 / 判定 20 项）。这一跑顺手把盘上的
 * `build/run-shot.png` 重落了一遍（63,252 字节），于是判据 7 里「认的是**哪一份**」那半笔账成了短板 ——
 * 第 133 轮新立判据 9：表头记的**路径**必须逐字等于 `build/run-shot.png`，且认领盘上那张图的必须是**轮次号
 * 最大**的那一份跑了 `--shot` 的证据（`build/` 只留最新一份产物）。
 *
 * ## 二、判据（十三条），以及每一条**独有的那一面**
 *
 * 1. **真动作分支闭集**（[everyRealActionFlagHasEvidence]）：从 `run.sh` 的解析 `case` 块里抠出全部参数臂，
 *    去掉两条**不是真动作**的（下节说明为什么），剩下的每个参数**至少要有一份证据的分支行覆盖它**。
 *    第 118 轮的登记表是**按文件名**钉的，这条是**按参数名**钉的 —— 补的是「新增一个真动作参数却不留
 *    证据」这个缺口，两把尺子互补而不重叠。
 * 2. **各份证据说的是同一份安装包，且等于磁盘现值**（[everyEvidenceHeaderApkMatchesTheBuildOnDisk]）：
 *    表头 `[branch] apk <md5> 字节 <b>` 各行之间必须一致（十三份证据都该指着同一个 APK），且
 *    `app/build/outputs/apk/debug/app-debug.apk` **若在盘上**就必须与它逐位相等。`build/` 被 gitignore，
 *    产物不一定在盘上，所以「不在盘上」时只压「内部一致」，**不做硬断言**。
 * 3. **冷启动那份证据记着冷启动的真事实**（[theEmulatorBranchEvidenceRecordsAColdBoot]）：表头要有
 *    「开跑前 adb devices 无设备」「冷启动 AVD Elder_API35 端口 5554」，正文要有「开模拟器 …（日志 …」
 *    「设备 emulator-5554」「先卸载 …」「Success」「完成：… 已在 emulator-5554 上跑起来」；截图那一行只判
 *    **形状**（逐字对账那一半第 131 轮挪进判据 7，理由见判据 7 那段）。
 * 4. **`run.sh` 里冷启动那一段的**顺序**（[runShBootsTheEmulatorBeforeItWaitsForBoot]）：源码里必须
 *    `开模拟器` < `wait-for-device` < `sys.boot_completed` < `say "安装"` < `完成：`，且 `--emulator` 那一段
 *    里必须真的把模拟器**后台**起起来（`&` + `>"$ELOG" 2>&1` + `echo $! >` 写 pid 文件）再等开机，并且
 *    带着 `-no-snapshot`、`-gpu swiftshader_indirect`、`240` 秒的等机上限。这条钉的是「**先起进程、再等
 *    开机**」这个顺序 —— 顺序反了就成了「等一个根本没起来的设备」，而它**不看证据**，看的是脚本现值。
 * 5. **冷启动证据的规模与形状下限**（[emulatorEvidenceKeepsTheColdStartFactsBounded]）：表头行数 ≥ 10、
 *    字节 ≥ 1,500、`退出码 0`、`判定 PASS 全部 N 项`（N ≥ 8）、正文里有 `BUILD SUCCESSFUL` 与
 *    `up-to-date`（这一跑**带构建**，与第 118 轮那四份都不同；第 131 轮那份与它同形、也带构建，
 *    这是它区别于那四份的形状）、两个产物
 *    路径字面量（`build/run-emulator.log` / `build/run-emulator.pid`）必须在表头里露过面。
 *    **上限不判**：真流有多长不判，照第 118 轮的口径。
 * 6. **命令行递进去的那个 AVD 名原样到了模拟器**（[avdFlagReachesTheEmulatorUnchanged]）：两面对称地判 ——
 *    源码面：`run.sh` 里必须有 `--avd) shift; AVD_NAME="${1:-}"`（参数臂真的把值接进了变量）、
 *    `say "开模拟器 $AVD_NAME（日志 $ELOG）"`（变量真的接到了那句报话上）、`-avd "$AVD_NAME"`（变量真的
 *    递给了 `emulator`）；证据面：新那份表头记着 `冷启动 AVD TripWear_API35`、正文里有
 *    `开模拟器 TripWear_API35（日志`，且**没有** `开模拟器 Elder_API35（日志`（没有悄悄退回默认名）。
 *    「值原样穿过脚本」这件事只看源码会漏掉「到底穿没穿过去」、只看证据会漏掉「它凭什么穿过去了」，
 *    所以两面都要。
 * 7. **盘上那份截图产物必须被某一份证据认领**（[diskShotArtifactIsAttestedBySomeEvidence]）：先按每一跑自己的
 *    `[branch] 命令 …` 行分流 —— **跑了 `--shot` 的那几份必须记一行合形的 `[branch] 截图 …`，没跑的必须不记**
 *    （两向都判）；然后 `build/run-shot.png` **在盘上**时（它被 gitignore，不在盘上就整半跳过），
 *    它的**实测**宽高与字节数必须与**至少一份**已登记证据表头里记的读数逐位相等。
 *    这条是第 131 轮**改出来的**：判据 3 原来自己压着「表头读数 == 盘上 PNG」这一半，而 `build/` 只留
 *    **最新一份**产物 —— 第 131 轮那一跑（`--shot`）把图重落了一遍，24,797 字节换成 19,248 字节，
 *    判据 3 随即报红。红的不是脚本，是**那一半判错了对象**：它盯的是第 130 轮那一次，产物却属于
 *    **最后一次跑**。改成「至少一份认领」之后这条就不再随跑数漂移，而且照样能抓住「盘上放了一张
 *    谁也没记过的图」；**认不出「是哪一次落的」**这一点如实挂在 [documentedGaps] 里。
 *    分流那一半第 131 轮也当场报红过一次：它头一版写的是「**每份**证据的截图行都得合形」，
 *    而第 118 轮那三份（`--no-build` / `--no-build --reinstall` / `--no-build --logcat`）没截过图 ——
 *    又是**判据判宽了**，不是证据缺了，遂改成按 `命令` 行分流、两向都判。
 * 8. **环境变量递进去的那个 AVD 名原样到了模拟器**（[avdEnvVarReachesTheEmulatorUnchanged]）：两面对称地判 ——
 *    源码面：`run.sh` 里必须有 `AVD_NAME="${AVD:-Elder_API35}"`（环境变量真的能从那里取值、缺省才是默认名）、
 *    `say "开模拟器 $AVD_NAME（日志 $ELOG）"`（变量真的接到了那句报话上）、`-avd "$AVD_NAME"`（变量真的
 *    递给了 `emulator`）；证据面：新那份表头记着 `环境变量入口 AVD=TripWear_API35` 与
 *    `冷启动 AVD TripWear_API35`、正文里有 `开模拟器 TripWear_API35（日志`，且**没有**
 *    `开模拟器 Elder_API35（日志`（没有悄悄退回默认名）；另外表头里还必须有
 *    `[branch] 设备属性 ro.boot.qemu.avd_name TripWear_API35` 那一行 —— 那是**模拟器自己**在设备上吐出来的
 *    名字，是判据 6 / 判据 8 里**唯一一处**不靠脚本文本的证据：「脚本打印对了」与「真的开进了这个 AVD」
 *    是两件事，只靠前者顶不上后者。第 132 轮立。
 * 9. **盘上那份截图产物必须被「最新那一份」跑了 `--shot` 的证据认领**（[diskShotArtifactIsClaimedByTheNewestShotRun]）：
 *    判据 7 那一半里收窄两处 —— 表头截图行记的**路径**必须逐字等于 `build/run-shot.png`（判据 7 只判形状，
 *    四个捕获组里的路径那组被丢掉），认领者必须是**轮次号最大**的那一份跑了 `--shot` 的已登记证据
 *    （`build/` 只留最新一份产物，只有最新那一跑才可能落下盘上这张图）。第 133 轮立：此前「认的是哪一份」
 *    判不出来，一份指着**别的文件**的截图行也能顶账，改图不改证据时也判不出来。
 * 10. **两个入口同时给出时，命令行 `--avd` 盖过环境变量 `AVD`**（[avdFlagWinsOverTheAvdEnvVar]）：三面对称地判 ——
 *    源码面：`run.sh` 里 `AVD_NAME="${AVD:-Elder_API35}"`（环境变量入口）必须**排在** `--avd) shift;
 *    AVD_NAME="${1:-}"`（参数臂）**之前** —— 后面那个才盖得住前面那个，次序反了这条就是错的；
 *    证据面：那一跑两个入口给的名字必须**不同**（都叫一个名就证明不了谁赢），表头必须明写
 *    `两个入口同时给出时 --avd 赢`，且设备上现读的 `ro.boot.qemu.avd_name` 必须等于**命令行那个**名字、
 *    **不等于**环境变量那个，正文里报出来的必须是命令行那个名字（不是环境变量那个、也不是默认名）。
 *    判据 6 压的是「只给 `--avd`」、判据 8 压的是「只给 `AVD`」、本条压的是「两个都给」——
 *    三个组合是**三笔不同的账**：前两条各证一个入口自己走得通，本条证的是两者碰头时的**优先级**，
 *    只给一个入口时优先级根本用不上。第 134 轮立，证据在
 *    `records/branch-evidence/2026-11-27-r134-avd-vs-flag.log`。
 * 11. **`--avd` 的两种畸形写法都不会被脚本挡住**（[avdWithoutAValueIsSwallowedSilently]）：源码面 —— `run.sh` 里
 *     `--avd) shift; AVD_NAME="${1:-}"` 必须还在（把值原样接进变量）、这一臂的正文里**不许有** `$#`
 *     （臂内没有任何守卫）、`set -euo pipefail` 必须在场、`AVD_NAME` 必须仍只出现 4 次；证据面 —— 甲
 *     （`--avd` 是命令行最后一个参数、后面不带值）以退出码 1 **静默**收场（表头记 `stdout 0 字节 stderr 0 字节`）、
 *     正文为空（脚本在打印任何东西之前就退了）；乙（`--avd` 后面紧跟着 `--no-build`）照样构建、退出码 0，
 *     表头明写「解析循环正常走完：AVD_NAME=[--no-build] DO_BUILD=1 DO_SHOT=0」—— 被吞掉的正是 `--no-build`。
 *     **这一条判的是「脚本挡不住畸形写法」这个事实本身**，不是判它该不该挡：脚本没有校验、前后两轮也一样过，
 *     本条把这件事钉在盘上、免得将来有人加了校验却以为「本来就有」。第 135 轮立，证据在
 *     `records/branch-evidence/2026-11-27-r135-avd-novalue.log` 与 `…-r135-avd-swallow.log`。
 * 12. **`--log` 的两种畸形写法都不会被脚本挡住**（[logWithoutAValueIsSwallowedSilently]）：源码面与判据 11 同形 ——
 *     `run.sh` 里 `--log) shift; LOG_PATH="${1:-}"` 必须还在（把值原样接进变量）、这一臂的正文里**不许有** `$#`
 *     （臂内没有任何守卫）、`set -euo pipefail` 必须在场、`LOG_PATH` 必须仍只落在 **8 行**上（臂外那 7 行是初始化、
 *     写日志那一小段与 wrapper；那 8 行里带判空的只有一行 `if [ -n "$LOG_PATH" ]`，它是「要不要写日志」的分支、
 *     不是对解析结果的校验，所以空值一路放行）；证据面 —— 甲（`--log` 是命令行最后一个参数、后面不带值）以退出码 1
 *     **静默**收场（表头记 `stdout 0 字节 stderr 0 字节`）、正文为空（脚本在打印任何东西之前就退了）；乙（`--log`
 *     后面紧跟着 `--no-build`）照样构建、退出码 0，表头明写 `LOG_PATH=[--no-build] DO_BUILD=1`，
 *     **并且仓库根被写出一个名叫 `--no-build` 的文件**（3,104 字节 / 69 行 / `11fad13e9c1ebcf50ba3915394f435d3`）——
 *     那是这一跑唯一落到盘上的副作用。盘上那份已由探针在量完读数后删掉（原始副本在 `build/r136/stray--no-build.bin`，
 *     `build/` 已 gitignore、不入库），所以本条只认表头里那行观测，**不读盘上那个文件**：单元测试不许有产物依赖。
 *     **`--log` 是 `run.sh:50~63` 那份解析 `case` 里最后一个带值的臂**（另一个是 `--avd`，判据 11 压着）——
 *     判据 11 与判据 12 合起来，才把「取值开关的值位置」这一类跑穷尽。第 136 轮立，证据在
 *     `records/branch-evidence/2026-11-27-r136-log-novalue.log` 与 `…-r136-log-swallow.log`。
 * 13. **模拟器起不来时脚本会卡死在 `wait-for-device` 上、`240 秒` 那条计时器永远轮不到**（[emulatorBootFailureHangsBeforeTheTimerOverran]）：
 *     源码面读 `run.sh` —— 含 `wait-for-device` 的那一行**不许有** `timeout` 包裹（行内不许出现 `timeout` 这个词）；
 *     字节次序必须成立：`wait-for-device` → `waited=0` → `until [`，且 `240 秒内没开机完成` 必须排在 `waited=0` 之后 ——
 *     也就是那句死等坐在计时器**前面**，计时器因此形同虚设；全脚本含 `timeout` 一词的行数必须是 **0**（脚本不给自己兜底）。
 *     证据面读第 137 轮那份证据 —— 表头明写这跑是 `timeout -k 5 60 bash run.sh --no-build --emulator --avd __no_such_avd__`、
 *     `[branch] 退出码 124`；现场记着「开跑前 adb devices 一台设备都没有」（真零设备）与
 *     `观测 stdout 357 字节 stderr 0 字节`（打到「开模拟器 __no_such_avd__（日志 …）」之后再无一行输出）；
 *     「机内形状」那行把 `run.sh:167` 那一句 `行内无任何 timeout 包裹`、`:168` 才是 `waited=0` 钉在盘上；
 *     对照面 P3 直接跑模拟器本体、同一个坏 AVD 名 **0.1 秒内以 1 退出**并明写 `Unknown AVD name [__no_such_avd__]`，
 *     原语面 P1 零设备下 `timeout 10 adb wait-for-device` **10.1 秒被掐** —— 卡住的不是模拟器，是脚本那句 wait-for-device；
 *     用时 P2 整跑 60.1 秒（全程被外层 timeout 压着，脚本没有自己退出的路径）；掐断后留下一对孤儿（跑 run.sh 的 bash.exe
 *     与它前台死等的 adb.exe），当场收掉。**正文那一面**（分隔行以下）必须含 `开模拟器 __no_such_avd__（日志`，且**不许**含
 *     `模拟器 240 秒内没开机完成` / `已在 emulator-5554 上跑起来` / `构建 :app:assembleDebug` —— 这三件事都该发生却没发生
 *     （注意：`模拟器 240 秒内没开机完成` 这句在**表头**里出现过，那是「没打印」的引语，所以负向断言只对正文判）。
 *     第 130~134 轮四次冷启动没暴露它，是因为那四次模拟器都**开成功了**；第 137 轮故意递一个本机不存在的 AVD 名才把它逼出来。
 *     第 137 轮立，证据在 `records/branch-evidence/2026-11-27-r137-emu-hang.log`（2,895 字节 / 24 行）。
 *
 * ## 三、为什么这两个（原为三个）参数被排除在「真动作」之外（写死，照实交代）
 *
 * - `--log`：它不改脚本做什么，只**包一层**把子进程的输出重定向到文件再把退出码原样透传 —— 这一面已经在
 *   [RunScriptBranchGuardTest.logWrapperCapturesTheFailureAndPropagatesItsExitCode] 里真跑判过了，
 *   不必也不该要求它单独留一份「分支证据」。
 * - `-h | --help`：打印用法即退出，不做任何动作，[RunScriptBranchGuardTest.helpPrintsTheHeaderCommentAndDoesNoWork]
 *   已判。
 *
 * 这里原来还排除了 `--avd`（第 130 轮的理由是「它只改一个修饰量、那个开机路径没验」，并如实挂账）；第 131 轮
 * 把非默认名 `TripWear_API35` 的开机路径真跑了一遍、留证入库（判据 6 兜着），故**从本条起它算真动作**。
 * `--avd` 与 `AVD=` 是**同一条路的两个入口**：入口不同就各自要证，不能互相顶账 —— 第 132 轮给环境变量
 * 那个入口新立了判据 8，证据在 `records/branch-evidence/2026-11-27-r132-avd-env.log`。
 *
 * 这条路上还剩下第三种走法：**两个入口都不给**，让它走 `AVD_NAME="${AVD:-Elder_API35}"` 的缺省分支。第 133 轮
 * 把这一臂也真跑了一遍（命令行不传 `--avd`、进程环境不给 `AVD`、再杀一次模拟器凑零设备），证据是
 * `records/branch-evidence/2026-11-27-r133-shot.log`（4,415 字节 / 88 行 / 判定 20 项）：表头明写默认臂
 * `AVD_NAME="${AVD:-Elder_API35}"`，设备上现读的 `ro.boot.qemu.avd_name` 正是 `Elder_API35`，同时钉着
 * 「非默认 AVD 名 `TripWear_API35` 本轮**未**被用到」。三个走法（参数臂、环境变量臂、缺省臂）至此各有一份证据。
 *
 * 除这两条以外的参数（`--emulator` / `--no-build` / `--reinstall` / `--shot` / `--logcat` / `--avd`）都算真动作。
 * `--no-build` 算真动作的理由：它「跳过构建」本身就是可观察的行为，它的证据必须证明**构建行没出现**
 * （那正是第 118 轮 `mustNotHave` 里钉的 `构建 :app:assembleDebug`）。
 * `--avd` 的判据是第 131 轮新立的判据 6；它的证据就在 `records/branch-evidence/2026-11-27-r131-avd.log`。
 *
 * ## 四、它不保证什么（照实交代）
 *
 * - **不重跑任何脚本、不连任何设备**：unit test 里不许有设备依赖（[RunScriptBranchEvidenceGuardTest] 的底线，
 *   本条照守）。判据 1 / 3 / 5 / 6 / 8 / 9 / 10 / 11 读的是**已经入库的证据**，判据 2 / 4 读的是**当下的源码**
 *   （判据 6、判据 7、判据 8、判据 9、判据 10、判据 11、判据 12、判据 13 两面都读：判据 7 与判据 9 的形状那一半读证据、
 *   认领那一半读盘；判据 10 的证据那一半读证据、次序那一半读源码；判据 11 的证据那一半读证据、源码那一半读脚本；
 *   判据 12 的源码那一半读 `case` 臂与 `LOG_PATH` 落点、证据那一半读两份 `--log` 证据；判据 13 的源码那一半读
 *   「那句死等落在哪一行 + 三条字节次序 + 全脚本 `timeout` 计数」、证据那一半读第 137 轮那份被外层掐断的证据），两者各管一面。
 * - **判据 1 判的是「有没有证据」，不是「证据对不对」**：一份证据落在目录里、分支行里点了某个参数，
 *   这条就算它覆盖了；内容对不对是 [RunScriptBranchEvidenceGuardTest] 判据 1~4 的事。
 * - **判据 6 / 判据 8 / 判据 10 是三个入口/组合各自要证的账**：判据 6 压「只给参数臂 `--avd`」（样本
 *   `TripWear_API35`），判据 8 压「只给环境变量 `AVD=`」（同一个样本），判据 10 压「两个入口同时给」——
 *   前两条各证一个入口自己走得通，第三条证的是两者碰头时的**优先级**，样本刻意**不是一个名字**
 *   （命令行 `--avd Elder_API35`、环境变量 `AVD=TripWear_API35`），否则「谁赢」无从判起。
 *   第 134 轮已把「同时给出时谁赢」真跑坐实：`records/branch-evidence/2026-11-27-r134-avd-vs-flag.log`
 *   （4,774 字节 / 92 行 / 判定 23 项）表头里，设备上现读的 `ro.boot.qemu.avd_name` 是 `Elder_API35`
 *   —— **`--avd` 赢**，且表头明写「非默认名 `TripWear_API35` 本轮**未**被用到」。
 *   源码面上这条优先级的凭据是**次序**：`AVD_NAME="${AVD:-…}"`（第 37 行，环境变量入口）排在
 *   `--avd) shift; AVD_NAME="${1:-}"`（第 58 行，参数臂）**之前**，后面那个才盖得住前面那个。
 * - **判据 10 不保证「次序换了就一定报红」的全覆盖**：它压的是那两处的**相对位置**，谁把参数臂挪到
 *   环境变量之前，这条当场报红（第 134 轮的反向变异真跑过这一档）；但它证不了「两个入口给**同一个**
 *   名字时会怎样」—— 那种输入下「谁赢」看不出来，本条也刻意要求那一跑给不同名，所以没跑过这种样本。
 * - **判据 6 / 判据 8 的「没有退回默认名」是负向断言**：它只证明这一跑的输出里没有出现默认名，不证明脚本在所有
 *   输入下都不退回 —— 真流有多长不判，照第 118 轮的口径。
 * - **冷启动会弹一个可见窗口**：`run.sh` 的冷启动那一段**没有传 `-no-window`**（脚本里就这一处，第 158~175
 *   行），所以它起的模拟器在桌面上是**看得见**的。这是脚本的真行为，本轮照实记，**没有为此改 `run.sh`**：
 *   改它会让十三份证据表头里的 `run.sh` 读数全部过期、十三份证据一起作废，代价远大于收益。
 * - **判据 7 与判据 9 的认领那一半都是软的**：`build/run-shot.png` 被 gitignore，别人机器上多半不在盘上，那时
 *   这两半都自动跳过（只判每份表头里那行读数的形状），所以它们不算硬判据。
 * - **判据 9 认的是「最新那一份认领」，不是「证据里那行读数没被改过」**：它把判据 7 的「至少一份」收窄成
 *   「轮次号最大的那一份跑了 `--shot` 的已登记证据」，同时要求那行截图记的路径逐字等于 `build/run-shot.png`；
 *   但它仍证不了盘上那张图的**大小**有没有被谁改过之后又对上路（[documentedGaps] 里那条）—— 认领靠的是
 *   宽高与字节数三个读数比对，改图改到读数仍旧相等时判据 7 与判据 9 都判不出来。
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

    /** 冷启动那份证据的文件名（第 130 轮真跑，走的是默认 AVD 名）。 */
    private val emulatorLog = "2026-11-27-r130-emulator.log"

    /** 非默认 AVD 名那份证据的文件名（第 131 轮真跑 `--avd`，判据 6 压着它）。 */
    private val avdLog = "2026-11-27-r131-avd.log"

    /** 环境变量入口那份证据的文件名（第 132 轮真跑 `AVD=`，判据 8 压着它）。 */
    private val avdEnvLog = "2026-11-27-r132-avd-env.log"

    /** 两个入口同时给出那份证据的文件名（第 134 轮真跑 `AVD=` + `--avd`，判据 10 压着它）。 */
    private val avdVsFlagLog = "2026-11-27-r134-avd-vs-flag.log"

    /** `--avd` 末尾不带值那份证据的文件名（第 135 轮真跑，判据 11 压着它）。 */
    private val avdNoValueLog = "2026-11-27-r135-avd-novalue.log"

    /** `--avd` 吞掉紧跟开关那份证据的文件名（第 135 轮真跑，判据 11 压着它）。 */
    private val avdSwallowLog = "2026-11-27-r135-avd-swallow.log"

    /** `--log` 末尾不带值那份证据的文件名（第 136 轮真跑，判据 12 压着它）。 */
    private val logNoValueLog = "2026-11-27-r136-log-novalue.log"

    /** `--log` 吞掉紧跟开关那份证据的文件名（第 136 轮真跑，判据 12 压着它）。 */
    private val logSwallowLog = "2026-11-27-r136-log-swallow.log"

    /** 模拟器起不来、脚本卡死在 `wait-for-device` 那份证据的文件名（第 137 轮真跑，判据 13 压着它）。 */
    private val emuHangLog = "2026-11-27-r137-emu-hang.log"

    /** 那一跑递进去的**不存在**的 AVD 名（判据 13 用它点名「卡住的不是模拟器，是脚本那句 wait-for-device」）。 */
    private val badAvdName = "__no_such_avd__"

    /** 那一跑递进去的非默认 AVD 名，以及脚本里写死的默认名（判据 6 / 判据 8 都压着它们）。 */
    private val avdName = "TripWear_API35"
    private val defaultAvdName = "Elder_API35"

    /** 那一跑顺手落下的截图（gitignore 覆盖，可能不在盘上）。 */
    private val shot: File = File(repoRoot, "build" + File.separatorChar + "run-shot.png")

    /** 本条**主动排除**在「真动作」之外的参数，以及排除它的理由（理由写在类注释第三节）。 */
    private val nonAction = linkedMapOf(
        "--log" to "包一层重定向 + 透传退出码，动作在子进程里，已在 RunScriptBranchGuardTest 真跑判过",
        "-h" to "打印用法即退出；`--help` 与它同臂，已在 RunScriptBranchGuardTest 判过",
        "--help" to "同上",
    )

    /** 本条自认仍挂着的缺口（照实登记；判据 1 的下限压着这张表不许被清空）。 */
    private val documentedGaps = listOf(
        "判据 9 认得出盘上那份 `build/run-shot.png` 必须是**最新那一份**跑了 `--shot` 的已登记证据认领的，" +
            "但仍认不出盘上那张图有没有被谁改过之后**又对上路** —— `build/` 只留最新一份产物，" +
            "认领靠的是宽高与字节数三个读数比对，改图改到读数仍旧相等时判据 7 与判据 9 都判不出来",
    )

    /**
     * 真动作参数个数下限（现值 6：`--emulator` / `--no-build` / `--reinstall` / `--shot` / `--logcat` / `--avd`）。
     *
     * 第 130 轮是 5（`--avd` 那时被排在外）；第 131 轮把它收回来，此数随之加一。
     */
    private val realActionFloor = 6

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

    /** 证据文件名里的轮次号（形如 `2026-11-27-r133-shot.log` → `133`），判据 9 拿它挑「最新那一份」。 */
    private val roundOf = Regex("""-r(\d+)-""")

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

    /** 判据 2：十三份证据指着同一个安装包，且（产物在盘上时）等于磁盘现值。 */
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
        assertTrue(
            "有 ${bad.size} 份证据的表头里抠不出 apk 读数：\n${bad.joinToString("\n")}\n" +
                "抠不出时那份证据会被**静默跳过**（它不进 seen），下面「十三份读数一致」那条照样能过 ——" +
                "所以这里必须单独压一次。第 131 轮补：此前 [bad] 只攒不判，是条真缺口",
            bad.isEmpty(),
        )
        val distinct = seen.values.toSet()
        assertTrue(
            "十三份证据表头里的安装包读数**不是同一个**（出现 ${distinct.size} 套）：" +
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
                    "不在盘上时（`build/` 被 gitignore）本条只压「十三份之间一致」，不做硬断言",
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
                    "这三处是这一跑区别于第 118 轮那四份的形状 —— 它必须是**从一台设备都没有**开始、" +
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
        // 截图那一行的**形状**判据（逐字对账那一半不在这里 —— 见下面那段说明）
        val shotLine = head.lineSequence().firstOrNull { it.startsWith("[branch] 截图") }
        assertTrue("$emulatorLog 的表头里没有 `[branch] 截图 …` 那一行", shotLine != null)
        assertTrue(
            "表头里的截图行形状对不上 `[branch] 截图 <path> PNG magic ok 尺寸 <w>x<h> 字节 <n>`：$shotLine",
            shotHeader.find(shotLine!!) != null,
        )
        // 这里**原有一半**是「`build/run-shot.png` 在盘上时，表头记的尺寸/字节必须与它逐位相等」。
        // 第 131 轮那一跑（`--avd` 非默认名 + `--shot`）把 `build/run-shot.png` 重新落了一遍，
        // 这一半随即变红 —— 它不是脚本坏了，是**这一半本身判错了对象**：`build/` 只留**最新一份**
        // 产物，那份截图属于**最后一次跑**，而本方法盯的是第 130 轮那一次。所以从第 131 轮起
        // 这一半挪进判据 7（[diskShotArtifactIsAttestedBySomeEvidence]），只在「盘上那份必须被
        // **某一份**已登记证据认领」这个不随跑数漂移的口径上判。
    }

    /** 判据 7：盘上那份截图产物，必须被某一份已登记证据的表头读数认领（第 131 轮立）。 */
    @Test
    fun diskShotArtifactIsAttestedBySomeEvidence() {
        // 形状那一半：**跑了 `--shot` 的证据必须记一行合形的截图读数，没跑的不许记** —— 两向都判。
        // 判据 7 原来这一半写的是「每份证据的截图行都得合形」，第 131 轮真跑当场报红：
        // 第 118 轮那三份（`--no-build` / `--no-build --reinstall` / `--no-build --logcat`）压根没截过图，
        // 表头里当然没有那一行。**是这一半判宽了，不是证据缺了** —— 所以改成按每一跑自己的
        // `[branch] 命令 …` 行分流（那一行列的是整跑真参数；`[branch] 分支 …` 只点本合同要盖的那个参数，
        // r131 那份的 `分支` 行里就没有 `--shot`，拿它分流会误判）。
        val lines = linkedMapOf<String, String>()
        for (file in evidenceFiles()) {
            val head = headerOf(file)
            val command = head.lineSequence().firstOrNull { it.startsWith("[branch] 命令 ") }
            assertTrue("${file.name} 的表头里没有 `[branch] 命令 …` 那一行，分流判不出来", command != null)
            val ranShot = command!!.contains("--shot")
            val line = head.lineSequence().firstOrNull { it.startsWith("[branch] 截图") }
            if (!ranShot) {
                assertTrue(
                    "${file.name} 的整跑命令（$command）里没有 `--shot`，表头里却记着截图行：$line\n" +
                        "不截图的那几跑记了截图读数，等于把别人的产物算到自己头上",
                    line == null,
                )
                continue
            }
            assertTrue("${file.name} 跑了 `--shot`，表头里却没有 `[branch] 截图 …` 那一行", line != null)
            assertTrue(
                "${file.name} 的截图行形状对不上 `[branch] 截图 <path> PNG magic ok 尺寸 <w>x<h> 字节 <n>`：$line",
                shotHeader.find(line!!) != null,
            )
            lines[file.name] = line
        }
        assertTrue(
            "一份跑了 `--shot` 的证据都没有，认领那一半会对着空表判",
            lines.isNotEmpty(),
        )
        // 认领那一半：产物被 gitignore，不在盘上时这一半自动跳过（软判据）
        if (!shot.isFile) return
        val raw = shot.readBytes()
        val w = intFromBigEndian(raw, 16)
        val h = intFromBigEndian(raw, 20)
        val claimants = lines.filterValues { line ->
            val (_, lw, lh, lb) = shotHeader.find(line)!!.destructured
            lw.toInt() == w && lh.toInt() == h && lb.toInt() == raw.size
        }
        assertTrue(
            "盘上的 ${shot.name} 实测 ${w}x$h / ${raw.size} 字节，而**没有一份**已登记证据的表头认领它：" +
                "各份记的是 ${lines.map { (n, l) -> "$n → " + shotHeader.find(l)!!.destructured.let { (_, a, b, c) -> "${a}x$b/$c" } }}。" +
                "`build/` 只留最新一份产物，所以只认领「属于某一次真跑」，不认领「属于哪一次」——" +
                "盘上放一张谁也没记过的图时本条报红",
            claimants.isNotEmpty(),
        )
    }

    /**
     * 判据 9：盘上那份截图产物，必须被**最新那一份**跑了 `--shot` 的已登记证据认领（第 133 轮立）。
     *
     * 判据 7 那一半里收窄两处：
     *
     * 1. 表头那行记的**路径**必须逐字等于 `build/run-shot.png` —— 判据 7 只判形状，四个捕获组里的路径那组
     *    被丢掉了，一份指着**别的文件**的截图行照样能「认领」盘上这张图。
     * 2. 认领者不能只是「随便一份」，必须是**轮次号最大的那一份**跑了 `--shot` 的证据 —— 只有它才可能落下
     *    盘上这份产物（`build/` 只留最新一份）。判据 7 认的是「至少一份」，于是「是哪一次落的」判不出来。
     */
    @Test
    fun diskShotArtifactIsClaimedByTheNewestShotRun() {
        val canonical = "build/run-shot.png"
        val shotLines = linkedMapOf<String, String>()
        val badPath = mutableListOf<String>()
        for (file in evidenceFiles()) {
            val head = headerOf(file)
            val command = head.lineSequence().firstOrNull { it.startsWith("[branch] 命令 ") }
            assertTrue(
                "${file.name} 的表头里没有 `[branch] 命令 …` 那一行，判据 9 分不出它跑没跑 `--shot`",
                command != null,
            )
            if (!command!!.contains("--shot")) continue
            val line = head.lineSequence().firstOrNull { it.startsWith("[branch] 截图") }
            assertTrue("${file.name} 跑了 `--shot`，表头里却没有 `[branch] 截图 …` 那一行", line != null)
            val m = shotHeader.find(line!!)
            assertTrue(
                "${file.name} 的截图行形状对不上 `[branch] 截图 <path> PNG magic ok 尺寸 <w>x<h> 字节 <n>`：$line",
                m != null,
            )
            val (path, _, _, _) = m!!.destructured
            if (path != canonical) {
                badPath += "${file.name} 的截图行记的路径是「$path」，不是「$canonical」"
                continue
            }
            shotLines[file.name] = line
        }
        assertTrue(
            "有 ${badPath.size} 份跑了 `--shot` 的证据，截图行记的路径不是 `$canonical`：\n" +
                badPath.joinToString("\n") +
                "\n截图产物落的就是这个路径；表头里记成别的文件时，「认领」认的就不是同一个文件",
            badPath.isEmpty(),
        )
        assertTrue(
            "一份「跑了 `--shot` 且截图行路径合形」的证据都没有，判据 9 的认领那一半会对着空表判",
            shotLines.isNotEmpty(),
        )
        val rounds = shotLines.keys.associateWith { name ->
            val m = roundOf.find(name)
            assertTrue("$name 的文件名里解析不出轮次号（形如 `-r<数字>-`）", m != null)
            m!!.groupValues[1].toInt()
        }
        val newest = rounds.maxByOrNull { it.value }!!
        // 产物被 gitignore，不在盘上时认领那一半自动跳过（软判据，与判据 7 同一口径）
        if (!shot.isFile) return
        val raw = shot.readBytes()
        val w = intFromBigEndian(raw, 16)
        val h = intFromBigEndian(raw, 20)
        val claimants = shotLines.filterValues { line ->
            val (_, lw, lh, lb) = shotHeader.find(line)!!.destructured
            lw.toInt() == w && lh.toInt() == h && lb.toInt() == raw.size
        }
        assertTrue(
            "盘上的 ${shot.name} 实测 ${w}x$h / ${raw.size} 字节；认领它的是 ${claimants.keys.sorted()}，" +
                "而最新那一份跑了 `--shot` 的证据是「${newest.key}」（第 ${newest.value} 轮）—— 它不在认领者里。" +
                "`build/` 只留最新一份产物，所以盘上这张图要么是更早某一跑落下的、要么被谁换过了；" +
                "「属于最新那一跑」这个口径与跑数无关，改图不改证据时本条报红",
            newest.key in claimants.keys,
        )
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

    /** 判据 6：命令行递进去的那个 AVD 名，原样到了模拟器（第 131 轮立）。 */
    @Test
    fun avdFlagReachesTheEmulatorUnchanged() {
        // 源码面：值怎么从命令行进到变量、又怎么递出去
        val src = runSh.readText().replace("\r\n", "\n")
        for ((needle, why) in listOf(
            "--avd) shift; AVD_NAME=\"\${1:-}\"" to "参数臂把命令行那个值接进了变量",
            "say \"开模拟器 \$AVD_NAME（日志 \$ELOG）\"" to "变量接到了那句报话上",
            "-avd \"\$AVD_NAME\"" to "变量递给了 emulator",
        )) {
            assertTrue(
                "`run.sh` 里找不到 `$needle`（$why）：判据 6 的源码面就靠这三处串成一条链 —— " +
                    "缺一环，值就可能在半路被丢掉、或者干脆换回默认名，而证据面又只看得见终点",
                src.contains(needle),
            )
        }

        // 证据面：命令行的那个名字有没有原样落到模拟器上
        val file = File(evidenceDir, avdLog)
        assertTrue(
            "$avdLog 不在 $evidenceDir 里。这一份是第 131 轮真跑 `--avd` 传非默认 AVD 名留下的证据，" +
                "它被删掉时本条报红（第 118 轮那把的目录全集相等判据也会一起报）",
            file.isFile,
        )
        val head = headerOf(file)
        val body = normalizedOf(file).substringAfter(separator)
        assertTrue(
            "$avdLog 的表头里找不到 `冷启动 AVD $avdName`：\n$head\n" +
                "命令行递的是非默认名 `$avdName`，表头就该记这个名字，而不是默认名 `$defaultAvdName`",
            head.contains("冷启动 AVD $avdName"),
        )
        assertTrue(
            "$avdLog 的正文里找不到 `开模拟器 $avdName（日志` —— 脚本没把这个名字报出来，" +
                "就说明它半路被换掉了",
            body.contains("开模拟器 $avdName（日志"),
        )
        assertTrue(
            "$avdLog 的正文里出现了 `开模拟器 $defaultAvdName（日志` —— 命令行明明递了 `$avdName`，" +
                "脚本却退回默认名了（这是负向断言：只证明这一跑没退回，不证明所有输入下都不退回）",
            !body.contains("开模拟器 $defaultAvdName（日志"),
        )
    }

    /** 判据 8：环境变量里递进去的那个 AVD 名，原样到了模拟器（第 132 轮立）。 */
    @Test
    fun avdEnvVarReachesTheEmulatorUnchanged() {
        // 源码面：值怎么从环境里进到变量、又怎么递出去
        val src = runSh.readText().replace("\r\n", "\n")
        for ((needle, why) in listOf(
            "AVD_NAME=\"\${AVD:-$defaultAvdName}\"" to "变量允许从环境里取值，缺省才是默认名",
            "say \"开模拟器 \$AVD_NAME（日志 \$ELOG）\"" to "变量接到了那句报话上",
            "-avd \"\$AVD_NAME\"" to "变量递给了 emulator",
        )) {
            assertTrue(
                "`run.sh` 里找不到 `$needle`（$why）：判据 8 的源码面就靠这三处串成一条链 —— " +
                    "缺一环，环境变量里的那个值就可能在半路被丢掉、或者干脆换回默认名，而证据面又只看得见终点。" +
                    "注意第一处是**环境变量入口**（`\${AVD:-…}`），判据 6 压的是参数臂 `--avd` —— 两个入口，各自要证",
                src.contains(needle),
            )
        }

        // 证据面：环境变量里的那个名字有没有原样落到模拟器上
        val file = File(evidenceDir, avdEnvLog)
        assertTrue(
            "$avdEnvLog 不在 $evidenceDir 里。这一份是第 132 轮真跑 `AVD=` 传非默认 AVD 名留下的证据，" +
                "它被删掉时本条报红（第 118 轮那把的目录全集相等判据也会一起报）",
            file.isFile,
        )
        val head = headerOf(file)
        val body = normalizedOf(file).substringAfter(separator)
        assertTrue(
            "$avdEnvLog 的表头里找不到 `环境变量入口 AVD=$avdName`：\n$head\n" +
                "这一跑命令行**没有**传 `--avd`，名字只可能从环境变量里来 —— 表头必须把这件事写死，" +
                "否则本条与判据 6 就分不清是哪个入口的账",
            head.contains("环境变量入口 AVD=$avdName"),
        )
        assertTrue(
            "$avdEnvLog 的表头里找不到 `冷启动 AVD $avdName`：\n$head\n" +
                "环境变量递的是非默认名 `$avdName`，表头就该记这个名字，而不是默认名 `$defaultAvdName`",
            head.contains("冷启动 AVD $avdName"),
        )
        assertTrue(
            "$avdEnvLog 的正文里找不到 `开模拟器 $avdName（日志` —— 脚本没把这个名字报出来，就说明它半路被换掉了",
            body.contains("开模拟器 $avdName（日志"),
        )
        assertTrue(
            "$avdEnvLog 的正文里出现了 `开模拟器 $defaultAvdName（日志` —— 环境变量明明给了 `$avdName`，" +
                "脚本却退回默认名了（这是负向断言：只证明这一跑没退回，不证明所有输入下都不退回）",
            !body.contains("开模拟器 $defaultAvdName（日志"),
        )
        // 设备侧的独立取证：这个名字是**模拟器自己**吐出来的，不是脚本打印出来的。
        val deviceName = head.lineSequence()
            .firstOrNull { it.startsWith("[branch] 设备属性 ro.boot.qemu.avd_name ") }
            ?.removePrefix("[branch] 设备属性 ro.boot.qemu.avd_name ")
            ?.substringBefore('（')
            ?.trim()
        assertTrue(
            "$avdEnvLog 的表头里没有 `[branch] 设备属性 ro.boot.qemu.avd_name …` 那一行：\n$head\n" +
                "第 132 轮那一跑在设备上 `getprop ro.boot.qemu.avd_name` 现读了一次，把模拟器自己认的名字" +
                "记进了表头 —— 少了这一行，「开的是哪个 AVD」就只剩脚本自己那句话可作凭据",
            deviceName != null,
        )
        assertTrue(
            "设备侧读回来的 AVD 名是「$deviceName」，而这一跑递进去的是「$avdName」：\n" +
                "这一条是判据 6 / 判据 8 里**唯一一处**不靠脚本文本的证据 —— 它证明模拟器真的开的是那个 AVD",
            deviceName == avdName,
        )
    }

    /** 判据 10：两个入口同时给出时，命令行 `--avd` 盖过环境变量 `AVD`（第 134 轮立）。 */
    @Test
    fun avdFlagWinsOverTheAvdEnvVar() {
        // 源码面：两个入口在脚本里的**先后次序**决定谁盖谁（排在后面那个赢）
        val src = runSh.readText().replace("\r\n", "\n")
        val envAt = src.indexOf("AVD_NAME=\"\${AVD:-$defaultAvdName}\"")
        val flagAt = src.indexOf("--avd) shift; AVD_NAME=\"\${1:-}\"")
        assertTrue(
            "`run.sh` 里抠不到环境变量入口 `AVD_NAME=\"\${AVD:-$defaultAvdName}\"`（在字节 $envAt 处）或参数臂" +
                " `--avd) shift; AVD_NAME=\"\${1:-}\"`（在字节 $flagAt 处）：判据 10 的源码面就靠这两处的" +
                "**相对位置**判谁盖谁，抠不到时下面那条次序断言会退化成一句空话",
            envAt >= 0 && flagAt >= 0,
        )
        assertTrue(
            "源码里环境变量入口在第 $envAt 字节处、参数臂在第 $flagAt 字节处 —— 参数臂没有排在环境变量入口" +
                "**之后**，于是命令行那个值会被环境变量盖掉，判据 10 要证的那个优先级就是反的",
            envAt < flagAt,
        )

        // 证据面：两个入口一起给时，落到设备上的是命令行那个名字
        val file = File(evidenceDir, avdVsFlagLog)
        assertTrue(
            "$avdVsFlagLog 不在 $evidenceDir 里。这一份是第 134 轮真跑「`AVD=` 与 `--avd` 同时给出」留下的证据，" +
                "它被删掉时本条报红（第 118 轮那把的目录全集相等判据也会一起报）",
            file.isFile,
        )
        val head = headerOf(file)
        val body = normalizedOf(file).substringAfter(separator)
        val envAvd = head.lineSequence()
            .firstOrNull { it.startsWith("[branch] 环境变量入口 AVD=") }
            ?.removePrefix("[branch] 环境变量入口 AVD=")
            ?.trim()
        val flagAvd = head.lineSequence()
            .firstOrNull { it.startsWith("[branch] 命令行参数入口 --avd ") }
            ?.removePrefix("[branch] 命令行参数入口 --avd ")
            ?.trim()
        assertTrue(
            "$avdVsFlagLog 的表头里抠不出两个入口各自给出的名字（环境变量入口 = $envAvd，命令行参数入口 =" +
                " $flagAvd）：\n$head\n这一跑两个入口各给了什么名字是判据 10 全部断言的锚点，抠不到就没法判「谁赢」",
            envAvd != null && flagAvd != null,
        )
        assertTrue(
            "两个入口给的是同一个名字（环境变量 `AVD=$envAvd`、命令行 `--avd $flagAvd`）：这一跑就证明不了" +
                "「谁赢」—— 两个值一样时，落到设备上的名字无论来自哪个入口都长这样。本条刻意要求它们不同名",
            envAvd != flagAvd,
        )
        assertTrue(
            "$avdVsFlagLog 的表头里找不到 `两个入口同时给出时 --avd 赢`：\n$head\n" +
                "那一跑的探针把结论写死在表头里；少了这一行，本条与判据 6 / 判据 8 就分不清是哪个组合的账",
            head.contains("两个入口同时给出时 --avd 赢"),
        )
        assertTrue(
            "$avdVsFlagLog 的表头里找不到 `非默认名 $envAvd 本轮**未**被用到`：\n$head\n" +
                "环境变量给的那个名字本该被命令行那个盖掉，表头必须把「它没被用到」这件事写死",
            head.contains("非默认名 $envAvd 本轮**未**被用到"),
        )
        // 设备侧的独立取证：这个名字是**模拟器自己**吐出来的，不是脚本打印出来的（读法照判据 8）。
        val deviceName = head.lineSequence()
            .firstOrNull { it.startsWith("[branch] 设备属性 ro.boot.qemu.avd_name ") }
            ?.removePrefix("[branch] 设备属性 ro.boot.qemu.avd_name ")
            ?.substringBefore('（')
            ?.trim()
        assertTrue(
            "$avdVsFlagLog 的表头里没有 `[branch] 设备属性 ro.boot.qemu.avd_name …` 那一行：\n$head\n" +
                "「谁赢」这件事只有一个不靠脚本文本的凭据 —— 模拟器自己在设备上认的那个名字",
            deviceName != null,
        )
        assertTrue(
            "设备上现读的 AVD 名是「$deviceName」，而命令行递的是「$flagAvd」、环境变量给的是「$envAvd」：\n" +
                "两个入口同时给出时，落到设备上的必须是**命令行**那个（`--avd` 排在环境变量入口之后，盖得住它）",
            deviceName == flagAvd && deviceName != envAvd,
        )
        assertTrue(
            "$avdVsFlagLog 的正文里找不到 `开模拟器 $flagAvd（日志` —— 脚本没把命令行那个名字报出来，" +
                "就说明它半路被环境变量顶掉了",
            body.contains("开模拟器 $flagAvd（日志"),
        )
        assertTrue(
            "$avdVsFlagLog 的正文里出现了 `开模拟器 $envAvd（日志` —— 环境变量那个名字明明该被命令行盖掉，" +
                "脚本却用了它（这是负向断言：只证明这一跑没被顶掉，不证明所有输入下都不被顶掉）",
            !body.contains("开模拟器 $envAvd（日志"),
        )
        val arm = branchHeader.find(head)
        assertTrue(
            "$avdVsFlagLog 的分支行（`[branch] 分支 …`）里没点 `--avd`：\n$head\n" +
                "这一跑命令行确实传了 `--avd`；分支行漏点它，判据 1 的闭集覆盖就会对不上",
            arm != null && arm.groupValues[1].contains("--avd"),
        )
    }

    /**
     * 判据 11：`--avd` 的两种畸形写法都不会被脚本挡住（第 135 轮立）。
     *
     * 源码面：参数臂照旧把值接进变量、臂内没有任何守卫；`set -euo pipefail` 在场；`AVD_NAME` 只出现 4 次。
     * 证据面：甲（`--avd` 末尾不带值）以退出码 1 **静默**收场、正文为空；乙（`--avd` 吞掉紧跟的 `--no-build`）
     * 照样构建、退出码 0。
     */
    @Test
    fun avdWithoutAValueIsSwallowedSilently() {
        // 源码面
        val src = runSh.readText().replace("\r\n", "\n")
        val arm = "--avd) shift; AVD_NAME=\"\${1:-}\""
        val armAt = src.indexOf(arm)
        assertTrue(
            "`run.sh` 里找不到 `$arm`：判据 11 的源码面就靠这一臂 —— 它把值原样接进变量、**不做任何校验**",
            armAt >= 0,
        )
        val armText = src.substring(armAt + arm.length).substringBefore(";;")
        assertTrue(
            "`--avd` 这一臂的正文是 `$armText` —— 里面出现了 `\$#`（或别的守卫）：畸形值本来会被静默吞掉，" +
                "一旦有人在这里加校验，本条就该重写（那说明脚本真的挡住了畸形写法）",
            !armText.contains("\$#"),
        )
        assertTrue(
            "`run.sh` 里找不到 `set -euo pipefail`：正是它把循环尾 shift 在 \$#=0 上返回的 1 变成了脚本整体的退出码 1",
            src.contains("set -euo pipefail"),
        )
        val avdNameHits = Regex("AVD_NAME").findAll(src).count()
        assertTrue(
            "`AVD_NAME` 现在出现 $avdNameHits 次（登记值 4）：变化说明参数臂/报话/传参那条链被人动过，得重新对账",
            avdNameHits == 4,
        )

        // 证据面 甲：--avd 末尾不带值 → 退出码 1、正文为空
        val noValue = File(evidenceDir, avdNoValueLog)
        assertTrue(
            "$avdNoValueLog 不在 $evidenceDir 里。这一份是第 135 轮真跑「`--avd` 是命令行最后一个参数」留下的证据，" +
                "它被删掉时本条报红",
            noValue.isFile,
        )
        val nvHead = headerOf(noValue)
        val nvBody = normalizedOf(noValue).substringAfter(separator)
        assertTrue(
            "$avdNoValueLog 的表头里找不到 `畸形写法 --avd 是命令行最后一个参数、后面不带值`：\n$nvHead",
            nvHead.contains("畸形写法 --avd 是命令行最后一个参数、后面不带值"),
        )
        assertTrue(
            "$avdNoValueLog 的表头里找不到 `[branch] 退出码 1`：\n$nvHead\n" +
                "空值打头会让循环尾的 shift 在 \$#=0 上返回 1，脚本该以 1 退出",
            nvHead.contains("[branch] 退出码 1"),
        )
        assertTrue(
            "$avdNoValueLog 的表头里找不到 `观测 stdout 0 字节 stderr 0 字节`：\n$nvHead\n" +
                "这一跑该是**一声不响**地失败",
            nvHead.contains("观测 stdout 0 字节 stderr 0 字节"),
        )
        assertTrue(
            "$avdNoValueLog 的正文不是空的（分隔行以下有内容）：\n$nvBody\n" +
                "脚本在打印任何东西之前就退出了，正文该为空",
            nvBody.trim().isEmpty(),
        )
        val nvArm = branchHeader.find(nvHead)
        assertTrue(
            "$avdNoValueLog 的分支行里没点 `--avd`：\n$nvHead",
            nvArm != null && nvArm.groupValues[1].contains("--avd"),
        )
        assertTrue(
            "$avdNoValueLog 的正文里出现了 `构建 :app:assembleDebug`：脚本没跑到构建就退出了（负向断言）",
            !nvBody.contains("构建 :app:assembleDebug"),
        )
        assertTrue(
            "$avdNoValueLog 的表头里出现了 `已在 emulator-5554 上跑起来`：脚本没跑到启动就退出了（负向断言）",
            !nvHead.contains("已在 emulator-5554 上跑起来"),
        )

        // 证据面 乙：--avd 吞掉紧跟的 --no-build → 照样构建、退出码 0
        val swallow = File(evidenceDir, avdSwallowLog)
        assertTrue(
            "$avdSwallowLog 不在 $evidenceDir 里。这一份是第 135 轮真跑「`--avd --no-build`」留下的证据，" +
                "它被删掉时本条报红",
            swallow.isFile,
        )
        val swHead = headerOf(swallow)
        val swBody = normalizedOf(swallow).substringAfter(separator)
        assertTrue(
            "$avdSwallowLog 的表头里找不到 `畸形写法 --avd 后面紧跟着另一个开关 --no-build`：\n$swHead",
            swHead.contains("畸形写法 --avd 后面紧跟着另一个开关 --no-build"),
        )
        assertTrue(
            "$avdSwallowLog 的表头里找不到 `解析循环正常走完：AVD_NAME=[--no-build] DO_BUILD=1 DO_SHOT=0`：\n" +
                "$swHead\n这就是「吞掉」的机内形状 —— 值位置被开关字面量占了",
            swHead.contains("解析循环正常走完：AVD_NAME=[--no-build] DO_BUILD=1 DO_SHOT=0"),
        )
        assertTrue(
            "$avdSwallowLog 的表头里找不到 `[branch] 退出码 0`：\n$swHead",
            swHead.contains("[branch] 退出码 0"),
        )
        assertTrue(
            "$avdSwallowLog 的正文里找不到 `构建 :app:assembleDebug`：被吞掉的 `--no-build` 本该让构建行消失，" +
                "构建行还在正说明它没生效",
            swBody.contains("构建 :app:assembleDebug"),
        )
        assertTrue(
            "$avdSwallowLog 的正文里找不到 `已在 emulator-5554 上跑起来`：这一跑整段跑完了",
            swBody.contains("已在 emulator-5554 上跑起来"),
        )
        val swArm = branchHeader.find(swHead)
        assertTrue(
            "$avdSwallowLog 的分支行里没点 `--avd`：\n$swHead",
            swArm != null && swArm.groupValues[1].contains("--avd"),
        )
    }

    /**
     * 判据 12：`--log` 的两种畸形写法都不会被脚本挡住（第 136 轮立）。
     *
     * 源码面：参数臂照旧把值接进变量、臂内没有任何守卫；`set -euo pipefail` 在场；`LOG_PATH` 只落在 8 行上。
     * 证据面：甲（`--log` 末尾不带值）以退出码 1 **静默**收场、正文为空；乙（`--log` 吞掉紧跟的 `--no-build`）
     * 照样构建、退出码 0，**并且在仓库根写出一个名叫 `--no-build` 的文件**（那一跑唯一的落盘副作用）。
     *
     * `--log` 是 `run.sh:50~63` 那份解析 `case` 里**最后一个带值的臂**（另一个是 `--avd`，判据 11 压着）：
     * 跑掉这一种之后，「取值开关的值位置」这一类才算穷尽。
     */
    @Test
    fun logWithoutAValueIsSwallowedSilently() {
        // 源码面
        val src = runSh.readText().replace("\r\n", "\n")
        val arm = "--log) shift; LOG_PATH=\"\${1:-}\""
        val armAt = src.indexOf(arm)
        assertTrue(
            "`run.sh` 里找不到 `$arm`：判据 12 的源码面就靠这一臂 —— 它把值原样接进变量、**不做任何校验**",
            armAt >= 0,
        )
        val armText = src.substring(armAt + arm.length).substringBefore(";;")
        assertTrue(
            "`--log` 这一臂的正文是 `$armText` —— 里面出现了 `\$#`（或别的守卫）：畸形值本来会被静默吞掉，" +
                "一旦有人在这里加校验，本条就该重写（那说明脚本真的挡住了畸形写法）",
            !armText.contains("\$#"),
        )
        assertTrue(
            "`run.sh` 里找不到 `set -euo pipefail`：正是它把循环尾 shift 在 \$#=0 上返回的 1 变成了脚本整体的退出码 1",
            src.contains("set -euo pipefail"),
        )
        val logPathHits = src.split('\n').count { it.contains("LOG_PATH") }
        assertTrue(
            "`LOG_PATH` 现在落在 $logPathHits 行上（登记值 8）：变化说明参数臂 / 写日志那一小段 / wrapper " +
                "被人动过，得重新对账 —— 那 8 行里带判空的只有一行，空值因此一路放行",
            logPathHits == 8,
        )

        // 证据面 甲：--log 末尾不带值 → 退出码 1、正文为空
        val noValue = File(evidenceDir, logNoValueLog)
        assertTrue(
            "$logNoValueLog 不在 $evidenceDir 里。这一份是第 136 轮真跑「`--log` 是命令行最后一个参数」留下的证据，" +
                "它被删掉时本条报红",
            noValue.isFile,
        )
        val nvHead = headerOf(noValue)
        val nvBody = normalizedOf(noValue).substringAfter(separator)
        assertTrue(
            "$logNoValueLog 的表头里找不到 `畸形写法 --log 是命令行最后一个参数、后面不带值`：\n$nvHead",
            nvHead.contains("畸形写法 --log 是命令行最后一个参数、后面不带值"),
        )
        assertTrue(
            "$logNoValueLog 的表头里找不到 `[branch] 退出码 1`：\n$nvHead\n" +
                "空值打头会让循环尾的 shift 在 \$#=0 上返回 1，脚本该以 1 退出",
            nvHead.contains("[branch] 退出码 1"),
        )
        assertTrue(
            "$logNoValueLog 的表头里找不到 `观测 stdout 0 字节 stderr 0 字节`：\n$nvHead\n" +
                "这一跑该是**一声不响**地失败",
            nvHead.contains("观测 stdout 0 字节 stderr 0 字节"),
        )
        assertTrue(
            "$logNoValueLog 的正文不是空的（分隔行以下有内容）：\n$nvBody\n" +
                "脚本在打印任何东西之前就退出了，正文该为空",
            nvBody.trim().isEmpty(),
        )
        val nvArm = branchHeader.find(nvHead)
        assertTrue(
            "$logNoValueLog 的分支行里没点 `--log`：\n$nvHead",
            nvArm != null && nvArm.groupValues[1].contains("--log"),
        )
        assertTrue(
            "$logNoValueLog 的正文里出现了 `构建 :app:assembleDebug`：脚本没跑到构建就退出了（负向断言）",
            !nvBody.contains("构建 :app:assembleDebug"),
        )
        assertTrue(
            "$logNoValueLog 的表头里出现了 `已在 emulator-5554 上跑起来`：脚本没跑到启动就退出了（负向断言）",
            !nvHead.contains("已在 emulator-5554 上跑起来"),
        )

        // 证据面 乙：--log 吞掉紧跟的 --no-build → 照样构建、退出码 0，且在仓库根写出一个名叫 --no-build 的文件
        val swallow = File(evidenceDir, logSwallowLog)
        assertTrue(
            "$logSwallowLog 不在 $evidenceDir 里。这一份是第 136 轮真跑「`--log --no-build`」留下的证据，" +
                "它被删掉时本条报红",
            swallow.isFile,
        )
        val swHead = headerOf(swallow)
        val swBody = normalizedOf(swallow).substringAfter(separator)
        assertTrue(
            "$logSwallowLog 的表头里找不到 `畸形写法 --log 后面紧跟着另一个开关 --no-build，值位置被它占掉`：\n$swHead",
            swHead.contains("畸形写法 --log 后面紧跟着另一个开关 --no-build，值位置被它占掉"),
        )
        assertTrue(
            "$logSwallowLog 的表头里找不到 `LOG_PATH=[--no-build] DO_BUILD=1`：\n$swHead\n" +
                "这就是「吞掉」的机内形状 —— 值位置被开关字面量占了，`--no-build` 那一臂再也轮不到",
            swHead.contains("LOG_PATH=[--no-build] DO_BUILD=1"),
        )
        assertTrue(
            "$logSwallowLog 的表头里找不到 `[branch] 退出码 0`：\n$swHead",
            swHead.contains("[branch] 退出码 0"),
        )
        assertTrue(
            "$logSwallowLog 的正文里找不到 `构建 :app:assembleDebug`：被吞掉的 `--no-build` 本该让构建行消失，" +
                "构建行还在正说明它没生效",
            swBody.contains("构建 :app:assembleDebug"),
        )
        assertTrue(
            "$logSwallowLog 的正文里找不到 `已在 emulator-5554 上跑起来`：这一跑整段跑完了",
            swBody.contains("已在 emulator-5554 上跑起来"),
        )
        val swArm = branchHeader.find(swHead)
        assertTrue(
            "$logSwallowLog 的分支行里没点 `--log`：\n$swHead",
            swArm != null && swArm.groupValues[1].contains("--log"),
        )
        assertTrue(
            "$logSwallowLog 的表头里找不到那行落盘副作用读数 " +
                "`副作用 仓库根被写出一个名叫 --no-build 的文件：3104 字节 / 69 行 / " +
                "11fad13e9c1ebcf50ba3915394f435d3`：\n$swHead\n" +
                "这一行是第 136 轮独有的一面 —— 它把「相对路径被解析成 \$ROOT/--no-build」这件事钉在盘上；" +
                "盘上那个文件已由探针在量完读数后删掉（原始副本在 build/r136/stray--no-build.bin、不入库），" +
                "所以本条只认表头这行观测、不去读盘上那个文件",
            swHead.contains(
                "副作用 仓库根被写出一个名叫 --no-build 的文件：" +
                    "3104 字节 / 69 行 / 11fad13e9c1ebcf50ba3915394f435d3",
            ),
        )
    }

    /**
     * 判据 13：模拟器起不来时，脚本会卡死在 `wait-for-device` 上、`240 秒` 那条计时器永远轮不到（第 137 轮立）。
     *
     * 源码面：含 `wait-for-device` 的那一行里没有 `timeout`；`wait-for-device` → `waited=0` → `until [` 的字节次序成立，
     * 且 `240 秒内没开机完成` 排在 `waited=0` 之后（死等排在计时器前面，计时器因此形同虚设）；全脚本 `timeout` 一词出现 0 次。
     * 证据面：第 137 轮递一个本机不存在的 AVD 名真跑，外层用 `timeout -k 5 60` 才把它掐下来（退出码 124、整跑 60.1 秒），
     * 输出停在「开模拟器 __no_such_avd__」那一句之后；对照面（直接跑模拟器本体 0.1 秒退出、明写不认识这个 AVD 名）与
     * 原语面（零设备下 `timeout 10 adb wait-for-device` 10.1 秒被掐）合起来点名：卡的不是模拟器，是脚本那句 wait-for-device。
     *
     * 本条**不重跑脚本、不连设备**：源码面读当下的 `run.sh`，证据面读已入库的那一份。
     */
    @Test
    fun emulatorBootFailureHangsBeforeTheTimerOverran() {
        // 源码面
        val src = runSh.readText().replace("\r\n", "\n")

        val waitLine = src.split('\n').firstOrNull { it.contains("wait-for-device") }
        assertTrue(
            "`run.sh` 里找不到含 `wait-for-device` 的行：判据 13 的源码面就靠这一句 —— 它就是那句死等",
            waitLine != null,
        )
        assertTrue(
            "含 `wait-for-device` 的那一行是 `$waitLine` —— 里面出现了 `timeout`：一旦有人给它套上超时，" +
                "本条就该重写（那说明脚本真的会自己退出、不再死等）",
            waitLine == null || !waitLine.contains("timeout"),
        )

        val waitAt = src.indexOf("wait-for-device")
        val waitedAt = src.indexOf("waited=0")
        val untilAt = src.indexOf("until [")
        val dieAt = src.indexOf("240 秒内没开机完成")
        assertTrue(
            "字节座标次序不对：wait-for-device=$waitAt、waited=0=$waitedAt、until [=$untilAt —— " +
                "必须是 wait-for-device → waited=0 → until [（那句死等排在计时器之前）",
            waitAt in 0 until waitedAt && waitedAt < untilAt,
        )
        assertTrue(
            "字节座标次序不对：waited=0=$waitedAt、240 秒内没开机完成=$dieAt —— " +
                "那条 240 秒的 `die` 必须排在计时器初始化之后；现值说明它被挪到了死等前面（那反而是修好了）",
            waitedAt in 0 until dieAt,
        )

        val timeoutLines = src.split('\n').count { it.contains("timeout") }
        assertTrue(
            "`run.sh` 里含 `timeout` 一词的行数是 $timeoutLines（登记值 0）：脚本自己不给自己兜底 —— " +
                "一旦有人给那句死等套上超时，这个数字就会变，本条报红提示重写",
            timeoutLines == 0,
        )

        // 证据面
        val hang = File(evidenceDir, emuHangLog)
        assertTrue(
            "$emuHangLog 不在 $evidenceDir 里。这一份是第 137 轮真跑「递一个本机不存在的 AVD 名、用外层 timeout 掐断」" +
                "留下的证据，它被删掉时本条报红",
            hang.isFile,
        )
        val head = headerOf(hang)
        val body = normalizedOf(hang).substringAfter(separator)

        assertTrue(
            "$emuHangLog 的表头里找不到 `[branch] 命令 timeout -k 5 60 bash run.sh --no-build --emulator --avd $badAvdName`：\n$head\n" +
                "外层那圈 `timeout -k 5 60` 是这一跑能被掐下来的唯一靠山",
            head.contains(
                "[branch] 命令 timeout -k 5 60 bash run.sh --no-build --emulator --avd $badAvdName",
            ),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `[branch] 退出码 124`：\n$head\n" +
                "124 是 `timeout` 掐断进程的专属退出码 —— 脚本自己没有退出的路径",
            head.contains("[branch] 退出码 124"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `开跑前 adb devices 一台设备都没有`：\n$head\n" +
                "这一跑先杀掉了本机那台模拟器，才凑出真零设备、逼脚本走开机臂",
            head.contains("开跑前 adb devices 一台设备都没有"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `观测 stdout 357 字节 stderr 0 字节`：\n$head\n" +
                "输出停在「开模拟器 $badAvdName（日志 …）」那一句之后再无一行 —— 这正是死等的形状",
            head.contains("观测 stdout 357 字节 stderr 0 字节"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `退出码 124、整跑 60.1 秒`：\n$head\n" +
                "整跑被压满 60 秒还没自己退出，说明脚本没有兜底",
            head.contains("退出码 124、整跑 60.1 秒"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `run.sh:167`：\n$head\n" +
                "这一行是把「那句死等在哪一行」钉在盘上的机内形状",
            head.contains("run.sh:167"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `行内无任何 timeout 包裹`：\n$head",
            head.contains("行内无任何 timeout 包裹"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `因为 :167 排在 :168 的 waited=0 计时器**之前**`：\n$head\n" +
                "这就是「240 秒那条 die 永远轮不到」的成因",
            head.contains("因为 :167 排在 :168 的 waited=0 计时器**之前**"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `0.1 秒内以 1 退出`：\n$head\n" +
                "对照面：直接跑模拟器本体、同一个坏 AVD 名，它 0.1 秒就死了",
            head.contains("0.1 秒内以 1 退出"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `Unknown AVD name [$badAvdName]`：\n$head\n" +
                "模拟器自己明写了不认识这个 AVD 名 —— 卡住的不是它",
            head.contains("Unknown AVD name [$badAvdName]"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `10.1 秒被掐`：\n$head\n" +
                "原语面：零设备下 `timeout 10 adb wait-for-device` 10.1 秒被掐 —— 这条死等坐在 adb 原语身上",
            head.contains("10.1 秒被掐"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `用时 P2 整跑 60.1 秒`：\n$head",
            head.contains("用时 P2 整跑 60.1 秒"),
        )
        assertTrue(
            "$emuHangLog 的表头里找不到 `孤儿 P2 掐断后留下一对`：\n$head\n" +
                "掐断会留下一对孤儿（跑 run.sh 的 bash.exe 与它前台死等的 adb.exe），这一行记着当场收掉了",
            head.contains("孤儿 P2 掐断后留下一对"),
        )
        val arm = branchHeader.find(head)
        assertTrue(
            "$emuHangLog 的分支行里没点 `--emulator`：\n$head",
            arm != null && arm.groupValues[1].contains("--emulator"),
        )

        assertTrue(
            "$emuHangLog 的正文里找不到 `开模拟器 $badAvdName（日志`：\n$body\n" +
                "脚本打到这一句之后再没输出 —— 正文只有这四行正是死等的证据",
            body.contains("开模拟器 $badAvdName（日志"),
        )
        assertTrue(
            "$emuHangLog 的正文里出现了 `模拟器 240 秒内没开机完成`：那一句该发生却没发生 —— " +
                "它排在死等之后，永远轮不到（负向断言只判正文；表头里那句是「没打印」的引语）",
            !body.contains("模拟器 240 秒内没开机完成"),
        )
        assertTrue(
            "$emuHangLog 的正文里出现了 `已在 emulator-5554 上跑起来`：这一跑根本没开成机（负向断言）",
            !body.contains("已在 emulator-5554 上跑起来"),
        )
        assertTrue(
            "$emuHangLog 的正文里出现了 `构建 :app:assembleDebug`：这一跑带的是 `--no-build`（负向断言）",
            !body.contains("构建 :app:assembleDebug"),
        )
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
