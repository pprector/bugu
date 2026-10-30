package com.bugu.feedback.infra

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime

 data class FeedbackRow(
    val id: Long,
    val userId: Long,
    val category: String,
    val content: String,
    val appVersion: String?,
    val status: String,
    val reply: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

@Repository
class FeedbackRepository(private val jdbc: JdbcClient) {
    private fun row(rs: ResultSet, index: Int): FeedbackRow = FeedbackRow(
        rs.getLong("id"), rs.getLong("user_id"), rs.getString("category"), rs.getString("content"),
        rs.getString("app_version"), rs.getString("status"), rs.getString("reply"),
        rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
    )

    fun create(userId: Long, category: String, content: String, appVersion: String?, now: LocalDateTime): FeedbackRow =
        jdbc.sql("""
            INSERT INTO feedback (user_id, category, content, app_version, status, created_at, updated_at)
            VALUES (:userId, :category, :content, :appVersion, 'OPEN', :now, :now) RETURNING *
        """.trimIndent())
            .param("userId", userId).param("category", category).param("content", content)
            .param("appVersion", appVersion, java.sql.Types.VARCHAR).param("now", now)
            .query(::row).single()

    fun mine(userId: Long): List<FeedbackRow> =
        jdbc.sql("SELECT * FROM feedback WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT 50")
            .param("userId", userId).query(::row).list()

    fun list(page: Int, size: Int, status: String?): List<FeedbackRow> {
        val where = if (status == null) "" else "WHERE status = :status"
        val query = jdbc.sql("SELECT * FROM feedback $where ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset")
            .param("size", size).param("offset", (page.toLong() - 1) * size)
        if (status != null) query.param("status", status)
        return query.query(::row).list()
    }

    fun count(status: String?): Long {
        val query = jdbc.sql("SELECT COUNT(*) FROM feedback" + if (status == null) "" else " WHERE status = :status")
        if (status != null) query.param("status", status)
        return query.query(Long::class.java).single()
    }

    fun update(id: Long, status: String, reply: String?, now: LocalDateTime): FeedbackRow? =
        jdbc.sql("UPDATE feedback SET status = :status, reply = :reply, updated_at = :now WHERE id = :id RETURNING *")
            .param("id", id).param("status", status).param("reply", reply, java.sql.Types.VARCHAR).param("now", now)
            .query(::row).optional().orElse(null)
}
