package com.bugu.admin.api

import kotlinx.serialization.Serializable

/** 模板视图。 */
@Serializable
data class AdminTemplateView(
    val id: Long,
    val tone: String,
    val scene: String,
    val content: String,
    val enabled: Boolean,
    val createdAt: String,
)

/** 模板创建请求。 */
@Serializable
data class TemplateCreateRequest(
    val tone: String,
    val scene: String,
    val content: String,
)

/** 模板更新请求:content/enabled 至少一项。 */
@Serializable
data class TemplateUpdateRequest(
    val content: String? = null,
    val enabled: Boolean? = null,
)

/** 生成日志视图。 */
@Serializable
data class AdminGenLogView(
    val id: Long,
    val scene: String,
    val tone: String? = null,
    val degraded: Boolean,
    val latencyMs: Int? = null,
    val createdAt: String,
)

/** 按 scene 汇总的降级率。 */
@Serializable
data class AdminGenLogSummary(
    val scene: String,
    val total: Long,
    val degraded: Long,
    /** 0~1 */
    val degradeRate: Double,
)

@Serializable
data class AdminGenLogsView(
    val summary: List<AdminGenLogSummary> = emptyList(),
    val logs: List<AdminGenLogView> = emptyList(),
)

/** AI 状态(只读,运行时切换经配置重启)。 */
@Serializable
data class AdminAiStatusView(
    val llmProvider: String,
    val gatewayRegistered: Boolean,
    val note: String,
)
