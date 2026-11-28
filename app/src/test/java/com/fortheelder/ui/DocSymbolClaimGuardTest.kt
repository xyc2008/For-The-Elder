package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「文档里点名的**符号、取值、例数**，代码里真的是那样吗」的守卫（`docs/07-风险管理计划.md` 的风险 R-10）。
 *
 * 第 91 轮那条 [DocLineRefGuardTest] 读的是**位置**（`` `SettingsScreen.kt:122` `` 这种写法），
 * 第 67 / 68 轮那两条 [DocScreenCopyGuardTest] 读的是**手册里的字**。文档里还有第三类宣称一直没被机器看过一眼：
 * **点名**——「这张表叫 `gameWords`」「`PlayHint` 有 4 个非空提示」「`EncouragementTest` 10 例」。
 * 它比行号更隐蔽：行号错了肉眼一看就知道过期了，而一个**当年真有、后来并表改名**的符号，读起来完全像一个活的证据。
 *
 * 第 96 轮对 `docs/01-需求规格说明书.md` 的验收表做了第一次普查，本文档守卫就是那次普查的产物：
 * **三处点名已经死掉的词表**（AC-08.3 / AC-10.2 / AC-10.3 各一处，都是第 37 轮按 [CopyRules] 分表收口后的残留，
 * 见 `records/03` ADR-035 与 ADR-074），外加两处**读数过期**（AC-10.1 的行数与例数）。
 *
 * ## 它做什么
 *
 * 把「文档里那一串 → 源码里那一处地标」逐条登记成 [claims]，再补两张**数**的登记表（[listSizes] 词表长度、
 * [regexCounts] 例数），然后用五条用例钉住：
 *
 * 1. [everyRegisteredSymbolClaimIsQuotedInItsDocument]：引文**逐字**出现在指定文档里；同一条登记带一张
 *    [SymbolClaim.notInDocument]——**已经作废的旧说法不许再回到文档里**（这是把第 96 轮那次纠正钉住的那一半）。
 * 2. [everyRegisteredSymbolStillExistsInItsSourceFile]：源码文件里 [SymbolClaim.mustContain] 的每一个符号都在，
 *    [SymbolClaim.sourceMustNotContain] 的每一个都不在（AC-04.3 的「没有任何网络库依赖」用的就是这一半）。
 * 3. [registeredWordListSizesMatchTheCode]：文档说「11 个词」「17 个词」，就真去数那张表的字面量个数。
 * 4. [registeredExampleCountsMatchTheTestSources]：文档说「12 例」「7 例」「4 个非空提示」，就真去数。
 * 5. [theAppHasNoSecondClock]：AC-04.2 的另一半——除 `core/time/AppClock.kt` 外，`app/src/main` 里
 *    不许有第二处读钟。
 *
 * 四条文档类用例都带下限自检（登记表条目数、行数下限表的 key 必须**至少被一条登记引到**、最小 .kt 文件数）：
 * 登记表被删空、或源码文件被换成一个壳子，都会当场红，而不是「零条目 ⇒ 全部通过」。
 * 下限纪律抄自 [DocLineRefGuardTest] 与 [CopyCountGuardTest]。
 *
 * ## 它不保证什么
 *
 * - **只覆盖登记在册的那几条**。这是一张**手写**的表：第 96 轮的普查范围是「验收表证据栏里点名的符号 /
 *   取值 / 例数」，普查结论是「除这五处外，其余点名都对得上现值」——那句话是**人读出来的**，不是机器证出来的。
 * - **不核语境**。符号在源码里存在，不等于文档把它用对了地方（可能引错文件、引错语义）。
 * - **不核行数类宣称**。「`Encouragement.kt` 现在是 54 行」这一句**故意不入守卫**：行数是最容易随一次
 *   无关改动漂掉的一类数（加一行 KDoc 就变），把它登记进来等于给自己造一个每轮必红的假警报；它照旧只由
 *   `docs/07` §4 的人工监控兜着。理由写在 `records/03` ADR-074 里。
 * - **取值写法与表达式写法对不上时不报红**。`docs/01` AC-12.1 写的 `numbers` 是 `1..25`，源码写的是
 *   `(1..(SIDE * SIDE))`——**值相等、字样不等**。这条登记只钉 `SIDE = 5` 与那个表达式，`1..25` 那一串
 *   看不见；`docs/07` 的 GAP 清单里记着这条残余。
 * - **不核屏幕**。那归设备层用例（`app/src/androidTest`）。
 *
 * ## 结论
 *
 * **R-10 仍然是橙色**，只是残余面又少了一块：从第 96 轮起，「文档里点名的符号对不对」在**这一族之内**
 * 有机器兜底；这一族之外照旧只能靠人工。
 *
 * 源码目录由 `app/build.gradle.kts:98` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时这条用例
 * **直接失败**而不是跳过。
 */
class DocSymbolClaimGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    /** `app/src/main/java` 往上四层就是仓库根：`java` → `main` → `src` → `app` → 根。 */
    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val textCache = mutableMapOf<String, String>()

    /** 去掉 CRLF 再比：文档是 LF、`app/src/main` 下的源码是 CRLF，逐字比之前先拉齐行尾。 */
    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun docText(doc: String): String = textCache.getOrPut("doc:$doc") {
        val file = File(repoRoot, doc)
        assertTrue("文档不存在：$doc", file.isFile)
        readNormalized(file)
    }

    private fun sourceText(path: String): String = textCache.getOrPut("src:$path") {
        val file = File(repoRoot, path)
        assertTrue("源码文件不存在：$path", file.isFile)
        readNormalized(file)
    }

    /** 一条「文档里点名的符号」登记。 */
    private data class SymbolClaim(
        val doc: String,
        val quote: String,
        val source: String,
        val mustContain: List<String> = emptyList(),
        val sourceMustNotContain: List<String> = emptyList(),
        val notInDocument: List<String> = emptyList(),
        val note: String,
    )

    /** 一条「文档说这张表有几个词」登记：真去数 [valName] 那张表的字面量个数。 */
    private data class ListSize(
        val doc: String,
        val quote: String,
        val source: String,
        val valName: String,
        val expected: Int,
        val note: String,
    )

    /** 一条「文档说这条路有几个例」登记：真去数 [regex] 在 [file] 里的命中数。 */
    private data class RegexCount(
        val doc: String,
        val quote: String,
        val file: String,
        val regex: String,
        val expected: Int,
        val note: String,
    )

    private val main = "app/src/main/java/com/fortheelder"

    /**
     * 登记表：第 96 轮普查出来的**全部**符号类宣称。
     *
     * 第 1 ~ 5 条同时是**纠正证据**——它们的 [SymbolClaim.notInDocument] 就是本轮从验收表里删掉的那几句。
     * 第 1 / 2 / 3 / 4 / 5 条各自的旧原文逐字记在 `records/01-迭代记录.md` 第九十六轮里（故意的：文档里
     * 只留「旧原文见 records」，这样本表的「不许再出现」才判得动）。
     */
    private val claims: List<SymbolClaim> = listOf(
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`Route` 现在有 **5 个常量**",
            source = "$main/ui/nav/MainNav.kt",
            mustContain = listOf(
                "const val HOME",
                "const val SETTINGS",
                "const val TALK",
                "const val PLAY",
                "const val ONBOARDING_REVIEW",
            ),
            notInDocument = listOf("`Route` 只有 `HOME` 与 `SETTINGS`"),
            note = "AC-01.5：原先只点名两个常量，第 3 / 11 / 47 轮加的三个没回改",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`EncouragementTest` 第 96 轮实测 **12 例**",
            source = "$main/domain/companion/Encouragement.kt",
            mustContain = listOf(
                "fun forAction(action: String)",
                "CopyRules.violations(text)",
                "sentenceEnds",
            ),
            notInDocument = listOf("`EncouragementTest` **10 例**"),
            note = "AC-10.1：例数过期（第 16 轮加了双标点两例），例数本身另由 regexCounts 守",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`streakWords`（11 个词",
            source = "$main/domain/companion/CopyRules.kt",
            mustContain = listOf("val streakWords", "\"积分\"", "\"等级\"", "\"徽章\"", "\"排行\""),
            notInDocument = listOf("`gameWords`"),
            note = "AC-10.2：原先说词表住在 Encouragement 里，实际是 CopyRules 的单一来源（ADR-035）",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`CopyRules.spendWords`（17 个词",
            source = "$main/domain/companion/CopyRules.kt",
            mustContain = listOf("val spendWords", "\"报名\"", "\"会员\"", "\"下单\""),
            notInDocument = listOf("`moneyWords`"),
            note = "AC-10.3：原先点名的表不存在，第 37 轮并进了 spendWords（ADR-035）",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "要花钱的那一类词只有 `domain/companion/CopyRules.kt:30-33` 一张表 `spendWords`（17 个词",
            source = "$main/domain/companion/StarterOptions.kt",
            mustContain = listOf("fun violations(option: StarterOption)", "CopyRules.violations(text)"),
            note = "AC-08.3：本轮普查新发现——起点目录侧点名的那张表同样早就不存在了",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "**用户可见文案依旧 0 命中**",
            source = "$main/domain/voice/ClaimWords.kt",
            mustContain = listOf(
                "\"训练\"",
                "\"改善\"",
                "\"提升\"",
                "\"延缓\"",
                "\"评估\"",
                "\"认知能力\"",
                "\"疗效\"",
                "\"疗程\"",
                "\"治疗\"",
                "\"预防\"",
                "\"诊断\"",
                "\"康复\"",
                "\"大脑退化\"",
                "\"记忆力\"",
                "\"变聪明\"",
                "fun checkWords(text: String",
            ),
            note = "AC-11.1：15 词的单一来源；第 96 轮复测扫描命中 17 行、多出的 2 行是 KDoc",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`PlayHint` 有 **4** 个非空提示",
            source = "$main/domain/play/PlayGame.kt",
            mustContain = listOf(
                "ALREADY_DONE(\"这个已经点过了，换一个\")",
                "NOT_YET(\"这个还在后面，不急\")",
                "NOT_THIS_ONE(\"不是它，再看看\")",
                "MISSED_PAIR(\"不是一对，点一下接着找\")",
            ),
            note = "AC-11.2：「4 个」这个数另由 regexCounts 守",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`SchulteGrid.game`、`FindPair.game`、`SubtractSeven.game`、`Memory.game`、`SameKind.game` 与 `OddOne.game`",
            source = "$main/domain/play/PlayCatalog.kt",
            mustContain = listOf(
                "SchulteGrid.game",
                "FindPair.game",
                "SubtractSeven.game",
                "Memory.game",
                "SameKind.game",
                "OddOne.game",
            ),
            note = "AC-11.4：注册表 6 条各自的符号都得在",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`SchulteGrid.SIDE = 5`（`:23`）",
            source = "$main/domain/play/SchulteGrid.kt",
            mustContain = listOf("const val SIDE = 5", "(1..(SIDE * SIDE))"),
            note = "AC-12.1：文档的 `1..25` 是取值写法，源码是表达式，只钉 SIDE 与那个表达式",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`BuildConfig.VERSION_NAME` + `buildFeatures { buildConfig = true }`",
            source = "$main/ui/settings/SettingsScreen.kt",
            mustContain = listOf("BuildConfig.VERSION_NAME"),
            note = "AC-13.3：只钉「界面读的是构建配置」这一层，钉不住「源码别处没写死同一个字面量」",
        ),
        SymbolClaim(
            doc = "docs/01-需求规格说明书.md",
            quote = "`app/build.gradle.kts` 中**没有任何网络库依赖**",
            source = "app/build.gradle.kts",
            sourceMustNotContain = listOf(
                "okhttp",
                "retrofit",
                "volley",
                "ktor",
                "work-runtime",
                "serialization",
            ),
            note = "AC-04.3：permission 那一半归 ManifestPostureTest，这里只守依赖那一半",
        ),
    )

    private val listSizes: List<ListSize> = listOf(
        ListSize(
            doc = "docs/01-需求规格说明书.md",
            quote = "`streakWords`（11 个词",
            source = "$main/domain/companion/CopyRules.kt",
            valName = "streakWords",
            expected = 11,
            note = "AC-10.2",
        ),
        ListSize(
            doc = "docs/01-需求规格说明书.md",
            quote = "`CopyRules.spendWords`（17 个词",
            source = "$main/domain/companion/CopyRules.kt",
            valName = "spendWords",
            expected = 17,
            note = "AC-10.3 / AC-08.3",
        ),
    )

    private val regexCounts: List<RegexCount> = listOf(
        RegexCount(
            doc = "docs/01-需求规格说明书.md",
            quote = "`EncouragementTest` 第 96 轮实测 **12 例**",
            file = "app/src/test/java/com/fortheelder/domain/companion/EncouragementTest.kt",
            regex = "@Test",
            expected = 12,
            note = "AC-10.1",
        ),
        RegexCount(
            doc = "docs/01-需求规格说明书.md",
            quote = "`ClaimWordsTest` **7 例**",
            file = "app/src/test/java/com/fortheelder/domain/voice/ClaimWordsTest.kt",
            regex = "@Test",
            expected = 7,
            note = "AC-11.1",
        ),
        RegexCount(
            doc = "docs/01-需求规格说明书.md",
            quote = "`PlayCatalogTest` 7 例",
            file = "app/src/test/java/com/fortheelder/domain/play/PlayCatalogTest.kt",
            regex = "@Test",
            expected = 7,
            note = "AC-11.4",
        ),
        RegexCount(
            doc = "docs/01-需求规格说明书.md",
            quote = "`SchulteGridTest` 15 例",
            file = "app/src/test/java/com/fortheelder/domain/play/SchulteGridTest.kt",
            regex = "@Test",
            expected = 15,
            note = "AC-12.1",
        ),
        RegexCount(
            doc = "docs/01-需求规格说明书.md",
            quote = "`PlayHint` 有 **4** 个非空提示",
            file = "$main/domain/play/PlayGame.kt",
            // 4 空格缩进、名字全大写、带一段**非空**文案的枚举条目（`NONE("")` 故意不算）。
            regex = """^ {4}[A-Z][A-Z0-9_]*\("[^"]+"\),$""",
            expected = 4,
            note = "AC-11.2：扣掉空文案的 NONE 之后正好 4 个",
        ),
    )

    /**
     * 行数下限表：源码文件被换成一个「同名壳子」时当场红。
     * 每一把 key 都必须**至少被一条登记引到**（在 [everyRegisteredSymbolStillExistsInItsSourceFile] 里核），
     * 否则这把下限谁也守不到，等于没写。
     */
    private val sourceLineFloor: Map<String, Int> = mapOf(
        "$main/ui/nav/MainNav.kt" to 60,
        "$main/domain/companion/Encouragement.kt" to 40,
        "$main/domain/companion/CopyRules.kt" to 45,
        "$main/domain/companion/StarterOptions.kt" to 320,
        "$main/domain/voice/ClaimWords.kt" to 35,
        "$main/domain/play/PlayGame.kt" to 200,
        "$main/domain/play/PlayCatalog.kt" to 25,
        "$main/domain/play/SchulteGrid.kt" to 35,
        "$main/ui/settings/SettingsScreen.kt" to 350,
        "app/build.gradle.kts" to 100,
        "app/src/test/java/com/fortheelder/domain/companion/EncouragementTest.kt" to 90,
        "app/src/test/java/com/fortheelder/domain/voice/ClaimWordsTest.kt" to 80,
        "app/src/test/java/com/fortheelder/domain/play/PlayCatalogTest.kt" to 60,
        "app/src/test/java/com/fortheelder/domain/play/SchulteGridTest.kt" to 110,
    )

    private val symbolClaimFloor: Int = 11
    private val listSizeFloor: Int = 2
    private val regexCountFloor: Int = 5
    private val sourceLineFloorCount: Int = 14

    /** 读钟判据要扫的 .kt 文件数下限（实测 `app/src/main` 下 79 个）。 */
    private val mainKtFileFloor: Int = 60

    private val allRegisteredSources: Set<String> =
        (claims.map { it.source } + listSizes.map { it.source } + regexCounts.map { it.file }).toSet()

    @Test
    fun everyRegisteredSymbolClaimIsQuotedInItsDocument() {
        assertTrue("符号登记表被删空了（下限 $symbolClaimFloor）：${claims.size}", claims.size >= symbolClaimFloor)
        assertEquals(
            "登记表里 (文档, 引文) 有重复：引文必须逐字唯一，否则改一处会连带骗过另一处",
            claims.size,
            claims.map { it.doc to it.quote }.toSet().size,
        )
        val docs = claims.map { it.doc }.toSet()
        assertTrue("登记表必须覆盖 docs/01-需求规格说明书.md，现在只覆盖 $docs", DOCS_01 in docs)

        for (claim in claims) {
            val text = docText(claim.doc)
            assertTrue("【${claim.note}】引文没有逐字出现在 ${claim.doc} 里：${claim.quote}", text.contains(claim.quote))
            for (stale in claim.notInDocument) {
                assertFalse(
                    "【${claim.note}】${claim.doc} 里又出现了已经作废的说法：$stale",
                    text.contains(stale),
                )
            }
        }
    }

    @Test
    fun everyRegisteredSymbolStillExistsInItsSourceFile() {
        val missing = mutableListOf<String>()
        for (claim in claims) {
            val text = sourceText(claim.source)
            for (needle in claim.mustContain) {
                if (!text.contains(needle)) missing += "【${claim.note}】${claim.source} 里找不到 $needle"
            }
            for (banned in claim.sourceMustNotContain) {
                if (text.contains(banned)) missing += "【${claim.note}】${claim.source} 里出现了不该有的 $banned"
            }
        }
        assertEquals("文档点名的符号与源码对不上：\n" + missing.joinToString("\n"), 0, missing.size)

        assertSourceLineFloors()
    }

    @Test
    fun registeredWordListSizesMatchTheCode() {
        assertTrue("词表长度登记表被删空了（下限 $listSizeFloor）：${listSizes.size}", listSizes.size >= listSizeFloor)
        val complaints = mutableListOf<String>()
        for (claim in listSizes) {
            val doc = docText(claim.doc)
            if (!doc.contains(claim.quote)) {
                complaints += "【${claim.note}】引文没有逐字出现在 ${claim.doc} 里：${claim.quote}"
                continue
            }
            val actual = listLiteralCount(sourceText(claim.source), claim.valName)
            if (actual != claim.expected) {
                complaints += "【${claim.note}】${claim.source} 的 ${claim.valName} 现在有 $actual 个词，文档说 ${claim.expected} 个"
            }
        }
        assertEquals("词表长度与文档对不上：\n" + complaints.joinToString("\n"), 0, complaints.size)

        assertSourceLineFloors()
    }

    @Test
    fun registeredExampleCountsMatchTheTestSources() {
        assertTrue("例数登记表被删空了（下限 $regexCountFloor）：${regexCounts.size}", regexCounts.size >= regexCountFloor)
        val complaints = mutableListOf<String>()
        for (claim in regexCounts) {
            if (!docText(claim.doc).contains(claim.quote)) {
                complaints += "【${claim.note}】引文没有逐字出现在 ${claim.doc} 里：${claim.quote}"
                continue
            }
            val actual = Regex(claim.regex, RegexOption.MULTILINE).findAll(sourceText(claim.file)).count()
            if (actual != claim.expected) {
                complaints += "【${claim.note}】${claim.file} 里 /${claim.regex}/ 命中 $actual 处，文档说 ${claim.expected}"
            }
        }
        assertEquals("例数与文档对不上：\n" + complaints.joinToString("\n"), 0, complaints.size)

        assertSourceLineFloors()
    }

    @Test
    fun theAppHasNoSecondClock() {
        val appClock = "$main/core/time/AppClock.kt"
        val call = Regex("""Local(DateTime|Date|Time)\.now\(""")

        val files = mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()
        assertTrue(
            "`app/src/main` 下的 .kt 只有 ${files.size} 个（下限 $mainKtFileFloor），扫描范围可疑",
            files.size >= mainKtFileFloor,
        )

        val offenders = mutableListOf<String>()
        var hitsInAppClock = 0
        for (file in files) {
            val relative = file.relativeTo(repoRoot).path.replace('\\', '/')
            readNormalized(file).split("\n").forEachIndexed { index, line ->
                val trimmed = line.trim()
                // 只认代码行：KDoc / 行注释里写 `LocalDateTime.now()` 是在**说**这件事，不是在**做**它。
                val isComment = trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                if (!isComment && call.containsMatchIn(line)) {
                    if (relative == appClock) hitsInAppClock++ else offenders += "$relative:${index + 1}"
                }
            }
        }

        assertEquals(
            "AC-04.2：只有 AppClock.kt 可以读钟，这些地方又开了一个钟：${offenders.joinToString(", ")}",
            0,
            offenders.size,
        )
        assertTrue("$appClock 里没有一处 now()，判据本身失效了（扫描器认不出调用）", hitsInAppClock >= 1)
    }

    /** [valName] 那张 `List<String>` 表里的字面量个数；表找不到就返回 -1（用例当场红，不静默通过）。 */
    private fun listLiteralCount(text: String, valName: String): Int {
        val head = "val $valName: List<String> = listOf("
        val start = text.indexOf(head)
        if (start < 0) return -1
        val open = text.indexOf('(', start)
        val close = text.indexOf(')', open)
        if (open < 0 || close < 0) return -1
        return Regex("\"[^\"]*\"").findAll(text.substring(open + 1, close)).count()
    }

    /** 行数下限自检：key 必须被登记引到，文件必须还在，行数必须够。 */
    private fun assertSourceLineFloors() {
        assertTrue(
            "行数下限表被删空了（下限 $sourceLineFloorCount 把）：${sourceLineFloor.size}",
            sourceLineFloor.size >= sourceLineFloorCount,
        )
        val complaints = mutableListOf<String>()
        for ((path, floor) in sourceLineFloor) {
            if (path !in allRegisteredSources) {
                complaints += "下限表的 key $path 没有任何一条登记引到，这把下限谁也守不到"
                continue
            }
            val file = File(repoRoot, path)
            if (!file.isFile) {
                complaints += "源码文件不存在：$path"
                continue
            }
            val lines = readNormalized(file).count { it == '\n' } + 1
            if (lines < floor) complaints += "$path 只剩 $lines 行（下限 $floor），像是被换成了壳子"
        }
        assertEquals("行数下限自检没过：\n" + complaints.joinToString("\n"), 0, complaints.size)
    }

    private companion object {
        const val DOCS_01 = "docs/01-需求规格说明书.md"
    }
}
