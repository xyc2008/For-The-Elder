package com.fortheelder.data.prefs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「设置面板说到做到」的机械守卫（第 154 轮新增）。
 *
 * ## 为什么要有它
 *
 * [SettingsStore] 是整个应用唯一一处持久化设置的出入口：六个字段（[AppSettings]）、
 * 一个只读的 `settings` 流、十个读/写方法、七个存储键。它长得太像「一份契约」了，
 * 而契约最容易出的毛病有三种，三种都**不会让任何既有用例变红**：
 *
 * 1. **加了字段没人读**。往 [AppSettings] 里加一个字段、在 `dataStore.data.map { … }` 里
 *    把它读出来，然后就没有下文了——界面从来不显示它。用户会觉得设置项「打开了没用」。
 * 2. **加了写方法没人调**。`setQuietOn` / `clearQuiet` 这种一整对方法，接了界面才成立。
 *    写完方法忘了接线，编译通过、单测通过，功能却不存在。
 * 3. **加了键没接线**。往 `private companion object` 里添一个 `KEY_*`，但谁也没把它
 *    读出来也没写进去——这个键就只是个字符串常量，占着位置冒充已实现功能。
 *
 * 这三件事此前靠人工 grep 数（`records/04` 里第 148、152 两轮都记了「本轮手工核过一遍」），
 * 数完不留痕、下一轮还得重数。这一族守卫在别处已经立过同形的尺子（`docs/07` 的风险 R-10）：
 * `domain/DomainSurfaceGuardTest` 管「domain 下的公开函数有没有人调」，
 * 这里补的是**设置面板这一格**。
 *
 * ## 它怎么判
 *
 * 判据不写死名单，而是**从 [SettingsStore] 自己的源码里解析出公开面**——这样今天加一个字段、
 * 明天加一个方法，守卫自动把它们纳入管辖，不需要有人记得改测试：
 *
 * - `data class AppSettings(` 之后到第一个独占一行的 `)` 之间，每一个 `val 名字:` 是**字段**；
 *   它在 `app/src/main` 里（**排除 [SettingsStore] 本文件**，剥掉两种注释后）必须至少有一处
 *   **带点的访问**（`\.\s*名字\b`）。要求「带点」是关键：不带点会把别处同名的**形参、
 *   局部变量、属性声明**也算成消费者（例如 `GreetingEngine` 里的 `val address: String?`），
 *   那样守卫就废了——它得能分辨「某处读了这份设置」和「某处恰好叫这个名字」。
 * - `class SettingsStore(` 之后，每一个 `fun 名字(` 是**公开方法**；每个方法在同一个范围里
 *   必须至少有一处 `名字\s*(` 的**真调用点**（剥注释后再数，所以 KDoc 里的
 *   `[SettingsStore.setAddress]` 这种引用不会假充调用点）。同一个类体里每一个 `val 名字:`
 *   是**公开属性**（今天只有 `settings` 一个），同样要求至少一处 `\.\s*名字`。
 * - 同一个类体里每一个 `val KEY_… =` 是**存储键**；它在 [SettingsStore] 本文件里必须
 *   出现 **≥ 2** 次——一次是声明，另一次才是真用（读或写）。只出现一次就是「加了键没接线」。
 *
 * 报出来的 offender 一律带着出处（`方法名` / `字段名`），因为这一族守卫的价值全在于
 * 「红了以后知道去哪儿改」：
 *
 * ```text
 * fun setQuietOn
 * quietEpochDay：在 SettingsStore.kt 里只出现 1 次
 * ```
 *
 * 外加 [theScannerReallyReadTheWholeSettingsPackage] 一条**自检**：解析正则写错、目录挪走、
 * 读成空文件，都会让三条主判据「零处 offender ⇒ 全绿」，所以得先把解析出来的规模钉住
 * （形制照抄 `domain/DomainSurfaceGuardTest`）。
 *
 * ## 它不保证什么
 *
 * - **按名字数，不按接收者类型归属**。`callSitesOf` 只认 `名字(`，不认它是被谁调的；
 *   同名函数在别处也存在时会合并计数，一个被调就会盖住另一个没人调。要按接收者归属得做
 *   类型推断，超出这个守卫的力气。
 * - **只判「有没有人提到」，不判「这次调用有没有意义」**。`名字(` 出现在同一表达式的两端、
 *   出现在 `@Suppress` 的注解文本里，都照数。它保证的不是「功能对」，而是「没有哪个公开
 *   入口是彻底没人接的」——「接了但接错」是另一类毛病，归界面层用例与人工走查管。
 * - **只管 `data/prefs` 这一包**。别的层（`ui/`、`data/rhythm/`…）的公开面不归它管，
 *   一刀切会当场红成一片，没有信息量。
 * - **剥注释是弱的**。按行去掉 `//` 之后整段、按 `/* */` 整段去掉（补回等量换行以保行号），
 *   分不出这个 `//` 是在字符串字面量里还是在代码里（`domain/DomainSurfaceGuardTest` 同一个毛病）。
 *   今天 `main/` 下没有这样的行，所以没误伤；哪天有了，这里会先红。
 * - **不判「字段被读的时候读的是不是这一份设置」**。`\.\s*字号\b` 这种要求已经滤掉了
 *   大部分同名形参，但两处都叫 `fontSize` 的属性仍然会互相顶替。
 * - **不代替「这块设置该不该存在」的判断**。它只说「没人用」，至于该删、该收窄成 `internal`、
 *   还是该把界面接上，是人定的。
 *
 * 源码目录由 `app/build.gradle.kts` 的系统属性 `fortheelder.mainSrc` 提供；属性缺失时
 * 这条用例**直接失败**而不是跳过（纪律同 `domain/DomainSurfaceGuardTest` /
 * `domain/companion/CopyRulesPlacementTest`）。
 */
class SettingsSurfaceGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val storeFile: File = File(mainSrc, "com/fortheelder/data/prefs/SettingsStore.kt")

    private fun ktFilesUnder(dir: File): List<File> =
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

    private val mainFiles: List<File> = ktFilesUnder(mainSrc)

    /** 块注释（`/*` 起、`*/` 止），含换行。 */
    private val blockComment = Regex("""/\*[\s\S]*?\*/""")

    /** `// …` 到行尾。 */
    private val lineComment = Regex("""//[^\n]*""")

    /**
     * 剥掉两种注释，**行号保持不变**。
     *
     * 块注释整段替换成同样多的换行符而不是空串：注释里的换行如果不补回去，
     * 后面每一行的行号都会前移，报出来的位置就对不上了（`domain/DomainSurfaceGuardTest`
     * 第 101 轮踩过这个坑）。
     */
    private fun stripComments(text: String): String {
        val noBlock = blockComment.replace(text) { m -> "\n".repeat(m.value.count { it == '\n' }) }
        return lineComment.replace(noBlock, "")
    }

    /**
     * [SettingsStore] 剥注释之后的正文。
     *
     * 下面几个属性都从它派生，所以这一句必须排在 [blockComment] / [lineComment] 之后：
     * Kotlin 按书写顺序初始化属性，正则还没赋值就调 [stripComments] 会当场 NPE。
     */
    private val storeText: String = stripComments(storeFile.readText())

    /** 除 [SettingsStore] 本文件外，全 `app/src/main` 剥注释之后的正文，拼成一段。 */
    private val otherMainText: String = mainFiles
        .filter { it != storeFile }
        .joinToString("\n") { stripComments(it.readText()) }

    /** `val 名字:` —— 字段声明与公开属性声明共用这一条。 */
    private val typedValDecl = Regex("""^\s*val\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""")

    /** `fun 名字(` —— 可带 `suspend`。 */
    private val funDecl = Regex("""^\s*(?:suspend\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")

    /** `val KEY_… =` —— 存储键声明（不带类型标注，与 [typedValDecl] 天然不重叠）。 */
    private val keyDecl = Regex("""^\s*val\s+(KEY_[A-Z0-9_]+)\s*=""")

    /** [SettingsStore] 的类体（从 `class SettingsStore(` 那一行到文件末尾）。 */
    private val storeClassBody: String = storeText.lineSequence()
        .dropWhile { !it.contains("class SettingsStore(") }
        .joinToString("\n")

    /** [AppSettings] 的六个字段名，按声明顺序。 */
    private val appSettingsFields: List<String> = storeText.lineSequence()
        .dropWhile { !it.contains("data class AppSettings(") }
        .drop(1)
        .takeWhile { it.trim() != ")" }
        .mapNotNull { typedValDecl.find(it)?.groupValues?.get(1) }
        .toList()

    /** [SettingsStore] 的公开属性名（今天只有只读的 `settings`）。 */
    private val storeProperties: List<String> = storeClassBody.lineSequence()
        .mapNotNull { typedValDecl.find(it)?.groupValues?.get(1) }
        .toList()

    /** [SettingsStore] 的公开读/写方法名。 */
    private val storeMethods: List<String> = storeClassBody.lineSequence()
        .mapNotNull { funDecl.find(it)?.groupValues?.get(1) }
        .toList()

    /** `private companion object` 里的存储键名。 */
    private val storageKeys: List<String> = storeClassBody.lineSequence()
        .mapNotNull { keyDecl.find(it)?.groupValues?.get(1) }
        .toList()

    /**
     * 别处有几处 `名字(` 的**真调用点**。
     *
     * 只排除前面紧贴字母数字的情况（`mysetAddress(` 不算）；前面的 `.` **不排除**——
     * `container.settings.setAddress(…)` 这种带接收者的调用才是常态。
     */
    private fun callSitesOf(name: String): Int =
        Regex("""(?<!\w)""" + Regex.escape(name) + """\s*\(""").findAll(otherMainText).count()

    /** 别处有几处**带点**的访问（`\.\s*名字\b`），用来判「这份设置有没有人读」。 */
    private fun dottedReadsOf(name: String): Int =
        Regex("""\.\s*""" + Regex.escape(name) + """\b""").findAll(otherMainText).count()

    /** [SettingsStore] 本文件里这个名字出现了几次（含声明那一处）。 */
    private fun mentionsInStore(name: String): Int =
        Regex("""(?<!\w)""" + Regex.escape(name) + """\b""").findAll(storeText).count()

    @Test
    fun everySettingsFieldIsReadSomewhereOutsideTheStore() {
        val offenders = appSettingsFields.filter { dottedReadsOf(it) < 1 }

        assertEquals(
            "[AppSettings] 这些字段在 app/src/main 里（SettingsStore.kt 之外）一个读取点都没有：\n" +
                offenders.joinToString("\n") +
                "\n\n处置方向：真要用就把界面接上（设置项 / 问候语 / 作息画像任一处）；" +
                "不要了就从数据模型里删掉——留在模型里只读不用的字段，等于界面上摆了个假开关。",
            0,
            offenders.size,
        )
    }

    @Test
    fun everySettingsEntryPointIsCalledSomewhereInMain() {
        val offenders = storeProperties.filter { dottedReadsOf(it) < 1 }.map { "val $it" } +
            storeMethods.filter { callSitesOf(it) < 1 }.map { "fun $it" }

        assertEquals(
            "[SettingsStore] 这些公开入口在 app/src/main 里（SettingsStore.kt 之外）一个调用点都没有：\n" +
                offenders.joinToString("\n") +
                "\n\n处置方向：写完没人调，说明功能没接上（编得过、测得过、用得着的时候不生效）；" +
                "只是给同包或测试用的就收窄成 internal（仓内惯例）；真的不用了就删掉。",
            0,
            offenders.size,
        )
    }

    @Test
    fun everyStorageKeyIsBothDeclaredAndUsedInTheStore() {
        val offenders = storageKeys
            .filter { mentionsInStore(it) < 2 }
            .map { "$it：在 SettingsStore.kt 里只出现 ${mentionsInStore(it)} 次" }

        assertEquals(
            "这些存储键只声明、没接线（只出现一次就是「加了个字符串常量冒充已实现功能」）：\n" +
                offenders.joinToString("\n") +
                "\n\n处置方向：要么在 settings 流里读出来、在某个 set… 里写进去（≥ 2 次）；" +
                "要么把键连同它的读写一起删掉。",
            0,
            offenders.size,
        )
    }

    /**
     * 自检：解析范围钉住。
     *
     * 下限取第 154 轮实测读数的**七到九成**（实测：`app/src/main` 81 份源码 /
     * [AppSettings] 6 个字段 / `settings` 1 个公开属性 / [SettingsStore] 10 个公开方法 /
     * 7 个存储键 / 6 个字段数得出读取点 / 10 个方法数得出调用点）。取这个区间的意思是：
     * 解析范围缩水一成以上就红，而正常增删一两个字段或方法不会红。
     */
    @Test
    fun theScannerReallyReadTheWholeSettingsPackage() {
        assertTrue(
            "只扫到 ${mainFiles.size} 个 main 源码文件（第 154 轮实测 81），扫描范围可疑",
            mainFiles.size >= 70,
        )
        assertTrue(
            "data class AppSettings 只解析出 ${appSettingsFields.size} 个字段（第 154 轮实测 6），解析口径可疑",
            appSettingsFields.size >= 5,
        )
        assertTrue(
            "SettingsStore 只解析出 ${storeProperties.size} 个公开属性（第 154 轮实测 1），解析口径可疑",
            storeProperties.size >= 1,
        )
        assertTrue(
            "SettingsStore 只解析出 ${storeMethods.size} 个公开方法（第 154 轮实测 10），解析口径可疑",
            storeMethods.size >= 8,
        )
        assertTrue(
            "只解析出 ${storageKeys.size} 个存储键（第 154 轮实测 7），解析口径可疑",
            storageKeys.size >= 6,
        )

        val readFields = appSettingsFields.count { dottedReadsOf(it) >= 1 }
        assertTrue(
            "字段里只有 $readFields 个数得出读取点（第 154 轮实测 6），读取点正则可疑",
            readFields >= 5,
        )

        val wiredMethods = storeMethods.count { callSitesOf(it) >= 1 }
        assertTrue(
            "公开方法里只有 $wiredMethods 个数得出调用点（第 154 轮实测 10），调用点正则可疑",
            wiredMethods >= 8,
        )

        val usedKeys = storageKeys.count { mentionsInStore(it) >= 2 }
        assertTrue(
            "存储键里只有 $usedKeys 个数得出第二次出现（第 154 轮实测 7），键正则可疑",
            usedKeys >= 6,
        )
    }
}
