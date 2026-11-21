package com.fortheelder.ui.talk

import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishStatus
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

    // ---- 第 10 轮：哪些意愿配得上一个起点（FR-08）----

    /**
     * 只有「确认过的 + 想做的事 + 目录里有」才配得上起点。
     *
     * 这条判据是 RC-009 的核心：状态类（睡不着）与请求类（想找人帮忙）**明确不给选项**，
     * 因为「今晚没睡着」和「想找人下棋」递过去一个活动，是打扰不是陪伴。
     */
    @Test
    fun `起点只给确认过的想做的事`() {
        val page = TalkPageState(
            remembered = listOf(
                wish(IntentKind.INTEREST, "书法", WishStatus.CONFIRMED),
                wish(IntentKind.STATE, "睡不着", WishStatus.CONFIRMED),
                wish(IntentKind.REQUEST, "下棋的地方", WishStatus.CONFIRMED),
                wish(IntentKind.INTEREST, "唱歌", WishStatus.PENDING),
                wish(IntentKind.INTEREST, "跳舞", WishStatus.DECLINED),
            ),
        )

        assertEquals(listOf("书法"), page.offerableWishes.map { it.term })
    }

    @Test
    fun `目录没覆盖的兴趣不给起点`() {
        // 「园艺」这类话识别得出来，但目录里没有对应的小事。宁可不给，
        // 也不能拿一条不相关的小事去凑数——那才是真的假功能。
        val outside = listOf("园艺", "看老电影")
        for (term in outside) {
            assertTrue(
                "「$term」现在已经在目录里了，这条测试要换一个目录外的词",
                !StarterCatalog.coveredTerms.contains(term),
            )
        }

        val page = TalkPageState(
            remembered = outside.map { wish(IntentKind.INTEREST, it, WishStatus.CONFIRMED) },
        )

        assertTrue("目录外的兴趣也给了起点", page.offerableWishes.isEmpty())
    }

    @Test
    fun `起点的顺序跟用户说过的顺序一致`() {
        val page = TalkPageState(
            remembered = listOf(
                wish(IntentKind.INTEREST, "下棋", WishStatus.CONFIRMED),
                wish(IntentKind.STATE, "睡不着", WishStatus.CONFIRMED),
                wish(IntentKind.INTEREST, "养花", WishStatus.CONFIRMED),
            ),
        )

        assertEquals(listOf("下棋", "养花"), page.offerableWishes.map { it.term })
    }

    private fun wish(kind: IntentKind, term: String, status: WishStatus) = StoredWish(
        kind = kind,
        term = term,
        evidence = term,
        sentence = term,
        status = status,
        heardAtEpochMilli = 0L,
        decidedAtEpochMilli = if (status == WishStatus.PENDING) null else 1L,
    )
}
