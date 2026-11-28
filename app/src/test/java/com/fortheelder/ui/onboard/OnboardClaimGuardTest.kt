package com.fortheelder.ui.onboard

import com.fortheelder.domain.play.PlayCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导文案里那些「几个」「几条」的数，得跟它数的东西对得上。
 *
 * 这一版引导页上有一批**可数**的说法：第一条「它现在会做的」列了五条，最后一条说
 * 「有六个小游戏」，类注释里写着「会做的」五条、「三条边界」、以及每条能力对应的页面。
 * 这些字都没有第二个来源——写错了没有谁会发现，文档守卫
 * （[com.fortheelder.ui.DocScreenCopyGuardTest]）把数字归一成了占位符，只核句子在不在、
 * 不核数对不对；[com.fortheelder.domain.play.PlayCatalogTest] 只核目录自己内部一致，
 * 不问文案说了几个。于是「六个」这件事实质上一直是**没人管**的：第 144 轮往目录里加了
 * 第六个游戏（找一个不一样的），文案改不改、对不对，跑多少测试都不会红。
 *
 * 这里补的就是这把尺子：**文案里数出来的数，必须等于它数的那堆东西的真实条数**。
 *
 * ## 这份尺子不保证什么
 *
 * - 「跟它说两句」里的「两」是**习语**，不是计数。尺子只从「X个小游戏」这个固定位置上取
 *   数字，所以这句永远不会被喂进解析器。反过来说，如果有人把某条文案写成「两个小游戏」，
 *   解析器照样会读成 2——它分辨不了习语，只认位置。
 * - 注释里那几个括注（设置页、问候引擎、三档排版表、对话页）**各自指向的能力是否真的存在，
 *   这里不核**。那要有别的尺子（界面在哪、常量在哪）才说得清，不是数条数能解决的。
 * - 只核 [OnboardVoice] 这一份文案与 [PlayCatalog] 这一份目录。别处若还有「六个」的说法，
 *   这份尺子管不到。
 *
 * 解析器的规矩：读不出数就**报错**，不静默当 0。宁可红一次提醒有人改了措辞，
 * 也不要让这把尺子悄悄退化成一句永远成立的断言。
 */
class OnboardClaimGuardTest {

    private val mainSrc: File = File(
        System.getProperty("fortheelder.mainSrc")
            ?: error("缺少系统属性 fortheelder.mainSrc，无法定位 main 源码目录"),
    )

    /** 引导文案那条提到小游戏的句子。它必须**恰好有一条**，多一条少一条都要红。 */
    private val gameBullet: String =
        OnboardVoice.doesBullets.single { it.contains("小游戏") }

    private val onboardSource: String = readSource(
        File(mainSrc, "com/fortheelder/ui/onboard/OnboardVoice.kt"),
    )

    @Test
    fun gameCountInOnboardCopyMatchesTheCatalog() {
        val said = parseChineseCount(
            numeralBeforeGameCount(gameBullet, "引导文案那条提到小游戏的句子"),
            "引导文案那条提到小游戏的句子",
        )
        assertEquals(
            "文案上写着有 $said 个小游戏，游戏页的目录里其实有 ${PlayCatalog.games.size} 个",
            PlayCatalog.games.size,
            said,
        )
    }

    @Test
    fun bulletCountsInOnboardCopyMatchTheirHeadings() {
        // 「会做的」五条：说几条，列表里就得有几条。
        val saidDoes = parseChineseCount(
            requireMatch(
                Regex("「会做的」([一二三四五六七八九十两]+)条"),
                onboardSource,
                "「会做的」X条",
            ),
            "「会做的」X条",
        )
        assertEquals(
            "注释说「会做的」有 $saidDoes 条，实际列了 ${OnboardVoice.doesBullets.size} 条",
            OnboardVoice.doesBullets.size,
            saidDoes,
        )

        // 「三条边界」：同一条规矩。
        val saidWont = parseChineseCount(
            requireMatch(
                Regex("([一二三四五六七八九十两]+)条边界"),
                onboardSource,
                "X条边界",
            ),
            "X条边界",
        )
        assertEquals(
            "注释说有 $saidWont 条边界，实际列了 ${OnboardVoice.wontBullets.size} 条",
            OnboardVoice.wontBullets.size,
            saidWont,
        )

        // 类注释里「一样是三条以上」：两条列表都得够这个下限。
        val floor = parseChineseCount(
            requireMatch(
                Regex("一样是([一二三四五六七八九十两]+)条以上"),
                onboardSource,
                "一样是X条以上",
            ),
            "一样是X条以上",
        )
        assertTrue(
            "注释说两条列表都是 $floor 条以上，「会做的」只有 ${OnboardVoice.doesBullets.size} 条",
            OnboardVoice.doesBullets.size >= floor,
        )
        assertTrue(
            "注释说两条列表都是 $floor 条以上，「不会做的」只有 ${OnboardVoice.wontBullets.size} 条",
            OnboardVoice.wontBullets.size >= floor,
        )
    }

    @Test
    fun capabilityItemsInTheCommentMatchTheBulletList() {
        val anchor = "真的存在的能力："
        val start = onboardSource.indexOf(anchor)
        assertTrue(
            "OnboardVoice.kt 的注释里找不到「$anchor」，这段说明被删或改了措辞，尺子失效",
            start >= 0,
        )
        val stop = onboardSource.indexOf('。', start + anchor.length)
        assertTrue("「$anchor」后面没有句号，取不出这份能力清单", stop > start)

        val items = onboardSource.substring(start + anchor.length, stop)
            .split('、')
            .map { it.trim() }
        assertTrue("能力清单里出现了空条目：$items", items.all { it.isNotEmpty() })
        assertEquals(
            "注释给每条文案都配了一份「它对应哪里的能力」，共 ${items.size} 条，" +
                "文案却有 ${OnboardVoice.doesBullets.size} 条",
            OnboardVoice.doesBullets.size,
            items.size,
        )

        val gameItem = items.single { it.contains("小游戏") }
        assertEquals(
            "注释说能力清单里的游戏是「$gameItem」，游戏页却有 ${PlayCatalog.games.size} 个",
            PlayCatalog.games.size,
            parseChineseCount(numeralBeforeGameCount(gameItem, "能力清单里的游戏那条"), "能力清单里的游戏那条"),
        )
    }

    /**
     * 尺子本身的自检。
     *
     * 少了这一条，上面几条断言有可能因为「目录空了」「解析器把什么都读成 6」而**空转着绿**。
     * 下面把两头都钉死：目录当下确实是六个，解析器读得准。
     */
    @Test
    fun theParserAndTheCatalogAreNotDegenerate() {
        assertEquals("目录条数变了，这条自检得跟着改", 6, PlayCatalog.games.size)
        assertEquals(1, parseChineseCount("一", "自检"))
        assertEquals(6, parseChineseCount("六", "自检"))
        assertEquals(10, parseChineseCount("十", "自检"))
        assertEquals(13, parseChineseCount("十三", "自检"))
        assertEquals(20, parseChineseCount("二十", "自检"))

        var threw = false
        try {
            parseChineseCount("好些", "自检")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("解析器对「好些」没有报错，它会静默把读不出的东西当成某个数", threw)
    }

    // ---- 取数与解析 ----

    /** 从「……六个小游戏……」里把「六」掏出来。掏不出就报错，不返回空串。 */
    private fun numeralBeforeGameCount(text: String, where: String): String =
        requireMatch(Regex("([一二三四五六七八九十两]+)个小游戏"), text, "$where 里的「X个小游戏」")

    private fun requireMatch(regex: Regex, text: String, what: String): String {
        val found = regex.find(text)
            ?: error("在 $what 里按 $regex 找不到数，尺子失效了：把说明补回去，或者改这份测试")
        return found.groupValues[1]
    }

    /**
     * 把「六」「十三」「二十」读成整数。
     *
     * 只认一~九、十、两，组合方式只认「X十Y」与「X十」；别的写法一律报错。
     * 报错而不是兜底返回 0，是因为兜底会让上面所有断言在措辞被改后**假装通过**。
     */
    private fun parseChineseCount(raw: String, where: String): Int {
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (raw.isEmpty()) error("$where 里没有数字")
        if ('十' in raw) {
            val parts = raw.split('十')
            if (parts.size != 2) error("$where 写成「$raw」，这份尺子只认一个「十」")
            val tens = if (parts[0].isEmpty()) 1 else singleDigit(parts[0], digits, where)
            val ones = if (parts[1].isEmpty()) 0 else singleDigit(parts[1], digits, where)
            return tens * 10 + ones
        }
        return raw.sumOf { singleDigit(it.toString(), digits, where) }
    }

    private fun singleDigit(text: String, digits: Map<Char, Int>, where: String): Int {
        if (text.length != 1) error("$where 写成「$text」，这份尺子只认单个汉字")
        return digits[text[0]] ?: error("$where 里的「$text」不在 一~九、十、两 之内")
    }

    private fun readSource(file: File): String {
        check(file.isFile) { "找不到 $file，引导文案可能被挪走了" }
        return file.readText(Charsets.UTF_8)
    }
}
