package com.bugu.identity.domain

import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime

/**
 * 短信验证码与发送限流策略(domain 纯函数,时间由调用方注入,便于测试)。
 *
 * 规则(epic Requirements R2):
 * - 同一手机号 60s 窗口内最多发 5 次,第 6 次拒绝并锁定 15 分钟;
 * - 验证码 6 位数字,有效期 5 分钟,一次性使用;
 * - 明文不落库,只存 SHA-256 哈希。
 */
object SmsCodePolicy {

    const val CODE_LENGTH = 6
    val SEND_WINDOW: Duration = Duration.ofSeconds(60)
    const val MAX_SENDS_PER_WINDOW = 5
    val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    val CODE_TTL: Duration = Duration.ofMinutes(5)

    /** 发码决策:窗口内未超限则放行并返回新的计数与窗口起点;超限返回锁定到期时刻。 */
    sealed interface SendDecision {
        /** 放行:newCount 为本次发送后的窗口计数,windowStart 为应写入的窗口起点。 */
        data class Allowed(val newCount: Int, val windowStart: LocalDateTime) : SendDecision

        /** 拒绝:lockedUntil 为锁到期时刻。 */
        data class Denied(val lockedUntil: LocalDateTime) : SendDecision
    }

    /**
     * @param sendCount 窗口内已发次数(0 表示该手机号从未发过)
     * @param windowStart 当前窗口起点(null 表示从未发过)
     * @param lockedUntil 现存锁到期时刻(null 表示未锁)
     */
    fun evaluateSend(
        sendCount: Int,
        windowStart: LocalDateTime?,
        lockedUntil: LocalDateTime?,
        now: LocalDateTime,
    ): SendDecision {
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            return SendDecision.Denied(lockedUntil)
        }
        val inWindow = windowStart != null && Duration.between(windowStart, now) < SEND_WINDOW
        return if (inWindow && sendCount >= MAX_SENDS_PER_WINDOW) {
            SendDecision.Denied(now.plus(LOCK_DURATION))
        } else if (inWindow) {
            SendDecision.Allowed(sendCount + 1, windowStart)
        } else {
            SendDecision.Allowed(1, now)
        }
    }

    /** 验证码是否有效:哈希一致且在有效期内。调用方在验证成功后自行删除记录(一次性)。 */
    fun isCodeValid(storedHash: String, sentAt: LocalDateTime, input: String, now: LocalDateTime): Boolean {
        val expired = Duration.between(sentAt, now) >= CODE_TTL
        if (expired) return false
        return hash(input) == storedHash
    }

    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(code.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
