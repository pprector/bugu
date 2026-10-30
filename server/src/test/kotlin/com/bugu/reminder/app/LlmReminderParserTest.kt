package com.bugu.reminder.app

import com.bugu.ai.infra.LlmCopyResult
import com.bugu.ai.infra.LlmGateway
import com.bugu.ai.infra.LlmUnavailableException
import com.bugu.reminder.domain.ReminderParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Clock

/** LlmReminderParser:LLM 优先解析成功 degraded=false;超时/输出不合法/无网关回落正则 degraded=true。 */
class LlmReminderParserTest {

    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 26, 15, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
        ZoneId.of("Asia/Shanghai"),
    )
    private val now = LocalDateTime.of(2026, 10, 26, 15, 0)
    private val fallback = ReminderParser(clock)

    /** 最小 ObjectProvider 假实现:gateway 为 null 模拟 provider=none 未注册。 */
    private fun providerOf(gateway: LlmGateway?): ObjectProvider<LlmGateway> = object : ObjectProvider<LlmGateway> {
        override fun getObject(): LlmGateway = gateway!!
        override fun getObject(vararg args: Any?): LlmGateway = gateway!!
        override fun getIfAvailable(): LlmGateway? = gateway
        override fun getIfUnique(): LlmGateway? = gateway
        override fun iterator(): MutableIterator<LlmGateway> {
            val list = ArrayList<LlmGateway>()
            gateway?.let { list.add(it) }
            return list.iterator()
        }
    }

    /** 行为可变的假网关:按 behavior 返回或抛异常。 */
    private class FakeGateway(var behavior: () -> LlmCopyResult) : LlmGateway {
        override fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult =
            behavior()
    }

    @Test
    fun `llm正常解析逐字段返回且degraded_false`() {
        val content = """```json
            {"title":"妈妈吃降压药","fireAt":"2026-10-27T08:00:00","kind":"ONCE","note":"记得多喝点水"}
        ```"""
        val parser = LlmReminderParser(providerOf(FakeGateway { LlmCopyResult(content) }), fallback)
        val parsed = parser.parse("明天早上七点半提醒妈妈吃降压药,记得多喝点水", now)
        assertFalse(parsed.degraded)
        assertFalse(parsed.draft.needsManual)
        assertEquals("妈妈吃降压药", parsed.draft.title)
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0), parsed.draft.fireAt)
        assertEquals("记得多喝点水", parsed.draft.note)
        assertNull(parsed.draft.repeatRule)
    }

    @Test
    fun `llm判REPEAT缺repeatRule补DAILY`() {
        val content = """{"title":"吃药","fireAt":"2026-10-27T20:30:00","kind":"REPEAT"}"""
        val parser = LlmReminderParser(providerOf(FakeGateway { LlmCopyResult(content) }), fallback)
        val parsed = parser.parse("每天晚上八点半吃药", now)
        assertFalse(parsed.degraded)
        assertEquals("DAILY", parsed.draft.repeatRule)
    }

    @Test
    fun `llm请求失败降级正则且degraded_true`() {
        val parser = LlmReminderParser(
            providerOf(FakeGateway { throw LlmUnavailableException("timeout") }),
            fallback,
        )
        val parsed = parser.parse("明早九点提醒我妈吃降压药", now)
        assertTrue(parsed.degraded)
        assertFalse(parsed.draft.needsManual)
        assertEquals("我妈吃降压药", parsed.draft.title)
        assertEquals(LocalDateTime.of(2026, 10, 27, 9, 0), parsed.draft.fireAt)
    }

    @Test
    fun `llm输出不合法降级正则`() {
        val parser = LlmReminderParser(providerOf(FakeGateway { LlmCopyResult("好的,我记下来啦~") }), fallback)
        val parsed = parser.parse("明早九点提醒我妈吃降压药", now)
        assertTrue(parsed.degraded)
        assertEquals("我妈吃降压药", parsed.draft.title)
    }

    @Test
    fun `无网关降级正则`() {
        val parser = LlmReminderParser(providerOf(null), fallback)
        val parsed = parser.parse("明早九点提醒我妈吃降压药", now)
        assertTrue(parsed.degraded)
        assertFalse(parsed.draft.needsManual)
        assertEquals("我妈吃降压药", parsed.draft.title)
    }
}
