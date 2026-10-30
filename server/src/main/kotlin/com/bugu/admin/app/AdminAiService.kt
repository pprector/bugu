package com.bugu.admin.app

import com.bugu.admin.api.AdminAiStatusView
import com.bugu.admin.api.AdminGenLogSummary
import com.bugu.admin.api.AdminGenLogView
import com.bugu.admin.api.AdminGenLogsView
import com.bugu.admin.api.AdminTemplateView
import com.bugu.ai.infra.LlmGateway
import com.bugu.ai.infra.LlmProperties
import com.bugu.push.infra.CopyTemplateRepository
import com.bugu.push.infra.CopyTemplateRow
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * AI 管理(E9-S3):降级模板库 CRUD 与启停(AiCopyService 取用时即查 enabled,改动即时生效);
 * ai_gen_log 观测与降级率;LLM 配置只读(运行时切换经配置重启)。
 */
@Service
class AdminAiService(
    private val copyTemplateRepository: CopyTemplateRepository,
    private val genLogRepository: com.bugu.ai.infra.AiGenLogRepository,
    private val llmProperties: LlmProperties,
    private val llmGatewayProvider: ObjectProvider<LlmGateway>,
) {

    companion object {
        private val TONES = setOf("GENTLE", "NAG", "STRICT")
        private val SCENES = setOf("CREATE", "REPUSH", "WAKEUP")
        private const val MAX_CONTENT = 40
    }

    fun templates(tone: String?, scene: String?): List<AdminTemplateView> =
        copyTemplateRepository.findAll()
            .filter { tone == null || it.tone == tone }
            .filter { scene == null || it.scene == scene }
            .sortedBy { it.id }
            .map { it.toView() }

    fun createTemplate(tone: String, scene: String, content: String): AdminTemplateView {
        validateToneScene(tone, scene)
        validateContent(content)
        val saved = copyTemplateRepository.save(
            CopyTemplateRow(tone = tone, scene = scene, content = content.trim(), enabled = true, createdAt = LocalDateTime.now()),
        )
        return saved.toView()
    }

    fun updateTemplate(id: Long, content: String?, enabled: Boolean?): AdminTemplateView {
        if (content == null && enabled == null) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "无可更新字段")
        }
        val row = copyTemplateRepository.findByIdOrNull(id)
            ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "模板不存在")
        if (content != null) validateContent(content)
        val saved = copyTemplateRepository.save(
            row.copy(content = content?.trim() ?: row.content, enabled = enabled ?: row.enabled),
        )
        return saved.toView()
    }

    /** 取模板即查 enabled(AiCopyService 同源),无缓存,启停即时生效。 */
    fun enabledTemplateContents(tone: String, scene: String): List<String> =
        copyTemplateRepository.findByToneAndSceneAndEnabledTrue(tone, scene).map { it.content }

    fun genLogs(scene: String?, page: Int, size: Int): AdminGenLogsView {
        val all = genLogRepository.findAll()
            .filter { scene == null || it.scene == scene }
            .sortedByDescending { it.id }

        val byScene = genLogRepository.findAll().groupBy { it.scene }.map { (name, rows) ->
            val degraded = rows.count { it.degraded }
            AdminGenLogSummary(
                scene = name,
                total = rows.size.toLong(),
                degraded = degraded.toLong(),
                degradeRate = if (rows.isEmpty()) 0.0 else degraded.toDouble() / rows.size,
            )
        }.sortedBy { it.scene }

        val safeSize = size.coerceIn(1, 200)
        val safePage = page.coerceAtLeast(0)
        val pageRows = all.drop(safePage * safeSize).take(safeSize)

        return AdminGenLogsView(
            summary = byScene,
            logs = pageRows.map {
                AdminGenLogView(
                    id = requireNotNull(it.id) { "log id 不应为空" },
                    scene = it.scene,
                    tone = it.tone,
                    degraded = it.degraded,
                    latencyMs = it.latencyMs,
                    createdAt = it.createdAt.toString(),
                )
            },
        )
    }

    fun aiStatus(): AdminAiStatusView {
        val gateway = llmGatewayProvider.getIfAvailable()
        return AdminAiStatusView(
            llmProvider = llmProperties.provider,
            gatewayRegistered = gateway != null,
            note = "P0 只读:LLM 供应商切换经配置(bugu.llm.provider)重启生效;AI 是增强项,无网关时全部走模板降级",
        )
    }

    private fun validateToneScene(tone: String, scene: String) {
        if (tone !in TONES) throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "语气档非法,须为 GENTLE/NAG/STRICT")
        if (scene !in SCENES) throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "场景非法,须为 CREATE/REPUSH/WAKEUP")
    }

    private fun validateContent(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CONTENT) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "文案必填且不超过 40 字")
        }
    }
}

private fun CopyTemplateRow.toView() = AdminTemplateView(
    id = requireNotNull(id) { "template id 不应为空" },
    tone = tone,
    scene = scene,
    content = content,
    enabled = enabled,
    createdAt = createdAt.toString(),
)
