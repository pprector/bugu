package com.bugu.bond.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * ConfirmThrottlePolicy 纯函数单测:窗口计数、达上限锁定、锁到期放行、窗口过期重新开窗。
 */
class ConfirmThrottlePolicyTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 26, 12, 0, 0)

    @Test
    fun `未锁定时放行`() {
        assertEquals(
            ConfirmThrottlePolicy.Decision.Allowed,
            ConfirmThrottlePolicy.evaluateBeforeConfirm(null, t0),
        )
    }

    @Test
    fun `锁未到期拒绝_锁到期放行`() {
        val lockedUntil = t0.plusMinutes(30)
        val denied = ConfirmThrottlePolicy.evaluateBeforeConfirm(lockedUntil, t0.plusMinutes(10))
        assertTrue(denied is ConfirmThrottlePolicy.Decision.Denied)
        assertEquals(lockedUntil, (denied as ConfirmThrottlePolicy.Decision.Denied).lockedUntil)

        assertEquals(
            ConfirmThrottlePolicy.Decision.Allowed,
            ConfirmThrottlePolicy.evaluateBeforeConfirm(lockedUntil, t0.plusMinutes(30)),
        )
    }

    @Test
    fun `窗口内失败累加_达上限写入锁`() {
        var count = 0
        var windowStart: LocalDateTime? = null
        var lock: LocalDateTime? = null
        repeat(ConfirmThrottlePolicy.MAX_FAILURES_PER_WINDOW - 1) {
            val (c, w, l) = ConfirmThrottlePolicy.onFailure(count, windowStart, t0)
            count = c; windowStart = w; lock = l
            assertNull(lock)
        }
        assertEquals(ConfirmThrottlePolicy.MAX_FAILURES_PER_WINDOW - 1, count)

        val (c, _, l) = ConfirmThrottlePolicy.onFailure(count, windowStart, t0)
        assertEquals(ConfirmThrottlePolicy.MAX_FAILURES_PER_WINDOW, c)
        assertEquals(t0.plus(ConfirmThrottlePolicy.LOCK_DURATION), l)
    }

    @Test
    fun `窗口过期重新开窗_计数从1开始`() {
        val oldStart = t0
        val now = t0.plus(ConfirmThrottlePolicy.WINDOW).plusSeconds(1)
        val (count, windowStart, lock) = ConfirmThrottlePolicy.onFailure(4, oldStart, now)
        assertEquals(1, count)
        assertEquals(now, windowStart)
        assertNull(lock)
    }
}
