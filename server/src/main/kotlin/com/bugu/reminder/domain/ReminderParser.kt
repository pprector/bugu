package com.bugu.reminder.domain

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

/**
 * 一句话解析降级桩(epic Decision,2026-10-26):关键词/正则提取,不抽象接口,
 * LLM 通道接入后由 LlmReminderParser 优先调用,本类作为正则兜底保留。
 *
 * 能力边界:日期词(今天/明天/明早/明晚/后天)、时段词默认钟点、「N点(半)」与「HH:mm」、
 * 「每天/天天」判 REPEAT;事项取剥离时间词与前缀后的剩余文本(残留空白收敛为单空格);
 * 「,记得/别忘了/顺便 …」尾句提取为便签。提取不到时刻或事项即低置信。
 */
@Component
class ReminderParser(private val clock: Clock) {

    data class Draft(
        val title: String?,
        val fireAt: LocalDateTime?,
        val kind: ReminderKind,
        val repeatRule: String?,
        val note: String? = null,
        val needsManual: Boolean,
    )

    fun parse(text: String): Draft {
        val now = LocalDateTime.now(clock)
        val kind = if (REPEAT_WORDS.any { text.contains(it) }) ReminderKind.REPEAT else ReminderKind.ONCE
        val repeatRule = if (kind == ReminderKind.REPEAT) "DAILY" else null

        val dayOffset = when {
            text.contains("后天") -> 2L
            text.contains("明天") || text.contains("明日") || text.contains("明早") || text.contains("明晚") -> 1L
            text.contains("今天") || text.contains("今晚") || text.contains("今早") -> 0L
            else -> 1L // 无日期词默认明天,避免过去时刻
        }

        val dayPartHour = DAY_PARTS.entries.firstOrNull { text.contains(it.key) }?.value
        val clockHourMinute = CLOCK_AT.find(text)?.let { m ->
            val h = cnNumber(m.groupValues[1]) ?: m.groupValues[1].toIntOrNull() ?: return@let null
            val minute = if (m.groupValues[2].contains("半")) 30 else 0
            h to minute
        } ?: COLON_TIME.find(text)?.let { m ->
            (m.groupValues[1].toIntOrNull() ?: return@let null) to m.groupValues[2].toInt()
        }

        val fireAt = when {
            clockHourMinute != null && dayPartHour != null -> {
                // 「晚上8点」:钟点 <12 时按时段折算下午/晚上
                val (h, min) = clockHourMinute
                val hour = if (h < 12 && dayPartHour >= 12) h + 12 else h
                if (hour in 0..23) now.toLocalDate().plusDays(dayOffset).atTime(hour, min) else null
            }
            clockHourMinute != null ->
                now.toLocalDate().plusDays(dayOffset).atTime(clockHourMinute.first % 24, clockHourMinute.second)
            dayPartHour != null -> now.toLocalDate().plusDays(dayOffset).atTime(dayPartHour, 0)
            else -> null
        }

        val note = NOTE_MARKER.find(text)?.let { m ->
            (m.groupValues[1] + m.groupValues[2]).trim().take(Reminder.MAX_NOTE).ifBlank { null }
        }
        val title = extractTitle(text)?.take(Reminder.MAX_TITLE)
        return Draft(
            title = title,
            fireAt = fireAt,
            kind = kind,
            repeatRule = repeatRule,
            note = note,
            needsManual = title == null || fireAt == null,
        )
    }

    /** 事项:剥离便签尾句、引导前缀与全部时间词后的剩余文本(空白收敛),空则 null。 */
    private fun extractTitle(text: String): String? {
        var t = NOTE_MARKER.find(text)?.let { text.removeRange(it.range) } ?: text
        PREFIXES.forEach { t = t.replace(it, "") }
        t = t.replace(COLON_TIME, "").replace(CLOCK_AT, "")
        TIME_WORDS.forEach { t = t.replace(it, "") }
        return t.trim().replace(SPACES, " ").ifBlank { null }
    }

    private fun cnNumber(s: String): Int? = CN_NUMBERS[s]

    companion object {
        private val REPEAT_WORDS = listOf("每天", "天天")
        private val DAY_PARTS = linkedMapOf(
            "早上" to 8, "早晨" to 8, "明早" to 8, "今早" to 8,
            "上午" to 9, "中午" to 12, "下午" to 14,
            "晚上" to 20, "明晚" to 20, "今晚" to 20, "傍晚" to 18,
        )
        /** 引导动词前缀;人称不剥(「我妈」的「我」是定语,留给标题)。 */
        private val PREFIXES = listOf("提醒", "记得要", "记得")
        private val TIME_WORDS = listOf(
            "今天", "明天", "明日", "后天", "明早", "明晚", "今晚", "今早",
            "早上", "早晨", "上午", "中午", "下午", "晚上", "傍晚", "每天", "天天",
        )
        private val CN_NUMBERS = mapOf(
            "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10, "十一" to 11, "十二" to 12,
        )
        private val CLOCK_AT = Regex("""([0-9]{1,2}|十一|十二|一|二|两|三|四|五|六|七|八|九|十)点(半)?""")
        private val COLON_TIME = Regex("""([01]?\d|2[0-3]):([0-5]\d)""")

        /** 便签尾句:「,记得(要)/别忘了/顺便 …」;标记与内容一起作为便签,标题剥离该区段。 */
        private val NOTE_MARKER = Regex("""[,，;；]\s*(记得要|记得|别忘了|顺便)([^,，。;；]+)""")

        /** 时间词剥离后的残留空白收敛为单空格,避免「我妈 吃降压药」这类双空格标题。 */
        private val SPACES = Regex("""\s{2,}""")
    }
}
