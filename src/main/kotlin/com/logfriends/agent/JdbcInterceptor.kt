package com.logfriends.agent

import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.implementation.bind.annotation.This
import java.lang.reflect.Method
import java.util.concurrent.Callable

object JdbcInterceptor {

    @JvmStatic
    @RuntimeType
    @Throws(Exception::class)
    fun intercept(
        @This statement: Any,
        @Origin method: Method,
        @SuperCall callable: Callable<*>
    ): Any? {
        val sql = extractSql(statement)
        val start = System.currentTimeMillis()
        var exception: String? = null
        var exceptionStack: String? = null
        var rowCount = -1

        return try {
            val result = callable.call()
            rowCount = extractRowCount(result)
            result
        } catch (e: Exception) {
            exception = e.message
            exceptionStack = e.stackTraceToString()
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            BatchTransporter.getInstance().enqueueJdbc(sql, duration, rowCount, null, exception, exceptionStack)
        }
    }

    private fun extractSql(statement: Any): String {
        return try {
            val toStr = statement.toString()
            val idx = toStr.indexOf(':')
            if (idx >= 0) toStr.substring(idx + 1).trim() else toStr
        } catch (e: Exception) {
            try {
                statement.javaClass.getMethod("getSql").invoke(statement)?.toString() ?: ""
            } catch (ignored: Exception) {
                ""
            }
        }
    }

    private fun extractRowCount(result: Any?): Int {
        if (result == null) return -1
        return when (result) {
            is Int -> result
            is Long -> result.toInt()
            else -> -1
        }
    }
}
