package com.bugu.scheduling

import com.bugu.push.PushResult
import com.bugu.push.app.PushService
import com.bugu.reminder.infra.EscalationLogRepository
import com.bugu.reminder.infra.EscalationLogRow
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderFireRow
import com.bugu.reminder.infra.ReminderRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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
 * fireAt 到点推送扫描器(服务端主动触达,不依赖端侧打开 App)。
 *
 * 每 60s 扫描一次:state=PENDING/FIRED、未上报、未取消、未静默、fire_at 已到且仍在补推窗口内的 fire。
 * 对每条 fire 以 escalation_log 的 (fire_id, FIRE_AT) 唯一行做原子 claim——插入成功者才发送,
 * 发送后回填 ACCEPTED/FAILED/SKIPPED。重启/多实例/重复扫描都因唯一约束不会二次推送。
 *
 * 超过 [WINDOW] 的过期 fire 不补推,交给 EscalationScanner 的 T1/T2 升级链,避免用户短时间收到多条。
 * 端侧已上报(reported_at 非空)的 fire 说明本地闹钟已响,不再推,避免重复打扰。
 */
@Component
class FireAtPushScanner(
    private val jdbc: JdbcTemplate,
    private val reminderFireRepository: ReminderFireRepository,
    private val reminderRepository: ReminderRepository,
    private val escalationLogRepository: EscalationLogRepository,
    private val pushService: PushService,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) {

    companion object {
        private val log = LoggerFactory.getLogger(FireAtPushScanner::class.java)

        /** 到点后允许补推的时间窗;超过即视为过期,不再补推。 */
        val WINDOW: Duration = Duration.ofMinutes(10)

        const val STAGE = "FIRE_AT"
        const val ACTION = "RING_RECEIVER"
        const val CLAIMED = "CLAIMED"

        private const val FIND_CANDIDATES =
            "SELECT f.id, f.reminder_id, f.target_user_id, f.fire_at, f.reported_at, f.source, f.state, " +
                "f.idempotency_key, f.created_at FROM reminder_fire f " +
                "JOIN reminder r ON r.id = f.reminder_id " +
                "WHERE f.state IN ('PENDING', 'FIRED') AND f.reported_at IS NULL " +
                "AND r.status <> 'CANCELED' AND r.silent = FALSE " +
                "AND f.fire_at <= ? AND f.fire_at >= ? " +
                "AND NOT EXISTS (SELECT 1 FROM escalation_log e WHERE e.fire_id = f.id AND e.stage = 'FIRE_AT') " +
                "ORDER BY f.fire_at LIMIT 200"
    }

    @Scheduled(fixedDelayString = "\${bugu.fire-at.scan-delay:60000}")
    @SchedulerLock(
        name = "fire-at-push-scan",
        lockAtMostFor = "\${bugu.fire-at.lock-at-most:PT4M}",
        lockAtLeastFor = "\${bugu.fire-at.lock-at-least:PT30S}",
    )
    fun scan() {
        doScan()
    }

    /** 扫描主体(包内可见,供测试直接调用以绕开 @SchedulerLock)。 */
    internal fun doScan() {
        val now = LocalDateTime.now(clock)
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
            Timestamp.valueOf(now),
            Timestamp.valueOf(now.minus(WINDOW)),
        )
        if (candidates.isEmpty()) {
            log.debug("fire-at scan: no due fire in window")
            return
        }

        for (fire in candidates) {
            runCatching { process(fire) }
                .onFailure { log.error("fire-at scan: process fire {} failed", fire.id, it) }
        }
    }

    private fun process(fire: ReminderFireRow) {
        val fireId = fire.id ?: return
        // 二次校验:查询后到发送前可能被端侧上报/确认/取消
        val current = reminderFireRepository.findByIdOrNull(fireId) ?: return
        if (current.state !in setOf("PENDING", "FIRED") || current.reportedAt != null) return
        val reminder = reminderRepository.findByIdOrNull(current.reminderId) ?: return
        if (reminder.status == "CANCELED" || reminder.silent) return

        val claim = claim(fireId) ?: run {
            log.debug("fire-at already claimed fire={}, skip", fireId)
            return
        }
        val result = runCatching { pushService.sendFireAt(fireId) }
            .onFailure { log.error("fire-at push failed fire={}", fireId, it) }
            .getOrElse { PushResult.Failed("exception: ${it.javaClass.simpleName}", retryable = false) }
        runCatching {
            escalationLogRepository.save(claim.copy(pushResult = result.toCode()))
        }.onFailure { log.error("fire-at result record failed fire={} result={}", fireId, result, it) }
        log.info(
            "fire-at push fire={} reminder={} target={} result={}",
            fireId, current.reminderId, current.targetUserId, result,
        )
    }

    /**
     * 原子 claim:插入 (fireId, FIRE_AT) 唯一行,成功返回带 id 的行;已存在返回 null。
     * 独立事务提交后再发送,保证重启/多实例不会因重复扫描重推;进程若在 claim 后崩溃,
     * 该 fire 不会补推(至多一次语义),由 T1/T2 升级链兜底。
     */
    private fun claim(fireId: Long): EscalationLogRow? =
        try {
            transactionTemplate.execute {
                escalationLogRepository.save(
                    EscalationLogRow(
                        fireId = fireId,
                        stage = STAGE,
                        action = ACTION,
                        pushResult = CLAIMED,
                        createdAt = LocalDateTime.now(clock),
                    ),
                )
            }
        } catch (e: DataIntegrityViolationException) {
            null
        }

    private fun PushResult.toCode(): String = when (this) {
        is PushResult.Accepted -> "ACCEPTED"
        is PushResult.Failed -> "FAILED"
        is PushResult.Skipped -> "SKIPPED"
    }
}
