package com.bugu.identity.infra

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JwtTokenServiceTest {

    private val secret = "unit-test-secret-key-0123456789abcdef00"

    private fun service(expireDays: Long = 7) =
        JwtTokenService(JwtProperties(secret = secret, expireDays = expireDays))

    @Test
    fun `签发后可解析出 userId 与 phone`() {
        val token = service().issue(42L, "13812345678")
        assertEquals(42L, service().parseUserId(token))
    }

    @Test
    fun `phone 为空的长辈token同样可解析`() {
        val token = service().issue(9L, null)
        assertEquals(9L, service().parseUserId(token))
    }

    @Test
    fun `篡改令牌被拒绝`() {
        val token = service().issue(1L, null)
        val tampered = token.dropLast(2) + "xx"
        assertThrows<JwtException> { service().parseUserId(tampered) }
    }

    @Test
    fun `过期令牌被拒绝`() {
        val token = service(expireDays = -1).issue(1L, null)
        assertThrows<ExpiredJwtException> { service().parseUserId(token) }
    }

    @Test
    fun `不同密钥签发的令牌被拒绝`() {
        val other = JwtTokenService(JwtProperties(secret = "another-secret-key-0123456789abcdefghij", expireDays = 7))
        val token = service().issue(1L, null)
        assertThrows<JwtException> { other.parseUserId(token) }
        assertTrue(token.length > 20)
    }
}
