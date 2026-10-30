package com.bugu.admin.api

import com.bugu.admin.app.AdminAiService
import com.bugu.admin.app.AdminAuditService
import com.bugu.admin.infra.AdminAuthFilter
import com.bugu.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * AI 管理(E9-S3):模板库增改启停(写,SUPER)、生成日志与降级率(读)、LLM 状态(只读)。
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
class AdminAiController(
    private val service: AdminAiService,
    private val audit: AdminAuditService,
) {

    @GetMapping("/copy-templates")
    fun templates(
        @RequestParam(required = false) tone: String?,
        @RequestParam(required = false) scene: String?,
    ): ApiResponse<List<AdminTemplateView>> = ApiResponse.ok(service.templates(tone, scene))

    @PostMapping("/copy-templates")
    fun create(request: HttpServletRequest, @RequestBody body: TemplateCreateRequest): ApiResponse<AdminTemplateView> {
        val view = service.createTemplate(body.tone, body.scene, body.content)
        audit.record(adminId(request), action = "TEMPLATE_CREATE", targetType = "copy_template", targetId = view.id, detail = "${body.tone}/${body.scene}")
        return ApiResponse.ok(view)
    }

    @PutMapping("/copy-templates/{id}")
    fun update(
        request: HttpServletRequest,
        @PathVariable id: Long,
        @RequestBody body: TemplateUpdateRequest,
    ): ApiResponse<AdminTemplateView> {
        val view = service.updateTemplate(id, body.content, body.enabled)
        audit.record(adminId(request), action = "TEMPLATE_UPDATE", targetType = "copy_template", targetId = id, detail = "content=${body.content != null} enabled=${body.enabled}")
        return ApiResponse.ok(view)
    }

    @GetMapping("/gen-logs")
    fun genLogs(
        @RequestParam(required = false) scene: String?,
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
    ): ApiResponse<AdminGenLogsView> = ApiResponse.ok(service.genLogs(scene, page, size))

    @GetMapping("/status")
    fun status(): ApiResponse<AdminAiStatusView> = ApiResponse.ok(service.aiStatus())

    private fun adminId(request: HttpServletRequest): Long =
        request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
}
