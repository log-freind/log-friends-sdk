package com.logfriends.agent

import net.bytebuddy.implementation.bind.annotation.Argument
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object SpringInterceptor {

    private val CAPTURE_HEADERS: Set<String> = run {
        val prop = System.getProperty(
            "logfriends.http.capture.headers",
            "traceparent,x-trace-id,x-request-id,x-b3-traceid,x-b3-spanid,user-agent,content-type"
        )
        prop.split(",").map { it.trim().lowercase() }.toSet()
    }

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
        var traceId = ""
        val capturedHeaders = mutableMapOf<String, String>()

        try {
            httpMethod = safeInvoke(request, "getMethod")
            uri = safeInvoke(request, "getRequestURI")
            extractHeaders(request, capturedHeaders)
            traceId = resolveTraceId(capturedHeaders)
        } catch (ignored: Exception) {}

        try {
            val result = callable.call()

            val duration = System.currentTimeMillis() - start

            try {
                statusCode = response.javaClass.getMethod("getStatus").invoke(response) as Int
            } catch (ignored: Exception) {
                statusCode = 200
            }

            BatchTransporter.getInstance().enqueueHttp(
                httpMethod, uri, statusCode, duration, traceId,
                capturedHeaders.ifEmpty { null }, null
            )

            return result

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            statusCode = 500

            BatchTransporter.getInstance().enqueueHttp(
                httpMethod, uri, statusCode, duration, traceId,
                capturedHeaders.ifEmpty { null },
                e.stackTraceToString()
            )

            throw e
        }
    }

    private fun extractHeaders(request: Any, out: MutableMap<String, String>) {
        try {
            val getHeaderNames = request.javaClass.getMethod("getHeaderNames")
            @Suppress("UNCHECKED_CAST")
            val names = getHeaderNames.invoke(request) as? java.util.Enumeration<String> ?: return
            val getHeader = request.javaClass.getMethod("getHeader", String::class.java)
            while (names.hasMoreElements()) {
                val name = names.nextElement()?.lowercase() ?: continue
                if (name in CAPTURE_HEADERS) {
                    val value = getHeader.invoke(request, name)?.toString() ?: continue
                    out[name] = value
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun resolveTraceId(headers: Map<String, String>): String {
        // W3C traceparent: 00-<traceId>-<spanId>-<flags>
        headers["traceparent"]?.let { tp ->
            val parts = tp.split("-")
            if (parts.size >= 2) return parts[1]
        }
        headers["x-trace-id"]?.let { return it }
        headers["x-b3-traceid"]?.let { return it }
        return ""
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
