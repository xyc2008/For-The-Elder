package com.fortheelder.ui

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一键运行脚本（`run.sh` / `run.bat`）的每一条参数分支，**行为和文档说的是同一件事**」的守卫。
 *
 * 第 103 轮交付 `run.sh`（Windows 侧配 20 行的 `run.bat` 只负责找 `bash.exe` 再转发），把
 * 「找 SDK → 构建 → 装到设备 → 启动 → 回显读数」串成一条命令；第 112 轮给它加了 `--log FILE` 并
 * 真跑了三条入口、留了三份日志入库。但这条链上一直挂着一笔账：**脚本的每一条参数分支都没有自动化
 * 判据**——`--emulator` / `--reinstall` / `--shot` / `--logcat` / `--avd` 各自「跑起来之后该出现
 * 什么」，只能人眼看。第 113 轮的 [RunEvidenceGuardTest] 收掉了其中「日志与记录能对账」那一面，
 * **脚本行为那一面照旧欠着**；本条轮（第 116 轮）是它的第一次收口。
 *
 * 与 [RunEvidenceGuardTest] 的关系：那条判**已经入库的日志字节**（文本层，**不重跑任何脚本**）；
 * 本条判**脚本自己现在会怎么动**（行为层，**真起进程**）。两者互补，谁也不能替谁。
 *
 * ## 一、判据（第 116 轮定案，九道）
 *
 * 1. **`--help` 只查用法、不干活，且帮助正文不是抄来的第二份**（[helpPrintsTheHeaderCommentAndDoesNoWork]）：
 *    退出码 0、stderr 一字不写、输出里**不许出现 `[run] `**（出现了就说明它在找 SDK / 构建 / 装包，
 *    那就不叫「查用法」），且输出**逐字等于**由脚本头部注释推导出来的文本——`usage()` 是拿 `awk`
 *    把头部注释当正文打印的，两边一旦走散（有人另抄一份帮助去改），这条当场报红。
 * 2. **不认识的参数：退出码 2、指出是哪个、并指路 `--help`**（[unknownFlagFailsWithExitCodeTwoAndSaysWhichFlag]）。
 * 3. **解析期就返回的两条路不落日志**（[parseTimeReturnsNeverWriteALogFile]）：`--log L --help` 与
 *    `--log L --nope` 都必须**不产生** `L`——`--log` 的 wrapper 在参数解析**之后**，解析期就 `exit`
 *    的两次调用根本走不到那儿（见第四节这笔账）。
 * 4. **`--log` 真的接住输出、并原样转发退出码**（[logWrapperCapturesTheFailureAndPropagatesItsExitCode]）：
 *    给一个「有 `platform-tools` 目录、里面没有 adb」的假 SDK，跑 `--log L --no-build` ⇒ 子进程在
 *    `adb 不存在` 上死掉，wrapper 必须（a）让日志文件正好 4 行：三行脚标 ＋ 一行失败原因；
 *    （b）把日志 `cat` 回屏幕并回显真实退出码；（c）自己**不往 stderr 写**（子进程的 stderr 已被
 *    重定向进日志）；（d）自己的退出码 = 子进程的退出码 1。
 * 5. **`run.bat` 转发参数、转发退出码**（[runBatForwardsArgumentsAndExitCodes]）：`cmd /c run.bat --help`
 *    退出 0 且帮助正文原样出现；`--nope` 退出 **2**（不是 0——吞掉退出码的转发器会让 CI 永远绿）。
 * 6. **帮助里写的开关 ⇄ 解析器认识的开关，双向相等**（[everyFlagInTheHeaderCommentIsHandledAndEveryHandlerIsDocumented]）：
 *    头部注释里的 `--flag` 集合必须**等于** `case` 分支里的集合。**第 116 轮就是靠这条抓出真缺陷的**：
 *    `--avd NAME` 一直在 `case` 里（第 103 轮就在），头注释与 README 的用法清单里都没有 ⇒ 照 `--help`
 *    用的人**永远发现不了它**；同一轮把两处都补上了（`-h` 由 `--help` 那一条覆盖，不单列）。
 * 7. **每条分支都得有实质动作，且被赋的变量后面真被读到**（[everyFlagHandlerDoesSomethingAndItsVariableIsReadLater]）：
 *    空壳分支（`:`）＝ **假开关**；赋了一个从没被读过的变量 ＝ **死开关**。第 115 轮正是从一张登记表里
 *    挖出过一个「从没被检查过的下限字段」，同一类毛病在脚本里长这样，所以这条照原样复用。
 * 8. **README 的用法清单不多不少**（[readmeListsExactlyTheFlagsTheScriptAccepts]）：含 `./run.sh` 的
 *    代码围栏里的 `--flag` 集合 == 脚本接受的集合。**只在围栏内取**，因为 README 别处（Gradle 那几行）
 *    有 `--console=plain` 这类别的 `--` 参数。
 * 9. **`run.bat` 是纯 ASCII 的转发器，不是第二份实现**（[runBatIsAnAsciiCrlfForwarderNotAReimplementation]）：
 *    纯 ASCII（cmd.exe 按 OEM 码页解析 `.bat`，UTF-8 中文会变乱码、甚至从句中断开命令）、行尾**不混用**、
 *    含 `run.sh` / `%*` / `exit /b %ERRORLEVEL%`，且**不含** `adb` / `gradlew` / `sdk` 这些该由
 *    `run.sh` 管的词（一旦两边各实现一遍，「哪边才是真的」就说不清了）。
 *
 * ## 二、为什么**真调进程**，以及它的边界
 *
 * 这是本仓库测试里**第一处**起进程（此前 60 个 JVM 测试类里 `ProcessBuilder` / `Runtime.exec` 一个都
 * 没有）。理由：判据 1~5 说的是「脚本会怎么动」，**读文本判不出来**——`case` 里写了 `exit 2` 不代表
 * 真跑起来退出码是 2（wrapper 可能吞掉、`run.bat` 可能不转发）。所以只有真跑一条路。
 *
 * 刻意**不做**的事：不重跑整条 `run.sh`（要设备、要 Gradle、要几分钟——那是 L3 / L4 的活）；不把
 * 安装 / 启动路径搬进单元测试；不碰 `run.sh` 里任何真动作。本类只跑**能在几秒内结束**的分支：
 * `--help`、坏参数、`--log` 的解析期、以及「找不到 adb」这条早退路径。
 *
 * 进程定位：`bash.exe` 依次找 `%ProgramFiles%\Git\bin`、`%ProgramFiles(x86)%\Git\bin`、
 * `%LocalAppData%\Programs\Git\bin`，再退到 `PATH`；`cmd.exe` 用 `%ComSpec%`。**找不到就报红、
 * 写明怎么装**，不静默跳过——跳过等于这道守卫不存在（同 [RunEvidenceGuardTest] 里「属性缺失直接
 * 报错」的口径）。
 *
 * ## 三、它能不能抓住「有人改坏一处」（反向变异实测：第 116 轮）
 *
 * 逐组改一处、定向跑、看红在哪一条，再**逐字节还原并核对 md5**（`run.sh` 工作区是 LF、`run.bat`
 * 工作区是 `text=auto` 检出的 CRLF，还原用 `cp` 快照而不是 `sed`）。八组**全部报红**，还原后三份文件
 * 的 md5 都回到快照值：
 *
 * | 变异 | 改了什么 | 预期红在 | 实测红在（八组真跑读数） |
 * | --- | --- | --- | --- |
 * | M1 | 加一个 `--fake) FAKE_MODE=1 ;;` 分支，头注释里也写上 | 第 7 条（死开关） | 第 7 条（`FAKE_MODE` 从没被读过）＋第 8 条（README 里没这个开关），共红 2 条 |
 * | M2 | 只在头注释里加 `--shotty` | 第 6 条（帮助里写了解析器不认识） | 第 6 条，差集正是 `[--shotty]`，只此一条 |
 * | M3 | `--shot) DO_SHOT=1 ;;` 改成 `--shot) : ;;` | 第 7 条（空壳分支＝假功能） | 第 7 条，报的是「`--shot` 的分支体实为 `:`」，只此一条 |
 * | M4 | 坏参数分支的 `exit 2` 改成 `exit 0` | 第 2 条 | 第 2 条＋第 3 条＋第 5 条，共红 3 条 |
 * | M5 | `--log` wrapper 的 `exit "$rc"` 改成 `exit 0` | 第 4 条 | 第 4 条（wrapper 报 0、子进程是 1），只此一条 |
 * | M6 | `run.bat` 的 `exit /b %ERRORLEVEL%` 改成 `exit /b 0` | 第 5 条 | 第 5 条＋第 9 条（末行那句不见了），共红 2 条 |
 * | M7 | 往 `run.bat` 里塞一行中文注释 | 第 9 条 | 第 9 条，报「24 个非 ASCII 字节」，只此一条 |
 * | M8 | 从 README 围栏里删掉 `--logcat` 一行 | 第 8 条 | 第 8 条（脚本有、README 藏着），只此一条 |
 *
 * 五组（M2 / M3 / M5 / M7 / M8）**精确落在预期的那一条**上，说明判据能定位；另三组多红几条，
 * 不是误报而是**真耦合**：M4 改的那个 `exit 2` 同时是「坏参数」「`--log L --nope`」「`run.bat --nope`」
 * 三条路的退出码来源，一处改坏自然三面见血；M1 添的开关同时违反「分支要有实质动作」与「README 不多不少」；
 * M6 抹掉的那句既是「退出码要转发」的**因**、也是第 9 条白纸黑字要它存在的**果**。这三组多红的地方
 * 正好说明这几条判据判的是不同的事、不是同一件事的复述。
 *
 * ## 四、它不保证什么（照实交代）
 *
 * - **只覆盖解析期与「找不到 adb」这一条早退路径。** `--emulator` / `--reinstall` / `--shot` /
 *   `--logcat` 的**真动作**（真开模拟器、真卸载、真截图、真跟 logcat）**仍无自动化判据**——那要设备、
 *   要几分钟，代价太高。第 112 轮挂的这笔账，本条**只收了一部分**，剩下的照旧欠着。
 * - **`--log` 的两条路它留不下日志。** 解析期就 `exit` 的两次调用（`--help`、坏参数）走不到 wrapper，
 *   所以那两次的「完整输出」只在终端上，日志里没有；这不是缺陷而是结构使然（wrapper 在解析循环之后），
 *   但**用例 3 把它钉成了已知形状**，免得哪天有人以为「加了 `--log` 就什么都有」。
 * - **`run.bat` 的 CRLF-ness 仍然没有机器判据。** 它的行尾由 `.gitattributes` 的 `text=auto` 决定：
 *   同一份文件在 Windows 上检出是 CRLF、在 Linux 上检出是 LF，所以这里**不能**断言「磁盘上必须 CRLF」
 *   （在 Linux 上会假红）。第 9 条只判**纯 ASCII** 与**行尾不混用**这两件真正有害的事。
 * - **`bash.exe` / `cmd.exe` 不存在时是报红，不是跳过。** 在非 Windows 主机上本类会红——这是刻意的：
 *   这条链是 Windows 开发者的入口，判不了就该有人知道，而不是安静地绿。
 * - **它不改任何文档**，也不判「README 该不该这么写」。报红之后是改脚本、改 README 还是按
 *   `records/03` ADR-070 留作当轮快照，本守卫不判。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。本轮补的是「脚本自己的分支行为 ↔ 文档承诺」这一面的兜底，且是**部分**收口。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这些用例**直接失败**
 * 而不是跳过。另外，`run.sh` 与 `run.bat` 本身已在同一批里声明成 Gradle 测试任务的输入（第 116 轮修掉的
 * 一处真缺陷，见 `app/build.gradle.kts` 里 `inputs.files` 那一段）——否则「改了脚本、守卫静默不跑」。
 */
class RunScriptBranchGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val runSh: File = File(repoRoot, "run.sh")
    private val runBat: File = File(repoRoot, "run.bat")
    private val readme: File = File(repoRoot, "README.md")

    // ---------------------------------------------------------------- 判据 1：--help

    @Test
    fun helpPrintsTheHeaderCommentAndDoesNoWork() {
        val r = exec(listOf(bash.absolutePath, runSh.absolutePath, "--help"))

        assertEquals("`run.sh --help` 的退出码应当是 0（实为 ${r.exit}）", 0, r.exit)
        assertEquals(
            "`run.sh --help` 不该往 stderr 写任何东西（实为 `${r.stderr.take(80)}`）：查用法是最轻的一条路，" +
                "它一旦开始报错，就说明它在解析完之后还往下走了",
            "",
            r.stderr,
        )
        assertFalse(
            "`run.sh --help` 的输出里出现了 `[run] `：那是「开始干活」的脚标（找 SDK、构建、装包都在它之后），" +
                "查用法不该干活。输出开头是 `${r.stdout.take(60)}`",
            r.stdout.contains("[run] "),
        )
        assertEquals(
            "`run.sh --help` 的输出必须**逐字**等于由脚本头部注释推导出的文本：`usage()` 是拿 awk 把头部注释" +
                "当帮助正文打印的——「用法只有一份」这句话就靠这条钉住。有人另抄一份帮助去改，两边立刻走散",
            headerUsageText(),
            r.stdout,
        )
    }

    // ---------------------------------------------------------------- 判据 2：坏参数

    @Test
    fun unknownFlagFailsWithExitCodeTwoAndSaysWhichFlag() {
        val r = exec(listOf(bash.absolutePath, runSh.absolutePath, "--nope"))

        assertEquals(
            "不认识的参数应当以退出码 **2** 结束（实为 ${r.exit}）：0 会让脚本调用方以为跑成功了，" +
                "1 会和「运行中失败」混在一起分不开",
            2,
            r.exit,
        )
        assertEquals("坏参数不该往 stdout 写东西（实为 `${r.stdout.take(80)}`）", "", r.stdout)
        assertTrue(
            "stderr 里应当**点名**是哪个参数不认识（实为 `${r.stderr.take(120)}`）：只说「参数错误」等于把人" +
                "留在原地猜",
            r.stderr.contains("不认识的参数：--nope"),
        )
        assertTrue(
            "stderr 里应当指路 `--help`（实为 `${r.stderr.take(120)}`）：报错却不给出路，等于把人堵在门口",
            r.stderr.contains("--help"),
        )
        assertFalse("坏参数这条路也不该出现 `[run] ` 脚标：它在解析期就返回了", r.stdout.contains("[run] ") || r.stderr.contains("[run] "))
    }

    // ---------------------------------------------------------------- 判据 3：解析期不落日志

    @Test
    fun parseTimeReturnsNeverWriteALogFile() {
        val tmp = freshTmp("parse-time")
        val helpLog = File(tmp, "with-help.log")
        val badLog = File(tmp, "with-bad-flag.log")

        val a = exec(listOf(bash.absolutePath, runSh.absolutePath, "--log", msysPath(helpLog), "--help"))
        assertEquals("`--log L --help` 该走「查用法」那条路、退出 0（实为 ${a.exit}）", 0, a.exit)
        assertFalse(
            "`--log L --help` 留下了日志文件 `${helpLog.name}`：`--log` 的 wrapper 在参数解析循环**之后**，" +
                "`--help` 在解析期就 exit 了、走不到那里。这条判据把这个**已知形状**钉住——" +
                "别以为「加了 --log 就什么都有」",
            helpLog.exists(),
        )

        val b = exec(listOf(bash.absolutePath, runSh.absolutePath, "--log", msysPath(badLog), "--nope"))
        assertEquals("`--log L --nope` 该走「坏参数」那条路、退出 2（实为 ${b.exit}）", 2, b.exit)
        assertFalse(
            "`--log L --nope` 留下了日志文件 `${badLog.name}`：坏参数和 `--help` 一样在解析期返回，" +
                "解析期的错误**连日志都没有**——这是本轮登记的一笔账，不是本守卫要修的东西",
            badLog.exists(),
        )
    }

    // ---------------------------------------------------------------- 判据 4：--log wrapper

    @Test
    fun logWrapperCapturesTheFailureAndPropagatesItsExitCode() {
        val tmp = freshTmp("log-wrapper")
        // 造一个「有 platform-tools 目录、里面没有 adb」的假 SDK：脚本的 SDK 解析会认下它（目录存在即选中），
        // 紧接着的 adb 存在性检查必然失败 —— 这是唯一一条几秒内就能走完、又真的走到 die 的路径。
        val sdk = File(tmp, "sdk")
        assertTrue("建不出假 SDK 目录", File(sdk, "platform-tools").mkdirs())
        val log = File(tmp, "run.log")

        val r = exec(
            listOf(bash.absolutePath, runSh.absolutePath, "--log", msysPath(log), "--no-build"),
            put = mapOf("ANDROID_SDK" to msysPath(sdk)),
            remove = listOf("ANDROID_HOME"),
        )

        assertEquals(
            "子进程死在「找不到 adb」上（退出 1），wrapper 必须原样转发这个退出码（实为 ${r.exit}）：" +
                "退出码被吞掉，脚本调用方就再也看不到失败了",
            1,
            r.exit,
        )
        assertEquals(
            "wrapper 把子进程的 stdout 与 stderr 一起重定向进了日志文件，它自己不该再往 stderr 写" +
                "（实为 `${r.stderr.take(80)}`）——两边都写会让同一句话出现两次",
            "",
            r.stderr,
        )
        assertTrue("日志文件 `${log.name}` 没生成：`--log` 的全部意义就是留下这一份", log.isFile)

        val raw = log.readText()
        val lines = raw.split("\n").dropLastWhile { it.isEmpty() }
        assertEquals(
            "日志应当正好 **4** 行：三行脚标（时间 / 命令 / 主机）＋ 一行失败原因。实为 ${lines.size} 行，" +
                "内容：${lines.joinToString(" / ") { it.take(40) }}",
            4,
            lines.size,
        )
        assertTrue("日志第 1 行该是时间脚标（实为 `${lines[0].take(60)}`）", lines[0].startsWith("[run] 时间 "))
        assertTrue(
            "日志第 2 行该记下这次用的是什么命令与参数（实为 `${lines[1].take(80)}`）——" +
                "日志脱离了「谁跑的这一趟」就变成了没头没尾的一堆字",
            lines[1].startsWith("[run] 命令 ") && lines[1].contains("--log"),
        )
        assertTrue("日志第 3 行该记下主机与 bash 版本（实为 `${lines[2].take(80)}`）", lines[2].startsWith("[run] 主机 "))
        assertTrue(
            "日志第 4 行该是失败原因（实为 `${lines[3].take(120)}`）",
            lines[3].startsWith("[run] 失败：") && lines[3].contains("不存在"),
        )
        assertTrue(
            "失败原因里该带上那个「本来要找、但没找到」的 adb 路径（实为 `${lines[3].take(160)}`）：" +
                "只说「找不到」不说是哪个路径，等于让人自己去猜",
            lines[3].contains("/platform-tools/adb"),
        )
        assertTrue(
            "wrapper 跑完必须把日志 `cat` 回屏幕（实为 `${r.stdout.take(80)}`）：`--log` 承诺的是" +
                "「同时写进文件并照常打印」，少一半就不算",
            r.stdout.contains("[run] 日志 "),
        )
        assertTrue(
            "wrapper 回显的尾行该带上真实退出码（实为 `${r.stdout.takeLast(80)}`）：屏幕上看到「日志 xxx」" +
                "却看不到结果，人还得回头翻文件",
            r.stdout.contains("退出码 1"),
        )
        assertFalse(
            "日志里出现了 `[run] SDK ` —— 这说明 adb 的存在性检查被挪到了打印 SDK **之后**。" +
                "顺序变了不一定是错，但这条判据钉的是当前形状（先确认 adb 在、再宣称找到 SDK）：" +
                "它一改，本用例就该有人来看一眼并同步改判据",
            raw.contains("[run] SDK "),
        )
    }

    // ---------------------------------------------------------------- 判据 5：run.bat 转发

    @Test
    fun runBatForwardsArgumentsAndExitCodes() {
        val help = runBat("--help")
        assertEquals(
            "`cmd /c run.bat --help` 的退出码应当是 0（实为 ${help.exit}）：run.bat 末尾必须把子进程的" +
                "退出码转发出去",
            0,
            help.exit,
        )
        assertTrue(
            "run.bat 的输出里该有它自己报的那句「用的是哪个 bash.exe」（实为 `${help.stdout.take(80)}`）" +
                "与 `${help.stderr.take(80)}`）：转发器黑箱化，出了问题没法一眼看出它用了哪个解释器",
            help.combined.contains("[run.bat] bash: "),
        )
        assertTrue(
            "run.bat 把参数转发给 run.sh 之后，帮助正文应当原样出现（实为 `${help.stdout.take(80)}`）",
            help.combined.contains("常伴（For-The-Elder）一键运行脚本"),
        )

        val bad = runBat("--nope")
        assertEquals(
            "`cmd /c run.bat --nope` 的退出码应当也是 **2**（实为 ${bad.exit}）：转发的意义就在这里——" +
                "退出码一吞，Windows 侧的调用者（CI、批处理）就永远看到绿色",
            2,
            bad.exit,
        )
        assertTrue(
            "run.bat 那条路的报错正文该原样透出来（实为 `${bad.combined.take(120)}`）",
            bad.combined.contains("不认识的参数：--nope"),
        )
    }

    // ---------------------------------------------------------------- 判据 6：帮助 ⇄ 解析器

    @Test
    fun everyFlagInTheHeaderCommentIsHandledAndEveryHandlerIsDocumented() {
        val text = runSh.readText()
        val documented = flagTokens(headerComment(text))
        // 比之前先排序：`documented` 是排好序的清单，`keys` 是脚本里的书写顺序——两者直接比会因为
        // 「集合 vs 列表」以及顺序不同而**假红**（内容一模一样也报错）。
        val handled = caseBranches(text).keys.sorted()

        assertEquals(
            "「帮助里写的开关」与「解析器认识的开关」必须双向相等。差集 `$documented - $handled`（帮助里写了、" +
                "解析器不认识）让人照帮助用却撞上「不认识的参数」；差集 `$handled - $documented`（解析器认了、" +
                "帮助里没有）更隐蔽——那个开关**照 --help 用的人永远发现不了它**，第 116 轮就是靠这条抓出" +
                "`--avd NAME` 的真缺陷（它在 case 里从第 103 轮就在，头注释与 README 的用法清单里都没有）",
            handled,
            documented,
        )
        assertTrue(
            "解析器一个开关都没解析出来（实为 ${handled.size} 个）：多半是 `case \"$1\" in` 或分支的写法变了，" +
                "本守卫的取法也就瞎了——这时该改判据，而不是让判据静默通过",
            handled.size >= 8,
        )
    }

    // ---------------------------------------------------------------- 判据 7：分支不是空壳

    @Test
    fun everyFlagHandlerDoesSomethingAndItsVariableIsReadLater() {
        val text = runSh.readText()
        val branches = caseBranches(text)
        val afterCase = afterParseCase(text)

        for ((flag, body) in branches) {
            assertTrue(
                "`$flag` 的分支体是空的或只写了 `:`（实为 `$body`）：空壳分支 ＝ **假开关**——参数被吃掉了、" +
                    "脚本却什么也没做，比报「不认识的参数」还坏",
                body.isNotBlank() && body.trim() != ":",
            )

            if (flag == "--help") {
                assertTrue(
                    "`--help` 的分支该调 `usage` 把帮助打出来（实为 `$body`）：这是它唯一该做的事",
                    body.contains("usage"),
                )
                continue
            }

            val assigned = Regex("""([A-Z][A-Z0-9_]*)=""").findAll(body)
                .map { it.groupValues[1] }
                .toList()
            assertTrue(
                "`$flag` 的分支里没有任何赋值动作（实为 `$body`）：这条分支即使被触发也什么都不会发生。" +
                    "一个不改变任何状态的开关就是假开关",
                assigned.isNotEmpty(),
            )
            for (v in assigned) {
                assertTrue(
                    "`$flag` 赋了 `$v`，但脚本后面一次也没读过 `$v`：这是**死开关**——参数被记下来了、" +
                        "却没有一处行为依赖它。第 115 轮正是从一张登记表里挖出过一个从没被检查过的下限字段，" +
                        "同一类毛病在脚本里长这样",
                    afterCase.contains("\$" + v) || afterCase.contains("\${" + v),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 判据 8：README 清单

    @Test
    fun readmeListsExactlyTheFlagsTheScriptAccepts() {
        val fences = fencedBlocks(readme.readText()).filter { it.contains("./run.sh") }
        assertTrue(
            "README 里找不到含 `./run.sh` 的代码围栏：用法清单被挪走或改写成别的形式了，这道判据就瞎了" +
                "（这正是它必须**先断言自己找到了东西**的理由）",
            fences.isNotEmpty(),
        )
        val listed = fences.flatMap { flagTokens(it) }.toSet()
        val accepted = caseBranches(runSh.readText()).keys

        assertEquals(
            "README 里含 `./run.sh` 的围栏所列的开关，必须与脚本接受的开关一致。只报 README 有的、" +
                "脚本没的（让人照 README 抄下来就撞墙），或反过来（脚本有、README 藏着）都不行。" +
                "**只在围栏内取**：README 别处（Gradle 那几行）有 `--console=plain` 这类别的 `--` 参数，" +
                "扫全文会把它们一起卷进来",
            accepted,
            listed,
        )
    }

    // ---------------------------------------------------------------- 判据 9：run.bat 本体

    @Test
    fun runBatIsAnAsciiCrlfForwarderNotAReimplementation() {
        val bytes = runBat.readBytes()
        val nonAscii = bytes.count { (it.toInt() and 0xFF) > 0x7E }
        assertEquals(
            "run.bat 里有 $nonAscii 个非 ASCII 字节：cmd.exe 按 OEM 码页解析 `.bat`，UTF-8 中文进去会变成" +
                "乱码，甚至把命令从句子中间断开——所以这个文件必须纯 ASCII（README 也这么承诺）",
            0,
            nonAscii,
        )

        val text = String(bytes, Charsets.US_ASCII)
        val crlf = Regex("\r\n").findAll(text).count()
        val lineFeeds = text.count { it == '\n' }
        val loneLf = lineFeeds - crlf
        assertTrue(
            "run.bat 的行尾混用了：$crlf 个 CRLF 与 $loneLf 个孤立 LF。混用行尾在 cmd.exe 下会出现" +
                "「半条命令」这种最难查的怪事。注：这里**只**判「不混用」——磁盘上是 CRLF 还是 LF 由" +
                "`.gitattributes` 的 `text=auto` 决定（Windows 检出 CRLF、Linux 检出 LF），要求「必须 CRLF」" +
                "会在 Linux 上假红",
            crlf == 0 || loneLf == 0,
        )
        assertTrue(
            "run.bat 里没有 `run.sh`：它存在的**全部理由**就是找到 bash.exe 再转发过去",
            text.contains("run.sh"),
        )
        assertTrue(
            "run.bat 里没有 `%*`：参数没被转发出去，`run.bat --no-build` 会被当成「不带参数」——" +
                "本该跳过构建的一次运行会重新构建，而且不报错",
            text.contains("%*"),
        )
        assertTrue(
            "run.bat 里没有 `exit /b %ERRORLEVEL%`：子进程的退出码会被吞掉，`run.bat --nope` 也会报 0",
            text.contains("exit /b %ERRORLEVEL%"),
        )
        for (word in listOf("adb", "gradlew", "sdk")) {
            assertFalse(
                "run.bat 里出现了 `$word`：它只该找 bash.exe 再转发，**不重复实现** `run.sh` 的逻辑" +
                    "（README 明写了这条分工）。两边各实现一遍之后，「哪边才是真的」就说不清了",
                text.contains(word),
            )
        }
    }

    // ---------------------------------------------------------------- 工装

    /** 一次真跑的读数：退出码与两路输出（CRLF 已归一成 LF，方便与派生文本逐字比）。 */
    private class Exec(val exit: Int, val stdout: String, val stderr: String) {
        val combined: String get() = stdout + stderr
    }

    /**
     * 起一个进程并把两路输出各自收全。`put` / `remove` 用来构造确定的环境（例如给一个假 SDK、
     * 把 `ANDROID_HOME` 摘掉免得真 SDK 抢先被选中）。
     */
    private fun exec(
        command: List<String>,
        put: Map<String, String> = emptyMap(),
        remove: List<String> = emptyList(),
    ): Exec {
        val pb = ProcessBuilder(command)
        pb.directory(repoRoot)
        for ((k, v) in put) pb.environment()[k] = v
        for (k in remove) pb.environment().remove(k)
        // 万一测试自己是在一个 --log wrapper 的子进程里跑起来的（理论上不会），也得是干净的一次。
        pb.environment().remove("FTE_RUN_LOGGED")

        val p = pb.start()
        p.outputStream.close()
        val err = StringBuilder()
        val errThread = Thread { p.errorStream.reader(Charsets.UTF_8).use { err.append(it.readText()) } }
        errThread.start()
        val out = p.inputStream.reader(Charsets.UTF_8).use { it.readText() }
        val done = p.waitFor(180, TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            throw AssertionError("${command.joinToString(" ")} 跑了 180 秒还没结束，已强杀")
        }
        errThread.join(10_000)
        return Exec(
            p.exitValue(),
            out.replace("\r\n", "\n"),
            err.toString().replace("\r\n", "\n"),
        )
    }

    private val bash: File by lazy {
        val candidates = listOfNotNull(
            System.getenv("ProgramFiles")?.let { File(it, "Git/bin/bash.exe") },
            System.getenv("ProgramFiles(x86)")?.let { File(it, "Git/bin/bash.exe") },
            System.getenv("LocalAppData")?.let { File(it, "Programs/Git/bin/bash.exe") },
        ).filter { it.isFile } +
            System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
                .map { File(it.trim(), "bash.exe") }
                .filter { it.isFile }
        candidates.firstOrNull() ?: throw AssertionError(
            "找不到 bash.exe（找过 Git for Windows 的三条常见路径与 PATH）：`run.sh` 是 Git Bash 脚本，" +
                "本守卫要**真调进程**才判得了它的分支行为。装 Git for Windows 后重跑。" +
                "这里报红而不是跳过——跳过等于这道守卫不存在",
        )
    }

    private fun runBat(vararg args: String): Exec {
        val cmd = System.getenv("ComSpec")?.takeIf { File(it).isFile }?.let { it } ?: "cmd.exe"
        return exec(listOf(cmd, "/c", runBat.absolutePath) + args)
    }

    /** 把 Windows 路径转成 MSYS 形式（`C:\a\b` → `/c/a/b`），免得 bash 侧被反斜杠绊倒。 */
    private fun msysPath(f: File): String {
        val p = f.absolutePath.replace('\\', '/')
        val m = Regex("""^([A-Za-z]):(.*)$""").find(p) ?: return p
        return "/" + m.groupValues[1].lowercase() + m.groupValues[2]
    }

    /** 建（并先清空）一个落临时产物的目录；`build/` 不入库，不会被守卫当成受管文件。 */
    private fun freshTmp(name: String): File {
        val dir = File(repoRoot, "build/r116/tmp/$name")
        dir.deleteRecursively()
        assertTrue("建不出临时目录 ${dir.absolutePath}（build/ 下应当可写）", dir.mkdirs())
        return dir
    }

    /**
     * 头部注释的**行文本**（不含首行 shebang，遇第一个非 `#` 行即止）——与 `usage()` 里那段
     * `awk 'NR == 1 { next } /^#/ { print; next } { exit }'` 的取法逐字对应。
     */
    private fun headerComment(text: String): String {
        val sb = StringBuilder()
        for ((i, line) in text.split("\n").withIndex()) {
            if (i == 0) continue
            if (!line.startsWith("#")) break
            sb.append(line).append("\n")
        }
        return sb.toString()
    }

    /**
     * 由头部注释推导出的帮助正文：去掉 `#` 与紧随其后的**至多一个**空格——与 `usage()` 里那句
     * `sed 's/^# \{0,1\}//'` 逐字对应（所以「只有一份用法」这句话才有机器判据）。
     */
    private fun headerUsageText(): String =
        headerComment(runSh.readText())
            .split("\n")
            // 头部注释末行必带一个换行 ⇒ split 之后多出一个空串；只去掉这一串，别动正文里的空行
            // （头部注释里那些裸 `#` 行去掉前缀**之后**才是空行，它们要在场）。
            .dropLastWhile { it.isEmpty() }
            .joinToString("\n") { it.removePrefix("#").removePrefix(" ") } + "\n"

    /** 参数解析的那个 `case` 块（从 `case "$1" in` 到与它配对的 `esac` 行末）。 */
    private fun caseBlock(text: String): String {
        val start = text.indexOf("case \"\$1\" in")
        assertTrue(
            "run.sh 里找不到 `case \"\$1\" in`：参数解析被重写了，本守卫的取法要跟着改（不能静默通过）",
            start >= 0,
        )
        return text.substring(start, parseCaseEsac(text, start).range.last + 1)
    }

    /**
     * 参数解析那个 `case` 块**之后**的原文（从它那个 `esac` 行的行首起）——判据 7 问「这个开关赋的
     * 值后面有没有人读」，往后找的就是这一段。
     */
    private fun afterParseCase(text: String): String {
        val start = text.indexOf("case \"\$1\" in")
        assertTrue("run.sh 里找不到 `case \"\$1\" in`（与 caseBlock 同一处前置条件）", start >= 0)
        return text.substring(parseCaseEsac(text, start).range.first)
    }

    /**
     * 参数解析那个 `case` 的收尾 `esac` 所在的行。
     *
     * 不能拿 `indexOf("\nesac")` 去找：本脚本的 `esac` 都带缩进，**唯一顶格的那个 `esac`** 是后面
     * 「按平台定 `.exe` 后缀」那个小 case 的收尾。照 `\nesac` 取会把参数解析这个块**拉长**到那一段，
     * 于是 `--log` 赋的 `LOG_PATH` 明明在紧跟的 wrapper 里被读了，却因为取到的区间从更靠后开始
     * 而被判成「没人读」——第 116 轮第一次跑就是这么错过的，所以这里改成按「成行的 `esac`」找。
     */
    private fun parseCaseEsac(text: String, from: Int): MatchResult {
        val m = Regex("""^[ \t]*esac[ \t]*$""", RegexOption.MULTILINE).find(text, from)
        assertTrue(
            "从第 ${from + 1} 个字符起找不到成行的 `esac`：参数解析那个 `case` 的收尾被改写了，" +
                "本守卫的取法要跟着改（不能静默通过）",
            m != null,
        )
        return m!!
    }

    /**
     * `case` 块里每条 `--flag)` 分支的「开关 → 分支体」映射。`-h | --help)` 这种并列写法也算
     * （`-h` 由 `--help` 那一条覆盖，不单列——它和 `--help` 是同一个行为）。
     */
    private fun caseBranches(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val pattern = Regex(
            """^[ \t]+(?:-h \| )?(--[a-z][a-z0-9-]*)\)[ \t]*(.*?)[ \t]*;;[ \t]*$""",
            RegexOption.MULTILINE,
        )
        for (m in pattern.findAll(caseBlock(text))) {
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    /** 文本里的 `--flag` 词元（去重、排序，便于报错时逐字比）。 */
    private fun flagTokens(text: String): List<String> =
        Regex("""--[a-z][a-z0-9-]*""").findAll(text).map { it.value }.distinct().sorted().toList()

    /** markdown 里成对围栏之间的正文（不含围栏行本身）。 */
    private fun fencedBlocks(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inside = false
        for (line in text.split("\n")) {
            if (line.startsWith("```")) {
                if (inside) {
                    out.add(cur.toString())
                    cur.setLength(0)
                    inside = false
                } else {
                    inside = true
                }
                continue
            }
            if (inside) cur.append(line).append("\n")
        }
        assertTrue("README 里有没闭合的代码围栏（读到末尾还在围栏里）", !inside)
        return out
    }
}
