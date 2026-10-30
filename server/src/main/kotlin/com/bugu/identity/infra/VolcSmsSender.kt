package com.bugu.identity.infra

import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import com.volcengine.model.request.SmsSendRequest
import com.volcengine.service.sms.SmsService
import com.volcengine.service.sms.impl.SmsServiceImpl
import org.apache.http.HttpHost
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus

/**
 * 火山引擎短信配置。凭证不落代码与仓库:本地放 server/application-local.yml(gitignore),生产经环境变量注入。
 * smsAccount 为火山控制台的消息组 ID;proxyHost/proxyPort:本地开发经代理出网用,留空则直连(生产)。
 */
@ConfigurationProperties(prefix = "bugu.sms.volc")
data class VolcSmsProperties(
    val ak: String = "",
    val sk: String = "",
    val sign: String = "",
    val templateId: String = "",
    val smsAccount: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
)

/**
 * 火山引擎短信真实通道(SmsSender 实现),基于官方 SDK(volc-sdk-java SmsService),签名/重试由 SDK 处理。
 * 发送失败如实抛 UPSTREAM_UNAVAILABLE(验证码未送达不能装作已发),由 GlobalExceptionHandler 统一响应。
 */
class VolcSmsSender(
    private val props: VolcSmsProperties,
) : SmsSender {

    private val log = LoggerFactory.getLogger(javaClass)

    private val service: SmsService = if (props.proxyHost.isNotBlank() && props.proxyPort > 0) {
        SmsServiceImpl.getInstance(HttpHost(props.proxyHost, props.proxyPort))
    } else {
        SmsServiceImpl.getInstance()
    }.apply {
        setAccessKey(props.ak)
        setSecretKey(props.sk)
    }

    override fun send(phone: String, code: String) {
        val request = SmsSendRequest().apply {
            setSmsAccount(props.smsAccount)
            setSign(props.sign)
            setTemplateId(props.templateId)
            // 变量名与火山控制台短信模板一致(当前模板变量为 code)
            setTemplateParamByMap(mapOf("code" to code))
            setPhoneNumbers(phone)
        }

        val response = try {
            service.send(request)
        } catch (e: Exception) {
            log.error("火山短信请求失败 phone=***{}", phone.takeLast(4), e)
            throw BizException(ErrorCode.UPSTREAM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "短信发送失败,请稍后重试")
        }

        val error = response.responseMetadata?.error
        if (error != null) {
            log.error("火山短信发送被拒 phone=***{} code={} message={}", phone.takeLast(4), error.code, error.message)
            throw BizException(ErrorCode.UPSTREAM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "短信发送失败,请稍后重试")
        }
        log.info("火山短信已提交 phone=***{} messageIds={}", phone.takeLast(4), response.result?.messageId)
    }
}
