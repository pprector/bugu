package com.bugu.identity.infra

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * sms_code 行:验证码哈希与按手机的发送限流计数。
 */
data class SmsCodeRow(
    val phone: String,
    val codeHash: String,
    val sendCount: Int,
    val windowStart: LocalDateTime,
    val lockedUntil: LocalDateTime?,
    val sentAt: LocalDateTime,
)

/**
 * sms_code 表访问。upsert 用 PostgreSQL ON CONFLICT 保证一行一手机。
 */
@Repository
class SmsCodeRepository(private val jdbc: JdbcClient) {

    fun find(phone: String): SmsCodeRow? =
        jdbc.sql(
            """
            SELECT phone, code_hash, send_count, window_start, locked_until, sent_at
            FROM sms_code WHERE phone = :phone
            """.trimIndent(),
        )
            .param("phone", phone)
            .query { rs, _ ->
                SmsCodeRow(
                    phone = rs.getString("phone"),
                    codeHash = rs.getString("code_hash"),
                    sendCount = rs.getInt("send_count"),
                    windowStart = rs.getObject("window_start", LocalDateTime::class.java),
                    lockedUntil = rs.getObject("locked_until", LocalDateTime::class.java),
                    sentAt = rs.getObject("sent_at", LocalDateTime::class.java),
                )
            }
            .optional()
            .orElse(null)

    /** 放行发送:写入新码哈希与窗口计数,清除旧锁。 */
    fun upsertOnSend(row: SmsCodeRow, now: LocalDateTime) {
        jdbc.sql(
            """
            INSERT INTO sms_code (phone, code_hash, send_count, window_start, locked_until, sent_at, created_at, updated_at)
            VALUES (:phone, :codeHash, :sendCount, :windowStart, NULL, :sentAt, :now, :now)
            ON CONFLICT (phone) DO UPDATE SET
                code_hash = EXCLUDED.code_hash,
                send_count = EXCLUDED.send_count,
                window_start = EXCLUDED.window_start,
                locked_until = NULL,
                sent_at = EXCLUDED.sent_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .param("phone", row.phone)
            .param("codeHash", row.codeHash)
            .param("sendCount", row.sendCount)
            .param("windowStart", row.windowStart)
            .param("sentAt", row.sentAt)
            .param("now", now)
            .update()
    }

    /** 拒绝发送:写入锁到期时刻,计数与窗口保持不变。 */
    fun lock(phone: String, lockedUntil: LocalDateTime, now: LocalDateTime) {
        jdbc.sql(
            """
            INSERT INTO sms_code (phone, code_hash, send_count, window_start, locked_until, sent_at, created_at, updated_at)
            VALUES (:phone, '', 0, :now, :lockedUntil, :now, :now, :now)
            ON CONFLICT (phone) DO UPDATE SET locked_until = EXCLUDED.locked_until, updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .param("phone", phone)
            .param("lockedUntil", lockedUntil)
            .param("now", now)
            .update()
    }

    /** 验证成功后删除(一次性使用)。 */
    fun delete(phone: String) {
        jdbc.sql("DELETE FROM sms_code WHERE phone = :phone")
            .param("phone", phone)
            .update()
    }
}
