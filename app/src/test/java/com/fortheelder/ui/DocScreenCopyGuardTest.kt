package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「用户手册里引的那句话，屏幕上真的这么写吗」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * R-10 从登记那天起就是橙色的，残余风险一句话：**所有守卫都在读代码，没有一条守卫读文档**。
 * `docs/09-用户手册.md` §8.9 自己写着这个缺口——「这个错没有任何测试能抓到……抓它的动作是
 * 「写功能时回头把相关的手册段重读一遍」，不是自动化的」；`docs/07` 的 R-10 处置栏也写着
 * 「守卫不读 docs 目录下的 md 文件，在册宣称的措辞与数字可能双双过期而守卫照旧绿」。这条用例就是
 * 第一次让守卫去读文档：**把手册里反引号引起来的「屏幕上原样这么写的字」逐条对回 main 源码**。
 *
 * ## 它做什么（第 68 轮起是两段）
 *
 * **第一段吃反引号，第二段吃「」。**两段是同一个类的两条独立用例：同一份手册、同一份源码
 * 快照、同一套归一化与过筛，但**引文规则与判定单位不同**，一段红不等于另一段绿。
 *
 * **第一段（第 67 轮）**判定的单位是「整段引文」：
 *
 * 1. 扫 `docs/09-用户手册.md` 全文，抽出每一段 `` `…` ``（反引号）里的片段；
 * 2. 只留「看起来是在引用屏幕文案」的那些：**含 2 个以上汉字、不含任何拉丁字母、不含
 *    `/ § → ： < > * |` 与反引号本身**。这一步把「`§4.6`」「`dp → cm`」「`records/04`」
 *    这类元文本滤掉（同一个文件里 346 段反引号，过筛后只剩几十条）；
 * 3. 把 `app/src/main/java` 下全部 `.kt` 文件读成一份大文本，做三种归一化：
 *    - `${…}` 与 `$name`（字符串模板插值）→ 一个占位符字符；
 *    - 相邻两条字面量的拼接 `"…" + "…"` → 接成一条（屏幕上本来就是接起来显示的，
 *      例：`SettingsVoice.rhythmOnNote`）；
 *    - 手册片段里的阿拉伯数字 → 同一个占位符字符（屏幕上的数字多半来自插值）。
 * 4. **手册片段必须整段出现在归一化后的源码文本里**，找不到就报红，报错信息里逐条列出。
 *
 * **第二段（第 68 轮）**判定的单位是「从句」：
 *
 * 1. 扫同一份手册，抽出每一段「」里的片段；
 * 2. 同一个过筛；
 * 3. **再留一道筛：片段里必须带句末标点或省略号**。这一道筛是必需的，不是保守——
 *    手册里 473 段「」过筛后仍有 289 段，而其中带标点的只有 54 段；另外那 235 段是
 *    术语与话题名（「不做的事」「主动入口」「已知缺口」「已修」），它们在屏幕上没有
 *    对应的一句字面量，硬查只会得到上百条假报红（读数在 `build/r68/probe68.txt`）；
 * 4. 把片段按**省略号与句末标点**切成从句，去掉空白后**有 2 个以上汉字的从句逐条对回
 *    归一化后的源码**，任何一条对不上就报红。
 *
 * ## 它不保证什么
 *
 * - **只核 `docs/09`，且只核两类引文**：反引号片段，加上带句末标点或省略号的「」引文。
 *   **不带标点的「」一律不查**（术语、话题名、短语），其它 docs 目录下的 md 的引文、
 *   以及任何行号与数字宣称，两段都看不见——R-10 因此**仍然是橙色**，只是残余风险
 *   又少了一块。行号类宣称依旧只能靠人工（`docs/07` §4 的监控信号）。
 * - **第二段是按从句判的，比第一段松**。整句由好几段屏幕话拼成、或夹着省略号截断时，
 *   只要每段从句在源码里各自找得到，就算过。它抓得住「整句被改写」，抓不住「从句之间
 *   的连接词被改」（例如把「或者」写成「还是」而两侧的词组各自还在别处存在）。
 * - **不核语境**。片段在源码里存在，不等于手册用对了地方：可能是引错了页面、引错了时机。
 * - **不核布局**。文案存在不等于它在这个屏上放得下、读得到。
 * - **数字是放宽的**。片段里的数字被归一成占位符，所以手册把「最近 30 天」写成「最近
 *   90 天」而代码是插值的话，两段都抓不到（[tolerantHits] 与 [quotedTolerantHitLimit]
 *   会把放宽命中的片段列出来，供人工复核，并且条数上限被断言住，不许无声增长）。
 * - **不覆盖历史行**。`records` 目录下 md 里的引文是当轮当时的快照，守卫不去动它们。
 *
 * ## 豁免清单
 *
 * [exempt] 与 [quotedExempt] 里的每一条都带理由。豁免不是「静音」，三条纪律把豁免钉住：
 * ① 豁免项必须在手册里**真的出现**（手册改掉了它，豁免就得跟着撤）；
 * ② 豁免项必须**真的匹配不上**（一旦能匹配上，说明豁免过期了，判失败）；
 * ③ 没登记、又匹配不上的片段一律报红，不许沉默放过。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过。下限自检（文件数、片段数、源码行数）的纪律抄自
 * [CopyCountGuardTest] 与 [UiCopyPlacementTest]。
 */
class DocScreenCopyGuardTest {

    /** 一条豁免：某段手册引文为什么可以不上屏。 */
    private data class Exemption(val fragment: String, val reason: String)

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** `app/src/main/java` 往上四层就是仓库根（`app/src/main/java` → `app` → 根）。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!
    private val manual: File = File(repoRoot, "docs/09-用户手册.md")

    /** 插值与被归一化的数字都换成它。挑 U+0001 是因为它不可能出现在任何一句中文文案里。 */
    private val placeholder: Char = '\u0001'

    private val backtickSpan = Regex("`([^`\n]+)`")
    private val bracedInterpolation = Regex("[$]\\{[^}]*\\}")
    private val bareInterpolation = Regex("[$][A-Za-z_][A-Za-z0-9_]*")
    private val literalJoin = Regex("\"\\s*\\+\\s*\"")
    /**
     * 连续数字当作**一个整体**（「100 减 7」是两个数，不是一个数加三个数）：源码里的插值
     * 也是整段一个占位符，逐位替换会让 `100` 变成三个占位符，对不上。
     */
    private val digitRun = Regex("[0-9]+")

    private val forbidden = charArrayOf('/', '§', '→', '：', '<', '>', '*', '|', '\u0060')

    /**
     * 登记在册的豁免。每条都得说清楚「屏幕上的值从哪儿来」——写不出理由的，就该去改手册。
     */
    private val exempt: List<Exemption> = listOf(
        Exemption(
            fragment = "字号已改成「大一点」。",
            reason = "屏幕真值来自 SettingsVoice.kt 的 fontSizeChanged(label)，" +
                "「大一点」是 FontSize.LARGE.label（data/prefs/SettingsStore.kt）的取值，" +
                "源码里没有任何一处把「大一点」直接写进这句话。插值实参不在本守卫可达范围内。",
        ),
        Exemption(
            fragment = "把「水果」都点出来，一共 4 个",
            reason = "屏幕真值来自 PlayVoice.kt 的 \"把「\${round.kindLabel}」都点出来，" +
                "一共 \${round.stepsToFinish} 个\"；「水果」是 PlayCatalog 给的分类名，" +
                "源码字面量里没把它和这句话连在一起写。两类插值实参都够不着。",
        ),
        Exemption(
            fragment = "版本 0.1.0",
            reason = "屏幕真值来自 SettingsVoice.kt 的 \"版本 \${name}\"，实参是构建期生成的 " +
                "BuildConfig.VERSION_NAME，不在 app/src/main 的源码树里——守卫扫不到它，" +
                "但 `:app:assembleDebug` 的产物与 docs/01 能对上版本号。",
        ),
        Exemption(
            fragment = "滑过 = 0",
            reason = "不是屏幕上的字。它是 docs/09 §第 61 轮那条设备探针读数在手册里的引文" +
                "（诊断记法），屏幕上看不到「滑过 = 0」这句话。",
        ),
    )

    /** 放宽命中（数字归一后才对上）的条数上限。超过就说明源码里的字面量被换成了插值、要人工复核。 */
    private val tolerantHitLimit: Int = 8

    @Test
    fun everyQuotedScreenCopyInManualStillExistsInMainSource() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)
        assertTrue("用户手册不存在：$manual", manual.isFile)

        val manualText = manual.readText(Charsets.UTF_8)
        assertTrue(
            "用户手册短得离谱（${manualText.length} 字），守卫可能读错了文件",
            manualText.length > 30_000,
        )

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)

        val blob = StringBuilder()
        var scannedLines = 0
        for (file in files) {
            val text = file.readText(Charsets.UTF_8)
            scannedLines += text.count { it == '\n' } + 1
            blob.append(text).append('\n')
        }
        assertTrue(
            "扫描到的 main 源码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 6_000,
        )

        val normalizedSource = normalizeSource(blob.toString())

        val spans = backtickSpan.findAll(manualText).map { it.groupValues[1] }.toList()
        assertTrue(
            "手册里的反引号片段只有 ${spans.size} 段，守卫可能没真的读到手册",
            spans.size >= 200,
        )
        val candidates = spans.filter(::looksLikeScreenCopy).distinct().sorted()
        assertTrue(
            "手册里像屏幕文案的反引号片段只有 ${candidates.size} 条，过筛规则可能已经失效",
            candidates.size >= 40,
        )

        val exactHits = mutableListOf<String>()
        val tolerantHits = mutableListOf<String>()
        val misses = mutableListOf<String>()
        for (candidate in candidates) {
            when {
                normalizedSource.contains(candidate) -> exactHits += candidate
                normalizedSource.contains(withDigitsNormalized(candidate)) -> tolerantHits += candidate
                else -> misses += candidate
            }
        }

        val exemptFragments = exempt.map { it.fragment }
        val liveExemptions = exemptFragments.filter { it in candidates }
        val unexplained = misses.filterNot { it in exemptFragments }

        // 诊断先行：报红时 println 也要出现在报告里，读数才是这一轮的证据（只报红不报数没用）。
        println(
            "[doc-screen-copy] 手册反引号片段=${spans.size} 过筛=${candidates.size} " +
                "逐字命中=${exactHits.size} 放宽命中=${tolerantHits.size} " +
                "豁免=${liveExemptions.size} 报红=${unexplained.size}",
        )
        for (hit in tolerantHits) {
            println("[doc-screen-copy] 放宽命中：「$hit」")
        }
        for (miss in unexplained) {
            println("[doc-screen-copy] 报红：「$miss」")
        }

        val staleExemptions = exemptFragments.filterNot { it in candidates }
        assertTrue(
            "下面这些豁免在手册里已经找不到了（手册改了引文），请把豁免撤掉——" +
                "一条悬空的豁免和没有守卫一样：\n" + staleExemptions.joinToString("\n"),
            staleExemptions.isEmpty(),
        )
        assertTrue(
            "下面这些豁免其实已经能匹配上了（源码或手册改好了），请把豁免撤掉：" +
                liveExemptions.filter { it in exactHits || it in tolerantHits }.joinToString("\n"),
            liveExemptions.none { it in exactHits || it in tolerantHits },
        )

        assertTrue(
            "用户手册引的 ${unexplained.size} 句话，屏幕上（app/src/main 全部 .kt）" +
                "找不到，共 ${unexplained.size} 条。改手册、或者改代码——两边必须一样；" +
                "确实够不着源码字面量的，写进本用例的 exempt 表并给出理由：\n" +
                unexplained.joinToString("\n") { "  「$it」" },
            unexplained.isEmpty(),
        )

        assertTrue(
            "放宽命中（片段里的数字归一后才对上）有 ${tolerantHits.size} 条，超过上限 " +
                "$tolerantHitLimit 条。这可能是源码把写死的文案改成了插值（正常），" +
                "也可能是手册把数字写错了（守卫抓不到）。请人工看一眼：\n" +
                tolerantHits.joinToString("\n") { "  「$it」" },
            tolerantHits.size <= tolerantHitLimit,
        )
    }

    // ── 第二段（第 68 轮）：「」式引文 ──────────────────────────────────────
    //
    // 第一段只吃反引号。手册里还有一种引法是「」——它比反引号多背一层用途：**术语与话题名
    // 也用「」**（「不做的事」「主动入口」「已知缺口」「已修」「那一支。。这件事」这类）。
    // 本机量到的分层是：全文 473 段「」→ 过筛 289 段 → **带句末标点或省略号的只剩 54 段**，
    // 那 235 段是术语、短语、字段名，本段不查。所以第二段的判据必须是
    // 「过筛 + 带句末标点或省略号」——**「凡「」必查」是做不到的**：术语类引文永远找不到
    // 对得上的屏幕字面量，硬查只会得到 101 条假报红（读数在 build/r68/probe68.txt）。
    // 这一条是量出来的，不是推测。
    //
    // 断句也量出来了两条必要规则，都来自真实引文形态：
    // ① 省略号是**截断标记**，不是句子的一部分——手册用「您可以把想说的写下来……」代指
    //    屏幕上一整句（原句以「。」结尾），拿整段去比对源码必然落空，得先按省略号断开；
    // ② 手册会用「 …… 」把**同一屏的两三句话并引**（首页那句「早上好 …… 说话的时机，
    //    我先按普通的作息来。」就是两行并成一条引文），所以句末标点也得当断点。
    // 规则：按省略号与句末标点切成从句，去掉空白后**有 2 个以上汉字的从句必须逐条命中**
    // （逐字命中，或数字归一后命中）。不足 2 个汉字的碎片（「」「早上好」这类提问词、
    // 断出来的空串）不参与判定，它们太短，命中与否都不说明问题。

    /** 一条「」引文豁免：它为什么可以不上屏。纪律与 [exempt] 完全相同。 */
    private data class QuoteExemption(val fragment: String, val reason: String)

    private val quoteSpan = Regex("「([^「」\n]+)」")

    /** 「带句末标点或省略号」——本段只吃这一种「」引文。 */
    private val quotedSentence = Regex("[。？！]|…")

    /** 断句：省略号与句末标点都当断点（理由见本段开头的 ①②）。 */
    private val clauseSplit = Regex("…+|[。？！]")

    /**
     * 登记在册的「」引文豁免。两条都是**插值实参类**：屏幕上那句是对的，只是值来自
     * 插值，源码里没有任何一处把取值和句子写在同一处字面量里，本守卫够不着。
     * 写不出理由的，就该去改手册。
     */
    private val quotedExempt: List<QuoteExemption> = listOf(
        QuoteExemption(
            fragment = "这一步已经记成日常了，记在上午。",
            reason = "屏幕真值来自 domain/companion/CompanionVoice.kt 的 anchorAlreadyLine(slot)：" +
                "「记在」之后接的是 confirmSlotLabel(slot)（第 86 轮起改为复述用户刚按下的那个按钮），" +
                "这一段取值就是 confirmSlotLabel(AnchorSlot.MORNING) 的「记在上午」。" +
                "取值没有和这句话写在同一处字面量里——插值实参不在本守卫可达范围内。",
        ),
        QuoteExemption(
            fragment = "这些只在您打开应用的时候才出现，我不会另外吵您。不用记了就是直接删掉，" +
                "随时可以再记一次。",
            reason = "屏幕真值来自同文件 CompanionVoice.kt 的 anchorNote()：后半句由" +
                " anchorForgetLabel() 这个插值给出，它的字面量是「不用记了」。同样是插值实参够不着。",
        ),
    )

    /** 「」引文里放宽命中（数字归一后才对上）的条数上限。本机量到 3 条，留 2 条余量。 */
    private val quotedTolerantHitLimit: Int = 5

    /**
     * 手册里用「」引的**整句屏幕话**，屏幕上还这么写吗。
     *
     * 与第一段的差别只有两条：引文规则换成「」，并只吃带句末标点或省略号的那些。
     * 归一化、过筛、三段断言纪律（悬空豁免、已匹配豁免、未解释报红）一律照抄，
     * 两段各自独立成例——**一段红不代表另一段绿**，两段都红才说明手册整体脱节。
     */
    @Test
    fun everyQuotedScreenSentenceInManualStillExistsInMainSource() {
        assertTrue("main 源码目录不存在：$mainSrc", mainSrc.isDirectory)
        assertTrue("用户手册不存在：$manual", manual.isFile)

        val manualText = manual.readText(Charsets.UTF_8)
        assertTrue(
            "用户手册短得离谱（${manualText.length} 字），守卫可能读错了文件",
            manualText.length > 30_000,
        )

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
        assertTrue("main 源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 50)

        val blob = StringBuilder()
        var scannedLines = 0
        for (file in files) {
            val text = file.readText(Charsets.UTF_8)
            scannedLines += text.count { it == '\n' } + 1
            blob.append(text).append('\n')
        }
        assertTrue(
            "扫描到的 main 源码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 6_000,
        )

        val normalizedSource = normalizeSource(blob.toString())

        val spans = quoteSpan.findAll(manualText).map { it.groupValues[1] }.toList()
        assertTrue(
            "手册里的「」片段只有 ${spans.size} 段，守卫可能没真的读到手册",
            spans.size >= 350,
        )
        val screened = spans.filter(::looksLikeScreenCopy).distinct().sorted()
        assertTrue(
            "手册里像屏幕文案的「」片段只有 ${screened.size} 条，过筛规则可能已经失效",
            screened.size >= 200,
        )
        val candidates = screened.filter { quotedSentence.containsMatchIn(it) }
        assertTrue(
            "带句末标点或省略号的「」片段只有 ${candidates.size} 条，判据可能已经失效",
            candidates.size >= 35,
        )

        val exactHits = mutableListOf<String>()
        val tolerantHits = mutableListOf<String>()
        val misses = mutableListOf<String>()
        for (candidate in candidates) {
            val clauses = clausesOf(candidate)
            val unmatched = clauses.filterNot { normalizedSource.contains(it) }
            val hardMiss = unmatched.filterNot { normalizedSource.contains(withDigitsNormalized(it)) }
            when {
                clauses.isEmpty() -> misses += candidate
                hardMiss.isEmpty() && unmatched.isEmpty() -> exactHits += candidate
                hardMiss.isEmpty() -> tolerantHits += candidate
                else -> misses += candidate
            }
        }

        val exemptFragments = quotedExempt.map { it.fragment }
        val liveExemptions = exemptFragments.filter { it in candidates }
        val unexplained = misses.filterNot { it in exemptFragments }

        // 诊断先行：报红时 println 也要出现在报告里，读数才是这一轮的证据。
        println(
            "[doc-screen-copy-quote] 手册「」片段=${spans.size} 过筛=${screened.size} " +
                "带句末标点或省略号=${candidates.size} " +
                "整段命中=${exactHits.size} 放宽命中=${tolerantHits.size} " +
                "豁免=${liveExemptions.size} 报红=${unexplained.size}",
        )
        for (hit in tolerantHits) {
            println("[doc-screen-copy-quote] 放宽命中：「$hit」")
        }
        for (miss in unexplained) {
            println("[doc-screen-copy-quote] 报红：「$miss」")
            for (clause in clausesOf(miss)) {
                if (!normalizedSource.contains(clause) &&
                    !normalizedSource.contains(withDigitsNormalized(clause))
                ) {
                    println("[doc-screen-copy-quote]   断句对不上：「$clause」")
                }
            }
        }

        val staleExemptions = exemptFragments.filterNot { it in candidates }
        assertTrue(
            "下面这些「」豁免在手册里已经找不到了（手册改了引文），请把豁免撤掉——" +
                "一条悬空的豁免和没有守卫一样：\n" + staleExemptions.joinToString("\n"),
            staleExemptions.isEmpty(),
        )
        assertTrue(
            "下面这些「」豁免其实已经能匹配上了（源码或手册改好了），请把豁免撤掉：" +
                liveExemptions.filter { it in exactHits || it in tolerantHits }.joinToString("\n"),
            liveExemptions.none { it in exactHits || it in tolerantHits },
        )

        assertTrue(
            "用户手册用「」引的 ${unexplained.size} 句整屏幕话，在 app/src/main 全部 .kt 里" +
                "找不到，共 ${unexplained.size} 条。改手册、或者改代码——两边必须一样；" +
                "确实够不着源码字面量的，写进本用例的 quotedExempt 表并给出理由：\n" +
                unexplained.joinToString("\n") { "  「$it」" },
            unexplained.isEmpty(),
        )

        assertTrue(
            "「」引文里放宽命中（数字归一后才对上）有 ${tolerantHits.size} 条，超过上限 " +
                "$quotedTolerantHitLimit 条。这可能是源码把写死的文案改成了插值（正常），" +
                "也可能是手册把数字写错了（本段抓不到）。请人工看一眼：\n" +
                tolerantHits.joinToString("\n") { "  「$it」" },
            tolerantHits.size <= quotedTolerantHitLimit,
        )
    }

    /** 把一段「」引文切成若干从句：省略号与句末标点都当断点；不足 2 个汉字的碎片丢掉。 */
    private fun clausesOf(quote: String): List<String> = clauseSplit.split(quote)
        .map { it.trim() }
        .filter { it.count { ch -> ch in '\u4e00'..'\u9fff' } >= 2 }

    /** 三段归一化，顺序不能换：先插值，再把相邻字面量接起来。 */
    private fun normalizeSource(text: String): String = text
        .replace(bracedInterpolation, placeholder.toString())
        .replace(bareInterpolation, placeholder.toString())
        .replace(literalJoin, "")

    private fun withDigitsNormalized(candidate: String): String =
        candidate.replace(digitRun, placeholder.toString())

    /** 过筛：够中文化、够干净，才当它是「在引用屏幕文案」。 */
    private fun looksLikeScreenCopy(span: String): Boolean {
        if (span.count { it in '\u4e00'..'\u9fff' } < 2) return false
        if (span.any { it in forbidden }) return false
        if (span.any { it in 'A'..'Z' || it in 'a'..'z' }) return false
        return true
    }
}
