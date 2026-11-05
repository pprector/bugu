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

    companion object {
        /** 与前端 admin/src/constants/reg.ts 的 REG_USER_NAME 保持同一口径。 */
        val USERNAME_PATTERN = Regex("^[\\u4E00-\\u9FA5A-Za-z0-9_-]{3,32}$")
    }

    /** 内存分页切片:page 从 1 起,size 1-100,越界页返回空列表(与反馈管理同口径)。 */
    fun list(page: Int, size: Int): Pair<List<AdminUserRow>, Long> {
        require(page >= 1 && size in 1..100) { "分页参数非法，page须大于0，size须为1至100" }
        val all = repository.findAll().sortedBy { requireNotNull(it.id) { "admin id 不应为空" } }
        val total = all.size.toLong()
        val from = (page - 1) * size
        val items = if (from >= all.size) emptyList() else all.subList(from, minOf(from + size, all.size))
        return items to total
    }

    fun create(username: String, password: String, nickname: String, role: String): AdminUserRow {
        if (!USERNAME_PATTERN.matches(username)) {
            throw BizException(
                ErrorCode.INVALID_ARGUMENT,
                HttpStatus.BAD_REQUEST,
                "用户名需为 3-32 位中文、字母、数字、下划线或短横线",
            )
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

    /** 启停后台账号:不能停用自己——避免最后一个 SUPER 自锁后无人可恢复;停用同时吊销该账号全部 token。 */
    fun setEnabled(operatorId: Long, id: Long, enabled: Boolean): AdminUserRow {
        if (!enabled && operatorId == id) {
            throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "不能停用自己")
        }
        val row = repository.findByIdOrNull(id)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "后台账号不存在")
        val revokedVersion = if (row.enabled && !enabled) row.tokenVersion + 1 else row.tokenVersion
        return repository.save(row.copy(enabled = enabled, tokenVersion = revokedVersion))
    }
}
