package com.bugu.kuikly

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.bugu.ApiFactory
import com.bugu.PushReporter
import com.bugu.deeplink.InviteDeepLink
import com.bugu.schedule.ReminderSync
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.context.KuiklyRenderCoreExecuteModeBase
import com.tencent.kuikly.core.render.android.exception.ErrorReason
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 真实 App 的 Kuikly 壳:页面装载、路由栈、看板 payload、登录态恢复与深链回填。
 *
 * 职责迁移自 AppRoot(Compose):会话恢复 / PushReporter / ReminderSync / 深链消费 / 返回栈;
 * AppRoot.kt 本轮保留不动,待段三统一下线。
 */
class BuguKuiklyHost(
    private val activity: ComponentActivity,
    private val container: ViewGroup,
) {

    private var delegator: KuiklyRenderViewBaseDelegator? = null
    private var currentPage: String = ""

    /** 路由栈(routerOpen 入栈,routerReplace 清栈);返回键按栈回退。 */
    private val stack = ArrayDeque<PageEntry>()

    private val prefs by lazy { activity.getSharedPreferences("bugu", Context.MODE_PRIVATE) }
    private val deepPrefs by lazy { activity.getSharedPreferences("bugu_main", Context.MODE_PRIVATE) }

    private fun token(): String = prefs.getString("jwt", "") ?: ""

    fun onCreate(initialPage: String? = null, initialParams: String = "{}") {
        INSTANCE = this
        // 深链解析结果的统一出口:合法码已写入 pending 槽由登录页/亲友页回填;
        // 非法码(缺码、非 6 位等)此前在 Kuikly 壳里无人消费而静默,这里补一次明确提示。
        activity.lifecycleScope.launch {
            InviteDeepLink.events.collect { event ->
                when (event) {
                    is InviteDeepLink.Event.Invalid -> {
                        Log.i(TAG, "deeplink invalid: ${event.message}")
                        Toast.makeText(activity, event.message, Toast.LENGTH_LONG).show()
                    }
                    is InviteDeepLink.Event.Code -> Unit
                    null -> Unit
                }
                InviteDeepLink.clearEvent()
            }
        }
        container.setBackgroundColor(Color.parseColor("#FAF8F5"))
        // 系统栏兜底:页面用固定 top padding,底部由壳统一避让导航栏,避免末行按钮/入口被覆盖
        container.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(0, 0, 0, bottom)
            insets
        }
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            },
        )
        if (initialPage != null) {
            // debug 联验入口(等价于探针壳的 --es page):跳过会话恢复,直开指定页
            Log.i(TAG, "debug direct page=$initialPage params=$initialParams")
            if (initialPage == "BuguRealDashboardPage") openDashboard() else routeTo(initialPage, initialParams, replace = true)
            return
        }
        activity.lifecycleScope.launch { restoreSession() }
    }

    /** 启动分流:无 token → 登录页;有 token → 校验 me() 后进看板(深链待处理码优先回填)。 */
    private suspend fun restoreSession() {
        val pending = InviteDeepLink.pendingCode(activity)
        if (token().isEmpty()) {
            Log.i(TAG, "boot token=<empty> pending=${pending ?: "-"} -> login")
            attach("BuguLoginPage", emptyMap(), replaceAll = true)
            return
        }
        val api = ApiFactory.create(activity)
        val me = runCatching { api.me() }
        val error = me.exceptionOrNull()
        when {
            error != null && error.code() == "UNAUTHORIZED" -> {
                Log.i(TAG, "boot me() UNAUTHORIZED -> clear token -> login")
                prefs.edit().remove("jwt").commit()
                attach("BuguLoginPage", emptyMap(), replaceAll = true)
            }
            else -> {
                if (me.isSuccess) {
                    Log.i(TAG, "boot me() ok user=${me.getOrNull()?.nickname}")
                    startBackgroundSync(api)
                } else {
                    // 网络故障保留 token:页面自带错误态,不把「已登录」误降级为未登录
                    Log.e(TAG, "boot me() network error: ${error?.message}")
                }
                if (pending != null) {
                    InviteDeepLink.clearPending(activity)
                    Log.i(TAG, "boot pending code=$pending -> friends")
                    attach("BuguFriendsPage", mapOf("code" to pending), replaceAll = true)
                } else {
                    openDashboard()
                }
            }
        }
    }

    /** 热启动深链:已登录进亲友页并回填邀请码,未登录回登录页(登录页挂载时 takeCode)。 */
    fun onNewIntent(intent: Intent?) {
        val pending = InviteDeepLink.pendingCode(activity) ?: return
        if (token().isEmpty()) {
            Log.i(TAG, "deeplink(hot) code=$pending -> login prefill")
            attach("BuguLoginPage", emptyMap(), replaceAll = true)
            return
        }
        InviteDeepLink.clearPending(activity)
        Log.i(TAG, "deeplink(hot) code=$pending -> friends")
        attach("BuguFriendsPage", mapOf("code" to pending), replaceAll = false)
    }

    // ---- 导航 ----

    private fun openDashboard() {
        // 首次通知权限门:只拦「还没过门」的登录态。过了这道门之后,即便用户之后在系统设置里
        // 关掉通知,也不再阻断,只由看板顶部红条提示(见 BuguRealDashboardPage 的通知横幅)。
        if (token().isNotEmpty() && !BuguBridgeNativeModule.permissionGatePassed(activity)) {
            Log.i(TAG, "permission gate not passed -> BuguPermissionGuidePage(gate=1)")
            attach("BuguPermissionGuidePage", mapOf("gate" to "1"), replaceAll = true)
            return
        }
        val t = token()
        activity.lifecycleScope.launch {
            // 看板 payload 走阻塞 HTTP,必须切 IO,主线程会抛 NetworkOnMainThreadException
            val payload = withContext(Dispatchers.IO) {
                runCatching { buildPayload(t) }.getOrElse {
                    Log.e(TAG, "dashboard payload failed: ${it.message}", it)
                    """{"error":"${it.javaClass.simpleName}: ${it.message ?: "请求失败"}"}"""
                }
            }
            Log.i(TAG, "dashboard payloadLen=${payload.length}")
            attach("BuguRealDashboardPage", mapOf("payload" to payload), replaceAll = true)
        }
    }

    fun routeTo(page: String, paramsJson: String, replace: Boolean) {
        if (page !in BuguBridgeNativeModule.KNOWN_PAGES) return
        Log.i(TAG, "route ${if (replace) "replace" else "open"} $page params=$paramsJson")
        if (page == "BuguRealDashboardPage") {
            stack.clear()
            openDashboard()
            return
        }
        val extra = HashMap<String, Any>()
        runCatching {
            val o = JSONObject(paramsJson)
            o.keys().forEach { k -> extra[k] = o.optString(k) }
        }
        attach(page, extra, replaceAll = replace)
    }

    private fun attach(page: String, extra: Map<String, Any>, replaceAll: Boolean) {
        delegator?.onDetach()
        delegator = null
        if (replaceAll) stack.clear()
        // 同页同参重复入栈(重载/重复点击)时替换栈顶,避免返回键把本页再 attach 一遍
        val last = stack.lastOrNull()
        if (last != null && last.page == page && last.params == extra) stack.removeLast()
        stack.addLast(PageEntry(page, extra))
        currentPage = page
        val data = HashMap<String, Any>(extra)
        data["appId"] = 1
        val newDelegator = KuiklyRenderViewBaseDelegator(
            object : KuiklyRenderViewBaseDelegatorDelegate {
                override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
                    super.registerExternalModule(kuiklyRenderExport)
                    kuiklyRenderExport.moduleExport(BuguBridgeNativeModule.MODULE_NAME) {
                        BuguBridgeNativeModule()
                    }
                }

                override fun onPageLoadComplete(
                    isSucceed: Boolean,
                    errorReason: ErrorReason?,
                    executeMode: KuiklyRenderCoreExecuteModeBase,
                ) {
                    Log.i(TAG, "onPageLoadComplete page=$page isSucceed=$isSucceed errorReason=$errorReason")
                }
            },
        )
        delegator = newDelegator
        Log.i(TAG, "attach page=$page")
        newDelegator.onAttach(container, "", page, data)
    }

    private fun onBack() {
        // 先交给 Kuikly 页面内的返回键回调(如登录页协议门 Dialog 的 BackHandler):
        // 页面消费(如只关弹窗)时不再走路由回退,避免弹窗按返回键把整页退栈重载。
        if (delegator?.onBackPressed() == true) return
        val now = System.currentTimeMillis()
        when {
            stack.size > 1 -> {
                // 当前页与目标页都出栈,由 attach 重新压入唯一一份,避免栈内同页叠加导致返回键重载本页
                stack.removeLast()
                val previous = stack.removeLast()
                attach(previous.page, previous.params, replaceAll = false)
            }
            currentPage != "BuguRealDashboardPage" && currentPage.isNotEmpty() -> {
                stack.clear()
                openDashboard()
            }
            now - lastBackAt < 2000 -> activity.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(activity, "再按一次退出布谷", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onResume() = delegator?.onResume() ?: Unit
    fun onPause() = delegator?.onPause() ?: Unit

    fun onDestroy() {
        delegator?.onDetach()
        delegator = null
        if (INSTANCE === this) INSTANCE = null
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == REQ_NOTI || requestCode == REQ_MIC) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "permission result code=$requestCode granted=$granted")
            permissionResultSink?.invoke(granted)
            permissionResultSink = null
        }
    }

    // ---- 登录态联动(与 AppRoot 的 restoreSession / logout 一致)----

    private fun startBackgroundSync(api: com.bugu.client.api.GeitaApi) {
        val ctx = activity.applicationContext
        PushReporter.launch(ctx, api)
        ReminderSync.schedulePeriodic(ctx)
        activity.lifecycleScope.launch {
            runCatching { ReminderSync.sync(ctx, api) }
                .onFailure { Log.e(TAG, "ReminderSync.sync failed: ${it.message}") }
        }
    }

    // ---- 看板 payload:原生拉真实接口归一化(与探针壳同构)----

    private fun buildPayload(token: String): String {
        val baseUrl = ApiFactory.BASE_URL + "/api/v1"
        val me = getEnvelopeData(baseUrl, token, "/auth/me") as JSONObject
        val bonds = getEnvelopeData(baseUrl, token, "/bond") as JSONArray
        val incoming = getEnvelopeData(baseUrl, token, "/reminder/incoming") as JSONArray
        val mine = getEnvelopeData(baseUrl, token, "/reminder/mine") as JSONArray
        // 进行中提醒(长辈模式当前提醒);普通账号返回 data=null,忽略即可
        val current = runCatching {
            (getEnvelopeData(baseUrl, token, "/reminder/current") as? JSONObject)
        }.getOrNull()

        val nicknames = HashMap<String, String>()
        // /bond 同时返回 ACTIVE 与本人发出的 PENDING_OUTBOUND(BondService.listMine);
        // 「已和 N 位家人连上」与亲友页/我的页同口径,只统计 ACTIVE,邀请中不计入。
        var activeBonds = 0
        for (i in 0 until bonds.length()) {
            val bond = bonds.optJSONObject(i) ?: continue
            if (bond.optString("status") == "ACTIVE") activeBonds++
            nicknames[bond.optString("counterpartId")] = bond.optString("counterpartNickname")
        }
        return JSONObject().apply {
            put("nickname", me.optString("nickname", "家人"))
            put("bonds", activeBonds)
            put("incoming", normalize(incoming, "creatorId", "from", nicknames))
            put("mine", normalize(mine, "receiverId", "to", nicknames))
            current?.let { put("live", normalizeOne(it, "creatorId", "from", nicknames)) }
        }.toString()
    }

    private fun normalize(
        source: JSONArray,
        personIdKey: String,
        personKey: String,
        nicknames: Map<String, String>,
    ): JSONArray {
        val result = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            result.put(
                JSONObject().apply {
                    put("id", item.optString("id"))
                    put("title", item.optString("title"))
                    put("fireAt", item.optString("fireAt"))
                    put("status", item.optString("status"))
                    put("fireId", item.optLong("fireId", -1L))
                    // 与服务端 ReminderView 同名字段:类型/语气/重复规则,看板信息行直接消费
                    put("kind", item.optString("kind", "ONCE"))
                    put("tone", item.optString("tone", "GENTLE"))
                    put("repeatRule", item.optString("repeatRule", ""))
                    put(personKey, nicknames[item.optString(personIdKey)] ?: "家人")
                },
            )
        }
        return result
    }

    private fun normalizeOne(
        item: JSONObject,
        personIdKey: String,
        personKey: String,
        nicknames: Map<String, String>,
    ): JSONObject = JSONObject().apply {
        put("id", item.optString("id"))
        put("title", item.optString("title"))
        put("fireAt", item.optString("fireAt"))
        put("status", item.optString("status"))
        put("fireId", item.optLong("fireId", -1L))
        put("kind", item.optString("kind", "ONCE"))
        put("tone", item.optString("tone", "GENTLE"))
        put("repeatRule", item.optString("repeatRule", ""))
        put(personKey, nicknames[item.optString(personIdKey)] ?: "家人")
    }

    private fun getEnvelopeData(baseUrl: String, token: String, path: String): Any {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val envelope = JSONObject(body)
            if (envelope.optString("code") != "OK") {
                throw IllegalStateException("$path 返回 $code/${envelope.optString("message")}")
            }
            return envelope.opt("data") ?: throw IllegalStateException("$path 缺少 data")
        } finally {
            conn.disconnect()
        }
    }

    private data class PageEntry(val page: String, val params: Map<String, Any>)

    companion object {
        private const val TAG = "BuguKuiklyHost"
        private const val REQ_NOTI = 1001
        private const val REQ_MIC = 1002
        private var lastBackAt = 0L

        @Volatile
        private var INSTANCE: BuguKuiklyHost? = null

        /** 系统权限弹窗结果出口(模块线程回调,由宿主转发)。 */
        var permissionResultSink: ((Boolean) -> Unit)? = null

        fun route(page: String, paramsJson: String, replace: Boolean) {
            val host = INSTANCE ?: return
            host.activity.runOnUiThread { host.routeTo(page, paramsJson, replace) }
        }

        /** 登录成功(bridge.save):启动推送上报与端侧闹钟同步。 */
        fun onLoggedIn(activity: Activity?) {
            val ctx = activity?.applicationContext ?: return
            val app = activity as? ComponentActivity ?: return
            val api = ApiFactory.create(ctx)
            PushReporter.launch(ctx, api)
            ReminderSync.schedulePeriodic(ctx)
            app.lifecycleScope.launch {
                runCatching { ReminderSync.sync(ctx, api) }
                    .onFailure { Log.e(TAG, "post-login sync failed: ${it.message}") }
            }
        }

        /** 401 时清 token 后回到登录页(Kuikly 侧同时收到 bugu.auth.expired 事件)。 */
        fun onSessionExpired() {
            val host = INSTANCE ?: return
            host.activity.runOnUiThread {
                host.prefs.edit().remove("jwt").commit()
                host.attach("BuguLoginPage", emptyMap(), replaceAll = true)
            }
        }
    }
}

/** GeitaApiException.code 的安全读取(异常类型在 client 层)。 */
private fun Throwable.code(): String? =
    (this as? com.bugu.client.api.GeitaApiException)?.code
