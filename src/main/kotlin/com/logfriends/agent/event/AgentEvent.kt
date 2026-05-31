package com.logfriends.agent.event

sealed interface AgentEvent {
    val timestamp: String
}

data class LogCapturedEvent(
    override val timestamp: String,
    val level: String,
    val loggerName: String,
    val threadName: String,
    val message: String,
    val exception: String? = null
) : AgentEvent

data class HttpCapturedEvent(
    override val timestamp: String,
    val method: String,
    val uri: String,
    val statusCode: Int,
    val durationMs: Long
) : AgentEvent

data class JdbcCapturedEvent(
    override val timestamp: String,
    val sql: String,
    val durationMs: Long,
    val rowCount: Int,
    val exception: String? = null
) : AgentEvent

data class MethodTraceCapturedEvent(
    override val timestamp: String,
    val className: String,
    val methodName: String,
    val durationMs: Long,
    val exception: String? = null
) : AgentEvent

data class LogEventCapturedEvent(
    override val timestamp: String,
    val eventName: String,
    val fields: Map<String, String>
) : AgentEvent
