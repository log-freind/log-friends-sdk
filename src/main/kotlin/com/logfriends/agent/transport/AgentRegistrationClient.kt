package com.logfriends.agent.transport

import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class AgentRegistrationClient(
    private val registrationUrl: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {

    fun register(workerId: String, appName: String): AgentRegistrationHandshake {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(registrationUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(writeRequest(workerId, appName)))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 409) {
            return heartbeat(workerId, appName)
        }
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()}")
        }
        return AgentRegistrationResponseParser.parse(response.body().orEmpty(), workerId, appName)
    }

    private fun heartbeat(workerId: String, appName: String): AgentRegistrationHandshake {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(registrationUrl).resolve("/api/agents/heartbeat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"workerId\":\"${escape(workerId)}\"}"))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("heartbeat HTTP ${response.statusCode()}")
        }
        return AgentRegistrationResponseParser.parse(response.body().orEmpty(), workerId, appName)
    }

    private fun writeRequest(workerId: String, appName: String): String {
        return buildString {
            append("{\"workerId\":\"").append(escape(workerId)).append('"')
            append(",\"appName\":\"").append(escape(appName)).append('"')
            append(",\"javaVersion\":\"").append(escape(System.getProperty("java.version", "unknown"))).append('"')
            hostname()?.let { append(",\"hostname\":\"").append(escape(it)).append('"') }
            append('}')
        }
    }

    private fun hostname(): String? {
        return runCatching { InetAddress.getLocalHost().hostName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
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
        fun fromIngestUrl(ingestUrl: String): AgentRegistrationClient {
            return AgentRegistrationClient(URI.create(ingestUrl).resolve("/api/agents").toString())
        }
    }
}
