package com.bugu.ai.infra

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** 接收人响应记录查询(脱敏使用:只取延迟秒数分档,原文不出库)。 */
@Component
class ResponseStatsQuery(private val jdbc: JdbcTemplate) {

    /** 接收人近 [limit] 次响应延迟秒(ack.acked_at - fire.fire_at),新到旧;无记录返回空列表。 */
    fun recentDelaysSeconds(receiverId: Long, limit: Int = RECENT_LIMIT): List<Long> =
        jdbc.query(
            """
            SELECT EXTRACT(EPOCH FROM (a.acked_at - f.fire_at))::BIGINT AS delay_s
            FROM reminder_ack a
            JOIN reminder_fire f ON f.id = a.fire_id
            JOIN reminder r ON r.id = f.reminder_id
            WHERE r.receiver_id = ?
            ORDER BY a.acked_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("delay_s") },
            receiverId, limit,
        )

    companion object {
        const val RECENT_LIMIT = 10
    }
}
