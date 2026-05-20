package com.logfriends.agent

import com.logfriends.agent.proto.AgentEvent
import com.logfriends.agent.proto.LogEventCapture
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
}
