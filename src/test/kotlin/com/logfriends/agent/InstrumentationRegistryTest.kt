package com.logfriends.agent

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstrumentationRegistryTest {

    @AfterEach
    fun tearDown() {
        InstrumentationRegistry.supportedInterceptorKeys.forEach {
            System.clearProperty("logfriends.interceptor.$it.enabled")
        }
    }

    @Test
    fun `supported interceptors are limited to the first phase event types`() {
        assertEquals(
            listOf("http", "logback", "jdbc", "method_trace", "log_event"),
            InstrumentationRegistry.supportedInterceptorKeys
        )
    }

    @Test
    fun `interceptor is enabled by default`() {
        assertTrue(InstrumentationRegistry.isEnabled("http"))
    }

    @Test
    fun `interceptor false disables only matching key`() {
        System.setProperty("logfriends.interceptor.http.enabled", "false")

        assertFalse(InstrumentationRegistry.isEnabled("http"))
        assertTrue(InstrumentationRegistry.isEnabled("logback"))
    }

    @Test
    fun `interceptor false is case insensitive`() {
        System.setProperty("logfriends.interceptor.log_event.enabled", "FALSE")

        assertFalse(InstrumentationRegistry.isEnabled("log_event"))
    }
}
