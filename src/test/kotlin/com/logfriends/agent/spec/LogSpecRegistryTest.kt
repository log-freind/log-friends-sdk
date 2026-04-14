package com.logfriends.agent.spec

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LogSpecRegistryTest {

    @BeforeEach
    fun clearRegistry() {
        val field = LogSpecRegistry::class.java.getDeclaredField("REGISTRY")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LogSpecRegistry) as MutableMap<*, *>).clear()
    }

    @Test
    fun `register stores spec by name`() {
        val spec = LogSpecDef("user.login", "로그인", listOf("INFO"), "SECURITY", emptyList())
        LogSpecRegistry.register(spec)

        assertEquals(1, LogSpecRegistry.size())
        assertTrue(LogSpecRegistry.getAll().any { it.name == "user.login" })
    }

    @Test
    fun `duplicate name overwrites previous spec`() {
        val v1 = LogSpecDef("event", "v1", listOf("INFO"), "BUSINESS", emptyList())
        val v2 = LogSpecDef("event", "v2", listOf("WARN"), "SYSTEM", emptyList())

        LogSpecRegistry.register(v1)
        LogSpecRegistry.register(v2)

        assertEquals(1, LogSpecRegistry.size())
        assertEquals("v2", LogSpecRegistry.getAll().first().description)
    }

    @Test
    fun `getAll returns unmodifiable collection`() {
        LogSpecRegistry.register(LogSpecDef("e1", "", listOf("INFO"), "BUSINESS", emptyList()))
        val col = LogSpecRegistry.getAll()
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (col as MutableCollection<LogSpecDef>).clear()
        }
    }

    @Test
    fun `concurrent registration is thread-safe`() {
        val pool = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)

        repeat(100) { i ->
            pool.submit {
                LogSpecRegistry.register(
                    LogSpecDef("event.$i", "", listOf("INFO"), "BUSINESS", emptyList())
                )
                latch.countDown()
            }
        }
        latch.await()
        pool.shutdown()

        assertEquals(100, LogSpecRegistry.size())
    }

    @Test
    fun `size reflects registered count`() {
        assertEquals(0, LogSpecRegistry.size())
        LogSpecRegistry.register(LogSpecDef("a", "", listOf("INFO"), "BUSINESS", emptyList()))
        assertEquals(1, LogSpecRegistry.size())
        LogSpecRegistry.register(LogSpecDef("b", "", listOf("INFO"), "BUSINESS", emptyList()))
        assertEquals(2, LogSpecRegistry.size())
    }
}
