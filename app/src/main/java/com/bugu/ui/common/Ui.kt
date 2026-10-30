package com.bugu.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.bugu.ui.theme.H
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 头像：圆形裁剪 + 状态环 + 底部状态点
@Composable
fun StatusAvatar(
    avatar: Int?,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    dotColor: Color? = null,
    fallbackText: String? = null,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                .background(H.Hairline),
        ) {
            if (avatar != null) {
                Image(
                    painter = painterResource(avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        fallbackText ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = H.Ink2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.26f)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border((size.value * 0.05f).dp, H.Canvas, CircleShape),
            )
        }
    }
}

// 语音波形条（自适应宽度，不溢出画布）
@Composable
fun Waveform(
    bars: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = H.Coral,
    dimColor: Color = H.Coral.copy(alpha = 0.35f),
    playedRatio: Float = 1f,
) {
    Canvas(modifier = modifier) {
        val n = bars.size.coerceAtLeast(1)
        val slot = size.width / n
        val barWidth = minOf(4.dp.toPx(), slot * 0.55f)
        bars.forEachIndexed { i, amp ->
            val h = size.height * (0.25f + 0.75f * amp)
            val x = i * slot + (slot - barWidth) / 2f
            val c = if (i.toFloat() / n <= playedRatio) color else dimColor
            drawRoundRect(
                color = c,
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

// 语义标签胶囊:浅色底用深色文字,实底用白字,保证 13sp 标签的对比度
@Composable
fun TagChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    icon: ImageVector? = null,
) {
    val content = accessibleTagColor(tint)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) content else tint.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (solid) Color.White else content)
            Box(Modifier.size(3.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (solid) Color.White else content,
        )
    }
}

/** 品牌亮色 → 可读的深色文字变体;已深色/自定义色原样返回。 */
private fun accessibleTagColor(tint: Color): Color = when (tint) {
    H.Coral, H.CoralLight -> H.CoralInk
    H.Indigo, H.IndigoLight -> H.IndigoInk
    H.Mint -> H.MintInk
    H.Rose -> H.RoseInk
    H.Ink3 -> H.Ink3Strong
    else -> tint
}

// 圆形图标按钮
@Composable
fun CircleIcon(
    icon: ImageVector,
    bg: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    border: Color? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

// 字段级错误:就近内联显示在对应输入下方,避免远端红字被键盘遮挡
@Composable
fun FieldError(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        Text(
            text = message.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = H.Rose,
        )
    }
}

// 品牌渐变主按钮
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(H.CoralLight, H.Coral)))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// 通用空态：睡觉的咕咕 + 引导文案 + 可选行动按钮
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GuguBird(
            GuguMood.SLEEP,
            Modifier.size(104.dp),
            onTap = {},
            pokeLines = listOf("咕咕…这里空空的", "等你来设第一个惦记"),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = H.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = H.Ink3,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.7f),
                height = 46.dp,
            )
        }
    }
}

// 入场动效：淡入 + 轻微上浮；delayMs 用于卡片错峰，key 变化时重播
@Composable
fun RiseIn(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    key: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(key) {
        appear.snapTo(0f)
        delay(delayMs.toLong())
        appear.animateTo(1f, tween(380, easing = LinearOutSlowInEasing))
    }
    Box(
        modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * 18.dp.toPx()
        },
        content = content,
    )
}

// 轻微脉动缩放：用于「今天」等需要呼吸感的强调元素
@Composable
fun Modifier.pulseScale(max: Float = 1.07f, durationMs: Int = 1400): Modifier {
    val t = rememberInfiniteTransition(label = "pulse")
    val s = t.animateFloat(
        initialValue = 1f,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )
    return graphicsLayer { scaleX = s.value; scaleY = s.value }
}

// 品牌色开关
@Composable
fun BrandSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedColor: Color = H.Coral,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = checkedColor,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = H.Track,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

// 组头：标题 + 右侧内容
@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = H.Ink, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

// ---- 吉祥物：咕咕（报时小布谷）----
enum class GuguMood { SLEEP, RING, VOICE, CHEER }

private val GuguBelly = Color(0xFFFFF3EC)
private val GuguDark = Color(0xFFE0523F)
private val GuguBeak = Color(0xFFFFB020)
private val GuguBeakDark = Color(0xFFF59E0B)
private val GuguBlush = Color(0xFFFFB8A8)
private val GuguSpark = Color(0xFFF2C879)

// 咕咕：珊瑚色小布谷鸟，四种状态（Canvas 矢量绘制，随尺寸缩放）
// 点击交互：压扁-弹起 + 振翅 + 轻震反馈；pokeLines 非空时头顶冒气泡台词
// 待机细节：呼吸起伏、定期眨眼、叫醒声波/捎话音条脉冲、睡觉 zZ 飘动、开心星光闪烁
@Composable
fun GuguBird(
    mood: GuguMood,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    pokeLines: List<String> = emptyList(),
    bubbleOffset: Dp = (-12).dp,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val squash = remember { Animatable(1f) }
    val flap = remember { Animatable(0f) }
    var bubble by remember { mutableStateOf<String?>(null) }

    // 情绪切换弹跳：切到新情绪时从 0.85 弹回 1（带回弹过冲），像被轻轻「点醒」
    val moodPop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        moodPop.snapTo(0.85f)
        moodPop.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    // 待机呼吸：睡觉时更深更慢
    val breath = rememberInfiniteTransition(label = "guguBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = if (mood == GuguMood.SLEEP) 2400 else 1600, easing = LinearEasing),
        ),
        label = "breath",
    )
    // 睁眼状态每 3.4s 眨一次眼（线性循环，尾段 ~120ms 为闭眼窗口）
    val blink = rememberInfiniteTransition(label = "guguBlink").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3400, easing = LinearEasing)),
        label = "blink",
    )
    val blinking = (mood == GuguMood.RING || mood == GuguMood.VOICE) && blink.value > 0.965f

    fun poke() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onTap?.invoke()
        scope.launch {
            launch {
                squash.animateTo(0.84f, tween(70))
                squash.animateTo(1.07f, tween(130))
                squash.animateTo(0.97f, tween(90))
                squash.animateTo(1f, tween(130))
            }
            launch {
                flap.animateTo(1f, tween(90))
                delay(430)
                flap.animateTo(0f, tween(150))
            }
        }
        if (pokeLines.isNotEmpty()) {
            scope.launch {
                val line = pokeLines.random()
                bubble = line
                delay(1000)
                if (bubble == line) bubble = null
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sy = squash.value * (1f + breath.value * if (mood == GuguMood.SLEEP) 0.03f else 0.014f)
                    val pop = moodPop.value
                    scaleX = (1f + (1f - sy) * 0.9f) * pop
                    scaleY = sy * pop
                    translationY = -breath.value * (if (mood == GuguMood.SLEEP) 1.6f else 0.8f).dp.toPx()
                }
                .then(
                    if (onTap != null) {
                        Modifier.pointerInput(onTap, pokeLines) { detectTapGestures { poke() } }
                    } else {
                        Modifier
                    },
                ),
        ) {
            drawGugu(
                mood = mood,
                breath = breath.value,
                blinking = blinking,
                flap = flap.value,
            )
        }
        AnimatedVisibility(
            visible = bubble != null,
            enter = scaleIn(initialScale = 0.6f, animationSpec = tween(110)) + fadeIn(tween(110)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = bubbleOffset)
                .wrapContentSize(Alignment.TopCenter, unbounded = true),
        ) {
            Text(
                bubble ?: "",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = H.Coral,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(999.dp))
                    .border(1.dp, H.Coral.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun DrawScope.drawGugu(mood: GuguMood, breath: Float, blinking: Boolean, flap: Float) {
    val r = minOf(size.width, size.height) / 2.9f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val wave = sin(breath * PI).toFloat() // 0→1→0 平滑波

    // 头顶呆毛
    drawCircle(GuguDark, r * 0.12f, Offset(cx - r * 0.14f, cy - r * 1.06f))
    drawCircle(GuguDark, r * 0.15f, Offset(cx + r * 0.08f, cy - r * 1.14f))

    // 翅膀：睡觉收拢 / 叫醒右翅 / 捎话左翅 / 开心双翅；点按时双翅在情绪姿态上平滑抬起振翅
    val lift = flap.coerceIn(0f, 1f)
    val (baseL, baseR) = when (mood) {
        GuguMood.SLEEP -> 0f to 0f
        GuguMood.RING -> 0f to 1f
        GuguMood.VOICE -> 1f to 0f
        GuguMood.CHEER -> 1f to 1f
    }
    val lL = maxOf(baseL, lift)
    val lR = maxOf(baseR, lift)
    wing(cx - r * 0.95f, lerp(cy + r * 0.22f, cy - r * 0.48f, lL), lerp(-18f, -35f, lL), r)
    wing(cx + r * 0.95f, lerp(cy + r * 0.22f, cy - r * 0.48f, lR), lerp(18f, 35f, lR), r)

    // 身体 + 肚皮
    drawCircle(H.Coral, r, Offset(cx, cy))
    drawOval(
        GuguBelly,
        topLeft = Offset(cx - r * 0.56f, cy - r * 0.19f),
        size = Size(r * 1.12f, r * 0.94f),
    )

    // 肚皮上的小心心
    val hy = cy + r * 0.28f
    drawCircle(H.Coral, r * 0.105f, Offset(cx - r * 0.085f, hy))
    drawCircle(H.Coral, r * 0.105f, Offset(cx + r * 0.085f, hy))
    drawPath(
        Path().apply {
            moveTo(cx - r * 0.185f, hy + r * 0.02f)
            lineTo(cx + r * 0.185f, hy + r * 0.02f)
            lineTo(cx, hy + r * 0.34f)
            close()
        },
        H.Coral,
    )

    // 眼睛：睁眼（会眨眼），睡觉 ︶，开心 ∩
    val eyeY = cy - r * 0.24f
    if ((mood == GuguMood.RING || mood == GuguMood.VOICE) && !blinking) {
        listOf(-0.31f, 0.31f).forEach { dx ->
            drawCircle(H.Ink, r * 0.085f, Offset(cx + r * dx, eyeY))
            drawCircle(Color.White, r * 0.03f, Offset(cx + r * dx + r * 0.03f, eyeY - r * 0.03f))
        }
    } else {
        val start = if (mood == GuguMood.SLEEP || blinking) 25f else 205f
        listOf(-0.31f, 0.31f).forEach { dx ->
            drawArc(
                H.Ink, start, 130f, false,
                topLeft = Offset(cx + r * dx - r * 0.18f, eyeY - r * 0.15f),
                size = Size(r * 0.36f, r * 0.30f),
                style = Stroke(width = r * 0.06f, cap = StrokeCap.Round),
            )
        }
    }

    // 嘴：叫醒时张嘴；捎话时随呼吸一张一合（像在说话）；其余闭嘴
    if (mood == GuguMood.RING) {
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.02f)
                lineTo(cx + r * 0.18f, cy - r * 0.02f)
                lineTo(cx, cy + r * 0.08f)
                close()
            },
            GuguBeak,
        )
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.11f, cy + r * 0.10f)
                lineTo(cx + r * 0.11f, cy + r * 0.10f)
                lineTo(cx, cy + r * 0.24f)
                close()
            },
            GuguBeakDark,
        )
    } else {
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.16f, cy)
                lineTo(cx + r * 0.16f, cy)
                lineTo(cx, cy + r * 0.20f)
                close()
            },
            GuguBeak,
        )
        if (mood == GuguMood.VOICE && wave > 0.45f) {
            drawPath(
                Path().apply {
                    moveTo(cx - r * 0.10f, cy + r * 0.14f)
                    lineTo(cx + r * 0.10f, cy + r * 0.14f)
                    lineTo(cx, cy + r * (0.16f + 0.12f * wave))
                    close()
                },
                GuguBeakDark,
            )
        }
    }

    // 腮红
    listOf(-0.50f, 0.50f).forEach { dx ->
        drawOval(
            GuguBlush,
            topLeft = Offset(cx + r * dx - r * 0.15f, cy + r * 0.10f - r * 0.095f),
            size = Size(r * 0.30f, r * 0.19f),
        )
    }

    // 状态配件（随呼吸脉动）
    when (mood) {
        GuguMood.RING -> {
            val acx = cx + r * 0.50f
            val acy = cy - r * 0.75f
            listOf(0.20f, 0.34f).forEach { rr ->
                drawArc(
                    H.Ink, -80f, 100f, false,
                    topLeft = Offset(acx - r * rr, acy - r * rr),
                    size = Size(r * rr * 2f, r * rr * 2f),
                    alpha = 0.4f + 0.6f * wave,
                    style = Stroke(width = r * 0.05f, cap = StrokeCap.Round),
                )
            }
        }
        GuguMood.VOICE -> {
            val by = cy - r * 0.78f
            listOf(0.26f to 0f, 0.44f to 0.17f, 0.34f to 0.34f).forEachIndexed { i, (h, dx) ->
                val hh = h * (0.72f + 0.42f * sin(breath * PI + i * 0.85).toFloat()).coerceAtLeast(0.30f)
                drawRoundRect(
                    H.Ink,
                    topLeft = Offset(cx + r * 0.42f + r * dx, by - r * hh / 2f),
                    size = Size(r * 0.08f, r * hh),
                    cornerRadius = CornerRadius(r * 0.04f),
                )
            }
        }
        GuguMood.CHEER -> {
            val s = 1f + 0.12f * wave
            val fx = cx + r * 0.66f
            val fy = cy - r * 0.92f
            drawCircle(H.Coral, r * 0.085f * s, Offset(fx - r * 0.07f * s, fy))
            drawCircle(H.Coral, r * 0.085f * s, Offset(fx + r * 0.07f * s, fy))
            drawPath(
                Path().apply {
                    moveTo(fx - r * 0.15f * s, fy + r * 0.02f)
                    lineTo(fx + r * 0.15f * s, fy + r * 0.02f)
                    lineTo(fx, fy + r * 0.27f * s)
                    close()
                },
                H.Coral,
            )
            spark(cx - r * 0.66f, cy - r * 0.82f, r * 0.16f * s)
            spark(cx - r * 0.48f, cy - r * 1.02f, r * 0.10f * s)
        }
        GuguMood.SLEEP -> {
            val za = 1f - wave * 0.55f
            drawPath(
                Path().apply { zzPath(cx - r * 0.62f, cy - r * 0.86f - wave * r * 0.16f, r * 0.15f) },
                GuguSpark.copy(alpha = za),
            )
            drawPath(
                Path().apply { zzPath(cx + r * 0.58f, cy - r * 0.98f - wave * r * 0.10f, r * 0.10f) },
                GuguSpark.copy(alpha = za),
            )
        }
    }
}

private fun DrawScope.wing(x: Float, y: Float, rotation: Float, r: Float) {
    rotate(rotation, Offset(x, y)) {
        drawOval(
            GuguDark,
            topLeft = Offset(x - r * 0.21f, y - r * 0.45f),
            size = Size(r * 0.42f, r * 0.90f),
        )
    }
}

private fun DrawScope.spark(x: Float, y: Float, f: Float) {
    drawPath(
        Path().apply {
            moveTo(x, y - f)
            lineTo(x + f * 0.28f, y - f * 0.28f)
            lineTo(x + f, y)
            lineTo(x + f * 0.28f, y + f * 0.28f)
            lineTo(x, y + f)
            lineTo(x - f * 0.28f, y + f * 0.28f)
            lineTo(x - f, y)
            lineTo(x - f * 0.28f, y - f * 0.28f)
            close()
        },
        GuguSpark,
    )
}

private fun Path.zzPath(x: Float, y: Float, f: Float) {
    moveTo(x - f, y - f)
    lineTo(x + f, y - f)
    lineTo(x - f, y + f)
    lineTo(x + f, y + f)
}
