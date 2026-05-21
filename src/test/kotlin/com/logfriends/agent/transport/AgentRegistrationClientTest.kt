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
        val ingestUrl = startServer(201) { exchange ->
            path = exchange.requestURI.path
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        }

        AgentRegistrationClient.fromIngestUrl(ingestUrl).register("worker-1", "order-service")

        assertEquals("/api/agents", path)
        assertEquals("POST", method)
        assertEquals("application/json", contentType)
        assertTrue(body.contains("\"workerId\":\"worker-1\""))
        assertTrue(body.contains("\"appName\":\"order-service\""))
    }

    @Test
    fun `accepts conflict when worker is already registered`() {
        var heartbeatPath = ""
        val ingestUrl = startServer(409) { exchange ->
            if (exchange.requestURI.path.endsWith("/heartbeat")) {
                heartbeatPath = exchange.requestURI.path
            }
        }

        AgentRegistrationClient.fromIngestUrl(ingestUrl).register("worker-1", "order-service")

        assertEquals("/api/agents/heartbeat", heartbeatPath)
    }

    private fun startServer(
        statusCode: Int,
        handler: (HttpExchange) -> Unit = {}
    ): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/agents") { exchange ->
            handler(exchange)
            exchange.sendResponseHeaders(statusCode, -1)
            exchange.close()
        }
        httpServer.createContext("/api/agents/heartbeat") { exchange ->
            handler(exchange)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}/ingest"
    }
}
