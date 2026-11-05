package com.bugu.push.app

import com.bugu.ai.app.AiCopyService
import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.DayPart
import com.bugu.ai.domain.ResponseHabit
import com.bugu.ai.infra.ResponseStatsQuery
import com.bugu.identity.infra.DeviceTokenRepository
import com.bugu.identity.infra.UserRepository
import com.bugu.push.Platform
import com.bugu.push.PushGateway
import com.bugu.push.PushMessage
import com.bugu.push.PushResult
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.EscalationLogRow
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * 推送应用服务:文案经 AiCopyService(AI 生成或模板降级)→ token → PushGateway → escalation_log 记录到达回执。
 * 无文案(模板库也缺)/无设备令牌时返回 Skipped 并落库,不伪造成功。
 */
@Service
class PushService(
    private val aiCopyService: AiCopyService,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushGateway: PushGateway,
    private val escalationLogRepository: EscalationLogRepository,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
    private val responseStatsQuery: ResponseStatsQuery,
    private val clock: Clock,
    private val bondRepository: com.bugu.bond.infra.BondRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 向指定用户推送一条升级链消息并记录 escalation_log。
     * @param fireId 触发记录 id
     * @param stage T1 / T2 / CLOSE
     * @param action REPUSH_RECEIVER / NOTIFY_CREATOR / MARK_MISSED
     * @param userId 推送目标用户
     * @param tone 语气档 GENTLE/NAG/STRICT,用于生成或选模板
     * @param scene 文案场景 CREATE/REPUSH/WAKEUP
     */
    @org.springframework.transaction.annotation.Transactional
    fun push(fireId: Long, stage: String, action: String, userId: Long, tone: String, scene: String): PushResult {
        val content = composeContent(fireId, stage, tone, scene, null)
        if (content == null) {
            log.warn("no copy for fire={} tone={} scene={}, push skipped", fireId, tone, scene)
            return record(fireId, stage, action, PushResult.Skipped)
        }
        val result = sendToUser(userId, content, content, fireId, stage)
        return record(fireId, stage, action, result)
    }

    /**
     * 创建通知推送(scene=CREATE):不属于升级链,不落 escalation_log
     * (升级链日志有外键指向 fire,创建通知若落库会阻塞 update 删除重建 PENDING fire);
     * 文案生成与降级观测由 ai_gen_log(scene=COPY)承担,推送结果记日志。
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    fun pushCreated(fireId: Long, receiverId: Long, title: String, tone: String): PushResult {
        val content = composeContent(fireId, "T0", tone, "CREATE", title)
        if (content == null) {
            log.warn("no copy for create push fire={}, skipped", fireId)
            return PushResult.Skipped
        }
        return sendToUser(receiverId, content, content, fireId, "T0")
    }

    /**
     * 误触撤销通知(E6-S2):子女端撤销后通知双方——长辈端告知已被撤销、子女端确认。
     * 文案固定(无需 AI 生成),不落 escalation_log。
     */
    fun pushRevoked(fireId: Long, creatorId: Long, receiverId: Long, title: String) {
        val elderMsg = "「$title」的完成已被家人撤销,请重新确认"
        val creatorMsg = "已撤销「$title」的误触完成"
        runCatching { sendToUser(receiverId, elderMsg, elderMsg, fireId, "REVOKE") }
            .onFailure { log.error("撤销通知长辈失败 fire={}", fireId, it) }
        runCatching { sendToUser(creatorId, creatorMsg, creatorMsg, fireId, "REVOKE") }
            .onFailure { log.error("撤销通知子女失败 fire={}", fireId, it) }
    }

    /**
     * fireAt 到点推送(FIRE_AT):只发送,不写 escalation_log。
     * 幂等 claim 由 [com.bugu.scheduling.FireAtPushScanner] 先插入 (fireId, FIRE_AT) 唯一行完成,
     * 发送结果再回填同一行;因此本方法不得在未 claim 的情况下直接调用。
     * 标题/正文都包含发起人昵称与提醒标题,extras 附 deeplink 供端侧点击直达响铃页。
     */
    @org.springframework.transaction.annotation.Transactional
    fun sendFireAt(fireId: Long): PushResult {
        val fire = reminderFireRepository.findByIdOrNull(fireId) ?: return PushResult.Skipped
        val reminder = reminderRepository.findByIdOrNull(fire.reminderId) ?: return PushResult.Skipped
        // 端侧已上报/已取消/静默落库的 fire 不再推,避免和本地闹钟重复打扰
        if (fire.state !in setOf("PENDING", "FIRED") || fire.reportedAt != null) return PushResult.Skipped
        if (reminder.status == "CANCELED" || reminder.silent) return PushResult.Skipped
        if (reminder.bondId != null && bondRepository.lockById(reminder.bondId)?.status != "ACTIVE") return PushResult.Skipped

        val creatorName = userRepository.findByIdOrNull(reminder.creatorId)?.nickname
            ?.takeIf { it.isNotBlank() } ?: "家人"
        val copy = composeContent(fireId, "FIRE_AT", reminder.tone, "REPUSH", reminder.title)
            ?: reminder.title
        val title = if (reminder.creatorId == reminder.receiverId) {
            "到点啦：${reminder.title}（你自己的提醒）"
        } else {
            "来自$creatorName 的提醒：${reminder.title}"
        }
        // 极光通知栏标题固定为应用名,正文必须自带提醒人与内容,用户不打开 App 也能看懂
        val body = if (reminder.creatorId == reminder.receiverId) {
            "$copy｜${reminder.title}（你自己的提醒）"
        } else {
            "$copy｜${reminder.title}（来自$creatorName）"
        }
        return sendToUser(
            userId = fire.targetUserId,
            title = title,
            body = body,
            fireId = fireId,
            stage = "FIRE_AT",
            extraExtras = mapOf(
                "reminderId" to fire.reminderId.toString(),
                "deeplink" to "bugu://ring?fireId=$fireId&reminderId=${fire.reminderId}",
                // 端侧点击通知可直接构造 RingActivity 入参,无需再查一次列表
                "title" to reminder.title,
                "fireAt" to fire.fireAt.toString(),
                "idempotencyKey" to fire.idempotencyKey,
                "kind" to reminder.kind,
                // 与端侧 AlarmScheduler.EXTRA_VOICE / EXTRA_SELF 同名,便于直接构造 Intent
                "voice" to reminder.voiceEnabled.toString(),
                "selfReminder" to (reminder.creatorId == reminder.receiverId).toString(),
            ),
            notificationTitle = title,
        )
    }

    /** 文案生成:要素由 fire→reminder 装配(脱敏,无 note/手机号);AI 不可用时 compose 内部降级模板。 */
    private fun composeContent(fireId: Long, stage: String, tone: String, scene: String, titleOverride: String?): String? {
        val fire = reminderFireRepository.findByIdOrNull(fireId) ?: return null
        val reminder = reminderRepository.findByIdOrNull(fire.reminderId) ?: return null
        val delays = responseStatsQuery.recentDelaysSeconds(reminder.receiverId)
        val context = CopyContext(
            title = titleOverride ?: reminder.title,
            scene = scene,
            tone = tone,
            attempt = if (stage == "T1") 2 else 1,
            responseHabit = ResponseHabit.fromAverageDelay(
                delays.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            ),
            dayPart = DayPart.ofHour(LocalDateTime.now(clock).hour),
        )
        return aiCopyService.compose(scene, tone, context)
    }

    /** 逐令牌发送,任一通道 Accepted 即止;无令牌返回 Skipped,不伪造成功。 */
    private fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        fireId: Long,
        stage: String,
        extraExtras: Map<String, String> = emptyMap(),
        notificationTitle: String? = null,
    ): PushResult {
        val fire = reminderFireRepository.findByIdOrNull(fireId) ?: return PushResult.Skipped
        val reminder = reminderRepository.findByIdOrNull(fire.reminderId) ?: return PushResult.Skipped
        if (reminder.bondId != null && bondRepository.lockById(reminder.bondId)?.status != "ACTIVE") return PushResult.Skipped
        if (reminder.status == "CANCELED" || fire.state == "CLOSED") return PushResult.Skipped
        val tokens = deviceTokenRepository.findByUserIdAndPushEnabledTrue(userId)
        if (tokens.isEmpty()) {
            log.warn("no enabled device token for user={}, push skipped", userId)
            return PushResult.Skipped
        }
        var last: PushResult = PushResult.Skipped
        for (token in tokens) {
            val platform = runCatching { Platform.valueOf(token.platform) }.getOrNull() ?: continue
            val message = PushMessage(
                token = token.pushToken,
                platform = platform,
                title = title,
                body = body,
                extras = mapOf("fireId" to fireId.toString(), "stage" to stage) + extraExtras,
                notificationTitle = notificationTitle,
            )
            last = pushGateway.send(message)
            if (last is PushResult.Accepted) break
        }
        return last
    }

    private fun record(fireId: Long, stage: String, action: String, result: PushResult): PushResult {
        escalationLogRepository.save(
            EscalationLogRow(
                fireId = fireId,
                stage = stage,
                action = action,
                pushResult = result.toCode(),
                createdAt = LocalDateTime.now(clock),
            ),
        )
        return result
    }

    private fun PushResult.toCode(): String = when (this) {
        is PushResult.Accepted -> "ACCEPTED"
        is PushResult.Failed -> "FAILED"
        is PushResult.Skipped -> "SKIPPED"
    }
}
