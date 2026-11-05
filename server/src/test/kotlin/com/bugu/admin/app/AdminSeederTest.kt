package com.bugu.admin.app

import com.bugu.admin.infra.AdminPasswordHasher
import com.bugu.admin.infra.AdminUserRepository
import com.bugu.admin.infra.AdminUserRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.ApplicationArguments

/** AdminSeeder:无 SUPER 时按配置创建种子账号(口令可校验),已有 SUPER 时跳过(幂等)。 */
class AdminSeederTest {

    private val repository = mock(AdminUserRepository::class.java)
    private val args = mock(ApplicationArguments::class.java)

    @Test
    fun `无SUPER时按配置口令创建种子账号`() {
        `when`(repository.existsByRole(AdminRoles.SUPER)).thenReturn(false)
        `when`(repository.save(any(AdminUserRow::class.java))).thenAnswer {
            it.getArgument<AdminUserRow>(0).copy(id = 1)
        }

        AdminSeeder(repository, AdminSeedProperties(seedPassword = "seed-pass-1234")).run(args)

        val captor = org.mockito.ArgumentCaptor.forClass(AdminUserRow::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertEquals("super", saved.username)
        assertEquals(AdminRoles.SUPER, saved.role)
        assertEquals("超级管理员", saved.nickname)
        assertTrue(saved.enabled)
        assertTrue(AdminPasswordHasher.verify("seed-pass-1234", saved.passwordHash))
        assertFalse(AdminPasswordHasher.verify("wrong-password", saved.passwordHash))
    }

    @Test
    fun `未配置口令时随机生成_不再回落到仓库默认值`() {
        `when`(repository.existsByRole(AdminRoles.SUPER)).thenReturn(false)
        `when`(repository.save(any(AdminUserRow::class.java))).thenAnswer {
            it.getArgument<AdminUserRow>(0).copy(id = 2)
        }

        AdminSeeder(repository, AdminSeedProperties()).run(args)

        val captor = org.mockito.ArgumentCaptor.forClass(AdminUserRow::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertFalse(saved.passwordHash.isBlank())
        // 旧默认口令不得再作为兜底真值
        assertFalse(AdminPasswordHasher.verify("bugu-admin-2026", saved.passwordHash))
    }

    @Test
    fun `已有SUPER时跳过`() {
        `when`(repository.existsByRole(AdminRoles.SUPER)).thenReturn(true)

        AdminSeeder(repository, AdminSeedProperties()).run(args)

        verify(repository, never()).save(any(AdminUserRow::class.java))
    }
}
