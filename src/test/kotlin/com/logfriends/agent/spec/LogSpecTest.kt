package com.logfriends.agent.spec

import com.logfriends.agent.annotation.LogCategory
import com.logfriends.agent.annotation.LogLevel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.ConcurrentHashMap

class LogSpecTest {

    @BeforeEach
    fun clearRegistry() {
        val field = LogSpecRegistry::class.java.getDeclaredField("REGISTRY")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LogSpecRegistry) as MutableMap<*, *>).clear()
    }

    @Test
    fun `define returns LogSpec instance`() {
        val spec = LogSpec.define("test.event")
        assertNotNull(spec)
    }

    @Test
    fun `build registers spec in registry`() {
        LogSpec.define("order.created")
            .description("주문 생성")
            .build()

        assertEquals(1, LogSpecRegistry.size())
        val spec = LogSpecRegistry.getAll().first()
        assertEquals("order.created", spec.name)
        assertEquals("주문 생성", spec.description)
    }

    @Test
    fun `default level is INFO when none set`() {
        val def = LogSpec.define("no.level").build()
        assertEquals(listOf("INFO"), def.levels)
    }

    @Test
    fun `multiple levels are preserved`() {
        val def = LogSpec.define("multi.level")
            .level(LogLevel.ERROR, LogLevel.WARN)
            .build()
        assertEquals(listOf("ERROR", "WARN"), def.levels)
    }

    @Test
    fun `category defaults to BUSINESS`() {
        val def = LogSpec.define("biz.event").build()
        assertEquals("BUSINESS", def.category)
    }

    @Test
    fun `category can be set to SECURITY`() {
        val def = LogSpec.define("sec.event")
            .category(LogCategory.SECURITY)
            .build()
        assertEquals("SECURITY", def.category)
    }

    @Test
    fun `fields are included in built spec`() {
        val def = LogSpec.define("order.created")
            .field("orderId").required().example("ORD-123").and()
            .field("amount").type(Long::class.java).and()
            .build()

        assertEquals(2, def.fields.size)
        val orderIdField = def.fields.find { it.name == "orderId" }!!
        assertTrue(orderIdField.isRequired)
        assertEquals("ORD-123", orderIdField.example)

        val amountField = def.fields.find { it.name == "amount" }!!
        assertEquals("long", amountField.type)
    }

    @Test
    fun `build returns LogSpecDef with correct values`() {
        val def = LogSpec.define("payment.processed")
            .description("결제 완료")
            .level(LogLevel.INFO)
            .category(LogCategory.BUSINESS)
            .build()

        assertEquals("payment.processed", def.name)
        assertEquals("결제 완료", def.description)
        assertEquals(listOf("INFO"), def.levels)
        assertEquals("BUSINESS", def.category)
    }
}
