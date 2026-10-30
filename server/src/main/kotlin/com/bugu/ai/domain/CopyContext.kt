package com.bugu.ai.domain

/** 响应习惯档(脱敏:只分档,不传原始秒数)。 */
enum class ResponseHabit {
    FAST, NORMAL, SLOW, UNKNOWN;

    companion object {
        /** 平均响应延迟 → 习惯档:≤2min 秒回,≥10min 慢响应。 */
        fun fromAverageDelay(delaySeconds: Long?): ResponseHabit = when {
            delaySeconds == null -> UNKNOWN
            delaySeconds <= 120 -> FAST
            delaySeconds >= 600 -> SLOW
            else -> NORMAL
        }
    }
}

/** 时段(脱敏:只到时段不到具体时刻)。 */
enum class DayPart {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun ofHour(hour: Int): DayPart = when (hour) {
            in 5..10 -> MORNING
            in 11..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}

/** 文案生成结果:degraded 标识是否落入模板池(叫醒语缓存等需如实记录)。 */
data class CopyOutcome(
    val content: String,
    val degraded: Boolean,
)

/**
 * 文案生成的脱敏要素(PRD FR-05 五要素、约束 C7)。
 * title 为发起人主动创建的事项文本;不含原始备注、语音、手机号,用户 id 不出现在 prompt。
 */
data class CopyContext(
    val title: String,
    val scene: String,
    val tone: String,
    /** 第几次提醒:T0 首提=1,T1 再推=2。 */
    val attempt: Int,
    val responseHabit: ResponseHabit = ResponseHabit.UNKNOWN,
    val dayPart: DayPart,
)

/** 组装 LLM 提示词:只含 CopyContext 脱敏要素,约束输出 ≤40 字、语气温和不指责。 */
object CopyPromptBuilder {

    fun build(ctx: CopyContext): String {
        val toneLabel = when (ctx.tone) {
            "GENTLE" -> "温柔关怀"
            "NAG" -> "唠叨叮嘱"
            "STRICT" -> "严格直接"
            else -> "温和"
        }
        val sceneLabel = when (ctx.scene) {
            "CREATE" -> "首次提醒"
            "REPUSH" -> "到点再推"
            "WAKEUP" -> "闹钟叫醒"
            else -> "提醒"
        }
        val habitLabel = when (ctx.responseHabit) {
            ResponseHabit.FAST -> "通常很快响应"
            ResponseHabit.NORMAL -> "响应速度一般"
            ResponseHabit.SLOW -> "响应较慢"
            ResponseHabit.UNKNOWN -> "响应习惯未知"
        }
        val dayPartLabel = when (ctx.dayPart) {
            DayPart.MORNING -> "上午"
            DayPart.AFTERNOON -> "下午"
            DayPart.EVENING -> "晚上"
            DayPart.NIGHT -> "深夜"
        }
        return buildString {
            append("请为「").append(toneLabel).append("」语气档位生成一条共享提醒文案,要素如下:\n")
            append("- 事项:").append(ctx.title).append('\n')
            append("- 场景:").append(sceneLabel).append(",这是第 ").append(ctx.attempt).append(" 次提醒\n")
            append("- 接收人").append(habitLabel).append('\n')
            append("- 当前时段:").append(dayPartLabel).append('\n')
            append("要求:不超过 40 字;语气温和,不出现指责性表述;直接输出文案本身,不要引号与解释。")
        }
    }
}
