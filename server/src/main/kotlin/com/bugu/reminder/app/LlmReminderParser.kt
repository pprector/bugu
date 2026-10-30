package com.bugu.reminder.app

import com.bugu.ai.infra.LlmGateway
import com.bugu.reminder.domain.Reminder
import com.bugu.reminder.domain.ReminderKind
import com.bugu.reminder.domain.ReminderParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 一句话解析:LLM 优先,正则兜底(AI 是增强项不是依赖项)。
 * bugu.llm.provider=none(默认)或 LLM 超时/输出不合法时,回落 ReminderParser 正则解析;
 * degraded 如实标记,解析不抛错,创建链路永不被 AI 阻塞。
 */
@Component
class LlmReminderParser(
    llmGatewayProvider: ObjectProvider<LlmGateway>,
    private val fallbackParser: ReminderParser,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val gateway: LlmGateway? = llmGatewayProvider.ifAvailable
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析结果:复用正则桩的 Draft 形状,degraded=true 表示走了正则兜底。 */
    data class Parsed(val draft: ReminderParser.Draft, val degraded: Boolean)

    fun parse(text: String, now: LocalDateTime): Parsed {
        val gw = gateway ?: return fallback(text, "无可用 LLM 网关")
        val result = try {
            gw.generateText(
                systemPrompt = PARSE_SYSTEM_PROMPT,
                userPrompt = buildPrompt(text, now),
                maxTokens = MAX_OUTPUT_TOKENS,
                temperature = TEMPERATURE,
            )
        } catch (e: Exception) {
            return fallback(text, "LLM 请求失败: ${e.message}")
        }
        return try {
            val dto = json.decodeFromString<LlmParseDto>(extractJson(result.content))
            Parsed(toDraft(dto), degraded = false)
        } catch (e: Exception) {
            fallback(text, "LLM 输出不合法: ${e.message}")
        }
    }

    private fun fallback(text: String, reason: String): Parsed {
        log.warn("llm parse 降级正则解析: {}", reason)
        return Parsed(fallbackParser.parse(text), degraded = true)
    }

    /** LLM 输出草稿→Draft:kind 白名单校验、时刻 ISO 解析,字段缺失按低置信处理。 */
    private fun toDraft(dto: LlmParseDto): ReminderParser.Draft {
        val kind = ReminderKind.entries.firstOrNull { it.name == dto.kind }
            ?: throw IllegalArgumentException("kind 不合法: ${dto.kind}")
        val title = dto.title?.trim()?.takeIf { it.isNotEmpty() }?.take(Reminder.MAX_TITLE)
        val fireAt = dto.fireAt?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDateTime.parse(it)
            } catch (_: Exception) {
                null
            }
        }
        return ReminderParser.Draft(
            title = title,
            fireAt = fireAt,
            kind = kind,
            repeatRule = if (kind == ReminderKind.REPEAT) {
                dto.repeatRule?.trim()?.takeIf { it.isNotEmpty() } ?: "DAILY"
            } else {
                null
            },
            note = dto.note?.trim()?.takeIf { it.isNotEmpty() }?.take(Reminder.MAX_NOTE),
            needsManual = title == null || fireAt == null,
        )
    }

    /** 抠出首个 {...} JSON 块(容忍 ```json 围栏与前后闲话)。 */
    private fun extractJson(content: String): String {
        val body = content.replace("```json", "").replace("```", "")
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        require(start in 0 until end) { "缺少 JSON 对象: $content" }
        return body.substring(start, end + 1)
    }

    /** 带当前时间,让「明早」这类相对表达可锚定。 */
    private fun buildPrompt(text: String, now: LocalDateTime): String =
        "当前时间:${now.format(TIME_FORMAT)}(24 小时制,ISO yyyy-MM-dd'T'HH:mm:ss)。" +
            "请把「待解析」内容解析成提醒草稿,只输出一个 JSON 对象:" +
            "{\"title\":\"事项,不超过20字\",\"fireAt\":\"本地时刻,无时间词则留空\", " +
            "\"kind\":\"ONCE|REPEAT|MUTUAL_ALARM\",\"repeatRule\":\"REPEAT 时填 DAILY 或 WEEKLY:MON,TUE…,否则留空\", " +
            "\"note\":\"便签,如「记得多喝水」,无则留空\"}。" +
            "待解析:「$text」"

    @Serializable
    private data class LlmParseDto(
        val title: String? = null,
        val fireAt: String? = null,
        val kind: String,
        val repeatRule: String? = null,
        val note: String? = null,
    )

    companion object {
        private const val PARSE_SYSTEM_PROMPT =
            "你是家庭共享提醒应用的解析引擎,把中文自然语言解析成提醒草稿。只输出一个 JSON 对象,不要输出任何其他文字。"

        /** 草稿字段少而短,200 token 足够;低温保证结构稳定。 */
        private const val MAX_OUTPUT_TOKENS = 200
        private const val TEMPERATURE = 0.2

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
