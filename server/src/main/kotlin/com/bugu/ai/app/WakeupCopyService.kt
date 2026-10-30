package com.bugu.ai.app

import com.bugu.ai.domain.CopyContext
import com.bugu.ai.infra.WakeupCopyRepository
import com.bugu.ai.infra.WakeupCopyRow
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 叫醒语按日缓存(E5-S3):当日首次经 AiCopyService 生成(scene/logScene=WAKEUP)并落库,
 * 同日幂等命中;AI 不可用走 WAKEUP 模板池,降级结果同样落缓存,保证当日内容稳定(端侧断网可播)。
 */
@Service
class WakeupCopyService(
    private val wakeupCopyRepository: WakeupCopyRepository,
    private val aiCopyService: AiCopyService,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 取某 reminder 某日叫醒语:先查缓存,未命中生成并落缓存;并发同键靠 uk 兜底重查。 */
    fun get(reminderId: Long, date: LocalDate, context: CopyContext): WakeupCopyRow {
        wakeupCopyRepository.findByReminderIdAndCopyDate(reminderId, date)?.let { return it }
        val outcome = aiCopyService.composeOutcome(SCENE, context.tone, context, logScene = SCENE)
        if (outcome == null) {
            log.warn("no wakeup copy for reminder={} date={}, use fallback", reminderId, date)
        }
        val row = WakeupCopyRow(
            reminderId = reminderId,
            copyDate = date,
            content = outcome?.content ?: FALLBACK_CONTENT,
            degraded = outcome?.degraded ?: true,
            createdAt = LocalDateTime.now(clock),
        )
        return try {
            wakeupCopyRepository.save(row)
        } catch (_: DuplicateKeyException) {
            // 并发同键:uk(reminder_id, copy_date) 兜底,重查已落库内容
            checkNotNull(wakeupCopyRepository.findByReminderIdAndCopyDate(reminderId, date))
        }
    }

    companion object {
        const val SCENE = "WAKEUP"

        /** 模板池全空时的最后兜底(种子数据保证不发生,防御端侧拿到空内容)。 */
        const val FALLBACK_CONTENT = "早安,该起床啦~"
    }
}
