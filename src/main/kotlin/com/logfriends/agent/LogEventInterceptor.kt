package com.logfriends.agent

import com.logfriends.agent.annotation.LogEvent
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object LogEventInterceptor {

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @Origin method: Method,
        @AllArguments args: Array<Any?>,
        @SuperCall callable: Callable<*>
    ): Any? {
        try {
            val logEvent = method.getAnnotation(LogEvent::class.java)
            if (logEvent != null) {
                val eventName = logEvent.value
                val paramNames = extractParamNames(method)
                BatchTransporter.getInstance().enqueueLogEvent(eventName, paramNames, args)
            }
        } catch (e: Exception) {
            System.err.println("[Log Friends] LogEventInterceptor error: " + e.message)
        }
        return callable.call()
    }

    private fun extractParamNames(method: Method): Array<String> {
        val params = method.parameters
        return Array(params.size) { i ->
            if (params[i].isNamePresent) params[i].name else "arg$i"
        }
    }
}
