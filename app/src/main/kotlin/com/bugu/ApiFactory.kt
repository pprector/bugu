package com.bugu

import android.content.Context
import com.bugu.client.api.ApiJson
import com.bugu.client.api.GeitaApi
import com.bugu.client.api.KtorGeitaApi
import com.bugu.client.api.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/** JWT 存取(安卓):SharedPreferences。 */
class SharedPrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences("bugu", Context.MODE_PRIVATE)

    override suspend fun get(): String? = prefs.getString(KEY_JWT, null)

    override suspend fun set(token: String?) {
        prefs.edit().putString(KEY_JWT, token).apply()
    }

    private companion object {
        const val KEY_JWT = "jwt"
    }
}

object ApiFactory {

    /** 本地联验临时值(验证完还原生产 https://www.yzhzhaohao.cn);接口路径自带 api/v1 前缀。 */
    const val BASE_URL = "http://10.0.2.2:8081"

    /** 与 [create] 共用同一实例:登录成功后由 UI 写入 JWT,后续请求经 tokenProvider 取出。 */
    fun tokenStore(context: Context): TokenStore = SharedPrefsTokenStore(context)

    fun create(
        context: Context,
        store: TokenStore = SharedPrefsTokenStore(context),
        onUnauthorized: suspend () -> Unit = {},
    ): GeitaApi {
        val client = HttpClient(io.ktor.client.engine.android.Android) {
            install(ContentNegotiation) { json(ApiJson.json) }
        }
        return KtorGeitaApi(client, BASE_URL, tokenProvider = { store.get() }, onUnauthorized = onUnauthorized)
    }
}
