package com.logfriends.agent.transport

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class IngestHttpClientTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `posts json to ingest endpoint`() {
        var method = ""
        var contentType = ""
        var body = ""

        val url = startServer(200) { exchange ->
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        }

        IngestHttpClient(url).post("{\"workerId\":\"worker-1\",\"events\":[]}")

        assertEquals("POST", method)
        assertEquals("application/json", contentType)
        assertEquals("{\"workerId\":\"worker-1\",\"events\":[]}", body)
    }

    @Test
    fun `throws when ingest returns non 2xx status`() {
        val url = startServer(500)

        val error = assertThrows(RuntimeException::class.java) {
            IngestHttpClient(url).post("{\"workerId\":\"worker-1\",\"events\":[]}")
        }

        assertEquals("HTTP 500", error.message)
    }

    private fun startServer(
        statusCode: Int,
        handler: (HttpExchange) -> Unit = {}
    ): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/ingest") { exchange ->
            handler(exchange)
            exchange.sendResponseHeaders(statusCode, -1)
            exchange.close()
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}/ingest"
    }
}
