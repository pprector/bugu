package com.bugu.admin.api

import com.bugu.admin.app.AdminAuditService
import com.bugu.admin.app.AdminOpsService
import com.bugu.admin.infra.AdminAuthFilter
import com.bugu.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 运营查询与处理(E9-S2):读 OPS/SUPER 均可,写仅 SUPER(AdminAuthFilter 已按 admin:write 拦截),
 * 写操作统一落审计。
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminOpsController(
    private val service: AdminOpsService,
    private val audit: AdminAuditService,
) {

    @GetMapping("/users")
    fun users(
        @RequestParam(required = false) query: String?,
    ): ApiResponse<List<AdminAppUserView>> = ApiResponse.ok(service.users(query))

    @PatchMapping("/users/{id}/elder-mode")
    fun setElderMode(
        request: HttpServletRequest,
        @PathVariable id: Long,
        @RequestBody body: AdminElderModeRequest,
    ): ApiResponse<AdminAppUserView> {
        val adminId = adminId(request)
        val view = service.setElderMode(id, body.enabled)
        audit.record(adminId, action = "USER_ELDER_MODE", targetType = "app_user", targetId = id, detail = "enabled=${body.enabled}")
        return ApiResponse.ok(view)
    }

    @GetMapping("/bonds")
    fun bonds(@RequestParam(required = false) query: String?): ApiResponse<List<AdminBondView>> =
        ApiResponse.ok(service.bonds(query))

    @PostMapping("/bonds/{id}/unbind")
    fun unbind(request: HttpServletRequest, @PathVariable id: Long): ApiResponse<Map<String, Boolean>> {
        val adminId = adminId(request)
        service.unbind(id)
        audit.record(adminId, action = "BOND_UNBIND", targetType = "bond", targetId = id)
        return ApiResponse.ok(mapOf("unbound" to true))
    }

    @GetMapping("/reminders")
    fun reminders(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) query: String?,
    ): ApiResponse<List<AdminReminderView>> = ApiResponse.ok(service.reminders(status, kind, query))

    @GetMapping("/reminders/{id}/escalations")
    fun escalations(@PathVariable id: Long): ApiResponse<List<AdminEscalationView>> =
        ApiResponse.ok(service.escalations(id))

    @GetMapping("/push/status")
    fun pushStatus(): ApiResponse<AdminPushStatusView> = ApiResponse.ok(service.pushStatus())

    @GetMapping("/audit-logs")
    fun auditLogs(@RequestParam(required = false) adminUserId: Long?): ApiResponse<List<AdminAuditLogView>> =
        ApiResponse.ok(service.auditLogs(adminUserId))

    private fun adminId(request: HttpServletRequest): Long =
        request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
}
