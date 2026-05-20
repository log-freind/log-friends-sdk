package com.logfriends.agent

import com.logfriends.agent.annotation.LogMasked
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogMaskerTest {

    data class SignupRequest(
        val userId: Long,
        @field:LogMasked
        val internalCode: String,
        @field:LogMasked
        val email: String,
        val password: String
    )

    @Test
    fun `explicitly masked parameter returns sentinel value`() {
        val masked = LogMasker.maskValue("request", "secret-value", explicitlyMasked = true)

        assertEquals("__MASKED__", masked)
    }

    @Test
    fun `sensitive field names are masked by rule`() {
        assertEquals("__MASKED__", LogMasker.maskValue("password", "abc123"))
        assertEquals("__MASKED__", LogMasker.maskValue("accessToken", "token-value"))
        assertEquals("__MASKED__", LogMasker.maskValue("refresh_token", "token-value"))
        assertEquals("__MASKED__", LogMasker.maskValue("email", "user@example.com"))
        assertEquals("__MASKED__", LogMasker.maskValue("phone", "01012345678"))
    }

    @Test
    fun `top level DTO fields can be masked`() {
        val masked = LogMasker.maskValue(
            "request",
            SignupRequest(userId = 1L, internalCode = "private-code", email = "user@example.com", password = "abc123")
        )

        assertTrue(masked.contains("\"userId\":1"))
        assertTrue(masked.contains("\"internalCode\":\"__MASKED__\""))
        assertTrue(masked.contains("\"email\":\"__MASKED__\""))
        assertTrue(masked.contains("\"password\":\"__MASKED__\""))
    }

    @Test
    fun `non sensitive primitive values are preserved as strings`() {
        assertEquals("42", LogMasker.maskValue("orderId", 42))
        assertEquals("true", LogMasker.maskValue("success", true))
        assertEquals("orderCreated", LogMasker.maskValue("eventName", "orderCreated"))
    }

    @Test
    fun `payload json keeps primitive types and object values`() {
        val json = LogMasker.toJsonValue(
            "request",
            SignupRequest(userId = 1L, internalCode = "private-code", email = "user@example.com", password = "abc123")
        )

        assertTrue(json.contains("\"userId\":1"))
        assertTrue(json.contains("\"internalCode\":\"__MASKED__\""))
        assertTrue(json.contains("\"email\":\"__MASKED__\""))
        assertTrue(json.contains("\"password\":\"__MASKED__\""))
        assertEquals("42", LogMasker.toJsonValue("orderId", 42))
        assertEquals("\"orderCreated\"", LogMasker.toJsonValue("eventName", "orderCreated"))
    }
}
