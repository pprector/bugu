package com.bugu.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val fireId = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE_ID, -1)
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

        // 到点先响「布谷」两声(raw/bugu_call.wav 内含两次鸣叫),走闹钟声道
        bell = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(
                    this@RingActivity,
                    Uri.parse("android.resource://$packageName/${R.raw.bugu_call}"),
                )
                prepare()
                start()
            }
        }.getOrNull()

        // 防抖:确认/小憩只发一次
        var handled = false
        fun ack(action: String, snoozeMinutes: Int? = null) {
            if (handled) return
            handled = true
            scope.launch {
                runCatching { api.ack(AckRequest(fireId, action, snoozeMinutes = snoozeMinutes)) }
                finish()
            }
        }

        // TTS 初始化(系统引擎,离线可播)
        var speakText: String? = null
        if (voice) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
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
        bell?.release()
        bell = null
        tts?.shutdown()
        super.onDestroy()
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
