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
 * **「文档里那一串 → 源码里那一行地标」**（[refs]，一条一个 [LineRef]），然后用三条用例交叉钉住：
 *
 * 1. [everyRegisteredDocLineRefPointsAtItsAnchor]：每个地标行号都落在该源码文件的行数范围内，
 *    而且**那一行上确实有那句话**（[LineRef.anchor] 是那一行的一小段原文）。代码挪行 ⇒ 红。
 * 2. [everyRegisteredDocLineRefIsActuallyQuotedInTheDocument]：每一串都**逐字**出现在指定的那一份
 *    文档里。文档把行号改掉或删掉 ⇒ 红。
 * 3. [everyStrictlyWrittenDocLineRefAgreesWithItsAnchorLine]：**纯粹写法**（`文件名.kt:NNN` 或
 *    `文件名.kt:NNN-MMM`，文件名里不带路径分隔符）的那一串，它写出来的数字必须与地标行号一致
 *    （区间写法则要求地标落在这个区间里）。这条是三条里唯一真的把「文档里那个数」与「代码里那一行」
 *    连起来的，前两条只保证「两边各自没变」。
 *
 * 三条用例都带下限自检（条目数、纯粹写法条目数、源码文件行数下限）：登记表被删空、或源码文件被换成
 * 一个壳子，都会当场红，而不是「零条目 ⇒ 全部通过」。
 *
 * ## 它不保证什么
 *
 * - **只覆盖这一族**。登记表里 13 条全部落在 `docs/01-需求规格说明书.md` §4.13 验收表的
 *   AC-13.4 / AC-13.5 两行、§4.4 / §4.5 / §4.14 里那三行，以及 `docs/03-详细设计说明书.md`
 *   §8.18 现值表那六行与 §7.9 追溯矩阵的索引行上。`docs/01` §7 变更史、`docs/03` §8.4x /
 *   §8.5x 的叙述段与 §8.18 内部的历史行号、`docs/06` / `docs/09`、`records/` 里的行号
 *   **照旧一个字节不核**——它们是当轮当时的快照，不是现值宣称。
 * - **只认纯粹写法**。带目录前缀的写法（`ui/settings/SettingsScreen.kt:446-461`）与多段写法
 *   （`ui/settings/SettingsScreen.kt:79/182-185`）第 3 条用例看不见，只由第 1 / 2 条保证「两边各自
 *   没变」；把行号写成别的形状（`第 122 行`、`L122`）同样看不见。这是有意为之：登记表要与文档里
 *   的**原样字样**逐字对齐，硬解一串自由文本里的数字，会得到大量假报红。
 * - **不核语境**。行号对了不等于那段话说对了地方——可能引错文件、引错语义。
 * - **不核布局、不核屏幕上读得到读不到**。那两件事分别归 `docs/03` §8.18 的版式读数与设备层用例。
 * - **不动 `docs/01` §7 那张矩阵里的行号**。那部分从第 21 轮起就在册为「未回填」（见 `docs/07`
 *   的 GAP 清单），本轮的边界就是**不回填它**，而不是假装它已经回填。
 *
 * ⇒ 结论与第 67 / 68 两轮相同：**R-10 仍然是橙色**，只是残余风险又少了一块——「文档里的行号会漂」
 * 这句话，从本轮起在**这一族之内**有了机器兜底，在这一族之外照旧只能靠人工（`docs/07` §4 的监控信号）。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。下限自检的纪律抄自 [CopyCountGuardTest] 与 [DocScreenCopyGuardTest]。
 */
class DocLineRefGuardTest {

    /**
     * 一条登记：文档里写的那一串（[quote]，与文档**逐字一致**）应当指向 [source] 的第 [anchorLine] 行，
     * 而那一行上确实有 [anchor] 这一小段原文；[note] 写清这条为什么在登记表里。
     */
    private data class LineRef(
        val doc: String,
        val quote: String,
        val source: String,
        val anchorLine: Int,
        val anchor: String,
        val note: String,
    )

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val sourceRoot: File = File(mainSrc, "com/fortheelder")

    private val requirements = "docs/01-需求规格说明书.md"
    private val design = "docs/03-详细设计说明书.md"

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
    )

    /** 登记表下限：删条目就必须同时改这里，不许无声缩水。 */
    private val registeredRefFloor = 13

    /**
     * 纯粹写法：文件名里**不带路径分隔符**，只有 `文件.kt:` 加一个数或一段区间。
     * 带 `ui/settings/` 这类前缀、或用 `/` 串多段的写法按设计不参与第 3 条用例（见类注释）。
     */
    private val strictQuote = Regex("^[A-Za-z0-9]+\\.kt:(\\d+)(?:-(\\d+))?$")

    /** 纯粹写法条目下限：把登记表里那 10 条纯数字写法删掉几条就得同时改这里。 */
    private val strictRefFloor = 10

    /** 每个登记到的源码文件的行数下限：文件被换成壳子时，地标检查会「恰好落空」而不是正常通过。 */
    private val sourceLineFloor = mapOf(
        "ui/settings/SettingsScreen.kt" to 400,
        "ui/settings/SettingsVoice.kt" to 350,
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

            val file = File(sourceRoot, ref.source)
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
        for ((source, list) in seen) {
            assertEquals("$source 没被登记到（`sourceLineFloor` 里的下限靠谁守？）", true, list.isNotEmpty())
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
}
