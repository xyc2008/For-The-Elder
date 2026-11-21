package com.fortheelder.domain.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 10 轮新界面文案的单元测试（JVM）。
 *
 * 新文案全部集中在 [CompanionVoice]，所以这里的词表扫描一次就能覆盖整轮的界面文字。
 * 另有一条纪律单列出来：不能拿「提醒/通知/推送/闹钟/到点」这四个字许诺事情——
 * 本版本没有任何通知通道，写了就是假承诺。
 */
class CompanionVoiceTest {

    @Test
    fun everyStaticLinePassesTheWordScan() {
        val lines = CompanionVoice.staticLines()
        assertTrue("被扫的文案不该是空的", lines.size >= 15)
        for (line in lines) {
            assertEquals("文案「$line」有问题：${CompanionVoice.violations(line)}", emptyList<String>(), CompanionVoice.violations(line))
        }
    }

    @Test
    fun noLinePromisesAChannelWeDoNotHave() {
        for (line in CompanionVoice.staticLines()) {
            for (word in CompanionVoice.absentChannelWords) {
                assertFalse("文案里出现本版本没有的通道「$word」：$line", line.contains(word))
            }
        }
    }

    @Test
    fun staticLinesCoverEverySlotButton() {
        val lines = CompanionVoice.staticLines()
        for (slot in AnchorSlot.entries) {
            assertTrue("时段「$slot」的按钮文案没被扫到", lines.contains(CompanionVoice.confirmSlotLabel(slot)))
        }
    }

    @Test
    fun slotButtonsAreFourDistinctLabelsAndCoverEverySlot() {
        val labels = AnchorSlot.entries.map { CompanionVoice.confirmSlotLabel(it) }
        assertEquals(4, labels.size)
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(AnchorSlot.entries.toList(), CompanionVoice.confirmableSlots)
        for (label in labels) assertTrue(label.isNotBlank())
    }

    @Test
    fun minutesAndPrepLinesBothAnswerTheirQuestion() {
        assertEquals("大概 10 分钟", CompanionVoice.minutesLine(10))
        assertEquals("要准备：什么都不用准备", CompanionVoice.prepLine("什么都不用准备"))
    }

    @Test
    fun dueLineNamesTheActionTheUserRemembered() {
        val anchor = Anchor(
            id = 1L,
            term = "书法",
            optionId = "calligraphy-paper",
            action = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
            slot = AnchorSlot.MORNING,
            createdAtEpochMilli = 0L,
        )
        val line = CompanionVoice.dueLine(anchor)
        assertTrue("首页那一行没有说出用户记下的那件事", line.contains(anchor.action))
        assertEquals(emptyList<String>(), CompanionVoice.violations(line))
    }

    @Test
    fun anchorAskNamesTheActionAndPromisesNothingExtra() {
        val line = CompanionVoice.anchorAsk("写满一张纸")
        assertTrue(line.contains("写满一张纸"))
        assertTrue("要说清是用户自己决定记下来的", line.contains("随时能删"))
        assertEquals(emptyList<String>(), CompanionVoice.violations(line))
    }

    @Test
    fun forgettingAThingIsStatedPlainlyAndNeverAsAQuestion() {
        val line = CompanionVoice.anchorNote()
        assertTrue("说明书里要写清删掉就是删掉", line.contains(CompanionVoice.anchorForgetLabel()))
        assertFalse(line.contains("？"))
        assertFalse(line.contains("?"))
    }

    @Test
    fun emptyAnchorCardTellsTheUserHowToFillIt() {
        val line = CompanionVoice.anchorEmpty()
        assertTrue(line.contains(CompanionVoice.rememberLabel()))
        assertEquals(emptyList<String>(), CompanionVoice.violations(line))
    }

    @Test
    fun optionsIntroNamesTheThingTheUserWanted() {
        assertEquals("「摄影」可以这样开始", CompanionVoice.optionsIntro("摄影"))
    }

    @Test
    fun violationsCatchForbiddenWording() {
        assertTrue(CompanionVoice.violations("到点我会提醒您").toString().contains("提醒"))
        assertTrue(CompanionVoice.violations("现在报名还来得及").toString().contains("报名"))
        assertTrue(CompanionVoice.violations("每天打卡连续一周").toString().contains("打卡"))
        assertTrue(CompanionVoice.violations("").toString().contains("空"))
    }

    @Test
    fun scanAlsoCoversEveryOptionAndEncouragementCopy() {
        // 目录文案与鼓励语也过一次这里的分词表，免得只在各自的测试里各扫一遍却漏了通道纪律。
        for (option in StarterCatalog.all) {
            for ((field, text) in StarterRules.fieldsOf(option)) {
                assertEquals("${option.id} 的$field 有问题", emptyList<String>(), CompanionVoice.violations(text))
            }
            assertEquals(
                "${option.id} 的鼓励语有问题",
                emptyList<String>(),
                CompanionVoice.violations(Encouragement.forAction(option.firstStep)),
            )
        }
    }
}
