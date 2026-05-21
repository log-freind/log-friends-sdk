package com.logfriends.agent.transport

import com.logfriends.agent.proto.AgentEvent

object EventJsonWriter {

    fun writeBatch(workerId: String, events: List<AgentEvent>): String {
        val sb = StringBuilder()
        sb.append("{\"workerId\":\"").append(esc(workerId)).append("\",\"events\":[")
        events.forEachIndexed { i, event ->
            if (i > 0) sb.append(",")
            sb.append(writeEvent(event))
        }
        sb.append("]}")
        return sb.toString()
    }

    fun writeEvent(event: AgentEvent): String {
        val sb = StringBuilder()
        when {
            event.hasLog() -> {
                val e = event.log
                sb.append("{\"type\":\"LOG\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"level\":\"").append(esc(e.level)).append("\"")
                sb.append(",\"loggerName\":\"").append(esc(e.loggerName)).append("\"")
                sb.append(",\"threadName\":\"").append(esc(e.threadName)).append("\"")
                sb.append(",\"message\":\"").append(esc(e.message)).append("\"")
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
            }
            event.hasHttp() -> {
                val e = event.http
                sb.append("{\"type\":\"HTTP\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"method\":\"").append(esc(e.method)).append("\"")
                sb.append(",\"uri\":\"").append(esc(e.uri)).append("\"")
                sb.append(",\"statusCode\":").append(e.statusCode)
                sb.append(",\"durationMs\":").append(e.durationMs)
            }
            event.hasJdbc() -> {
                val e = event.jdbc
                sb.append("{\"type\":\"JDBC\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"sql\":\"").append(esc(e.sql)).append("\"")
                sb.append(",\"durationMs\":").append(e.durationMs)
                sb.append(",\"rowCount\":").append(e.rowCount)
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
            }
            event.hasMethodTrace() -> {
                val e = event.methodTrace
                sb.append("{\"type\":\"METHOD_TRACE\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"className\":\"").append(esc(e.className)).append("\"")
                sb.append(",\"methodName\":\"").append(esc(e.methodName)).append("\"")
                sb.append(",\"durationMs\":").append(e.durationMs)
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
            }
            event.hasLogEvent() -> {
                val e = event.logEvent
                sb.append("{\"type\":\"LOG_EVENT\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"eventName\":\"").append(esc(e.eventName)).append("\"")
                sb.append(",\"payload\":").append(jsonLiteralMapToJson(e.fieldsMap))
            }
            else -> sb.append("{\"type\":\"UNKNOWN\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun jsonLiteralMapToJson(map: Map<String, String>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { i, (key, value) ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(esc(key)).append("\":").append(value)
        }
        sb.append("}")
        return sb.toString()
    }

    private fun esc(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
