package com.bugu.client.api

/** JWT 存取:宿主平台实现(安卓 SharedPreferences / 桌面内存),Ktor 实现经 tokenProvider 消费。 */
interface TokenStore {

    suspend fun get(): String?

    suspend fun set(token: String?)
}
