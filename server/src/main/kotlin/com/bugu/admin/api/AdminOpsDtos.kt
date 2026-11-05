package com.bugu.admin.api

import kotlinx.serialization.Serializable

/** 后台用户视图(E9)。 */
@Serializable
data class AdminAppUserView(
    val id: Long,
    val nickname: String,
    val phone: String? = null,
    val accountType: String,
    val elderMode: Boolean,
)

/** 长辈模式标记请求(admin 直设)。 */
@Serializable
data class AdminElderModeRequest(
    val enabled: Boolean,
)

/** 后台绑定视图:带双方昵称/手机号。 */
@Serializable
data class AdminBondView(
    val id: Long,
    val status: String,
    val requesterId: Long,
    val requesterName: String,
    val receiverId: Long? = null,
    val receiverName: String? = null,
    val createdAt: String,
)

/** 后台提醒视图:带发起/接收双方与状态。 */
@Serializable
data class AdminReminderView(
    val id: Long,
    val title: String,
    val kind: String,
    val status: String,
    val fireAt: String,
    val creatorId: Long,
    val creatorName: String,
    val receiverId: Long,
    val receiverName: String,
    val createdAt: String,
)

/** 升级链日志视图:一条 fire 及其 T1/T2/CLOSE 动作。 */
@Serializable
data class AdminEscalationView(
    val fireId: Long,
    val fireAt: String,
    val state: String,
    val actions: List<AdminEscalationAction> = emptyList(),
)

@Serializable
data class AdminEscalationAction(
    val stage: String,
    val action: String,
    val createdAt: String,
)

/** 推送通道状态(只读)。 */
@Serializable
data class AdminPushStatusView(
    val channel: String,
    val description: String,
)

/** 后台列表分页响应:与反馈管理同口径(page 从 1 起,size 1-100)。 */
@Serializable
data class AdminUserPage(
    val items: List<AdminAppUserView>,
    val total: Long,
    val page: Int,
    val size: Int,
)

@Serializable
data class AdminBondPage(
    val items: List<AdminBondView>,
    val total: Long,
    val page: Int,
    val size: Int,
)

@Serializable
data class AdminReminderPage(
    val items: List<AdminReminderView>,
    val total: Long,
    val page: Int,
    val size: Int,
)

@Serializable
data class AdminAuditLogPage(
    val items: List<AdminAuditLogView>,
    val total: Long,
    val page: Int,
    val size: Int,
)

/** 后台操作审计视图(E9)。 */
@Serializable
data class AdminAuditLogView(
    val id: Long,
    val adminUserId: Long,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val detail: String? = null,
    val createdAt: String,
)
