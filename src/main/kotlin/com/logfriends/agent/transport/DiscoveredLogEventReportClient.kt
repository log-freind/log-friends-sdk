package com.logfriends.agent.transport

import com.logfriends.agent.discovery.DiscoveredLogEventCandidate
import com.logfriends.agent.discovery.LogFieldHint
import com.logfriends.agent.discovery.LogSpecHint
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DiscoveredLogEventReportClient(
    private val reportUrl: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {

    fun report(
        handshake: AgentRegistrationHandshake,
        appVersion: String?,
        events: List<DiscoveredLogEventCandidate>
    ): DiscoveredLogEventReportResult {
        val agentId = handshake.agentId
            ?: throw IllegalStateException("agentId is required to report discovered LOG_EVENT candidates")
        val appName = handshake.appName
            ?: throw IllegalStateException("appName is required to report discovered LOG_EVENT candidates")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(reportUrlFor(agentId)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(writeRequest(handshake.workerId, appName, appVersion, events)))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()}")
        }
        return DiscoveredLogEventReportResult(received = events.size)
    }

    private fun reportUrlFor(agentId: Long): String {
        return reportUrl.replace("{agentId}", agentId.toString())
    }

    private fun writeRequest(
        workerId: String,
        appName: String,
        appVersion: String?,
        events: List<DiscoveredLogEventCandidate>
    ): String {
        return buildString {
            append("{\"workerId\":\"").append(escape(workerId)).append('"')
            append(",\"appName\":\"").append(escape(appName)).append('"')
            if (!appVersion.isNullOrBlank()) {
                append(",\"appVersion\":\"").append(escape(appVersion)).append('"')
            }
            append(",\"events\":[")
            events.forEachIndexed { index, event ->
                if (index > 0) append(',')
                append("{\"eventName\":\"").append(escape(event.eventName)).append('"')
                append(",\"sourceClass\":\"").append(escape(event.sourceClass)).append('"')
                append(",\"sourceMethod\":\"").append(escape(event.sourceMethod)).append('"')
                append(",\"parameterNames\":[")
                event.parameterNames.forEachIndexed { paramIndex, parameterName ->
                    if (paramIndex > 0) append(',')
                    append('"').append(escape(parameterName)).append('"')
                }
                append("]")
                event.specHint?.let {
                    append(",\"specHint\":").append(writeSpecHint(it))
                }
                append("}")
            }
            append("]}")
        }
    }

    private fun writeSpecHint(hint: LogSpecHint): String {
        return buildString {
            append("{")
            var needsComma = false
            fun field(name: String, value: String?) {
                if (value.isNullOrBlank()) return
                if (needsComma) append(',')
                append('"').append(name).append("\":\"").append(escape(value)).append('"')
                needsComma = true
            }
            field("description", hint.description)
            field("apiMethod", hint.apiMethod)
            field("apiPath", hint.apiPath)
            field("apiDescription", hint.apiDescription)
            if (needsComma) append(',')
            append("\"fields\":").append(writeFieldHints(hint.fields))
            append("}")
        }
    }

    private fun writeFieldHints(fields: List<LogFieldHint>): String {
        return buildString {
            append("[")
            fields.forEachIndexed { index, field ->
                if (index > 0) append(',')
                append("{\"name\":\"").append(escape(field.name)).append('"')
                field.description?.takeIf { it.isNotBlank() }?.let {
                    append(",\"description\":\"").append(escape(it)).append('"')
                }
                field.type?.takeIf { it.isNotBlank() }?.let {
                    append(",\"type\":\"").append(escape(it)).append('"')
                }
                append(",\"required\":").append(field.required)
                if (field.nestedFields.isNotEmpty()) {
                    append(",\"nestedFields\":").append(writeFieldHints(field.nestedFields))
                }
                append("}")
            }
            append("]")
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        fun fromIngestUrl(ingestUrl: String): DiscoveredLogEventReportClient {
            val template = URI.create(ingestUrl)
                .resolve("/api/agents/")
                .toString() + "{agentId}/discovered-log-events"
            return DiscoveredLogEventReportClient(template)
        }
    }
}

data class DiscoveredLogEventReportResult(
    val received: Int
)
