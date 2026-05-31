package com.logfriends.agent.discovery

import com.logfriends.agent.annotation.LogEvent
import java.lang.instrument.Instrumentation
import java.lang.reflect.Method

object DiscoveredLogEventScanner {
    private val eventNamePattern = Regex("^[a-z][a-zA-Z0-9]*$")

    fun scan(instrumentation: Instrumentation): List<DiscoveredLogEventCandidate> {
        val discovered = instrumentation.allLoadedClasses
            .asSequence()
            .filter { shouldScan(it) }
            .flatMap { scanClass(it).asSequence() }
            .distinctBy { "${it.eventName}|${it.sourceClass}|${it.sourceMethod}" }
            .toList()

        DiscoveredLogEventRegistry.replaceAll(discovered)
        return DiscoveredLogEventRegistry.getAll()
    }

    private fun scanClass(type: Class<*>): List<DiscoveredLogEventCandidate> {
        return try {
            type.declaredMethods
                .mapNotNull { method -> toCandidate(type, method) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toCandidate(type: Class<*>, method: Method): DiscoveredLogEventCandidate? {
        val annotation = method.getAnnotation(LogEvent::class.java) ?: return null
        val eventName = annotation.value.trim()
        if (!eventNamePattern.matches(eventName)) {
            System.err.println(
                "[Log Friends] Discovered LOG_EVENT skipped: eventName must be camelCase " +
                    "(method=${type.name}.${method.name})"
            )
            return null
        }

        return DiscoveredLogEventCandidate(
            eventName = eventName,
            sourceClass = type.name,
            sourceMethod = method.name,
            parameterNames = method.parameters.mapIndexed { index, parameter ->
                if (parameter.isNamePresent) parameter.name else "arg$index"
            }
        )
    }

    private fun shouldScan(type: Class<*>): Boolean {
        if (type.isArray || type.isPrimitive || type.isAnnotation || type.isSynthetic) return false
        val name = type.name
        return !(
            name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("jakarta.") ||
                name.startsWith("jdk.") ||
                name.startsWith("sun.") ||
                name.startsWith("kotlin.") ||
                name.startsWith("net.bytebuddy.") ||
                name.startsWith("com.logfriends.agent.")
            )
    }
}
