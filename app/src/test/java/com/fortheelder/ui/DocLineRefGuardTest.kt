package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档里写的那个行号，指的真是那一行吗」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 第 67 / 68 轮那两条守卫（[DocScreenCopyGuardTest]）读的是**字**：屏幕上那句话在源码里还在不在。
 * 文档里另一类宣称它们一个字都读不到——**位置**：`` `SettingsScreen.kt:122` `` 这种写法。行号是最容
 * 易过期的一类宣称（正文一动就全错），而它恰恰从来没被任何机器看过一眼：第 24 轮那条
 * [CopyCountGuardTest] 只扫 `app/src/main`，第 67 / 68 轮那两条只扫 `docs/09` 的引文。
 * 这条用例是**第一次把「文档里写的行号」当输入**。
 *
 * ## 它做什么
 *
 * 把第 47 轮 `SettingsScreen.kt` 正文整体后移留下的一族过期行号，逐条登记成
 * **「文档里那一串 → 源码里那一行地标」**（[refs]，一条一个 [LineRef]），然后用**七条**用例交叉钉住：
 *
 * 1. [everyRegisteredDocLineRefPointsAtItsAnchor]：每个地标行号都落在该源码文件的行数范围内，
 *    而且**那一行上确实有那句话**（[LineRef.anchor] 是那一行的一小段原文）。代码挪行 ⇒ 红。
 * 2. [everyRegisteredDocLineRefIsActuallyQuotedInTheDocument]：每一串都**逐字**出现在指定的那一份
 *    文档里。文档把行号改掉或删掉 ⇒ 红。
 * 3. [everyStrictlyWrittenDocLineRefAgreesWithItsAnchorLine]：**纯粹写法**（`文件名.kt:NNN` 或
 *    `文件名.kt:NNN-MMM`，文件名里不带路径分隔符）的那一串，它写出来的数字必须与地标行号一致
 *    （区间写法则要求地标落在这个区间里）。
 * 4. [everyPathPrefixedDocLineRefAgreesWithItsAnchorLine]（**第 97 轮新增**）：**带目录前缀**的写法
 *    （`路径/文件.kt:NNN` 或 `路径/文件.kt:NNN-MMM`）的那一串，① 它写出来的**路径**必须与地标登记的
 *    `source` 逐字相等（少一层目录、多一层目录都得红）② 它写出来的数字必须与地标行号一致。
 * 5. [everyPathPrefixedRefInTheBodyIsRegistered]（**第 102 轮新增**）：`docs/01` **正文区**
 *    （第 1 行起、到 `## 7.` 那个标题之前）里**出现的**每一串带目录前缀的行号，都必须**逐字**在 [refs]
 *    里有一条登记。给正文区里的带前缀引用上**全覆盖对账**：从「登记了才管」变成「一律得登记」。
 *    同族条目**一个不少**就是第 1~4 条的事，这一条只问「有没有漏网的」。
 * 6. [everyFileNameOnlyRefInTheBodyIsRegistered]（**第 104 轮新增**）：同一件事搬到**纯粹写法**上——
 *    正文区里出现的每一串**不带路径前缀**的行号，也必须**逐字**登记（[strictRefFloor] 那一族同族）；
 *    顺带钉死一条反面：正文区里**不许**出现 `/` 串多段的写法（`PlayGame.kt:22/25` 这种），命中即红，
 *    要拆成互不相同的单条引用、各自登记。第 102 轮挂的那笔账（「纯粹写法在正文区仍无全覆盖对账」）
 *    就此关掉。
 * 7. [everyLineRefEitherPointsInsideItsFileOrIsExplicitlyExempt]（**第 105 轮新增**）：前六条都只看
 *    **登记在案**的那几条；这一条反过来，把 16 份普查对象（`docs/` 与 `records/` 下所有 `.md` 加
 *    `README.md`）里**所有** `文件.kt:行号` 一股脑抓出来，凡文件名能在 `app/src` 下找到**同名**源码
 *    文件的，就逐条比「写出来的那个行号有没有超出该文件现有的行数」。越界、且不在 [lineRefOverflowExempt]
 *    里的 ⇒ 红。它不要求登记，只要求**越界这件事必须有过登记**——「文档里那个数比文件还长」是唯一一种
 *    不必看语境就能机械判定的错（第 105 轮实测 2,548 处引用，命中我方源码 2,403 处、展开成 2,623 个
 *    行号 / 585 个互不相同的「文件名:行号」，越界的**只有 41 处 / 5 对**，逐处看过上下文后判定全部正当，
 *    登记在 [lineRefOverflowExempt] 里）。
 *
 * ③ / ④ / ⑤ / ⑥ / ⑦ 是七条里真的把「文档里那个数、那个路径」与「代码里那一行」连起来的，① / ② 只保证
 * 「两边各自没变」。
 *
 * 七条用例都带下限自检（条目数、纯粹写法条目数、带前缀写法条目数、正文区带前缀命中串数、
 * 正文区纯文件名命中串数、全量比对里的普查份数 / 源码文件数 / 命中处数 / 不同串数、源码文件行数下限）：
 * 登记表被删空、
 * 某一类写法被无声删光、或源码文件被换成一个壳子，都会当场红，而不是「零条目 ⇒ 全部通过」；
 * `sourceLineFloor` 的每一把 key 还额外要求**至少被一条登记引到**——否则那把下限谁也守不到，
 * 等于没写（第 92 轮把一条恒真的自检改正成了真判据）。
 *
 * ## 它不保证什么
 *
 * - **只覆盖这一族**。第 92 轮把登记表从 13 条扩到 49 条、第 93 轮再扩到 61 条、
 *   **第 97 轮再扩到 69 条、第 104 轮再扩到 95 条**：`docs/01-需求规格说明书.md` §4.3~§4.19 与 §NFR 表里
 *   **当前态**的那些行（第 92 轮逐条回改到现值的 13 处，加上此前只人工核过「对」、第 92 轮纳入兜底的 7 处，
 *   再加**第 97 轮纳入兜底的 8 处**——7 处带目录前缀、1 处纯粹写法，
 *   再加**第 104 轮纳入兜底的 26 处纯粹写法**）、
 *   `docs/02-概要设计说明书.md` 2 处、`docs/05-接口设计说明书.md` 1 处交叉引用、
 *   `docs/03-详细设计说明书.md` §7.9 追溯矩阵索引行与 4 处现值地标、
 *   **第 93 轮新增的两族**：`docs/03` §7.3 语义挂载表的 10 条现行坐标（`asScreenTitle` / `asSectionTitle` /
 *   `announcesWhenItChanges` / `gridOf` / `gridCell` 各自的挂载点）与 `docs/09-用户手册.md` 选择页那 2 条，
 *   外加几条只为锚定邻域的「地标」。
 *   **一个字节不核**的仍是三类：**历史叙述**（`docs/01` §7 变更史与追溯矩阵、`docs/03` §8.4x /
 *   §8.5x 叙述段与 §8.18 内部的历史行号、`records` 目录下的迭代记录与决策记录、`docs/06` / `docs/09` 里「第 N 轮时是…」——
 *   它们是**当轮当时的快照**，写错才是错，回改成现值反而是篡改历史，见 `records/03` ADR-070。
 *   **第 97 轮据此明确一个字节不改**：`docs/01` §7 GAP-07 那行**删除线里**的 `domain/play/PlayWords.kt:22-38`
 *   ——那个文件**早已不存在**（第 37 轮收口时删掉、不留转发壳），但那一串被 `~~…~~` 包着、属**缺口原文**，
 *   同行的关闭叙述已写明「文件已删」⇒ 自洽的历史叙述，照 ADR-070 原样留着；GAP-04 / 05 / 08 / 09 同理）、
 *   **示例文本**（`docs/05` 代码块里的示意注释：它演示的是「这种注释长这样」，不是现值宣称）、
 *   **缩写形**（`：` 后直接跟数字、不带文件名，如 §4.4 里那几处 `:63-73` / `:157`——它说的是「紧邻
 *   上文那个文件的第 N 行」，要认出来得先解上文指的是谁，硬解会得到大量假报红；**第 93 轮把 §7.3 挂载表里
 *   三处缩写形主动展开成全形**，为的是让那几个挂载点逐条进得了登记表——这是**手工**决定，
 *   哪一处值不值得展开、展开到哪一层，没有机器判据）。
 * - **④ 只认单段路径前缀**。**多段串写**（`ui/common/ElderComponents.kt:110/142/157`、
 *   `ui/play/PlayScreen.kt:142/153/167/191/250`）与**缩写形**（`：` 后直接跟数字）第 4 条用例看不见，
 *   只由第 1 / 2 条保证「两边各自没变」；
 *   把行号写成别的形状（`第 122 行`、`L122`）同样看不见。这是有意为之：登记表要与文档里的
 *   **原样字样**逐字对齐，硬解一串自由文本里的数字，会得到大量假报红。**正文区里禁止多段串写**
 *   （**第 104 轮起**）：`PlayGame.kt:22/25` 已拆成 `PlayGame.kt:22` 与 `PlayGame.kt:25` 两条各自登记，
 *   同一形状再出现在正文区里就由第 6 条报红；`## 7.` 起的历史叙述（上面那两条带前缀的）
 *   不在此列，按 ADR-070 原样留。
 * - **④ 的路径必须精确等**。文档里若把路径写长 / 写短一层（把 `ui/settings/SettingsScreen.kt:446-461`
 *   写成 `settings/SettingsScreen.kt:446-461`），第 4 条会**报红**而不是「智能对上」——这是刻意：
 *   让「文档里那条路径」与「代码里那个文件」逐字一致，宁可误红也不要默默放过。
 * - **带前缀引用的覆盖面**（**第 102 轮改写**）：`docs/01` **正文区**（第 1 行起、到 `## 7.` 那个标题
 *   之前）里出现的每一串带目录前缀的行号，**自第 102 轮起一律要登记**（第 5 条用例对账）——第 97 轮那种
 *   「往正文里添一串带前缀、没同步加登记，第 4 条不会红」的盲点，在正文区之内就此关掉（第 102 轮反向
 *   变异实测：正文区插一串 `ui/theme/Dimens.kt:999` ⇒ 第 5 条红、其余四条不红）。**`## 7.` 起的变更史、
 *   追溯矩阵与 §9 的 GAP 清单不在管辖内**，按 ADR-070 原样留、不核（那部分写的是「当时这么写」，
 *   是历史叙述或缺口原文，不是现值宣称）。`docs/01` 通篇带前缀引用共 **27 处 / 24 串**，剖分是
 *   **9 处 / 9 串现值（全在登记表里）+ 18 处 / 15 串历史叙述（豁免）**；正文区实测命中就是那 9 串。
 * - **纯粹写法的覆盖面**（**第 104 轮改写**）：`docs/01` **正文区**里出现的每一串**不带路径前缀**的
 *   行号（`StarterOptions.kt:419`、`Encouragement.kt:50`、`PlayVoice.kt:115` 这类），**自第 104 轮起
 *   也一律要登记**（第 6 条用例对账）。第 102 轮只把**带前缀**那一类做成了正文区全覆盖、把这一类
 *   挂着（原文：「纯粹写法在正文区仍无全覆盖对账」）；第 104 轮补上并划掉那笔账：正文区纯文件名命中
 *   实测 **56 串**（**第 144 轮重测 61 串**，下限随之上抬），其中 26 串此前只有人工核过「对」、这一轮逐条登记进表（第 104 轮反向变异实测：
 *   正文区插一串 `PlayCatalog.kt:999` ⇒ 第 6 条红、其余五条不红）。**`## 7.` 起的变更史、追溯矩阵与
 *   §9 的 GAP 清单**仍不在管辖内**，按 ADR-070 原样留、不核（`docs/01` 通篇那两处多段串写就落在那儿）。
 *   正文区之外的纯文件名引用仍靠 `docs/07` §4 的监控信号兜着。
 * - **不核语境**。行号对了不等于那段话说对了地方——可能引错文件、引错语义。
 * - **第 7 条只看「行号有没有越界」**。它不判那一行现在说的是不是同一件事（语境不核，与第 1~6 条同一个
 *   已知边界）；也**只管文件名能在我方源码里找到同名的那些**——实测 145 处引用指向的 19 个 basename
 *   （`RoomDatabase.kt` / `NavController.kt` / `DispatchedTask.kt` 这类第三方堆栈帧、`Foo.kt` / `X.kt` /
 *   `a.kt` 这类变异示例、`PlayWords.kt` 这个第 37 轮删掉的文件）**整族不在管辖内**，第 105 轮逐类看过，
 *   全部正当、没有活缺陷。
 * - **越界豁免按 (文件名, 行号) 硬编码**。某份历史文档被重排、行号漂了，豁免会失配并误红；那时按实修表，
 *   不许放宽判据。[lineRefOverflowExempt] 是**双向**断言：多一条（不再越界了）少一条（有没登记的越界）都红。
 * - **不核布局、不核屏幕上读得到读不到**。那两件事分别归 `docs/03` §8.18 的版式读数与设备层用例。
 * - **不动 `docs/01` §7 那张矩阵里的行号**。那部分从第 21 轮起就在册为「未回填」（见 `docs/07`
 *   的 GAP 清单），第 92 轮的边界就是**不回填它**，而不是假装它已经回填。
 *
 * ⇒ 结论与第 67 / 68 / 91 三轮相同：**R-10 仍然是橙色**，只是残余风险又少了一块——「文档里的行号
 * 会漂」这句话，从第 91 轮起在**这一族之内**有了机器兜底（第 92 轮把这一族从 13 条铺开到 49 条、
 * 第 93 轮铺到 61 条、第 97 轮铺到 69 条、第 104 轮铺到 95 条、**第 144 轮铺到 100 条**），在这一族之外照旧只能靠人工
 * （`docs/07` §4 的监控信号）。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。下限自检的纪律抄自 [CopyCountGuardTest] 与 [DocScreenCopyGuardTest]。
 */
class DocLineRefGuardTest {

    /**
     * 一条登记：文档里写的那一串（[quote]，与文档**逐字一致**）应当指向 [source] 的第 [anchorLine] 行，
     * 而那一行上确实有 [anchor] 这一小段原文；[note] 写清这条为什么在登记表里。
     *
     * [root] 指明这条登记落在哪个源码根下（`main` / `test` / `androidTest` 三个值；第 104 轮新增
     * `androidTest`、第 148 轮加上 `test`，默认 `main`）；
     * [source] 一律是相对 `com/fortheelder` 的包内路径。
     */
    private data class LineRef(
        val doc: String,
        val quote: String,
        val source: String,
        val anchorLine: Int,
        val anchor: String,
        val note: String,
        val root: String = "main",
    )

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val sourceRoot: File = File(mainSrc, "com/fortheelder")

    /**
     * 设备层用例所在根 `app/src/androidTest/java`，与 `app/src/main/java` 同级（都在 `app/src` 下）。
     * 第 104 轮为了让正文区那两处引到仪器化用例的行号也能真正登记，才需要它。
     */
    private val androidTestSrc: File = File(mainSrc.parentFile!!.parentFile!!, "androidTest/java")

    /**
     * 单元测试用例所在根 `app/src/test/java`，与 `app/src/main/java` 同在 `app/src` 下（都是三级上交）。
     * 第 148 轮为了让 `docs/01` §4.20 AC-20.4 引到的 `PlayCatalogTest.kt` 行号也能真正登记，才需要它。
     */
    private val testSrc: File = File(mainSrc.parentFile!!.parentFile!!, "test/java")

    /**
     * 按登记的 [LineRef.root] 取该条源码文件所在的包根：只认 [mainSrc] / [testSrc] / [androidTestSrc]
     * 三个根（第 148 轮加上 `test`）。写下第四个值当场报错，而不是默默落到某个根上去找一个不存在的文件。
     */
    private fun sourceRootFor(root: String): File = File(
        when (root) {
            "main" -> mainSrc
            "test" -> testSrc
            "androidTest" -> androidTestSrc
            else -> error("未知的源码根「$root」：只认 main / test / androidTest")
        },
        "com/fortheelder",
    )

    private val requirements = "docs/01-需求规格说明书.md"
    private val design = "docs/03-详细设计说明书.md"
    private val manual = "docs/09-用户手册.md"

    /**
     * 这一族的现值登记表。左端是文档里**原样**的一串，右端是它现在该指的那一行。
     *
     * 第 47 轮给「再看一遍开头那几页」加了目的地，`SettingsScreen.kt` 正文整体后移，本表这一族
     * 的行号从那时起就一直没回改；第 91 轮先逐条回改到现值，再把它们钉在这里。
     */
    private val refs: List<LineRef> = listOf(
        // ---- docs/01 §4.4 / §4.5：设置页那两处「说话的时机」与「安静」的现值 ----
        LineRef(
            doc = requirements,
            quote = "SettingsScreen.kt:396",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 396,
            anchor = "SettingsVoice.timingLine(profile,",
            note = "§4.4 与 §4.5 两处都引它：打招呼间隔那一行的现值",
        ),
        LineRef(
            doc = requirements,
            quote = "ui/settings/SettingsScreen.kt:446-461",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 446,
            anchor = "if (quietToday) {",
            note = "§4.5 AC-05.5：今天处在安静里时那段状态行与撤销按钮",
        ),
        LineRef(
            doc = requirements,
            quote = "`:467` 的 `quietReceipt?.let { ChangeReceipt(it) }`",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 467,
            anchor = "quietReceipt?.let { ChangeReceipt(it) }",
            note = "§4.5 AC-05.5：撤销回执刻意摆在 if 条件之外，这一条要钉住",
        ),
        // ---- docs/01 §4.13 验收表 ----
        LineRef(
            doc = requirements,
            quote = "SettingsScreen.kt:201-221",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 201,
            anchor = "if (confirmingReset) {",
            note = "§4.13 AC-13.4：清空全部数据那句确认对话框的一整段",
        ),
        LineRef(
            doc = requirements,
            quote = "SettingsScreen.kt:182-185",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 182,
            anchor = "SecondaryWideButton(",
            note = "§4.13 AC-13.5：只读重看的入口按钮（文案取自 SettingsVoice.reviewGuideButton）",
        ),
        // ---- docs/01 §4.14 ----
        LineRef(
            doc = requirements,
            quote = "SettingsVoice.kt:108-112",
            source = "ui/settings/SettingsVoice.kt",
            anchorLine = 110,
            anchor = "const val resetButton: String = \"清空全部数据\"",
            note = "§4.14 AC-14.3：第 71 轮改口径后按钮文案与说明那三句的现值",
        ),
        // ---- docs/03 §8.18 现值表六行 ----
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:111",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 111,
            anchor = "addressReceipt = null",
            note = "§8.18：点保存称呼之后把上一句回执清掉的那一行",
        ),
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:122",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 122,
            anchor = "addressReceipt = SettingsVoice.addressReceipt(editingAddress)",
            note = "§8.18：保存称呼落库成功之后的回执（第 85 轮起由一处收口）",
        ),
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:128",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 128,
            anchor = "addressReceipt?.let { ChangeReceipt(it) }",
            note = "§8.18：称呼回执摆在称呼卡尾注之前",
        ),
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:146",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 146,
            anchor = "SettingsVoice.fontSizeChanged(option.label)",
            note = "§8.18：点某一档字号之后的回执",
        ),
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:165",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 165,
            anchor = "fontReceipt?.let { ChangeReceipt(it) }",
            note = "§8.18：字号回执回到它所属的那张卡里",
        ),
        LineRef(
            doc = design,
            quote = "SettingsScreen.kt:234-241",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 234,
            anchor = "private fun ChangeReceipt(text: String) {",
            note = "§8.18：两处回执共用的那一段（bodyMedium / primary / fillMaxWidth）",
        ),
        // ---- docs/03 §7.9 追溯矩阵的索引行 ----
        LineRef(
            doc = design,
            quote = "ui/settings/SettingsScreen.kt:79/182-185",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 79,
            anchor = "fun SettingsScreen(",
            note = "§7.9 矩阵 AC-13.5 行：函数起点与只读重看入口按钮两端",
        ),

        // ---- docs/01-需求规格说明书.md：§4.3~§4.19 现值回改后的登记 ----
        LineRef(
            doc = requirements,
            quote = "HomeScreen.kt:171-175",
            source = "ui/home/HomeScreen.kt",
            anchorLine = 171,
            anchor = "ActionRow(",
            note = "首页动作行",
        ),
        LineRef(
            doc = requirements,
            quote = "InterventionWindow.kt:120",
            source = "domain/engine/intervention/InterventionWindow.kt",
            anchorLine = 120,
            anchor = "USER_ASKED,",
            note = "触发来源枚举项",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:232-248",
            source = "domain/play/PlayGame.kt",
            anchorLine = 246,
            anchor = "这个类里没有等级",
            note = "找对子轮次等级说明",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:260-268",
            source = "domain/play/PlayGame.kt",
            anchorLine = 267,
            anchor = "所以它是个估时",
            note = "找对子估时说明",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:58",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 58,
            anchor = "const val leaveLabel",
            note = "退出按钮文案",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayScreen.kt:207",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 207,
            anchor = "PrimaryWideButton(text = PlayVoice.leaveLabel",
            note = "退出按钮落位",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayScreen.kt:165-167",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 166,
            anchor = "PlayVoice.switchLabel",
            note = "换一个按钮落位",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:167-173",
            source = "domain/play/PlayGame.kt",
            anchorLine = 170,
            anchor = "if (label == target) return copy",
            note = "顺序轮次点按",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayViewModel.kt:51",
            source = "ui/play/PlayViewModel.kt",
            anchorLine = 51,
            anchor = "data class Finished(",
            note = "一局结束事件",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:78-79",
            source = "domain/play/PlayGame.kt",
            anchorLine = 78,
            anchor = "没有 id、没有时间戳",
            note = "轮次状态不含 id",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:171-172",
            source = "domain/play/PlayGame.kt",
            anchorLine = 172,
            anchor = "ALREADY_DONE else PlayHint.NOT_YET",
            note = "提示分支",
        ),
        LineRef(
            doc = requirements,
            quote = "AppContainer.kt:76",
            source = "core/AppContainer.kt",
            anchorLine = 76,
            anchor = "val clock: AppClock = SystemAppClock,",
            note = "容器时钟构造属性",
        ),
        LineRef(
            doc = requirements,
            quote = "FindPair.kt:29",
            source = "domain/play/FindPair.kt",
            anchorLine = 29,
            anchor = "const val SIDE = 4",
            note = "找对子棋盘边长",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:185-195",
            source = "domain/play/PlayGame.kt",
            anchorLine = 191,
            anchor = "val firstPick: Int? = null,",
            note = "找对子轮次首翻位",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:222-229",
            source = "domain/play/PlayGame.kt",
            anchorLine = 222,
            anchor = "override fun tap(index: Int): FindPairRound {",
            note = "找对子点按实现",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:209-214",
            source = "domain/play/PlayGame.kt",
            anchorLine = 209,
            anchor = "override fun markOf(index: Int): CellMark {",
            note = "找对子格子标记",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:121",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 121,
            anchor = "is MemoryRound ->",
            note = "记忆轮次收尾句分支",
        ),
        LineRef(
            doc = requirements,
            quote = "KindWords.kt:26-35",
            source = "domain/play/KindWords.kt",
            anchorLine = 35,
            anchor = "val all: List<String> = kinds.flatMap",
            note = "暖心话全量派生",
        ),
        LineRef(
            doc = requirements,
            quote = "ui/theme/Dimens.kt:64",
            source = "ui/theme/Dimens.kt",
            anchorLine = 64,
            anchor = "val minTouchGap = 16.dp",
            note = "最小触控间隙",
        ),

        // ---- 以下几条此前只人工核过「对」，第 92 轮纳入机器兜底 ----
        LineRef(
            doc = requirements,
            quote = "SchulteGrid.kt:33",
            source = "domain/play/SchulteGrid.kt",
            anchorLine = 33,
            anchor = "longestSeconds = 180,",
            note = "舒尔特估时上限",
        ),
        LineRef(
            doc = requirements,
            quote = "FindPair.kt:46",
            source = "domain/play/FindPair.kt",
            anchorLine = 46,
            anchor = "longestSeconds = 120,",
            note = "找对子估时上限",
        ),
        LineRef(
            doc = requirements,
            quote = "FindPair.kt:57-65",
            source = "domain/play/FindPair.kt",
            anchorLine = 57,
            anchor = "fun deal(random: Random): FindPairRound {",
            note = "找对子发牌",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayCatalog.kt:41-46",
            source = "domain/play/PlayCatalog.kt",
            anchorLine = 41,
            anchor = "val games: List<PlayGame> =",
            note = "玩法目录清单（七个游戏）",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:49-57",
            source = "domain/play/Memory.kt",
            anchorLine = 49,
            anchor = "data class MemoryRound(",
            note = "记忆轮次数据类",
        ),
        LineRef(
            doc = requirements,
            quote = "SameKind.kt:50",
            source = "domain/play/SameKind.kt",
            anchorLine = 50,
            anchor = "const val KINDS_ON_BOARD = 3",
            note = "同类同屏类别数",
        ),
        LineRef(
            doc = requirements,
            quote = "OddOne.kt:34",
            source = "domain/play/OddOne.kt",
            anchorLine = 34,
            anchor = "const val CELLS = 12",
            note = "找一个不一样：盘上格数",
        ),
        LineRef(
            doc = requirements,
            quote = "OddOne.kt:61-71",
            source = "domain/play/OddOne.kt",
            anchorLine = 61,
            anchor = "fun deal(random: Random): OddOneRound {",
            note = "找一个不一样：发牌",
        ),
        LineRef(
            doc = requirements,
            quote = "OddOne.kt:48",
            source = "domain/play/OddOne.kt",
            anchorLine = 48,
            anchor = "longestSeconds = 60,",
            note = "找一个不一样：估时上限",
        ),
        LineRef(
            doc = requirements,
            quote = "OddOne.kt:112-118",
            source = "domain/play/OddOne.kt",
            anchorLine = 112,
            anchor = "override fun tap(index: Int): OddOneRound {",
            note = "找一个不一样：点击分支",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:123",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 123,
            anchor = "is OddOneRound ->",
            note = "找一个一样的分支落点（完成卡）",
        ),

        // ---- docs/02-概要设计说明书.md：交叉文档那两处现值 ----
        LineRef(
            doc = "docs/02-概要设计说明书.md",
            quote = "ui/home/HomeScreen.kt:171-175",
            source = "ui/home/HomeScreen.kt",
            anchorLine = 171,
            anchor = "ActionRow(",
            note = "首页动作行（带路径）",
        ),
        LineRef(
            doc = "docs/02-概要设计说明书.md",
            quote = "data/intervention/InterventionStore.kt:29",
            source = "data/intervention/InterventionStore.kt",
            anchorLine = 29,
            anchor = "interventionDataStore",
            note = "干预存储 DataStore 属性",
        ),

        // ---- docs/03-详细设计说明书.md：§7.9 / §8.5x 现值与邻域地标 ----
        LineRef(
            doc = design,
            quote = "InterventionStore.kt:29",
            source = "data/intervention/InterventionStore.kt",
            anchorLine = 29,
            anchor = "interventionDataStore",
            note = "干预存储 DataStore 属性",
        ),
        LineRef(
            doc = design,
            quote = "HomeScreen.kt:171-175",
            source = "ui/home/HomeScreen.kt",
            anchorLine = 171,
            anchor = "ActionRow(",
            note = "首页动作行",
        ),
        LineRef(
            doc = design,
            quote = "InterventionWindow.kt:186",
            source = "domain/engine/intervention/InterventionWindow.kt",
            anchorLine = 186,
            anchor = "fun decide(",
            note = "干预窗口判定入口",
        ),
        LineRef(
            doc = design,
            quote = "PlayViewModel.kt:51",
            source = "ui/play/PlayViewModel.kt",
            anchorLine = 51,
            anchor = "data class Finished(",
            note = "一局结束事件",
        ),
        LineRef(
            doc = design,
            quote = "SchulteGrid.kt:23",
            source = "domain/play/SchulteGrid.kt",
            anchorLine = 23,
            anchor = "const val SIDE = 5",
            note = "舒尔特网格边长",
        ),
        LineRef(
            doc = design,
            quote = "MainNav.kt:33",
            source = "ui/nav/MainNav.kt",
            anchorLine = 33,
            anchor = "ONBOARDING_REVIEW",
            note = "回看路由常量",
        ),
        LineRef(
            doc = design,
            quote = "PlayGame.kt:22/25",
            source = "domain/play/PlayGame.kt",
            anchorLine = 22,
            anchor = "ALREADY_DONE(",
            note = "顺序轮次提示枚举",
        ),

        // ---- docs/05-接口设计说明书.md：接口表里的存储地标 ----
        LineRef(
            doc = "docs/05-接口设计说明书.md",
            quote = "data/intervention/InterventionStore.kt:29",
            source = "data/intervention/InterventionStore.kt",
            anchorLine = 29,
            anchor = "interventionDataStore",
            note = "干预存储 DataStore 属性",
        ),

        // ---- docs/03-详细设计说明书.md §7.3 / §7.6：语义挂载表的现行坐标（第 93 轮逐条重核并纳入兜底） ----
        LineRef(
            doc = design,
            quote = "ElderComponents.kt:219",
            source = "ui/common/ElderComponents.kt",
            anchorLine = 219,
            anchor = ".asScreenTitle(titleState),",
            note = "ElderHeader 表头挂 asScreenTitle",
        ),
        LineRef(
            doc = design,
            quote = "ElderComponents.kt:255",
            source = "ui/common/ElderComponents.kt",
            anchorLine = 255,
            anchor = "modifier = Modifier.asSectionTitle(),",
            note = "SectionCard 标题挂 asSectionTitle",
        ),
        LineRef(
            doc = design,
            quote = "ElderComponents.kt:347",
            source = "ui/common/ElderComponents.kt",
            anchorLine = 347,
            anchor = "modifier = Modifier.asSectionTitle(),",
            note = "SectionTitle 挂 asSectionTitle",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:101",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 101,
            anchor = "modifier = Modifier.asSectionTitle(),",
            note = "游戏选择页「挑一个」挂 asSectionTitle（第 93 轮新增）",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:178",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 178,
            anchor = "modifier = Modifier.announcesWhenItChanges(),",
            note = "题面播报区",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:189",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 189,
            anchor = "modifier = Modifier.announcesWhenItChanges(),",
            note = "提示行播报区",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:203",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 203,
            anchor = "modifier = Modifier.announcesWhenItChanges(),",
            note = "结束语播报区",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:231",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 231,
            anchor = "modifier = Modifier.gridOf(rows = rows, columns = game.columns),",
            note = "棋盘 Column 挂 gridOf",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:293",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 293,
            anchor = "modifier = modifier.gridCell(row = row, column = column),",
            note = "单个格子挂 gridCell",
        ),
        LineRef(
            doc = design,
            quote = "PlayScreen.kt:185-190",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 186,
            anchor = "text = PlayVoice.hint(state.round).orEmpty(),",
            note = "提示行那个 Text（始终占着位置的那段区间）",
        ),

        // ---- docs/09-用户手册.md：选择页目录循环与卡上文字（第 93 轮首次纳入兜底） ----
        LineRef(
            doc = manual,
            quote = "PlayScreen.kt:104-105",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 104,
            anchor = "for (game in games) {",
            note = "选择页目录循环：一张卡一个目录项",
        ),
        LineRef(
            doc = manual,
            quote = "PlayScreen.kt:127-128",
            source = "ui/play/PlayScreen.kt",
            anchorLine = 128,
            anchor = "text = game.name,",
            note = "卡片上只有游戏名",
        ),

        // ---- docs/01-需求规格说明书.md §4 / NFR 表里**带目录前缀**的当前态行号引用（第 97 轮首次纳入兜底） ----
        LineRef(
            doc = requirements,
            quote = "domain/companion/CopyRules.kt:30-33",
            source = "domain/companion/CopyRules.kt",
            anchorLine = 30,
            anchor = "val spendWords: List<String> = listOf(",
            note = "§4.12 AC-08.3：要花钱的那一类词唯一一张表",
        ),
        LineRef(
            doc = requirements,
            quote = "domain/companion/CopyRules.kt:25-27",
            source = "domain/companion/CopyRules.kt",
            anchorLine = 25,
            anchor = "val streakWords: List<String> = listOf(",
            note = "§4.14 AC-10.2：累计/排行/施压那一类词唯一一张表",
        ),
        LineRef(
            doc = requirements,
            quote = "ui/home/InterventionVoice.kt:22-24",
            source = "ui/home/InterventionVoice.kt",
            anchorLine = 22,
            anchor = "顶上那两句话下面的一行补充",
            note = "§4.9 边界：安静里首页不显示锚点行，理由在这段 KDoc",
        ),
        LineRef(
            doc = requirements,
            quote = "ui/talk/TalkScreen.kt:161",
            source = "ui/talk/TalkScreen.kt",
            anchorLine = 161,
            anchor = "跟着滚动一起走的内容列",
            note = "AC-11.1 复测：宣称词命中的是 KDoc 注释，不是用户可见文案",
        ),
        LineRef(
            doc = requirements,
            quote = "domain/play/CardWords.kt:14",
            source = "domain/play/CardWords.kt",
            anchorLine = 14,
            anchor = "都是这个年纪的人一眼认得的日常物件",
            note = "AC-11.1 复测：同上，命中的是 KDoc 注释",
        ),
        LineRef(
            doc = requirements,
            quote = "ui/nav/MainNav.kt:33",
            source = "ui/nav/MainNav.kt",
            anchorLine = 33,
            anchor = "const val ONBOARDING_REVIEW",
            note = "§4.16 AC-13.5：只读重看的目的地路由常量",
        ),
        LineRef(
            doc = requirements,
            quote = "domain/engine/intent/IntentScan.kt:54",
            source = "domain/engine/intent/IntentScan.kt",
            anchorLine = 54,
            anchor = "这个类所在的包里不出现",
            note = "NFR-10：全仓网络关键字唯一一处命中，是它自己的 KDoc",
        ),
        LineRef(
            doc = requirements,
            quote = "IntentScan.kt:54",
            source = "domain/engine/intent/IntentScan.kt",
            anchorLine = 54,
            anchor = "这个类所在的包里不出现",
            note = "§4.6 AC-06.1：同一处注释的纯粹写法（不带路径前缀），第 97 轮一并纳入",
        ),
        // ---- 正文区纯粹写法全覆盖：第 104 轮纳入兜底的 27 条（最后一条是新用例自查抓出来的） ----
        LineRef(
            doc = requirements,
            quote = "InterventionWindow.kt:186-193",
            source = "domain/engine/intervention/InterventionWindow.kt",
            anchorLine = 186,
            anchor = "fun decide(",
            note = "§4.4 AC-04.4：作息推断的决策函数签名，正文区现值引用（此前只登记了带前缀写法），第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "InterventionWindow.kt:109",
            source = "domain/engine/intervention/InterventionWindow.kt",
            anchorLine = 109,
            anchor = "ASSUMED_COLLECT_OFF,",
            note = "§4.4 AC-04.4：关掉提醒时假设的收集时段常量，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "StarterOptions.kt:419",
            source = "domain/companion/StarterOptions.kt",
            anchorLine = 419,
            anchor = "CopyRules.violations(text).forEach",
            note = "§4.8 AC-08.3：起点选项逐条过文案规则那道循环，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "StarterCard.kt:127",
            source = "ui/talk/StarterCard.kt",
            anchorLine = 127,
            anchor = "recorded != null -> QuietNote(text = CompanionVoice.anchorAlreadyLine(recorded.slot))",
            note = "§4.9 AC-09.4：已记过的槽位改用那句安静提示，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "TalkInputTest.kt:175",
            source = "ui/talk/TalkInputTest.kt",
            anchorLine = 175,
            anchor = "assertEquals(1, count(\"好，这件事就不用常伴记着了。\"))",
            note = "§4.9 AC-09.5：设备层用例断言「记下之后」的那句回话，第 104 轮纳入兜底（这条落在 androidTest）",
            root = "androidTest",
        ),
        LineRef(
            doc = requirements,
            quote = "Encouragement.kt:50",
            source = "domain/companion/Encouragement.kt",
            anchorLine = 50,
            anchor = "found += CopyRules.violations(text)",
            note = "§4.10 AC-10.2：鼓励语也要过一遍文案规则，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "CopyRules.kt:30-33",
            source = "domain/companion/CopyRules.kt",
            anchorLine = 30,
            anchor = "val spendWords: List<String> = listOf(",
            note = "§4.10 AC-10.3：文案规则里那份「不引导消费」的词表，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:68-95",
            source = "domain/play/PlayGame.kt",
            anchorLine = 75,
            anchor = "没有「错了几次」「扣了几分」「用了多久」",
            note = "§4.11 AC-11.2：脑力互动一律不记分、不比较、不下结论那段说明，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:78",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 78,
            anchor = "一局多久由用户说了算",
            note = "§4.11 AC-11.3：「一局多久由用户说了算」那句，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:52",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 52,
            anchor = "见 [PlayViewModel.chooseAgain]",
            note = "§4.11 AC-11.3：再来一局指向 ViewModel 的说明，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:22",
            source = "domain/play/PlayGame.kt",
            anchorLine = 22,
            anchor = "ALREADY_DONE(\"这个已经点过了，换一个\"),",
            note = "§4.12 AC-12.3：重复点已完成项的温和提示语，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "OnboardingScreen.kt:140",
            source = "ui/onboard/OnboardingScreen.kt",
            anchorLine = 140,
            anchor = "OutlinedTextField(",
            note = "§4.13 AC-13.5：只读重看引导页上的那个输入框，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "SettingsScreenTest.kt:342",
            source = "ui/settings/SettingsScreenTest.kt",
            anchorLine = 342,
            anchor = "compose.onNode(hasSetTextAction()).performTextInput(NEW_ADDRESS)",
            note = "§4.13 AC-13.6：设备层用例清空称呼框的输入动作，第 104 轮纳入兜底（这条落在 androidTest）",
            root = "androidTest",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:119",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 119,
            anchor = "is FindPairRound -> \"那两张",
            note = "§4.15 AC-15.3：「找一对」那一轮的开场文案（正文区旧行号已改成现值），第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:170",
            source = "domain/play/Memory.kt",
            anchorLine = 170,
            anchor = "const val PAIRS = 6",
            note = "§4.17 AC-17.1：记忆翻牌的对数常量，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:205-208",
            source = "domain/play/Memory.kt",
            anchorLine = 205,
            anchor = "fun deal(random: Random): MemoryRound {",
            note = "§4.17 AC-17.1：发牌函数，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:65",
            source = "domain/play/Memory.kt",
            anchorLine = 65,
            anchor = "val pairs: Int get() = cells.size / 2",
            note = "§4.17 AC-17.1：按格子数推出对数，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:120-147",
            source = "domain/play/Memory.kt",
            anchorLine = 120,
            anchor = "override fun tap(index: Int): MemoryRound {",
            note = "§4.17 AC-17.2：翻牌那一下的处理，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:36",
            source = "domain/play/PlayGame.kt",
            anchorLine = 36,
            anchor = "MISSED_PAIR(\"不是一对，点一下接着找\"),",
            note = "§4.17 AC-17.2：两张配不上时的温和提示语，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:15-31",
            source = "domain/play/Memory.kt",
            anchorLine = 15,
            anchor = "sealed interface MemoryOpening {",
            note = "§4.17 AC-17.2：记忆翻牌的开局形态，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "Memory.kt:190",
            source = "domain/play/Memory.kt",
            anchorLine = 190,
            anchor = "longestSeconds = 180,",
            note = "§4.17 AC-17.3：记忆翻牌的最长时长上限，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:136",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 136,
            anchor = "is FindPairRound, is SubtractRound, is MemoryRound, is SameKindRound, is OddOneRound ->",
            note = "§4.17 AC-17.3：几种轮次共用那句小字（正文区旧行号已改成现值），第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "SameKind.kt:71-82",
            source = "domain/play/SameKind.kt",
            anchorLine = 71,
            anchor = "fun deal(random: Random): SameKindRound {",
            note = "§4.18 AC-18.1：同类归放的发牌函数，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "SameKind.kt:125-131",
            source = "domain/play/SameKind.kt",
            anchorLine = 125,
            anchor = "override fun tap(index: Int): SameKindRound {",
            note = "§4.18 AC-18.2：同类归放的点选处理，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "SameKind.kt:61",
            source = "domain/play/SameKind.kt",
            anchorLine = 61,
            anchor = "longestSeconds = 120,",
            note = "§4.18 AC-18.3：同类归放的最长时长上限，第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:25",
            source = "domain/play/PlayGame.kt",
            anchorLine = 25,
            anchor = "NOT_YET(\"这个还在后面，不急\"),",
            note = "§4.12 AC-12.3：还没到那一步的提示语（与上一串同在 AC-12.3 那一行），第 104 轮纳入兜底",
        ),
        LineRef(
            doc = requirements,
            quote = "SettingsScreen.kt:122",
            source = "ui/settings/SettingsScreen.kt",
            anchorLine = 122,
            anchor = "addressReceipt = SettingsVoice.addressReceipt(editingAddress)",
            note = "§4.13 AC-13.6：回执收口处只把 editingAddress 交给它（第 104 轮新用例自查抓出来的第 27 条）",
        ),
        // ---- docs/01 §4.20 FR-20 倒着点：第 148 轮新增的十条（本轮第一次引到单元测试源码） ----
        LineRef(
            doc = requirements,
            quote = "ReverseGrid.kt:37",
            source = "domain/play/ReverseGrid.kt",
            anchorLine = 37,
            anchor = "const val ID = \"countdown-5x5\"",
            note = "§4.20 AC-20.1：倒着点的 gameId（与 SIDE = 5 一起决定盘面尺寸）",
        ),
        LineRef(
            doc = requirements,
            quote = "ReverseGrid.kt:26-33",
            source = "domain/play/ReverseGrid.kt",
            anchorLine = 26,
            anchor = "三条边界跟舒尔特那三条是同一套",
            note = "§4.20 AC-20.2：三条边界（点错只提示、点过的只说一句、中途退出不留痕迹）写在注释里",
        ),
        LineRef(
            doc = requirements,
            quote = "ReverseGrid.kt:50",
            source = "domain/play/ReverseGrid.kt",
            anchorLine = 50,
            anchor = "longestSeconds = 180,",
            note = "§4.20 AC-20.3：单局估时 180 秒，规则页那句话由 `PlayVoice.promise` 按它算出来",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:127",
            source = "domain/play/PlayGame.kt",
            anchorLine = 127,
            anchor = "data class OrderRound(",
            note = "§4.20 AC-20.2：与舒尔特方格同一个 `data class`，倒着点只换 `order` 的方向",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:127-135",
            source = "domain/play/PlayGame.kt",
            anchorLine = 127,
            anchor = "data class OrderRound(",
            note = "§4.20 AC-20.2/AC-20.3：`OrderRound` 的五个字段全在这一段里，没有错误计数字段、也没有时刻字段",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:170",
            source = "domain/play/PlayGame.kt",
            anchorLine = 170,
            anchor = "copy(step = step + 1, hint = PlayHint.NONE)",
            note = "§4.20 AC-20.2：点对这一格时的唯一动作——步数加一、提示清空（倒着点与舒尔特共用这一行）",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayGame.kt:155-158",
            source = "domain/play/PlayGame.kt",
            anchorLine = 155,
            anchor = "override fun markOf(index: Int): CellMark {",
            note = "§4.20 AC-20.2：`markOf` 只回 `DONE` 或 `PLAIN`，刻意不用 `PICKED`",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:81-82",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 81,
            anchor = "fun promise(game: PlayGame): String =",
            note = "§4.20 AC-20.3：规则页那句「一局大概 X 分钟」由它按 `longestSeconds` 算，照实说成「大概」",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayVoice.kt:118",
            source = "ui/play/PlayVoice.kt",
            anchorLine = 118,
            anchor = "个都点到了。这一局用了",
            note = "§4.20 AC-20.3：倒着点复用 `OrderRound` ⇒ 完成卡**报用时**，与找一样的等五局不同",
        ),
        LineRef(
            doc = requirements,
            quote = "PlayCatalogTest.kt:16",
            source = "domain/play/PlayCatalogTest.kt",
            anchorLine = 16,
            anchor = "目录里现在有七条",
            note = "§4.20 AC-20.4：本轮只改了这句注释里的条目数（六条 → 七条），断言一条未改",
            root = "test",
        ),
    )

    /**
     * 登记表下限（第 97 轮由 61 抬到 69，第 104 轮由 69 抬到 95，第 144 轮由 95 抬到 100，
     * 第 148 轮由 100 抬到 110）：
     * 第 104 轮新增 27 条「正文区纯粹文件名写法」兜底（第 27 条是新用例自己抓出来的 `SettingsScreen.kt:122`），
     * 同时删掉 1 条因 `docs/01` 把 `PlayGame.kt:22/25` 拆成两条独立写法而失效的旧条目，故净增 26。
     * 第 144 轮净增 5 条：`PlayCatalog.kt:35-39` 随 `games` 区间右移改写 1 条，加上第六个游戏
     * `OddOne.kt` 的 4 条、`PlayVoice.kt:123` 的 1 条，合计 +5（改写那条不增减计数）。
     * 第 148 轮净增 10 条：`docs/01` 新增 §4.20 FR-20（第七个游戏「倒着点」）引到的
     * `ReverseGrid.kt` 3 条、`PlayGame.kt` 4 条、`PlayVoice.kt` 2 条，加上第一条落在
     * `app/src/test/java` 下的 `PlayCatalogTest.kt:16`（`root = "test"`）。
     * 删条目就必须同时改这里，不许无声缩水。
     */
    private val registeredRefFloor = 110

    /**
     * 纯粹写法：文件名里**不带路径分隔符**，只有 `文件.kt:` 加一个数或一段区间。
     * 带 `ui/settings/` 这类前缀、或用 `/` 串多段的写法按设计不参与第 3 条用例（见类注释）。
     */
    private val strictQuote = Regex("^[A-Za-z0-9]+\\.kt:(\\d+)(?:-(\\d+))?$")

    /**
     * 带目录前缀的写法：`路径/文件.kt:` 加一个数或一段区间（路径里至少一段 `/`）。
     * 由第 4 条用例 [everyPathPrefixedDocLineRefAgreesWithItsAnchorLine] 管辖；`79/182-185`
     * 这类**多段串写**不命中本正则，按设计仍在管辖之外（见类注释）。
     */
    private val prefixedQuote = Regex("^([A-Za-z0-9_]+(?:/[A-Za-z0-9_]+)+)\\.kt:(\\d+)(?:-(\\d+))?$")

    /**
     * [prefixedQuote] 的**不锚定**同族：用以在 `docs/01` 正文区里抓出**行内出现的**带前缀行号
     * （一串可能在句子中间、前后还有别的字）。由第 5 条用例
     * [everyPathPrefixedRefInTheBodyIsRegistered] 管「正文区有没有漏网的」。
     */
    private val anyPrefixedRef = Regex("[A-Za-z0-9_]+(?:/[A-Za-z0-9_]+)+\\.kt:\\d+(?:-\\d+)?")

    /**
     * [strictQuote] 的**不锚定**同族：用以在 `docs/01` 正文区里抓出**行内出现的**纯粹文件名行号。
     * 由第 6 条用例 [everyFileNameOnlyRefInTheBodyIsRegistered] 管「正文区有没有漏网的」。
     *
     * 前面的否定后顾 `(?<![A-Za-z0-9_/])` 是这一族的关键：没有它，`ui/play/PlayVoice.kt:50` 这种
     * **带前缀**写法会被从 `PlayVoice` 处切成一条纯粹写法，与 [anyPrefixedRef] 撞车（同族重叠计数）。
     */
    private val anyFileNameOnlyRef = Regex("(?<![A-Za-z0-9_/])[A-Za-z0-9]+\\.kt:\\d+(?:-\\d+)?")

    /**
     * `/` 串多段的写法（`PlayCatalog.kt:88/111/125` 这一种）：第 104 轮起**禁止**在正文区出现。
     * 它既不是纯粹写法也不是带目录前缀写法，两族正则都抓不住它，于是同一个文件被拆成一串行号时
     * 谁也守不到——第 6 条用例用一个「上限 0」的断言把它挡在正文区之外。
     */
    private val anySlashJoinedRef = Regex("[A-Za-z0-9]+\\.kt:\\d+(?:-\\d+)?(?:/\\d+(?:-\\d+)?)+")

    /**
     * 纯粹写法条目下限（第 97 轮由 52 抬到 53；第 144 轮由 53 抬到 85——第 98 ~ 143 轮新登记的
     * 纯粹写法条目一直没同步抬，本轮按实测一并抬齐；第 148 轮由 85 抬到 95，本轮新登记的
     * 10 条全是纯粹写法）：把登记表里的纯数字写法删掉几条就得同时改这里。
     */
    private val strictRefFloor = 95

    /** 带目录前缀写法条目下限（第 97 轮新增，现 12 条）：删这类条目就得同时改这里。 */
    private val prefixedRefFloor = 12

    /**
     * `docs/01` **正文区**（第 1 行起、到 `## 7.` 那个标题之前）里带目录前缀引用的**命中串数下限**
     * （第 102 轮新增，实测 9 串，取 9 与实测齐平）：删掉正文区里一条现值引用、正则失效、
     * 或正文区被整段换走，都会当场红——防的是「零命中 ⇒ 全部通过」的假绿。
     */
    private val bodyPrefixedRefFloor = 9

    /**
     * `docs/01` **正文区**里纯粹文件名引用的**命中串数下限**（第 104 轮新增，实测 56 串，取 56 与
     * 实测齐平；第 144 轮重测 61 串，下限随之上抬到 61；第 148 轮重测 71 串，下限随之上抬到 71
     * ——§4.20 FR-20 那十五条行号引用整段都在正文区里，全是纯粹文件名写法）：与 [bodyPrefixedRefFloor]
     * 同一个理由——正则写歪、正文区被整段换走，都会退化成「零命中 ⇒ 一律通过」，这里用下限把那种假绿挡住。
     */
    private val bodyFileNameOnlyRefFloor = 71

    /**
     * 第 7 条 [everyLineRefEitherPointsInsideItsFileOrIsExplicitlyExempt] 用的自由形态：抓
     * `文件.kt:行号`（带目录前缀、不带前缀、`/` 串多段都算，前面那一段路径不参与判读），
     * 与 [anyPrefixedRef] / [anyFileNameOnlyRef] 的区别是**不以登记表为口径**——先一股脑抓出来，
     * 再拿 `app/src` 下同名源码文件的**实际行数**去比。
     */
    private val anyLineRefForBoundCheck = Regex("[A-Za-z0-9_]+\\.kt:\\d+(?:\\s*/\\s*\\d+)*")

    /** 第 7 条：普查对象的份数下限（`docs/` 与 `records/` 下所有 `.md` 加 `README.md`，现值 16）。 */
    private val lineRefScanFileFloor = 16

    /** 第 7 条：`app/src` 下 `.kt` 文件数下限（现值 165）：索引被走空 ⇒ 一律「查无此文件」⇒ 假绿。 */
    private val lineRefBoundSourceFloor = 160

    /**
     * 第 7 条：命中我方源码的引用**处数**下限（第 105 轮实测 2,403 处，取值 2,380 留一截余量）。
     *
     * 「处」是**一段 `文件.kt:行号` 写法**，与下面那条「串数」是两个量：2,403 处里有若干处写成
     * `A.kt:12/13/14` 这种多段形式，展开后是 2,623 个行号（那 2,623 不单独设下限，理由是它随写法
     * 的粗细变动比「处数」大；「处数」＋「不同串数」已经能同时挡住正则写歪与扫描目录走空）。
     * 与 [lineRefBoundDistinctFloor] 一起挡住「正则写歪 / 扫描目录被换 ⇒ 零命中 ⇒ 一律通过」。
     */
    private val lineRefBoundResolvedFloor = 2380

    /** 第 7 条：互不相同的「文件名:行号」**串数**下限（第 105 轮实测 585 个，取 580；先前误记为 599，本轮用同一正则复算改正）。 */
    private val lineRefBoundDistinctFloor = 580

    /**
     * 第 7 条里**允许越界**的那些引用：键是 `文件名:行号`，值是「为什么它越界仍然是正当的」。
     *
     * 第 105 轮把 16 份普查对象里的引用全量过了一遍（2,548 处），越界的只有 41 处 / 5 对，
     * 逐处看过上下文后判定**全部正当、没有活缺陷**，就这 5 条：三类是**当轮当时的读数**（历史叙述 /
     * 逐字引用的编译器报错），两类是**变异实验里故意写进去的对照值**。它们越界是刻意的——
     * 按 `records/03` ADR-070，历史叙述与逐字引文**不回改**；变异例是不可执行的对照字符串。
     *
     * 这张表是**双向**判据：有越界没记进来 ⇒ 红；这里记着的某条不再越界了（文件长大了 / 引用被改了）⇒ 也红，
     * 逼着人来重新判定一次，而不是让一条过期豁免悄悄躺在那儿。
     */
    private val lineRefOverflowExempt = mapOf(
        "StarterOptions.kt:435" to "第 12 轮的历史叙述（当时该文件 444 行；现值 424 行）。" +
            "写的是「字段已删除，`StarterOptions.kt:435` 改为 checkWords(...)」——当轮快照，按 ADR-070 不回改",
        "PlayStaysClocklessTest.kt:100" to "逐字引用的编译器报错（现值的第 100 行就是 EOF，文件 99 行）；" +
            "报错原文写的是 `PlayStaysClocklessTest.kt:100 Unclosed comment`，引文一个字不能动",
        "Dimens.kt:999" to "第 97 轮 M4 对照变异例：往 docs/01 末尾追加没登记过的 `ui/theme/Dimens.kt:999`，" +
            "用来坐实「那时全绿 ⇒ 盲区」这一步，是**故意**越界的对照字符串",
        "Anchor.kt:82" to "第 101 轮变异 m1 的逐字读数 `Anchor.kt:82 r101ZeroCallProbe`：" +
            "当时往文件末尾追加一行探针后的实测行号，属当轮快照",
        "PlayCatalog.kt:999" to "第 104 轮反向变异 M1 的例：正文区插一串 `PlayCatalog.kt:999` 验第 6 条会不会红，" +
            "同样是对照字符串",
        "DocSkeletonGuardTest.kt:768" to "逐字引用的编译器报错（现值 767 行，报错当时整份文件到文件末都成了注释，" +
            "第 768 行就是 EOF）：报错原文写的是 `DocSkeletonGuardTest.kt:768:1 Unclosed comment`，引文一个字不能动",
    )

    /**
     * 每个登记到的源码文件的行数下限：文件被换成壳子时，地标检查会「恰好落空」而不是正常通过。
     * 下限一律取比登记当年的现值低一截的整数（现值见 `docs/03` §8.55 的表），只求「文件还是原来那个量级」。
     */
    private val sourceLineFloor = mapOf(
        "ui/common/ElderComponents.kt" to 300,
        "ui/settings/SettingsScreen.kt" to 400,
        "ui/settings/SettingsVoice.kt" to 350,
        "ui/home/HomeScreen.kt" to 150,
        "domain/engine/intervention/InterventionWindow.kt" to 200,
        "domain/play/PlayGame.kt" to 200,
        "ui/play/PlayVoice.kt" to 120,
        "ui/play/PlayScreen.kt" to 230,
        "ui/play/PlayViewModel.kt" to 120,
        "core/AppContainer.kt" to 150,
        "domain/play/FindPair.kt" to 50,
        "domain/play/KindWords.kt" to 30,
        "domain/play/SchulteGrid.kt" to 35,
        "domain/play/PlayCatalog.kt" to 41,
        "domain/play/Memory.kt" to 160,
        "domain/play/SameKind.kt" to 100,
        "domain/play/OddOne.kt" to 100,
        "ui/theme/Dimens.kt" to 60,
        "data/intervention/InterventionStore.kt" to 100,
        "ui/nav/MainNav.kt" to 70,
        "domain/companion/CopyRules.kt" to 60,
        "ui/home/InterventionVoice.kt" to 130,
        "ui/talk/TalkScreen.kt" to 400,
        "domain/play/CardWords.kt" to 25,
        "domain/engine/intent/IntentScan.kt" to 180,
        // ---- 第 104 轮新增：正文区纯粹写法引到的 6 个文件下限 ----
        // 底下两把 key 是 `app/src/androidTest/java` 下的设备层用例，不是 main 源码、行数也不在
        // `docs/03` §8.55 那张表里；它们的现值见 `docs/03` §8.65 的说明，下限同样取低一截。
        "domain/companion/StarterOptions.kt" to 380,
        "domain/companion/Encouragement.kt" to 40,
        "ui/talk/StarterCard.kt" to 120,
        "ui/onboard/OnboardingScreen.kt" to 250,
        "ui/settings/SettingsScreenTest.kt" to 1000,
        "ui/talk/TalkInputTest.kt" to 200,
        // ---- 第 148 轮新增：`docs/01` §4.20 FR-20 第一次引到「倒着点」自己的文件，
        // 也第一次引到 `app/src/test/java` 下的单元测试（`root = "test"`）。两把 key 的现值
        // 分别是 67 行与 97 行，下限同样取低一截；`domain/play/PlayCatalogTest.kt` 不在
        // `docs/03` §8.55 那张表里（那张表只收 main 源码），下限按「还是原来那个量级」取 80。
        "domain/play/ReverseGrid.kt" to 40,
        "domain/play/PlayCatalogTest.kt" to 80,
    )

    @Test
    fun everyRegisteredDocLineRefPointsAtItsAnchor() {
        assertTrue(
            "登记表只剩 ${refs.size} 条，低于下限 $registeredRefFloor：是删了条目还是忘了更新下限？",
            refs.size >= registeredRefFloor,
        )

        val failures = mutableListOf<String>()
        val seen = mutableMapOf<String, MutableList<LineRef>>()
        for (ref in refs) {
            seen.getOrPut(ref.source) { mutableListOf() }.add(ref)

            val file = File(sourceRootFor(ref.root), ref.source)
            if (!file.isFile) {
                failures += "${ref.source} 不存在（${ref.note}）"
                continue
            }
            val lines = file.readLines()
            val floor = sourceLineFloor[ref.source]
            if (floor != null && lines.size < floor) {
                failures += "${ref.source} 只剩 ${lines.size} 行，低于下限 $floor：地标检查会整片落空"
            }
            if (ref.anchorLine !in 1..lines.size) {
                failures += "${ref.source}:${ref.anchorLine} 超出该文件的行数（${lines.size} 行）——${ref.note}"
                continue
            }
            val actual = lines[ref.anchorLine - 1]
            if (!actual.contains(ref.anchor)) {
                failures += "${ref.source}:${ref.anchorLine} 上找不到「${ref.anchor}」" +
                    "（文档 ${ref.doc} 写的是「${ref.quote}」），该行实际是：${actual.trim()}"
            }
        }

        assertEquals(
            "登记表里有重复条目（同一份文档、同一串）：${refs.size} 条登记只有 ${refs.distinct().size} 条互不相同",
            refs.size,
            refs.distinct().size,
        )
        for (source in sourceLineFloor.keys) {
            assertTrue(
                "$source 在 `sourceLineFloor` 里有下限，却没有任何登记条目引到它：" +
                    "这个下限等于没写——要么把条目补上，要么把这一行删掉",
                source in seen,
            )
        }
        assertTrue(
            "地标检查失败 ${failures.size} 条：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun everyRegisteredDocLineRefIsActuallyQuotedInTheDocument() {
        val failures = mutableListOf<String>()
        val cache = mutableMapOf<String, String>()
        for (ref in refs) {
            val docFile = File(repoRoot, ref.doc)
            if (!docFile.isFile) {
                failures += "${ref.doc} 不存在"
                continue
            }
            val text = cache.getOrPut(ref.doc) { docFile.readText() }
            if (!text.contains(ref.quote)) {
                failures += "${ref.doc} 里找不到「${ref.quote}」（${ref.note}）：" +
                    "行号被改掉了？还是这串挪到别的行上了？改完文档要同步改登记表"
            }
        }
        assertTrue(
            "文档逐字引用检查失败 ${failures.size} 条：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun everyStrictlyWrittenDocLineRefAgreesWithItsAnchorLine() {
        val strict = refs.filter { strictQuote.matches(it.quote) }
        assertTrue(
            "纯粹写法条目只剩 ${strict.size} 条，低于下限 $strictRefFloor：" +
                "把行号写成别的形状就不会被这条用例看见了，不许无声缩水",
            strict.size >= strictRefFloor,
        )

        val failures = mutableListOf<String>()
        for (ref in strict) {
            val match = strictQuote.matchEntire(ref.quote) ?: continue
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].ifEmpty { match.groupValues[1] }.toInt()
            if (ref.anchorLine !in start..end) {
                failures += "${ref.doc} 写的是「${ref.quote}」，而地标落在 ${ref.source}:" +
                    "${ref.anchorLine}——文档里那个数与代码里那一行对不上（${ref.note}）"
            }
        }
        assertTrue(
            "文档行号与地标行号不一致 ${failures.size} 条：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun everyPathPrefixedDocLineRefAgreesWithItsAnchorLine() {
        val prefixed = refs.filter { prefixedQuote.matches(it.quote) }
        assertTrue(
            "带目录前缀写法条目只剩 ${prefixed.size} 条，低于下限 $prefixedRefFloor：" +
                "把路径前缀去掉、或写成别的形状就不会被这条用例看见了，不许无声缩水",
            prefixed.size >= prefixedRefFloor,
        )

        val failures = mutableListOf<String>()
        for (ref in prefixed) {
            val match = prefixedQuote.matchEntire(ref.quote) ?: continue
            val path = match.groupValues[1] + ".kt"
            if (path != ref.source) {
                failures += "${ref.doc} 写的是「${ref.quote}」，路径指向 $path，而地标登记在 " +
                    "${ref.source} 上——文档里那条路径与代码里那个文件对不上（${ref.note}）"
                continue
            }
            val start = match.groupValues[2].toInt()
            val end = match.groupValues[3].ifEmpty { match.groupValues[2] }.toInt()
            if (ref.anchorLine !in start..end) {
                failures += "${ref.doc} 写的是「${ref.quote}」，而地标落在 ${ref.source}:" +
                    "${ref.anchorLine}——文档里那个数与代码里那一行对不上（${ref.note}）"
            }
        }
        assertTrue(
            "文档带前缀行号与地标不一致 ${failures.size} 条：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /**
     * 第 5 条（**第 102 轮新增**）：`docs/01` **正文区**里出现的每一串带目录前缀的行号，
     * 都必须在 [refs] 里有一条**逐字**登记。
     *
     * 第 1~4 条问的是「登记了的那几条还对不对」；这一条反过来问「正文里有没有漏网的」——第 97 轮
     * 那版只有前四条，往正文里添一串带前缀、没同步加登记，四条里没有一条会红（第 102 轮反向变异实测）。
     *
     * **管辖边界**：只扫第一个匹配 `^## 7.` 的那一行**之前**的正文；`## 7.` 起的变更史、追溯矩阵与
     * §9 的 GAP 清单**不在管辖内**——那部分写的是「当时这么写」（历史叙述）或缺口原文（`~~…~~` 里
     * 已删的文件），按 `records/03` ADR-070 原样留、不核。边界行现读现求，不硬编码行号（文档会长）。
     */
    @Test
    fun everyPathPrefixedRefInTheBodyIsRegistered() {
        val traceHeading = Regex("^##\\s*7\\.")
        val lines = File(repoRoot, requirements).readText().lines()
        val boundary = lines.indexOfFirst { traceHeading.containsMatchIn(it) }
        assertTrue(
            "$requirements 里找不到以 `## 7.` 起头的标题，正文区与追溯矩阵的分界没了；" +
                "边界不许硬编码行号，这条用例只能当场失败而不是默默扫全文",
            boundary > 0,
        )
        val body = lines.subList(0, boundary)

        val hits = LinkedHashMap<String, Int>()
        for ((offset, line) in body.withIndex()) {
            for (hit in anyPrefixedRef.findAll(line)) {
                if (!hits.containsKey(hit.value)) hits[hit.value] = offset + 1
            }
        }
        assertTrue(
            "$requirements 正文区（第 1 ~ $boundary 行）里只找到 ${hits.size} 串带目录前缀的行号，" +
                "低于下限 $bodyPrefixedRefFloor（现值 9）：引用被删走、正则失效、或正文区被整段换掉，" +
                "这条用例都会退化成「零命中 ⇒ 一律通过」——不许这么绿",
            hits.size >= bodyPrefixedRefFloor,
        )

        val registered = refs.filter { it.doc == requirements }.map { it.quote }.toSet()
        val missing = hits.entries.filter { it.key !in registered }
        assertTrue(
            "$requirements 正文区里出现 ${missing.size} 串带目录前缀的行号，登记表里没有对应条目：\n" +
                missing.joinToString("\n") { "  第 ${it.value} 行「${it.key}」" } +
                "\n正文区里的带前缀引用一律要登记（见类注释「带前缀引用的覆盖面」）；" +
                "若那一串确实属于历史叙述，它就该待在 `## 7.` 之后，而不是正文里",
            missing.isEmpty(),
        )
    }

    /**
     * 第 6 条（**第 104 轮新增**）：`docs/01` **正文区**里出现的每一串**纯粹文件名**写法
     * （`文件.kt:` 加一个数或一段区间，不带目录前缀、也不用 `/` 串多段）都必须在 [refs] 里有一条**逐字**登记。
     *
     * 第 5 条那一版只管「带目录前缀」那一类；往正文里添一串**纯粹文件名**、没同步加登记，五条里没有一条会红
     * （第 104 轮反向变异实测）。这一条把那半边补上——正文区出现过的每一串行号都得有账。
     *
     * **管辖边界**：只扫第一个匹配 `^## 7.` 的那一行**之前**的正文，与第 5 条同一把尺子；`## 7.` 起的
     * 变更史、追溯矩阵与 §9 的 GAP 清单不在管辖内。边界行现读现求，不硬编码行号（文档会长）。
     *
     * **正文区禁止 `/` 串多段**：同一文件的多段写法（形如 `Memory.kt:170/205-208/65`）从第 104 轮起不许
     * 待在正文区——它既落不进「带目录前缀」也落不进「纯粹文件名」，等于两半边都漏。要引用多段就拆成互不
     * 相同的单条引用、各自登记，所以这一条末尾补了一个正向断言，把 `/` 串多段的写法当场顶回去。
     */
    @Test
    fun everyFileNameOnlyRefInTheBodyIsRegistered() {
        val traceHeading = Regex("^##\\s*7\\.")
        val lines = File(repoRoot, requirements).readText().lines()
        val boundary = lines.indexOfFirst { traceHeading.containsMatchIn(it) }
        assertTrue(
            "$requirements 里找不到以 `## 7.` 起头的标题，正文区与追溯矩阵的分界没了；" +
                "边界不许硬编码行号，这条用例只能当场失败而不是默默扫全文",
            boundary > 0,
        )
        val body = lines.subList(0, boundary)

        val hits = LinkedHashMap<String, Int>()
        for ((offset, line) in body.withIndex()) {
            for (hit in anyFileNameOnlyRef.findAll(line)) {
                if (!hits.containsKey(hit.value)) hits[hit.value] = offset + 1
            }
        }
        assertTrue(
            "$requirements 正文区（第 1 ~ $boundary 行）里只找到 ${hits.size} 串纯粹文件名写法，" +
                "低于下限 $bodyFileNameOnlyRefFloor（现值 56）：引用被删走、正则失效、或正文区被整段换掉，" +
                "这条用例都会退化成「零命中 ⇒ 一律通过」——不许这么绿",
            hits.size >= bodyFileNameOnlyRefFloor,
        )

        val registered = refs.filter { it.doc == requirements }.map { it.quote }.toSet()
        val missing = hits.entries.filter { it.key !in registered }
        assertTrue(
            "$requirements 正文区里出现 ${missing.size} 串纯粹文件名写法，登记表里没有对应条目：\n" +
                missing.joinToString("\n") { "  第 ${it.value} 行「${it.key}」" } +
                "\n正文区里的行号引用一律要登记（见类注释「带前缀引用的覆盖面」与这一条的说明）；" +
                "若那一串是同一文件的多段写法（用 `/` 串起来），请拆成互不相同的单条引用、各自登记",
            missing.isEmpty(),
        )

        val slashJoined = LinkedHashMap<String, Int>()
        for ((offset, line) in body.withIndex()) {
            for (hit in anySlashJoinedRef.findAll(line)) {
                if (!slashJoined.containsKey(hit.value)) slashJoined[hit.value] = offset + 1
            }
        }
        assertTrue(
            "$requirements 正文区（第 1 ~ $boundary 行）里出现 ${slashJoined.size} 串 `/` 串多段的写法：\n" +
                slashJoined.entries.joinToString("\n") { "  第 ${it.value} 行「${it.key}」" } +
                "\n正文区禁止 `/` 串多段（第 104 轮起），请拆成互不相同的单条引用并各自登记",
            slashJoined.isEmpty(),
        )
    }

    /**
     * 第 7 条（**第 105 轮新增**）：把 16 份普查对象（`docs/` 与 `records/` 下所有 `.md` 加 `README.md`）
     * 里**所有** `文件.kt:行号` 一股脑抓出来，凡文件名能在 `app/src` 下找到**同名**源码文件的，就逐条比
     * 「写出来的那个行号有没有超出该文件现有的行数」；**越界、且不在 [lineRefOverflowExempt] 里的当场红**。
     *
     * 第 1~6 条有一条共同的边界：它们只看**登记在案**的那几条，于是**没被登记的越界引用**谁也看不见。
     * 这一条不看登记表，看的是「物理上还有没有那一行」——`PlayCatalog.kt:999` 这种数，不需要任何关于
     * 「这段话该指哪儿」的知识就能判定为假。**它不要求登记，只要求越界这件事必须有过登记。**
     *
     * **判读口径**（三条，都是刻意的）：
     * 1. **只按文件名对**。文档里的路径前缀（`ui/theme/`）与 `/` 串多段（`22/25`）都不参与判读，只取
     *    `文件.kt` 这一节去找同名源码；同名多份 ⇒ 当场红（`文件名.kt:行号` 到底指哪一份说不清）。
     * 2. **行数按 `\n` 数**（`readBytes().count { it == '\n' }`，不用 `readLines()`），EOL 是 CRLF 还是
     *    LF 都不影响结论。
     * 3. **查无此文件的不判**（第三方堆栈帧、变异示例、第 37 轮删掉的文件的历史叙述——第 105 轮逐类看过，
     *    全部正当）。这一族特意留在管辖之外，否则要把大量正当引用都写成豁免，表就成了垃圾桶。
     *
     * **第 105 轮实测**：全量抓出 2,548 处引用；命中我方源码 2,403 处（其中若干处是多段写法，展开后
     * 2,623 个行号）/ 585 个互不相同的「文件名:行号」；越界 41 处 / 5 对，逐处看过上下文后**全部正当**
     * （见 [lineRefOverflowExempt]）。剩下 145 处是第三方堆栈帧、变异示例与已删文件的历史叙述，不归这条管。
     * ⇒ 结论是**没有找到活的缺陷**，这条用例的作用是把这类错从此钉住，而不是修掉什么。
     *
     * 下限自检（普查份数 / `.kt` 文件数 / 命中处数 / 不同串数）的纪律同第 1~6 条：把索引走空、或把
     * 正则写歪，退化成「零命中 ⇒ 全部通过」，都会当场红。
     */
    @Test
    fun everyLineRefEitherPointsInsideItsFileOrIsExplicitlyExempt() {
        // ---- 1. 建「文件名 → 实有行数」索引，只收 app/src 下的 .kt（main / androidTest / test 一视同仁）----
        val appSrc = mainSrc.parentFile!!.parentFile!!
        val lineCounts = mutableMapOf<String, Int>()
        val sameName = mutableMapOf<String, MutableList<String>>()
        var sourceFiles = 0
        val buildDir = File.separator + "build" + File.separator
        appSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") && !it.path.contains(buildDir) }
            .forEach { file ->
                sourceFiles++
                val lines = file.readBytes().count { it == '\n'.code.toByte() }
                sameName.getOrPut(file.name) { mutableListOf() }.add(file.path)
                val prev = lineCounts[file.name]
                if (prev == null || lines > prev) lineCounts[file.name] = lines
            }
        assertTrue(
            "`app/src` 下只数到 $sourceFiles 份 `.kt` 源码，低于下限 $lineRefBoundSourceFloor：" +
                "源码索引走空了，这条用例会退化成「一切查无此文件 ⇒ 全部通过」——不许这么绿",
            sourceFiles >= lineRefBoundSourceFloor,
        )
        val ambiguous = sameName.filterValues { it.size > 1 }
        assertTrue(
            "`app/src` 下有 ${ambiguous.size} 个文件名对应多份源码，`文件名.kt:行号` 这种写法指哪一份就说不清了：\n" +
                ambiguous.entries.joinToString("\n") { "  ${it.key}：${it.value.joinToString("、")}" } +
                "\n文档请改用带目录前缀的写法（那一族由第 4 条用例逐字核路径），" +
                "或者这条用例的判读口径要重新定——不许默默取一个",
            ambiguous.isEmpty(),
        )

        // ---- 2. 普查对象：docs/ 与 records/ 下的 .md，加仓库根的 README.md ----
        val targets = mutableListOf<File>()
        for (dir in listOf("docs", "records")) {
            val root = File(repoRoot, dir)
            assertTrue("$dir 目录不见了：普查对象少了一整个目录", root.isDirectory)
            root.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".md") }
                .forEach { targets += it }
        }
        targets += File(repoRoot, "README.md")
        assertTrue(
            "普查对象只剩 ${targets.size} 份，低于下限 $lineRefScanFileFloor（现值 16：`docs/` 10 份 + " +
                "`records/` 5 份 + `README.md`）：少扫一份文档就等于给它整个豁免",
            targets.size >= lineRefScanFileFloor,
        )

        // ---- 3. 全量抓引用、逐条比行数 ----
        var resolved = 0
        val distinctPairs = linkedSetOf<String>()
        val overflowPairs = linkedSetOf<String>()
        val overflowSamples = linkedMapOf<String, String>()
        for (target in targets) {
            val where = target.relativeTo(repoRoot).invariantSeparatorsPath
            for ((offset, line) in target.readText().lines().withIndex()) {
                for (hit in anyLineRefForBoundCheck.findAll(line)) {
                    val text = hit.value
                    val baseName = text.substringBefore(":")
                    val bound = lineCounts[baseName] ?: continue
                    resolved++
                    for (token in text.substringAfter(":").split("/")) {
                        val number = token.trim().toInt()
                        val key = "$baseName:$number"
                        distinctPairs += key
                        if (number > bound) {
                            overflowPairs += key
                            if (!overflowSamples.containsKey(key)) {
                                overflowSamples[key] = "$where 第 ${offset + 1} 行「$text」"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(
            "16 份普查对象里只认出 $resolved 处引到我方源码的行号（含越界），" +
                "低于下限 $lineRefBoundResolvedFloor（第 105 轮实测 2,403 处）：正则失效、扫描目录被换走、" +
                "或文档被删空，这条用例都会退化成「零命中 ⇒ 一律通过」——不许这么绿",
            resolved >= lineRefBoundResolvedFloor,
        )
        assertTrue(
            "只认出 ${distinctPairs.size} 个互不相同的「文件名:行号」，低于下限 $lineRefBoundDistinctFloor" +
                "（第 105 轮实测 585 个）：同上，这是同一层假绿的另一个面",
            distinctPairs.size >= lineRefBoundDistinctFloor,
        )

        // ---- 4. 双向对账：越界的一条不许漏、豁免的一条不许剩 ----
        val unregistered = overflowPairs - lineRefOverflowExempt.keys
        assertTrue(
            "有 ${unregistered.size} 个行号引用超出了文件现有的行数，而 [lineRefOverflowExempt] 里没有记账：\n" +
                unregistered.sorted().joinToString("\n") { key ->
                    val baseName = key.substringBefore(":")
                    "  $key（$baseName 实有 ${lineCounts[baseName]} 行）首次出现：${overflowSamples[key]}"
                } +
                "\n要么把那处引用回改到现值，要么把它补进豁免表——补之前先看清上下文：" +
                "历史叙述与逐字引文按 `records/03` ADR-070 不回改，现值宣称则要回改",
            unregistered.isEmpty(),
        )
        val stale = lineRefOverflowExempt.keys - overflowPairs
        assertTrue(
            "[lineRefOverflowExempt] 里有 ${stale.size} 条已经不越界了（文件长大了、或那处引用被改过），" +
                "表却还在替它们作保——过期豁免会掩盖下一次真的越界：\n" +
                stale.sorted().joinToString("\n") { "  $it：${lineRefOverflowExempt.getValue(it)}" } +
                "\n要么把这条从表里删掉，要么重新判定它现在是不是还该留着",
            stale.isEmpty(),
        )
    }
}
