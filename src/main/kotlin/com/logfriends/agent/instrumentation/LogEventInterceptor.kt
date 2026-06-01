package com.logfriends.agent.instrumentation

import com.logfriends.agent.annotation.LogEvent
import com.logfriends.agent.annotation.LogMasked
import com.logfriends.agent.transport.BatchTransporter
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object LogEventInterceptor {
    private val EVENT_NAME_REGEX = Regex("^[a-z][a-zA-Z0-9]*$")

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @Origin method: Method,
        @AllArguments args: Array<Any?>,
        @SuperCall callable: Callable<*>
    ): Any? {
        val result = callable.call()

        try {
            val logEvent = method.getAnnotation(LogEvent::class.java)
            if (logEvent != null) {
                val eventName = logEvent.name.ifBlank { logEvent.value }.trim()
                if (!isValidEventName(eventName)) {
                    System.err.println(
                        "[Log Friends] LOG_EVENT skipped: eventName must be camelCase and match ^[a-z][a-zA-Z0-9]*$ " +
                            "(method=${method.declaringClass.name}.${method.name})"
                    )
                    return result
                }

                val paramNames = extractParamNames(method)
                if (hasFallbackParamNames(method)) {
                    System.err.println(
                        "[Log Friends] LOG_EVENT using fallback parameter names arg0, arg1... " +
                            "Compile with parameter metadata for eventName=$eventName " +
                            "(method=${method.declaringClass.name}.${method.name})"
                    )
                }
                val maskedParams = extractMaskedParams(method)
                BatchTransporter.getInstance().enqueueLogEvent(eventName, paramNames, args, maskedParams)
            }
        } catch (e: Exception) {
            System.err.println("[Log Friends] LogEventInterceptor error: " + e.message)
        }
        return result
    }

    fun isValidEventName(eventName: String): Boolean {
        return eventName.isNotBlank() && EVENT_NAME_REGEX.matches(eventName)
    }

    private fun extractParamNames(method: Method): Array<String> {
        val params = method.parameters
        return Array(params.size) { i ->
            if (params[i].isNamePresent) params[i].name else "arg$i"
        }
    }

    fun hasFallbackParamNames(method: Method): Boolean {
        return method.parameters.any { !it.isNamePresent }
    }

    private fun extractMaskedParams(method: Method): BooleanArray {
        return BooleanArray(method.parameterCount) { index ->
            method.parameterAnnotations.getOrNull(index)
                ?.any { it.annotationClass.java == LogMasked::class.java }
                ?: false
        }
    }
}
