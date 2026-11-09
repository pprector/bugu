package com.bugu.ui

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bugu.ApiFactory
import com.bugu.R
import com.bugu.client.api.AckRequest
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.GeitaApiException
import com.bugu.schedule.AlarmNotifications
import com.bugu.schedule.AlarmScheduler
import com.bugu.schedule.PendingReport
import com.bugu.schedule.PrefsStores
import com.bugu.schedule.ReminderSync
import com.bugu.schedule.ReportQueue
import com.bugu.schedule.ReportWorker
import com.bugu.schedule.WakeupCopyCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * 全屏响铃(FR-06/FR-07):同一时间多条 fire 只保留一个 Activity。
 * 新 fire 通过 singleTask onNewIntent 入队;当前 fire 确认/稍后后自动切下一条,
 * 全部处理完才 finish;声音、震动、WakeLock、音频焦点和通知按当前 fire 统一收尾。
 *
 * 段二:UI 改为纯原生 View([RingScreenView],零布局 XML / drawable / style),不再依赖 Compose。
 * 响铃持续到用户右滑确认或小憩,不存在到点自动停止的逻辑。
 */
class RingActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var noticePlayer: MediaPlayer? = null
    private var loopPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ringingStopped = false

    private lateinit var api: GeitaApi
    private lateinit var reportQueue: ReportQueue
    private lateinit var copyCache: WakeupCopyCache
    private lateinit var scope: CoroutineScope
    private lateinit var ui: RingScreenView

    private val pending = ArrayDeque<RingPayload>()
    private var currentPayload: RingPayload? = null
    private var currentCopyLine: String? = null
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏直达:亮屏、越过 keyguard 展示,必要时请求用户解锁。
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        // 全屏直达:内容自己按 WindowInsets 留边,状态栏/导航栏保持透明(浅色图标 + 关掉导航栏对比蒙层)。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        api = ApiFactory.create(this)
        reportQueue = ReportQueue(PrefsStores(this).reportQueue)
        copyCache = WakeupCopyCache(PrefsStores(this).wakeupCopy)
        scope = lifecycleScope

        ui = RingScreenView(
            context = this,
            onConfirmed = { ack("DONE") },
            onSnoozed = { ack("SNOOZE", snoozeMinutes = 15) },
        )
        setContentView(ui.root)

        val payload = readPayload(intent)
        if (payload != null) {
            startRinging(payload)
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val payload = readPayload(intent) ?: return
        if (payload.fireId == currentPayload?.fireId ||
            pending.any { it.fireId == payload.fireId }
        ) {
            Log.i("BuguRing", "duplicate fire ignored fire=${payload.fireId}")
            return
        }
        if (currentPayload == null) {
            startRinging(payload)
        } else {
            // 不打断当前响铃;等当前确认/稍后后再切下一条。
            pending.addLast(payload)
            Log.i("BuguRing", "fire queued fire=${payload.fireId} pending=${pending.size}")
        }
    }

    private fun startRinging(payload: RingPayload) {
        AlarmNotifications.cancel(this, payload.fireId)
        // 切下一条时复用当前 Activity,先释放上一条的播放器,避免叠音/泄漏。
        releaseAlarmPlayers()
        currentPayload = payload
        currentCopyLine = copyCache.hit(payload.reminderId, LocalDate.now().toString()) ?: payload.title
        ringingStopped = false
        handled = false

        ui.render(
            title = payload.title,
            kind = payload.kind,
            self = payload.selfReminder,
            peerName = peerNameFor(payload.creatorId),
            elderMode = elderModeEnabled(),
            copyLine = currentCopyLine,
            confirmLabel = confirmLabel(payload),
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        audioManager = getSystemService(AudioManager::class.java)
        requestAudioFocus(audioAttributes)
        acquireWakeLock()
        startVibration()
        startCueThenLoop(audioAttributes)

        if (payload.voiceEnabled) {
            val line = currentCopyLine
            tts = TextToSpeech(this) { status ->
                if (!ringingStopped && status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINA
                    line?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "bugu-ring") }
                }
            }
        }
    }

    /** 确认按钮文案:叫醒类提醒用「我已起床」,其余用「我已完成」。 */
    private fun confirmLabel(payload: RingPayload): String {
        val wakeupWords = listOf("起床", "叫醒", "唤醒", "睡醒", "醒醒", "醒来")
        return if (payload.kind == "MUTUAL_ALARM" || wakeupWords.any { payload.title.contains(it) }) {
            "向右滑动：我已起床"
        } else {
            "向右滑动：我已完成"
        }
    }

    /**
     * 响铃动机 → 循环段串联:先播 1.25s 的 alarm_notice,播完立即启动 alarm_loop。
     * 两个 MediaPlayer 都提前 prepare,动机结束时直接 start 循环,衔接间隙远小于 1s。
     * 动机加载/播放失败时直接进循环,保证任何情况下都有循环响铃。
     */
    private fun startCueThenLoop(audioAttributes: AudioAttributes) {
        val loop = runCatching {
            createAlarmPlayer(audioAttributes, R.raw.alarm_loop, looping = true)
        }.onFailure { Log.e("BuguRing", "loop player start failed", it) }.getOrNull() ?: return
        loopPlayer = loop

        val notice = runCatching {
            createAlarmPlayer(audioAttributes, R.raw.alarm_notice, looping = false)
        }.onFailure { Log.w("BuguRing", "notice player start failed; loop only", it) }.getOrNull()
        if (notice == null) {
            startLoop()
            return
        }
        noticePlayer = notice
        notice.setOnCompletionListener { player ->
            if (ringingStopped) return@setOnCompletionListener
            noticePlayer = null
            runCatching { player.release() }
            Log.i("BuguRing", "notice done; loop start")
            startLoop()
        }
        notice.setOnErrorListener { player, what, extra ->
            Log.w("BuguRing", "notice error what=$what extra=$extra; loop start")
            if (!ringingStopped) {
                noticePlayer = null
                runCatching { player.release() }
                startLoop()
            }
            true
        }
        runCatching { notice.start() }.onFailure {
            Log.w("BuguRing", "notice play failed; loop start", it)
            noticePlayer = null
            runCatching { notice.release() }
            startLoop()
        }
    }

    private fun startLoop() {
        if (ringingStopped) return
        val player = loopPlayer ?: return
        runCatching { player.start() }.onFailure { Log.e("BuguRing", "loop play failed", it) }
    }

    /** raw 资源用 openRawResourceFd 直接喂 MediaPlayer,不依赖 android.resource:// URI。 */
    private fun createAlarmPlayer(
        audioAttributes: AudioAttributes,
        resId: Int,
        looping: Boolean,
    ): MediaPlayer {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(audioAttributes)
            resources.openRawResourceFd(resId).use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            player.isLooping = looping
            player.prepare()
            return player
        } catch (e: Exception) {
            runCatching { player.release() }
            throw e
        }
    }

    private fun releaseAlarmPlayers() {
        noticePlayer?.let { player ->
            noticePlayer = null
            runCatching { player.setOnCompletionListener(null) }
            runCatching { player.setOnErrorListener(null) }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        loopPlayer?.let { player ->
            loopPlayer = null
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    private fun ack(action: String, snoozeMinutes: Int? = null) {
        val payload = currentPayload ?: return
        if (handled) return
        handled = true
        // 先停本地声音/震动/锁,再发网络请求;确认后立即安静并切下一条。
        stopRinging()
        // 队列里还有下一条时先立即接管,网络确认在后台继续;
        // 避免等待 ack 接口期间出现超过 1 秒的静音。
        val next = pending.removeFirstOrNull()
        if (next != null) {
            Log.i("BuguRing", "advance immediately to fire=${next.fireId} pending=${pending.size}")
            startRinging(next)
        }
        scope.launch {
            runCatching { api.ack(AckRequest(payload.fireId, action, snoozeMinutes = snoozeMinutes)) }
                .onSuccess { ackView ->
                    // SNOOZE 优先用服务端 ack 返回的 nextFireAt;旧服务端无该字段时才回退 +15min。
                    val hint = if (action == "SNOOZE" && payload.reminderId >= 0) {
                        ackView.nextFireAt?.let { ReminderSync.snoozeHint(payload.reminderId, it) }
                            ?: ReminderSync.legacySnoozeHint(payload.reminderId)
                    } else {
                        null
                    }
                    // REPEAT 使用服务端列表推进后的 fireAt(与 ack.nextFireAt 同源),不做本地推算。
                    runCatching { ReminderSync.sync(this@RingActivity, api, hint) }
                }
            if (next == null) finish()
        }
    }

    override fun onDestroy() {
        ui.release()
        stopRinging()
        pending.clear()
        super.onDestroy()
    }

    private fun requestAudioFocus(audioAttributes: AudioAttributes) {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        Log.w("BuguRing", "audio focus lost($change); alarm keeps ringing")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        Log.w("BuguRing", "audio focus duck ignored for alarm")
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        audioFocusRequest = request
        runCatching { manager.requestAudioFocus(request) }
            .onFailure { Log.w("BuguRing", "requestAudioFocus failed", it) }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = runCatching {
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bugu:alarmRing").apply {
                setReferenceCounted(false)
                acquire(5 * 60 * 1000L)
            }
        }.onFailure { Log.w("BuguRing", "acquire wake lock failed", it) }.getOrNull()
    }

    private fun startVibration() {
        val current = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = current
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1_200), 0)
        runCatching { current?.vibrate(effect) }
            .onFailure { Log.w("BuguRing", "vibrate failed", it) }
    }

    /** 统一收尾:声音、震动、WakeLock、音频焦点、通知全部释放。 */
    private fun stopRinging() {
        if (ringingStopped) return
        ringingStopped = true
        releaseAlarmPlayers()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
        audioFocusRequest?.let { request ->
            audioManager?.let { manager -> runCatching { manager.abandonAudioFocusRequest(request) } }
        }
        audioFocusRequest = null
        currentPayload?.let { AlarmNotifications.cancel(this, it.fireId) }
    }

    private fun readPayload(intent: android.content.Intent): RingPayload? {
        val fireId = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1)
        if (fireId < 0) return null
        return RingPayload(
            fireId = fireId,
            reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1),
            title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "到点了",
            fireAt = intent.getStringExtra(AlarmScheduler.EXTRA_FIRE_AT) ?: "",
            idempotencyKey = intent.getStringExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY) ?: "",
            voiceEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_VOICE, false),
            kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "ONCE",
            selfReminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_SELF, false),
            creatorId = intent.getLongExtra(AlarmScheduler.EXTRA_CREATOR_ID, 0L),
        )
    }

    /** 亲友昵称缓存由 ReminderSync 在每次同步时写入;离线响铃时取不到就用中性「亲友」。 */
    private fun peerNameFor(creatorId: Long): String {
        if (creatorId <= 0L) return "亲友"
        val raw = getSharedPreferences("bugu_main", MODE_PRIVATE).getString("bond_names_json", null)
            ?: return "亲友"
        return runCatching { org.json.JSONObject(raw).optString(creatorId.toString(), "亲友") }
            .getOrDefault("亲友")
    }

    private fun elderModeEnabled(): Boolean =
        getSharedPreferences("bugu_main", MODE_PRIVATE).getBoolean("care_mode", false)
}

private data class RingPayload(
    val fireId: Long,
    val reminderId: Long,
    val title: String,
    val fireAt: String,
    val idempotencyKey: String,
    val voiceEnabled: Boolean,
    val kind: String,
    val selfReminder: Boolean,
    val creatorId: Long = 0L,
)
