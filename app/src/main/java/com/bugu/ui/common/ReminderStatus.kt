package com.bugu.ui.common

import androidx.compose.ui.graphics.Color
import com.bugu.ui.theme.H
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** ISO 本地时刻 → LocalDateTime(服务端偶发带时区偏移时兜底换算)。 */
fun parseIsoTime(iso: String): LocalDateTime? = try {
    LocalDateTime.parse(iso)
} catch (_: Exception) {
    try {
        OffsetDateTime.parse(iso).toLocalDateTime()
    } catch (_: Exception) {
        null
    }
}

val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 相对日期标签:今天/昨天/明天,其余带星期,避免列表只看到 HH:mm 分不清哪一天。 */
fun relativeDayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    today.plusDays(1) -> "明天"
    else -> {
        val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
        "${d.monthValue}月${d.dayOfMonth}日 周$week"
    }
}

/** 提醒状态 → 彩色小标签文案(全局一致)。 */
fun statusLabel(status: String): String = when (status) {
    "PENDING" -> "待触发"
    "FIRED" -> "已触发"
    "ACKED" -> "已完成"
    "SNOOZED" -> "稍后再叫"
    "DECLINED" -> "已拒绝"
    "MISSED" -> "已错过"
    "CANCELED" -> "已撤销"
    else -> status
}

/** 提醒状态 → 标签色:PENDING 蓝 / FIRED 橙 / ACKED 绿 / SNOOZED 黄 / DECLINED 红 / MISSED·CANCELED 灰。 */
fun statusColor(status: String): Color = when (status) {
    "PENDING" -> H.IndigoInk
    "FIRED" -> H.CoralInk
    "ACKED" -> H.MintInk
    "SNOOZED" -> H.AmberInk
    "DECLINED" -> H.RoseInk
    "MISSED", "CANCELED" -> H.Ink3Strong
    else -> H.Ink2
}

/** 提醒类型 → 中文短标。 */
fun kindLabel(kind: String): String = when (kind) {
    "ONCE" -> "单次惦记"
    "REPEAT" -> "循环惦记"
    "MUTUAL_ALARM" -> "互叫闹钟"
    else -> kind
}

/** 语气档位 → 中文(server ToneLevel:GENTLE/NAG/STRICT)。 */
fun toneLabel(tone: String): String = when (tone) {
    "GENTLE" -> "轻柔语气"
    "NAG" -> "唠叨语气"
    "STRICT" -> "严格语气"
    else -> tone
}

/** 重复规则 → 中文(最小表达:DAILY / WEEKLY:MON,TUE…)。 */
fun repeatLabel(rule: String?): String = when {
    rule == null -> "仅一次"
    rule == "DAILY" -> "每天重复"
    rule.startsWith("WEEKLY:") -> "每周 " + rule.removePrefix("WEEKLY:").split(",")
        .joinToString(" ") { day -> day.take(1) + day.drop(1).lowercase() }
    else -> rule
}
