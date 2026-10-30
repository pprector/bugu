package com.bugu.bond.infra

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * bond_confirm_throttle 行:按确认方用户的绑定码确认失败限流计数。
 */
data class ConfirmThrottleRow(
    val userId: Long,
    val failCount: Int,
    val windowStart: LocalDateTime,
    val lockedUntil: LocalDateTime?,
)

/**
 * bond_confirm_throttle 表访问。upsert 用 PostgreSQL ON CONFLICT 保证一行一用户。
 */
@Repository
class ConfirmThrottleRepository(private val jdbc: JdbcClient) {

    fun find(userId: Long): ConfirmThrottleRow? =
        jdbc.sql(
            """
            SELECT user_id, fail_count, window_start, locked_until
            FROM bond_confirm_throttle WHERE user_id = :userId
            """.trimIndent(),
        )
            .param("userId", userId)
            .query { rs, _ ->
                ConfirmThrottleRow(
                    userId = rs.getLong("user_id"),
                    failCount = rs.getInt("fail_count"),
                    windowStart = rs.getObject("window_start", LocalDateTime::class.java),
                    lockedUntil = rs.getObject("locked_until", LocalDateTime::class.java),
                )
            }
            .optional()
            .orElse(null)

    /** 确认失败:写入新计数、窗口起点与锁(若有)。 */
    fun upsertOnFailure(userId: Long, failCount: Int, windowStart: LocalDateTime, lockedUntil: LocalDateTime?, now: LocalDateTime) {
        jdbc.sql(
            """
            INSERT INTO bond_confirm_throttle (user_id, fail_count, window_start, locked_until, created_at, updated_at)
            VALUES (:userId, :failCount, :windowStart, :lockedUntil, :now, :now)
            ON CONFLICT (user_id) DO UPDATE SET
                fail_count = EXCLUDED.fail_count,
                window_start = EXCLUDED.window_start,
                locked_until = EXCLUDED.locked_until,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("failCount", failCount)
            .param("windowStart", windowStart)
            .param("lockedUntil", lockedUntil)
            .param("now", now)
            .update()
    }

    /** 确认成功:清零该行(不再有失败计数)。 */
    fun delete(userId: Long) {
        jdbc.sql("DELETE FROM bond_confirm_throttle WHERE user_id = :userId")
            .param("userId", userId)
            .update()
    }
}
