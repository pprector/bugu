package com.bugu.push.infra

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * copy_template 行:降级文案模板库(结构见 V1__baseline.sql)。
 * tone=GENTLE/NAG/STRICT;scene=CREATE/REPUSH/WAKEUP。
 */
@Table("copy_template")
data class CopyTemplateRow(
    @Id
    val id: Long? = null,
    val tone: String,
    val scene: String,
    val content: String,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Repository
interface CopyTemplateRepository : CrudRepository<CopyTemplateRow, Long> {

    /** 三档轮换(PRD FR-05):取 enabled 列表供随机选取。 */
    fun findByToneAndSceneAndEnabledTrue(tone: String, scene: String): List<CopyTemplateRow>
}
