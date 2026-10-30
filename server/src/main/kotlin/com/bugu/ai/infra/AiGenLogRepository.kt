package com.bugu.ai.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** ai_gen_log 行:AI 生成日志,成本与降级观测(FR-08),结构见 V1__baseline.sql。 */
@Table("ai_gen_log")
data class AiGenLogRow(
    @Id
    val id: Long? = null,
    val scene: String,
    val tone: String? = null,
    val degraded: Boolean = false,
    val latencyMs: Int? = null,
    val promptTokens: Int? = null,
    val outputTokens: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface AiGenLogRepository : CrudRepository<AiGenLogRow, Long> {
    fun findByScene(scene: String): List<AiGenLogRow>
}
