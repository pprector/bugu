package com.bugu.ai.domain

import java.time.Duration

/**
 * 节奏自适应纯函数(PRD FR-05):按接收人近期响应延迟微调 T1/T2。
 * 秒回者提前、慢响应者放宽,±5min 封顶,不改变 24h 收口。
 * 分档阈值与 ResponseHabit 一致(≤120s 秒回、≥600s 慢响应),中间档不动。
 */
object PaceAdvisor {

    /** 偏移上限(PRD FR-05:±5min 封顶)。 */
    val MAX_SHIFT: Duration = Duration.ofMinutes(5)

    /** 秒回阈值(与 ResponseHabit.FAST 一致)。 */
    private const val FAST_SECONDS = 120L

    /** 慢响应阈值(与 ResponseHabit.SLOW 一致)。 */
    private const val SLOW_SECONDS = 600L

    /**
     * @param recentDelays 接收人近期响应延迟(fireAt→ackedAt),新到旧
     * @return T1/T2 偏移(负=提前,正=放宽);无记录零偏移
     */
    fun shift(recentDelays: List<Duration>): Duration {
        if (recentDelays.isEmpty()) return Duration.ZERO
        val avgSeconds = recentDelays.map { it.seconds }.average()
        return when {
            avgSeconds <= FAST_SECONDS -> MAX_SHIFT.negated()
            avgSeconds >= SLOW_SECONDS -> MAX_SHIFT
            else -> Duration.ZERO
        }
    }
}
