package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档里写的『`某文件.kt`（N 行）』还对不对得上磁盘」的守卫。
 *
 * [DocLineRefGuardTest] 管的是 `文件.kt:行号`（指向源码的**某一处**），[DocCrossRefGuardTest] 管的是
 * `docs/0X:NNN`（文档之间的引用），[DocTableShapeGuardTest] / [DocHeadingStructureGuardTest] 管的是
 * 表格与标题的**形状**。文档里还有第四类位置宣称，前四条谁都不管：**整份文件的行数**
 * —— `` `core/AppContainer.kt`（143 行） `` 这种「这个文件一共多少行」的说法。它跟行号一样是最容易
 * 过期的一类（源码长一行，写下的数字就漂），而它从第 1 轮到第 109 轮**从来没有过任何机器判据**。
 *
 * ## 它做什么
 *
 * 把 16 份受管文档正文里**所有**「`.kt` 文件名 + 括号 + 行数」一股脑抓出来，逐条比磁盘实测行数，
 * 用**六条**用例交叉钉住：
 *
 * 1. [everyManagedDocIsRead]：16 份受管文档一份不少地在（10 份 `docs/00`~`09` + `README.md` +
 *    `records/` 下 5 份）。少读一份就等于把它的读数整个豁免——那是这条守卫最想防的假绿。
 * 2. [everyClaimedKtFileExists]：每一处提到的 `.kt` 文件都必须**逐字大小写不差地**存在于
 *    `app/src/main|test|androidTest/java` 三个根之下，**只有 [deletedFileExempt] 里那张表例外**。
 *    表外的不存在 ⇒ 红；表里那条现在又存在了 ⇒ 也红（双向）。
 * 3. [everyLiveClaimMatchesDisk]：**现读**类读数里，写下的 N 必须**等于**磁盘行数（同名多份时，
 *    任一份行数相等即算对）。**历史叙述类不判**（见下）。
 * 4. [everyClaimIsCounted]：三道覆盖面下限——命中**处数**、**现读类处数**、**现读类涉及的不同文件数**。
 *    正则写歪、扫描根被换、规则被改宽，都会退化成「零命中 ⇒ 一律通过」，这三把下限挡住那种假绿。
 * 5. [headingHistRuleStillFires]：**历史叙述**类的两道下限——命中**处数**与涉及的不同文件数。
 *    第 3 条下界只钉「现读类没有缩水」，这一条钉「历史类那条规则还在成规模地生效」，两条合起来
 *    才把「哪一类该判、哪一类该豁免」的分界线钉住。
 * 6. [readmeCodeScaleClaimMatchesDisk]：`README.md`「当前状态」里那句**代码规模**（`` `app/src/main`
 *    有 N 个 Kotlin 文件 ``、`` `app/src/test` 有 N 个 JVM 测试类 ``、`` `app/src/androidTest` 有 N 个
 *    设备层测试类 ``）三个数必须等于磁盘实测的 `*.kt` **文件数**。第 120 轮加，理由见下。
 *
 * ## 为什么「历史叙述」不回改（`records/03` ADR-070）
 *
 * 同一段写法在文档体系里承担两种完全不同的角色：
 *
 * - **现读**：`` `ui/play/PlayScreen.kt`（312 行） `` 是在讲**今天**这份文件多少行——它必须对，
 *   一漂就是文档假话，本轮 [everyLiveClaimMatchesDisk] 判的就是这一类。
 * - **历史叙述**：`` `domain/play/PlayWords.kt`(53 行) `` 出现在「第 11 轮新增…」「第 12 轮 GAP-07
 *   收口：删除…」这种段子里，写的是**当轮当时的快照**。第 12 轮真把这个文件删了，今天磁盘上找不到它
 *   ——这不是错字，是历史记录该有的样子。按 ADR-070，记录里对「当轮当时的尺寸」**一律不回改**，
 *   也就没有机器判据可判。
 *
 * 「一行是现读还是历史」按**三条规则**机械判定（第 110 轮定案，实现在 [isHistorical]）：
 *
 * 1. **本行**含 `第 N 轮`（含中文数字轮次，`records/04` 用的是「第五十四轮 / 第六十三轮」那种写法）；
 * 2. **本行所在的标题链**里任一层含轮次或历史语汇（`## 第 11 轮…`、`### 8.13 第 42 轮…`、
 *    `## 7. 追溯矩阵`、`### 63.8 变更与关联` 这些**节标题**本身就把整节定性了）；
 * 3. **`records/CHANGELOG.md` 整份文件**——它的大标题是 `# 变更日志`、条目是 `### Added` 这种
 *    发布历史快照，一旦把 `变更日志` / `CHANGELOG` 加进规则 2 的词表，该文件全部读数自动归历史类。
 *
 * ## 本轮把五处**真失真**改掉了（第 110 轮的真事件）
 *
 * 普查脚本 `build/r110/classify3.py` 跑出 `TOTAL=248 LIVE=20 HIST=228`，现读类里**五处**对不上，
 * 全部是**简单数字替换、替换前后同为三位数、字节等长**，逐处读过原文后回改成现值：
 *
 * - `docs/02-概要设计说明书.md:63`：`core/AppContainer.kt`（143 行）→ **202**（依赖装配那一节的标题行）；
 * - `docs/03-详细设计说明书.md:464`：`domain/play/PlayCatalog.kt`（38 行）→ **40**；
 * - `docs/03-详细设计说明书.md:598`：`ui/play/PlayVoice.kt`（139 行）→ **148**；
 * - `docs/03-详细设计说明书.md:716`：`ui/play/PlayScreen.kt`（295 行）→ **312**；
 * - `docs/04-数据库设计说明书.md:227`：`data/db/AnchorDao.kt`（36 行）→ **45**（同行 `AnchorEntity.kt`
 *   的 31 行是对的，只改后一处）。
 *
 * 另有**一处真错字**（不是读数漂，是路径压根不存在）：`docs/01-需求规格说明书.md:514` 把测试文件写成
 * `ui/play/uiKnowsNoRulesTest.kt`，真名**首字母大写** `UiKnowsNoRulesTest.kt`（Kotlin 类名即文件名，
 * 首字母必须大写）。那一行属于历史叙述（标题链含「追溯矩阵」）本不需判，但文件名存在性对**所有**读数
 * 都判（见第 2 条），所以本轮一并改成真名。改后复扫 `LIVE_BAD=0 HIST_NOFILE=3`——剩下三条就是
 * [deletedFileExempt] 里那三处 `PlayWords.kt`。
 *
 * ## 它不保证什么
 *
 * - **不核字节数与 md5**。本轮**只做行数**。同一句话里往往还写着「（N 行 / M 字节）」或「md5 `…`」，
 *   那些数字**一个字都不判**：对账要改的不是一个数字而是整句（如 `docs/03:3327`「447 行 19,138 字节
 *   → **467 行 20,543 字节 / md5 …**」），本轮明确不做；那些句子在最新判据下**已全部归历史类**。
 *   这条缺口是 R110 新挂的一笔旧账。
 * - **不判语境**。文件存在、行数相等，不等于那句话把它放在了正确的位置或正确的语义里。
 * - **只认单行里的一处读数**。`（143 行）` 认，`` （第 143 行） `` 是 [DocLineRefGuardTest] 的地盘，
 *    `143 行 / 11,070 字节` 只取走前半截的行数、后半截字节不看（见上一条）。
 * - **代码规模只判 `README.md` 那三句**（第 6 条，第 120 轮加）。`docs/0X` / `records/0X` 里若也写
 *   「某个目录下有多少个 `.kt`」这类**总数**，一条都不判；第 6 条的锚点是**逐字**钉在 `README.md`
 *   「当前状态」那三句上的，措辞一改就走「锚点找不到」分支报红，而不是静默通过。
 * - **不改文档**。它只报红。哪一处该回改成现值、哪一处该按 ADR-070 留着，**没有机器判据**；
 *   这条守卫把「改完之后不许再有现读类坏键」钉住。
 *
 * ⇒ 结论：**R-10 仍然是橙色**。第 110 轮把「整份文件的行数会漂」这句话从**完全没有兜底**变成
 * **有了兜底**（16 份受管文档之内、只判行数与文件名存在性），并修掉了当场查出的五处读数失真
 * 与一处文件名错字；第 120 轮又把 `README.md` 的「代码规模」三句从**从来没人管**变成**有判据**，
 * 并修掉当场查出的 `app/src/test` 61 → **62**（那份清单从第 118 轮起就该是 62）。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。下限自检的纪律抄自 [DocLineRefGuardTest] / [DocCrossRefGuardTest] /
 * [CopyCountGuardTest]。
 */
class DocCodeLineClaimGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    /** 受管文档清单：`docs/00`~`09` 十份 + 仓库根 [README]，加 `records/` 下五份。 */
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
     * 读数写法：`` `X.kt`（N 行） ``。
     *
     * 逐段：文件名（字母数字下划线 + `.kt`）；后随**最多四个非括号、非换行字符**（吃掉 `` ` ``、`/`、
     * 空格这些间隔符）；然后左括号（半角或全角）；再是 N（允许千分位逗号）；最后「行」。
     * 这四字符间隔是**刻意**留的余量：`` `a/B.kt`（143 行） `` 与 `` `B.kt`(143 行) `` 都要认，
     * 但隔得太远（跨小节）的写法不认，免得把两句无关的话拼成一处读数。
     */
    private val claimPattern =
        Regex("""([A-Za-z0-9_]+\.kt)[^（(\n]{0,4}[（(]\s*([0-9][0-9,]*)\s*行""")

    /**
     * 历史叙述判定词表（第 110 轮定案）。轮次里含中文数字：`records/04` 用「第五十四轮」这类写法，
     * 只认阿拉伯数字会整片漏掉。`变更日志` / `CHANGELOG` 是为 `records/CHANGELOG.md` 的整份文件
     * 定性准备的（见类注释规则 3）。
     */
    private val historicalMarker = Regex(
        """第\s*[0-9一二三四五六七八九十百零]+\s*轮|追溯|修订记录|变更记录|变更日志|CHANGELOG|历史|实测|附录""",
    )

    /** 受管文档份数下限（现值 16，与 [DocHeadingStructureGuardTest] 的 `managedFileFloor` 同值）。 */
    private val managedFileFloor = 16

    /**
     * 命中**处数**下限（第 110 轮实测 248 处，取值 240 留一截余量）。正则写歪、扫描根被换走、
     * 或受管文档被删空，都会退化成「零命中 ⇒ 一律通过」。
     */
    private val totalClaimFloor = 240

    /**
     * **现读**类命中处数下限（第 110 轮实测 20 处，取 20 与实测齐平，是**刻意**的）。
     * 规则被改宽（比如给历史词表加了太常见的词）会把现读类整片洗成历史类；那些读数正是**唯一
     * 会被判对错**的一群，少一处就该有人来看一眼，而不是被「历史类还有两百多处」糊过去。
     */
    private val liveClaimFloor = 20

    /** 现读类涉及的**不同 `.kt` 文件数**下限（第 110 轮实测 20 个文件各一处，取 20）。 */
    private val distinctLiveFileFloor = 20

    /**
     * **历史**类命中处数下限（第 110 轮实测 228 处，取值 220）。这一条与 [liveClaimFloor] 是一对：
     * 它挡住「历史规则整片失效 ⇒ 全部读数被当成现读 ⇒ 靠大范围豁免/回改糊过去」的另一面。
     */
    private val histClaimFloor = 220

    /** 历史类涉及的**不同 `.kt` 文件数**下限（第 110 轮实测 94 个，取值 90）。 */
    private val distinctHistFileFloor = 90

    /**
     * 允许「磁盘上已经没有这个文件」的那些文件名，值是「为什么它正当」。
     *
     * 第 110 轮全量 248 处逐条比过，找不到文件的只有 **3** 处，全是同一个文件 `PlayWords.kt`：
     * 它在**第 12 轮**「GAP-07 收口：词表合一」时被**真删了**（连同它的用例一起），而
     * `records/01` 第 11 轮（新增）、`records/01` 第 12 轮（删除）、`records/CHANGELOG.md` 三处
     * 都在**如实记录当时的样子**——那不是错字，是历史记录。按 `records/03` ADR-070 **不回改**。
     *
     * 这张表是**双向**判据（见 [everyClaimedKtFileExists]）：表外的文件找不到 ⇒ 红；表里这个
     * 名字将来又在 `app/src` 下出现了 ⇒ 也红，逼着人来重新判定一次，而不是让一条过期豁免悄悄躺着。
     */
    private val deletedFileExempt = mapOf(
        "PlayWords.kt" to
            "第 12 轮「GAP-07 收口：词表合一」时真删了这个文件（连同它的用例），三处读数" +
            "（records/01:1482 第 11 轮新增、records/01:1726 第 12 轮删除、records/CHANGELOG:1625）" +
            "都是当轮快照，按 records/03 ADR-070 不回改",
    )

    /**
     * `README.md`「当前状态」的**代码规模**三句的锚（第 120 轮加，供 [readmeCodeScaleClaimMatchesDisk] 用）。
     *
     * 每条锚 = 目录 + **带完整措辞**的正则 + 分类词 + 下限：
     *
     * - 正则里写死了「`app/src/main`」「有」「个 Kotlin 文件」这些**逐字样**，为的是「措辞被改宽/换掉」
     *   能走「锚点找不到」那条分支报红，而不是悄悄不再判（那正是这笔账挂了十轮的原因：`README.md`
     *   的代码规模从第 1 轮到第 119 轮**没有任何机器判据**，写着什么没人看）。
     * - 下限只增不减，防的是「整片丢失」（比如 `app/src` 被搬走 ⇒ 实测 0 ⇒ 若只看「两数相等」，一个
     *   把 README 也改成 0 的人就能『对齐』过去）。
     *
     * ## 口径为什么是「`*.kt` **文件数**」而不是「测试**类**数」
     *
     * `app/src/androidTest` 下有 **31** 个 `.kt`，但只有 **30** 个带 `@Test`——唯一不含 `@Test` 的是
     * `app/src/androidTest/java/com/fortheelder/ui/TestTeardown.kt`（JUnit `@AfterClass` 的收尾工具，
     * 不是测试类）。README 那句按**文件数**读是 31、按**测试类**读是 30，两种口径都说得通；第 120 轮
     * **钉死为文件数**，并把口径写进 README 那句的抬头（「都按 `*.kt` 文件数」），好让文档与守卫一个口径。
     * 同一天 `app/src/test` 下 62 个 `.kt` **全是** `*Test.kt`、**全都**含 `@Test`，三种口径同值 62，
     * 所以差别只在 `androidTest` 那一侧。
     *
     * 目录取**README 原文写的那个**（`app/src/main`、不带 `/java`）：今天三个目录的 `.kt` 都落在
     * `java/` 之下，两种读法同值（79 / 62 / 31）；这层等价是**事实**不是规定，将来若有 `.kt` 落在
     * `java/` 之外，判的就是 README 字面上那句「`app/src/main` 有 N 个」。
     */
    private val codeScaleAnchors = listOf(
        CodeScaleAnchor(
            root = "app/src/main",
            pattern = Regex("""`app/src/main`\s*有\s*([0-9][0-9,]*)\s*个\s*Kotlin\s*文件"""),
            wording = "`app/src/main` 有 N 个 Kotlin 文件",
            floor = 75,
        ),
        CodeScaleAnchor(
            root = "app/src/test",
            pattern = Regex("""`app/src/test`\s*有\s*([0-9][0-9,]*)\s*个\s*JVM\s*测试类"""),
            wording = "`app/src/test` 有 N 个 JVM 测试类",
            floor = 60,
        ),
        CodeScaleAnchor(
            root = "app/src/androidTest",
            pattern = Regex("""`app/src/androidTest`\s*有\s*([0-9][0-9,]*)\s*个\s*设备层测试类"""),
            wording = "`app/src/androidTest` 有 N 个设备层测试类",
            floor = 30,
        ),
    )

    /** 一条代码规模锚。 */
    private data class CodeScaleAnchor(
        /** 相对仓库根的目录，如 `app/src/main`。 */
        val root: String,
        /** 带完整措辞的锚定正则，第一组是写下的数。 */
        val pattern: Regex,
        /** 人话写法，用于报错信息。 */
        val wording: String,
        /**
         * 该目录下 `.kt` 文件数下限（第 120 轮实测 79 / 62 / 31，分别取 75 / 60 / 30）。
         *
         * 三条都**只增不减**：目录被搬走、被改名、或被整片清空会让实测掉到 0，那时若只判「两数相等」，
         * 把 README 也改成 0 就能对齐过去——下限挡住这种「一起错」。
         */
        val floor: Int,
    )

    /** 一处命中。 */
    private data class Claim(
        /** 受管文件名（如 `02-概要设计说明书.md`、`01-迭代记录.md`、`README.md`）。 */
        val source: String,
        /** 源文件里的行号（1 起）。 */
        val sourceLine: Int,
        /** 读到的 `.kt` 文件名（如 `AppContainer.kt`）。 */
        val ktName: String,
        /** 写下的行数。 */
        val claimedLines: Int,
        /** 是否属于历史叙述（历史类不判行数，见类注释）。 */
        val historical: Boolean,
    ) {
        /** 登记 / 报错用的键：`源文件:源行 -> X.kt`。 */
        val key: String get() = "$source:$sourceLine -> $ktName"
    }

    /**
     * 行数口径：数 `\n` 的个数，文件不以 `\n` 结尾再补一个。这与 `wc -l`、与第 110 轮普查脚本
     * `build/r110/classify3.py` 的 `klines()` **逐字同口径**，好让两边读数直接对账。
     * 刻意**不复用** [DocCrossRefGuardTest.linesOf]——那个是「按行切成表再丢掉末尾空元素」，
     * 口径不同（对「只有一个换行」这类边界会差一行）。
     */
    private fun lineCount(file: File): Int {
        val text = file.readText()
        var n = 0
        for (c in text) if (c == '\n') n++
        if (text.isNotEmpty() && !text.endsWith("\n")) n++
        return n
    }

    /** 受管文档清单（按 [managedPaths] 顺序，缺文件的当场报红由第 1 条负责）。 */
    private fun managedFiles(): List<File> =
        managedPaths.map { File(repoRoot, it.replace('/', File.separatorChar)) }

    /**
     * `app/src` 三个根下全部 `.kt` 的索引：文件名 → 各份实测行数。
     * 同名多份（三个根下各有一份同名文件的可能）时收成一个列表，判定时「任一份行数相等即算对」。
     */
    private fun ktIndex(): Map<String, List<Int>> {
        val index = LinkedHashMap<String, MutableList<Int>>()
        for (root in srcRoots) {
            val dir = File(repoRoot, root.replace('/', File.separatorChar))
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .forEach { index.getOrPut(it.name) { mutableListOf() } += lineCount(it) }
        }
        return index
    }

    /**
     * 逐份受管文档抽读数。标题链按 `#` 的个数裁栈，与 `build/r110/classify3.py` 的写法同构。
     */
    private fun claimsIn(file: File): List<Claim> {
        val lines = file.readText().split("\n")
        val headings = mutableListOf<String>()
        val out = mutableListOf<Claim>()
        for ((offset, text) in lines.withIndex()) {
            if (text.startsWith("#")) {
                val level = text.takeWhile { it == '#' }.length
                while (headings.size >= level) headings.removeAt(headings.size - 1)
                headings += text.trim()
            }
            for (match in claimPattern.findAll(text)) {
                val historical = historicalMarker.containsMatchIn(text) ||
                    headings.any { historicalMarker.containsMatchIn(it) }
                out += Claim(
                    source = file.name,
                    sourceLine = offset + 1,
                    ktName = match.groupValues[1],
                    claimedLines = match.groupValues[2].replace(",", "").toInt(),
                    historical = historical,
                )
            }
        }
        return out
    }

    /** 全量扫描：受管文档的每处读数、以及 `.kt` 索引。 */
    private fun scan(): Pair<List<Claim>, Map<String, List<Int>>> {
        val claims = mutableListOf<Claim>()
        for (file in managedFiles()) {
            if (!file.isFile) continue
            claims += claimsIn(file)
        }
        return claims to ktIndex()
    }

    /**
     * 第 1 条：16 份受管文档一份不少地在。
     *
     * 这是**所有别的判据的地基**：少读一份文档，它的读数就整片掉出管辖、既不报红也不报绿，
     * 是这条守卫最想防的假绿。所以这里既设份数下限，又逐份点名。
     */
    @Test
    fun everyManagedDocIsRead() {
        val present = managedFiles().filter { it.isFile }
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
     * 第 2 条：每一处提到的 `.kt` 文件都**逐字**存在于三个源码根之下，只有 [deletedFileExempt]
     * 例外；且表里那些名字**现在必须已经不存在**。
     *
     * 文件名的**大小写**是硬判据：Kotlin 的类名即文件名，`uiKnowsNoRulesTest.kt` 这种小写起头的名字
     * 在任何平台上都不是一个真实文件（第 110 轮就是这样揪出 `docs/01:514` 的一处错字）。
     */
    @Test
    fun everyClaimedKtFileExists() {
        val (claims, index) = scan()

        val notFound = claims.filter { it.ktName !in index }
        val unregistered = notFound.filter { it.ktName !in deletedFileExempt }.map { it.key }
        assertTrue(
            "有 ${unregistered.size} 处读数提到的 `.kt` 文件在 `app/src` 三个根下找不到，" +
                "而 [deletedFileExempt] 里没有记账：\n" +
                unregistered.sorted().joinToString("\n") { "  $it" } +
                "\n要么把文件名回改成真名（注意大小写），要么（只有当那一行确实在记录「当时有个文件、" +
                "后来被删了」时）把它补进 [deletedFileExempt] 并写清理由",
            unregistered.isEmpty(),
        )

        val stale = deletedFileExempt.keys.filter { it in index }
        assertTrue(
            "[deletedFileExempt] 里有 ${stale.size} 个文件名现在**又出现在** `app/src` 下了：" +
                stale.joinToString("、") +
                "\n这张表是替「已经被删掉的文件」作保的；被保的文件又回来了，这条豁免就过期了" +
                "（会吃掉下一次同名文件的真报红），要么把它从表里删掉，要么重新判定它现在是不是还该留着",
            stale.isEmpty(),
        )
    }

    /**
     * 第 3 条：**现读**类读数里，写下的 N 必须等于磁盘行数（同名多份时任一份相等即算对）。
     *
     * **历史叙述类一处不判**——那不是错字，是当轮快照，按 `records/03` ADR-070 不回改（见类注释）。
     * 这一条只在这一群「号称在讲今天」的读数里挑错。
     */
    @Test
    fun everyLiveClaimMatchesDisk() {
        val (claims, index) = scan()
        val live = claims.filterNot { it.historical }

        val bad = mutableListOf<String>()
        for (claim in live) {
            val real = index[claim.ktName]
            if (real == null) {
                bad += "${claim.key}：磁盘上没有这个文件（第 2 条也会报这一处）"
                continue
            }
            if (claim.claimedLines !in real) {
                bad += "${claim.key}：文档写 ${claim.claimedLines} 行，磁盘实测 " +
                    real.joinToString("/") + " 行"
            }
        }
        assertTrue(
            "有 ${bad.size} 处**现读**读数与磁盘对不上（历史叙述类不在此列）：\n" +
                bad.sorted().joinToString("\n") { "  $it" } +
                "\n现读读数写的是「这个文件今天多少行」，一漂就是文档假话，请回改到上面的实测值；" +
                "若那一行其实在记录「当时多少行」，请检查它所在的标题链/本行为何没被认成历史叙述",
            bad.isEmpty(),
        )
    }

    /**
     * 第 4 条：三道下限——命中**处数**、**现读类处数**、**现读类不同文件数**。
     *
     * 这一条不判任何一处读数的对错，只问「同一条尺子还是不是一个量级」。它与第 3 条是一对：
     * 第 3 条在「扫到了的」里面挑错，这一条保证「扫到的」不是空集、且现读类没被洗掉。
     */
    @Test
    fun everyClaimIsCounted() {
        val (claims, _) = scan()
        assertTrue(
            "16 份受管文档里只抓到 ${claims.size} 处「`.kt`（N 行）」写法，低于下限 $totalClaimFloor" +
                "（第 110 轮实测 248 处）：正则失效、受管清单被换走、或文档被删空，" +
                "这条守卫都会退化成「零命中 ⇒ 一律通过」——不许这么绿",
            claims.size >= totalClaimFloor,
        )
        val live = claims.filterNot { it.historical }
        assertTrue(
            "现读类只认出 ${live.size} 处，低于下限 $liveClaimFloor（第 110 轮实测 20 处）：" +
                "历史词表被改宽会把现读类整片洗成历史类，而现读类正是唯一会被判对错的一群",
            live.size >= liveClaimFloor,
        )
        val liveFiles = live.map { it.ktName }.toSet()
        assertTrue(
            "现读类只涉及 ${liveFiles.size} 个不同的 `.kt` 文件，低于下限 $distinctLiveFileFloor" +
                "（第 110 轮实测 20 个）：同上，这是同一层假绿的另一个面",
            liveFiles.size >= distinctLiveFileFloor,
        )
    }

    /**
     * 第 5 条：**历史**类的两道下限。与第 4 条是**一对反向**判据。
     *
     * 第 4 条防「现读类被洗掉」，这一条防它的镜像——「历史规则整片失效 ⇒ 全部读数被当成现读」。
     * 那种情况下第 3 条会大面积报红，但若有人为了消红而把文档整片回改（本不该回改的历史叙述），
     * 记录就毁了。两道下限一起，才把「哪一类该判、哪一类该豁免」的分界线钉住。
     */
    @Test
    fun headingHistRuleStillFires() {
        val (claims, _) = scan()
        val hist = claims.filter { it.historical }
        assertTrue(
            "历史类只认出 ${hist.size} 处，低于下限 $histClaimFloor（第 110 轮实测 228 处）：" +
                "标题链词表或轮次正则失效，会让本该豁免的历史叙述全变成现读读数、被第 3 条大面积报红，" +
                "进而诱使人去回改历史记录——这与 records/03 ADR-070 相反",
            hist.size >= histClaimFloor,
        )
        val histFiles = hist.map { it.ktName }.toSet()
        assertTrue(
            "历史类只涉及 ${histFiles.size} 个不同的 `.kt` 文件，低于下限 $distinctHistFileFloor" +
                "（第 110 轮实测 94 个）：同上，这是同一层假绿的另一个面",
            histFiles.size >= distinctHistFileFloor,
        )
    }

    /**
     * 某个目录（相对仓库根，如 `app/src/main`）下 `*.kt` 的**文件数**。
     *
     * 走法与 [ktIndex] 同构（同 [srcRoots] 那套 `walkTopDown` + 按扩展名筛），**只数文件、不看内容**：
     * 口径是「文件数」，不是「含 `@Test` 的类数」（见 [codeScaleAnchors] 里对 31 vs 30 的说明）。
     * 目录不存在时返回 0——那时第 6 条的下限会当场报红，而不是静默通过。
     */
    private fun ktFileCount(root: String): Int {
        val dir = File(repoRoot, root.replace('/', File.separatorChar))
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().count { it.isFile && it.name.endsWith(".kt") }
    }

    /**
     * 第 6 条：`README.md`「当前状态」里的**代码规模**三句必须与磁盘实测的 `*.kt` **文件数**相等。
     *
     * 这道账是**第 110 轮**挂下的：「`README.md`『代码规模』从第 1 轮到第 109 轮没有任何机器判据」
     * （见 [codeScaleAnchors] 的类注释），一直橙着到第 120 轮才收。收的时候**当场就查到一处真错**：
     *
     * - `app/src/main` 79 ✅（对）
     * - `app/src/test` 写 **61**，实测 **62** ❌（第 118 轮起就有 62 个 `.kt` 了，README 没跟上）
     * - `app/src/androidTest` 31 ✅（对，按文件数口径；按「含 `@Test` 的类数」是 30）
     *
     * 三道断言：
     *
     * 1. **每条锚恰好命中一次**。一处都没命中 ⇒ 措辞被改（`有` → `有共`、`个 Kotlin 文件` → `份 .kt`）
     *    就等于把这条读数整个豁免，必须报红；命中多于一处 ⇒ 同一句口径出现两份、其中一份会掩盖另一份，
     *    也报红。
     * 2. **写下的数 == 磁盘实测文件数**（逐条比 [ktFileCount]）。
     * 3. **三道下限**（[CodeScaleAnchor.floor]）：只增不减，防「目录被搬走 ⇒ 两边一起变 0」那种假对齐。
     *
     * 它**不改文档**，只报红；改哪儿、改成几，由人按实测读数定（本轮改的是 `61` → `62`）。
     */
    @Test
    fun readmeCodeScaleClaimMatchesDisk() {
        val readme = File(repoRoot, "README.md")
        assertTrue("仓库根下找不到 README.md：这条守卫管的就是它，缺了就整片失去判据", readme.isFile)
        val lines = readme.readText().split("\n")

        val missing = mutableListOf<String>()
        val bad = mutableListOf<String>()
        val receipts = mutableListOf<String>()

        for (anchor in codeScaleAnchors) {
            val hits = mutableListOf<Pair<Int, Int>>()
            for ((idx, text) in lines.withIndex()) {
                for (match in anchor.pattern.findAll(text)) {
                    hits += (idx + 1) to match.groupValues[1].replace(",", "").toInt()
                }
            }
            if (hits.isEmpty()) {
                missing += "「${anchor.wording}」（锚定正则：${anchor.pattern.pattern}）"
                continue
            }
            if (hits.size > 1) {
                bad += "「${anchor.wording}」在 README.md 里命中 ${hits.size} 处" +
                    "（第 ${hits.joinToString("、") { it.first.toString() }} 行）：" +
                    "同一句口径只该有一处，多出来的那处会把真读数掩盖掉"
                continue
            }

            val (lineNo, claimed) = hits.single()
            val real = ktFileCount(anchor.root)
            if (claimed != real) {
                bad += "README.md:$lineNo：写 **$claimed**，磁盘实测 **$real**" +
                    "（${anchor.root} 下 `*.kt` 文件数）"
            } else {
                receipts += "README.md:$lineNo：${anchor.root} = $real"
            }
            if (real < anchor.floor) {
                bad += "README.md:$lineNo：${anchor.root} 下只数到 **$real** 个 `*.kt`，" +
                    "低于下限 ${anchor.floor}（第 120 轮实测：" +
                    codeScaleAnchors.joinToString(" / ") { "${it.root} ${ktFileCount(it.root)}" } +
                    "）：目录被搬走、改名或被清空都会掉到这个量级以下，" +
                    "那时「两数相等」不等于「两数都对」——把 README 也改成同一个错数就能对齐过去，" +
                    "下限挡住这种一起错"
            }
        }

        assertTrue(
            "README.md 的「代码规模」有 ${missing.size} 句锚点**一处都没命中**：\n" +
                missing.joinToString("\n") { "  $it" } +
                "\n这三句是「逐字钉住」的：措辞一改（增字、换词、拆行改标点）就会走到这里。" +
                "请把 README.md 那句话改回 `codeScaleAnchors` 里认得的措辞，" +
                "或者（只有当口径真的要换时）同步改 [codeScaleAnchors] 的正则与本条用例的说明——" +
                "**不要**为了让它绿就把这条断言删掉，那正是这笔账从前十轮没人管的老路",
            missing.isEmpty(),
        )

        assertTrue(
            "README.md 的「代码规模」有 ${bad.size} 处对不上：\n" +
                bad.joinToString("\n") { "  $it" } +
                "\n现读读数写的是「这个目录今天有多少个 `.kt`」，一漂就是文档假话。" +
                "核对过的三处实测值：\n" +
                codeScaleAnchors.joinToString("\n") { "  ${it.root} = ${ktFileCount(it.root)} 个 `*.kt`" } +
                "\n（口径见 [codeScaleAnchors]：数**文件**，不数「含 `@Test` 的类」——" +
                "androidTest 下 `TestTeardown.kt` 不是测试类，所以按文件数 31、按测试类 30。）",
            bad.isEmpty(),
        )

        assertTrue(
            "第 6 条的三条锚读到的现读回执与磁盘对上了：${receipts.joinToString("；") { it }}" +
                "；三道下限 ${codeScaleAnchors.joinToString(" / ") { "${it.root}>=${it.floor}" }}" +
                "，实测 ${codeScaleAnchors.joinToString(" / ") { "${it.root}=${ktFileCount(it.root)}" }}",
            receipts.size == codeScaleAnchors.size,
        )
    }
}
