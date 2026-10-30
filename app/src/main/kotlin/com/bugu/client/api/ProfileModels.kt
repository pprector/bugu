package com.bugu.client.api

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(val nickname: String)

val feedbackCategories = linkedMapOf("REMINDER" to "提醒问题", "ACCOUNT" to "账号问题", "SUGGESTION" to "功能建议", "OTHER" to "其他")

@Serializable
data class CreateFeedbackRequest(val category: String, val content: String, val appVersion: String) {
    companion object {
        fun validated(category: String, content: String, appVersion: String): CreateFeedbackRequest {
            require(category in feedbackCategories) { "请选择反馈类别" }
            val trimmed = content.trim()
            require(trimmed.length in 10..2000) { "反馈内容须为10至2000字" }
            require(appVersion.length <= 64) { "应用版本不能超过64字" }
            return CreateFeedbackRequest(category, trimmed, appVersion)
        }
    }
}

@Serializable
data class FeedbackDto(
    val id: Long,
    val category: String,
    val content: String,
    val appVersion: String?,
    val status: String,
    val reply: String?,
    val createdAt: String,
    val updatedAt: String,
)

fun feedbackStatusLabel(status: String): String = when (status) {
    "OPEN" -> "待处理"
    "PROCESSING" -> "处理中"
    "RESOLVED" -> "已解决"
    else -> "未知状态（$status）"
}
