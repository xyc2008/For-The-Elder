package com.fortheelder.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 相邻可点元素之间的**净间隙**守卫。
 *
 * ## 为什么单为「间隙」开一条守卫
 *
 * 上一轮（第 62 轮）在设备上量到一组数：五句现成的话每一句的可点框高 168 px，而**相邻两句之间
 * 只有 21 px**。168 px ÷ 21 px ≈ 8，也就是说两个目标之间留出的空隙不到目标高度的八分之一。
 * 21 px 出自 `SectionCard` 内层的 `spacedBy(gapSm)` = 8dp × 420dpi / 160 = 21px。
 *
 * 目标够大并不等于点得准。Nielsen Norman Group 的《Touch Targets on Touchscreens》
 * 把这件事单列成一小节「**Mind the Gap——Crowding Causes Errors**」：堆叠起来的按钮或链接列表，
 * 出错的主要来源就是**彼此挨得太近**；同一篇的措辞是目标先要够大，**然后**还要留出足够的间距。
 * 手指落点不是一个点，是一个约 1.6–2cm 的接触面（同一篇引 MIT Touch Lab），
 * 边缘相碰的目标会把这次触摸判给隔壁。55 岁以上人群的手抖与触觉反馈下降会放大这件事
 * （同一篇：目标要「更能容忍一只手抖」）。
 *
 * ## 这条守卫钉不住什么（照实写）
 *
 * 那篇文章**没有给出间隙的数值下限**——它只指出「太挤会出错」。所以 [FLOOR_DP] = 12dp 是
 * **本项目的取值**，不是引文里的数字，依据只有两条：一是实测的 21 px 在设备上确实太挤；
 * 二是它必须明显大于原值 `gapSm` = 8dp（21px），又不必大过目标本身。
 * 按 1in = 160dp 的名义换算，12dp ≈ 4.7mm，约是 64dp 目标高度的五分之一。
 *
 * 同样照实说：这是**名义换算**，不是拿尺子量真实设备；真机上 dp 与物理毫米的偏差取决于厂商
 * 是否如实上报 `xdpi`。这条用例只挡「数值被改小」这一类回归，挡不住它在该屏幕上到底够不够宽。
 *
 * ## 断言的四个层次
 *
 * 1. **常量层**：`Dimens.kt` 里得有 `minTouchGap`、`primaryActionHeight`，且各自过线。
 * 2. **接线层**：常量对了却没接到按钮上，界面照样是旧的。所以主按钮必须用 `primaryActionHeight`。
 * 3. **结构层**：三处最要紧的相邻可点栈必须真的被 [ElderDimens.minTouchGap] 撑开——
 *    外壳的 footer 栈、首页那一组入口行、说话页五句现成的话。结构断言按**缩进块**做，
 *    不按字面相邻做，所以往块里加东西不会误报。
 * 4. **判据层**（第 66 轮加的）：判据收紧成「**整列都是可点目标**才整列换栈；列里混着不可点的
 *    正文行时，只换列尾那一段按钮」。第 4 节钉的就是这个判据的两面——说话页三条区块与起点卡
 *    问时段那一支的按钮段换了栈（正面），同一列里的正文行仍留在 8dp、而只有 1 个可点目标的
 *    `AnchorBlock` 不许跟着换（反面）。
 */
class TouchSpacingTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    private val themeDir = File(mainSrc, "com/fortheelder/ui/theme")
    private val uiDir = File(mainSrc, "com/fortheelder/ui")

    /** 1cm 的 dp 名义换算下界，与 [PaletteContrastTest.touchTargetsReachOneCentimetre] 同一口径。 */
    private val oneCentimetreDp = 160.0 / 2.54 // ≈ 62.99dp

    /**
     * 相邻可点元素之间的净间隙下限，**本项目的取值**（那篇 NN/g 文章没给数字，理由见类注释）。
     */
    private val floorDp = 12.0

    /** `val 名字 = 数字.dp`。只认这一种写法。 */
    private val dimens: Map<String, Double> by lazy {
        Regex("val\\s+(\\w+)\\s*=\\s*([0-9.]+)\\.dp")
            .findAll(File(themeDir, "Dimens.kt").readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
    }

    private fun 尺寸(name: String): Double =
        dimens[name] ?: error("Dimens.kt 里没有 $name，这条守卫失去意义")

    private fun 主源码(相对路径: String): File = File(mainSrc, 相对路径)

    private fun text(相对路径: String): String = 主源码(相对路径).readText(Charsets.UTF_8)

    /**
     * 取从含 [marker] 的那一行起、到与它**同缩进**的收尾 `}` 为止的一整块源码。
     *
     * 用缩进定界而不是数括号或正则匹配：Kotlin 里 `{` 也会出现在 lambda 实参与字符串中，
     * 正则数不过来；缩进块是这份代码库的稳定形状（`ktlint` 在构建里卡着）。
     * 块里再加几行不会影响定界，所以这些结构断言不会因为无关改动变红。
     */
    private fun blockAt(source: String, marker: String): String {
        val lines = source.lines()
        val start = lines.indexOfFirst { it.contains(marker) }
        require(start >= 0) { "源码里找不到「$marker」这一行，守卫失去意义" }
        val indent = lines[start].takeWhile { it == ' ' }.length
        val end = (start + 1 until lines.size).firstOrNull { i ->
            val line = lines[i]
            line.takeWhile { it == ' ' }.length <= indent && line.trim() == "}"
        } ?: error("「$marker」那一块找不到同缩进的收尾 `}`，缩进可能被改乱了")
        return lines.subList(start, end + 1).joinToString("\n")
    }

    // ---- 1. 常量层 ------------------------------------------------------------------

    /**
     * 间隙下限本身。
     *
     * 这条断言的方向只有一个：**不许再往下调**。往下调回到 8dp 就等于回到实测的 21px，
     * 而那正是这一轮要解决的问题。
     */
    @Test
    fun 相邻可点元素的间隙过了自定的下限() {
        val gap = 尺寸("minTouchGap")
        assertTrue(
            "minTouchGap = ${gap}dp，小于本项目的下限 ${floorDp}dp。" +
                "上一轮实测 8dp 只有 21px，相邻两句现成话的可点框几乎相接——" +
                "NN/g《Touch Targets on Touchscreens》的「Mind the Gap」一节把这类误触归因于间距不足",
            gap >= floorDp,
        )
        assertTrue(
            "minTouchGap = ${gap}dp 不大于原来的 gapSm = ${尺寸("gapSm")}dp，" +
                "那这一轮什么都没改",
            gap > 尺寸("gapSm"),
        )
    }

    /**
     * 主操作按钮比次要按钮高。
     *
     * 出处是同一篇 NN/g 文章：`primary calls to action often deserve greater visual prominence
     * and thus deserve a larger tap target`。主次靠的不只是填色与描边，尺寸也要分层。
     *
     * 放大**不能**以跌破 1cm 为代价，所以这里同时卡住 1cm 的名义换算下界；
     * 次要按钮仍走 [ElderDimens.buttonHeight]，由 [PaletteContrastTest] 卡 1cm。
     */
    @Test
    fun 主按钮比次按钮高且仍然过一厘米线() {
        val 主 = 尺寸("primaryActionHeight")
        val 次 = 尺寸("buttonHeight")
        assertTrue(
            "primaryActionHeight = ${主}dp 不大于 buttonHeight = ${次}dp，主次没有分层",
            主 > 次,
        )
        assertTrue(
            "primaryActionHeight = ${主}dp 小于 1cm 的名义换算值 ${oneCentimetreDp}dp——" +
                "为了分层把主按钮做小了，方向反了",
            主 >= oneCentimetreDp,
        )
    }

    /** 三个做过尺寸推断的常量都得留在 1cm / 100dp 线之上，改小任何一个是回归。 */
    @Test
    fun 触控目标下限一个都没被这轮改动带下去() {
        for (name in listOf("minTouchTarget", "buttonHeight", "primaryActionHeight")) {
            val value = 尺寸(name)
            assertTrue("$name = ${value}dp 小于 1cm 的名义换算值 ${oneCentimetreDp}dp", value >= oneCentimetreDp)
        }
        assertTrue("entryCardMinHeight 被带矮了", 尺寸("entryCardMinHeight") >= 100.0)
    }

    // ---- 2. 接线层 ------------------------------------------------------------------

    /**
     * 常量对了但没接到按钮上，界面等于没改。上一轮那次「描边颜色对了却没接进 scheme」
     * 就是这么漏的，所以这里同样按源码级接线上断言。
     */
    @Test
    fun 主按钮真的用了那个更高的常量() {
        val 源码 = text("com/fortheelder/ui/common/ElderComponents.kt")
        val 主按钮块 = blockAt(源码, "fun PrimaryWideButton(")
        assertTrue(
            "PrimaryWideButton 里没有 ElderDimens.primaryActionHeight：常量加了却没接上，按钮还是 64dp",
            主按钮块.contains("ElderDimens.primaryActionHeight"),
        )

        val 次按钮块 = blockAt(源码, "fun SecondaryWideButton(")
        assertTrue(
            "SecondaryWideButton 里没有 ElderDimens.buttonHeight",
            次按钮块.contains("ElderDimens.buttonHeight"),
        )
        assertTrue(
            "SecondaryWideButton 也用了 primaryActionHeight，主次按钮一样高就等于没分层",
            !次按钮块.contains("ElderDimens.primaryActionHeight"),
        )
    }

    // ---- 3. 结构层：三处最要紧的相邻可点栈 --------------------------------------------

    /**
     * 外壳的 footer 栈。
     *
     * 全项目每一个屏的底部「主按钮 + 次按钮」都排在这一列里（引导页第 2 页的「下一步」与
     * 「回去看上一页」、说话页的「这一步做完了」与「先不记」……）。以前这里是 `gapSm` = 8dp，
     * 也就是两个相邻按钮之间只有 21px——同一屏里最容易连点错的两处。
     */
    @Test
    fun 每个屏底部的按钮栈不再是八dp() {
        val 源码 = text("com/fortheelder/ui/common/ElderComponents.kt")
        val 栈 = blockAt(源码, "TargetStack(")
        assertTrue(
            "ElderScreen 的 footer 没有放进 TargetStack：底部主次两个按钮之间还是 8dp(21px)。" +
                "这一列属于每一个屏，改这里比逐个屏改按钮更不容易漏",
            栈.contains("footer()"),
        )
        assertTrue(
            "footer 那一列不是 TargetStack（没有走 minTouchGap）",
            栈.contains("TargetStack("),
        )
    }

    /**
     * 五句现成的话。
     *
     * 这是上一轮**量到问题**的那一处：可点框高 168 px、间隙只有 21 px。
     * 它同时也是「一时想不出说什么」这一条需求的落地处，点错一句就等于答错了用户根本没想说的事。
     */
    @Test
    fun 五句现成的话之间留出了间隙() {
        val 源码 = text("com/fortheelder/ui/talk/TalkScreen.kt")
        val 栈 = blockAt(源码, "TargetStack {")
        assertTrue(
            "五句现成的话没有被 TargetStack 包住：它们仍靠 SectionCard 内层的 8dp 排开，" +
                "也就是实测 21px 的那一组",
            栈.contains("for (sentence in TalkExamples.sentences)"),
        )
        assertTrue(
            "TargetStack 里没有 ActionRow——包住了列表却没有把每一句做成整行可点，等于没包",
            栈.contains("ActionRow(text = sentence"),
        )
    }

    /** 首页「您自己的节奏」那一组的标题与两条入口行。 */
    @Test
    fun 首页那组入口行也留出了间隙() {
        val 源码 = text("com/fortheelder/ui/home/HomeScreen.kt")
        val 栈 = blockAt(源码, "TargetStack {")
        assertTrue(
            "首页那一组没有 TargetStack：两条入口行之间仍是 8dp",
            栈.contains("HomeVoice.settingsAction"),
        )
        assertTrue(
            "首页那一组里缺了「今天先安静一会儿」那一行——它和「设置」是相邻的两个可点行",
            栈.contains("HomeVoice.quietAction"),
        )
        assertTrue(
            "首页那一组里缺了小节标题（标题与第一行入口之间也该有 16dp）",
            栈.contains("SectionTitle(text = HomeVoice.sectionSettings)"),
        )
    }

    /**
     * 三处都已经改过一遍，所以整个 `ui` 包里至少要有三个文件用上 `TargetStack`。
     *
     * 这条例的不作用在单点上，只是防「某处被悄悄退回 `Column + spacedBy(gapSm)`」——
     * 退回去不会让别的用例变红，只会让那一处的间隙重新变成 21px。
     */
    @Test
    fun 三处相邻可点栈都走同一根杠杆() {
        val 用上的文件 = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText(Charsets.UTF_8).contains("TargetStack") }
            .map { it.name }
            .sorted()
            .toList()
        assertTrue(
            "用上 TargetStack 的界面文件只有 ${用上的文件.size} 个（${用上的文件.joinToString("、")}），期望至少 3 个" +
                "（外壳 + 两屏）",
            用上的文件.size >= 3,
        )
    }

    // ---- 4. 结构层：第 66 轮从「混排正文的列」里挑出来的按钮段 --------------------------

    /**
     * 判据的**反面**：一列里混着不可点的正文行时，整列不许换 [TargetStack]。
     *
     * 上一轮那三处之所以改得对，是因为它们整列都是可点目标。说话页这三条区块不是：
     * 标题、证据行、问法都不可点，一律撑到 16dp 只会把一段说明拆得读不成句。
     * 所以这一轮只把**列尾那一段按钮**拎出来换栈，正文行仍留在 8dp 上。
     *
     * 这条例卡两件事：外层那一列**仍然是 `gapSm`**（整列撑到 16dp，这一行就找不到了），
     * 并且列里的按钮**都排在 `TargetStack` 之后**——排在栈前头的那一颗，说明它没被拎进栈，
     * 跟前一颗之间仍只有 21px。
     *
     * 「列里不许出现按钮」这种判据是做不到的：按钮本来就嵌在这一列里，`blockAt` 取的是整块，
     * 换成 `TargetStack` 之后它们在文字上依然在列内。这里卡的是**顺序**，不是有无。
     */
    @Test
    fun 混排正文的列没被整列硬撑开() {
        val 源码 = text("com/fortheelder/ui/talk/TalkScreen.kt")
        val 八dp列 = "Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm))"
        for (函数名 in listOf("WishBlock(", "SettledBlock(", "PendingBlock(")) {
            val 函数块 = blockAt(源码, "private fun $函数名")
            assertTrue(
                "$函数名 里找不到那根 8dp 的外层列——是不是整列都被撑到 16dp 了？" +
                    "这一列里混着标题、证据、问法这些不可点的正文行，整列撑开只会把说明拆得读不成句",
                函数块.contains(八dp列),
            )
            val 列 = blockAt(函数块, 八dp列)
            val 栈首 = 列.indexOf("TargetStack {")
            assertTrue(
                "$函数名 的 8dp 那一列里没有 TargetStack，列尾那一段按钮要么没拎出来、要么整列被换掉了",
                栈首 >= 0,
            )
            val 首个按钮 = 列.indexOf("WideButton(")
            assertTrue(
                "$函数名 的 8dp 那一列里有按钮排在 TargetStack 之前，说明它没被拎进栈、" +
                    "跟前一颗之间仍只有 21px",
                首个按钮 < 0 || 首个按钮 > 栈首,
            )
        }
    }

    /**
     * 说话页三处按钮段。
     *
     * 这三处是同一页上后果最重的按钮：确认就留下、婉拒就不留、忘掉就删。从前它们两两之间
     * 只有卡片内层的 21px，而它们又都长得一样宽——点错一次的代价是「我以为我拒绝了」。
     */
    @Test
    fun 说话页那三处按钮段都换成了十六dp() {
        val 源码 = text("com/fortheelder/ui/talk/TalkScreen.kt")

        val 刚说的 = blockAt(源码, "private fun WishBlock(")
        val 刚说的栈 = blockAt(刚说的, "TargetStack {")
        for (线索 in listOf("TalkVoice.confirmLabel(hit.kind)", "TalkVoice.declineLabel(hit.kind)")) {
            assertTrue("WishBlock 还没拿主意那一段的 $线索 不在 TargetStack 里", 刚说的栈.contains(线索))
        }

        val 待定 = blockAt(源码, "private fun PendingBlock(")
        val 待定栈 = blockAt(待定, "TargetStack {")
        for (线索 in listOf(
            "TalkVoice.confirmLabel(wish.kind)",
            "TalkVoice.declineLabel(wish.kind)",
            "TalkVoice.forgetThis",
        )) {
            assertTrue("PendingBlock 的 $线索 不在 TargetStack 里", 待定栈.contains(线索))
        }

        val 已定 = blockAt(源码, "private fun SettledBlock(")
        val 已定栈 = blockAt(已定, "TargetStack {")
        for (线索 in listOf("TalkVoice.reopenLabel(wish.kind)", "TalkVoice.forgetThis")) {
            assertTrue("SettledBlock 的 $线索 不在 TargetStack 里", 已定栈.contains(线索))
        }
    }

    /**
     * 起点卡里「问时段」那一支。
     *
     * 摊开之后同时有 **四个时段行 + 一个退路**，五个可点目标紧挨着。它们比五句现成的话更
     * 危险：现成的话点错只是问了别的事，这里点错是把这件事记到了错的时段上，而卡片上
     * 只会显示「已记在傍晚」——用户不细看就发现不了。
     */
    @Test
    fun 起点卡问时段那一支也换成了十六dp() {
        val 源码 = text("com/fortheelder/ui/talk/StarterCard.kt")
        val 分支 = blockAt(源码, "page.anchorAskFor == option.id -> {")
        assertTrue("问时段那一支里没有 TargetStack，五个目标之间还是 8dp(21px)", 分支.contains("TargetStack {"))

        val 栈 = blockAt(分支, "TargetStack {")
        assertTrue(
            "时段不是按 CompanionVoice.confirmableSlots 铺出来的，那「四个时段」这句话就对不上",
            栈.contains("for (slot in CompanionVoice.confirmableSlots)"),
        )
        assertTrue("时段行没有做成整行可点", 栈.contains("ActionRow("))
        assertTrue("退路（不再问这一件事的时段）不在同一段里", 栈.contains("onCancelAnchor"))
    }

    /**
     * 判据的**反面**的第二条：只有 1 个可点目标的列，不许跟着撑开。
     *
     * 「常伴记着的日常」里每一条只有一个删除按钮，上面两行是时段与事情本身。没有相邻对，
     * 就没有误触的来源；为它让出 16dp 只会把两行正文推得更散。
     */
    @Test
    fun 只有一个可点目标的那一处没被硬撑开() {
        val 源码 = text("com/fortheelder/ui/talk/AnchorCard.kt")
        val 函数块 = blockAt(源码, "private fun AnchorBlock(")
        assertTrue(
            "AnchorBlock 用上了 TargetStack，可这一列里只有 1 个可点目标（删除）——" +
                "第 66 轮的判据是「相邻可点」，不是「越多越好」",
            !函数块.contains("TargetStack"),
        )
        assertTrue("AnchorBlock 里那个删除按钮不见了", 函数块.contains("SecondaryWideButton("))
    }

    /** 这四处都改过一遍之后，谁也不许单独退回 `Column + spacedBy(gapSm)`。 */
    @Test
    fun 第66轮挑出来的那一处也走上了同一根杠杆() {
        for (相对路径 in listOf(
            "com/fortheelder/ui/common/ElderComponents.kt",
            "com/fortheelder/ui/home/HomeScreen.kt",
            "com/fortheelder/ui/talk/TalkScreen.kt",
            "com/fortheelder/ui/talk/StarterCard.kt",
        )) {
            assertTrue("$相对路径 里没有 TargetStack，那一处又退回 8dp(21px) 了", text(相对路径).contains("TargetStack"))
        }
    }
}
