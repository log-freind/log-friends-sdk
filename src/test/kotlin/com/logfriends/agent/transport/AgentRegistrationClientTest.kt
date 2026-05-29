package com.logfriends.agent.transport

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class AgentRegistrationClientTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `posts worker and app name to agent endpoint derived from ingest url`() {
        var path = ""
        var method = ""
        var contentType = ""
        var body = ""
        val ingestUrl = startServer(
            statusCode = 201,
            responseBody = registrationResponse()
        ) { exchange ->
            path = exchange.requestURI.path
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        }

        val handshake = AgentRegistrationClient.fromIngestUrl(ingestUrl).register("worker-1", "order-service")

        assertEquals("/api/agents", path)
        assertEquals("POST", method)
        assertEquals("application/json", contentType)
        assertTrue(body.contains("\"workerId\":\"worker-1\""))
        assertTrue(body.contains("\"appName\":\"order-service\""))
        assertEquals(1L, handshake.agentId)
        assertEquals("worker-1", handshake.workerId)
        assertEquals("order-service", handshake.appName)
        assertEquals(listOf("orderCancelled", "orderCreated"), handshake.knownLogSpecs.map { it.eventName })
    }

    @Test
    fun `parses heartbeat response when worker is already registered`() {
        var heartbeatPath = ""
        val ingestUrl = startServer(
            statusCode = 409,
            heartbeatResponseBody = heartbeatResponse()
        ) { exchange ->
            if (exchange.requestURI.path.endsWith("/heartbeat")) {
                heartbeatPath = exchange.requestURI.path
            }
        }

        val handshake = AgentRegistrationClient.fromIngestUrl(ingestUrl).register("worker-1", "order-service")

        assertEquals("/api/agents/heartbeat", heartbeatPath)
        assertEquals(7L, handshake.agentId)
        assertEquals("worker-1", handshake.workerId)
        assertEquals("order-service", handshake.appName)
        assertTrue(handshake.knownLogSpecs.isEmpty())
    }

    private fun startServer(
        statusCode: Int,
        responseBody: String = "",
        heartbeatResponseBody: String = "",
        handler: (HttpExchange) -> Unit = {}
    ): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/agents") { exchange ->
            handler(exchange)
            respond(exchange, statusCode, responseBody)
            exchange.close()
        }
        httpServer.createContext("/api/agents/heartbeat") { exchange ->
            handler(exchange)
            respond(exchange, 200, heartbeatResponseBody)
            exchange.close()
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}/ingest"
    }

    private fun respond(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun registrationResponse(): String {
        return """
            {
              "agentId": 1,
              "workerId": "worker-1",
              "appName": "order-service",
              "knownLogSpecs": [
                {"eventName": "orderCreated", "updatedAt": "2026-05-27T00:00:00Z"},
                {"eventName": "orderCancelled", "updatedAt": "2026-05-27T00:00:00Z"}
              ]
            }
        """.trimIndent()
    }

    private fun heartbeatResponse(): String {
        return """
            {
              "id": 7,
              "workerId": "worker-1",
              "appName": "order-service"
            }
        """.trimIndent()
    }
}
