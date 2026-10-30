package com.bugu.admin.app

import com.bugu.admin.infra.AdminAuditLogRepository
import com.bugu.admin.infra.AdminAuditLogRow
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 后台操作审计(E9):全部写操作与登录统一落 admin_audit_log。 */
@Service
class AdminAuditService(private val repository: AdminAuditLogRepository) {

    fun record(adminUserId: Long, action: String, targetType: String? = null, targetId: Long? = null, detail: String? = null) {
        repository.save(
            AdminAuditLogRow(
                adminUserId = adminUserId,
                action = action,
                targetType = targetType,
                targetId = targetId?.toString(),
                detail = detail?.take(500),
                createdAt = LocalDateTime.now(),
            ),
        )
    }
}
