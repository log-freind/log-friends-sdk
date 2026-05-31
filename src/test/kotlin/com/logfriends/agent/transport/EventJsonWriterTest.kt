package com.logfriends.agent.transport

import com.logfriends.agent.event.AgentEventFactory
import com.logfriends.agent.event.HttpCapturedEvent
import com.logfriends.agent.event.JdbcCapturedEvent
import com.logfriends.agent.event.LogCapturedEvent
import com.logfriends.agent.event.LogEventCapturedEvent
import com.logfriends.agent.event.MethodTraceCapturedEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventJsonWriterTest {

    @Test
    fun `writes batch with worker id and events`() {
        val event = LogEventCapturedEvent(
            timestamp = "2026-05-20T00:00:00Z",
            eventName = "orderCreated",
            fields = mapOf("orderId" to "1")
        )

        val json = EventJsonWriter.writeBatch("worker-1", listOf(event))

        assertTrue(json.startsWith("{\"workerId\":\"worker-1\",\"events\":["))
        assertTrue(json.contains("\"type\":\"LOG_EVENT\""))
        assertTrue(json.contains("\"eventName\":\"orderCreated\""))
    }

    @Test
    fun `log event fields are written as payload json literals`() {
        val event = LogEventCapturedEvent(
            timestamp = "2026-05-20T00:00:00Z",
            eventName = "orderCreated",
            fields = mapOf(
                "orderId" to "1",
                "email" to "\"__MASKED__\"",
                "request" to "{\"productId\":10}"
            )
        )

        val json = EventJsonWriter.writeBatch("worker-1", listOf(event))

        assertTrue(json.contains("\"payload\":{"))
        assertTrue(json.contains("\"orderId\":1"))
        assertTrue(json.contains("\"email\":\"__MASKED__\""))
        assertTrue(json.contains("\"request\":{\"productId\":10}"))
        assertFalse(json.contains("\"fields\""))
    }

    @Test
    fun `log event keeps eventName unchanged and writes empty payload object`() {
        val event = AgentEventFactory.logEvent(
            eventName = "orderCreated",
            paramNames = emptyArray(),
            args = emptyArray(),
            maskedParams = BooleanArray(0)
        )

        val json = EventJsonWriter.writeBatch("worker-1", listOf(event))

        assertTrue(json.contains("\"type\":\"LOG_EVENT\""))
        assertTrue(json.contains("\"eventName\":\"orderCreated\""))
        assertTrue(json.contains("\"payload\":{}"))
    }

    @Test
    fun `log event fallback arg names are serialized deterministically`() {
        val event = AgentEventFactory.logEvent(
            eventName = "orderCreated",
            paramNames = arrayOf("arg0", "arg1"),
            args = arrayOf(1001L, "paid"),
            maskedParams = BooleanArray(2)
        )

        val json = EventJsonWriter.writeBatch("worker-1", listOf(event))

        assertTrue(json.contains("\"eventName\":\"orderCreated\""))
        assertTrue(json.contains("\"payload\":{"))
        assertTrue(json.contains("\"arg0\":1001"))
        assertTrue(json.contains("\"arg1\":\"paid\""))
    }

    @Test
    fun `writes all first phase event type names`() {
        val events = listOf(
            HttpCapturedEvent(
                timestamp = "2026-05-20T00:00:00Z",
                method = "GET",
                uri = "/health",
                statusCode = 200,
                durationMs = 12
            ),
            LogCapturedEvent(
                timestamp = "2026-05-20T00:00:01Z",
                level = "INFO",
                loggerName = "app",
                threadName = "main",
                message = "started"
            ),
            JdbcCapturedEvent(
                timestamp = "2026-05-20T00:00:02Z",
                sql = "select 1",
                durationMs = 3,
                rowCount = 1
            ),
            MethodTraceCapturedEvent(
                timestamp = "2026-05-20T00:00:03Z",
                className = "OrderService",
                methodName = "create",
                durationMs = 20
            ),
            LogEventCapturedEvent(
                timestamp = "2026-05-20T00:00:04Z",
                eventName = "orderCreated",
                fields = emptyMap()
            )
        )

        val json = EventJsonWriter.writeBatch("worker-1", events)

        assertTrue(json.contains("\"type\":\"HTTP\""))
        assertTrue(json.contains("\"type\":\"LOG\""))
        assertTrue(json.contains("\"type\":\"JDBC\""))
        assertTrue(json.contains("\"type\":\"METHOD_TRACE\""))
        assertTrue(json.contains("\"type\":\"LOG_EVENT\""))
    }
}
