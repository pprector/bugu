package com.bugu.client.api

import kotlinx.serialization.Serializable

/** 创建提醒。fireAt 为 ISO 本地时刻;kind = ONCE / REPEAT / MUTUAL_ALARM。POST /api/v1/reminder */
@Serializable
data class CreateReminderRequest(
    val receiverId: Long,
    val title: String,
    val kind: String,
    val fireAt: String,
    val repeatRule: String? = null,
    val tone: String = "GENTLE",
    val voiceEnabled: Boolean = false,
    val note: String? = null,
)

/** 修改提醒:仅标题与 fireAt 可改。PATCH /api/v1/reminder/{id} */
@Serializable
data class UpdateReminderRequest(
    val title: String? = null,
    val fireAt: String? = null,
)

/** 接收人确认:DONE / SNOOZE / DECLINE(DECLINE 必带 reasonCode)。POST /api/v1/reminder/ack */
@Serializable
data class AckRequest(
    val fireId: Long,
    val action: String,
    val reasonCode: String? = null,
    val snoozeMinutes: Int? = null,
)

/** 端侧触发上报(E7-S1)。POST /api/v1/reminder/fire/{fireId}/report */
@Serializable
data class FireReportRequest(
    val idempotencyKey: String,
    val source: String = "LOCAL_ALARM",
)

/** 触发上报回执:reportedAt 为空表示尚未上报成功;409 表示已收口(端侧展示「已错过」)。 */
@Serializable
data class FireReportView(
    val fireId: Long,
    val reminderId: Long,
    val targetUserId: Long,
    val fireAt: String,
    val reportedAt: String? = null,
    val source: String? = null,
    val state: String,
)

/** 互叫状态互见:仅「已起/未起」二元。GET /api/v1/reminder/{id}/mutual-status */
@Serializable
data class MutualStatusView(
    val self: String,
    val peer: String,
)

/** 叫醒语:按 reminder+date 服务端缓存。GET /api/v1/reminder/{id}/wakeup-copy */
@Serializable
data class WakeupCopyView(
    val date: String,
    val content: String,
    val degraded: Boolean,
)

/** 静音设置。PUT/DELETE /api/v1/reminder/mute */
@Serializable
data class MuteRequest(
    val creatorId: Long,
    val kind: String,
)

@Serializable
data class MuteSettingView(
    val creatorId: Long,
    val kind: String,
    val createdAt: String,
)

/** 一句话解析。POST /api/v1/reminder/parse */
@Serializable
data class ParseRequest(
    val text: String,
)

/** 解析草稿:needsManual=true 回退手动表单;degraded=true 为降级桩结果;note 为解析出的便签。 */
@Serializable
data class ParseDraft(
    val title: String? = null,
    val fireAt: String? = null,
    val kind: String,
    val repeatRule: String? = null,
    val note: String? = null,
    val needsManual: Boolean,
    val degraded: Boolean = true,
)

/**
 * 提醒视图:server reminder/api/ReminderView 同形,双方列表(mine/incoming)与创建/确认共用。
 * silent=true 表示超每日上限静默落库,端侧到点不弹强提醒;
 * fireId = 当前查看者可操作的待响应 fire(E8 S3 契约),null = 无可操作 fire;
 * ackId = 已 ACKED 时最早一次 DONE 确认,发起人撤销误触用,null = 无。
 */
@Serializable
data class ReminderView(
    val id: Long,
    val bondId: Long,
    val creatorId: Long,
    val receiverId: Long,
    val title: String,
    val kind: String,
    val tone: String,
    val fireAt: String,
    val repeatRule: String? = null,
    val voiceEnabled: Boolean,
    val note: String? = null,
    val status: String,
    val silent: Boolean,
    val createdAt: String,
    val fireId: Long? = null,
    val ackId: Long? = null,
)
