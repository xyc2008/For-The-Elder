package com.fortheelder.domain.companion

import com.fortheelder.domain.wish.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.LocalTime

/**
 * 生活锚点的规则单元测试（JVM，纯函数）。
 *
 * 三条要守住的东西：
 * - **AC-09.1**：锚点只能由用户确认过的意愿推过来，[AnchorRules.mayPropose] 是唯一入口判据；
 * - **AC-09.2**：中断之后不显示「已中断 N 天」。这条不是靠界面不写这句话，而是靠
 *   [Anchor] / [AnchorDraft] 上**没有任何计数字段**——用反射钉住；
 * - **AC-09.3**：删掉就是删掉，[AnchorRules.deletionAsksAgain] 刻意是 `false`。
 */
class AnchorRulesTest {

    // ---- 时段边界 ----

    @Test
    fun morningCoversFiveToNoon() {
        assertFalse(AnchorSlot.MORNING.covers(LocalTime.of(4, 59)))
        assertTrue(AnchorSlot.MORNING.covers(LocalTime.of(5, 0)))
        assertTrue(AnchorSlot.MORNING.covers(LocalTime.of(9, 30)))
        assertTrue(AnchorSlot.MORNING.covers(LocalTime.of(11, 59)))
        assertFalse(AnchorSlot.MORNING.covers(LocalTime.of(12, 0)))
    }

    @Test
    fun afternoonCoversNoonToEvening() {
        assertFalse(AnchorSlot.AFTERNOON.covers(LocalTime.of(11, 59)))
        assertTrue(AnchorSlot.AFTERNOON.covers(LocalTime.of(12, 0)))
        assertTrue(AnchorSlot.AFTERNOON.covers(LocalTime.of(15, 0)))
        assertTrue(AnchorSlot.AFTERNOON.covers(LocalTime.of(17, 59)))
        assertFalse(AnchorSlot.AFTERNOON.covers(LocalTime.of(18, 0)))
    }

    @Test
    fun eveningCoversEighteenToFiveAcrossMidnight() {
        assertFalse(AnchorSlot.EVENING.covers(LocalTime.of(17, 59)))
        assertTrue(AnchorSlot.EVENING.covers(LocalTime.of(18, 0)))
        assertTrue(AnchorSlot.EVENING.covers(LocalTime.of(23, 59)))
        assertTrue(AnchorSlot.EVENING.covers(LocalTime.MIDNIGHT))
        assertTrue(AnchorSlot.EVENING.covers(LocalTime.of(4, 59)))
        assertFalse(AnchorSlot.EVENING.covers(LocalTime.of(5, 0)))
    }

    @Test
    fun anytimeAlwaysCovers() {
        for (hour in 0..23) {
            for (minute in listOf(0, 30)) {
                assertTrue(AnchorSlot.ANYTIME.covers(LocalTime.of(hour, minute)))
            }
        }
    }

    @Test
    fun theThreeFixedSlotsPartitionTheWholeDay() {
        // 一天里的每一刻都恰好落在一个时段里：既不重叠（同一条日常被摆两次），也不留空。
        for (hour in 0..23) {
            for (minute in listOf(0, 15, 30, 45)) {
                val time = LocalTime.of(hour, minute)
                val hits = listOf(AnchorSlot.MORNING, AnchorSlot.AFTERNOON, AnchorSlot.EVENING).filter { it.covers(time) }
                assertEquals("$time 落进了 $hits，应当只有一个", 1, hits.size)
            }
        }
    }

    @Test
    fun unknownSlotNameFallsBackToAnytime() {
        // 兜底方向是刻意的：宁可时段不准，也不能把用户自己记下的事静默丢掉。
        assertEquals(AnchorSlot.ANYTIME, AnchorSlot.parse("星期二"))
        assertEquals(AnchorSlot.FALLBACK, AnchorSlot.parse(""))
        assertEquals(AnchorSlot.MORNING, AnchorSlot.parse("MORNING"))
    }

    @Test
    fun everySlotHasItsOwnLabel() {
        val labels = AnchorSlot.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        for (slot in AnchorSlot.entries) {
            assertEquals(slot, AnchorSlot.byLabel(slot.label))
        }
        assertNull(AnchorSlot.byLabel("清早"))
    }

    // ---- 锚点草稿：只能从目录里来 ----

    @Test
    fun draftOfReturnsNullForUnknownOption() {
        assertNull(AnchorRules.draftOf("没有这个起点", AnchorSlot.MORNING))
    }

    @Test
    fun draftOfInheritsActionAndTermFromTheCatalog() {
        for (option in StarterCatalog.all) {
            val draft = AnchorRules.draftOf(option.id, AnchorSlot.EVENING)
            assertEquals(option.term, draft?.term)
            assertEquals(option.id, draft?.optionId)
            assertEquals(option.firstStep, draft?.action)
            assertEquals(AnchorSlot.EVENING, draft?.slot)
        }
    }

    // ---- AC-09.1：只能由用户确认过的意愿推过来 ----

    @Test
    fun mayProposeOnlyForConfirmedWishesWithAKnownOption() {
        for (status in WishStatus.entries) {
            for (option in StarterCatalog.all) {
                assertEquals(
                    "($status, ${option.id}) 的提议判断错了",
                    status == WishStatus.CONFIRMED,
                    AnchorRules.mayPropose(status, option.id),
                )
            }
            assertFalse("状态 $status 下不该为一件事提议锚点", AnchorRules.mayPropose(status, "没有这个起点"))
        }
    }

    // ---- 该摆出来的日常 ----

    @Test
    fun dueAtKeepsOnlyMatchingSlotsAndKeepsOrder() {
        val anchors = listOf(
            anchor(id = 1, slot = AnchorSlot.MORNING),
            anchor(id = 2, slot = AnchorSlot.EVENING),
            anchor(id = 3, slot = AnchorSlot.ANYTIME),
            anchor(id = 4, slot = AnchorSlot.MORNING),
        )
        assertEquals(listOf(1L, 3L, 4L), AnchorRules.dueAt(anchors, LocalTime.of(9, 0)).map { it.id })
        assertEquals(listOf(2L, 3L), AnchorRules.dueAt(anchors, LocalTime.of(21, 0)).map { it.id })
        // 凌晨三点仍然算「晚上」：晚睡的人这时候才想起来，是常态，不是异常。
        assertEquals(listOf(2L, 3L), AnchorRules.dueAt(anchors, LocalTime.of(3, 0)).map { it.id })
        // 五点整一到，晚上的那条就让位给上午的。
        assertEquals(listOf(1L, 3L, 4L), AnchorRules.dueAt(anchors, LocalTime.of(5, 0)).map { it.id })
    }

    @Test
    fun dueAtStaysEmptyForEmptyInput() {
        assertEquals(emptyList<Anchor>(), AnchorRules.dueAt(emptyList(), LocalTime.NOON))
    }

    @Test
    fun dueAtDoesNotModifyInput() {
        val anchors = listOf(anchor(id = 1, slot = AnchorSlot.MORNING), anchor(id = 2, slot = AnchorSlot.MORNING))
        AnchorRules.dueAt(anchors, LocalTime.of(9, 0))
        assertEquals(2, anchors.size)
    }

    // ---- AC-09.2：结构上没有能算出「中断了几天」的东西 ----

    @Test
    fun anchorHasNoCountingFields() {
        assertNoCountingFields(Anchor::class.java, "Anchor")
        assertNoCountingFields(AnchorDraft::class.java, "AnchorDraft")
    }

    private fun assertNoCountingFields(type: Class<*>, name: String) {
        val declared = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }
        val forbidden = listOf("streak", "day", "days", "count", "times", "last", "missed", "done", "total", "seq")
        assertTrue("$name 上一个字段都没有，说明这个测试看错类型了", declared.isNotEmpty())
        for (field in declared) {
            for (word in forbidden) {
                assertFalse(
                    "$name 上出现了能算出累计量的字段「$field」（撞上 $word）：AC-09.2 要求结构上算不出来",
                    field.lowercase().contains(word),
                )
            }
        }
    }

    @Test
    fun anchorFieldsAreExactlyTheSixWeMeanToStore() {
        // 字段清单也钉住：以后有人想加一列「最近一次做到」，这里会先变红。
        val declared = Anchor::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(
            setOf("id", "term", "optionId", "action", "slot", "createdAtEpochMilli"),
            declared,
        )
    }

    // ---- AC-09.3：删掉就是删掉 ----

    @Test
    fun deletionNeverAsksAgain() {
        assertFalse("删掉之后不该追问「要不要再记回来」", AnchorRules.deletionAsksAgain)
    }

    private fun anchor(id: Long, slot: AnchorSlot) = Anchor(
        id = id,
        term = "书法",
        optionId = "calligraphy-paper",
        action = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
        slot = slot,
        createdAtEpochMilli = 1_700_000_000_000L,
    )
}
