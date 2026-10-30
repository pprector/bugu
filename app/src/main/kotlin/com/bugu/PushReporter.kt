package com.bugu

import android.content.Context
import android.util.Log
import cn.jpush.android.api.JPushInterface
import com.bugu.client.api.GeitaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 极光推送接入(E8-S5,D4 定案 JPush):
 * - init 幂等;AppKey 经 local.properties 的 jpush.appKey 注入(manifestPlaceholder),
 *   无凭据时 SDK 日志报 invalid appKey、registrationID 恒空,上报自然不发生,不崩
 * - init 后轮询 getRegistrationID(极光异步分配,空串=未就绪),拿到即上报 /devices/token(upsert 幂等)
 * - 未登录(UNAUTHORIZED)与网络失败均静默——下次 App 启动会再触发,不弹错不打扰
 * 触发点:MainActivity 冷启动 + BuguApp 登录成功(登录态就绪后上报才有意义)。
 */
object PushReporter {

    private const val POLL_TIMES = 10
    private const val POLL_INTERVAL_MS = 1_000L

    /** 独立 scope:与 UI 生命周期解耦,上报完成后自然结束。 */
    fun launch(context: Context, api: GeitaApi) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { JPushInterface.init(appContext) }
                .onFailure { Log.e(TAG, "JPush init failed", it) }
            val regId = pollRegistrationId(appContext)
            if (regId == null) {
                Log.w(TAG, "registrationID not ready after ${POLL_TIMES}s; will retry next launch")
                return@launch
            }
            runCatching { api.reportDeviceToken(regId) }
                .onSuccess { Log.i(TAG, "device token reported: $regId") }
                .onFailure { Log.w(TAG, "report token failed (will retry next launch)", it) }
        }
    }

    /** 极光异步分配 registrationID,轮询至非空或放弃。 */
    private suspend fun pollRegistrationId(context: Context): String? {
        repeat(POLL_TIMES) { attempt ->
            val id = runCatching { JPushInterface.getRegistrationID(context) }.getOrNull()
            if (!id.isNullOrBlank()) {
                Log.d(TAG, "registrationID ready on attempt ${attempt + 1}")
                return id
            }
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    private const val TAG = "BuguPush"
}
