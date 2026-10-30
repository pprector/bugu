package com.bugu.reminder.api

import kotlinx.serialization.Serializable

/** 一句话创建解析请求(PRD FR-02)。text 为发起人自然语言输入,≤200 字。 */
@Serializable
data class ParseRequest(
    val text: String,
)

/**
 * 解析草稿:表单回显用。needsManual=true 表示低置信,客户端回退手动表单逐项填写;
 * degraded=true 表示来自降级桩/兜底(真实 LLM 成功时为 false);note 为解析出的便签。
 */
@Serializable
data class ParseDraft(
    val title: String? = null,
    val fireAt: String? = null,
    val kind: String,
    val repeatRule: String? = null,
    val note: String? = null,
    val needsManual: Boolean,
    val degraded: Boolean = true,
)
