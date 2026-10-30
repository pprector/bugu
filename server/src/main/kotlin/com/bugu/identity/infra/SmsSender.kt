package com.bugu.identity.infra

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * 短信发送通道抽象。全局约定只给 PushChannel 与 LLM 网关做接口抽象,
 * SmsSender 因 epic E1 明确要求(S5 换真实供应商)而同列例外。
 */
interface SmsSender {
    fun send(phone: String, code: String)
}

/**
 * 控制台假实现:验证码打日志,不真正发短信。
 * 由 IdentityConfig 按 bugu.sms.provider 装配(默认 console);[lastCode] 供开发联调与集成测试取码。
 */
class ConsoleSmsSender : SmsSender {

    private val log = LoggerFactory.getLogger(javaClass)

    // 类被 kotlin-spring 插件 open,open 属性禁用 private set;用 AtomicReference 做只读暴露
    private val lastCodeRef = AtomicReference<String?>(null)

    val lastCode: String?
        get() = lastCodeRef.get()

    override fun send(phone: String, code: String) {
        lastCodeRef.set(code)
        val masked = phone.take(3) + "****" + phone.takeLast(4)
        log.info("[SMS-FAKE] 发送验证码到 {}: {}", masked, code)
    }
}
