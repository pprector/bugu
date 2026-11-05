package com.bugu.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 邀请深链解析,支持两种入口:
 * - `bugu://bond?code=xxxxxx`(服务端 InitiateResponse.deeplink 原始协议);
 * - `https://www.yzhzhaohao.cn/bond/?code=xxxxxx`(官网落地页 / App Links,含 `?code` 跳转形式)。
 *
 * - 冷启动:MainActivity.onCreate 取 Intent;
 * - 热启动:MainActivity.onNewIntent 取 Intent;
 * - 未登录:有效码写入待处理槽,登录成功后由 AppRoot 取出继续,不要求用户重输;
 * - 防重复:解析后清空 Intent.data 作为消费标记,同一 Intent 不会被二次处理。
 */
object InviteDeepLink {
    private const val PREFS = "bugu_main"
    private const val KEY_PENDING = "pending_invite_code"
    private const val HTTPS_HOST = "www.yzhzhaohao.cn"
    private const val BOND_SEGMENT = "bond"
    private val CODE_REGEX = Regex("^\\d{6}$")

    sealed interface Event {
        data class Code(val code: String) : Event
        data class Invalid(val message: String) : Event
    }

    private val _events = MutableStateFlow<Event?>(null)

    /** 单次事件(StateFlow 只保留最新一条,AppRoot 取走后调用 [clearEvent])。 */
    val events: StateFlow<Event?> = _events

    /** 解析 Intent;非邀请深链返回 null,邀请深链返回可读事件。 */
    fun handle(context: Context, intent: Intent?): Event? {
        val uri = intent?.data ?: return null
        if (!isInvite(uri)) return null
        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        val event = if (CODE_REGEX.matches(code)) {
            putPending(context, code)
            Event.Code(code)
        } else {
            Event.Invalid(
                if (code.isBlank()) "邀请链接缺少邀请码，请手动输入 6 位邀请码"
                else "邀请码格式不正确，请手动输入 6 位邀请码",
            )
        }
        // 消费标记:同一个 Intent 在 Activity 重建/重复投递时不再触发
        intent.data = null
        _events.value = event
        return event
    }

    fun clearEvent() {
        _events.value = null
    }

    fun pendingCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING, null)?.takeIf { CODE_REGEX.matches(it) }

    fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    private fun putPending(context: Context, code: String) {
        prefs(context).edit().putString(KEY_PENDING, code).apply()
    }

    private fun isInvite(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
        "bugu" -> uri.host == BOND_SEGMENT
        // http 仅用于兼容短链/重定向中转;正式入口仍由 Manifest 的 https App Links 过滤
        "https", "http" -> uri.host == HTTPS_HOST && isBondPath(uri.path)
        else -> false
    }

    /** 兼容 /bond、/bond/ 以及指向落地页的重定向路径(如 /bond/index.html)。 */
    private fun isBondPath(path: String?): Boolean =
        path.orEmpty().split('/').firstOrNull { it.isNotBlank() }
            ?.equals(BOND_SEGMENT, ignoreCase = true) == true

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
