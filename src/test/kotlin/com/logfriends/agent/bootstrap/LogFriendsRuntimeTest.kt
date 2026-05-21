package com.logfriends.agent.bootstrap

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
}
