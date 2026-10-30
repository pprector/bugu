package com.bugu.bond.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * 绑定码确认失败限流策略(domain 纯函数,时间由调用方注入)。
 *
 * 6 位数字码共 100 万种取值、10 分钟有效,confirm 不做限流存在被暴力枚举的理论窗口。
 * 规则与短信发送限流同语义:同一确认方 10 分钟窗口内最多失败 5 次,第 6 次起锁定 30 分钟;
 * 确认成功由调用方清零计数。
 */
object ConfirmThrottlePolicy {

    const val MAX_FAILURES_PER_WINDOW = 5
    val WINDOW: Duration = Duration.ofMinutes(10)
    val LOCK_DURATION: Duration = Duration.ofMinutes(30)

    /** 确认尝试决策。 */
    sealed interface Decision {
        /** 放行本次确认尝试。 */
        data object Allowed : Decision

        /** 锁定中:lockedUntil 为锁到期时刻。 */
        data class Denied(val lockedUntil: LocalDateTime) : Decision
    }

    /** 确认前检查:锁未到期则拒绝。 */
    fun evaluateBeforeConfirm(lockedUntil: LocalDateTime?, now: LocalDateTime): Decision =
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            Decision.Denied(lockedUntil)
        } else {
            Decision.Allowed
        }

    /**
     * 确认失败后的计数结果。
     *
     * @return Triple(新失败计数, 新窗口起点, 新锁到期时刻);窗口过期则重新开窗;
     *         失败达上限时 lockedUntil 非空。
     */
    fun onFailure(
        failCount: Int,
        windowStart: LocalDateTime?,
        now: LocalDateTime,
    ): Triple<Int, LocalDateTime, LocalDateTime?> {
        val inWindow = windowStart != null && Duration.between(windowStart, now) < WINDOW
        val newCount = if (inWindow) failCount + 1 else 1
        val newWindowStart = if (inWindow) windowStart else now
        val newLock = if (newCount >= MAX_FAILURES_PER_WINDOW) now.plus(LOCK_DURATION) else null
        return Triple(newCount, newWindowStart!!, newLock)
    }
}
