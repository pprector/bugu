package com.bugu.bond.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * 绑定码策略(domain 纯函数,时间由调用方注入)。
 */
object BindCodePolicy {

    const val CODE_LENGTH = 6

    /** 绑定码有效期,超时后发起方续发新码(同一 bond 行,不重复创建)。 */
    val CODE_TTL: Duration = Duration.ofMinutes(10)

    fun isExpired(codeExpiresAt: LocalDateTime, now: LocalDateTime): Boolean = !now.isBefore(codeExpiresAt)
}
