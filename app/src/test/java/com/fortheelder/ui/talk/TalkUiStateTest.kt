package com.fortheelder.ui.talk

import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「说两句」页面的状态映射与示例话的单元测试（JVM）。
 *
 * 重点守两件事：
 * 1. 「识别到 / 没识别到」两态不能混为一谈——后者必须是一个明确的、能被界面处理的状态；
 * 2. 页面上摆出来的示例话**必须一直是真的**。示例句坏掉的话，用户点下去看到的是
 *    「听不明白」，而我们演示的却是「它听得懂」——那就成了假功能。
 */
class TalkUiStateTest {

    @Test
    fun `识别到时进入听见状态并带上命中`() {
        val sentence = "我一直想学书法"
        val scan = IntentScanner.scan(sentence)

        val state = TalkUiState.of(sentence, scan)

        val heard = state as? TalkUiState.Heard
        assertTrue("期望 Heard，实际是 $state", heard != null)
        assertEquals(sentence, heard!!.sentence)
        assertEquals((scan as IntentScan.Recognized).hits, heard.hits)
    }

    @Test
    fun `没识别到时进入没听明白状态`() {
        val sentence = "今天天气还行"

        val state = TalkUiState.of(sentence, IntentScanner.scan(sentence))

        val missed = state as? TalkUiState.NotUnderstood
        assertTrue("期望 NotUnderstood，实际是 $state", missed != null)
        assertEquals(sentence, missed!!.sentence)
    }

    @Test
    fun `能给识别出来的示例话都真的能被识别`() {
        for (sentence in TalkExamples.recognized) {
            val scan = IntentScanner.scan(sentence)
            val hits = (scan as? IntentScan.Recognized)?.hits
            assertTrue("示例话「$sentence」现在识别不出来了，页面上演示的就是坏掉的功能", !hits.isNullOrEmpty())
        }
    }

    @Test
    fun `故意留的那句示例话一直识别不出来`() {
        for (sentence in TalkExamples.notRecognized) {
            val scan = IntentScanner.scan(sentence)
            assertTrue(
                "「$sentence」本来是拿来演示「不猜」的，现在被识别成了 $scan",
                scan is IntentScan.NotRecognized,
            )
        }
    }

    @Test
    fun `点选顺序是先能听懂的再听不明白的`() {
        assertEquals(TalkExamples.recognized + TalkExamples.notRecognized, TalkExamples.sentences)
    }
}
