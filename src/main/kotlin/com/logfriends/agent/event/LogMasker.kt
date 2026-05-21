package com.logfriends.agent.event

import com.logfriends.agent.annotation.LogMasked
import java.lang.reflect.Modifier

object LogMasker {
    const val MASKED_VALUE = "__MASKED__"

    private val sensitiveNames = setOf(
        "password",
        "passwd",
        "secret",
        "token",
        "accesstoken",
        "refreshtoken",
        "authorization",
        "cookie",
        "email",
        "phone"
    )

    fun maskValue(fieldName: String, value: Any?, explicitlyMasked: Boolean = false): String {
        if (explicitlyMasked || isSensitiveName(fieldName)) {
            return MASKED_VALUE
        }

        if (value == null) {
            return ""
        }

        return when (value) {
            is CharSequence,
            is Number,
            is Boolean,
            is Enum<*> -> value.toString()
            else -> maskObjectTopLevelFields(value)
        }
    }

    fun toJsonValue(fieldName: String, value: Any?, explicitlyMasked: Boolean = false): String {
        if (explicitlyMasked || isSensitiveName(fieldName)) {
            return quote(MASKED_VALUE)
        }

        if (value == null) {
            return "null"
        }

        return when (value) {
            is Number,
            is Boolean -> value.toString()
            is CharSequence,
            is Enum<*> -> quote(value.toString())
            else -> objectTopLevelFieldsToJson(value)
        }
    }

    fun isSensitiveName(name: String): Boolean {
        return sensitiveNames.contains(normalizeName(name))
    }

    private fun maskObjectTopLevelFields(value: Any): String {
        return objectTopLevelFieldsToJson(value)
    }

    private fun objectTopLevelFieldsToJson(value: Any): String {
        val fields = value.javaClass.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }

        if (fields.isEmpty()) {
            return value.toString()
        }

        return fields.joinToString(prefix = "{", postfix = "}") { field ->
            field.isAccessible = true
            val fieldValue = field.get(value)
            val masked = field.isAnnotationPresent(LogMasked::class.java) || isSensitiveName(field.name)
            "\"${escape(field.name)}\":${toNestedFieldJsonValue(field.name, fieldValue, masked)}"
        }
    }

    private fun toNestedFieldJsonValue(fieldName: String, value: Any?, explicitlyMasked: Boolean): String {
        if (explicitlyMasked || isSensitiveName(fieldName)) {
            return quote(MASKED_VALUE)
        }

        if (value == null) {
            return "null"
        }

        return when (value) {
            is Number,
            is Boolean -> value.toString()
            is CharSequence,
            is Enum<*> -> quote(value.toString())
            else -> quote(value.toString())
        }
    }

    private fun normalizeName(name: String): String {
        return name.replace("_", "").replace("-", "").lowercase()
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun quote(value: String): String {
        return "\"${escape(value)}\""
    }
}
