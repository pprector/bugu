package com.bugu.bond.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * 绑定码策略(domain 纯函数,时间由调用方注入)。
 */
object BindCodePolicy {

    const val CODE_LENGTH = 6

    /** 绑定码有效期：改为 7 天，方便家人朋友之间分享使用。 */
    val CODE_TTL: Duration = Duration.ofDays(7)

    fun isExpired(codeExpiresAt: LocalDateTime, now: LocalDateTime): Boolean = !now.isBefore(codeExpiresAt)
}
