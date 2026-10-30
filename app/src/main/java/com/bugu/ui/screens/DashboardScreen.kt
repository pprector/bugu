package com.bugu.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugu.client.api.AckRequest
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.client.api.ReminderView
import com.bugu.client.domain.EscalationCountdown
import com.bugu.client.domain.ReminderDisplay
import com.bugu.client.domain.ReminderDisplayState
import com.bugu.ui.common.EmptyState
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.RiseIn
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.TagChip
import com.bugu.ui.common.pulseScale
import com.bugu.ui.theme.H
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ISO 本地时刻 → LocalDateTime(服务端偶发带时区偏移时兜底换算)。 */
internal fun parseIsoTime(iso: String): LocalDateTime? = try {
    LocalDateTime.parse(iso)
} catch (_: Exception) {
    try {
        OffsetDateTime.parse(iso).toLocalDateTime()
    } catch (_: Exception) {
        null
    }
}

internal val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 相对日期标签:今天/昨天/明天,其余带星期,避免列表只看到 HH:mm 分不清哪一天。 */
private fun relativeDayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    today.plusDays(1) -> "明天"
    else -> {
        val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
        "${d.monthValue}月${d.dayOfMonth}日 周$week"
    }
}

/** 提醒状态 → 彩色小标签文案(History 同源,保持全局一致)。 */
internal fun statusLabel(status: String): String = when (status) {
    "PENDING" -> "待触发"
    "FIRED" -> "已触发"
    "ACKED" -> "已完成"
    "SNOOZED" -> "稍后再叫"
    "DECLINED" -> "已拒绝"
    "MISSED" -> "已错过"
    "CANCELED" -> "已撤销"
    else -> status
}

/** 提醒状态 → 标签色:PENDING 蓝 / FIRED 橙 / ACKED 绿 / SNOOZED 黄 / DECLINED 红 / MISSED·CANCELED 灰(深色变体保证可读)。 */
internal fun statusColor(status: String): Color = when (status) {
    "PENDING" -> H.IndigoInk
    "FIRED" -> H.CoralInk
    "ACKED" -> H.MintInk
    "SNOOZED" -> H.AmberInk
    "DECLINED" -> H.RoseInk
    "MISSED", "CANCELED" -> H.Ink3Strong
    else -> H.Ink2
}

/** 提醒类型 → 中文短标。 */
internal fun kindLabel(kind: String): String = when (kind) {
    "ONCE" -> "单次惦记"
    "REPEAT" -> "循环惦记"
    "MUTUAL_ALARM" -> "互叫闹钟"
    else -> kind
}

/** 语气档位 → 中文(server ToneLevel:GENTLE/NAG/STRICT)。 */
internal fun toneLabel(tone: String): String = when (tone) {
    "GENTLE" -> "轻柔语气"
    "NAG" -> "唠叨语气"
    "STRICT" -> "严格语气"
    else -> tone
}

/** 重复规则 → 中文(最小表达:DAILY / WEEKLY:MON,TUE…)。 */
internal fun repeatLabel(rule: String?): String = when {
    rule == null -> "仅一次"
    rule == "DAILY" -> "每天重复"
    rule.startsWith("WEEKLY:") -> "每周 " + rule.removePrefix("WEEKLY:").split(",")
        .joinToString(" ") { day -> day.take(1) + day.drop(1).lowercase() }
    else -> rule
}

/**
 * 看板:我发出的「我的惦记」与收到的「TA 的惦记」双列表 + 进行中提醒卡。
 * 首页只展示待响应的惦记,已结束统一引导到历史;数据来自服务端(mine/incoming/current),
 * 撤销与三出口确认直连 API,操作后整页重拉。
 */
@Composable
fun DashboardScreen(
    api: GeitaApi,
    peerName: String,
    onOpenHistory: () -> Unit,
    onOpenCalendar: () -> Unit,
    onGoCreate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var my by remember { mutableStateOf<List<ReminderView>?>(null) } // null = 首载中
    var incoming by remember { mutableStateOf<List<ReminderView>?>(null) }
    var current by remember { mutableStateOf<ReminderView?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var tabAutoChosen by remember { mutableStateOf(false) } // 首载后仅自动落位一次,不打断用户后续切换
    var notice by remember { mutableStateOf<String?>(null) } // 操作成功回执
    var error by remember { mutableStateOf<String?>(null) } // 网络或业务错误
    var busyIds by remember { mutableStateOf(setOf<Long>()) } // 操作中的提醒,按钮防重
    var refreshing by remember { mutableStateOf(false) } // 头部刷新入口的可见反馈
    // 秒级时钟:驱动头部时间与进行中卡的升级链倒计时
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }

    /** 进入/操作后统一重拉三路数据。 */
    fun refresh() {
        refreshing = true
        scope.launch {
            try {
                val mine = api.myReminders()
                val recd = api.incomingReminders()
                val cur = api.currentReminder()
                my = mine
                incoming = recd
                current = cur
                error = null
            } catch (e: GeitaApiException) {
                error = e.message ?: "网络不佳,请稍后再试"
            } catch (_: Exception) {
                error = "网络不佳,请稍后再试"
            } finally {
                refreshing = false
            }
        }
    }

    /** 撤销我发起的 PENDING 惦记。 */
    fun cancel(r: ReminderView) {
        if (r.id in busyIds) return
        scope.launch {
            busyIds = busyIds + r.id
            try {
                api.cancelReminder(r.id)
                notice = "已撤销「${r.title}」"
                refresh()
            } catch (e: GeitaApiException) {
                error = e.message ?: "撤销失败,请稍后再试"
            } catch (_: Exception) {
                error = "网络不佳,请稍后再试"
            } finally {
                busyIds = busyIds - r.id
            }
        }
    }

    /** 收到的惦记三出口:确认完成 / 稍后 15 分钟 / 无法完成(归因忙碌)。 */
    fun ack(r: ReminderView, action: String) {
        val fireId = r.fireId ?: return
        if (r.id in busyIds) return
        scope.launch {
            busyIds = busyIds + r.id
            try {
                api.ack(
                    AckRequest(
                        fireId = fireId,
                        action = action,
                        reasonCode = if (action == "DECLINE") "BUSY" else null,
                        snoozeMinutes = if (action == "SNOOZE") 15 else null,
                    ),
                )
                notice = if (action == "DONE") "已完成「${r.title}」" else if (action == "SNOOZE") "15 分钟后咕咕再叫你" else "已告诉${peerName}这会儿不方便"
                refresh()
            } catch (e: GeitaApiException) {
                error = e.message ?: "操作失败,请稍后再试"
            } catch (_: Exception) {
                error = "网络不佳,请稍后再试"
            } finally {
                busyIds = busyIds - r.id
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // 派生数据:首页只处理「仍待响应」的惦记,已结束统一进历史
    val myList = my.orEmpty().sortedBy { it.fireAt }
    val incomingList = incoming.orEmpty().sortedBy { it.fireAt }
    val myActive = myList.filter { ReminderDisplay.actionable(it.status) }
    val incomingActive = incomingList.filter { ReminderDisplay.actionable(it.status) }
    val activeAll = myActive + incomingActive
    val endedCount = (myList.size + incomingList.size) - activeAll.size
    val loading = my == null || incoming == null
    val today = LocalDate.now()
    val todayCount = activeAll.count { parseIsoTime(it.fireAt)?.toLocalDate() == today }
    // 「未来 7 天」:今天起 7 天内的待响应惦记(含已触发待确认)
    val weekCount = activeAll.count { r ->
        val d = parseIsoTime(r.fireAt)?.toLocalDate()
        d != null && !d.isBefore(today) && d.isBefore(today.plusDays(7))
    }
    val weekDays = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }
    val dayDot = remember(myActive, incomingActive) {
        activeAll.mapNotNull { parseIsoTime(it.fireAt)?.toLocalDate() }.toSet()
    }
    // 进行中卡片:仅展示仍可响应的当前提醒
    val live = current?.takeIf { ReminderDisplay.stateOf(it.status) == ReminderDisplayState.WAITING }

    // 首载完成后:有待回应就落在「TA 的惦记」,否则回「我的惦记」;只自动一次
    LaunchedEffect(my, incoming) {
        if (!tabAutoChosen && my != null && incoming != null) {
            tab = if (incoming.orEmpty().any { ReminderDisplay.actionable(it.status) && it.fireId != null }) 1 else 0
            tabAutoChosen = true
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶部标题:咕咕生活位 + 时辰问候
        item {
            val hour = now.hour
            val mood = when (hour) {
                in 5..10 -> GuguMood.RING
                in 11..13 -> GuguMood.VOICE
                in 14..21 -> GuguMood.CHEER
                else -> GuguMood.SLEEP
            }
            val greet = when (hour) {
                in 5..10 -> "早安"
                in 11..12 -> "午安"
                in 13..17 -> "下午好"
                in 18..22 -> "晚上好"
                else -> "夜深了"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuguBird(
                    mood = mood,
                    modifier = Modifier.size(52.dp),
                    onTap = { refresh() }, // 轻点咕咕即可整页刷新
                    pokeLines = listOf("咕咕!", "布谷布谷～", "今天也要布谷布谷哦!", "在呢在呢～", "别忘了惦记你的人呀"),
                    bubbleOffset = 54.dp, // 鸟贴近屏幕顶部,气泡放到下方
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(greet, style = MaterialTheme.typography.headlineSmall, color = H.Ink)
                        Spacer(Modifier.width(4.dp))
                        Text("✦", style = MaterialTheme.typography.titleMedium, color = H.Coral)
                    }
                    val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[today.dayOfWeek.value - 1]
                    Text(
                        "${today.monthValue}月${today.dayOfMonth}日 周$week · 今日 $todayCount 份惦记",
                        style = MaterialTheme.typography.bodySmall,
                        color = H.Ink3Strong,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeaderIconButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "刷新看板",
                        loading = refreshing,
                        onClick = { refresh() },
                    )
                    HeaderIconButton(
                        icon = Icons.Outlined.History,
                        contentDescription = "惦记历史",
                        onClick = onOpenHistory,
                    )
                }
            }
        }

        // 轻提示:成功回执(绿)与错误(红)互斥展示
        item {
            AnimatedVisibility(visible = notice != null || error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (error != null) H.RoseTint else H.MintTint)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error ?: notice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) H.RoseInk else H.MintInk,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clickable { error = null; notice = null }
                            .padding(start = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("知道了", style = MaterialTheme.typography.titleSmall, color = H.Ink2)
                    }
                }
            }
        }

        // 时钟/周历/分段切换常驻,无数据时仅在列表区降级为首用空态,不再整页隐藏
        // 进行中的惦记:升级链倒计时(T1/T2 与服务端基准一致)
        if (live != null) {
            item {
                val fireInstant = remember(live.fireAt) {
                    parseIsoTime(live.fireAt)
                        ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()
                        ?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it.toEpochMilli()) }
                }
                val nowInstant = remember(now) {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis())
                }
                val stage = fireInstant?.let { EscalationCountdown.stageAt(it, nowInstant) }
                    ?: EscalationCountdown.Stage.BEFORE_T1
                val remain = fireInstant
                    ?.let { EscalationCountdown.remainingToNext(it, nowInstant) }
                    ?.let { d -> "${d.inWholeMinutes} 分 ${(d.inWholeSeconds % 60).coerceAtLeast(0)} 秒" }
                    .orEmpty()
                val stageText = when (stage) {
                    EscalationCountdown.Stage.BEFORE_T1 -> "咕咕守着呢"
                    EscalationCountdown.Stage.AFTER_T1 -> "已再推一次"
                    EscalationCountdown.Stage.AFTER_T2 -> "已通知$peerName"
                    EscalationCountdown.Stage.CLOSED -> "今日收口"
                }
                RiseIn {
                    val heroShape = RoundedCornerShape(24.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(heroShape)
                            .background(H.CoralTint)
                            .border(1.dp, H.Coral.copy(alpha = 0.38f), heroShape)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp), tint = H.Coral)
                            Spacer(Modifier.width(6.dp))
                            Text("进行中的惦记", style = MaterialTheme.typography.titleMedium, color = H.CoralInk)
                            Spacer(Modifier.weight(1f))
                            TagChip(text = stageText, tint = H.Coral, solid = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            live.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = H.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                val fireDate = parseIsoTime(live.fireAt)?.toLocalDate()
                                append("来自$peerName · ")
                                if (fireDate != null) append(relativeDayLabel(fireDate, today)).append(" ")
                                append(parseIsoTime(live.fireAt)?.format(TIME_FMT) ?: live.fireAt)
                                if (stage != EscalationCountdown.Stage.CLOSED) append(" · 距下次呼唤 $remain")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                        // 三出口直接放在进行中卡上,不必切 Tab 再找同一条
                        if (stage != EscalationCountdown.Stage.CLOSED &&
                            ReminderDisplay.actionable(live.status) && live.fireId != null
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AckChip("完成了", H.MintInk, enabled = live.id !in busyIds) { ack(live, "DONE") }
                                AckChip("稍后 15 分", H.IndigoInk, enabled = live.id !in busyIds) { ack(live, "SNOOZE") }
                                AckChip("无法完成", H.RoseInk, enabled = live.id !in busyIds) { ack(live, "DECLINE") }
                            }
                        }
                    }
                }
            }
        }

        // 时钟卡:保留品牌的大时间视觉,但状态改为真实同步态
        item {
            RiseIn(delayMs = 40) {
                val syncText = when {
                    refreshing -> "同步中…"
                    error != null -> "同步失败"
                    else -> "惦记同步就绪"
                }
                val syncBg = when {
                    refreshing -> H.IndigoTint
                    error != null -> H.RoseTint
                    else -> H.MintTint
                }
                val syncTint = when {
                    refreshing -> H.IndigoInk
                    error != null -> H.RoseInk
                    else -> H.MintInk
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(syncBg)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("●", style = MaterialTheme.typography.labelSmall, color = syncTint)
                            Spacer(Modifier.width(5.dp))
                            Text(syncText, style = MaterialTheme.typography.titleSmall, color = syncTint)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "布谷替你守着时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%02d:%02d".format(now.hour, now.minute),
                            style = MaterialTheme.typography.displayLarge,
                            color = H.Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Alarm, null, Modifier.size(16.dp), tint = H.Mint)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "未来 7 天 $weekCount 份惦记",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink2,
                            )
                        }
                    }
                }
            }
        }

        // 分段切换(白色指示条平滑滑动)
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(H.GreyTint)
                    .border(1.dp, H.Hairline, RoundedCornerShape(999.dp))
                    .padding(4.dp),
            ) {
                val frac by animateFloatAsState(
                    targetValue = if (tab == 0) 0f else 1f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "segIndicator",
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { translationX = frac * size.width / 2f }
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White),
                )
                Row(Modifier.fillMaxWidth()) {
                    SegTab("我的惦记 (${myActive.size})", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    SegTab("TA 的惦记 (${incomingActive.size})", tab == 1, Modifier.weight(1f)) { tab = 1 }
                }
            }
        }

        // 首载占位
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = H.Coral, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
        } else if (myList.isEmpty() && incomingList.isEmpty() && current == null) {
            // 真正无数据才展示首用空态;大字关怀模式、已结束历史都不会再把看板清空
            item {
                EmptyState(
                    title = "还没有任何惦记",
                    subtitle = "设一条惦记,让咕咕替你记挂重要的人",
                    actionText = "设第一条惦记",
                    onAction = onGoCreate,
                )
            }
        } else if (tab == 0) {
            if (myActive.isEmpty()) {
                val hasEnded = myList.isNotEmpty()
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (hasEnded) "没有待处理的惦记" else "你还没有发出惦记",
                            style = MaterialTheme.typography.titleMedium,
                            color = H.Ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (hasEnded) "已结束的惦记都收在历史里,可随时回看"
                            else "设一条惦记,到点咕咕替你呼唤${peerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                }
            }
            items(myActive.size) { i ->
                val r = myActive[i]
                RiseIn(delayMs = i * 50, key = tab) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: "--:--",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                color = H.Ink,
                            )
                            Text(
                                parseIsoTime(r.fireAt)?.toLocalDate()?.let { relativeDayLabel(it, today) } ?: "日期未知",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3Strong,
                            )
                            Spacer(Modifier.height(3.dp))
                            TagChip(text = statusLabel(r.status), tint = statusColor(r.status))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = H.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${kindLabel(r.kind)} · ${repeatLabel(r.repeatRule)} · ${toneLabel(r.tone)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = H.Ink3Strong,
                            )
                        }
                        if (r.status == "PENDING") {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (r.id in busyIds) H.GreyTint else H.WarmFill)
                                    .clickable(enabled = r.id !in busyIds) { cancel(r) }
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (r.id in busyIds) "撤销中…" else "撤销",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = H.Ink2,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (incomingActive.isEmpty()) {
                val hasEnded = incomingList.isNotEmpty()
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (hasEnded) "没有待回应的惦记" else "${peerName}还没给你设惦记",
                            style = MaterialTheme.typography.titleMedium,
                            color = H.Ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (hasEnded) "TA 的呼唤都已处理完,历史里可回看"
                            else "TA 的呼唤会出现在这里,记得回应哦",
                            style = MaterialTheme.typography.bodySmall,
                            color = H.Ink3Strong,
                        )
                    }
                }
            }
            items(incomingActive.size) { i ->
                val r = incomingActive[i]
                val canRespond = ReminderDisplay.actionable(r.status) && r.fireId != null
                RiseIn(delayMs = i * 50, key = tab) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusAvatar(avatar = null, size = 44.dp, fallbackText = peerName.take(1))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        r.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = H.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    TagChip(text = kindLabel(r.kind), tint = if (r.kind == "MUTUAL_ALARM") H.Coral else H.Indigo)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    buildString {
                                        parseIsoTime(r.fireAt)?.toLocalDate()?.let {
                                            append(relativeDayLabel(it, today)).append(" ")
                                        }
                                        append(parseIsoTime(r.fireAt)?.format(TIME_FMT) ?: r.fireAt)
                                        append(" · ").append(toneLabel(r.tone))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = H.Ink3Strong,
                                )
                            }
                            TagChip(text = statusLabel(r.status), tint = statusColor(r.status))
                        }
                        // 三出口:仅待响应且有可操作 fire 时出现
                        if (canRespond) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AckChip("完成了", H.MintInk, enabled = r.id !in busyIds) { ack(r, "DONE") }
                                AckChip("稍后 15 分", H.IndigoInk, enabled = r.id !in busyIds) { ack(r, "SNOOZE") }
                                AckChip("无法完成", H.RoseInk, enabled = r.id !in busyIds) { ack(r, "DECLINE") }
                            }
                        }
                    }
                }
            }
        }

        // 已结束的惦记不堆在首页列表里,统一引导到历史回执
        if (endedCount > 0) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenHistory)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$endedCount 条惦记已结束",
                        style = MaterialTheme.typography.titleSmall,
                        color = H.Ink2,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("查看历史", style = MaterialTheme.typography.titleSmall, color = H.CoralInk)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        Modifier.size(16.dp),
                        tint = H.CoralInk,
                    )
                }
            }
        }

        // 日程周历:保留一周时间轴的点阵视觉,整卡可点进日历
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenCalendar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Alarm, null, Modifier.size(18.dp), tint = H.Coral)
                Spacer(Modifier.width(6.dp))
                Text("日程周历", style = MaterialTheme.typography.titleLarge, color = H.Ink)
                Spacer(Modifier.weight(1f))
                Text("未来 7 天 $weekCount 份", style = MaterialTheme.typography.titleSmall, color = H.CoralInk)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    Modifier.size(18.dp),
                    tint = H.Coral,
                )
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenCalendar)
                    .semantics { contentDescription = "查看未来 7 天日程" }
                    .padding(vertical = 14.dp, horizontal = 8.dp),
            ) {
                weekDays.forEach { d ->
                    val isToday = d == today
                    val hasEvent = d in dayDot
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1],
                            style = MaterialTheme.typography.titleSmall,
                            color = H.Ink3Strong,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(40.dp)
                                .then(if (isToday) Modifier.pulseScale(max = 1.04f) else Modifier)
                                .clip(CircleShape)
                                .background(if (isToday) H.CoralInk else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (!isToday && hasEvent) H.Hairline else Color.Transparent,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isToday) Color.White else H.Ink,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (hasEvent) H.Coral else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

/** 头部圆形入口:统一 44dp 触控区,并给图标补齐无障碍描述。 */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, H.Hairline, CircleShape)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = H.CoralInk,
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription, Modifier.size(20.dp), tint = H.Ink2)
        }
    }
}

/** 三出口操作胶囊(色彩随动作语义变化;忙态不可点且视觉降权)。 */
@Composable
private fun AckChip(text: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = if (enabled) 0.10f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = tint.copy(alpha = if (enabled) 1f else 0.55f))
    }
}

@Composable
private fun SegTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // 选中色渐变过渡;白色胶囊底由外层滑动指示条提供
    val tint by animateColorAsState(if (selected) H.CoralInk else H.Ink2, tween(200), label = "segTint")
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}
