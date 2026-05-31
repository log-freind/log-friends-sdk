package com.logfriends.agent.discovery

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoveredLogEventRegistryTest {

    @AfterEach
    fun tearDown() {
        DiscoveredLogEventRegistry.clear()
    }

    @Test
    fun `register keeps one candidate per source key`() {
        DiscoveredLogEventRegistry.register(
            DiscoveredLogEventCandidate(
                eventName = "orderCreated",
                sourceClass = "com.example.OrderService",
                sourceMethod = "createOrder",
                parameterNames = listOf("oldRequest")
            )
        )
        DiscoveredLogEventRegistry.register(
            DiscoveredLogEventCandidate(
                eventName = "orderCreated",
                sourceClass = "com.example.OrderService",
                sourceMethod = "createOrder",
                parameterNames = listOf("request")
            )
        )

        val candidates = DiscoveredLogEventRegistry.getAll()

        assertEquals(1, candidates.size)
        assertEquals(listOf("request"), candidates.single().parameterNames)
    }
}
