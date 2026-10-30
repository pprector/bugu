package com.bugu.bond.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDateTime

class BindCodePolicyTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 26, 12, 0, 0)

    @Test
    fun `有效期内未过期`() {
        val expiresAt = t0.plus(BindCodePolicy.CODE_TTL)
        assertFalse(BindCodePolicy.isExpired(expiresAt, t0.plusMinutes(9)))
    }

    @Test
    fun `到期时刻即过期`() {
        val expiresAt = t0.plus(BindCodePolicy.CODE_TTL)
        assertTrue(BindCodePolicy.isExpired(expiresAt, expiresAt))
    }

    @Test
    fun `过期之后判定为过期`() {
        val expiresAt = t0.plus(BindCodePolicy.CODE_TTL)
        assertTrue(BindCodePolicy.isExpired(expiresAt, expiresAt.plusSeconds(1)))
    }
}
