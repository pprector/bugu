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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.bugu.ApiFactory
import com.bugu.R
import com.bugu.client.api.AckRequest
import com.bugu.client.api.GeitaApiException
import com.bugu.schedule.AlarmScheduler
import com.bugu.schedule.PrefsStores
import com.bugu.schedule.PendingReport
import com.bugu.schedule.ReminderSync
import com.bugu.schedule.ReportQueue
import com.bugu.schedule.ReportWorker
import com.bugu.schedule.WakeupCopyCache
import com.bugu.ui.screens.AlarmScreen
import com.bugu.ui.theme.BuguTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * 全屏响铃(FR-06/FR-07):复用应用内 [AlarmScreen] 布谷视觉。
 * TTS 播叫醒语(缓存优先,断网回落标题);立即上报触发事实,
 * 失败入重试队列(WorkManager 退避);409 = 已错过,静默关闭。
 */
class RingActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var bell: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ringingStopped = false
    private var guardFireId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fireId = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1)
        // 同一 fire 可能同时被 startActivity 和全屏通知拉起;只保留一个实例,避免双份铃声/WakeLock。
        if (fireId >= 0 && !activeFireIds.add(fireId)) {
            Log.i("BuguRing", "duplicate RingActivity ignored fire=$fireId")
            finish()
            return
        }
        if (fireId >= 0) guardFireId = fireId
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
        // 全屏通知已经把用户带到响铃页,移除常驻通知;若全屏被拦截,通知仍可点击进入。
        com.bugu.schedule.AlarmNotifications.cancel(this, fireId)
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "到点了"
        val idempotencyKey = intent.getStringExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY) ?: ""
        val voice = intent.getBooleanExtra(AlarmScheduler.EXTRA_VOICE, false)
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
        val selfReminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_SELF, false)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "ONCE"
        // 打卡文案跟随内容:只有起床/叫醒类才是「我已起床」,其余提醒是「我已完成」
        // 注意不要用单字「醒」——「提醒自己」会被误判成起床
        val wakeupWords = listOf("起床", "叫醒", "唤醒", "睡醒", "醒醒", "醒来")
        val confirmLabel = if (kind == "MUTUAL_ALARM" || wakeupWords.any { title.contains(it) }) {
            "向右滑动：我已起床"
        } else {
            "向右滑动：我已完成"
        }

        val api = ApiFactory.create(this)
        val stores = PrefsStores(this)
        val queue = ReportQueue(stores.reportQueue)
        val copyCache = WakeupCopyCache(stores.wakeupCopy)
        val scope = this.lifecycleScope

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        audioManager = getSystemService(AudioManager::class.java)
        requestAudioFocus(audioAttributes)
        acquireWakeLock()
        startVibration()
        // 到点持续响「布谷」,走闹钟声道;确认/稍后/退出时会显式 stop/release。
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

        // 防抖:确认/小憩只发一次
        var handled = false
        fun ack(action: String, snoozeMinutes: Int? = null) {
            if (handled) return
            handled = true
            // 先停本地声音/震动/锁,再发网络请求;确认后立即安静。
            stopRinging()
            scope.launch {
                val hint = if (action == "SNOOZE" && reminderId >= 0) {
                    ReminderSync.snoozeHint(reminderId)
                } else {
                    null
                }
                runCatching { api.ack(AckRequest(fireId, action, snoozeMinutes = snoozeMinutes)) }
                    .onSuccess {
                        // ack 后统一重排:REPEAT 用服务端推进后的 fireAt,SNOOZE 用 hint 覆盖新 fireAt。
                        runCatching { ReminderSync.sync(this@RingActivity, api, hint) }
                    }
                finish()
            }
        }

        // TTS 初始化(系统引擎,离线可播)
        var speakText: String? = null
        if (voice) {
            tts = TextToSpeech(this) { status ->
                if (!ringingStopped && status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINA
                    speakText?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "bugu-ring") }
                }
            }
        }

        setContent {
            BuguTheme {
                var copyLine by remember { mutableStateOf<String?>(null) }

                // 互叫:当日叫醒语缓存优先(次日预取后断网可播),未命中回落标题
                LaunchedEffect(Unit) {
                    val today = LocalDate.now().toString()
                    copyLine = copyCache.hit(reminderId, today) ?: title
                    if (voice) {
                        speakText = copyLine
                    }
                    // 立即上报触发事实(E7):失败入队重试
                    attempt {
                        api.reportFire(fireId, idempotencyKey, "LOCAL_ALARM")
                    }.onFailure { e ->
                        if (e is GeitaApiException && e.code == "CONFLICT") {
                            queue.markMissed(fireId) // 已收口/已错过
                        } else {
                            queue.enqueue(PendingReport(fireId, idempotencyKey, "LOCAL_ALARM"))
                            ReportWorker.schedule(this@RingActivity, fireId)
                        }
                    }
                }

                AlarmScreen(
                    peerName = "惦记你的人",
                    title = title,
                    copyLine = copyLine,
                    kindLabel = "布谷来叫你啦",
                    elderMode = false,
                    onConfirmed = { ack("DONE") },
                    onSnoozed = { ack("SNOOZE", snoozeMinutes = 15) },
                    isSelf = selfReminder,
                    confirmLabel = confirmLabel,
                )
            }
        }
    }

    override fun onDestroy() {
        stopRinging()
        guardFireId?.let { activeFireIds.remove(it) }
        guardFireId = null
        super.onDestroy()
    }

    private fun requestAudioFocus(audioAttributes: AudioAttributes) {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change ->
                // 闹钟优先:被抢占/闪避时也不主动调低音量或停止;仅记录供诊断。
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
        // repeat=0 表示从头循环整段波形,直到 cancel。
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
        com.bugu.schedule.AlarmNotifications.cancel(
            this,
            intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1),
        )
    }

    private companion object {
        /** 同 fire 去重;不同 fire 仍各自独立响铃。 */
        val activeFireIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    }
}

/** suspend 友好 try/catch(CancellationException 透传)。 */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
