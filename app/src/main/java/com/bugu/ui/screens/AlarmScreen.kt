package com.bugu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bugu.ui.common.CircleIcon
import com.bugu.ui.common.GuguBird
import com.bugu.ui.common.GuguMood
import com.bugu.ui.common.StatusAvatar
import com.bugu.ui.common.Waveform
import com.bugu.ui.theme.H
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** 波形占位(确定性伪随机):仅作视觉装饰,与真实音频无关。 */
private val DEFAULT_VOICE_WAVE = listOf(
    0.45f, 0.7f, 0.35f, 0.9f, 0.55f, 0.75f, 0.4f, 0.65f, 0.95f, 0.5f,
    0.7f, 0.3f, 0.85f, 0.6f, 0.45f, 0.75f, 0.35f, 0.6f, 0.5f, 0.4f,
    0.3f, 0.25f, 0.2f, 0.18f, 0.15f, 0.22f, 0.18f, 0.14f,
)

/**
 * 响铃屏(FR-07 全屏穿透):由 [com.bugu.ui.RingActivity] 在真实触发时承载,
 * 也可在应用内预览。数据全部来自触发上下文,不依赖假数据。
 */
@Composable
fun AlarmScreen(
    peerName: String,
    title: String,
    copyLine: String?,
    kindLabel: String,
    elderMode: Boolean,
    onConfirmed: () -> Unit,
    onSnoozed: () -> Unit,
) {
    // 响铃时钟从当前时刻起走;倒计时 40s 与升级链 T1 对齐(纯展示)
    var countdown by remember { mutableIntStateOf(40) }
    val now = remember { LocalTime.now() }
    var clockSec by remember { mutableIntStateOf(now.hour * 60 + now.minute) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (countdown > 0) countdown--
            clockSec++
        }
    }
    val clockText = "%02d:%02d".format(clockSec / 60 % 24, clockSec % 60)

    // 语音播放
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            delay(100)
            progress += 0.01f
            if (progress >= 1f) { progress = 0f; playing = false }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(H.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, H.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GuguBird(GuguMood.RING, Modifier.size(34.dp), onTap = {})
            }
            Text(
                kindLabel,
                style = MaterialTheme.typography.titleLarge,
                color = H.Ink,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 状态 chips
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(H.CoralTint)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Bolt, null, Modifier.size(14.dp), tint = H.Coral)
                Spacer(Modifier.width(5.dp))
                Text(
                    "咕咕正在叫你 · 00:%02d".format(countdown),
                    style = MaterialTheme.typography.titleSmall,
                    color = H.Coral,
                )
            }
            Spacer(Modifier.weight(1f))
            if (elderMode) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(H.IndigoTint)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Accessibility, null, Modifier.size(14.dp), tint = H.Indigo)
                    Spacer(Modifier.width(5.dp))
                    Text("长辈极简模式", style = MaterialTheme.typography.titleSmall, color = H.Indigo)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // 大时钟
        Text(
            clockText,
            style = MaterialTheme.typography.displayLarge,
            color = H.Ink,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            remember {
                val d = LocalDate.now()
                val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
                "${d.monthValue}月${d.dayOfMonth}日 星期$week"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = H.Ink2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // 叫醒卡片
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(1.dp, H.Hairline, RoundedCornerShape(28.dp))
                .padding(vertical = 22.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                StatusAvatar(
                    avatar = null,
                    size = 92.dp,
                    ring = H.Coral,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(H.Mint)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, Modifier.size(13.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(H.CoralTint)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("来自${peerName}的惦记", style = MaterialTheme.typography.titleSmall, color = H.CoralInk)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = H.Ink,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(14.dp))

        // 原声留言/叫醒语(copyLine 为空时整卡隐藏)
        if (copyLine != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, H.Hairline, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.RecordVoiceOver, null, Modifier.size(17.dp), tint = H.Coral)
                    Spacer(Modifier.width(6.dp))
                    Text("${peerName}的留言", style = MaterialTheme.typography.titleMedium, color = H.Ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "00:%02d / 00:15".format((progress * 15).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = H.Coral,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "“$copyLine”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = H.Ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(H.WarmFill)
                        .padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(
                        icon = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        bg = H.Coral,
                        tint = Color.White,
                        size = 38.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.clickable { playing = !playing },
                    )
                    Spacer(Modifier.width(12.dp))
                    Waveform(
                        bars = remember { DEFAULT_VOICE_WAVE },
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        playedRatio = progress,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // 右滑打卡
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(H.Track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "向右滑动：我已起床",
                style = MaterialTheme.typography.titleMedium,
                color = H.Ink2,
                modifier = Modifier.align(Alignment.Center),
            )
            SwipeConfirm(onComplete = onConfirmed)
        }

        Spacer(Modifier.height(10.dp))

        // 小憩
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(H.IndigoTint)
                .clickable(onClick = onSnoozed)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("小憩 15 分钟 · 等咕咕再叫你", style = MaterialTheme.typography.titleMedium, color = H.IndigoInk)
        }

        Spacer(Modifier.height(20.dp))

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, Modifier.size(15.dp), tint = H.Ink3)
            Spacer(Modifier.width(6.dp))
            Text(
                "确认后，咕咕会把回执告诉${peerName}。若 40 秒未确认，咕咕会再轻轻叫你一次。",
                style = MaterialTheme.typography.bodySmall,
                color = H.Ink3Strong,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// 右滑确认控件：拖动圆形把手滑到滑轨末端约 90% 处触发打卡
@Composable
private fun SwipeConfirm(onComplete: () -> Unit) {
    var dragX by remember { mutableFloatStateOf(0f) }
    // 滑轨宽度（pointerInput 内的 size 是把手自身尺寸，不能用）
    var trackWidth by remember { mutableIntStateOf(1) }
    // 触发后本次手势不再重复回调（拖动中每帧都可能越线）
    var done by remember { mutableStateOf(false) }
    val handlePx = with(LocalDensity.current) { 52.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .onSizeChanged { trackWidth = it.width },
    ) {
        val maxDist = (trackWidth - handlePx).coerceAtLeast(1f)
        Box(
            Modifier
                .offset { IntOffset(dragX.toInt().coerceAtLeast(0), 0) }
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(H.Mint, Color(0xFF0DA271))))
                .pointerInput(maxDist) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragX < maxDist * 0.6f) dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                    ) { change, amount ->
                        change.consume()
                        if (!done) {
                            dragX = (dragX + amount.x).coerceIn(0f, maxDist)
                            if (dragX >= maxDist * 0.92f) {
                                done = true
                                dragX = 0f
                                onComplete()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                null,
                Modifier.size(26.dp),
                tint = Color.White,
            )
        }
    }
}
