package com.logfriends.agent.transport

import com.logfriends.agent.discovery.DiscoveredLogEventCandidate
import com.logfriends.agent.discovery.LogFieldHint
import com.logfriends.agent.discovery.LogSpecHint
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class DiscoveredLogEventReportClientTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `reports discovered log events to agent scoped endpoint`() {
        var path = ""
        var method = ""
        var contentType = ""
        var body = ""
        val ingestUrl = startServer { exchange ->
            path = exchange.requestURI.path
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        }

        val result = DiscoveredLogEventReportClient.fromIngestUrl(ingestUrl)
            .report(
                handshake = AgentRegistrationHandshake(
                    agentId = 7L,
                    workerId = "order-service-local-1",
                    appName = "order-service",
                    knownLogSpecs = emptyList()
                ),
                appVersion = "0.2.0",
                events = listOf(
                    DiscoveredLogEventCandidate(
                        eventName = "orderCreated",
                        sourceClass = "com.example.OrderService",
                        sourceMethod = "createOrder",
                        parameterNames = listOf("request"),
                        specHint = LogSpecHint(
                            description = "Order creation business eventName",
                            apiMethod = "POST",
                            apiPath = "/orders",
                            apiDescription = "Creates an order from an OrderRequest DTO",
                            fields = listOf(
                                LogFieldHint(
                                    name = "request",
                                    description = "OrderRequest DTO object",
                                    type = "JSON",
                                    nestedFields = listOf(
                                        LogFieldHint(
                                            name = "customerEmail",
                                            description = "Buyer email. Masked by SDK before transport",
                                            type = "STRING",
                                            required = false
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

        assertEquals(1, result.received)
        assertEquals("/api/agents/7/discovered-log-events", path)
        assertEquals("POST", method)
        assertEquals("application/json", contentType)
        assertTrue(body.contains("\"workerId\":\"order-service-local-1\""))
        assertTrue(body.contains("\"appName\":\"order-service\""))
        assertTrue(body.contains("\"appVersion\":\"0.2.0\""))
        assertTrue(body.contains("\"eventName\":\"orderCreated\""))
        assertTrue(body.contains("\"sourceClass\":\"com.example.OrderService\""))
        assertTrue(body.contains("\"sourceMethod\":\"createOrder\""))
        assertTrue(body.contains("\"parameterNames\":[\"request\"]"))
        assertTrue(body.contains("\"specHint\":{"))
        assertTrue(body.contains("\"description\":\"Order creation business eventName\""))
        assertTrue(body.contains("\"apiMethod\":\"POST\""))
        assertTrue(body.contains("\"apiPath\":\"/orders\""))
        assertTrue(body.contains("\"fields\":[{\"name\":\"request\""))
        assertTrue(body.contains("\"nestedFields\":[{\"name\":\"customerEmail\""))
        assertTrue(body.contains("\"required\":false"))
    }

    private fun startServer(handler: (HttpExchange) -> Unit): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/agents/7/discovered-log-events") { exchange ->
            handler(exchange)
            respond(exchange, 200, "{\"received\":1,\"upserted\":1}")
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
}
