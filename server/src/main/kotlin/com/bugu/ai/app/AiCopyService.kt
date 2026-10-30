package com.bugu.ai.app

import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.CopyOutcome
import com.bugu.ai.domain.CopyPromptBuilder
import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.AiGenLogRow
import com.bugu.ai.infra.LlmCopyResult
import com.bugu.ai.infra.LlmGateway
import com.bugu.push.infra.CopyTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * AI 文案应用服务(PRD FR-05/FR-08):正常经 LlmGateway 按脱敏要素生成,
 * 无网关/超时/异常一律降级 copy_template 模板库随机轮换——AI 是增强项不是依赖项。
 * 两条路径均落 ai_gen_log(scene=COPY)供成本与降级观测。
 */
@Service
class AiCopyService(
    private val copyTemplateRepository: CopyTemplateRepository,
    private val aiGenLogRepository: AiGenLogRepository,
    llmGatewayProvider: ObjectProvider<LlmGateway>,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val gateway: LlmGateway? = llmGatewayProvider.ifAvailable

    /**
     * 生成一条推送文案。
     * @return 文案;模板库也无可用模板时返回 null(调用方按 Skipped 处理,不伪造成功)。
     */
    fun compose(scene: String, tone: String, context: CopyContext): String? =
        composeOutcome(scene, tone, context)?.content

    /**
     * 同 [compose],但带降级标记返回(叫醒语缓存等需如实记录 degraded 的调用方使用)。
     * @param logScene ai_gen_log 落库场景(表注释:PARSE / COPY / WAKEUP),与模板场景 scene 可不同
     */
    fun composeOutcome(scene: String, tone: String, context: CopyContext, logScene: String = "COPY"): CopyOutcome? {
        val start = System.nanoTime()
        val gw = gateway
        if (gw != null) {
            runCatching { gw.generateCopy(CopyPromptBuilder.build(context)) }
                .onSuccess { result ->
                    val content = result.content.trim()
                    if (content.isNotEmpty()) {
                        record(logScene, tone, degraded = false, start, result)
                        log.info("ai copy generated scene={} tone={} degraded=false", scene, tone)
                        return CopyOutcome(content.take(MAX_COPY_LENGTH), degraded = false)
                    }
                    log.warn("llm 返回空文案,降级模板 scene={} tone={}", scene, tone)
                }
                .onFailure {
                    log.warn("llm 生成失败,降级模板 scene={} tone={}: {}", scene, tone, it.message)
                }
        }
        val template = pickTemplate(tone, scene)
        record(logScene, tone, degraded = true, start, null)
        if (template == null) {
            log.warn("no copy template for tone={} scene={}", tone, scene)
        } else {
            log.info("ai copy fallback to template scene={} tone={} degraded=true", scene, tone)
        }
        return template?.let { CopyOutcome(it, degraded = true) }
    }

    /** 模板库随机轮换(PRD FR-05:三档各备至少 5 条轮换)。 */
    private fun pickTemplate(tone: String, scene: String): String? =
        copyTemplateRepository.findByToneAndSceneAndEnabledTrue(tone, scene).randomOrNull()?.content

    /** ai_gen_log.scene 记能力类型(表注释:PARSE / COPY / WAKEUP),业务场景只进日志不进库。 */
    private fun record(logScene: String, tone: String, degraded: Boolean, startNanos: Long, result: LlmCopyResult?) {
        val latencyMs = ((System.nanoTime() - startNanos) / 1_000_000).toInt()
        aiGenLogRepository.save(
            AiGenLogRow(
                scene = logScene,
                tone = tone,
                degraded = degraded,
                latencyMs = latencyMs,
                promptTokens = result?.promptTokens,
                outputTokens = result?.outputTokens,
                createdAt = LocalDateTime.now(clock),
            ),
        )
    }

    companion object {
        /** PRD FR-05:单条文案 ≤40 字,超长截断。 */
        const val MAX_COPY_LENGTH = 40
    }
}
