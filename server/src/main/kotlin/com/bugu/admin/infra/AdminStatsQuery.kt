package com.bugu.admin.infra

import com.bugu.admin.api.AdminStatsView
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * 后台运营总览查询:六项关键计数一条 SQL 聚合(个人运营规模,无需缓存/预聚合)。
 * 直查事实表而非经 ORM 全量拉取,风格与 ResponseStatsQuery 一致。
 */
@Component
class AdminStatsQuery(private val jdbc: JdbcClient) {

    fun stats(): AdminStatsView =
        jdbc.sql(
            """
            SELECT
                (SELECT COUNT(*) FROM app_user)                                         AS user_total,
                (SELECT COUNT(*) FROM app_user WHERE elder_mode = TRUE)                 AS user_elder,
                (SELECT COUNT(*) FROM bond WHERE status = 'ACTIVE')                     AS bond_active,
                (SELECT COUNT(*) FROM reminder WHERE status = 'PENDING')                AS reminder_pending,
                (SELECT COUNT(*) FROM reminder_fire WHERE fire_at::date = CURRENT_DATE) AS fire_today,
                (SELECT COUNT(*) FROM ai_gen_log WHERE created_at::date = CURRENT_DATE) AS ai_gen_today
            """.trimIndent(),
        )
            .query { rs, _ ->
                AdminStatsView(
                    userTotal = rs.getLong("user_total"),
                    userElder = rs.getLong("user_elder"),
                    bondActive = rs.getLong("bond_active"),
                    reminderPending = rs.getLong("reminder_pending"),
                    fireToday = rs.getLong("fire_today"),
                    aiGenToday = rs.getLong("ai_gen_today"),
                )
            }
            .single()
}
