package com.logfriends.agent.instrumentation

import com.logfriends.agent.transport.BatchTransporter
import net.bytebuddy.implementation.bind.annotation.Argument
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object LogbackInterceptor {

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @Origin method: Method,
        @Argument(0) event: Any,
        @SuperCall callable: Callable<*>
    ) {
        try {
            val level = safeInvoke(event, "getLevel")
            val loggerName = safeInvoke(event, "getLoggerName")
            val threadName = safeInvoke(event, "getThreadName")
            val message = safeInvoke(event, "getFormattedMessage")

            var exceptionStr = ""
            try {
                val throwableProxy = event.javaClass.getMethod("getThrowableProxy").invoke(event)
                if (throwableProxy != null) {
                    exceptionStr = safeInvoke(throwableProxy, "getMessage")
                }
            } catch (ignored: Exception) {}

            BatchTransporter.getInstance().enqueueLog(
                level, loggerName, threadName, message,
                exceptionStr
            )
        } catch (e: Exception) {
            System.err.println("[Log Friends] Interceptor error: " + e.message)
        }

        callable.call()
    }

    private fun safeInvoke(obj: Any, methodName: String): String {
        return try {
            val result = obj.javaClass.getMethod(methodName).invoke(obj)
            result?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
