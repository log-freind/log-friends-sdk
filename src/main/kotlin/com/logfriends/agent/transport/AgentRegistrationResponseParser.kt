package com.logfriends.agent.transport

internal object AgentRegistrationResponseParser {
    private val objectPattern = Regex("\\{([^{}]*)}")

    fun parse(
        body: String,
        fallbackWorkerId: String,
        fallbackAppName: String? = null
    ): AgentRegistrationHandshake {
        return AgentRegistrationHandshake(
            agentId = longValue(body, "agentId") ?: longValue(body, "id"),
            workerId = stringValue(body, "workerId") ?: fallbackWorkerId,
            appName = stringValue(body, "appName") ?: fallbackAppName,
            knownLogSpecs = knownLogSpecs(body)
        )
    }

    private fun knownLogSpecs(body: String): List<KnownLogSpec> {
        val array = arrayValue(body, "knownLogSpecs") ?: return emptyList()
        return objectPattern.findAll(array)
            .mapNotNull { match ->
                val item = match.value
                val eventName = stringValue(item, "eventName") ?: return@mapNotNull null
                KnownLogSpec(
                    eventName = eventName,
                    updatedAt = stringValue(item, "updatedAt")
                )
            }
            .distinctBy { it.eventName }
            .sortedBy { it.eventName }
            .toList()
    }

    private fun longValue(body: String, key: String): Long? {
        return Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    private fun stringValue(body: String, key: String): String? {
        return Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.let { unescape(it) }
    }

    private fun arrayValue(body: String, key: String): String? {
        val start = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\\[")
            .find(body)
            ?: return null
        var depth = 1
        var index = start.range.last + 1
        while (index < body.length) {
            when (body[index]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return body.substring(start.range.last + 1, index)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
