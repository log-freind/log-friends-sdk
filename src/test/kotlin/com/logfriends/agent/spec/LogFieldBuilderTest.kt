package com.logfriends.agent.spec

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LogFieldBuilderTest {

    @BeforeEach
    fun clearRegistry() {
        val field = LogSpecRegistry::class.java.getDeclaredField("REGISTRY")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LogSpecRegistry) as MutableMap<*, *>).clear()
    }

    private fun buildField(name: String, configure: LogFieldBuilder.() -> LogFieldBuilder): LogFieldDef {
        val parent = LogSpec.define("__test__")
        val builder = parent.field(name)
        return configure(builder).and().build().fields.first { it.name == name }
    }

    @Test
    fun `default type is string`() {
        val field = buildField("myField") { this }
        assertEquals("string", field.type)
    }

    @Test
    fun `type mapping - String class`() {
        val field = buildField("f") { type(String::class.java) }
        assertEquals("string", field.type)
    }

    @Test
    fun `type mapping - Int class`() {
        val field = buildField("f") { type(Int::class.java) }
        assertEquals("integer", field.type)
    }

    @Test
    fun `type mapping - Long class`() {
        val field = buildField("f") { type(Long::class.java) }
        assertEquals("long", field.type)
    }

    @Test
    fun `type mapping - Boolean class`() {
        val field = buildField("f") { type(Boolean::class.java) }
        assertEquals("boolean", field.type)
    }

    @Test
    fun `type mapping - Double class`() {
        val field = buildField("f") { type(Double::class.java) }
        assertEquals("number", field.type)
    }

    @Test
    fun `type mapping - custom string`() {
        val field = buildField("f") { type("uuid") }
        assertEquals("uuid", field.type)
    }

    @Test
    fun `required sets flag`() {
        val field = buildField("f") { required() }
        assertTrue(field.isRequired)
    }

    @Test
    fun `not required by default`() {
        val field = buildField("f") { this }
        assertFalse(field.isRequired)
    }

    @Test
    fun `example and description are set`() {
        val field = buildField("orderId") {
            example("ORD-123").description("주문 ID")
        }
        assertEquals("ORD-123", field.example)
        assertEquals("주문 ID", field.description)
    }

    @Test
    fun `and returns parent LogSpec for chaining`() {
        val parent = LogSpec.define("chain.test")
        val returned = parent.field("f1").and()
        assertSame(parent, returned)
    }

    @Test
    fun `multiple fields chained correctly`() {
        val def = LogSpec.define("multi.fields")
            .field("id").required().example("1").and()
            .field("name").type(String::class.java).description("이름").and()
            .field("age").type(Int::class.java).and()
            .build()

        assertEquals(3, def.fields.size)
        assertTrue(def.fields.find { it.name == "id" }!!.isRequired)
        assertEquals("integer", def.fields.find { it.name == "age" }!!.type)
    }
}
