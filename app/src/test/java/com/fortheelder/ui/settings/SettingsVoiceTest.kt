package com.fortheelder.ui.settings

import com.fortheelder.domain.engine.intervention.wakeSleepSummary
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.domain.engine.rhythm.TimeSpan
import java.io.File
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「说话的时机」那张卡的四句结论必须**互相分得开**（第 39 轮补）。
 *
 * ## 为什么单挑这四句
 *
 * 第 38 轮的反向变异留下一条实测边界（`build/r38/mut38_out.txt`）：把卡片读画像的那一行拿掉
 * （M1，State 永不变、卡片永远停在初始那句）与把 [SettingsVoice.timingGathering] 换成加载那句
 * （M2，State 照样会变、但「还在了解中」这一支说出的是加载那句话），**在屏幕上读到的字节完全一样**：
 * 两轮走查红的三条断言一字不差（「读出真结论」「初始那句已不在屏上」「4 秒后仍不是初始那句」）。
 * 也就是说，句子一旦失去可区分性，**屏幕层的两套断言（`SettingsScreenTest` 的「结论句数」与
 * `build/probe38.py` 的「三句结论在不在」）会同时失去分辨力**，而它们各自是红是绿都反映不出来。
 *
 * 这件事对用户是实打实的：这张卡存在的理由就是把 FR-05 的判定依据摆到台面上。「还在了解中，
 * 已经看了 0 天」与「正在看您最近的作息」是两种不同的处境（前者**已经有结论**：天数还不够；
 * 后者是本次读取还没回来），用户有权区分。两句话若长成一句，卡片就是在对用户含糊其辞，
 * 而设备层没有任何断言能发现——所以这条守卫坐在文案层，不坐在屏幕层。
 *
 * ## 它盯的四条
 *
 * 1. 四句**两两不是同一句话**（参数网格上全比一遍）；
 * 2. 两句带参数的结论**真的跟着参数走**：句子里看得见 [参数值][SettingsVoice.timingGathering]
 *    本身，且换一个参数就换一句——堵的是「把带参数的句子换成一句写死的常量」这条捷径；
 * 3. **没有一句含着另一句**（子串层面）：屏幕层的计数是按前缀做子串匹配的，某句只要含进另一句，
 *    那些计数就会同时数到两句上；同时不许有空白句（空白句会被任何句子「含着」）；
 * 4. [SettingsVoice.gapLine] 必须带上 [间隔][SettingsVoice.gapLine] 本身，不能写成一句写死的。
 *
 * ## 最后一条盯的是「屏幕层的数法」本身
 *
 * `SettingsScreenTest` 里辨认「还在了解中」与「按您自己的作息」两句用的是两个**字面前缀**
 * （`countContaining("作息还在了解中：")` 那一对）。那两个前缀是抄进测试里的，改了主源码里的
 * 引导语而忘了改它们，屏幕层的计数会**静默数到 0**、看起来像「卡片没读到画像」——第 38 轮的
 * M1/M2 就是踩着这个坑分不开的。所以 [屏幕层的辨认前缀必须真的认得这几句结论] 把
 * `SettingsScreenTest.kt` 里的那两个字面量读出来，逐个要求它**确实是某一族结论的前缀、
 * 且不是另一族的任何一句的前缀**。
 *
 * ## 它不保证什么
 *
 * - 只管这四句**在字符串层面分得开**，管不了「哪一支该说哪一句」：把
 *   [SettingsVoice.timingGathering] 与 [SettingsVoice.timingInconsistent] 的内容**整体互换**，
 *   四句仍然两两不同，这条用例全绿。分支与句子的对应关系由设备层盯着。
 * - 「满 N 天」里的 N 是否合理、[RhythmConfig.minDays] 的取值是否合适，都不在这条用例的范围内；
 *   `minDays` 只用来取一个**真实的参数上限**（网格是 0..7，不跟着它走）。
 * - 「N 天」这种带空格的写法一旦改成别的写法，第 2 条会红——这是有意的：它逼着改动者回来
 *   同时改这条用例，而不是让守卫悄悄失效。
 */
class SettingsVoiceTest {

    /** 真实的参数上限：`RhythmInference` 判「还在了解中」时用的就是它。 */
    private val requiredDays: Int = RhythmConfig().minDays

    /** 参数网格：0..7 天，「一天都没有」与「看了几天」都在里面。 */
    private val daysGrid: IntRange = 0..7

    /** 两份画像摘要：一份带标点、一份不带，避免句子只对某一种写法成立。 */
    private val summaries: List<String> = listOf("8 点起，22 点睡", "7 点起，23 点睡")

    /** 「按您自己的作息：…」那一族的全部取值。 */
    private fun readySentences(): List<String> =
        summaries.flatMap { summary -> daysGrid.map { days -> SettingsVoice.timingReady(summary, days) } }

    /** 「以前记下的作息：…」那一族的全部取值（第 89 轮加的，采集关着时顶替上一族）。 */
    private fun recordedSentences(): List<String> =
        summaries.flatMap { summary -> daysGrid.map { days -> SettingsVoice.timingRecorded(summary, days) } }

    /** 「作息还在了解中：…」那一族的全部取值。 */
    private fun gatheringSentences(): List<String> =
        daysGrid.map { days -> SettingsVoice.timingGathering(days, requiredDays) }

    /** 「作息还在了解中：…采集没有打开…」那一族的全部取值（第 89 轮加的，采集关着时顶替上一族）。 */
    private fun pausedSentences(): List<String> =
        daysGrid.map { days -> SettingsVoice.timingGatheringPaused(days) }

    /** 「现在打招呼的间隔：…」那一族的全部取值。 */
    private fun gapLines(): List<String> = listOf("30 分钟", "1 小时", "半天").map { SettingsVoice.gapLine(it) }

    /** 一句话到底说了几种事：六族结论加上间隔那一行（间隔行也参与「不许互相含」）。 */
    private fun allSentences(): List<String> =
        listOf(SettingsVoice.timingLoading, SettingsVoice.timingInconsistent) +
            readySentences() + recordedSentences() + gatheringSentences() + pausedSentences() + gapLines()

    @Test
    fun 四句结论里没有两句是同一句话() {
        val all = allSentences()
        // 网格或句表被改空的话，下面两个循环会一个都不跑、守卫变成永远绿。
        // 第 89 轮从 29 句涨到 53 句（「以前记下的作息」与「采集没打开的那一句」各一族），
        // 门槛不跟着走的话，这两族被悄悄从表里拿掉也不会有东西报红。
        assertTrue("这句话太少（${all.size} 句），参数网格或句表被改空了", all.size >= 45)

        val collisions = mutableListOf<String>()
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                if (all[i] == all[j]) {
                    collisions += "第 $i 句与第 $j 句一模一样：「${all[i]}」"
                }
            }
        }
        assertTrue(
            "有两种处境说出的结论是同一句话。这张卡就是用来摊开判定依据的，" +
                "同一句话盖住两种处境等于对用户含糊其辞；而且屏幕层按前缀计数时会把它们数成同一个" +
                "（第 38 轮 M1/M2 分不开就是这个形状）：\n" + collisions.joinToString("\n"),
            collisions.isEmpty(),
        )
    }

    @Test
    fun 带参数的结论真的跟着参数走() {
        val problems = mutableListOf<String>()

        daysGrid.forEach { days ->
            val sentence = SettingsVoice.timingGathering(days, requiredDays)
            if (!sentence.contains("$days 天")) {
                problems += "「还在了解中」那句里看不到已经看了几天（期望含「$days 天」）：「$sentence」"
            }
            if (!sentence.contains("$requiredDays 天")) {
                problems += "「还在了解中」那句里看不到还差几天（期望含「$requiredDays 天」）：「$sentence」"
            }
        }
        // 只改 requiredDays，句子就必须跟着变：上面那条「看得见 N」在 days == requiredDays 时
        // 分不清两个数各出现在哪儿，这一条补上「两个参数各自都真的进了句子」。
        val fixedDays = 1
        assertTrue(
            "只改 requiredDays（$requiredDays → ${requiredDays + 1}）句子却没变，" +
                "说明这个参数根本没进句子：「${SettingsVoice.timingGathering(fixedDays, requiredDays)}」",
            SettingsVoice.timingGathering(fixedDays, requiredDays) !=
                SettingsVoice.timingGathering(fixedDays, requiredDays + 1),
        )

        summaries.forEach { summary ->
            val sentences = daysGrid.map { days -> SettingsVoice.timingReady(summary, days) }
            if (sentences.distinct().size != sentences.size) {
                problems += "同一份摘要「$summary」下换天数不换句子（${daysGrid.first}..${daysGrid.last} 天里只有 " +
                    "${sentences.distinct().size} 种）：$sentences"
            }
            sentences.forEach { sentence ->
                if (!sentence.contains(summary)) {
                    problems += "「按您自己的作息」那句里看不到画像摘要（期望含「$summary」）：「$sentence」"
                }
            }
        }

        assertTrue(
            "带参数的结论没有跟着参数走——很可能是被换成了一句写死的常量：" +
                "参数是代码里实时算出来的天数和摘要，写死就等于报出一个不是这份画像的数：\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun 没有一句含着另一句() {
        val all = allSentences()
        val problems = mutableListOf<String>()

        all.forEachIndexed { index, sentence ->
            if (sentence.isBlank()) {
                problems += "第 $index 句是空白句：空白句会被任何一句「含着」"
            }
        }
        for (i in all.indices) {
            for (j in all.indices) {
                if (i == j || all[i] == all[j]) continue
                if (all[i].contains(all[j])) {
                    problems += "第 $i 句里含着一整句第 $j 句（「${all[j]}」）：「${all[i]}」"
                }
            }
        }
        assertTrue(
            "有一句结论被另一句整个包住了。屏幕层是按前缀做子串匹配来数这几句的" +
                "（`SettingsScreenTest` 的「结论句数」与 `build/probe38.py` 的「三句结论在不在」），" +
                "一旦一句含进另一句，那些计数会同时数到两句上：\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    /**
     * 采集关着时，卡片首行绝不说「按您自己的作息」（第 89 轮补）。
     *
     * ## 这条用例钉的是哪一处
     *
     * 第 89 轮之前，「说话的时机」那张卡的第一行与卡尾那句 [SettingsVoice.collectOffNote]
     * **各判一次**采集开关：首行只看画像（`wakeSleepSummary` 照印不误），卡尾那一个 `if` 才看开关。
     * 于是「采集关掉 + 库里还躺着一份以前学出来的 Ready 画像」这一种组合下，同一张卡会在**同一屏**
     * 上同时写出
     *
     * - 首行：「按您自己的作息：…」
     * - 卡尾：「以后说话的时机就按普通的作息来。」
     *
     * 两句互相矛盾，而屏幕层没有任何一条断言能发现它——两条句子各自都在场，各自的计数都对。
     *
     * 修法是把首行也收进同一个布尔：[SettingsVoice.timingLine] 一次判完 `(画像, 开关)`，
     * 卡尾那句仍由同一个布尔驱动。这条用例就把 `(画像, 开关)` 的全部组合逐一钉在文案层，
     * 不必上设备。
     *
     * ## 它盯的四条
     *
     * 1. **八个组合各自的落点**（画像四种 × 开关两种）：一一对上 [SettingsVoice.timingLine]
     *    该回的那一句。只判「不等于某一句」是不够的——把两支对调也能通过那种写法；
     * 2. 采集关着时，首行**不是**「按您自己的作息」那一族的任何一句，也不以那七个字开头；
     * 3. 采集关着时，首行**不含**「就能用上」：采集没打开，天数不会再往上走，那是一个
     *    应用兑现不了的承诺（本对象注释第 2 条纪律）；
     * 4. 关掉开关**真的改变**了首行（对 Ready 与 Gathering 两支各比一次）：堵的是「抽了函数
     *    却把开关参数丢掉、两个分支回同一句」这条捷径——那种改法下第 1 条会红，但万一
     *    将来有人把第 1 条放宽，这一条还在。
     *
     * ## 它不保证什么
     *
     * - 管不了屏幕上的位置与顺序（首行到底渲染在哪儿、卡尾那句跟它隔多远）——那是设备层
     *   `SettingsScreenTest` 与 `build/r89/probe89.py` 的事；
     * - 管不了判定窗口怎么用 `useLearnedRhythm`（那是 `InterventionWindowTest` 的事）。
     *   文案层只说「界面这么写」，说不了「引擎真按这句话做」；
     * - 管不了 [RhythmResult.Inconsistent] 一支为什么不分开关（理由写在 [SettingsVoice.timingLine]
     *   的 KDoc 里）。这里照它的落点断言，不追问理由。
     */
    @Test
    fun 采集关着时首行不会说按您自己的作息() {
        val ready = RhythmResult.Ready(
            DailyRhythm(
                wakeAt = LocalTime.parse("07:00"),
                sleepAt = LocalTime.parse("22:00"),
                activeSpans = listOf(TimeSpan(LocalTime.parse("07:00"), LocalTime.parse("22:00"))),
                idleSpans = emptyList(),
                nightChargingAt = null,
                sampleDays = 5,
                confidence = 0.8f,
            ),
        )
        val gathering = RhythmResult.Gathering(validDays = 2, requiredDays = 3)
        val summary = ready.rhythm.wakeSleepSummary

        // 第 1 条：八个组合各自的落点，一个都不能错位。
        val 落点 = listOf(
            Triple<RhythmResult?, Boolean, String>(null, true, SettingsVoice.timingLoading),
            Triple(null, false, SettingsVoice.timingLoading),
            Triple<RhythmResult?, Boolean, String>(
                ready, true, SettingsVoice.timingReady(summary = summary, sampleDays = 5),
            ),
            Triple<RhythmResult?, Boolean, String>(
                ready, false, SettingsVoice.timingRecorded(summary = summary, sampleDays = 5),
            ),
            Triple<RhythmResult?, Boolean, String>(
                gathering, true, SettingsVoice.timingGathering(validDays = 2, requiredDays = 3),
            ),
            Triple<RhythmResult?, Boolean, String>(
                gathering, false, SettingsVoice.timingGatheringPaused(validDays = 2),
            ),
            Triple<RhythmResult?, Boolean, String>(RhythmResult.Inconsistent, true, SettingsVoice.timingInconsistent),
            Triple<RhythmResult?, Boolean, String>(RhythmResult.Inconsistent, false, SettingsVoice.timingInconsistent),
        )
        落点.forEach { (profile, collect, expected) ->
            assertEquals(
                "画像「$profile」＋采集${if (collect) "开" else "关"}时，首行该说的是「$expected」" +
                    "（这是同一个布尔要管住的落点表，错位就是那张卡又说回了老话）",
                expected,
                SettingsVoice.timingLine(profile, collectRhythm = collect),
            )
        }

        // 第 2、3 条：采集关着时首行不许说的话。
        val 关着 = listOf(null, ready, gathering, RhythmResult.Inconsistent).map { profile ->
            profile to SettingsVoice.timingLine(profile, collectRhythm = false)
        }
        关着.forEach { (profile, line) ->
            assertTrue(
                "采集关着、画像为「$profile」，首行仍以「按您自己的作息」开头：「$line」。" +
                    "这句话只有在采集开着时才有资格说——它和卡尾那句「以后说话的时机就按普通的作息来」" +
                    "会同屏打架",
                !line.startsWith("按您自己的作息"),
            )
            assertTrue(
                "采集关着、画像为「$profile」，首行整句就是「按您自己的作息」那一族里的一句：「$line」",
                readySentences().none { it == line },
            )
            assertTrue(
                "采集关着、画像为「$profile」，首行还在承诺「就能用上」：「$line」。" +
                    "采集没有打开，天数不会再往上走，这是一句兑现不了的话",
                !line.contains("就能用上"),
            )
        }

        // 第 4 条：开关对这两支必须真的起作用（关掉前后不是同一句）。
        assertNotEquals(
            "Ready 画像下，采集开与关切出来的首行是同一句（$summary）——开关参数被丢掉了",
            SettingsVoice.timingLine(ready, collectRhythm = true),
            SettingsVoice.timingLine(ready, collectRhythm = false),
        )
        assertNotEquals(
            "Gathering 画像下，采集开与关切出来的首行是同一句——开关参数被丢掉了",
            SettingsVoice.timingLine(gathering, collectRhythm = true),
            SettingsVoice.timingLine(gathering, collectRhythm = false),
        )
    }

    @Test
    fun 间隔那一行必须带上间隔本身() {
        val gaps = listOf("30 分钟", "1 小时", "半天")
        val lines = gaps.map { SettingsVoice.gapLine(it) }
        gaps.forEachIndexed { index, gap ->
            assertTrue(
                "间隔那一行里看不到间隔本身（期望含「$gap」）：「${lines[index]}」",
                lines[index].contains(gap),
            )
        }
        assertTrue(
            "两个不同的间隔说出了同一句话：「${lines[0]}」——间隔是随接话次数实时算的，写死就报错了",
            lines.distinct().size == lines.size,
        )
        assertTrue("间隔那一行是空白句", lines.none { it.isBlank() })
    }

    /**
     * 屏幕层用来辨认这两句结论的字面前缀，必须真的认得它们。
     *
     * `SettingsScreenTest.结论句数()` 里那两个前缀是从主源码抄过去的；主源码改引导语而这里忘改，
     * 屏幕层的计数就会静默数到 0（看起来像「卡片没读到画像」）。这条用例把那边**字面写下的**
     * 前缀读出来，逐个要求：它是某一族结论里**每一句**的前缀，且**不是另一族任何一句**的前缀。
     */
    @Test
    fun 屏幕层的辨认前缀必须真的认得这几句结论() {
        val source = androidTestSrc()
        assertTrue("androidTest 源码目录不存在：$source", source.isDirectory)

        val screenTest = File(source, "com/fortheelder/ui/settings/SettingsScreenTest.kt")
        assertTrue("找不到 $screenTest，屏幕层的辨认前缀无从核对", screenTest.isFile)
        val text = screenTest.readText(Charsets.UTF_8)

        val prefixes = Regex("""countContaining\("([^"]+)"\)""")
            .findAll(text)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        assertTrue(
            "在 SettingsScreenTest.kt 里读到 ${prefixes.size} 个「结论前缀」（$prefixes），期望恰好 2 个。" +
                "两个前缀是屏幕层分工数「按您自己的作息」与「作息还在了解中」的唯一依据，" +
                "数法的写法变了就必须回来更新这条守卫，否则它对屏幕层失效",
            prefixes.size == 2,
        )

        val families = mapOf(
            "按您自己的作息（timingReady）" to readySentences(),
            "作息还在了解中（timingGathering）" to gatheringSentences(),
        )
        // 每个前缀都要**唯一地**认下一族：全部是它的前缀，且不是另一族任何一句的前缀。
        val unclaimed = families.keys.toMutableSet()
        val problems = mutableListOf<String>()

        prefixes.forEach { prefix ->
            val claimed = families.filterValues { sentences ->
                sentences.all { it.startsWith(prefix) } &&
                    families.values.filter { it !== sentences }.all { other -> other.none { it.startsWith(prefix) } }
            }
            when (claimed.size) {
                1 -> unclaimed.remove(claimed.keys.single())
                0 -> problems += "前缀「$prefix」认不下任何一族结论（要么它已经不是那些句子的开头，" +
                    "要么它同时是两族的开头）。屏幕层数到 0 时会被误读成「卡片没读到画像」"
                else -> problems += "前缀「$prefix」同时是 ${claimed.keys} 几族结论的开头，分不开它们"
            }
        }
        assertTrue(
            "屏幕层的辨认前缀与结论对不上了：\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
        assertTrue(
            "有结论族没有任何前缀认领（${unclaimed}）：屏幕层会数不到它",
            unclaimed.isEmpty(),
        )
    }

    /**
     * 间隔被拉长之后那段出口的**门控**（第 79 轮补）。
     *
     * ## 为什么单挑这个 `if`
     *
     * 第 77 轮把出口加出来时自己记下一条：门控那一行写在界面代码里
     * （`if ((throttle?.ignoredStreak ?: 0) > 0)`），**只有它的一半被证过**——「计数大于 0 时
     * 说明句与按钮都在」由设备层的[间隔被推到最长时卡上给出出口按下去就回到最短]盯着；
     * 「计数为 0 时那一整段一个字都不占」却没有任何用例。这个缺口的形状很讨厌：把判据写成
     * `>= 0` 之类的错法，屏幕上多出一颗**按下去什么都不变**的死按钮，而所有既有用例照绿。
     *
     * 所以这一轮把门控整段抽到 [SettingsVoice.gapExitOf]：两侧边界都能在无设备的用例里钉住，
     * 而不是只在屏幕上「看它出没出现」。
     *
     * ## 它盯的四条
     *
     * 1. 计数为 **0** ⇒ 没有出口（间隔已经最短，没有什么可拉回来的）；
     * 2. 计数为**负数** ⇒ 也按「没有出口」处理。负数不是正常输入（计数从 0 起只增不减），
     *    但正因为它不该出现，才更要钉住「不因为这个值就把死按钮摆上去」；
     * 3. 计数为 **1** ⇒ 两样都在场，且**就是那两句**：说明句等于
     *    [SettingsVoice.gapStretchedNote]、按钮等于 [SettingsVoice.presentNow]。
     *    只判「非 null」是不够的——把常量换成任何别的句子都不该静默通过；
     * 4. 计数**很大**（99 > `InterventionConfig` 里任何一档）⇒ 与计数 1 完全同一段出口，
     *    不做额外的分支。
     *
     * 末尾再加一条**分辨力**：说明句与按钮必须是**两句不同的话**，而且不能有一句含进另一句。
     * 否则屏幕层的两个整串计数会同时数到同一个节点上，「两样都在场」这句断言就落不了地
     * （同类的坑在 [SettingsVoiceTest] 上半部分那四句结论上踩过一次：第 38 轮的 M1/M2）。
     *
     * ## 它不保证什么
     *
     * - 管不了「这个计数从哪儿来」：`ignoredStreak` 是不是真的随接话与忽略一增一减，由
     *   `InterventionWindowTest` 与设备层盯着。
     * - 管不了「出口摆上去之后卡片长什么样」（顺序、间距、回执在不在）——那是设备层的事。
     * - 管不了 `gapMinutes` 的档位取值是否合适。
     */
    @Test
    fun 出口只在计数大于零时才开门() {
        assertNull(
            "一次都没被忽略过的时候，这段出口该整段收起来——间隔已经是最短那一档，" +
                "这时摆一粒按钮按下去这个数一动不动，就是一颗死按钮",
            SettingsVoice.gapExitOf(0),
        )
        assertNull(
            "计数为负不是正常输入，但也不该因此露出一颗按下去什么都不变的按钮",
            SettingsVoice.gapExitOf(-1),
        )

        val 一次 = SettingsVoice.gapExitOf(1)
        assertNotNull("计数为 1 就该有出口：这是「有人被忽略过」的第一档", 一次)
        assertEquals(
            "出口上面那句说明必须是那一句「您有一阵子没接着说话…」",
            SettingsVoice.gapStretchedNote,
            一次?.note,
        )
        assertEquals(
            "出口按钮必须是那一粒「我现在就在这儿」",
            SettingsVoice.presentNow,
            一次?.button,
        )

        val 很多次 = SettingsVoice.gapExitOf(99)
        assertNotNull("计数再大也还是同一段出口，不该有别的分支把它收起来", 很多次)
        assertEquals(
            "很大的计数与计数 1 该说出完全一样的一段出口",
            一次?.note to 一次?.button,
            很多次?.note to 很多次?.button,
        )

        assertNotEquals(
            "出口的说明句与按钮是同一句话：屏幕层按整串数这两样，会把同一个节点数成两样都在场",
            很多次?.note,
            很多次?.button,
        )
        assertTrue(
            "出口的说明句或按钮是空白句：空白句会被任何一句「含着」，屏幕层的计数会同时数到它",
            很多次?.note?.isNotBlank() == true && 很多次?.button?.isNotBlank() == true,
        )
        assertTrue(
            "说明句里含着一整句按钮（或反过来）：屏幕层按子串计数时两样会互相数到",
            很多次?.note?.contains(很多次?.button ?: "") == false &&
                很多次?.button?.contains(很多次?.note ?: "") == false,
        )
    }

    /**
     * 清空输入框再按「保存称呼」时，回执必须说「去掉了」，不能说「已保存」（第 85 轮补）。
     *
     * 这条用例钉的是 [SettingsVoice.addressReceipt] 这个判据本身，而它对应的缺陷是屏幕上的
     * 一句话：在此之前，那颗按钮的回执是写死的 [SettingsVoice.addressSaved]，而清空输入框
     * 会让 `SettingsStore.setAddress` 把称呼真的删掉——屏幕在说一件没发生的事。
     *
     * 两处判据的边界写在这儿：
     *
     * - **只管「说的是哪句」**。这句话有没有真的渲染出来、渲染在哪张卡里，是设备层那条
     *   同名用例的事（`SettingsScreenTest.清空输入框再保存回执说的是去掉而且首页又开始问称呼`）。
     * - **只管空白是不是等于去掉**。`setAddress` 那边把空白存成 `null` 这件事由
     *   `SettingsStoreTest` 盯着；这里断的是「屏幕的说法跟着同一件事走」。
     *
     * 最后三句（互不相同、互不包含、都不空白）是屏幕层计数的前提：那两条回执在屏幕上按整串
     * 数节点，若一句含着一句，`count()` 会把同一处数成两样都在场（同 [出口只在计数大于零时才开门]）。
     */
    @Test
    fun 清空之后回执说的是去掉而不是保存() {
        val 去掉 = SettingsVoice.addressCleared

        assertEquals("空串就是去掉，回执必须说去掉那句", 去掉, SettingsVoice.addressReceipt(""))
        assertEquals("一个字都没有（null）同样是去掉", 去掉, SettingsVoice.addressReceipt(null))
        assertEquals("只剩空白同样是去掉：库里存的判据也是先去掉两头空白", 去掉, SettingsVoice.addressReceipt("   "))
        assertEquals("两头带空白的空白串还是去掉", 去掉, SettingsVoice.addressReceipt("\t \n"))

        assertEquals(
            "填了名字就是存下，回执必须说保存那句",
            SettingsVoice.addressSaved,
            SettingsVoice.addressReceipt("老张"),
        )
        assertEquals(
            "两头带空白的名字仍然是一个名字，不是去掉",
            SettingsVoice.addressSaved,
            SettingsVoice.addressReceipt("  老张  "),
        )

        assertNotEquals("两句回执是同一句话：屏幕上按整串数会把一次保存数成两处都在场", 去掉, SettingsVoice.addressSaved)
        assertTrue(
            "去掉那句是空白句：空白句会被任何一句含着，屏幕层的计数会同时数到它",
            去掉.isNotBlank(),
        )
        assertTrue(
            "两句回执里有一句含着另一句：屏幕上按子串计数时两样会互相数到",
            !去掉.contains(SettingsVoice.addressSaved) && !SettingsVoice.addressSaved.contains(去掉),
        )
        assertNotEquals(
            "回执与尾注是同一句话：默认就摆着的尾注会被当成刚发生的回执",
            去掉,
            SettingsVoice.addressHint,
        )
        assertTrue(
            "回执里含着一整句尾注（或反过来）：尾注一直在场，回执一出现就会把尾注数成两处",
            !去掉.contains(SettingsVoice.addressHint) && !SettingsVoice.addressHint.contains(去掉),
        )
        assertNotEquals(
            "新的尾注与「已保存」是同一句话",
            SettingsVoice.addressHint,
            SettingsVoice.addressSaved,
        )
        assertTrue(
            "尾注里含着一整句「已保存」：保存之后屏幕上会同时数到两句",
            !SettingsVoice.addressHint.contains(SettingsVoice.addressSaved),
        )
    }

    /** androidTest 源码目录由 `app/build.gradle.kts` 的系统属性提供；缺失时直接失败而不是跳过。 */
    private fun androidTestSrc(): File = File(
        System.getProperty("fortheelder.androidTestSrc")
            ?: error("缺少系统属性 fortheelder.androidTestSrc，无法定位 androidTest 源码目录"),
    )
}
