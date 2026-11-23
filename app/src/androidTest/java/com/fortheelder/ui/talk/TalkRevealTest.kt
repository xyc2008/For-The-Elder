package com.fortheelder.ui.talk

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.ui.theme.ForTheElderTheme
import com.fortheelder.ui.自己的存储
import com.fortheelder.ui.关库排在组合之后
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 点完一句之后，回应卡是不是**整张**进了视口（第 62 轮，设备级）。
 *
 * 第 62 轮修的是第 61 轮走查里量到的一件事：点完一句，「我听见的」确实生成了，
 * 但它的下沿落在首屏滚动区下沿**以下 485 px**——用户在这一屏里一个像素都看不到，
 * 得自己往下滑才知道常伴回应了。产品码这一轮的改动是让回应卡第一次落位时量一次，
 * 不在视口里就滚过去（`TalkScreen.kt` 的 `BringResponseIntoView`）。
 *
 * 这一条用例只用设备回答一个问题：**滚完之后，那张卡上的每一行是不是整条都在滚动区里**。
 * 判据取「行的框完整落在滚动区矩形的上下沿之间」，不取 `assertIsDisplayed()`：
 * 后者的口径是「有没有露出来」（部分露出也算在场），而这一轮要修的恰恰是「露出多少」。
 * 两者都留着——`assertIsDisplayed()` 在前面当粗筛，逐行的框包含判断在后面当细筛。
 *
 * 「每一行」是**七行**，含两个闸门按钮：这张卡存在的意义就是让用户当场决定记不记着这件事，
 * 而决定只能靠那两个按钮做出来。断言里没有按钮，等于「读得到问句、按不到答话」也算过。
 *
 * **量之前必须先等滚动动画真正停稳**（`等滚动停下`）。第 62 轮首跑就是被这一点骗过的：
 * `waitUntil` 的条件在动画半路上成立，用例绿了，而它证明的只是「某一帧上看得见」。
 * 等停稳要两层——整数读数连着几帧不变**并且**时钟再推 3 秒也不动；只靠前一层不够，
 * 因为弹簧末端的每帧位移小于 1 px，整数读数会在还在爬行时连着好几帧不变。
 *
 * 两条用例分工不同，不是重复：
 *
 * 1. `点完一句回应卡整张进视口`：起手停在顶端（先断言滚动位置是 0），点完看卡。
 *    它判的是**「不滚也能看见」这件承诺本身**。
 * 2. `先滑过一段再点回应卡照样整张进视口`：先用 `performScrollTo()` 把示例句里的最后
 *    一句带进视口、再点第一句，此时滚动位置**明显非零**。它判的是**`BringResponseIntoView`
 *    里那个坐标口径在 scroll > 0 时还成不成立**——那里的 `cardTopInViewport` 是两边的
 *    `positionInRoot().y` 相减，注释里写了「`parentLayoutCoordinates` 交回来的是滚动容器的
 *    哪一个 coordinator 会随实现细节变」。scroll = 0 时两种解释同值，只有这一条判得出来：
 *    若交回来的是滚动容器**内层**的坐标，卡会多滚一个「当前滚动量」出去。
 *
 * 逐像素的正确性不在这里判：算式本身由 JVM 的 `TalkRevealTargetTest` 单独守，
 * 那一条能在同一台机器上把 `currentScroll / maxScroll / viewportHeight` 摆成任意值。
 * 这一条判的是「真机上量到的坐标与真实的滚动动作能不能对得上」，量的次数越少越可信。
 *
 * 三处必须交代的边界（`records/04-质量与验证记录.md` 里记着同样的读数）：
 *
 * 1. **只在本机这一种屏幕参数上量过**（1080×2400、dpi 420、系统默认字号档）。
 *    系统字号档与应用内「更大」那一档下卡会长高，能不能整张进视口这一轮没量——
 *    卡高超过视口时它**不可能**整张进视口，那时这一条会红，而红的原因不在这一轮的改动上。
 * 2. **量到的是「滚动位置把它带进来了」，不是「老人因此看懂了回应」**。后者要用户测试，
 *    这一轮没有，不做任何可用性宣称。
 * 3. **点示例句的是脚本，不是真人**：设备走查那边是 `adb shell input tap` 发出的，
 *    发的人始终是开发者本人。
 *
 * 方法名沿用这个目录的约定：中文、**不含 ASCII 空格**（minSdk 26 下 D8 只认 DEX 040，
 * 带空格的 backtick 方法名会让整个 androidTest 编不出来，第 60 轮真踩过）。
 */
@RunWith(AndroidJUnit4::class)
class TalkRevealTest {

    /** 它自己不挂 `@get:Rule`：由下面的 `rules` 包住，收尾顺序才由那条链说了算。 */
    private val compose = createComposeRule()

    /**
     * 关库这一步排在 compose 规则**外面**：宿主 Activity 先被销毁，之后才关库。
     * 顺序为什么重要，写在 `关库排在组合之后` 上。
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 容器与库文件都是这个用例自己的（[TEST_DB]），理由与 `TalkInputTest` 里写的一样：
     * 应用那份 `ElderApp.container` 用的是正式库 `elder.db`，同进程里别的数据用例会把它删掉重开。
     */
    private lateinit var container: AppContainer

    /** 两份偏好也归它自己，不传的话写的会是这台设备上应用真正的设置（见 `自己的存储`）。 */
    private lateinit var 存储: 自己的存储

    @Before
    fun 用一份空的库把这一页摆出来() {
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
    }

    private fun show() {
        compose.setContent {
            ForTheElderTheme {
                TalkScreen(container = container, onBack = {})
            }
        }
    }

    @Test
    fun 点完一句回应卡整张进视口() {
        show()

        // 点之前先把「回应还没生成」钉住：不然下面几条断言可能只是「它本来就看得见」。
        assertEquals(
            "还没点任何一句，「${TalkVoice.heardSection}」就已经在语义树里了：这一格证明不了自动滚",
            0,
            有几处(TalkVoice.heardSection),
        )
        // 这一条读数必须在点之前就取：点完之后量程会随新卡长高，两者一比才知道
        // 产品算目标值时用的是哪一版量程（第 62 轮第二轮加，见 `等滚动停下` 的说明）。
        val 点前 = 滚动位置()
        assertEquals(
            "这一格的前提是起手停在顶端，现在滚动位置是 ${点前.first}",
            0f,
            点前.first,
            0.001f,
        )
        println("[例1] 点前读数：位置=${点前.first} 量程=${点前.second}")

        // **不先 performScrollTo**：这句在零滚动时就 displayed（第 61 轮的设备走查已证）。
        // 先滑一下会把「点完之后滚到哪」跟当时那个滚动量纠缠在一起，那是例 2 的事。
        compose.onNodeWithText(示例句).performClick()

        compose.waitUntil(超时毫秒) { 有几处(TalkVoice.heardSection) > 0 }
        val 停稳 = 等滚动停下("例1")
        println("[例1] 点后读数：位置=$停稳 量程=${滚动位置().second}")
        等着整张卡进视口("例1", 回应卡的每一行(示例句))
        打印读数("例1", 回应卡的每一行(示例句))

        val 区 = 滚动区()
        for (行 in 回应卡的每一行(示例句)) {
            整条在滚动区里(行, 区, "例1")
        }
    }

    @Test
    fun 先滑过一段再点回应卡照样整张进视口() {
        show()

        // 先把页面上最后一句带进视口——这一步只为了让「点之前」的滚动位置非零。
        compose.onNodeWithText(最后一句).performScrollTo()
        compose.waitForIdle()
        val 点前 = 滚动位置()

        // 这句是这一格的**前提**，不是它的结论：滚动位置还是 0 的话，它测的就跟例 1 同一件事。
        assertTrue(
            "把「$最后一句」滑进视口之后滚动位置仍是 ${点前.first}（量程 ${点前.second}）——" +
                "这一格退化成例 1 了，测不出 parent 坐标口径在 scroll > 0 时对不对",
            点前.first > 0.5f,
        )

        // 再回到第一句。`performScrollTo()` 是「最小滚动，使目标全可见」，落到顶端之后
        // 第一句的上沿就贴着视口上沿，此刻滚动位置约等于它自己在内容里的 y。
        compose.onNodeWithText(示例句).performScrollTo()
        compose.onNodeWithText(示例句).assertIsDisplayed()
        compose.waitForIdle()
        val 点前再 = 滚动位置()
        assertTrue(
            "回到「$示例句」之后滚动位置是 ${点前再.first}：这一格要求它是非零的",
            点前再.first > 0.5f,
        )

        compose.onNodeWithText(示例句).performClick()

        compose.waitUntil(超时毫秒) { 有几处(TalkVoice.heardSection) > 0 }
        val 停稳 = 等滚动停下("例2")
        println("[例2] 点后读数：位置=$停稳 量程=${滚动位置().second}（点前位置=${点前再.first}）")
        等着整张卡进视口("例2", 回应卡的每一行(示例句))
        打印读数("例2", 回应卡的每一行(示例句))

        val 区 = 滚动区()
        for (行 in 回应卡的每一行(示例句)) {
            整条在滚动区里(行, 区, "例2")
        }
    }

    /**
     * 点完 [示例句]（`我一直想学书法`）之后，那张卡上必须**整条**进视口的每一行。
     *
     * 七行，从卡的顶边数到卡里最后一个可点控件：卡标题、原话回显、「类别：事由」、
     * 闸门问句、**两个闸门按钮**、闸门说明。
     *
     * 为什么必须含两个按钮（第 62 轮第二轮改的）：这张卡存在的意义就是让用户当场
     * 决定「记不记着这件事」，而那个决定只能靠两个按钮做出来。只断言文案行、
     * 不管按钮，就等于说「用户能读到问句、但按不到答案」也算过——那不是这一轮要修的东西。
     * 首跑时断言列表里没有按钮，绿的；把按钮加进来之后才知道 `maxScroll` 够不够。
     *
     * 唯一不列在里面的是判定依据那一行（`是从「…」这几个字听出来的。`）：它的字面
     * 由意图引擎给出，混进来会让这一格同时守两份东西，而它守的那一份另有单测。
     */
    private fun 回应卡的每一行(sentence: String): List<String> = listOf(
        TalkVoice.heardSection,
        TalkVoice.echoLine(sentence),
        "${TalkVoice.kindLabel(IntentKind.INTEREST)}：书法",
        TalkVoice.prompt(IntentKind.INTEREST),
        TalkVoice.confirmLabel(IntentKind.INTEREST),
        TalkVoice.declineLabel(IntentKind.INTEREST),
        TalkVoice.gateNote,
    )

    /** 某一处文案的语义节点；一处都没有时交回 `null`（给 `waitUntil` 用，不许抛）。 */
    private fun 找节点(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().firstOrNull()

    /**
     * 某一处文案的框；一处都没有时交回 `null`。
     *
     * `boundsInRoot` 是**裁过**的：整条落在滚动区外的那一行会交回 `Rect.Zero`，
     * 看不出它本来在哪。第 62 轮红实验就是被这一点挡住了——卡没被滚进视口时七行全是零，
     * 日志里既像「节点没量」也像「节点被裁光」。所以 `打印读数` 里同时打
     * `positionInRoot`（未裁的那一个），把这两种情形分开。
     */
    private fun 框(text: String): Rect? = 找节点(text)?.boundsInRoot

    private fun 有几处(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * 这一屏里的滚动区矩形。
     *
     * 按 `VerticalScrollAxisRange` 这个键找，不按「有没有滚动动作」找：这一页上可滚的
     * 节点只该有一个，多一个或少一个都当场红——量错一个节点，下面所有包含判断都白算。
     */
    private fun 滚动区(): Rect = 滚动节点们().let { 们 ->
        if (们.size != 1) {
            throw AssertionError(
                "带纵向滚动语义的节点有 ${们.size} 个，期望正好 1 个：这一格量不到滚动区（见 TalkRevealTest.kt）",
            )
        }
        们.single().boundsInRoot
    }

    /** 滚动区自报的 (当前位置, 量程)。读不出就红，不静默返回默认值。 */
    private fun 滚动位置(): Pair<Float, Float> = 滚动节点们().let { 们 ->
        if (们.size != 1) {
            throw AssertionError(
                "带纵向滚动语义的节点有 ${们.size} 个，期望正好 1 个：这一格的滚动位置取不到",
            )
        }
        val 量程 = 们.single().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?: throw AssertionError("节点有这个键、却读不出量程：读取方式跟 compose-ui 的版本漂了")
        量程.value() to 量程.maxValue()
    }

    private fun 滚动节点们() = compose
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes()

    /**
     * 等滚动动作**真正停下来**，交回停稳后的位置。
     *
     * 为什么非等不可：`waitUntil` 的条件一旦成立就立刻返回，而 `scroll.animateScrollTo`
     * 是逐帧逼近的——条件完全可能在**动画半路上**成立，那条用例于是绿了，
     * 但它证明的只是「动画某个中间帧上卡看得见」，不是「最终落位上整张都看得见」。
     * `waitForIdle()` 挡不住这一手：它等的是**组合**空闲，动画帧在它眼里不算忙。
     *
     * 判据两层，缺一不可：
     *
     * 1. **同一读数连着 [连稳帧数] 次不变**（`waitUntil` 每次轮询都会推进一帧，
     *    所以这个循环真的能让动画往前走）。
     * 2. **再把测试时钟往前推 [停下后多推毫秒] 毫秒，位置仍不变。** 这一层是必需的：
     *    `ScrollState.value` 是整数，而 `animateScrollTo` 的默认规格是弹簧，
     *    末端每帧位移远小于 1 px——整数读数会在**还在爬行**的时候连着好几帧不变，
     *    只靠第 1 层就会把「爬行中」当成「到终点」。第 2 层一推，真到终点的不动，
     *    还在爬的必然动。
     *
     * 读不出滚动位置时算作「还在动」，不算「已停」：读数缺失时静默放过，
     * 正是这一轮要防的那一类假绿。
     */
    private fun 等滚动停下(标签: String): Float {
        var 上次 = Float.NaN
        repeat(最多停稳轮数) { 轮 ->
            var 连稳 = 0
            上次 = Float.NaN
            compose.waitUntil(超时毫秒) {
                val 现在 = runCatching { 滚动位置().first }.getOrNull()
                连稳 = if (现在 != null && 现在 == 上次) 连稳 + 1 else 0
                if (现在 != null) 上次 = 现在
                连稳 >= 连稳帧数
            }
            val 停前 = 上次
            compose.mainClock.advanceTimeBy(停下后多推毫秒)
            val 停后 = runCatching { 滚动位置().first }.getOrNull()
            if (停后 != null && 停后 == 停前) {
                println("[$标签] 停稳于 $停后（连稳 $连稳帧数 帧，再推 ${停下后多推毫秒}ms 不变，第 ${轮 + 1} 轮）")
                return 停后
            }
            println(
                "[$标签] 第 ${轮 + 1} 轮：时钟推了 ${停下后多推毫秒}ms 之后位置从 $停前 变成 $停后" +
                    "——说明刚才那几帧只是弹簧末端在爬，还没到终点，再等一轮",
            )
        }
        throw AssertionError(
            "[$标签] 时钟推了 $最多停稳轮数 轮之后滚动位置仍在变（最后读到 $上次）：" +
                "动画没有停下来，此刻的读数不足以支撑任何「看得见」的结论",
        )
    }

    /** [每一行] 是不是都整条落在滚动区里了。给 `waitUntil` 当条件用，**任何一步取不到都交回 false**。 */
    private fun 都进视口(每一行: List<String>): Boolean {
        val 区 = 滚动节点们().singleOrNull()?.boundsInRoot ?: return false
        if (区.height <= 0f) return false
        return 每一行.all { text ->
            val 框 = 框(text) ?: return@all false
            框.height > 0f && 框.top >= 区.top - 容差 && 框.bottom <= 区.bottom + 容差
        }
    }

    /**
     * 等它整张进视口。等不到就**先把这一刻的读数打出来再红**：红的时候要看得见数字，
     * 否则「没滚到位」与「滚过头了」这两种失败在日志里长得一模一样。
     */
    private fun 等着整张卡进视口(标签: String, 每一行: List<String>) {
        try {
            compose.waitUntil(超时毫秒) { 都进视口(每一行) }
        } catch (t: Throwable) {
            打印读数(标签, 每一行)
            throw t
        }
    }

    /** 把这一刻的读数打进日志：滚动区、滚动位置与量程、根节点、以及每一行的框。 */
    private fun 打印读数(标签: String, 每一行: List<String>) {
        val 区 = 滚动节点们().singleOrNull()?.boundsInRoot
        val 位置 = runCatching { 滚动位置() }.getOrNull()
        val 根 = compose.onRoot().fetchSemanticsNode().boundsInRoot
        println(
            "[$标签] 滚动区=$区 位置=${位置?.first} 量程=${位置?.second} 根=$根",
        )
        for (text in 每一行) {
            val 节点 = 找节点(text)
            println("[$标签] 「$text」 框=${节点?.boundsInRoot} 位置=${节点?.positionInRoot}")
        }
    }

    /**
     * 一行必须**整条**在滚动区里。
     *
     * 两条判据缺一不可：`assertIsDisplayed()` 证明它在场（它是这一轮之前就不成立的那一条），
     * 上下沿的包含判断证明它没被视口切掉（这才是这一轮真正要考的）。
     * 容差 [容差] 是给「顶边正好贴住滚动区上沿」这一种落法留的：算式把卡的顶边对齐到视口上沿，
     * 到位之后两边的量理论上相等，浮点上差零点几个像素不该算失败。
     */
    private fun 整条在滚动区里(text: String, 区: Rect, 标签: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
        val 框 = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "[$标签] 「$text」没有整条落进滚动区：" +
                "框=[${框.left},${框.top}][${框.right},${框.bottom}]，滚动区=$区——" +
                "框高 $框.height，上沿差 ${框.top - 区.top}，下沿差 ${区.bottom - 框.bottom}",
            框.height > 0f && 框.top >= 区.top - 容差 && 框.bottom <= 区.bottom + 容差,
        )
    }

    private companion object {
        /** 这个用例自己的库文件；应用那个 `elder.db` 不归它碰。 */
        const val TEST_DB = "elder-test-talk-reveal.db"

        /** 走查用的那一句：被识别成「想做的事 / 书法」，只出一条闸门区块。 */
        const val 示例句 = "我一直想学书法"

        /** 页面上最后一句，故意不被识别的那一句（`TalkExamples.notRecognized`）。 */
        const val 最后一句 = "今天天气还行"

        /** 上下沿判定留的余量，单位是像素（`boundsInRoot` 的单位）。 */
        const val 容差 = 0.5f

        /** 与同包其它屏幕级用例取同一个量级：等动画收尾，不等环境慢。 */
        const val 超时毫秒 = 5_000L

        /**
         * 判定「滚动已经停下」要连着几次取样不变。取 3 而不是 1：一帧的巧合——
         * 动画正处在两次位移之间插值出的同一个整值上——不该被当成停稳。
         */
        const val 连稳帧数 = 3

        /**
         * 连稳之后再把测试时钟往前推多少毫秒，用来确认「不动」不是因为时钟没走。
         * 取 3 秒：`animateScrollTo` 的默认规格是弹簧，3 秒足够把任何还在爬行的位移走完。
         */
        const val 停下后多推毫秒 = 3_000L

        /** 最多重复「连稳取样 + 推时钟」几轮。第 1 轮就停稳是常态，留 3 轮是给爬行留余量。 */
        const val 最多停稳轮数 = 3
    }
}
