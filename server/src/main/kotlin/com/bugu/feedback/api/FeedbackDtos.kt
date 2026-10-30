package com.bugu.feedback.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateFeedbackRequest(val category: String, val content: String, val appVersion: String? = null)

@Serializable
data class UpdateFeedbackRequest(val status: String, val reply: String? = null)

@Serializable
data class FeedbackView(
    val id: Long,
    val category: String,
    val content: String,
    val appVersion: String?,
    val status: String,
    val reply: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AdminFeedbackView(
    val id: Long,
    val category: String,
    val content: String,
    val appVersion: String?,
    val status: String,
    val reply: String?,
    val createdAt: String,
    val updatedAt: String,
    val userId: Long,
)

@Serializable
data class FeedbackPage(val items: List<AdminFeedbackView>, val total: Long, val page: Int, val size: Int)
