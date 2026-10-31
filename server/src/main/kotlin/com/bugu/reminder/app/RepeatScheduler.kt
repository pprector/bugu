package com.bugu.reminder.app

import com.bugu.reminder.domain.ReminderKind
import com.bugu.reminder.domain.RepeatRulePolicy
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderFireRow
import com.bugu.reminder.infra.ReminderMapper
import com.bugu.reminder.infra.ReminderRepository
import com.bugu.bond.infra.BondRepository
import com.bugu.bond.domain.BondStatus
import com.bugu.reminder.infra.ReminderRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 重复提醒续期(E10 决策 2026-10-27:确认后与错过收口后都续期,系列不因单次结果而终止)。
 *
 * 调用点:三出口 DONE 确认后、升级链 24h 收口 MISSED 后,二者都在各自事务内调用本方法。
 * 幂等:同一 fire_at 已存在则不重复生成(uk_fire_idem 之外的应用层防重)。
 */
@Component
class RepeatScheduler(
    private val reminderRepository: ReminderRepository,
    private val bondRepository: BondRepository,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 为 REPEAT 提醒排下一次;非重复、规则缺失或已排过返回 null。 */
    @org.springframework.transaction.annotation.Transactional
    fun scheduleNext(row: ReminderRow, now: LocalDateTime): ReminderFireRow? {
        if (row.kind != ReminderKind.REPEAT.name) return null
        if (row.bondId != null && bondRepository.lockById(row.bondId)?.status != BondStatus.ACTIVE.name) return null
        if (row.status == "CANCELED") return null
        val reminderId = row.id ?: return null
        val rule = row.repeatRule ?: run {
            log.warn("repeat reminder {} 缺 repeatRule,跳过续期", reminderId)
            return null
        }
        val nextAt = RepeatRulePolicy.nextOccurrence(rule, row.fireAt, now)
        val already = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
            .any { it.fireAt == nextAt }
        if (already) return null

        val domain = ReminderMapper.toDomain(row)
        domain.rescheduleNext(nextAt)
        reminderRepository.save(ReminderMapper.toRow(domain))

        val fire = reminderFireRepository.save(
            ReminderFireRow(
                reminderId = reminderId,
                targetUserId = row.receiverId,
                fireAt = nextAt,
                idempotencyKey = "$reminderId:$nextAt",
            ),
        )
        log.info("repeat 续期 reminder={} next={} rule={}", reminderId, nextAt, rule)
        return fire
    }
}
