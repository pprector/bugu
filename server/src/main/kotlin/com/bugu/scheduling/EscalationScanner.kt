package com.bugu.scheduling

import com.bugu.ai.domain.PaceAdvisor
import com.bugu.ai.infra.ResponseStatsQuery
import com.bugu.push.app.PushService
import com.bugu.reminder.app.RepeatScheduler
import com.bugu.reminder.domain.Reminder
import com.bugu.reminder.domain.ReminderKind
import com.bugu.reminder.domain.ReminderStatus
import com.bugu.reminder.infra.AckRepository
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.EscalationLogRow
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderFireRow
import com.bugu.reminder.infra.ReminderMapper
import com.bugu.reminder.infra.ReminderRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * 升级链扫描器(PRD FR-03 / FR-07 / 技术架构 2.3)。
 *
 * 每 60s 扫描 state IN (PENDING, FIRED) 且 fire_at <= now-T1 的触发记录,按 E7 三路径推进:
 * - 路径 A(reported_at 非空):端侧已响,走 T1/T2/CLOSE 升级链
 * - 路径 B(reported_at 为空且距 fire_at ≥ 2h 宽限):设备关机错过,直接收口 MISSED(不发 T1/T2)
 * - 路径 C(未上报且在 2h 宽限内):照常走 T1/T2/CLOSE——升级链推送兜底独立于端侧上报
 *
 * FIRED(撤销后重新待响应)纳入候选,修复 E6 撤销后该 fire 永不再被扫描的缺口。
 * 距 fire_at 时长按计划时刻计;T1(15min)再推接收人、T2(30min)升级通知、CLOSE(24h)收口。
 * 防重:escalation_log 同 fire+stage 行存在性;已 ack 的 fire 不升级。
 */
@Component
class EscalationScanner(
    private val jdbc: JdbcTemplate,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
    private val ackRepository: AckRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val pushService: PushService,
    private val responseStatsQuery: ResponseStatsQuery,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
    private val repeatScheduler: RepeatScheduler,
) {

    companion object {
        private val log = LoggerFactory.getLogger(EscalationScanner::class.java)

        /** T1:首次触发后 15 分钟无响应,再推一次接收人。 */
        val T1: Duration = Duration.ofMinutes(15)

        /** T2:30 分钟仍无响应,升级通知发起人。 */
        val T2: Duration = Duration.ofMinutes(30)

        /** 24 小时无任何响应,收口为未完成。 */
        val CLOSE: Duration = Duration.ofHours(24)

        /** 设备关机宽限窗口(E7/PRD FR-07):未上报且超窗的 fire 直接收口为已错过。 */
        val GRACE: Duration = Duration.ofHours(2)

        private const val FIND_CANDIDATES =
            "SELECT id, reminder_id, target_user_id, fire_at, reported_at, source, state, idempotency_key, created_at " +
                "FROM reminder_fire WHERE state IN ('PENDING', 'FIRED') AND fire_at <= ? ORDER BY fire_at LIMIT 200"
    }

    @Scheduled(fixedDelayString = "\${bugu.escalation.scan-delay:60000}")
    @SchedulerLock(
        name = "escalation-scan",
        lockAtMostFor = "\${bugu.escalation.lock-at-most:PT4M}",
        lockAtLeastFor = "\${bugu.escalation.lock-at-least:PT30S}",
    )
    fun scan() {
        doScan()
    }

    /** 扫描主体(包内可见,供测试直接调用以绕开 @SchedulerLock)。 */
    internal fun doScan() {
        val now = LocalDateTime.now(clock)
        // 候选阈值放宽到最早可能到期的 T1(秒回者提前 5min,即 10min),个体阈值在 processFire 内按偏移判定
        val t1Threshold = now.minus(T1).plus(PaceAdvisor.MAX_SHIFT)

        val candidates = jdbc.query(
            FIND_CANDIDATES,
            { rs, _ ->
                ReminderFireRow(
                    id = rs.getLong("id"),
                    reminderId = rs.getLong("reminder_id"),
                    targetUserId = rs.getLong("target_user_id"),
                    fireAt = rs.getTimestamp("fire_at").toLocalDateTime(),
                    reportedAt = rs.getTimestamp("reported_at")?.toLocalDateTime(),
                    source = rs.getString("source"),
                    state = rs.getString("state"),
                    idempotencyKey = rs.getString("idempotency_key"),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                )
            },
            Timestamp.valueOf(t1Threshold),
        )
        if (candidates.isEmpty()) {
            log.debug("escalation scan: no pending fire before {}", t1Threshold)
            return
        }

        for (fire in candidates) {
            runCatching { processFire(fire, now) }
                .onFailure { log.error("escalation scan: process fire {} failed", fire.id, it) }
        }
    }

    private fun processFire(fire: ReminderFireRow, now: LocalDateTime) {
        val fireId = fire.id ?: return
        // 已 ack 的 fire 不升级(防御:ack 会把 fire 置 RESPONDED,正常不会进候选)
        if (ackRepository.existsByFireId(fireId)) return

        val reminderRow = reminderRepository.findByIdOrNull(fire.reminderId) ?: run {
            log.warn("escalation scan: fire {} has no reminder {}, skip", fireId, fire.reminderId)
            return
        }
        val reminder = ReminderMapper.toDomain(reminderRow)
        val elapsed = Duration.between(fire.fireAt, now)

        // 路径 B(E7/FR-07):未上报且超 2h 宽限 → 设备关机错过,直接收口,不发 T1/T2;
        // 未上报但在宽限内(路径 C)与已上报(路径 A)一样照常走升级链(推送兜底独立于端侧上报)
        if (fire.reportedAt == null && elapsed >= GRACE) {
            if (!escalationLogRepository.existsByFireIdAndStage(fireId, "CLOSE")) {
                close(fire, reminder)
            }
            return
        }

        // CLOSE 为终态:命中即收口,不再推 T1/T2;24h 不做节奏偏移(PRD FR-05)
        if (elapsed >= CLOSE) {
            if (!escalationLogRepository.existsByFireIdAndStage(fireId, "CLOSE")) {
                close(fire, reminder)
            }
            return
        }

        // 节奏自适应:按本次触发目标用户近期响应延迟微调 T1/T2(±5min 封顶,负=提前)
        val shift = PaceAdvisor.shift(
            responseStatsQuery.recentDelaysSeconds(fire.targetUserId).map { Duration.ofSeconds(it) },
        )
        val t1At = T1.plus(shift)
        val t2At = T2.plus(shift)

        // T1 / T2 顺序推进(可能同一轮都到期)
        if (elapsed >= t1At && !escalationLogRepository.existsByFireIdAndStage(fireId, "T1")) {
            // T1 再推未起方本人(ONCE 的 target 即接收人,行为不变)
            pushService.push(
                fireId = fireId, stage = "T1", action = "REPUSH_RECEIVER",
                userId = fire.targetUserId, tone = reminder.tone.name, scene = "REPUSH",
            )
            log.info("escalation T1: fire {} reminder {} pushed to target {} (shift={}s)", fireId, reminder.id, fire.targetUserId, shift.seconds)
        }
        if (elapsed >= t2At && !escalationLogRepository.existsByFireIdAndStage(fireId, "T2")) {
            // 互叫闹钟:T2 通知另一方;其余类型通知发起人
            val t2Target = if (reminder.kind == ReminderKind.MUTUAL_ALARM) {
                if (fire.targetUserId == reminder.creatorId) reminder.receiverId else reminder.creatorId
            } else {
                reminder.creatorId
            }
            pushService.push(
                fireId = fireId, stage = "T2", action = "NOTIFY_CREATOR",
                userId = t2Target, tone = reminder.tone.name, scene = "REPUSH",
            )
            log.info("escalation T2: fire {} reminder {} escalated to {} (shift={}s)", fireId, reminder.id, t2Target, shift.seconds)
        }
    }

    /** 24h 收口:reminder→MISSED、fire→CLOSED、escalation_log CLOSE/MARK_MISSED,同事务;互叫双方都未起时第二条 fire 仅收口自身。 */
    private fun close(fire: ReminderFireRow, reminder: Reminder) {
        transactionTemplate.execute {
            val savedRow = if (reminder.status != ReminderStatus.MISSED) {
                reminder.markMissed()
                reminderRepository.save(ReminderMapper.toRow(reminder))
            } else {
                null
            }
            reminderFireRepository.save(fire.copy(state = "CLOSED"))
            escalationLogRepository.save(
                EscalationLogRow(
                    fireId = fire.id!!,
                    stage = "CLOSE",
                    action = "MARK_MISSED",
                    createdAt = LocalDateTime.now(clock),
                ),
            )
            log.info("escalation CLOSE: fire {} reminder {} marked MISSED", fire.id, reminder.id)
            // REPEAT:错过收口也排下一次,避免漏一天就把「每天提醒」系列终止(E10 决策)
            savedRow?.let { repeatScheduler.scheduleNext(it, LocalDateTime.now(clock)) }
        }
    }
}
