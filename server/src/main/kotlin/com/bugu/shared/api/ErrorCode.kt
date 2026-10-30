package com.bugu.shared.api

/**
 * 业务错误码。客户端按此驱动降级动作,例如 AI_UNAVAILABLE 时切手动表单。
 */
enum class ErrorCode(val defaultMessage: String) {
    OK("ok"),
    INVALID_ARGUMENT("参数不合法"),
    UNAUTHORIZED("未登录或登录态失效"),
    FORBIDDEN("无权访问该资源"),
    NOT_FOUND("资源不存在"),
    CONFLICT("状态冲突"),
    BOND_INVALID("绑定码无效或已过期"),
    BOND_LIMITED("已存在绑定关系"),
    SMS_RATE_LIMITED("短信发送过于频繁,请稍后再试"),
    REMINDER_MUTED("TA 关闭了这类提醒"),
    AI_UNAVAILABLE("AI 服务暂不可用"),
    UPSTREAM_UNAVAILABLE("上游服务不可用"),
    INTERNAL("服务内部错误"),
}
