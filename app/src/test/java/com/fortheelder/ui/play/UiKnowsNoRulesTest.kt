package com.fortheelder.ui.play

import com.fortheelder.domain.play.PlayCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面这一层不认识任何一个游戏的规则——这条约束的机械形式。
 *
 * 「加一个游戏不用改界面」这句话从第 12 轮起就写在 `docs/03` 里，第 12、17、22、41 轮
 * 四次要加游戏时也确实兑现了。但它一直是**读出来的**：谁顺手把
 * `when (round) { is SubtractRound -> … }` 写进 [PlayScreen]，读代码的人不一定拦得住，
 * 而那一刻「游戏的规则只在一个地方」就有了第二个出处——下一次改规则就会漏掉一处，
 * 漏掉的那一处正是用户看得见的那一处。
 *
 * 所以这一轮把它变成会失败的用例：扫 `ui/play` 下每一个 `.kt`，四组断言。
 *
 * 1. **不分支**：按局面的类型 `when` 只准出现在 [PlayVoice] 里（新加一种规则时编译器会在那里
 *    报出漏掉的分支，那是它该待的地方），其余文件里 `when (round` 与 `is …Round` 都要为零。
 * 2. **不点名实现体**：`SchulteGrid`、`FindPair`、`SubtractSeven`、`Memory`、`CardWords`
 *    这些**具体实现**的名字一个都不准出现。抽象的 `FindPairRound` 不受影响——用词边界区分，
 *    界面正当地认识 `PlayRound` 这个抽象。
 * 3. **不写死游戏名**：断言的真相取自**运行中的代码**（[PlayCatalog.games]），看这几个名字
 *    有没有被当成字符串字面量抄进界面。
 * 4. **正向锚**：[PlayScreen] 真的建在 [PlayRound.faceOf] 与 [PlayRound.markOf] 这两个口子上，
 *    不是把字写死。少了这一条，前三组都能靠「界面什么都不显示」通过。
 *
 * 豁免与边界（写在代码里，不写在记录里）：
 * - 整行注释不看。这不只是图省事：[PlayScreen] 的 KDoc 正当地解释了「规矩归领域层」，
 *   里面会提到 `Memory.BACK` 这样的名字——那是说明，不是依赖。
 * - 含 `copy-ok:` 的行跳过，给将来真有理由时留一个必须写理由的口子。
 * - **不覆盖数字字面量**。[PlayScreen] 里有 `56`、`1`、`0.35f` 这样的布局数字；把
 *   「一行摆几个」写死成 5 是另一种违规，要按值扫才判得准，本轮不做。
 * - **只覆盖 `ui/play`**，不覆盖 `ui/` 的其他目录。
 * - 第 3 组看的是「名字被写成**整段字面量**」这一种形态，**不能**改成裸子串扫描：
 *   [PlayVoice.headline] 里 `找出一样的那两张` 这句正文天然含「找出一样的」五个字，
 *   裸子串会把它当成违规。代价是把名字拆开拼起来的写法扫不到。
 *
 * 源码目录由 `app/build.gradle.kts` 里的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（照 [com.fortheelder.domain.play.PlayStaysClocklessTest]
 * 的写法）：守卫悄悄失效，比守卫报错难发现得多。
 */
class UiKnowsNoRulesTest {

    private val playRoot: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
        "com/fortheelder/ui/play",
    )

    /** 唯一允许按局面类型分支的文件：文案的分支是编译器逼出来的，不是界面自己想认识规则。 */
    private val voiceFileName = "PlayVoice.kt"

    /** 按局面类型分支的两种写法。`\b` 保证 `FindPairRound` 不会被当成 `FindPair`。 */
    private val branchOnRound = Regex("""when \(round|\bis [A-Za-z]+Round\b""")

    /** 具体实现的名字。出现在界面里，就说明界面开始认识某一个游戏了。 */
    private val implementationNames = listOf(
        "SchulteGrid",
        "FindPair",
        "SubtractSeven",
        "Memory",
        "CardWords",
    ).map { it to Regex("""\b$it\b""") }

    private fun sourceFiles(): List<File> {
        assertTrue("界面层的源码目录不存在：$playRoot", playRoot.isDirectory)
        val files = playRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
        // 防止「扫了个空目录于是零违规」这种假绿。
        assertTrue("界面层源码文件太少（${files.size} 个），守卫形同虚设", files.size >= 3)
        assertTrue(
            "没扫到 $voiceFileName，下面那条豁免就落空了",
            files.any { it.name == voiceFileName },
        )
        return files
    }

    /**
     * 逐行扫，用 [com.fortheelder.domain.play.PlayStaysClocklessTest] 的同一套豁免：
     * 整行注释与 `copy-ok:` 不看，行内 `//` 之后不看。
     */
    private fun scan(files: List<File>, onLine: (File, Int, String, String) -> Unit) {
        var scannedLines = 0
        for (file in files) {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                val trimmed = raw.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                if (raw.contains("copy-ok:")) return@forEachIndexed
                scannedLines++
                onLine(file, index, raw.substringBefore("//"), raw)
            }
        }
        assertTrue(
            "扫描到的代码行数异常（$scannedLines 行），守卫可能没真的读到源码",
            scannedLines > 300,
        )
    }

    @Test
    fun onlyTheVoiceLayerBranchesOnTheKindOfRound() {
        val files = sourceFiles()
        val offenders = mutableListOf<String>()
        var branchesInVoice = 0
        scan(files) { file, index, code, raw ->
            val hit = branchOnRound.find(code)?.value
            if (hit == null) return@scan
            if (file.name == voiceFileName) {
                branchesInVoice++
            } else {
                offenders += "${file.name}:${index + 1}  「$hit」  ${raw.trim()}"
            }
        }

        // 豁免必须是挣来的：PlayVoice 自己要是已经不分支了，就不该再豁免它。
        assertTrue(
            "$voiceFileName 里按局面类型分支的地方只剩 $branchesInVoice 处了。" +
                "若它真的不再分支，把这条豁免从本用例里删掉；否则守卫就有一个没人看得见的空档。",
            branchesInVoice >= 10,
        )

        assertTrue(
            "界面层有 ${offenders.size} 处开始按局面的类型分支了。游戏的规则只在领域层有一个出处，" +
                "界面一旦也认识规则，改规则时就会漏掉这里（见 `docs/03` 的「加一个游戏要改多少」）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScreenLayerNeverNamesAGameImplementation() {
        val files = sourceFiles()
        val offenders = mutableListOf<String>()
        scan(files) { file, index, code, raw ->
            for ((name, pattern) in implementationNames) {
                if (pattern.containsMatchIn(code)) {
                    offenders += "${file.name}:${index + 1}  「$name」  ${raw.trim()}"
                    break
                }
            }
        }
        assertTrue(
            "界面层有 ${offenders.size} 处直接点名了某一个游戏的实现。界面认识的是 `PlayRound` 这个抽象，" +
                "不认识任何一个具体游戏；点名的下一步就是「是它就这么画」：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScreenLayerNeverHardCodesAGameName() {
        val files = sourceFiles()
        val names = PlayCatalog.games.map { it.name }
        // 真相来自运行中的代码；目录要是被删空了，这条断言就退化成空跑。
        assertTrue(
            "目录里只有 ${names.size} 个游戏（$names），这条断言会退化成空跑",
            names.size >= 4,
        )
        val literals = names.map { "\"$it\"" }
        val offenders = mutableListOf<String>()
        scan(files) { file, index, code, raw ->
            for (literal in literals) {
                if (code.contains(literal)) {
                    offenders += "${file.name}:${index + 1}  $literal  ${raw.trim()}"
                    break
                }
            }
        }
        assertTrue(
            "界面层有 ${offenders.size} 处把游戏名抄成了字符串字面量。名字是目录里的数据" +
                "（[PlayCatalog.games]，选择页上那几张卡片读的就是它）；抄一份进来，" +
                "改名时屏幕上就会剩下一处旧名字：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScreenLayerShowsWhatTheRoundTellsIt() {
        val files = sourceFiles()
        var faceReads = 0
        var markReads = 0
        scan(files) { _, _, code, _ ->
            faceReads += Regex("""round\.faceOf\(""").findAll(code).count()
            markReads += Regex("""round\.markOf\(""").findAll(code).count()
        }
        assertTrue(
            "界面层一次都没有问过 `round.faceOf(...)`：格子上写什么字如果不向领域层要，" +
                "就只能由界面自己猜——那正是本用例要拦的那件事（第 41 轮分出来的口子）。",
            faceReads >= 1,
        )
        assertTrue(
            "界面层一次都没有问过 `round.markOf(...)`：每格画成什么样必须按**位置**向领域层要，" +
                "按字查会把盘面上两个字相同的格子一起改掉。",
            markReads >= 1,
        )
    }
}
