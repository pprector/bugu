package com.bugu.feedback.app

import com.bugu.feedback.api.AdminFeedbackView
import com.bugu.feedback.api.CreateFeedbackRequest
import com.bugu.feedback.api.FeedbackPage
import com.bugu.feedback.api.FeedbackView
import com.bugu.feedback.api.UpdateFeedbackRequest
import com.bugu.feedback.infra.FeedbackRepository
import com.bugu.feedback.infra.FeedbackRow
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FeedbackService(private val repository: FeedbackRepository) {
    private val categories = setOf("REMINDER", "ACCOUNT", "SUGGESTION", "OTHER")
    private val statuses = setOf("OPEN", "PROCESSING", "RESOLVED")

    fun create(userId: Long, request: CreateFeedbackRequest): FeedbackView {
        require(request.category in categories) { "反馈类别非法" }
        val content = request.content.trim()
        require(content.length in 10..2000) { "反馈内容须为10至2000字" }
        require(request.appVersion == null || request.appVersion.length <= 64) { "应用版本不能超过64字" }
        return repository.create(userId, request.category, content, request.appVersion, LocalDateTime.now()).view()
    }

    fun mine(userId: Long): List<FeedbackView> = repository.mine(userId).map { it.view() }

    fun list(page: Int, size: Int, status: String?): FeedbackPage {
        require(page >= 1 && size in 1..100) { "分页参数非法，page须大于0，size须为1至100" }
        require(status == null || status in statuses) { "反馈状态非法" }
        return FeedbackPage(repository.list(page, size, status).map { it.adminView() }, repository.count(status), page, size)
    }

    fun update(id: Long, request: UpdateFeedbackRequest): AdminFeedbackView {
        require(request.status in statuses) { "反馈状态非法" }
        require(request.reply == null || request.reply.length <= 2000) { "回复不能超过2000字" }
        val reply = request.reply?.trim()
        require(request.status != "RESOLVED" || !reply.isNullOrEmpty()) { "解决反馈前必须填写回复" }
        return (repository.update(id, request.status, reply, LocalDateTime.now())
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)).adminView()
    }

    private fun FeedbackRow.view() = FeedbackView(
        id, category, content, appVersion, status, reply, createdAt.toString(), updatedAt.toString(),
    )

    private fun FeedbackRow.adminView() = AdminFeedbackView(
        id, category, content, appVersion, status, reply, createdAt.toString(), updatedAt.toString(), userId,
    )
}
