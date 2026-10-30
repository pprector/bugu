package com.bugu.feedback.api

import com.bugu.feedback.app.FeedbackService
import com.bugu.identity.api.CurrentUser
import com.bugu.shared.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/feedback")
class FeedbackController(private val service: FeedbackService) {
    @PostMapping
    fun create(currentUser: CurrentUser, @RequestBody request: CreateFeedbackRequest): ApiResponse<FeedbackView> =
        ApiResponse.ok(service.create(currentUser.id, request))

    @GetMapping
    fun mine(currentUser: CurrentUser): ApiResponse<List<FeedbackView>> = ApiResponse.ok(service.mine(currentUser.id))
}
