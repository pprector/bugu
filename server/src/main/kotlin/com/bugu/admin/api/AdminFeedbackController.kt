package com.bugu.admin.api

import com.bugu.feedback.api.AdminFeedbackView
import com.bugu.feedback.api.FeedbackPage
import com.bugu.feedback.api.UpdateFeedbackRequest
import com.bugu.feedback.app.FeedbackService
import com.bugu.shared.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/feedback")
class AdminFeedbackController(private val service: FeedbackService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?,
    ): ApiResponse<FeedbackPage> = ApiResponse.ok(service.list(page, size, status))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdateFeedbackRequest): ApiResponse<AdminFeedbackView> =
        ApiResponse.ok(service.update(id, request))
}
