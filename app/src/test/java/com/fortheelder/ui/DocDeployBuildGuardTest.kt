package com.fortheelder.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ## 它做什么
 *
 * 判据 14 —— 盯住 `docs/08-部署与构建说明.md`（下文简称「部署文档」）这份「怎么把工程造出来、装上去、验一遍」的说明书。
 *
 * 前 13 条判据全部落在 `run.sh` 那一族上（它是不是真跑、分支有没有人走、日志与证据对不对得上）。判据 14 是**第一条
 * 落在纯文档上的判据**，起因是第 137 轮的一次实证：往部署文档末尾纯追加 293 字节散文（26,548 → 26,841 字节），
 * 定向集红数与基线同为 6、**零新增** —— 也就是说，只要不动别的文件，这份文档正文怎么写都没人看着。这一轮把这条盲区关掉。
 *
 * 九个用例分工：
 * 1. [theDeployDocIsActuallyReadAndHasTheKnownBaseShape] —— 文件真在、真读得进来、字节数与行数不低于底线。
 * 2. [theNineSectionHeadingsAreExactlyTheKnownOnes] —— 九个 `## ` 小节标题逐字、逐序相等，且编号 1..9 连号不漏。
 * 3. [theTailOfTheDocumentIsPinnedSoAnyAppendShowsUp] —— 文档末尾那一句与「末尾空行数」钉死，**这一条才是真正抓纯追加的**。
 * 4. [theFenceLinesArePinnedSoAppendingACodeBlockShowsUp] —— 围栏行数与对数钉死，追加或删除代码块会被看见。
 * 5. [everyGradlewTaskNamedInTheDocIsOneTheProjectActuallyHas] —— 文档里出现的每个 Gradle 任务名都要在项目里真存在。
 * 6. [theTailRulerReportsAppendsInsteadOfStayingGreen] —— 尺子自检：尾部形状函数在 6 组人造片段上的读数。
 * 7. [theFenceRulerReportsItsFixtures] —— 尺子自检：围栏普查函数在 8 组人造片段上的读数。
 * 8. [theGradlewRulerReportsItsFixtures] —— 尺子自检：任务名抽取函数在 5 组人造片段上的读数。
 * 9. [theFloorsAreSaneAndTheKnownReadingsHold] —— 底线自检：底线不许宽到形同虚设，记下的读数不许被悄悄改掉。
 *
 * ## 它不保证什么
 *
 * - **刻意不钉部署文档的精确字节数与 md5**。正文每改一个字都要人跟着滚常量，滚漏一次判据就退化成「一律通过」的假绿；
 *   这一轮只钉形状（标题、尾部、围栏数、任务名白名单）。第 137 轮实测：往末尾纯追加 293 字节，`split("\n")` 行数不变，
 *   所以「行数」口径也抓不到它 —— 抓住它的是第 3 条的尾部形状。
 * - 断言用的是 [readNormalized]（先把 CRLF 归一成 LF）。本仓库 `.gitattributes` 是 `* text=auto` 且 `core.autocrlf=true`，
 *   `git checkout` 会把盘上的 LF 转成 CRLF；此时若断言「`\r` 个数为 0」会造出假红。字节数底线用原始字节，两种行尾都过。
 * - 第 5 条只核对任务名**这个词本身**在项目里存在（`:模块:` 的模块目录下有 `build.gradle.kts`），加上「app 模块确实挂着
 *   Android 应用插件（直接写 id 或走版本目录别名都认，走别名时会回头核 `gradle/libs.versions.toml` 里的 id）」；
 *   不核对文档里那句话说得对不对。
 * - 「文件真被读了」只靠一条 `isFile` 加底线撑着：部署文档被删掉会红，被换成一个 26KB 的别的文件不会红。
 *
 * ## 结论
 *
 * 第 138 轮实测（判据落地时的读数）：26,548 字节、`split("\n")` 496 行、九个标题、36 行围栏 / 18 对 / 闭合、
 * 16 处 `./gradlew` 命中落在 6 个任务名上、末尾 1 个空行、末句为 [expectedTailLine]。
 */
class DocDeployBuildGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 forthelder.mainSrc（应写在 app/build.gradle.kts 的 test 任务里）"),
    )

    private val repoRoot: File = mainSrc.parentFile!!.parentFile!!.parentFile!!.parentFile!!

    private val doc: File = File(repoRoot, "docs/08-部署与构建说明.md")

    private val textCache: MutableMap<String, String> = mutableMapOf()

    private fun readNormalized(file: File): String = file.readText().replace("\r\n", "\n")

    private fun deployText(): String = textCache.getOrPut(doc.path) { readNormalized(doc) }

    /** `split("\n")` 口径：末尾带换行的文本会多出一个空串，与 `wc -l` 差 1。 */
    private fun linesOf(text: String): List<String> = text.split("\n")

    private data class TailShape(val lastText: String?, val trailingBlanks: Int)

    /** 尾部形状：最后一句非空正文 + 末尾连续空白行的条数。 */
    private fun tailShapeOf(text: String): TailShape {
        val lines: List<String> = linesOf(text)
        return TailShape(
            lastText = lines.lastOrNull { it.isNotBlank() },
            trailingBlanks = lines.takeLastWhile { it.isBlank() }.size,
        )
    }

    private data class FenceCensus(val lines: List<Int>, val closed: Boolean, val pairs: Int)

    /** 围栏普查：缩进 0~3 格的 ``` 或 ~~~ 算围栏行，记号只比前三字符，相邻两个配对。 */
    private fun fenceCensus(text: String): FenceCensus {
        val fence: Regex = Regex("""^ {0,3}(`{3,}|~{3,})""")
        val fenceLines: MutableList<Int> = mutableListOf()
        val markers: MutableList<String> = mutableListOf()
        linesOf(text).forEachIndexed { index, line ->
            val hit: MatchResult = fence.find(line) ?: return@forEachIndexed
            fenceLines += index + 1
            markers += hit.groupValues[1].substring(0, 3)
        }
        val pairs: Int = (0 until markers.size / 2).count { markers[2 * it] == markers[2 * it + 1] }
        return FenceCensus(
            lines = fenceLines,
            closed = markers.size % 2 == 0 && pairs * 2 == markers.size,
            pairs = pairs,
        )
    }

    /** 只认 `./gradlew <任务>`：散文里的裸词 gradlew、以及任务名后面那些开关都不算。 */
    private fun gradlewTasks(text: String): List<String> {
        val call: Regex = Regex("""\./gradlew[ \t]+([A-Za-z0-9:._-]+)""")
        return call.findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun fenceBones(text: String): Triple<Int, Boolean, Int> {
        val census: FenceCensus = fenceCensus(text)
        return Triple(census.lines.size, census.closed, census.pairs)
    }

    @Test
    fun theDeployDocIsActuallyReadAndHasTheKnownBaseShape() {
        assertTrue("部署文档不存在：${doc.path}", doc.isFile)
        val rawBytes: Int = doc.readBytes().size
        assertTrue(
            "部署文档只有 $rawBytes 字节，低于底线 $byteFloor —— 文件被截断、或者被换成了别的东西",
            rawBytes >= byteFloor,
        )
        val text: String = deployText()
        assertTrue("部署文档读进来是空的", text.isNotBlank())
        val lineCount: Int = linesOf(text).size
        assertTrue(
            "部署文档只有 $lineCount 行（split 口径），低于底线 $splitLineFloor",
            lineCount >= splitLineFloor,
        )
    }

    @Test
    fun theNineSectionHeadingsAreExactlyTheKnownOnes() {
        val headings: List<String> = linesOf(deployText()).filter { it.startsWith("## ") }
        assertEquals(
            "部署文档的 `## ` 小节标题与登记值不一致（左=登记值，右=文档里实际的）；新增、删除、改名或调序都会红",
            knownHeadings,
            headings,
        )
        val numbers: List<Int> = headings.mapNotNull { line ->
            Regex("""^## (\d+)\. """).find(line)?.groupValues?.get(1)?.toInt()
        }
        assertEquals(
            "九个标题里能抠出编号的只有 ${numbers.size} 条（应为 ${knownHeadings.size} 条）",
            knownHeadings.size,
            numbers.size,
        )
        assertEquals("小节编号应当从 1 连到 9、不漏不重", (1..9).toList(), numbers)
    }

    @Test
    fun theTailOfTheDocumentIsPinnedSoAnyAppendShowsUp() {
        val shape: TailShape = tailShapeOf(deployText())
        assertEquals(
            "部署文档的最后一句非空正文与登记值不一致 —— 末尾被追加或改写了" +
                "（第 137 轮实测：往末尾纯追加 293 字节散文，除本判据外无人报警）",
            expectedTailLine,
            shape.lastText,
        )
        assertEquals(
            "部署文档末尾的空行数为 ${shape.trailingBlanks}（登记值 $knownTrailingBlanks）—— 末尾被追加了换行或空行",
            knownTrailingBlanks,
            shape.trailingBlanks,
        )
    }

    @Test
    fun theFenceLinesArePinnedSoAppendingACodeBlockShowsUp() {
        val census: FenceCensus = fenceCensus(deployText())
        assertEquals(
            "部署文档里的围栏行数为 ${census.lines.size}（登记值 $knownFenceLines）—— 追加或删除代码块会改变它",
            knownFenceLines,
            census.lines.size,
        )
        assertEquals(
            "部署文档里的围栏对数为 ${census.pairs}（登记值 $knownFencePairs）—— 代码块数量变了",
            knownFencePairs,
            census.pairs,
        )
        assertTrue(
            "部署文档的围栏没有闭合（成对的 ``` 或 ~~~）—— 围栏行号：${census.lines}",
            census.closed,
        )
        assertTrue(
            "部署文档里的围栏行数 ${census.lines.size} 低于底线 $fenceLineFloor",
            census.lines.size >= fenceLineFloor,
        )
    }

    @Test
    fun everyGradlewTaskNamedInTheDocIsOneTheProjectActuallyHas() {
        val found: List<String> = gradlewTasks(deployText())
        assertTrue(
            "部署文档里一处 `./gradlew <任务>` 都没抽到，或者抽出来的比登记值 $knownGradlewHitCount 处还少（实际 ${found.size} 处）",
            found.size >= gradlewHitFloor,
        )
        val distinct: List<String> = found.distinct().sorted()
        assertEquals(
            "部署文档里出现的 Gradle 任务名与白名单不一致（左=登记白名单，右=文档里实际出现的）；" +
                "文档提到白名单外的任务、或白名单里的任务在文档里全体消失，都会红",
            knownGradlewTasks.sorted(),
            distinct,
        )
        val modules: List<String> = knownGradlewTasks
            .filter { it.startsWith(":") }
            .map { it.removePrefix(":").substringBefore(":") }
            .distinct()
        assertTrue("白名单里一条 `:模块:任务` 都没有，尺子坏了", modules.isNotEmpty())
        modules.forEach { module ->
            assertTrue(
                "文档把任务写在 `:$module:` 模块下，但仓库里没有 $module/build.gradle.kts —— 这个模块不存在",
                File(repoRoot, "$module/build.gradle.kts").isFile,
            )
        }
        val appScript: File = File(repoRoot, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts 不存在：${appScript.path}", appScript.isFile)
        val appText: String = readNormalized(appScript)
        assertTrue(
            "app/build.gradle.kts 里既没直接写 $androidApplicationPlugin，也没走版本目录别名 $androidApplicationAlias" +
                " —— 文档里那些 `:app:` 任务落不到一个 Android 应用模块上",
            appText.contains(androidApplicationPlugin) || appText.contains(androidApplicationAlias),
        )
        if (appText.contains(androidApplicationAlias)) {
            val catalog: File = File(repoRoot, "gradle/libs.versions.toml")
            assertTrue(
                "app/build.gradle.kts 用版本目录别名 $androidApplicationAlias 上插件，但 $catalog 不在",
                catalog.isFile,
            )
            assertTrue(
                "版本目录 $catalog 里没有插件 id $androidApplicationPlugin —— 别名指向的东西对不上",
                readNormalized(catalog).contains(androidApplicationPlugin),
            )
        }
    }

    @Test
    fun theTailRulerReportsAppendsInsteadOfStayingGreen() {
        val clean = TailShape("乙", 1)
        assertEquals("`甲\\n乙\\n` 的末句应为 乙、末尾 1 个空行", clean, tailShapeOf("甲\n乙\n"))
        assertEquals("`甲\\n乙\\n丙\\n` 的末句应为 丙", TailShape("丙", 1), tailShapeOf("甲\n乙\n丙\n"))
        assertEquals("`甲\\n乙\\n\\n` 末尾算 2 个空行", TailShape("乙", 2), tailShapeOf("甲\n乙\n\n"))
        assertEquals("`甲\\n乙\\n   \\n` 末尾的空白行也算空行", TailShape("乙", 2), tailShapeOf("甲\n乙\n   \n"))
        assertEquals("`甲\\n乙`（不带尾换行）末尾空行数为 0", TailShape("乙", 0), tailShapeOf("甲\n乙"))
        assertEquals("空文本没有末句，末行按 1 个空行算", TailShape(null, 1), tailShapeOf(""))
        listOf(
            "甲\n乙\n丙\n" to "末尾追加了一行",
            "甲\n乙\n\n" to "末尾多了一个空行",
            "甲\n乙\n   \n" to "末尾多了一个空白行",
            "甲\n乙" to "尾换行被吃掉了",
            "" to "整篇被清空",
        ).forEach { (fixture, what) ->
            assertNotEquals(
                "尺子坏了：$what 这种情形读出来的尾部形状与干净文本相同，等于恒绿",
                clean,
                tailShapeOf(fixture),
            )
        }
    }

    @Test
    fun theFenceRulerReportsItsFixtures() {
        assertEquals("一个反引号代码块：2 行围栏、闭合、1 对", Triple(2, true, 1), fenceBones("```\n甲\n```\n"))
        assertEquals(
            "两个反引号代码块：4 行围栏、闭合、2 对",
            Triple(4, true, 2),
            fenceBones("```\n甲\n```\n\n```\n乙\n```\n"),
        )
        assertEquals("只开不合：1 行围栏、未闭合、0 对", Triple(1, false, 0), fenceBones("```\n甲\n"))
        assertEquals("开合记号不同（``` 开 ~~~ 合）：不闭合、0 对", Triple(2, false, 0), fenceBones("```\n甲\n~~~\n"))
        assertEquals("两个波浪号不算围栏", Triple(0, true, 0), fenceBones("~~\n甲\n"))
        assertEquals("四个反引号也算围栏（只比前三字符）", Triple(2, true, 1), fenceBones("````\n甲\n````\n"))
        assertEquals("缩进四格不再算围栏", Triple(0, true, 0), fenceBones("    ```\n甲\n    ```\n"))
        assertEquals("缩进三格仍算围栏", Triple(2, true, 1), fenceBones("   ```\n甲\n   ```\n"))
    }

    @Test
    fun theGradlewRulerReportsItsFixtures() {
        assertEquals(
            "一行 `./gradlew :app:assembleDebug` 抽出一个任务名",
            listOf(":app:assembleDebug"),
            gradlewTasks("./gradlew :app:assembleDebug"),
        )
        assertEquals(
            "`--rerun-tasks` 这类开关不是任务名，只取紧跟 ./gradlew 的第一个 token",
            listOf(":app:assembleDebug"),
            gradlewTasks("先 rm -rf app/build && ./gradlew :app:assembleDebug --rerun-tasks"),
        )
        assertEquals(
            "`./gradlew --stop` 抽出来的是 --stop",
            listOf("--stop"),
            gradlewTasks("rm -rf x && ./gradlew --stop"),
        )
        assertEquals(
            "散文里那个裸词 `gradlew`（前面没有 ./）不算数",
            emptyList<String>(),
            gradlewTasks("`gradlew` 这个 wrapper 脚本本身是由 PATH 上的 java 启动的"),
        )
        val fabricated: List<String> = gradlewTasks("./gradlew :feature:assembleDebug")
        assertEquals("虚构模块也会被原样抽出来", listOf(":feature:assembleDebug"), fabricated)
        assertFalse(
            "尺子坏了：虚构的任务名应当落在白名单之外，否则白名单形同虚设",
            fabricated.all { it in knownGradlewTasks },
        )
    }

    @Test
    fun theFloorsAreSaneAndTheKnownReadingsHold() {
        assertTrue("byteFloor 必须为正", byteFloor > 0)
        assertTrue("byteFloor=$byteFloor 高于实测的 $knownBytes 字节，底线比现实还高，会立刻假红", byteFloor <= knownBytes)
        assertTrue("byteFloor=$byteFloor 连实测的一半都不到，宽到形同虚设", byteFloor * 2 >= knownBytes)
        assertTrue("splitLineFloor 必须为正", splitLineFloor > 0)
        assertTrue("splitLineFloor=$splitLineFloor 高于实测的 $knownSplitLines 行", splitLineFloor <= knownSplitLines)
        assertTrue("splitLineFloor=$splitLineFloor 连实测的一半都不到", splitLineFloor * 2 >= knownSplitLines)
        assertTrue("fenceLineFloor 必须为正", fenceLineFloor > 0)
        assertTrue("fenceLineFloor=$fenceLineFloor 高于实测的 $knownFenceLines 行围栏", fenceLineFloor <= knownFenceLines)
        assertTrue("fenceLineFloor=$fenceLineFloor 连实测的一半都不到", fenceLineFloor * 2 >= knownFenceLines)
        assertTrue("gradlewHitFloor 必须为正", gradlewHitFloor > 0)
        assertTrue(
            "gradlewHitFloor=$gradlewHitFloor 高于实测的 $knownGradlewHitCount 处命中",
            gradlewHitFloor <= knownGradlewHitCount,
        )
        assertTrue("gradlewHitFloor=$gradlewHitFloor 连实测的一半都不到", gradlewHitFloor * 2 >= knownGradlewHitCount)

        assertEquals("第 138 轮实测的字节数", 26_548, knownBytes)
        assertEquals("第 138 轮实测的 split 行数", 496, knownSplitLines)
        assertEquals("第 138 轮实测的围栏行数", 36, knownFenceLines)
        assertEquals("第 138 轮实测的围栏对数", 18, knownFencePairs)
        assertEquals("第 138 轮实测的 ./gradlew 命中处数", 16, knownGradlewHitCount)
        assertEquals("第 138 轮实测的末尾空行数", 1, knownTrailingBlanks)
        assertEquals("部署文档有九个小节", 9, knownHeadings.size)
        assertEquals("部署文档的任务名白名单是六条", 6, knownGradlewTasks.size)
    }

    private companion object {
        /** 底线：第 138 轮实测 26,548 字节，留出余量到 26,000。 */
        const val byteFloor: Int = 26_000

        /** 底线：第 138 轮实测 `split("\n")` 496 行，留出余量到 400。 */
        const val splitLineFloor: Int = 400

        /** 底线：第 138 轮实测 36 行围栏，留出余量到 30。 */
        const val fenceLineFloor: Int = 30

        /** 底线：第 138 轮实测 16 处 `./gradlew` 命中，与读数齐平（少提一处就该红）。 */
        const val gradlewHitFloor: Int = 16

        const val knownBytes: Int = 26_548

        const val knownSplitLines: Int = 496

        const val knownFenceLines: Int = 36

        const val knownFencePairs: Int = 18

        const val knownGradlewHitCount: Int = 16

        const val knownTrailingBlanks: Int = 1

        /** app 模块挂 Android 应用插件：直接写 id 的写法直接认，走版本目录别名时另核目录里的 id。 */
        const val androidApplicationPlugin: String = "com.android.application"

        const val androidApplicationAlias: String = "libs.plugins.android.application"

        /** 第 138 轮实测的末句（原文逐字，含反引号）。 */
        const val expectedTailLine: String =
            """本文只负责「怎么把工程造出来、装上去、验一遍」。第 3、5、6 节的命令与数字随每个迭代更新——更新时以 `records/04-质量与验证记录.md` 的实测值为准，不要抄旧数字。"""

        /** 第 138 轮实测的九个 `## ` 标题，逐字逐序。 */
        val knownHeadings: List<String> = listOf(
            "## 1. 环境要求",
            "## 2. 关键环境配置",
            "## 3. 日常构建",
            "## 4. 安装、启动与走查",
            "## 5. 测试",
            "## 6. Release 构建与签名",
            "## 7. 版本号、包名与数据库版本",
            "## 8. 常见故障与排查",
            "## 9. 本文与其它文档的关系",
        )

        /** 第 138 轮实测：文档里出现的全部任务名 = 这六条，双向相等。 */
        val knownGradlewTasks: Set<String> = setOf(
            ":app:testDebugUnitTest",
            ":app:connectedDebugAndroidTest",
            ":app:assembleDebug",
            ":app:compileDebugKotlin",
            ":app:assembleRelease",
            "--stop",
        )
    }
}
