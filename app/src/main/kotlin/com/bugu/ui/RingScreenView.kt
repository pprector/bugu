package com.bugu.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bugu.R
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 响铃页原生视图(段二):不依赖 Compose,不使用布局 XML / drawable / style。
 *
 * - 色值常量与迁移前 Theme.H 完全一致(删掉 Theme.kt 后本页自带色板);
 * - 尺寸用 dp、字号用 sp,跟随系统字体缩放(0.85 / 1.0 / 1.3 / 2.0);
 * - 内容整体可滚动,右滑确认轨道高度随字号自适应,把手始终完整可拖。
 */
internal class RingScreenView(
    private val context: Context,
    onConfirmed: () -> Unit,
    onSnoozed: () -> Unit,
) {

    val root: ScrollView = ScrollView(context).apply {
        setBackgroundColor(RingColors.CANVAS)
        isFillViewport = true
        isVerticalScrollBarEnabled = false
    }

    private val jakarta: Typeface =
        ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
    private val grotesk: Typeface =
        ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT

    private val countdownText: TextView
    private val clockText: TextView
    private val titleText: TextView
    private val sourceText: TextView
    private val copyCard: LinearLayout
    private val copyTitle: TextView
    private val copyQuote: TextView
    private val copyTime: TextView
    private val playButton: FrameLayout
    private val playGlyph: PlayPauseGlyph
    private val waveform: WaveformView
    private val swipe: SwipeConfirmView
    private val footerText: TextView
    private val topTitle: TextView
    private val elderChip: LinearLayout

    private var isSelf = false
    private var peerName = "亲友"
    private var countdown = 40
    private var clockSec = 0

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (countdown > 0) countdown--
            clockSec++
            renderTime()
            handler.postDelayed(this, 1_000L)
        }
    }

    private var playing = false
    private var progress = 0f
    private val playbackTicker = object : Runnable {
        override fun run() {
            if (!playing) return
            progress += 0.01f
            if (progress >= 1f) {
                progress = 0f
                playing = false
                playGlyph.playing = false
            }
            renderCopyProgress()
            if (playing) handler.postDelayed(this, 100L)
        }
    }

    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        root.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 全屏穿透窗口自己接管边距:顶栏避开状态栏,内容底部避开导航栏。
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        // ---- 顶栏:咕咕头像 + 居中标题 ----
        val topBar = FrameLayout(context)
        val birdBox = FrameLayout(context).apply {
            background = oval(RingColors.WHITE, strokeDp = 1, strokeColor = RingColors.HAIRLINE)
            addView(
                GuguRingView(context),
                FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER),
            )
            contentDescription = "咕咕"
        }
        topBar.addView(
            birdBox,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.CENTER_VERTICAL),
        )
        topTitle = label("单次提醒", 17f, RingColors.INK, bold(jakarta), Gravity.CENTER)
        topTitle.setPadding(dp(56), 0, dp(56), 0)
        topBar.addView(topTitle, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(topBar, lp(height = dp(56)))

        content.addView(Space(context), lp(height = dp(8)))

        // ---- 状态胶囊 ----
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(RingColors.CORAL_TINT, 10)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        chip.addView(BoltGlyph(context), LinearLayout.LayoutParams(dp(14), dp(14)))
        chip.addView(Space(context), LinearLayout.LayoutParams(dp(5), 1))
        countdownText = label("", 13f, RingColors.CORAL, semibold(jakarta))
        chip.addView(countdownText, LinearLayout.LayoutParams(WRAP, WRAP))
        chipRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP))
        chipRow.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        // R4:长辈极简模式标记(旧版与状态 chip 同一行、右侧)
        elderChip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(RingColors.INDIGO_TINT, 10)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            addView(label("长辈极简模式", 13f, RingColors.INDIGO_INK, semibold(jakarta)))
            visibility = View.GONE
        }
        chipRow.addView(elderChip, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(chipRow, lp(height = WRAP))

        content.addView(Space(context), lp(height = dp(8)))

        // ---- 时钟 + 日期 ----
        clockText = label("", 56f, RingColors.INK, bold(grotesk), Gravity.CENTER)
        clockText.letterSpacing = -0.02f
        clockText.fontFeatureSettings = "tnum"
        content.addView(clockText, lp())
        val dateText = label(dateLine(), 15f, RingColors.INK2, regular(jakarta), Gravity.CENTER)
        content.addView(dateText, lp(top = 0))

        content.addView(Space(context), lp(height = dp(8)))

        // ---- 叫醒卡片 ----
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(
                RingColors.WHITE, 16, strokeDp = 1, strokeColor = RingColors.HAIRLINE,
            )
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val avatar = FrameLayout(context).apply {
            background = oval(RingColors.HAIRLINE, strokeDp = 2, strokeColor = RingColors.CORAL)
        }
        val badge = FrameLayout(context).apply {
            background = oval(RingColors.MINT, strokeDp = 2, strokeColor = RingColors.WHITE)
            addView(HeartGlyph(context), FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER))
        }
        avatar.addView(
            badge,
            FrameLayout.LayoutParams(dp(26), dp(26), Gravity.END or Gravity.BOTTOM),
        )
        card.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(92)))
        card.addView(Space(context), lp(height = dp(12)))

        val sourceChip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(RingColors.CORAL_TINT, 10)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        sourceText = label("", 13f, RingColors.CORAL_INK, semibold(jakarta))
        sourceChip.addView(sourceText, LinearLayout.LayoutParams(WRAP, WRAP))
        card.addView(sourceChip, LinearLayout.LayoutParams(WRAP, WRAP))

        card.addView(Space(context), lp(height = dp(10)))
        titleText = label("", 24f, RingColors.INK, bold(jakarta), Gravity.CENTER, lineSpacing = 1.33f)
        card.addView(titleText, lp(width = MATCH))
        content.addView(card, lp(top = 14))

        // ---- 留言卡片(无留言时隐藏) ----
        copyCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                RingColors.WHITE, 16, strokeDp = 1, strokeColor = RingColors.HAIRLINE,
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val copyHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copyHeader.addView(RecordVoiceGlyph(context), LinearLayout.LayoutParams(dp(17), dp(17)))
        copyHeader.addView(Space(context), LinearLayout.LayoutParams(dp(6), 1))
        copyTitle = label("", 15f, RingColors.INK, semibold(jakarta), lineSpacing = 1.3f)
        copyHeader.addView(copyTitle, LinearLayout.LayoutParams(0, WRAP, 1f))
        copyTime = label("", 11f, RingColors.CORAL, semibold(grotesk))
        copyHeader.addView(copyTime, LinearLayout.LayoutParams(WRAP, WRAP))
        copyCard.addView(copyHeader, lp(width = MATCH))
        copyCard.addView(Space(context), lp(height = dp(10)))

        copyCard.visibility = View.GONE
        copyQuote = label("", 15f, RingColors.INK2, regular(jakarta), lineSpacing = 1.5f)
        copyQuote.setBackground(rounded(RingColors.WARM_FILL, 14))
        copyQuote.setPadding(dp(12), dp(12), dp(12), dp(12))
        copyCard.addView(copyQuote, lp(width = MATCH))
        copyCard.addView(Space(context), lp(height = dp(12)))

        val playRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playGlyph = PlayPauseGlyph(context)
        playButton = FrameLayout(context).apply {
            background = oval(RingColors.CORAL)
            addView(playGlyph, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            isClickable = true
            contentDescription = "播放留言"
            setOnClickListener { togglePlayback() }
        }
        playRow.addView(playButton, LinearLayout.LayoutParams(dp(38), dp(38)))
        playRow.addView(Space(context), LinearLayout.LayoutParams(dp(12), 1))
        waveform = WaveformView(context)
        playRow.addView(waveform, LinearLayout.LayoutParams(0, dp(30), 1f))
        copyCard.addView(playRow, lp(width = MATCH))
        content.addView(copyCard, lp(top = 14))

        // ---- 右滑确认 ----
        swipe = SwipeConfirmView(context, onConfirmed)
        content.addView(swipe, lp(height = WRAP, top = 14))

        content.addView(Space(context), lp(height = dp(10)))

        // ---- 小憩 ----
        val snooze = label(
            "小憩 15 分钟 · 等咕咕再叫你",
            15f,
            RingColors.INDIGO_INK,
            semibold(jakarta),
            Gravity.CENTER,
        ).apply {
            background = rounded(RingColors.INDIGO_TINT, 14)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            isClickable = true
            setOnClickListener { onSnoozed() }
        }
        content.addView(snooze, lp(width = MATCH))

        content.addView(Space(context), lp(height = dp(8)))

        // ---- 底部说明(文案已按产品口径修正:一直响到用户确认) ----
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        footer.addView(InfoGlyph(context), LinearLayout.LayoutParams(dp(15), dp(15)))
        footer.addView(Space(context), LinearLayout.LayoutParams(dp(6), 1))
        footerText = label(footerLine(), 13f, RingColors.INK3_STRONG, regular(jakarta), lineSpacing = 1.45f)
        footer.addView(footerText, LinearLayout.LayoutParams(0, WRAP, 1f))
        content.addView(footer, lp(top = 8))
        content.addView(Space(context), lp(height = dp(20)))

        resetTicker()
        renderCopyProgress()
    }

    /** 每次真实 fire(含排队切换)调用:重绘标题/来源/留言并重置倒计时与时钟。 */
    fun render(
        title: String,
        kind: String,
        self: Boolean,
        peerName: String,
        elderMode: Boolean,
        copyLine: String?,
        confirmLabel: String,
    ) {
        isSelf = self
        this.peerName = peerName
        topTitle.text = kindText(kind)
        titleText.text = title
        sourceText.text = if (self) "来自自己的提醒" else "来自 $peerName 的提醒"
        copyTitle.text = if (self) "留给自己的话" else "$peerName 的留言"
        copyQuote.text = if (copyLine.isNullOrBlank()) "" else "“$copyLine”"
        copyCard.visibility = if (copyLine.isNullOrBlank()) View.GONE else View.VISIBLE
        elderChip.visibility = if (elderMode) View.VISIBLE else View.GONE
        footerText.text = footerLine()
        swipe.label = confirmLabel
        resetTicker()
        root.scrollTo(0, 0)
    }

    /** 旧版 kindLabel:顶部标题按提醒类型显示,不再硬编码「布谷来叫你啦」。 */
    private fun kindText(kind: String): String = when (kind) {
        "ONCE" -> "单次提醒"
        "REPEAT" -> "循环提醒"
        "MUTUAL_ALARM" -> "互叫闹钟"
        else -> kind
    }

    /** Activity 销毁时停掉所有定时器,避免 Handler 泄漏。 */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        playing = false
    }

    private fun resetTicker() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(playbackTicker)
        countdown = 40
        val now = LocalTime.now()
        clockSec = now.hour * 3_600 + now.minute * 60 + now.second
        playing = false
        progress = 0f
        playGlyph.playing = false
        renderTime()
        renderCopyProgress()
        handler.postDelayed(ticker, 1_000L)
    }

    private fun renderTime() {
        countdownText.text = "咕咕正在叫你 · 00:%02d".format(countdown)
        clockText.text = "%02d:%02d".format(clockSec / 3_600 % 24, clockSec / 60 % 60)
    }

    private fun renderCopyProgress() {
        copyTime.text = "00:%02d / 00:15".format((progress * 15).toInt())
        waveform.progress = progress
        waveform.invalidate()
    }

    private fun togglePlayback() {
        playing = !playing
        playGlyph.playing = playing
        playGlyph.invalidate()
        handler.removeCallbacks(playbackTicker)
        if (playing) handler.postDelayed(playbackTicker, 100L)
    }

    /** R5 底部文案:保持段二产品口径「会一直响到你确认」,是否改回 40 秒由产品决定,此处不改。 */
    private fun footerLine(): String = if (isSelf) {
        "确认后，咕咕会记下这次完成。会一直响到你确认。"
    } else {
        "确认后，咕咕会把回执告诉 $peerName。会一直响到你确认。"
    }

    private fun dateLine(): String {
        val d = LocalDate.now()
        val week = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
        return "${d.monthValue}月${d.dayOfMonth}日 星期$week"
    }

    // ---- 构造工具 ----

    private fun label(
        text: String,
        sp: Float,
        color: Int,
        face: Typeface,
        gravity: Int = Gravity.START,
        lineSpacing: Float = 1.3f,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = face
        this.gravity = gravity
        setLineSpacing(0f, lineSpacing)
        includeFontPadding = false
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun dpF(v: Float): Float = v * context.resources.displayMetrics.density

    private fun rounded(fill: Int, radiusDp: Int, strokeDp: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dpF(radiusDp.toFloat())
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun oval(fill: Int, strokeDp: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun lp(
        width: Int = MATCH,
        height: Int = WRAP,
        top: Int = 0,
        left: Int = 0,
        right: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height).apply {
            topMargin = dp(top)
            leftMargin = dp(left)
            rightMargin = dp(right)
        }

    private fun bold(base: Typeface): Typeface = Typeface.create(base, Typeface.BOLD)

    private fun semibold(base: Typeface): Typeface = Typeface.create(base, Typeface.BOLD)

    private fun regular(base: Typeface): Typeface = Typeface.create(base, Typeface.NORMAL)

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** 亮色胶囊/轨道/正文色,与迁移前 Theme.H 一一对应。 */
private object RingColors {
    const val CANVAS = 0xFFFAF8F5.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val HAIRLINE = 0xFFEFECE6.toInt()
    const val WARM_FILL = 0xFFF4F1EA.toInt()
    const val TRACK = 0xFFEDE7E0.toInt()

    const val INK = 0xFF1E1B18.toInt()
    const val INK2 = 0xFF5C564E.toInt()
    const val INK3 = 0xFF9C9488.toInt()
    const val INK3_STRONG = 0xFF6B655C.toInt()
    const val CORAL_INK = 0xFFB83A1B.toInt()
    const val INDIGO_INK = 0xFF4338CA.toInt()

    const val CORAL = 0xFFFA5E38.toInt()
    const val INDIGO = 0xFF6366F1.toInt()
    const val MINT = 0xFF10B981.toInt()
    const val MINT_DEEP = 0xFF0DA271.toInt()

    const val CORAL_TINT = 0x1AFA5E38
    const val INDIGO_TINT = 0x1A6366F1
}

// ---- 轻量矢量图标:全部 Canvas 绘制,不依赖 drawable 资源 ----

private class BoltGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RingColors.CORAL }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path().apply {
            moveTo(w * 0.55f, h * 0.02f)
            lineTo(w * 0.18f, h * 0.56f)
            lineTo(w * 0.45f, h * 0.56f)
            lineTo(w * 0.38f, h * 0.98f)
            lineTo(w * 0.82f, h * 0.42f)
            lineTo(w * 0.53f, h * 0.42f)
            close()
        }
        canvas.drawPath(path, paint)
    }
}

private class HeartGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawCircle(w * 0.30f, h * 0.34f, w * 0.22f, paint)
        canvas.drawCircle(w * 0.70f, h * 0.34f, w * 0.22f, paint)
        val path = Path().apply {
            moveTo(w * 0.10f, h * 0.40f)
            lineTo(w * 0.90f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.92f)
            close()
        }
        canvas.drawPath(path, paint)
    }
}

private class RecordVoiceGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RingColors.CORAL
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.strokeWidth = w * 0.12f
        val body = RectF(w * 0.34f, h * 0.06f, w * 0.66f, h * 0.60f)
        canvas.drawRoundRect(body, w * 0.16f, w * 0.16f, paint)
        canvas.drawArc(RectF(w * 0.20f, h * 0.30f, w * 0.80f, h * 0.82f), 0f, 180f, false, paint)
        canvas.drawLine(w * 0.50f, h * 0.82f, w * 0.50f, h * 0.96f, paint)
    }
}

private class InfoGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RingColors.INK3
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.strokeWidth = w * 0.12f
        canvas.drawCircle(w * 0.5f, h * 0.5f, min(w, h) * 0.44f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(w * 0.5f, h * 0.30f, w * 0.06f, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(w * 0.5f, h * 0.46f, w * 0.5f, h * 0.72f, paint)
    }
}

private class PlayPauseGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    var playing: Boolean = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (!playing) {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.78f, h * 0.50f)
                lineTo(w * 0.32f, h * 0.78f)
                close()
            }
            canvas.drawPath(path, paint)
        } else {
            val barW = w * 0.13f
            val barH = h * 0.56f
            val top = (h - barH) / 2f
            canvas.drawRoundRect(RectF(w * 0.32f, top, w * 0.32f + barW, top + barH), barW / 2f, barW / 2f, paint)
            canvas.drawRoundRect(
                RectF(w * 0.55f, top, w * 0.55f + barW, top + barH),
                barW / 2f, barW / 2f, paint,
            )
        }
    }
}

/** 咕咕叫醒形象(简化版 RING 姿态):珊瑚色身体 + 张嘴 + 声波弧线。 */
private class GuguRingView(context: Context) : View(context) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RingColors.CORAL }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0523F.toInt() }
    private val belly = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF3EC.toInt() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RingColors.INK }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val beak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB020.toInt() }
    private val beakDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB8A8.toInt() }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RingColors.INK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2.9f
        val cx = w / 2f
        val cy = h / 2f

        canvas.drawCircle(cx - r * 0.14f, cy - r * 1.06f, r * 0.12f, dark)
        canvas.drawCircle(cx + r * 0.08f, cy - r * 1.14f, r * 0.15f, dark)

        canvas.save()
        canvas.rotate(-18f, cx - r * 0.95f, cy + r * 0.22f)
        canvas.drawOval(RectF(cx - r * 1.16f, cy - r * 0.23f, cx - r * 0.74f, cy + r * 0.67f), dark)
        canvas.restore()
        canvas.save()
        canvas.rotate(35f, cx + r * 0.95f, cy - r * 0.48f)
        canvas.drawOval(RectF(cx + r * 0.69f, cy - r * 0.93f, cx + r * 1.11f, cy - r * 0.03f), dark)
        canvas.restore()

        canvas.drawCircle(cx, cy, r, body)
        canvas.drawOval(RectF(cx - r * 0.56f, cy - r * 0.19f, cx + r * 0.56f, cy + r * 0.75f), belly)

        canvas.drawCircle(cx - r * 0.085f, cy + r * 0.28f, r * 0.105f, body)
        canvas.drawCircle(cx + r * 0.085f, cy + r * 0.28f, r * 0.105f, body)
        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.185f, cy + r * 0.30f)
                lineTo(cx + r * 0.185f, cy + r * 0.30f)
                lineTo(cx, cy + r * 0.62f)
                close()
            },
            body,
        )

        canvas.drawCircle(cx - r * 0.31f, cy - r * 0.24f, r * 0.085f, ink)
        canvas.drawCircle(cx + r * 0.31f, cy - r * 0.24f, r * 0.085f, ink)
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.27f, r * 0.03f, white)
        canvas.drawCircle(cx + r * 0.34f, cy - r * 0.27f, r * 0.03f, white)

        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.02f)
                lineTo(cx + r * 0.18f, cy - r * 0.02f)
                lineTo(cx, cy + r * 0.08f)
                close()
            },
            beak,
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.11f, cy + r * 0.10f)
                lineTo(cx + r * 0.11f, cy + r * 0.10f)
                lineTo(cx, cy + r * 0.24f)
                close()
            },
            beakDark,
        )

        canvas.drawOval(RectF(cx - r * 0.65f, cy + r * 0.005f, cx - r * 0.35f, cy + r * 0.195f), blush)
        canvas.drawOval(RectF(cx + r * 0.35f, cy + r * 0.005f, cx + r * 0.65f, cy + r * 0.195f), blush)

        arc.strokeWidth = r * 0.05f
        canvas.drawArc(
            RectF(cx + r * 0.30f, cy - r * 0.95f, cx + r * 0.70f, cy - r * 0.55f),
            -80f, 100f, false, arc,
        )
        canvas.drawArc(
            RectF(cx + r * 0.14f, cy - r * 1.09f, cx + r * 0.86f, cy - r * 0.41f),
            -80f, 100f, false, arc,
        )
    }
}

/** 语音波形条(与迁移前 Waveform 相同的确定性伪随机数据与配色)。 */
private class WaveformView(context: Context) : View(context) {
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RingColors.CORAL }
    private val pending = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RingColors.CORAL
        alpha = (0.35f * 255).roundToInt()
    }
    var progress: Float = 0f

    override fun onDraw(canvas: Canvas) {
        val n = BARS.size
        val slot = width.toFloat() / n
        val barWidth = min(dp(4f), slot * 0.55f)
        BARS.forEachIndexed { i, amp ->
            val bh = height.toFloat() * (0.25f + 0.75f * amp)
            val x = i * slot + (slot - barWidth) / 2f
            val paint = if (i.toFloat() / n <= progress) played else pending
            canvas.drawRoundRect(
                RectF(x, (height - bh) / 2f, x + barWidth, (height + bh) / 2f),
                barWidth / 2f, barWidth / 2f, paint,
            )
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private companion object {
        val BARS = listOf(
            0.45f, 0.7f, 0.35f, 0.9f, 0.55f, 0.75f, 0.4f, 0.65f, 0.95f, 0.5f,
            0.7f, 0.3f, 0.85f, 0.6f, 0.45f, 0.75f, 0.35f, 0.6f, 0.5f, 0.4f,
            0.3f, 0.25f, 0.2f, 0.18f, 0.15f, 0.22f, 0.18f, 0.14f,
        )
    }
}

/**
 * 右滑确认轨道:自测高度(最小 64dp,字号放大时按标签行高撑开),把手 52dp,
 * 拖动到轨道末端约 92% 触发;未到 60% 松手回弹。轨道内边距 6dp,把手始终完整可见。
 */
private class SwipeConfirmView(
    context: Context,
    private val onComplete: () -> Unit,
) : FrameLayout(context) {

    private val handleSize = dp(52)
    private val inner = dp(6)
    private val labelView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(RingColors.INK2)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
    private val handle = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(RingColors.MINT, RingColors.MINT_DEEP)
        }
        addView(ChevronGlyph(context), LayoutParams(dp(26), dp(26), Gravity.CENTER))
        contentDescription = "向右滑动确认"
    }
    private var dragX = 0f
    private var downX = 0f
    private var downDx = 0f
    private var dragging = false
    private var done = false

    var label: String
        get() = labelView.text.toString()
        set(value) {
            labelView.text = value
            requestLayout()
        }

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(RingColors.TRACK)
            cornerRadius = dp(999).toFloat()
        }
        addView(
            labelView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            handle,
            LayoutParams(handleSize, handleSize, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = inner
            },
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        labelView.measure(
            MeasureSpec.makeMeasureSpec(widthSize - inner * 2, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val desired = maxOf(dp(64), labelView.measuredHeight + dp(16))
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(desired, MeasureSpec.EXACTLY),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val maxDist = (width - inner * 2 - handleSize).coerceAtLeast(1).toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handleTop = (height - handleSize) / 2f
                val left = inner + dragX
                val hit = event.x >= left - dp(8) && event.x <= left + handleSize + dp(8) &&
                    event.y >= handleTop - dp(8) && event.y <= handleTop + handleSize + dp(8)
                if (!hit) return false
                dragging = true
                done = false
                downX = event.x
                downDx = dragX
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                dragX = (downDx + (event.x - downX)).coerceIn(0f, maxDist)
                handle.translationX = dragX
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP && dragX >= maxDist * 0.92f) {
                    done = true
                    dragX = 0f
                    handle.translationX = 0f
                    onComplete()
                } else if (dragX < maxDist * 0.6f) {
                    dragX = 0f
                    handle.translationX = 0f
                }
                return true
            }
        }
        return false
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

private class ChevronGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.strokeWidth = w * 0.10f
        val path = Path().apply {
            moveTo(w * 0.36f, h * 0.24f)
            lineTo(w * 0.64f, h * 0.50f)
            lineTo(w * 0.36f, h * 0.76f)
        }
        canvas.drawPath(path, paint)
    }
}
