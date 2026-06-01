package com.logfriends.agent.discovery

import com.logfriends.agent.annotation.LogEvent
import com.logfriends.agent.annotation.LogField
import java.lang.reflect.Field
import java.lang.instrument.Instrumentation
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
        val eventName = resolveEventName(annotation)
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
            },
            specHint = LogSpecHint(
                description = annotation.description.blankToNull(),
                apiMethod = annotation.apiMethod.blankToNull(),
                apiPath = annotation.apiPath.blankToNull(),
                apiDescription = annotation.apiDescription.blankToNull(),
                fields = method.parameters.mapIndexed { index, parameter ->
                    val name = if (parameter.isNamePresent) parameter.name else "arg$index"
                    val logField = parameter.getAnnotation(LogField::class.java)
                    LogFieldHint(
                        name = name,
                        description = logField?.description.blankToNull(),
                        type = logField?.type.blankToNull() ?: parameter.type.simpleName,
                        required = logField?.required ?: true,
                        nestedFields = nestedFieldHints(parameter.type)
                    )
                }
            )
        )
    }

    private fun resolveEventName(annotation: LogEvent): String {
        return annotation.name.ifBlank { annotation.value }.trim()
    }

    private fun nestedFieldHints(type: Class<*>): List<LogFieldHint> {
        if (isSimpleType(type) || type.name.startsWith("java.")) return emptyList()
        return try {
            type.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { field -> fieldHint(field) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fieldHint(field: Field): LogFieldHint {
        val annotation = field.getAnnotation(LogField::class.java)
        return LogFieldHint(
            name = field.name,
            description = annotation?.description.blankToNull(),
            type = annotation?.type.blankToNull() ?: field.type.simpleName,
            required = annotation?.required ?: true
        )
    }

    private fun isSimpleType(type: Class<*>): Boolean {
        return type.isPrimitive ||
            type == String::class.java ||
            Number::class.java.isAssignableFrom(type) ||
            type == java.lang.Boolean::class.java ||
            type.isEnum
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

    private fun String?.blankToNull(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }
}
