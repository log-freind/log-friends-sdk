package com.logfriends.agent.discovery

data class DiscoveredLogEventCandidate(
    val eventName: String,
    val sourceClass: String,
    val sourceMethod: String,
    val parameterNames: List<String>,
    val specHint: LogSpecHint? = null
)

data class LogSpecHint(
    val description: String? = null,
    val apiMethod: String? = null,
    val apiPath: String? = null,
    val apiDescription: String? = null,
    val fields: List<LogFieldHint> = emptyList()
)

data class LogFieldHint(
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val required: Boolean = true,
    val nestedFields: List<LogFieldHint> = emptyList()
)
