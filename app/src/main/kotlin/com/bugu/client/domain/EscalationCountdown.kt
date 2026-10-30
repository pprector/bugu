package com.bugu.client.domain

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 升级链端侧倒计时:与 server EscalationScanner 的 T1/T2 基准一致(不含服务端节奏自适应偏移——
 * 偏移是服务端推送时机,端侧展示用基准值即可,误差 ≤5min)。
 * 时间一律用 Instant(UTC 时刻),由调用方先把 ISO 本地时刻按时区换算,纯函数可测。
 */
object EscalationCountdown {

    /** T1:触发 15 分钟无响应,服务端再推一次。 */
    val T1: Duration = 15.minutes

    /** T2:30 分钟仍无响应,服务端升级通知发起人。 */
    val T2: Duration = 30.minutes

    /** 24 小时收口为未完成。 */
    val CLOSE: Duration = Duration.parseIsoString("PT24H")

    /** 升级链阶段:UI 据此展示「等待中 / 已再推 / 已通知家人」。 */
    enum class Stage { BEFORE_T1, AFTER_T1, AFTER_T2, CLOSED }

    /** 距 fireAt 经过 elapsed,当前所处升级链阶段。 */
    fun stageAt(fireAt: Instant, now: Instant): Stage {
        val elapsed = now - fireAt
        return when {
            elapsed >= CLOSE -> Stage.CLOSED
            elapsed >= T2 -> Stage.AFTER_T2
            elapsed >= T1 -> Stage.AFTER_T1
            else -> Stage.BEFORE_T1
        }
    }

    /** 距下一升级节点(或收口)的剩余时长;已过节点返回 Duration.ZERO。 */
    fun remainingToNext(fireAt: Instant, now: Instant): Duration {
        val elapsed = now - fireAt
        return when {
            elapsed >= T2 -> (CLOSE - elapsed).coerceAtLeast(Duration.ZERO)
            elapsed >= T1 -> (T2 - elapsed).coerceAtLeast(Duration.ZERO)
            else -> (T1 - elapsed).coerceAtLeast(Duration.ZERO)
        }
    }
}
