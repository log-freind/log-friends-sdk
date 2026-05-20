package com.logfriends.agent

import com.logfriends.agent.proto.AgentEvent
import com.logfriends.agent.proto.HttpEvent
import com.logfriends.agent.proto.JdbcEvent
import com.logfriends.agent.proto.LogEventCapture
import com.logfriends.agent.proto.LogEvent
import com.logfriends.agent.proto.MethodTraceEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventJsonWriterTest {

    @Test
    fun `writes batch with worker id and events`() {
        val event = AgentEvent.newBuilder()
            .setLogEvent(
                LogEventCapture.newBuilder()
                    .setTimestamp("2026-05-20T00:00:00Z")
                    .setEventName("orderCreated")
                    .putFields("orderId", "1")
                    .build()
            )
            .build()

        val json = EventJsonWriter.writeBatch("worker-1", listOf(event))

        assertTrue(json.startsWith("{\"workerId\":\"worker-1\",\"events\":["))
        assertTrue(json.contains("\"type\":\"LOG_EVENT\""))
        assertTrue(json.contains("\"eventName\":\"orderCreated\""))
    }

    @Test
    fun `log event fields are written as payload json literals`() {
        val event = AgentEvent.newBuilder()
            .setLogEvent(
                LogEventCapture.newBuilder()
                    .setTimestamp("2026-05-20T00:00:00Z")
                    .setEventName("orderCreated")
                    .putFields("orderId", "1")
                    .putFields("email", "\"__MASKED__\"")
                    .putFields("request", "{\"productId\":10}")
                    .build()
            )
            .build()

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
            AgentEvent.newBuilder()
                .setHttp(
                    HttpEvent.newBuilder()
                        .setTimestamp("2026-05-20T00:00:00Z")
                        .setMethod("GET")
                        .setUri("/health")
                        .setStatusCode(200)
                        .setDurationMs(12)
                        .build()
                )
                .build(),
            AgentEvent.newBuilder()
                .setLog(
                    LogEvent.newBuilder()
                        .setTimestamp("2026-05-20T00:00:01Z")
                        .setLevel("INFO")
                        .setLoggerName("app")
                        .setThreadName("main")
                        .setMessage("started")
                        .build()
                )
                .build(),
            AgentEvent.newBuilder()
                .setJdbc(
                    JdbcEvent.newBuilder()
                        .setTimestamp("2026-05-20T00:00:02Z")
                        .setSql("select 1")
                        .setDurationMs(3)
                        .setRowCount(1)
                        .build()
                )
                .build(),
            AgentEvent.newBuilder()
                .setMethodTrace(
                    MethodTraceEvent.newBuilder()
                        .setTimestamp("2026-05-20T00:00:03Z")
                        .setClassName("OrderService")
                        .setMethodName("create")
                        .setDurationMs(20)
                        .build()
                )
                .build(),
            AgentEvent.newBuilder()
                .setLogEvent(
                    LogEventCapture.newBuilder()
                        .setTimestamp("2026-05-20T00:00:04Z")
                        .setEventName("orderCreated")
                        .build()
                )
                .build()
        )

        val json = EventJsonWriter.writeBatch("worker-1", events)

        assertTrue(json.contains("\"type\":\"HTTP\""))
        assertTrue(json.contains("\"type\":\"LOG\""))
        assertTrue(json.contains("\"type\":\"JDBC\""))
        assertTrue(json.contains("\"type\":\"METHOD_TRACE\""))
        assertTrue(json.contains("\"type\":\"LOG_EVENT\""))
    }
}
