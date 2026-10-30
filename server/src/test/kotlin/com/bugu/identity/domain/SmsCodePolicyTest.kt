package com.bugu.identity.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Duration
import java.time.LocalDateTime

class SmsCodePolicyTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 26, 10, 0, 0)

    @Test
    fun `首次发码放行且计数为1`() {
        val decision = SmsCodePolicy.evaluateSend(0, null, null, t0)
        assertTrue(decision is SmsCodePolicy.SendDecision.Allowed)
        assertEquals(1, (decision as SmsCodePolicy.SendDecision.Allowed).newCount)
        assertEquals(t0, decision.windowStart)
    }

    @Test
    fun `窗口内第5次放行第6次拒绝并锁15分钟`() {
        var decision = SmsCodePolicy.SendDecision.Allowed(1, t0) as SmsCodePolicy.SendDecision
        for (i in 2..5) {
            decision = SmsCodePolicy.evaluateSend(
                (decision as SmsCodePolicy.SendDecision.Allowed).newCount,
                (decision as SmsCodePolicy.SendDecision.Allowed).windowStart,
                null,
                t0.plusSeconds(5L * i),
            )
            assertTrue(decision is SmsCodePolicy.SendDecision.Allowed, "第 $i 次应放行")
        }
        val denied = SmsCodePolicy.evaluateSend(5, t0, null, t0.plusSeconds(30))
        assertTrue(denied is SmsCodePolicy.SendDecision.Denied)
        assertEquals(t0.plusSeconds(30).plus(SmsCodePolicy.LOCK_DURATION), (denied as SmsCodePolicy.SendDecision.Denied).lockedUntil)
    }

    @Test
    fun `锁未到期时持续拒绝`() {
        val lockedUntil = t0.plus(SmsCodePolicy.LOCK_DURATION)
        val decision = SmsCodePolicy.evaluateSend(5, t0, lockedUntil, lockedUntil.minusSeconds(1))
        assertTrue(decision is SmsCodePolicy.SendDecision.Denied)
    }

    @Test
    fun `锁到期且窗口已过则重新计数`() {
        val lockedUntil = t0.plus(SmsCodePolicy.LOCK_DURATION)
        val after = lockedUntil.plusMinutes(1)
        val decision = SmsCodePolicy.evaluateSend(5, t0, lockedUntil, after)
        assertTrue(decision is SmsCodePolicy.SendDecision.Allowed)
        assertEquals(1, decision.newCount)
    }

    @Test
    fun `窗口过期后重新开窗`() {
        val decision = SmsCodePolicy.evaluateSend(5, t0, null, t0.plus(SmsCodePolicy.SEND_WINDOW).plusSeconds(1))
        assertTrue(decision is SmsCodePolicy.SendDecision.Allowed)
        assertEquals(1, decision.newCount)
    }

    @Test
    fun `验证码哈希稳定且验证通过`() {
        val code = "012345"
        val hash = SmsCodePolicy.hash(code)
        assertEquals(hash, SmsCodePolicy.hash(code))
        assertTrue(SmsCodePolicy.isCodeValid(hash, t0, code, t0.plusSeconds(10)))
    }

    @Test
    fun `验证码过期后无效`() {
        val hash = SmsCodePolicy.hash("012345")
        val atTtl = t0.plus(SmsCodePolicy.CODE_TTL)
        assertFalse(SmsCodePolicy.isCodeValid(hash, t0, "012345", atTtl))
        assertFalse(SmsCodePolicy.isCodeValid(hash, t0.minus(SmsCodePolicy.CODE_TTL), "012345", t0))
    }

    @Test
    fun `错误验证码无效`() {
        val hash = SmsCodePolicy.hash("012345")
        assertFalse(SmsCodePolicy.isCodeValid(hash, t0, "543210", t0.plusSeconds(1)))
    }
}
