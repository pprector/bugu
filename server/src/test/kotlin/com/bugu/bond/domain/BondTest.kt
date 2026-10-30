package com.bugu.bond.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import java.time.LocalDateTime

class BondTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 26, 12, 0, 0)

    private fun pendingBond(expiresAt: LocalDateTime = t0.plus(BindCodePolicy.CODE_TTL)): Bond =
        Bond(
            id = 1L,
            requesterId = 100L,
            receiverId = null,
            status = BondStatus.PENDING_OUTBOUND,
            bindCode = "123456",
            codeExpiresAt = expiresAt,
            confirmedAt = null,
            unboundAt = null,
            relationTag = null,
            createdAt = t0,
        )

    @Test
    fun `确认成功回填接收人并进入ACTIVE`() {
        val bond = pendingBond()
        bond.confirm(200L, t0.plusMinutes(1))
        assertEquals(BondStatus.ACTIVE, bond.status)
        assertEquals(200L, bond.receiverId)
        assertEquals(t0.plusMinutes(1), bond.confirmedAt)
        assertNull(bond.bindCode)
        assertNull(bond.codeExpiresAt)
    }

    @Test
    fun `自绑被聚合拒绝`() {
        val bond = pendingBond()
        assertFailsWith<IllegalStateException> { bond.confirm(100L, t0.plusMinutes(1)) }
    }

    @Test
    fun `过期绑定码被聚合拒绝`() {
        val bond = pendingBond(expiresAt = t0.plusMinutes(10))
        assertFailsWith<IllegalStateException> { bond.confirm(200L, t0.plusMinutes(10)) }
    }

    @Test
    fun `重复确认被拒绝`() {
        val bond = pendingBond()
        bond.confirm(200L, t0.plusMinutes(1))
        assertFailsWith<IllegalStateException> { bond.confirm(300L, t0.plusMinutes(2)) }
    }

    @Test
    fun `ACTIVE状态不可续发绑定码`() {
        val bond = pendingBond()
        bond.confirm(200L, t0.plusMinutes(1))
        assertFailsWith<IllegalStateException> { bond.reissueBindCode("654321", t0.plusMinutes(20)) }
    }

    @Test
    fun `续发绑定码更新码与过期时刻`() {
        val bond = pendingBond()
        bond.reissueBindCode("654321", t0.plusMinutes(30))
        assertEquals("654321", bond.bindCode)
        assertEquals(t0.plusMinutes(30), bond.codeExpiresAt)
        assertEquals(BondStatus.PENDING_OUTBOUND, bond.status)
    }

    private fun activeBond(): Bond = pendingBond().apply { confirm(200L, t0.plusMinutes(1)) }

    @Test
    fun `发起方解绑进入UNBOUND并记录时刻`() {
        val bond = activeBond()
        bond.unbind(100L, t0.plusMinutes(5))
        assertEquals(BondStatus.UNBOUND, bond.status)
        assertEquals(t0.plusMinutes(5), bond.unboundAt)
    }

    @Test
    fun `接收方同样可解绑`() {
        val bond = activeBond()
        bond.unbind(200L, t0.plusMinutes(5))
        assertEquals(BondStatus.UNBOUND, bond.status)
    }

    @Test
    fun `非参与者解绑被拒绝`() {
        val bond = activeBond()
        assertFailsWith<IllegalStateException> { bond.unbind(300L, t0.plusMinutes(5)) }
    }

    @Test
    fun `PENDING状态不可解绑`() {
        val bond = pendingBond()
        assertFailsWith<IllegalStateException> { bond.unbind(100L, t0.plusMinutes(5)) }
    }

    @Test
    fun `UNBOUND为终态不可逆`() {
        val bond = activeBond()
        bond.unbind(100L, t0.plusMinutes(5))
        assertFailsWith<IllegalStateException> { bond.unbind(200L, t0.plusMinutes(6)) }
        assertFailsWith<IllegalStateException> { bond.confirm(300L, t0.plusMinutes(6)) }
        assertFailsWith<IllegalStateException> { bond.reissueBindCode("654321", t0.plusMinutes(6)) }
    }
}
