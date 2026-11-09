package com.bugu.kuikly

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bugu.ApiFactory
import com.bugu.PushReporter
import com.bugu.schedule.AlarmScheduler
import com.bugu.schedule.ReminderSync
import com.bugu.ui.RingActivity
import com.tencent.kuikly.core.render.android.KuiklyRenderViewContext
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * bugu_bridge 原生实现:登录态 / 网络(401 统一出口) / 权限 / 深链码 / 响铃 / 路由。
 * 契约见 KUIKLY_全量迁移_方案与Bridge契约.md 第四章。
 *
 * 与探针壳的差异:登录态复用真实 App 的 SharedPreferences("bugu"/jwt),接口地址取
 * [ApiFactory.BASE_URL];登录成功/退出登录时联动 PushReporter 与 ReminderSync。
 */
class BuguBridgeNativeModule : KuiklyRenderBaseModule() {

    private val netPool = Executors.newFixedThreadPool(2)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /** 头像相册选择器(惰性注册,key 按模块实例唯一,避免重复注册崩溃)。 */
    private var avatarLauncher: ActivityResultLauncher<String>? = null
    private var pendingAvatarCallback: KuiklyRenderCallback? = null

    /** C5 语音识别:同一时刻只允许一个识别会话。 */
    private var pendingVoiceCallback: KuiklyRenderCallback? = null

    init {
        ensureImageAdapter()
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        Log.i(TAG, "call method=$method thread=${Thread.currentThread().name}")
        val json = runCatching { JSONObject(params ?: "{}") }.getOrDefault(JSONObject())
        when (method) {
            "token" -> callback?.invoke(mapOf("token" to token()))
            "save" -> {
                prefs().edit().putString(KEY_JWT, json.optString("token")).commit()
                Log.i(TAG, "auth.save ok=true")
                BuguKuiklyHost.onLoggedIn(activity as? android.app.Activity)
                callback?.invoke(mapOf("ok" to true))
            }
            "clear" -> {
                prefs().edit().remove(KEY_JWT).commit()
                (activity as? android.app.Activity)?.let { ReminderSync.clearLocalState(it) }
                Log.i(TAG, "auth.clear ok=true")
                callback?.invoke(mapOf("ok" to true))
            }
            "netGet" -> net(json.optString("path"), "GET", null, callback)
            "netPost" -> net(json.optString("path"), "POST", json.optString("body", "{}"), callback)
            "netRequest" -> {
                val httpMethod = json.optString("method", "GET").uppercase()
                val body = if (httpMethod == "GET" ||
                    httpMethod == "DELETE" && json.optString("body").isEmpty()
                ) {
                    null
                } else {
                    json.optString("body", "{}")
                }
                net(json.optString("path"), httpMethod, body, callback)
            }
            "careModeGet" -> {
                val enabled = mainPrefs().getBoolean(KEY_CARE_MODE, false)
                Log.i(TAG, "careMode.get=$enabled")
                callback?.invoke(mapOf("enabled" to enabled))
            }
            "careModeSet" -> {
                mainPrefs().edit().putBoolean(KEY_CARE_MODE, json.optBoolean("enabled")).commit()
                Log.i(TAG, "careMode.set=${json.optBoolean("enabled")}")
                callback?.invoke(mapOf("ok" to true))
            }
            "draftGet" -> {
                val raw = mainPrefs().getString(KEY_CREATE_DRAFT, "") ?: ""
                Log.i(TAG, "draft.get len=${raw.length}")
                if (raw.isNotEmpty()) {
                    callback?.invoke(mapOf("ok" to true, "json" to raw))
                } else {
                    callback?.invoke(mapOf("ok" to true))
                }
            }
            "draftSet" -> {
                mainPrefs().edit().putString(KEY_CREATE_DRAFT, json.optString("json")).apply()
                Log.i(TAG, "draft.set len=${json.optString("json").length}")
                callback?.invoke(mapOf("ok" to true))
            }
            "draftClear" -> {
                mainPrefs().edit().remove(KEY_CREATE_DRAFT).apply()
                Log.i(TAG, "draft.clear ok=true")
                callback?.invoke(mapOf("ok" to true))
            }
            "dismissedFiresGet" -> {
                val ids = mainPrefs().getString(KEY_EXPIRED_FIRES, "") ?: ""
                Log.i(TAG, "dismissedFires.get ids=$ids")
                callback?.invoke(mapOf("ok" to true, "ids" to ids))
            }
            "dismissedFiresSet" -> {
                val ids = json.optString("ids")
                mainPrefs().edit().putString(KEY_EXPIRED_FIRES, ids).apply()
                Log.i(TAG, "dismissedFires.set ids=$ids")
                callback?.invoke(mapOf("ok" to true))
            }
            "agreeGet" -> {
                val accepted = mainPrefs().getBoolean(KEY_AGREEMENT_ACCEPTED, false)
                Log.i(TAG, "agreement.get=$accepted")
                callback?.invoke(mapOf("ok" to true, "accepted" to accepted))
            }
            "agreeSet" -> {
                mainPrefs().edit().putBoolean(KEY_AGREEMENT_ACCEPTED, json.optBoolean("accepted")).apply()
                Log.i(TAG, "agreement.set=${json.optBoolean("accepted")}")
                callback?.invoke(mapOf("ok" to true))
            }
            "codeSentAtGet" -> {
                val at = mainPrefs().getLong(KEY_CODE_SENT_AT, 0L)
                Log.i(TAG, "codeSentAt.get=$at")
                callback?.invoke(mapOf("ok" to true, "at" to at))
            }
            "codeSentAtSet" -> {
                val at = json.optLong("at", 0L)
                mainPrefs().edit().putLong(KEY_CODE_SENT_AT, at).apply()
                Log.i(TAG, "codeSentAt.set=$at")
                callback?.invoke(mapOf("ok" to true))
            }
            "appVersion" -> {
                val ctx = context!!
                val v = runCatching {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                }.getOrDefault("")
                Log.i(TAG, "app.version=$v")
                callback?.invoke(mapOf("version" to v))
            }
            "testNotify" -> testNotify(json.optLong("delayMs", 0L), callback)
            "pickAvatar" -> pickAvatar(callback)
            "permissionStatus" -> permissionStatus(callback)
            "permissionRequest" -> permissionRequest(json.optString("kind"), callback, json.optString("channelId"))
            "openSettings" -> openSettings(json.optString("kind"), callback)
            "voiceInput" -> voiceInput(callback)
            "takeCode" -> {
                val code = deepPrefs().getString(KEY_PENDING_CODE, "") ?: ""
                Log.i(TAG, "deeplink.takeCode -> '$code'")
                callback?.invoke(mapOf("code" to code))
            }
            "clearCode" -> {
                deepPrefs().edit().remove(KEY_PENDING_CODE).commit()
                Log.i(TAG, "deeplink.clearCode ok=true")
                callback?.invoke(mapOf("ok" to true))
            }
            "ringOpen" -> ringOpen(json, callback)
            "alarmResync" -> {
                val act = activity as? android.app.Activity
                if (act == null) {
                    callback?.invoke(mapOf("ok" to false, "code" to "ALARM_SYNC_FAILED", "message" to "宿主未就绪"))
                } else {
                    ioScope.launch {
                        val result = runCatching { ReminderSync.sync(act, ApiFactory.create(act)) }
                        result.onSuccess { Log.i(TAG, "alarm.resync ok count=${it.size}") }
                            .onFailure { Log.e(TAG, "alarm.resync failed: ${it.message}") }
                        callback?.invoke(mapOf("ok" to result.isSuccess))
                    }
                }
            }
            "routerOpen" -> routerOpen(json, callback)
            "toast" -> {
                val host = activity
                (host as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(host, json.optString("content"), Toast.LENGTH_SHORT).show()
                }
            }
            "copyText" -> {
                val ctx = context!!
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("bugu", json.optString("content")))
                Log.i(TAG, "copyText ok len=${json.optString("content").length}")
                callback?.invoke(mapOf("ok" to true))
            }
            "shareText" -> {
                val host = activity
                (host as? android.app.Activity)?.runOnUiThread {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, json.optString("content"))
                    }
                    runCatching {
                        host.startActivity(Intent.createChooser(send, json.optString("title", "分享邀请")))
                        callback?.invoke(mapOf("ok" to true))
                    }.onFailure {
                        Log.e(TAG, "shareText failed: ${it.message}")
                        callback?.invoke(mapOf("ok" to false, "message" to (it.message ?: "无法分享")))
                    }
                }
            }
            else -> {
                Log.e(TAG, "方法不存在: $method")
                callback?.invoke(mapOf("ok" to false, "code" to "METHOD_NOT_FOUND", "message" to "方法不存在"))
            }
        }
        return null
    }

    // ---- 登录态(与原生 SharedPrefsTokenStore 同一份存储)----

    private fun prefs() = context!!.getSharedPreferences("bugu", Context.MODE_PRIVATE)
    private fun deepPrefs() = context!!.getSharedPreferences("bugu_main", Context.MODE_PRIVATE)
    private fun mainPrefs() = context!!.getSharedPreferences("bugu_main", Context.MODE_PRIVATE)
    private fun token(): String = prefs().getString(KEY_JWT, "") ?: ""

    /** 本机测试通知:只验证通知权限与送达,不触碰正式提醒与响铃渠道。 */
    private fun testNotify(delayMs: Long, callback: KuiklyRenderCallback?) {
        val ctx = context!!
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "testNotify -> PERMISSION_DENIED")
            callback?.invoke(mapOf("ok" to false, "code" to "PERMISSION_DENIED", "message" to "通知权限未开启"))
            return
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_TEST, "本机测试通知", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val deliver = {
            val notification = android.app.Notification.Builder(ctx, CHANNEL_TEST)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("咕咕的本机测试提醒")
                .setContentText("布谷本机通知已送达，看到这条就说明系统通知链路是通的。")
                .setAutoCancel(true)
                .build()
            nm.notify(TEST_NOTIFY_ID, notification)
            Log.i(TAG, "testNotify ok id=$TEST_NOTIFY_ID")
            Unit
        }
        if (delayMs > 0L) {
            Log.i(TAG, "testNotify scheduled in ${delayMs}ms")
            Handler(Looper.getMainLooper()).postDelayed({ deliver() }, delayMs)
        } else {
            deliver()
        }
        callback?.invoke(mapOf("ok" to true, "id" to TEST_NOTIFY_ID, "delayMs" to delayMs))
    }

    // ---- 头像更换:系统相册选图 → 申请直传凭证 → COS 直传 → confirm ----

    private fun pickAvatar(callback: KuiklyRenderCallback?) {
        if (pendingAvatarCallback != null) {
            callback?.invoke(mapOf("ok" to false, "code" to "BUSY", "message" to "正在选择图片,请稍候"))
            return
        }
        val act = activity as? ComponentActivity
        if (act == null) {
            Log.e(TAG, "avatar.pick host activity unavailable")
            callback?.invoke(mapOf("ok" to false, "code" to "NO_HOST", "message" to "当前环境不支持选择图片"))
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            act.runOnUiThread { pickAvatar(callback) }
            return
        }
        pendingAvatarCallback = callback
        val launcher = avatarLauncher ?: runCatching {
            // GetContent 走系统相册选择器,无需存储权限。按实例唯一 key 惰性注册;
            // 不用带 LifecycleOwner 的重载(Activity 已 STARTED 时会抛异常)。
            act.activityResultRegistry.register(
                "bugu_pick_avatar_${System.identityHashCode(this)}",
                ActivityResultContracts.GetContent(),
            ) { uri: Uri? -> onAvatarPicked(uri) }
        }.getOrElse { e ->
            Log.e(TAG, "avatar.pick register failed: $e")
            pendingAvatarCallback = null
            callback?.invoke(mapOf("ok" to false, "code" to "PICK_FAILED", "message" to "相册选择器打开失败,请重试"))
            return
        }.also { avatarLauncher = it }
        runCatching { launcher.launch("image/*") }.onFailure { e ->
            Log.e(TAG, "avatar.pick launch failed: $e")
            pendingAvatarCallback = null
            callback?.invoke(mapOf("ok" to false, "code" to "PICK_FAILED", "message" to "相册选择器打开失败,请重试"))
        }
    }

    private fun onAvatarPicked(uri: Uri?) {
        val cb = pendingAvatarCallback
        pendingAvatarCallback = null
        if (cb == null) return
        if (uri == null) {
            Log.i(TAG, "avatar.pick cancelled")
            cb.invoke(mapOf("ok" to false, "code" to "CANCELLED", "message" to "已取消选择"))
            return
        }
        uploadAvatar(uri, cb)
    }

    /** 三步上传全部在后台线程完成;回调保证只触发一次。 */
    private fun uploadAvatar(uri: Uri, callback: KuiklyRenderCallback?) {
        val ctx = context
        if (ctx == null) {
            callback?.invoke(mapOf("ok" to false, "message" to "网络不稳定,请重试"))
            return
        }
        netPool.execute {
            val replied = AtomicBoolean(false)
            fun reply(result: Map<String, Any?>) {
                if (replied.compareAndSet(false, true)) callback?.invoke(result)
            }

            fun fail(message: String) = reply(mapOf("ok" to false, "message" to message))

            try {
                if (token().isEmpty()) {
                    fail("登录已过期,请重新登录")
                    return@execute
                }
                val rawType = runCatching { ctx.contentResolver.getType(uri) }.getOrNull().orEmpty()
                val mime = rawType.substringBefore(';').trim().lowercase().ifEmpty { "image/jpeg" }
                    .let { if (it == "image/jpg") "image/jpeg" else it }
                if (mime !in AVATAR_TYPES) {
                    fail(if (mime.startsWith("image/")) "仅支持 JPG/PNG/WebP 图片" else "请选择图片文件")
                    return@execute
                }
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("openInputStream returned null")
                Log.i(TAG, "avatar.pick mime=$mime bytes=${bytes.size}")

                val (tokenCode, tokenBody) =
                    postJson("/auth/me/avatar/token", """{"contentType":${JSONObject.quote(mime)}}""")
                if (tokenCode == 401) {
                    expireSession()
                    fail("登录已过期,请重新登录")
                    return@execute
                }
                val tokenEnv = runCatching { JSONObject(tokenBody) }.getOrDefault(JSONObject())
                if (tokenCode !in 200..299 || tokenEnv.optString("code") != "OK") {
                    val msg = tokenEnv.optString("message").ifEmpty { "头像上传失败,稍后再试" }
                    Log.e(TAG, "avatar.token failed http=$tokenCode msg=$msg")
                    fail(msg)
                    return@execute
                }
                val data = tokenEnv.optJSONObject("data") ?: throw IOException("avatar ticket missing")
                val uploadUrl = data.optString("uploadUrl")
                val objectKey = data.optString("objectKey")
                val publicUrl = data.optString("publicUrl")
                val maxBytes = data.optLong("maxBytes", MAX_AVATAR_BYTES)
                if (uploadUrl.isEmpty() || objectKey.isEmpty()) throw IOException("avatar ticket incomplete")
                if (bytes.size > maxBytes) {
                    fail("头像不能超过 2MB")
                    return@execute
                }

                // 表单字段在上,file 必须最后
                val cosCode = postMultipart(uploadUrl, data.optJSONObject("formFields") ?: JSONObject(), mime, bytes)
                if (cosCode !in 200..299) {
                    Log.e(TAG, "avatar.cos failed http=$cosCode")
                    fail("头像上传失败,稍后再试")
                    return@execute
                }

                val (confirmCode, confirmBody) =
                    postJson("/auth/me/avatar/confirm", """{"objectKey":${JSONObject.quote(objectKey)}}""")
                if (confirmCode == 401) {
                    expireSession()
                    fail("登录已过期,请重新登录")
                    return@execute
                }
                val confirmEnv = runCatching { JSONObject(confirmBody) }.getOrDefault(JSONObject())
                if (confirmCode !in 200..299 || confirmEnv.optString("code") != "OK") {
                    val msg = confirmEnv.optString("message").ifEmpty { "头像上传失败,稍后再试" }
                    Log.e(TAG, "avatar.confirm failed http=$confirmCode msg=$msg")
                    fail(msg)
                    return@execute
                }
                val avatarUrl = confirmEnv.optJSONObject("data")?.optString("avatarUrl").orEmpty()
                    .ifEmpty { publicUrl }
                Log.i(TAG, "avatar.confirm ok avatarUrl=$avatarUrl")
                reply(mapOf("ok" to true, "avatarUrl" to avatarUrl, "message" to "头像已更新"))
            } catch (e: IOException) {
                Log.e(TAG, "avatar.upload network failed: $e")
                fail("网络不稳定,请重试")
            } catch (e: Exception) {
                Log.e(TAG, "avatar.upload failed: $e")
                fail("头像上传失败,稍后再试")
            }
        }
    }

    /** 与 net() 的 401 出口一致:清本地 token 并广播登录失效。 */
    private fun expireSession() {
        prefs().edit().remove(KEY_JWT).commit()
        val ctx = context ?: return
        ctx.sendBroadcast(
            Intent("com.tencent.kuikly.broadcast.hr.notify").apply {
                setPackage(ctx.packageName)
                putExtra("eventName", EVENT_AUTH_EXPIRED)
                putExtra("data", "{}")
            },
        )
    }

    private fun postJson(path: String, body: String): Pair<Int, String> {
        val baseUrl = ApiFactory.BASE_URL + "/api/v1"
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val t = token()
            if (t.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $t")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    /** COS PostObject:签名表单字段在前,file 字段(last)的 Content-Type 用实际 mime。 */
    private fun postMultipart(url: String, fields: JSONObject, mime: String, bytes: ByteArray): Int {
        val boundary = "----BuguAvatar${System.currentTimeMillis()}${System.nanoTime()}"
        val fieldParts = mutableListOf<Pair<String, String>>()
        val keys = fields.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            fieldParts.add(k to fields.optString(k))
        }
        fun partHeader(name: String): ByteArray =
            "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n"
                .toByteArray(Charsets.UTF_8)

        val fileHeader = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"avatar\"\r\n" +
                "Content-Type: $mime\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        var contentLength = 0L
        fieldParts.forEach { (k, v) ->
            contentLength += partHeader(k).size + v.toByteArray(Charsets.UTF_8).size + 2
        }
        contentLength += fileHeader.size + bytes.size + tail.size

        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setFixedLengthStreamingMode(contentLength)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                fieldParts.forEach { (k, v) ->
                    out.write(partHeader(k))
                    out.write(v.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray(Charsets.UTF_8))
                }
                out.write(fileHeader)
                out.write(bytes)
                out.write(tail)
                out.flush()
            }
            val code = conn.responseCode
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
            code
        } finally {
            conn.disconnect()
        }
    }

    // ---- 网络:统一 envelope 解析 + 401 出口 ----

    private fun net(path: String, method: String, body: String?, callback: KuiklyRenderCallback?) {
        Log.i(TAG, "net start path=$path httpMethod=$method")
        netPool.execute {
            val baseUrl = ApiFactory.BASE_URL + "/api/v1"
            var code = 0
            try {
                val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val t = token()
                if (t.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $t")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                code = conn.responseCode
                Log.i(TAG, "net done path=$path httpCode=$code")
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (code == 401) {
                    val envelope401 = runCatching { JSONObject(resp) }.getOrDefault(JSONObject())
                    prefs().edit().remove(KEY_JWT).commit()
                    Log.i(TAG, "HTTP 401 at $path -> clear token + broadcast $EVENT_AUTH_EXPIRED")
                    val ctx = context!!
                    ctx.sendBroadcast(
                        Intent("com.tencent.kuikly.broadcast.hr.notify").apply {
                            setPackage(ctx.packageName)
                            putExtra("eventName", EVENT_AUTH_EXPIRED)
                            putExtra("data", "{}")
                        },
                    )
                    callback?.invoke(
                        mapOf(
                            "ok" to false,
                            "code" to "HTTP_401",
                            "message" to envelope401.optString("message", "登录态已失效"),
                        ),
                    )
                    return@execute
                }

                val envelope = runCatching { JSONObject(resp) }.getOrDefault(JSONObject())
                if (code in 200..299 && envelope.optString("code") == "OK") {
                    callback?.invoke(
                        mapOf("ok" to true, "data" to (envelope.opt("data")?.toString() ?: "")),
                    )
                } else {
                    callback?.invoke(
                        mapOf(
                            "ok" to false,
                            "code" to "HTTP_$code",
                            "message" to envelope.optString("message", "请求失败"),
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "net $path failed: $e")
                callback?.invoke(
                    mapOf("ok" to false, "code" to "NET_UNREACHABLE", "message" to "${e.javaClass.simpleName}: ${e.message}"),
                )
            }
        }
    }

    // ---- 响铃:交给原生 RingActivity(契约 4.3,不在 Kuikly 内自绘)----

    private fun ringOpen(json: JSONObject, callback: KuiklyRenderCallback?) {
        val act = activity as? android.app.Activity
        val fireId = json.optLong("fireId", 0L)
        if (act == null || fireId <= 0L) {
            Log.i(TAG, "ring.open fireId=$fireId -> RING_NOT_FOUND")
            callback?.invoke(mapOf("ok" to false, "code" to "RING_NOT_FOUND", "message" to "提醒已撤销或已完成"))
            return
        }
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val intent = Intent(act, RingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_FIRE_ID, fireId)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, json.optLong("reminderId", -1L))
            putExtra(AlarmScheduler.EXTRA_TITLE, json.optString("title", "到点了"))
            putExtra(AlarmScheduler.EXTRA_FIRE_AT, json.optString("fireAt", now))
            putExtra(AlarmScheduler.EXTRA_IDEMPOTENCY_KEY, "kuikly-$fireId")
            putExtra(AlarmScheduler.EXTRA_VOICE, json.optBoolean("voice", false))
            putExtra(AlarmScheduler.EXTRA_KIND, json.optString("kind", "ONCE"))
            putExtra(AlarmScheduler.EXTRA_SELF, json.optBoolean("selfReminder", true))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { act.startActivity(intent) }.isSuccess
        Log.i(TAG, "ring.open fireId=$fireId opened=$opened")
        if (opened) {
            callback?.invoke(mapOf("ok" to true))
        } else {
            callback?.invoke(mapOf("ok" to false, "code" to "RING_NOT_FOUND", "message" to "无法打开响铃页"))
        }
    }

    // ---- 权限 ----

    private fun permissionStatus(callback: KuiklyRenderCallback?) {
        val ctx = context!!
        val notification = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fullScreen = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val channels = org.json.JSONArray()
        if (Build.VERSION.SDK_INT >= 26) {
            for (channel in nm.notificationChannels) {
                channels.put(
                    org.json.JSONObject()
                        .put("id", channel.id)
                        .put("name", channel.name?.toString() ?: channel.id)
                        .put("importance", channel.importance),
                )
            }
        }
        callback?.invoke(
            mapOf(
                "notification" to notification,
                "exactAlarm" to exact,
                "batteryUnrestricted" to battery,
                "fullScreen" to fullScreen,
                "sdkInt" to Build.VERSION.SDK_INT,
                "channels" to channels.toString(),
            ),
        )
    }

    private fun permissionRequest(kind: String, callback: KuiklyRenderCallback?, channelId: String = "") {
        val act = activity as? android.app.Activity
        if (act == null) {
            callback?.invoke(mapOf("granted" to false, "needSettings" to false, "code" to "PERMISSION_UNSUPPORTED"))
            return
        }
        when (kind) {
            "notification" -> {
                if (Build.VERSION.SDK_INT < 33) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                    return
                }
                BuguKuiklyHost.permissionResultSink = { granted ->
                    Log.i(TAG, "permission notification granted=$granted")
                    callback?.invoke(mapOf("granted" to granted, "needSettings" to !granted))
                }
                act.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTI)
            }
            "exactAlarm" -> {
                val am = act.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                } else if (Build.VERSION.SDK_INT >= 31) {
                    act.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${act.packageName}")),
                    )
                    callback?.invoke(mapOf("granted" to false, "needSettings" to true))
                } else {
                    callback?.invoke(mapOf("granted" to false, "needSettings" to false, "code" to "PERMISSION_UNSUPPORTED"))
                }
            }
            "battery" -> {
                val pm = act.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(act.packageName)) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                } else {
                    act.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${act.packageName}")),
                    )
                    callback?.invoke(mapOf("granted" to false, "needSettings" to true))
                }
            }
            "fullScreen" -> {
                if (Build.VERSION.SDK_INT < 34) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                    return
                }
                val nm = act.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.canUseFullScreenIntent()) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                } else {
                    act.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${act.packageName}")),
                    )
                    callback?.invoke(mapOf("granted" to false, "needSettings" to true))
                }
            }
            "notificationChannel" -> {
                if (Build.VERSION.SDK_INT < 26 || channelId.isEmpty()) {
                    callback?.invoke(mapOf("granted" to false, "needSettings" to false, "code" to "PERMISSION_UNSUPPORTED"))
                } else {
                    act.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, act.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
                    )
                    callback?.invoke(mapOf("granted" to false, "needSettings" to true))
                }
            }
            "microphone" -> {
                if (ContextCompat.checkSelfPermission(act, android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    callback?.invoke(mapOf("granted" to true, "needSettings" to false))
                    return
                }
                BuguKuiklyHost.permissionResultSink = { granted ->
                    Log.i(TAG, "permission microphone granted=$granted")
                    callback?.invoke(mapOf("granted" to granted, "needSettings" to !granted))
                }
                act.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
            }
            else -> callback?.invoke(mapOf("granted" to false, "needSettings" to false, "code" to "PERMISSION_UNSUPPORTED"))
        }
    }

    /** 权限永久拒绝后的系统设置直达(旧版通知横幅点击行为)。 */
    private fun openSettings(kind: String, callback: KuiklyRenderCallback?) {
        val act = activity as? android.app.Activity
        if (act == null) {
            callback?.invoke(mapOf("ok" to false, "code" to "SETTINGS_UNSUPPORTED", "message" to "宿主未就绪"))
            return
        }
        val target = when (kind) {
            "notification" -> if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, act.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}"))
            }
            "exactAlarm" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${act.packageName}"))
            "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}"))
        }
        val opened = runCatching { act.startActivity(target) }.isSuccess
        Log.i(TAG, "settings.open kind=$kind opened=$opened")
        callback?.invoke(mapOf("ok" to opened))
    }

    // ---- C5 语音输入:系统 SpeechRecognizer,结果只回填 AI 浮层文本框 ----

    private fun voiceInput(callback: KuiklyRenderCallback?) {
        val ctx = context
        if (ctx == null) {
            callback?.invoke(
                mapOf("ok" to false, "code" to "NO_SERVICE", "message" to "语音识别没能启动,打字也一样快"),
            )
            return
        }
        if (!runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)) {
            Log.i(TAG, "voice unavailable -> NO_SERVICE")
            callback?.invoke(
                mapOf("ok" to false, "code" to "NO_SERVICE", "message" to "这台设备没有语音识别服务,打字也一样快"),
            )
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "voice -> PERMISSION_REQUIRED")
            callback?.invoke(
                mapOf("ok" to false, "code" to "PERMISSION_REQUIRED", "message" to "需要麦克风权限"),
            )
            return
        }
        // SpeechRecognizer 必须在主线程创建与启动
        Handler(Looper.getMainLooper()).post {
            if (pendingVoiceCallback != null) {
                callback?.invoke(
                    mapOf("ok" to false, "code" to "BUSY", "message" to "语音识别正忙,稍后再试"),
                )
                return@post
            }
            startRecognition(ctx, callback)
        }
    }

    private fun startRecognition(ctx: Context, callback: KuiklyRenderCallback?) {
        val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrElse { e ->
            Log.e(TAG, "voice.create failed: $e")
            callback?.invoke(
                mapOf("ok" to false, "code" to "NO_SERVICE", "message" to "语音识别没能启动,打字也一样快"),
            )
            return
        }
        pendingVoiceCallback = callback
        var finished = false
        fun finish(result: Map<String, Any?>) {
            if (finished) return
            finished = true
            pendingVoiceCallback = null
            runCatching { recognizer.destroy() }
            Log.i(TAG, "voice done ${result["code"] ?: "ok"}")
            callback?.invoke(result)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) {
                    finish(mapOf("ok" to false, "code" to "NO_MATCH", "message" to "没听清,再试一次或直接打字"))
                } else {
                    finish(mapOf("ok" to true, "text" to text))
                }
            }

            override fun onError(error: Int) {
                val (code, message) = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "NO_MATCH" to "没听清,再试一次或直接打字"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "PERMISSION_REQUIRED" to "麦克风权限没开,打字也一样快"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "BUSY" to "语音识别正忙,稍后再试"
                    SpeechRecognizer.ERROR_AUDIO ->
                        "AUDIO" to "没听到声音,再试一次或直接打字"
                    else -> "FAILED" to "语音识别没能启动,打字也一样快"
                }
                Log.e(TAG, "voice error=$error code=$code")
                finish(mapOf("ok" to false, "code" to code, "message" to message))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer.startListening(intent) }.onFailure { e ->
            Log.e(TAG, "voice.start failed: $e")
            finish(
                mapOf(
                    "ok" to false,
                    "code" to if (e is SecurityException) "PERMISSION_REQUIRED" else "NO_SERVICE",
                    "message" to "语音识别没能启动,打字也一样快",
                ),
            )
        }
    }

    // ---- 路由:白名单外 ROUTE_NOT_FOUND + toast,不静默 ----

    private fun routerOpen(json: JSONObject, callback: KuiklyRenderCallback?) {
        val page = json.optString("page")
        val replace = json.optBoolean("replace")
        if (page !in KNOWN_PAGES) {
            Log.i(TAG, "router.open '$page' -> ROUTE_NOT_FOUND")
            val act = activity as? android.app.Activity
            act?.runOnUiThread { Toast.makeText(act, "页面不存在", Toast.LENGTH_SHORT).show() }
            callback?.invoke(mapOf("ok" to false, "code" to "ROUTE_NOT_FOUND", "message" to "页面不存在"))
            return
        }
        Log.i(TAG, "router.${if (replace) "replace" else "open"} -> $page")
        BuguKuiklyHost.route(page, json.optString("params", "{}"), replace)
        callback?.invoke(mapOf("ok" to true))
    }

    companion object {
        private const val TAG = "BuguBridgeNative"
        const val MODULE_NAME = "bugu_bridge"
        const val EVENT_AUTH_EXPIRED = "bugu.auth.expired"
        private const val KEY_JWT = "jwt"
        private const val KEY_PENDING_CODE = "pending_invite_code"
        private const val KEY_CARE_MODE = "care_mode"
        private const val KEY_CREATE_DRAFT = "create_draft"
        private const val KEY_EXPIRED_FIRES = "expired_fire_ids"
        private const val KEY_AGREEMENT_ACCEPTED = "agreement_agreed"
        private const val KEY_CODE_SENT_AT = "code_sent_at"
        private const val CHANNEL_TEST = "bugu_probe_test"
        private const val TEST_NOTIFY_ID = 9001
        private const val REQ_NOTI = 1001
        private const val REQ_MIC = 1002

        /** 与服务端 AvatarPolicy 白名单一致;单张上限取 ticket.maxBytes(默认 2MB)。 */
        private val AVATAR_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private const val MAX_AVATAR_BYTES = 2L * 1024 * 1024

        @Volatile
        private var imageAdapterInstalled = false

        /** 宿主未注册图片适配器时补一个最小实现,否则 Kuikly Image 无法解码远程头像。 */
        private fun ensureImageAdapter() {
            if (imageAdapterInstalled) return
            imageAdapterInstalled = true
            if (KuiklyRenderAdapterManager.krImageAdapter == null) {
                KuiklyRenderAdapterManager.krImageAdapter = BuguImageAdapter()
                Log.i(TAG, "imageAdapter installed")
            }
        }

        val KNOWN_PAGES = setOf(
            "BuguLoginPage",
            "BuguPermissionGuidePage",
            "BuguRealDashboardPage",
            "BuguCreatePage",
            "BuguHistoryPage",
            "BuguCalendarPage",
            "BuguFriendsPage",
            "BuguCompanionPage",
            "BuguProfilePage",
            "BuguProfileDetailsPage",
        )
    }
}

/**
 * 最小图片适配器:http(s)/file/base64 解码为 Drawable,回调切回主线程。
 * 宿主未提供 IKRImageAdapter 时兜底,保证头像等远程图片能上屏。
 */
private class BuguImageAdapter : IKRImageAdapter {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchDrawable(imageLoadOption: HRImageLoadOption, callback: (drawable: Drawable?) -> Unit) {
        val src = imageLoadOption.src
        thread(name = "bugu-image", isDaemon = true) {
            val drawable = runCatching { decode(src) }.getOrNull()
            Handler(Looper.getMainLooper()).post { callback(drawable) }
        }
    }

    private fun decode(src: String): Drawable? {
        val bytes = when {
            src.startsWith(HRImageLoadOption.SCHEME_HTTP) || src.startsWith(HRImageLoadOption.SCHEME_HTTPS) -> {
                val conn = URL(src).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 5000
                    conn.readTimeout = 10000
                    conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
            src.startsWith(HRImageLoadOption.SCHEME_FILE) ->
                File(src.removePrefix(HRImageLoadOption.SCHEME_FILE)).readBytes()
            src.startsWith(HRImageLoadOption.SCHEME_BASE64) ->
                Base64.decode(src.substringAfter(',', ""), Base64.DEFAULT)
            else -> return null
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?.let { BitmapDrawable(Resources.getSystem(), it) }
    }

    override fun getDrawableWidth(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float =
        drawable.intrinsicWidth.toFloat()

    override fun getDrawableHeight(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float =
        drawable.intrinsicHeight.toFloat()
}
