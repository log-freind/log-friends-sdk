package com.logfriends.agent

import com.logfriends.agent.proto.AgentEvent
import com.logfriends.agent.proto.HttpEvent
import com.logfriends.agent.proto.JdbcEvent
import com.logfriends.agent.proto.LogEvent
import com.logfriends.agent.proto.LogEventCapture
import com.logfriends.agent.proto.MethodTraceEvent
import java.time.Instant

object AgentEventFactory {

    fun log(
        level: String,
        loggerName: String,
        threadName: String,
        message: String,
        exception: String?
    ): AgentEvent {
        val proto = LogEvent.newBuilder()
            .setTimestamp(now())
            .setLevel(level)
            .setLoggerName(loggerName)
            .setThreadName(threadName)
            .setMessage(message)
            .apply {
                if (!exception.isNullOrEmpty()) setException(exception)
            }
            .build()
        return AgentEvent.newBuilder().setLog(proto).build()
    }

    fun http(
        method: String,
        uri: String,
        statusCode: Int,
        durationMs: Long
    ): AgentEvent {
        val proto = HttpEvent.newBuilder()
            .setTimestamp(now())
            .setMethod(method)
            .setUri(uri)
            .setStatusCode(statusCode)
            .setDurationMs(durationMs)
            .build()
        return AgentEvent.newBuilder().setHttp(proto).build()
    }

    fun jdbc(
        sql: String,
        durationMs: Long,
        rowCount: Int,
        exception: String?
    ): AgentEvent {
        val proto = JdbcEvent.newBuilder()
            .setTimestamp(now())
            .setSql(sql)
            .setDurationMs(durationMs)
            .setRowCount(rowCount)
            .apply {
                if (!exception.isNullOrEmpty()) setException(exception)
            }
            .build()
        return AgentEvent.newBuilder().setJdbc(proto).build()
    }

    fun methodTrace(
        className: String,
        methodName: String,
        durationMs: Long,
        exception: String?
    ): AgentEvent {
        val proto = MethodTraceEvent.newBuilder()
            .setTimestamp(now())
            .setClassName(className)
            .setMethodName(methodName)
            .setDurationMs(durationMs)
            .apply {
                if (!exception.isNullOrEmpty()) setException(exception)
            }
            .build()
        return AgentEvent.newBuilder().setMethodTrace(proto).build()
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

        val proto = LogEventCapture.newBuilder()
            .setTimestamp(now())
            .setEventName(eventName)
            .putAllFields(fields)
            .build()
        return AgentEvent.newBuilder().setLogEvent(proto).build()
    }

    private fun now(): String = Instant.now().toString()
}
