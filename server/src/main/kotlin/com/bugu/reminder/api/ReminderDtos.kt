package com.bugu.reminder.api

import kotlinx.serialization.Serializable

/**
 * 创建提醒请求(PRD FR-02 字段表)。fireAt 为 ISO 本地时刻(如 2026-10-27T09:00:00);
 * kind = ONCE / REPEAT / MUTUAL_ALARM;repeatRule 仅 REPEAT 必填(DAILY 或 WEEKLY:MON,WED)。
 */
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

@Serializable
data class BatchCreateReminderRequest(
    val receiverIds: List<Long>,
    val title: String,
    val kind: String,
    val fireAt: String,
    val repeatRule: String? = null,
    val tone: String = "GENTLE",
    val voiceEnabled: Boolean = false,
    val note: String? = null,
)

/** 修改提醒请求(S4):仅标题与 fireAt 可改,为 null 保持不变,至少一项。 */
@Serializable
data class UpdateReminderRequest(
    val title: String? = null,
    val fireAt: String? = null,
)

/** 接收人确认请求(S1):DONE / SNOOZE / DECLINE。 */
@Serializable
data class AckRequest(
    val fireId: Long,
    val action: String,
    val reasonCode: String? = null,
    val snoozeMinutes: Int? = null,
)

/**
 * 端侧触发上报请求(E7-S1):设备到点响铃后登记触发事实。
 * idempotencyKey 须与 fire 记录一致(创建时预生成,格式 reminderId:fireAt);
 * source = LOCAL_ALARM(本地闹钟响铃)/ PUSH(推送到达后响铃)。
 */
@Serializable
data class FireReportRequest(
    val idempotencyKey: String,
    val source: String = "LOCAL_ALARM",
)

/** 触发上报视图(E7-S1):回执给端侧;reportedAt 为空表示尚未上报成功。 */
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

/** 静音设置请求:接收人对某发起人静音/恢复某类提醒。 */
@Serializable
data class MuteRequest(
    val creatorId: Long,
    val kind: String,
)

/** 互叫闹钟状态互见视图(E5):仅「已起/未起」二元,不暴露任何中间状态。 */
@Serializable
data class MutualStatusView(
    val self: String,
    val peer: String,
)

/** 叫醒语视图(E5-S3):按 reminder+date 服务端缓存,degraded 标识是否降级模板。 */
@Serializable
data class WakeupCopyView(
    val date: String,
    val content: String,
    val degraded: Boolean,
)

/** 静音设置视图。 */
@Serializable
data class MuteSettingView(
    val creatorId: Long,
    val kind: String,
    val createdAt: String,
)

/**
 * 提醒视图:发起人 /mine 与接收人 /incoming 共用。
 * fireId = 当前查看者可操作的待响应(PENDING/FIRED)fire,供端侧 ack/report 定位;null = 无可操作 fire
 * (E8 S3 契约补充:客户端此前无法经 API 取得 fireId,ack/report 闭环断裂)。
 * ackId = 提醒已 ACKED 时最早一次 DONE 确认,供发起人撤销误触(E6-S2 / E8 S3 契约补充);其余状态 null。
 * bondId = null 表示自惦记(送给自己)。
 */
@Serializable
data class ReminderView(
    val id: Long,
    val bondId: Long?,
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
