package com.logfriends.agent

import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import java.lang.reflect.Method
import java.util.concurrent.Callable

object MethodTraceInterceptor {

    private val THRESHOLD_MS = System.getProperty("logfriends.trace.threshold.ms", "10").toLong()

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @Origin method: Method,
        @SuperCall callable: Callable<*>
    ): Any? {
        val start = System.currentTimeMillis()
        var exception: String? = null

        return try {
            callable.call()
        } catch (e: Exception) {
            exception = e.message
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            if (duration >= THRESHOLD_MS) {
                BatchTransporter.getInstance().enqueueMethodTrace(
                    method.declaringClass.simpleName,
                    method.name,
                    duration,
                    null,
                    exception
                )
            }
        }
    }
}
