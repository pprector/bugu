package com.bugu.admin.app

import com.bugu.admin.infra.AdminPasswordHasher
import com.bugu.admin.infra.AdminUserRepository
import com.bugu.admin.infra.AdminUserRow
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 后台账号管理服务:创建(查重 + PBKDF2 哈希)与启停。
 * 预查重只是友好提示,并发窗口由 uk_admin_username 唯一索引兜底,
 * DuplicateKeyException 同样转 CONFLICT(与 bindPhone 同款处理)。
 */
@Service
class AdminAccountService(private val repository: AdminUserRepository) {

    fun list(): List<AdminUserRow> =
        repository.findAll().sortedBy { requireNotNull(it.id) { "admin id 不应为空" } }

    fun create(username: String, password: String, nickname: String, role: String): AdminUserRow {
        if (username.length < 3 || username.length > 32) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "用户名需为 3-32 个字符")
        }
        if (password.length < 8) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "密码至少 8 位")
        }
        if (nickname.isBlank() || nickname.length > 32) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "昵称需为 1-32 个字符")
        }
        if (role != AdminRoles.SUPER && role != AdminRoles.OPS) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "角色仅支持 SUPER 或 OPS")
        }
        if (repository.findByUsername(username) != null) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "用户名已存在")
        }
        return try {
            repository.save(
                AdminUserRow(
                    username = username,
                    passwordHash = AdminPasswordHasher.hash(password),
                    nickname = nickname,
                    role = role,
                    enabled = true,
                    createdAt = LocalDateTime.now(),
                ),
            )
        } catch (e: DuplicateKeyException) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "用户名已存在")
        }
    }

    /** 启停后台账号:不能停用自己——避免最后一个 SUPER 自锁后无人可恢复。 */
    fun setEnabled(operatorId: Long, id: Long, enabled: Boolean): AdminUserRow {
        if (!enabled && operatorId == id) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "不能停用自己")
        }
        val row = repository.findByIdOrNull(id)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "后台账号不存在")
        return repository.save(row.copy(enabled = enabled))
    }
}
