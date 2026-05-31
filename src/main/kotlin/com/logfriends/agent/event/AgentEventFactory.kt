package com.logfriends.agent.event

import java.time.Instant

object AgentEventFactory {

    fun log(
        level: String,
        loggerName: String,
        threadName: String,
        message: String,
        exception: String?
    ): AgentEvent {
        return LogCapturedEvent(
            timestamp = now(),
            level = level,
            loggerName = loggerName,
            threadName = threadName,
            message = message,
            exception = exception?.takeIf { it.isNotBlank() }
        )
    }

    fun http(
        method: String,
        uri: String,
        statusCode: Int,
        durationMs: Long
    ): AgentEvent {
        return HttpCapturedEvent(
            timestamp = now(),
            method = method,
            uri = uri,
            statusCode = statusCode,
            durationMs = durationMs
        )
    }

    fun jdbc(
        sql: String,
        durationMs: Long,
        rowCount: Int,
        exception: String?
    ): AgentEvent {
        return JdbcCapturedEvent(
            timestamp = now(),
            sql = sql,
            durationMs = durationMs,
            rowCount = rowCount,
            exception = exception?.takeIf { it.isNotBlank() }
        )
    }

    fun methodTrace(
        className: String,
        methodName: String,
        durationMs: Long,
        exception: String?
    ): AgentEvent {
        return MethodTraceCapturedEvent(
            timestamp = now(),
            className = className,
            methodName = methodName,
            durationMs = durationMs,
            exception = exception?.takeIf { it.isNotBlank() }
        )
    }

    fun logEvent(
        eventName: String,
        paramNames: Array<String>,
        args: Array<Any?>,
        maskedParams: BooleanArray
    ): AgentEvent {
        val fields = mutableMapOf<String, String>()
        for (i in paramNames.indices) {
            fields[paramNames[i]] = LogMasker.toJsonValue(
                paramNames[i],
                args.getOrNull(i),
                maskedParams.getOrNull(i) == true
            )
        }

        return LogEventCapturedEvent(
            timestamp = now(),
            eventName = eventName,
            fields = fields
        )
    }

    private fun now(): String = Instant.now().toString()
}
