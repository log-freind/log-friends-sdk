package com.logfriends.agent.bootstrap

import com.logfriends.agent.transport.AgentRegistrationHandshake
import com.logfriends.agent.transport.KnownLogSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LogFriendsRuntimeTest {

    @Test
    fun `configured value uses first non blank candidate`() {
        val value = LogFriendsRuntime.resolveConfiguredValue(
            " env-worker ",
            "environment-worker",
            "property-worker"
        )

        assertEquals("env-worker", value)
    }

    @Test
    fun `configured value skips blank candidates`() {
        val value = LogFriendsRuntime.resolveConfiguredValue(
            " ",
            "",
            "property-worker"
        )

        assertEquals("property-worker", value)
    }

    @Test
    fun `configured value returns null when every candidate is blank`() {
        val value = LogFriendsRuntime.resolveConfiguredValue(
            null,
            " ",
            ""
        )

        assertNull(value)
    }

    @Test
    fun `app name falls back to spring application name`() {
        val appName = LogFriendsRuntime.resolveAppName(
            envAppName = null,
            environmentAppName = null,
            propertyAppName = null,
            springApplicationName = "order-service"
        )

        assertEquals("order-service", appName)
    }

    @Test
    fun `logfriends app name overrides spring application name`() {
        val appName = LogFriendsRuntime.resolveAppName(
            envAppName = null,
            environmentAppName = null,
            propertyAppName = "catalog-service",
            springApplicationName = "order-service"
        )

        assertEquals("catalog-service", appName)
    }

    @Test
    fun `stores registration handshake state internally`() {
        LogFriendsRuntime.markRegistered(
            AgentRegistrationHandshake(
                agentId = 1L,
                workerId = "worker-1",
                appName = "order-service",
                knownLogSpecs = listOf(KnownLogSpec("orderCreated", "2026-05-27T00:00:00Z"))
            )
        )

        assertEquals(1L, LogFriendsRuntime.handshake?.agentId)
        assertEquals("worker-1", LogFriendsRuntime.handshake?.workerId)
        assertEquals("order-service", LogFriendsRuntime.handshake?.appName)
        assertEquals(1, LogFriendsRuntime.handshake?.knownLogSpecCount)
    }
}
