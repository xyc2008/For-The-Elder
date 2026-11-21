package com.fortheelder.domain.engine.intervention

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ThrottleState] 的单元测试。
 *
 * 这个类只有三件事：判断「上次开口有没有被接话」、记一次开口、记一次接话。
 * 看起来简单，但它是 AC-05.3 的数据来源——只要 `afterSpoken` 在第一次开口时就加计数，
 * 新人会莫名其妙地被当成「老不理人」的用户对待。所以四种组合都摊开验。
 */
class ThrottleStateTest {

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    private val early = at("2026-03-10T09:00:00")
    private val late = at("2026-03-10T10:00:00")

    @Test
    fun `从没开口过就没有待回应的开口`() {
        assertFalse(ThrottleState().pendingSpeak)
        assertEquals(0, ThrottleState().ignoredStreak)
    }

    @Test
    fun `开过口但没人接 算待回应`() {
        val state = ThrottleState(lastSpokenAt = early)

        assertTrue(state.pendingSpeak)
    }

    @Test
    fun `开口之后有人接 就不算待回应`() {
        val state = ThrottleState(lastSpokenAt = early, lastAnsweredAt = late)

        assertFalse(state.pendingSpeak)
    }

    @Test
    fun `同一分钟里先接话后开口 仍算待回应`() {
        // 秒级信息在状态里被截掉了，同分钟只能按「接话在前」算——保守的一边，
        // 宁可多记一次忽略，也不要漏记。
        val state = ThrottleState(lastSpokenAt = early, lastAnsweredAt = early)

        assertTrue(state.pendingSpeak)
    }

    @Test
    fun `接话在前 之后再开口 算待回应`() {
        val state = ThrottleState(lastSpokenAt = late, lastAnsweredAt = early)

        assertTrue(state.pendingSpeak)
    }

    @Test
    fun `第一次开口不计忽略`() {
        val state = ThrottleState().afterSpoken(early)

        assertEquals(early, state.lastSpokenAt)
        // 第一次开口之前没有任何「被忽略」的证据，不能凭它就把间隔往后放。
        assertEquals(0, state.ignoredStreak)
    }

    @Test
    fun `连续两次没人接话 忽略计数加一`() {
        val once = ThrottleState().afterSpoken(early)
        val twice = once.afterSpoken(late)

        assertEquals(1, twice.ignoredStreak)
        assertEquals(late, twice.lastSpokenAt)
    }

    @Test
    fun `已经有人接话之后再开口 不计忽略`() {
        val answered = ThrottleState(lastSpokenAt = early, lastAnsweredAt = late, ignoredStreak = 3)

        val state = answered.afterSpoken(late.plusMinutes(1))

        assertEquals(3, state.ignoredStreak)
    }

    @Test
    fun `接话会把忽略计数清零并记下时刻`() {
        val state = ThrottleState(lastSpokenAt = early, ignoredStreak = 4)

        val answered = state.afterAnswered(late)

        assertEquals(0, answered.ignoredStreak)
        assertEquals(late, answered.lastAnsweredAt)
        // 接话不改「上次开口」，它是另一个事实。
        assertEquals(early, answered.lastSpokenAt)
        assertFalse(answered.pendingSpeak)
    }

    @Test
    fun `连着一串没被接话的开口 忽略计数一次次往上`() {
        var state = ThrottleState()
        for (i in 0..5) {
            state = state.afterSpoken(early.plusMinutes(i * 40L))
        }

        // 六次开口、零次接话：第一次不算忽略，之后每次加一。
        assertEquals(5, state.ignoredStreak)
    }

    @Test
    fun `忽略计数永远不会是负的`() {
        // 从坏数据里读出来的负数也按下限处理，避免间隔被算得更短。
        val broken = ThrottleState(ignoredStreak = -7)

        assertTrue(broken.ignoredStreak < 0)
        assertEquals(InterventionConfig().gapMinutes(0), InterventionConfig().gapMinutes(broken.ignoredStreak))
    }
}
