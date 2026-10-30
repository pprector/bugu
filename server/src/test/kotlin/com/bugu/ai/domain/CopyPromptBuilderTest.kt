package com.bugu.ai.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CopyPromptBuilder 纯函数测试:要素齐全、脱敏(只有 ctx 内字段)。 */
class CopyPromptBuilderTest {

    private fun ctx(
        title: String = "吃降压药",
        tone: String = "GENTLE",
        scene: String = "REPUSH",
        attempt: Int = 2,
        habit: ResponseHabit = ResponseHabit.FAST,
        dayPart: DayPart = DayPart.MORNING,
    ) = CopyContext(title, scene, tone, attempt, habit, dayPart)

    @Test
    fun `prompt包含全部脱敏要素`() {
        val prompt = CopyPromptBuilder.build(ctx())
        assertTrue(prompt.contains("吃降压药"))
        assertTrue(prompt.contains("温柔关怀"))
        assertTrue(prompt.contains("到点再推"))
        assertTrue(prompt.contains("第 2 次提醒"))
        assertTrue(prompt.contains("通常很快响应"))
        assertTrue(prompt.contains("上午"))
        assertTrue(prompt.contains("不超过 40 字"))
    }

    @Test
    fun `三档语气映射`() {
        assertTrue(CopyPromptBuilder.build(ctx(tone = "NAG")).contains("唠叨叮嘱"))
        assertTrue(CopyPromptBuilder.build(ctx(tone = "STRICT")).contains("严格直接"))
        assertTrue(CopyPromptBuilder.build(ctx(tone = "OTHER")).contains("温和"))
    }

    @Test
    fun `prompt不包含备注与手机号等字段`() {
        // CopyContext 结构上没有 note/phone 字段,此处防御性断言常见泄露字样不出现
        val prompt = CopyPromptBuilder.build(ctx())
        assertFalse(prompt.contains("备注"))
        assertFalse(prompt.contains("手机"))
    }

    @Test
    fun `响应习惯与时段分档`() {
        assertTrue(CopyPromptBuilder.build(ctx(habit = ResponseHabit.SLOW)).contains("响应较慢"))
        assertTrue(CopyPromptBuilder.build(ctx(habit = ResponseHabit.UNKNOWN)).contains("未知"))
        assertTrue(CopyPromptBuilder.build(ctx(dayPart = DayPart.NIGHT)).contains("深夜"))
    }

    @Test
    fun `响应习惯档阈值`() {
        kotlin.test.assertEquals(ResponseHabit.UNKNOWN, ResponseHabit.fromAverageDelay(null))
        kotlin.test.assertEquals(ResponseHabit.FAST, ResponseHabit.fromAverageDelay(60))
        kotlin.test.assertEquals(ResponseHabit.NORMAL, ResponseHabit.fromAverageDelay(300))
        kotlin.test.assertEquals(ResponseHabit.SLOW, ResponseHabit.fromAverageDelay(900))
    }

    @Test
    fun `时段划分`() {
        kotlin.test.assertEquals(DayPart.MORNING, DayPart.ofHour(8))
        kotlin.test.assertEquals(DayPart.AFTERNOON, DayPart.ofHour(14))
        kotlin.test.assertEquals(DayPart.EVENING, DayPart.ofHour(20))
        kotlin.test.assertEquals(DayPart.NIGHT, DayPart.ofHour(2))
    }
}
