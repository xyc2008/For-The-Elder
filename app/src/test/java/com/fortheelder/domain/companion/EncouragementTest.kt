package com.fortheelder.domain.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持续鼓励的单元测试（JVM）。
 *
 * AC-10.1 要求鼓励针对**具体做完的事**，所以每个起点的第一步都拿去生成一次回话，
 * 逐条检查回话里真的有那件事的原文；AC-10.2/10.3 用词表扫。
 */
class EncouragementTest {

    @Test
    fun everyOptionGetsAnEncouragementAboutThatVeryThing() {
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            assertTrue("「${option.id}」的鼓励语里没有那件事本身", text.contains(option.firstStep))
            assertEquals("「${option.id}」的鼓励语有问题：${Encouragement.violations(text, option.firstStep)}", emptyList<String>(), Encouragement.violations(text, option.firstStep))
        }
    }

    @Test
    fun encouragementDiffersPerAction() {
        val first = Encouragement.forAction(StarterCatalog.all[0].firstStep)
        val second = Encouragement.forAction(StarterCatalog.all[1].firstStep)
        assertNotEquals("两件不同的事得到同一句话，那就是通用好话", first, second)
    }

    @Test
    fun noEncouragementUsesGenericPraise() {
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            for (word in CopyRules.emptyPraiseWords) {
                assertFalse("鼓励语里出现放到哪件事上都成立的空话「$word」", text.contains(word))
            }
        }
    }

    @Test
    fun noEncouragementHasPointsLevelsOrBadges() {
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            for (word in CopyRules.streakWords) {
                assertFalse("鼓励语里出现累计或施压的说法「$word」", text.contains(word))
            }
        }
    }

    @Test
    fun noEncouragementSellsAnything() {
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            for (word in CopyRules.spendWords) {
                assertFalse("鼓励语里出现要花钱的内容「$word」", text.contains(word))
            }
        }
    }

    @Test
    fun noEncouragementHasDoubledPunctuation() {
        // 起点卡上每一条「第一步」的原文自己就以句号结尾（39/39，实测），
        // 模板若无条件在后面再补一个句号，屏幕上就会出现「。。」。
        // 这条用例把那次拼接缺陷钉住：改模板的人必须先让它绿。
        val doubled = listOf("。。", "！！", "？？", "，。", "。，", "。！", "。？")
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            for (mark in doubled) {
                assertFalse("「${option.id}」的鼓励语里出现重复标点「$mark」：$text", text.contains(mark))
            }
            assertTrue("「${option.id}」的鼓励语没有收尾标点：$text", text.endsWith("。"))
        }
    }

    @Test
    fun forActionAddsAPeriodWhenTheActionHasNone() {
        // 条件式标点的另一半：原文没有句末标点时，模板仍然要把它收住。
        val text = Encouragement.forAction("写满一张纸")
        assertTrue("缺句号的原文没有得到句号：$text", text.contains("写满一张纸。"))
        assertTrue(text.endsWith("。"))
    }

    @Test
    fun violationsCatchGenericPraise() {
        val found = Encouragement.violations("您真棒，加油！").toString()
        assertTrue(found.contains("真棒"))
        assertTrue(found.contains("加油"))
    }

    @Test
    fun violationsCatchStreakAndShoppingTalk() {
        val streak = Encouragement.violations("您刚做完的是：写满一张纸。这是连续第 3 天打卡。").toString()
        assertTrue(streak.contains("连续") || streak.contains("打卡"))

        val shopping = Encouragement.violations("您刚做完的是：写满一张纸。现在下单有优惠。").toString()
        assertTrue(shopping.contains("下单"))
        assertTrue(shopping.contains("优惠"))
    }

    @Test
    fun violationsCatchTextThatIgnoresTheAction() {
        val found = Encouragement.violations("您刚做完的是：别的什么。", action = "写满一张纸").toString()
        assertTrue(found.contains("没有那件事本身"))
    }

    @Test
    fun violationsCatchEmptyAndTooShortText() {
        assertTrue(Encouragement.violations("").toString().contains("空"))
        assertTrue(Encouragement.violations("好的").toString().contains("太短"))
    }

    @Test
    fun encouragementNeverMentionsAnyChannelWeDoNotHave() {
        // 鼓励是一次性的回话，不该顺手承诺「我会提醒您」这类本版本没有的东西。
        for (option in StarterCatalog.all) {
            val text = Encouragement.forAction(option.firstStep)
            for (word in CompanionVoice.absentChannelWords) {
                assertFalse("鼓励语里拿本版本没有的通道「$word」许诺了事情", text.contains(word))
            }
        }
    }
}
