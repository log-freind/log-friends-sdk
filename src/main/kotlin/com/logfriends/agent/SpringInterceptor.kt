package com.logfriends.agent

import net.bytebuddy.implementation.bind.annotation.Argument
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object SpringInterceptor {

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @Origin method: Method,
        @Argument(0) request: Any,
        @Argument(1) response: Any,
        @SuperCall callable: Callable<*>
    ): Any? {
        val start = System.currentTimeMillis()
        var httpMethod = ""
        var uri = ""
        var statusCode = 0

        try {
            httpMethod = safeInvoke(request, "getMethod")
            uri = safeInvoke(request, "getRequestURI")
        } catch (ignored: Exception) {}

        try {
            val result = callable.call()

            val duration = System.currentTimeMillis() - start

            try {
                val status = response.javaClass.getMethod("getStatus").invoke(response)
                statusCode = status as Int
            } catch (ignored: Exception) {
                statusCode = 200
            }

            BatchTransporter.getInstance().enqueueHttp(httpMethod, uri, statusCode, duration, "")

            return result

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            statusCode = 500

            BatchTransporter.getInstance().enqueueHttp(httpMethod, uri, statusCode, duration, "")

            throw e
        }
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
