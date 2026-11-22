package com.fortheelder.ui.settings

import com.fortheelder.domain.engine.rhythm.RhythmConfig
import java.io.File
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

    /** 「作息还在了解中：…」那一族的全部取值。 */
    private fun gatheringSentences(): List<String> =
        daysGrid.map { days -> SettingsVoice.timingGathering(days, requiredDays) }

    /** 「现在打招呼的间隔：…」那一族的全部取值。 */
    private fun gapLines(): List<String> = listOf("30 分钟", "1 小时", "半天").map { SettingsVoice.gapLine(it) }

    /** 一句话到底说了几种事：四句结论加上间隔那一行（间隔行也参与「不许互相含」）。 */
    private fun allSentences(): List<String> =
        listOf(SettingsVoice.timingLoading, SettingsVoice.timingInconsistent) +
            readySentences() + gatheringSentences() + gapLines()

    @Test
    fun 四句结论里没有两句是同一句话() {
        val all = allSentences()
        // 网格或句表被改空的话，下面两个循环会一个都不跑、守卫变成永远绿。
        assertTrue("这句话太少（${all.size} 句），参数网格或句表被改空了", all.size >= 26)

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

    /** androidTest 源码目录由 `app/build.gradle.kts` 的系统属性提供；缺失时直接失败而不是跳过。 */
    private fun androidTestSrc(): File = File(
        System.getProperty("fortheelder.androidTestSrc")
            ?: error("缺少系统属性 fortheelder.androidTestSrc，无法定位 androidTest 源码目录"),
    )
}
