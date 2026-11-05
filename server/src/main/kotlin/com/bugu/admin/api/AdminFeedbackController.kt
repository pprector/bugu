package com.bugu.admin.api

import com.bugu.admin.app.AdminAuditService
import com.bugu.admin.infra.AdminAuthFilter
import com.bugu.feedback.api.AdminFeedbackView
import com.bugu.feedback.api.FeedbackPage
import com.bugu.feedback.api.UpdateFeedbackRequest
import com.bugu.feedback.app.FeedbackService
import com.bugu.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/feedback")
class AdminFeedbackController(
    private val service: FeedbackService,
    private val audit: AdminAuditService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?,
    ): ApiResponse<FeedbackPage> = ApiResponse.ok(service.list(page, size, status))

    @PatchMapping("/{id}")
    fun update(
        request: HttpServletRequest,
        @PathVariable id: Long,
        @RequestBody body: UpdateFeedbackRequest,
    ): ApiResponse<AdminFeedbackView> {
        val adminId = request.getAttribute(AdminAuthFilter.ATTR_ADMIN_ID) as? Long
            ?: throw IllegalStateException("AdminAuthFilter 未注入 adminId")
        val view = service.update(id, body)
        audit.record(
            adminId,
            action = "FEEDBACK_UPDATE",
            targetType = "feedback",
            targetId = id,
            detail = "status=${view.status} replied=${!view.reply.isNullOrBlank()}",
        )
        return ApiResponse.ok(view)
    }
}
