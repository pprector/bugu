package com.bugu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugu.client.api.BondDto
import com.bugu.client.domain.directionLabel
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.ReminderView
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.TagChip
import com.bugu.ui.theme.H
import java.time.LocalDate
import java.time.YearMonth

/** 日历条目:提醒 + 方向。 */
private data class CalEntry(val r: ReminderView, val byMe: Boolean)

/**
 * 日历:我发出与收到的提醒按 fireAt 落到真实月历(周日起始),
 * 有惦记的日期打点,点选日期看当日列表;月份循环切换。
 */
@Composable
fun CalendarScreen(api: GeitaApi, bonds: List<BondDto>, onBack: () -> Unit) {
    var entries by remember { mutableStateOf<List<CalEntry>?>(null) } // null = 首载中
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }

    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        failed = false
        try {
            val mine = api.myReminders()
            val incoming = api.incomingReminders()
            entries = (mine.map { CalEntry(it, byMe = true) } + incoming.map { CalEntry(it, byMe = false) }).distinctBy { it.r.id }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }

    val all = entries.orEmpty()
    val byDate = remember(all) {
        buildMap {
            all.forEach { e ->
                parseIsoTime(e.r.fireAt)?.toLocalDate()?.let { d -> put(d, (get(d) ?: emptyList()) + e) }
            }
        }
    }
    val today = LocalDate.now()
    val monthEntries = remember(month, byDate) {
        byDate.filterKeys { YearMonth.from(it) == month }
    }
    val selectedList = remember(selectedDay, byDate) { byDate[selectedDay].orEmpty().sortedBy { it.r.fireAt } }
    val inMonth = monthEntries.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, H.Hairline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp), tint = H.Ink)
            }
            Spacer(Modifier.weight(1f))
            Text("日历", style = MaterialTheme.typography.titleLarge, color = H.Ink)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(38.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))

            if (failed) {
                EmptyState(title = "日历加载失败", subtitle = "检查网络后再试试", actionText = "重试", onAction = { retry++ })
            } else if (entries == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else {
                // 月份切换(循环)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                month = month.minusMonths(1)
                                selectedDay = today.takeIf { YearMonth.from(it) == month } ?: month.atDay(1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(18.dp), tint = H.Ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${month.year}年${month.monthValue}月 · ${if (inMonth) "共${monthEntries.size}天有安排" else "暂无安排"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = H.Ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                month = month.plusMonths(1)
                                selectedDay = today.takeIf { YearMonth.from(it) == month } ?: month.atDay(1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = H.Ink2)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 月历卡片
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 16.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                            Text(
                                w,
                                style = MaterialTheme.typography.titleSmall,
                                color = H.Ink3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 周日起始:周一偏移 1 格、周日偏移 0 格
                    val firstOffset = month.atDay(1).dayOfWeek.value % 7
                    val cells: List<Int?> = List(firstOffset) { null } + (1..month.lengthOfMonth()).toList()
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (day == null) {
                                        Spacer(Modifier.height(54.dp))
                                    } else {
                                        val date = month.atDay(day)
                                        DayCell(
                                            day = day,
                                            count = byDate[date]?.size ?: 0,
                                            isToday = date == today,
                                            selected = date == selectedDay,
                                            onClick = { selectedDay = date },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 当日安排
                val count = selectedList.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${month.monthValue}月${selectedDay.dayOfMonth}日",
                        style = MaterialTheme.typography.titleLarge,
                        color = H.Ink,
                    )
                    if (selectedDay == today) {
                        Spacer(Modifier.width(8.dp))
                        TagChip(text = "今天", tint = H.Coral)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (count == 0) "无安排" else "$count 项安排",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (count == 0) H.Ink3 else H.Coral,
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (count == 0) {
                    // 空态:咕咕睡觉
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White)
                            .padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GuguBird(GuguMood.SLEEP, Modifier.size(84.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("这天很清净", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                        Text("咕咕帮你盯着呢,放心休息", style = MaterialTheme.typography.bodySmall, color = H.Ink3)
                    }
                } else {
                    selectedList.forEachIndexed { i, e ->
                        val fire = parseIsoTime(e.r.fireAt)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                fire?.format(TIME_FMT) ?: "--:--",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = H.Ink,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.r.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = H.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${e.r.directionLabel(bonds, e.byMe)} · ${repeatLabel(e.r.repeatRule)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = H.Ink3,
                                )
                            }
                            TagChip(text = statusLabel(e.r.status), tint = statusColor(e.r.status))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, count: Int, isToday: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(when {
                    isToday -> H.Coral
                    selected -> H.Coral.copy(alpha = 0.12f)
                    else -> Color.Transparent
                })
                .border(
                    1.5.dp,
                    when {
                        isToday || selected -> Color.Transparent
                        count > 0 -> H.Hairline
                        else -> Color.Transparent
                    },
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isToday -> Color.White
                    selected -> H.Coral
                    else -> H.Ink
                },
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(count.coerceAtMost(3)) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isToday) Color.White else H.Coral),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
