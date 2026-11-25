package com.fortheelder.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「domain/ 下公开的 `fun` 到底有没有人用」的机械守卫（第 101 轮新增）。
 *
 * ## 为什么要有它
 *
 * `records/04` 与 `docs/03` 里记了好几轮的一类旧账：某个**公开**函数写出来之后
 * 生产代码里一次都没调过，只有测试在用它。第 99 轮记账时写的是「生产代码零调用，
 * 本轮没有处置」，第 100 轮也没再动。反复人工 grep 数这件事有三个毛病：
 *
 * 1. 数一遍就得手工读一遍全仓，数完不留痕，下一轮还得重数；
 * 2. 「只被测试用」与「被生产代码用」在人眼里长得很像——测试也是 `名字(`；
 * 3. 公开面一旦悄悄长出一个真死口子，**没有任何一条既有用例会红**。
 *
 * 这一族守卫已经在别处立过同形的尺子（`docs/07` 的风险 R-10）：
 * `CopyRulesPlacementTest` 管「表有几张、谁在用它」、`UiCopyPlacementTest` 管
 * 「界面文案摆在哪一屏」。这里补的是**源码可见性**这一格：写得出 `fun`，就得说得出
 * 谁在调它。第 101 轮第一次落地就报出三处：两处照单处置（删 / 收窄），
 * 一处接上了本来就重复的那段生产调用（见 `records/04` 第 101 轮）。
 *
 * ## 它怎么判
 *
 * 把 `app/src/main/java/com/fortheelder/domain/` 下每个 `.kt` 文件**剥掉两种注释**之后，
 * 逐行找出所有 `fun 名字(` 的**声明**；声明出现之前那一段文本里有 `private` / `internal` /
 * `override` 的不算「公开面」（前两者本来就不对外，第三者的可见性由被覆盖的那一处决定）。
 * 剩下的每一个名字，都去**整个 `app/src/main`**（同样剥注释）里数 `名字\s*\(` 的出现次数，
 * 再**减去声明本身的那些出现**——减完必须 ≥ 1。减成 0 就是 red：
 *
 * ```text
 * domain/companion/Anchor.kt:29  byLabel
 * ```
 *
 * 只有 [noPublicDomainFunctionIsCalledNowhereInMain] 一条主判据，外加
 * [theScannerReallyReadTheWholeDomainPackage] 一条**自检**：正则写错、目录挪走、
 * 读成空文件，都会让主判据「零处 offender ⇒ 全绿」，所以得先把扫描范围钉住。
 *
 * ## 落地时踩过的三个坑（都写了断言或注释，别再踩回去）
 *
 * 1. **整段替换块注释会把行号吃掉**。块注释的原文里含着换行，替换成空串之后
 *    后面所有声明的行号都前移。`InterventionWindow.kt` 的 `quietSpanOf` 当时真身在
 *    第 251 行（本轮去重后移到第 253 行），第一版报出来的是 167——读的人拿着 167 去找会找不到地方。
 *    现在把注释里的换行原样补回去（见 [stripComments]）。
 * 2. **接收者类型会被当成函数名**。`fun DailyRhythm.contains(…)` 用 `\bfun\s+(\w+)`
 *    抓到的是 `DailyRhythm`；报出来的 offender 会是「一个叫 `LocalTime` 的函数」。
 *    现在的声明正则吃掉了 `fun <T> ` 与 `fun 接收者.` 两种前缀。
 * 3. **调用点几乎都带点**。`config.quietSpanOf(…)`、`game.deal(…)` 前面都是 `.`，
 *    第一版用 `(?<![\w.])` 把这些**全部**排除掉了，一次跑出 40 处假 offender。
 *    现在只排除紧贴字母数字的情况（`mydeal(` 不算）。
 *
 * ## 它不保证什么
 *
 * - **只管 `domain/` 一包**。`ui/`、`data/` 下的公开函数不归它管——那两处的公开面
 *   本来就更大（`@Composable`、仓库接口），一刀切会当场红成一片，没有信息量。
 * - **按名字数调用点，不按声明点归属**。`deal` 在 `domain/play/` 下有 5 处、`tap` 有 7 处，
 *   全 main 里「公开且同名声明多份」的名字共 16 个（`build/r101/sim101.py` 第 101 轮实测）。
 *   这些名字合并计数：一个被调就会盖住另一个没人调。要按声明点归属得做接收者类型推断，
 *   超出这个守卫的力气；真要看的时候用一次性脚本逐个列调用点。
 * - **不判「包外必须有调用点」**。今天 `AnchorSlot.covers`、`CopyRules.violations` 这些
 *   都只在同一包里被调；再强一条规则它们会误红，而它们并不是死口子。
 * - **剥注释是弱的**。按行把 `//` 之后整段去掉，分不出这个 `//` 是在字符串字面量里
 *   还是在代码里（`CopyRulesPlacementTest` 第 5 条同一个毛病）。`main/` 今天没有
 *   字符串里含 `//` 的行（第 95 轮探针实测 0 行），所以没误伤；哪天有了，这里会先红。
 * - **不判「调用点算不算真调用」**。`名字(` 出现在同一个表达式的两端、
 *   出现在 `@Suppress` 的注解文本里，都照数。它判的是**有没有人提到它**，
 *   不是「这次调用有没有意义」。
 * - **不代替删代码的判断**。它只说「这个名字在 main 里没人提」，删掉、收窄成
 *   `internal`、还是把它接到一处重复的实现上，是人定的（第 101 轮三条各占一种）。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（纪律同 `CopyRulesPlacementTest` / `ui/UiCopyPlacementTest`）。
 */
class DomainSurfaceGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val domainDir: File = File(mainSrc, "com/fortheelder/domain")

    private fun ktFilesUnder(dir: File): List<File> =
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

    private val mainFiles: List<File> = ktFilesUnder(mainSrc)

    private val domainFiles: List<File> = ktFilesUnder(domainDir)

    /**
     * 一处 `fun` 声明的名字。三种前缀都要吃掉，否则报出来的是别的东西：
     * `fun <T> f(` → `f`、`fun Foo.bar(` → `bar`；`fun interface Foo {` 不匹配
     * （它不是函数，且必须紧跟 `(`，所以第 101 轮那个 `interface` 假声明自动消失）。
     */
    private val funKeyword = Regex(
        """\bfun\s+(?:<[^<>]*>\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>\[\], ]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
    )

    /** 声明之前出现这些词，说明它不是公开面。 */
    private val notPublic = Regex("""\b(private|internal|override)\b""")

    /** `/** … */`，含换行。 */
    private val blockComment = Regex("""/\*[\s\S]*?\*/""")

    /** `// …` 到行尾。 */
    private val lineComment = Regex("""//[^\n]*""")

    /**
     * 剥掉两种注释，**行号保持不变**。
     *
     * 块注释整段替换成同样多的换行符，而不是空串：否则块注释后面的每一个声明
     * 报出来的行号都比真身小，offender 列表就没法按图索骥了。
     */
    private fun stripComments(text: String): String {
        val noBlock = blockComment.replace(text) { m -> "\n".repeat(m.value.count { it == '\n' }) }
        return lineComment.replace(noBlock, "")
    }

    /**
     * main/ 每一份源码剥掉注释之后的正文，只读一次。
     *
     * 这一段必须排在 [blockComment] / [lineComment] 之后：Kotlin 按书写顺序初始化
     * 属性，正则还没赋值就调 [stripComments] 会当场 NPE（第 101 轮第一版就是这么挂的）。
     */
    private val strippedMain: List<Pair<File, String>> =
        mainFiles.map { it to stripComments(it.readText()) }

    private data class Decl(val name: String, val file: File, val line: Int)

    /**
     * main/ 里**每一处** `fun 名字(`（不论可见性、含 `override`）。
     * 它们的用处是从 `名字(` 的总出现次数里剔掉「声明本身」这一处。
     */
    private val allDecls: List<Decl> = strippedMain.flatMap { (file, text) ->
        text.lineSequence().toList().mapIndexedNotNull { idx, line ->
            funKeyword.find(line)?.let { Decl(it.groupValues[1], file, idx + 1) }
        }
    }

    /** domain/ 下**公开**的函数声明：既非 `private`、也非 `internal`，且不是 `override`。 */
    private val publicDomainDecls: List<Decl> = domainFiles.flatMap { file ->
        val text = stripComments(file.readText())
        text.lineSequence().toList().mapIndexedNotNull { idx, line ->
            val found = funKeyword.find(line) ?: return@mapIndexedNotNull null
            val before = line.substring(0, found.range.first)
            if (notPublic.containsMatchIn(before)) {
                null
            } else {
                Decl(found.groupValues[1], file, idx + 1)
            }
        }
    }

    /**
     * 整个 main/ 里 `名字(` 出现了几次（含声明那一处）。
     *
     * 只排除前面紧贴字母数字的情况（`mydeal(` 不算 `deal(`）；**不排除前面的 `.`**，
     * 因为 `config.quietSpanOf(…)` 这种带接收者的调用才是常态。
     */
    private fun occurrencesOf(name: String): Int {
        val pattern = Regex("""(?<!\w)""" + Regex.escape(name) + """\s*\(""")
        return strippedMain.sumOf { (_, text) -> pattern.findAll(text).count() }
    }

    /** 减去声明本身之后，这个名字在 main/ 里还有几个调用点。 */
    private fun callSitesOf(name: String): Int =
        occurrencesOf(name) - allDecls.count { it.name == name }

    @Test
    fun noPublicDomainFunctionIsCalledNowhereInMain() {
        val offenders = publicDomainDecls
            .filter { callSitesOf(it.name) < 1 }
            .map { "${it.file.relativeTo(mainSrc).path.replace('\\', '/')}:${it.line}  ${it.name}" }

        assertEquals(
            "domain/ 下这些公开函数在 main/ 里一个调用点都没有（声明本身不算）：\n" +
                offenders.joinToString("\n") +
                "\n\n处置方向：真没人要就删掉；只是测试口就收窄成 internal（仓内惯例）；" +
                "本就要给人用、只是一直没接上，就把它接到那处重复的实现上。",
            0,
            offenders.size,
        )
    }

    /**
     * 自检：扫描范围钉住。
     *
     * 五个下限取的是第 101 轮实测读数的**七到九成**（实测 main 79 份 / domain 24 份 /
     * 321 处 `fun` 声明 / 66 个公开函数 / 63 个公开函数数得出调用点）。取九成左右的意思是：
     * 扫描范围缩水一成以上就红，而正常增删几个函数不会红。
     */
    @Test
    fun theScannerReallyReadTheWholeDomainPackage() {
        assertTrue(
            "只扫到 ${mainFiles.size} 个 main 源码文件（第 101 轮实测 79），扫描范围可疑",
            mainFiles.size >= 70,
        )
        assertTrue(
            "domain/ 下只扫到 ${domainFiles.size} 个文件（第 101 轮实测 24），扫描范围可疑",
            domainFiles.size >= 22,
        )
        assertTrue(
            "全 main/ 只认出 ${allDecls.size} 处 fun 声明（第 101 轮实测 321），声明正则可疑",
            allDecls.size >= 290,
        )
        assertTrue(
            "domain/ 下只认出 ${publicDomainDecls.size} 个公开函数（第 101 轮实测 66），公开面口径可疑",
            publicDomainDecls.size >= 55,
        )
        val wired = publicDomainDecls.count { callSitesOf(it.name) >= 1 }
        assertTrue(
            "公开函数里只有 $wired 个数得出调用点（第 101 轮实测 63），调用点正则可疑",
            wired >= 50,
        )
    }
}
