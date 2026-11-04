package com.bugu.ui

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import com.bugu.ui.screens.AlarmScreen
import com.bugu.ui.theme.BuguTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * 全屏响铃(FR-06/FR-07):同一时间多条 fire 只保留一个 Activity。
 * 新 fire 通过 singleTask onNewIntent 入队;当前 fire 确认/稍后后自动切下一条,
 * 全部处理完才 finish;声音、震动、WakeLock、音频焦点和通知按当前 fire 统一收尾。
 */
class RingActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var bell: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ringingStopped = false

    private lateinit var api: GeitaApi
    private lateinit var reportQueue: ReportQueue
    private lateinit var copyCache: WakeupCopyCache
    private lateinit var scope: CoroutineScope

    private val pending = ArrayDeque<RingPayload>()
    private var currentPayload by mutableStateOf<RingPayload?>(null)
    private var currentCopyLine by mutableStateOf<String?>(null)
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        api = ApiFactory.create(this)
        reportQueue = ReportQueue(PrefsStores(this).reportQueue)
        copyCache = WakeupCopyCache(PrefsStores(this).wakeupCopy)
        scope = lifecycleScope

        setContent {
            BuguTheme {
                val payload = currentPayload
                if (payload != null) {
                    key(payload.fireId) {
                        RingContent(
                            payload = payload,
                            copyLine = currentCopyLine,
                            onConfirmed = { ack("DONE") },
                            onSnoozed = { ack("SNOOZE", snoozeMinutes = 15) },
                        )
                    }
                }
            }
        }

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

    @Composable
    private fun RingContent(
        payload: RingPayload,
        copyLine: String?,
        onConfirmed: () -> Unit,
        onSnoozed: () -> Unit,
    ) {
        val wakeupWords = listOf("起床", "叫醒", "唤醒", "睡醒", "醒醒", "醒来")
        val confirmLabel = if (payload.kind == "MUTUAL_ALARM" || wakeupWords.any { payload.title.contains(it) }) {
            "向右滑动：我已起床"
        } else {
            "向右滑动：我已完成"
        }
        LaunchedEffect(payload.fireId) {
            currentCopyLine = copyCache.hit(payload.reminderId, LocalDate.now().toString()) ?: payload.title
            if (payload.fireId >= 0) {
                attempt {
                    api.reportFire(payload.fireId, payload.idempotencyKey, "LOCAL_ALARM")
                }.onFailure { e ->
                    if (e is GeitaApiException && e.code == "CONFLICT") {
                        reportQueue.markMissed(payload.fireId)
                    } else {
                        reportQueue.enqueue(PendingReport(payload.fireId, payload.idempotencyKey, "LOCAL_ALARM"))
                        ReportWorker.schedule(this@RingActivity, payload.fireId)
                    }
                }
            }
        }
        AlarmScreen(
            peerName = "惦记你的人",
            title = payload.title,
            copyLine = copyLine,
            kindLabel = "布谷来叫你啦",
            elderMode = false,
            onConfirmed = onConfirmed,
            onSnoozed = onSnoozed,
            isSelf = payload.selfReminder,
            confirmLabel = confirmLabel,
        )
    }

    private fun startRinging(payload: RingPayload) {
        AlarmNotifications.cancel(this, payload.fireId)
        currentPayload = payload
        currentCopyLine = copyCache.hit(payload.reminderId, LocalDate.now().toString()) ?: payload.title
        ringingStopped = false
        handled = false

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        audioManager = getSystemService(AudioManager::class.java)
        requestAudioFocus(audioAttributes)
        acquireWakeLock()
        startVibration()
        bell = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(
                    this@RingActivity,
                    Uri.parse("android.resource://$packageName/${R.raw.bugu_call}"),
                )
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.e("BuguRing", "bell start failed", it) }.getOrNull()

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

    private fun ack(action: String, snoozeMinutes: Int? = null) {
        val payload = currentPayload ?: return
        if (handled) return
        handled = true
        // 先停本地声音/震动/锁,再发网络请求;确认后立即安静并切下一条。
        stopRinging()
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
            advance()
        }
    }

    private fun advance() {
        stopRinging()
        val next = pending.removeFirstOrNull()
        if (next == null) {
            finish()
        } else {
            Log.i("BuguRing", "advance to fire=${next.fireId}")
            startRinging(next)
        }
    }

    override fun onDestroy() {
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
        runCatching { bell?.stop() }
        bell?.release()
        bell = null
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
        )
    }
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
)

/** suspend 友好 try/catch(CancellationException 透传)。 */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
