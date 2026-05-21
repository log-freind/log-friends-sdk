package com.logfriends.agent.instrumentation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class LogEventInterceptorTest {

    class SampleService {
        @Suppress("unused")
        fun orderCreated(orderId: Long, userId: String) = Unit
    }

    @Test
    fun `valid eventName uses camelCase`() {
        assertTrue(LogEventInterceptor.isValidEventName("orderCreated"))
        assertTrue(LogEventInterceptor.isValidEventName("paymentApproved2"))
        assertTrue(LogEventInterceptor.isValidEventName("userRegistered"))
    }

    @Test
    fun `invalid eventName is rejected`() {
        assertFalse(LogEventInterceptor.isValidEventName(""))
        assertFalse(LogEventInterceptor.isValidEventName("   "))
        assertFalse(LogEventInterceptor.isValidEventName("OrderCreated"))
        assertFalse(LogEventInterceptor.isValidEventName("order_created"))
        assertFalse(LogEventInterceptor.isValidEventName("order.created"))
        assertFalse(LogEventInterceptor.isValidEventName("order-created"))
        assertFalse(LogEventInterceptor.isValidEventName("order created"))
        assertFalse(LogEventInterceptor.isValidEventName("1orderCreated"))
    }

    @Test
    fun `fallback parameter names can be detected`() {
        val method: Method = SampleService::class.java.getDeclaredMethod(
            "orderCreated",
            Long::class.javaPrimitiveType,
            String::class.java
        )

        assertTrue(LogEventInterceptor.hasFallbackParamNames(method))
    }
}
