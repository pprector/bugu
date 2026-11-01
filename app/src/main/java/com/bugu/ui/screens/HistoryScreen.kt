package com.bugu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.domain.companionName
import com.bugu.client.domain.directionLabel
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.ReminderView
import com.bugu.ui.common.BackIconButton
import com.bugu.ui.common.CircleIcon
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
import com.bugu.ui.common.kindLabel
import com.bugu.ui.common.parseIsoTime
import com.bugu.ui.common.repeatLabel
import com.bugu.ui.common.statusColor
import com.bugu.ui.common.statusLabel
import com.bugu.ui.common.TIME_FMT
import com.bugu.ui.theme.H
import java.time.LocalDate

/** 历史条目:提醒 + 方向(我发出 / 我收到)。 */
private data class HistoryEntry(val r: ReminderView, val byMe: Boolean)

/**
 * 惦记历史:我发出与收到的提醒合并后按触发日分组倒序,状态用彩色小标签。
 * 每一次惦记的触发与回应都来自服务端双列表,不再有假回执。
 */
@Composable
fun HistoryScreen(
    api: GeitaApi,
    bonds: List<BondDto>,
    onBack: () -> Unit,
    onGoCreate: () -> Unit,
) {
    var mine by remember { mutableStateOf<List<ReminderView>?>(null) } // null = 首载中
    var incoming by remember { mutableStateOf<List<ReminderView>?>(null) }

    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        failed = false
        try {
            val loadedMine = api.myReminders()
            val loadedIncoming = api.incomingReminders()
            mine = loadedMine
            incoming = loadedIncoming
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }

    val today = LocalDate.now()
    val entries = remember(mine, incoming) {
        (mine.orEmpty().map { HistoryEntry(it, byMe = true) } +
            incoming.orEmpty().map { HistoryEntry(it, byMe = false) })
            .distinctBy { it.r.id }
            .sortedByDescending { it.r.fireAt }
    }
    // 按触发日分组(LinkedHashMap 保持倒序插入)
    val groups = remember(entries) {
        entries.groupBy { parseIsoTime(it.r.fireAt)?.toLocalDate() ?: today }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackIconButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("惦记历史回执", style = MaterialTheme.typography.headlineSmall, color = H.Ink)
                    Text("每一次惦记,都有回音", style = MaterialTheme.typography.bodySmall, color = H.Ink3Strong)
                }
                TagChip(text = "共 ${entries.size} 条", tint = H.Ink2)
            }
        }

        if (failed) {
            item {
                EmptyState(title = "历史加载失败", subtitle = "检查网络后再试试", actionText = "重试", onAction = { retry++ })
            }
        } else if (mine == null || incoming == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
        } else if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有回执",
                    subtitle = "第一条惦记送达后,回执会记在这里",
                    actionText = "设第一条惦记",
                    onAction = onGoCreate,
                )
            }
        } else {
            groups.forEach { (day, items) ->
                item(key = day.toString()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dayLabel(day, today), style = MaterialTheme.typography.titleLarge, color = H.Ink)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(H.GreyTint)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text("${items.size} 条", style = MaterialTheme.typography.titleSmall, color = H.Ink2)
                            }
                        }
                        items.forEach { entry -> HistoryCard(entry, entry.r.companionName(bonds), entry.r.directionLabel(bonds, entry.byMe)) }
                    }
                }
            }
        }
    }
}

/** 单条历史卡:方向头像 + 标题/状态彩标 + 类型与时刻。 */
@Composable
private fun HistoryCard(entry: HistoryEntry, peerName: String, direction: String) {
    val r = entry.r
    RiseIn {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusAvatar(
                    avatar = null,
                    size = 44.dp,
                    ring = if (entry.byMe) H.Coral.copy(alpha = 0.4f) else H.Indigo.copy(alpha = 0.4f),
                    fallbackText = if (entry.byMe) "我" else peerName.firstOrNull()?.toString() ?: "?",
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = H.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        TagChip(text = statusLabel(r.status), tint = statusColor(r.status))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(text = kindLabel(r.kind), tint = if (r.kind == "MUTUAL_ALARM") H.Coral else H.Indigo, icon = Icons.Outlined.NotificationsActive)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            direction,
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = H.Ink3Strong,
                    )
                    Text(
                        repeatLabel(r.repeatRule),
                        style = MaterialTheme.typography.labelSmall,
                        color = H.Ink3Strong,
                    )
                }
            }
            if (r.status == "SNOOZED" || r.status == "DECLINED" || r.status == "MISSED") {
                Spacer(Modifier.height(10.dp))
                Text(
                    when (r.status) {
                        "SNOOZED" -> "💤 TA 选择稍后,咕咕会再叫一次"
                        "DECLINED" -> "🙏 TA 说这会儿不方便,回个关心吧"
                        else -> "⏰ 24 小时内没有回应,已收口为错过"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = H.Ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(H.WarmFill)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** 日期 → 分组头文案(今天/昨天给相对称呼,其余带星期)。 */
private fun dayLabel(d: LocalDate, today: LocalDate): String {
    val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
    return when (d) {
        today -> "今天 · ${d.monthValue}月${d.dayOfMonth}日"
        today.minusDays(1) -> "昨天 · ${d.monthValue}月${d.dayOfMonth}日"
        else -> "${d.monthValue}月${d.dayOfMonth}日 周$week"
    }
}
