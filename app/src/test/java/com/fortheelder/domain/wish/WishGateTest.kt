package com.fortheelder.domain.wish

import com.fortheelder.domain.engine.intent.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 意愿确认闸门的单元测试（JVM，不碰 Room、不碰界面）。
 *
 * AC-07.4 明确要求「闸门独立于 NLU，可单独单测」——这个文件就是那条要求的证据：
 * 它只 import 了闸门本身和类别枚举，没有任何 `android`/`androidx` 符号。
 *
 * 穷举是刻意的：闸门一共只有 3 个状态 × 2 个决定 = 6 种（状态, 决定）组合，
 * 全列出来比「抽几条看着像的」更能保证以后加状态时有人会看到这里变红。
 */
class WishGateTest {

    private fun wish(
        status: WishStatus,
        kind: IntentKind = IntentKind.INTEREST,
        term: String = "书法",
    ) = StoredWish(
        kind = kind,
        term = term,
        evidence = "一直想学书法",
        sentence = "我一直想学书法",
        status = status,
        heardAtEpochMilli = 1_700_000_000_000L,
        decidedAtEpochMilli = null,
    )

    private val allStatuses = WishStatus.entries.toList()
    private val allDecisions = WishDecision.entries.toList()

    // ---- mayOffer：只有「同意过」才放行内容 ----

    @Test
    fun mayOffer_onlyConfirmed() {
        assertTrue(WishGate.mayOffer(WishStatus.CONFIRMED))
        assertFalse(WishGate.mayOffer(WishStatus.PENDING))
        assertFalse(WishGate.mayOffer(WishStatus.DECLINED))
    }

    @Test
    fun mayOffer_pendingAndDeclinedAreBothBlocked() {
        // 分开写一遍是因为这两条挡的是不同的东西：PENDING 挡「替用户做主」，
        // DECLINED 挡「人家说了不用还硬塞」。合成一条断言会看不出是哪条退化。
        assertEquals(listOf(WishStatus.PENDING, WishStatus.DECLINED), allStatuses.filterNot { WishGate.mayOffer(it) })
    }

    // ---- mayBringUp：只有「还没问过」才可以主动问一次 ----

    @Test
    fun mayBringUp_onlyPending() {
        assertTrue(WishGate.mayBringUp(WishStatus.PENDING))
        assertFalse(WishGate.mayBringUp(WishStatus.CONFIRMED))
        assertFalse(WishGate.mayBringUp(WishStatus.DECLINED))
    }

    @Test
    fun mayBringUp_declinedMustNotBeBroughtUpAgain() {
        // AC-07.3 的直接断言。这条如果变红，就是「用户说了不用还反复问」这个 bug 回来了。
        assertFalse(WishGate.mayBringUp(WishStatus.DECLINED))
    }

    @Test
    fun mayOfferAndMayBringUp_neverBothTrue() {
        for (status in allStatuses) {
            assertFalse(
                "状态 $status 同时允许推内容和允许主动提起，这两件事不该同时成立",
                WishGate.mayOffer(status) && WishGate.mayBringUp(status),
            )
        }
    }

    // ---- bringUpable ----

    @Test
    fun bringUpable_emptyStaysEmpty() {
        assertEquals(emptyList<StoredWish>(), WishGate.bringUpable(emptyList()))
    }

    @Test
    fun bringUpable_keepsOnlyPendingAndKeepsOrder() {
        val input = listOf(
            wish(WishStatus.PENDING, term = "书法"),
            wish(WishStatus.CONFIRMED, term = "摄影"),
            wish(WishStatus.PENDING, term = "下棋"),
            wish(WishStatus.DECLINED, term = "书法"),
        )
        val terms = WishGate.bringUpable(input).map { it.term }
        assertEquals(listOf("书法", "下棋"), terms)
    }

    @Test
    fun bringUpable_allSettledGivesEmpty() {
        val input = listOf(
            wish(WishStatus.CONFIRMED, term = "摄影"),
            wish(WishStatus.DECLINED, term = "书法"),
        )
        assertEquals(emptyList<StoredWish>(), WishGate.bringUpable(input))
    }

    @Test
    fun bringUpable_doesNotModifyInput() {
        val input = listOf(wish(WishStatus.CONFIRMED), wish(WishStatus.PENDING, term = "下棋"))
        WishGate.bringUpable(input)
        assertEquals(2, input.size)
    }

    // ---- decide：6 种组合穷举 ----

    @Test
    fun decide_pendingConfirm_becomesConfirmed() {
        assertEquals(WishStatus.CONFIRMED, WishGate.decide(WishStatus.PENDING, WishDecision.CONFIRM))
    }

    @Test
    fun decide_pendingDecline_becomesDeclined() {
        assertEquals(WishStatus.DECLINED, WishGate.decide(WishStatus.PENDING, WishDecision.DECLINE))
    }

    @Test
    fun decide_confirmedConfirm_isNoOp() {
        assertNull(WishGate.decide(WishStatus.CONFIRMED, WishDecision.CONFIRM))
    }

    @Test
    fun decide_declinedDecline_isNoOp() {
        assertNull(WishGate.decide(WishStatus.DECLINED, WishDecision.DECLINE))
    }

    @Test
    fun decide_confirmedDecline_becomesDeclined() {
        assertEquals(WishStatus.DECLINED, WishGate.decide(WishStatus.CONFIRMED, WishDecision.DECLINE))
    }

    @Test
    fun decide_declinedConfirm_becomesConfirmed() {
        // 用户自己回到列表里点「我现在想了解一下」。这是显式动作，不算「常伴又提起」（AC-07.3）。
        // 没有这条路径的话，一次误点「先不用」就等于永久丢掉这条意愿。
        assertEquals(WishStatus.CONFIRMED, WishGate.decide(WishStatus.DECLINED, WishDecision.CONFIRM))
    }

    @Test
    fun decide_returnsNullExactlyWhenStatusWouldNotChange() {
        for (from in allStatuses) {
            for (decision in allDecisions) {
                val to = WishGate.decide(from, decision)
                val expectedNoOp = when (decision) {
                    WishDecision.CONFIRM -> from == WishStatus.CONFIRMED
                    WishDecision.DECLINE -> from == WishStatus.DECLINED
                }
                assertEquals("($from, $decision) 的「是否无变化」判断错了", expectedNoOp, to == null)
            }
        }
    }

    @Test
    fun decide_neverProducesPending() {
        // PENDING 只由「刚听出来」产生，表态不可能把状态退回「没问过」——
        // 退回去的话同一件事会被反复问，那正是 AC-07.3 要挡的。
        for (from in allStatuses) {
            for (decision in allDecisions) {
                assertTrue("($from, $decision) 不该产出 PENDING", WishGate.decide(from, decision) != WishStatus.PENDING)
            }
        }
    }

    @Test
    fun decide_resultAgreesWithDecisionAndWithMayOffer() {
        for (from in allStatuses) {
            for (decision in allDecisions) {
                val to = WishGate.decide(from, decision) ?: continue
                val expected = when (decision) {
                    WishDecision.CONFIRM -> WishStatus.CONFIRMED
                    WishDecision.DECLINE -> WishStatus.DECLINED
                }
                assertEquals("($from, $decision) 落到别的状态去了", expected, to)
                assertEquals(
                    "($from, $decision) 之后「能不能给内容」与决定不一致",
                    decision == WishDecision.CONFIRM,
                    WishGate.mayOffer(to),
                )
            }
        }
    }
}
