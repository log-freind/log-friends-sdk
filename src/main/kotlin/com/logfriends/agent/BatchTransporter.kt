package com.logfriends.agent

import com.logfriends.agent.proto.AgentEvent
import com.logfriends.agent.proto.HttpEvent
import com.logfriends.agent.proto.LogEvent
import com.logfriends.agent.proto.LogEventCapture
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BatchTransporter private constructor(
    private val batchSize: Int,
    private val intervalMs: Long
) {

    private val ingestUrl: String = run {
        System.getenv("LOGFRIENDS_INGEST_URL")
            ?: System.getProperty("logfriends.ingest.url", "http://localhost:8082/ingest")
    }

    private val queue: BlockingQueue<AgentEvent>
    private val scheduler: ScheduledExecutorService
    private val running = AtomicBoolean(true)
    private val sentCount = AtomicLong(0)
    private val dropCount = AtomicLong(0)

    @Volatile
    private var workerId: String = "unknown"

    fun setWorkerId(id: String) {
        workerId = id
    }

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    init {
        val queueCapacity = System.getProperty("logfriends.queue.capacity", "10000").toInt()
        queue = LinkedBlockingQueue(queueCapacity)

        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "log-friends-batch-flush").apply { isDaemon = true }
        }

        scheduler.scheduleAtFixedRate(
            this::flush,
            intervalMs, intervalMs, TimeUnit.MILLISECONDS
        )
    }

    fun enqueueLog(
        level: String, loggerName: String, threadName: String,
        message: String, traceId: String?, exception: String?,
        mdc: Map<String, String>?
    ) {
        val proto = LogEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setLevel(level)
            .setLoggerName(loggerName)
            .setThreadName(threadName)
            .setMessage(message)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!exception.isNullOrEmpty()) setException(exception)
                if (!mdc.isNullOrEmpty()) putAllMdc(mdc)
            }
            .build()
        enqueue(AgentEvent.newBuilder().setLog(proto).build())
    }

    fun enqueueHttp(
        method: String, uri: String, statusCode: Int,
        durationMs: Long, traceId: String?,
        requestHeaders: Map<String, String>? = null,
        exceptionStack: String? = null
    ) {
        val proto = HttpEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setMethod(method)
            .setUri(uri)
            .setStatusCode(statusCode)
            .setDurationMs(durationMs)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!requestHeaders.isNullOrEmpty()) putAllRequestHeaders(requestHeaders)
                if (!exceptionStack.isNullOrEmpty()) setExceptionStack(exceptionStack)
            }
            .build()
        enqueue(AgentEvent.newBuilder().setHttp(proto).build())
    }

    fun enqueueJdbc(
        sql: String, durationMs: Long, rowCount: Int,
        traceId: String?, exception: String?, exceptionStack: String? = null
    ) {
        val proto = com.logfriends.agent.proto.JdbcEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setSql(sql)
            .setDurationMs(durationMs)
            .setRowCount(rowCount)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!exception.isNullOrEmpty()) setException(exception)
                if (!exceptionStack.isNullOrEmpty()) setExceptionStack(exceptionStack)
            }
            .build()
        enqueue(AgentEvent.newBuilder().setJdbc(proto).build())
    }

    fun enqueueMethodTrace(
        className: String, methodName: String, durationMs: Long,
        traceId: String?, exception: String?, exceptionStack: String? = null
    ) {
        val proto = com.logfriends.agent.proto.MethodTraceEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setClassName(className)
            .setMethodName(methodName)
            .setDurationMs(durationMs)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!exception.isNullOrEmpty()) setException(exception)
                if (!exceptionStack.isNullOrEmpty()) setExceptionStack(exceptionStack)
            }
            .build()
        enqueue(AgentEvent.newBuilder().setMethodTrace(proto).build())
    }

    fun enqueueLogEvent(eventName: String, paramNames: Array<String>, args: Array<Any?>) {
        val fields = mutableMapOf<String, String>()
        for (i in paramNames.indices) {
            fields[paramNames[i]] = args.getOrNull(i)?.toString() ?: ""
        }
        val proto = LogEventCapture.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setEventName(eventName)
            .putAllFields(fields)
            .build()
        enqueue(AgentEvent.newBuilder().setLogEvent(proto).build())
    }

    fun shutdown() {
        running.set(false)
        flush()
        scheduler.shutdown()
    }

    val stats: String
        get() = "sent=${sentCount.get()}, dropped=${dropCount.get()}, queued=${queue.size}"

    private fun enqueue(event: AgentEvent) {
        if (!queue.offer(event)) {
            dropCount.incrementAndGet()
        }
        if (queue.size >= batchSize) {
            scheduler.execute(this::flush)
        }
    }

    @Synchronized
    private fun flush() {
        val buffer = ArrayList<AgentEvent>(batchSize)
        queue.drainTo(buffer, batchSize)
        if (buffer.isEmpty()) return

        val json = buildJson(workerId, buffer)
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(ingestUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()}")
            }
            sentCount.addAndGet(buffer.size.toLong())
        } catch (e: Exception) {
            System.err.println("[Log Friends] Batch flush error: ${e.message}")
            buffer.forEach { queue.offer(it) }
        }
    }

    private fun buildJson(workerId: String, events: List<AgentEvent>): String {
        val sb = StringBuilder()
        sb.append("{\"workerId\":\"").append(esc(workerId)).append("\",\"events\":[")
        events.forEachIndexed { i, evt ->
            if (i > 0) sb.append(",")
            sb.append(eventToJson(evt))
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun eventToJson(evt: AgentEvent): String {
        val sb = StringBuilder()
        when {
            evt.hasLog() -> {
                val e = evt.log
                sb.append("{\"type\":\"LOG\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"level\":\"").append(esc(e.level)).append("\"")
                sb.append(",\"loggerName\":\"").append(esc(e.loggerName)).append("\"")
                sb.append(",\"threadName\":\"").append(esc(e.threadName)).append("\"")
                sb.append(",\"message\":\"").append(esc(e.message)).append("\"")
                if (e.traceId.isNotBlank()) sb.append(",\"traceId\":\"").append(esc(e.traceId)).append("\"")
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
                if (e.mdcMap.isNotEmpty()) sb.append(",\"mdc\":").append(mapToJson(e.mdcMap))
            }
            evt.hasHttp() -> {
                val e = evt.http
                sb.append("{\"type\":\"HTTP\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"method\":\"").append(esc(e.method)).append("\"")
                sb.append(",\"uri\":\"").append(esc(e.uri)).append("\"")
                sb.append(",\"statusCode\":").append(e.statusCode)
                sb.append(",\"durationMs\":").append(e.durationMs)
                if (e.traceId.isNotBlank()) sb.append(",\"traceId\":\"").append(esc(e.traceId)).append("\"")
                if (e.exceptionStack.isNotBlank()) sb.append(",\"exceptionStack\":\"").append(esc(e.exceptionStack)).append("\"")
                if (e.requestHeadersMap.isNotEmpty()) sb.append(",\"requestHeaders\":").append(mapToJson(e.requestHeadersMap))
            }
            evt.hasJdbc() -> {
                val e = evt.jdbc
                sb.append("{\"type\":\"JDBC\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"sql\":\"").append(esc(e.sql)).append("\"")
                sb.append(",\"durationMs\":").append(e.durationMs)
                sb.append(",\"rowCount\":").append(e.rowCount)
                if (e.traceId.isNotBlank()) sb.append(",\"traceId\":\"").append(esc(e.traceId)).append("\"")
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
                if (e.exceptionStack.isNotBlank()) sb.append(",\"exceptionStack\":\"").append(esc(e.exceptionStack)).append("\"")
            }
            evt.hasMethodTrace() -> {
                val e = evt.methodTrace
                sb.append("{\"type\":\"METHOD_TRACE\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"className\":\"").append(esc(e.className)).append("\"")
                sb.append(",\"methodName\":\"").append(esc(e.methodName)).append("\"")
                sb.append(",\"durationMs\":").append(e.durationMs)
                if (e.traceId.isNotBlank()) sb.append(",\"traceId\":\"").append(esc(e.traceId)).append("\"")
                if (e.exception.isNotBlank()) sb.append(",\"exception\":\"").append(esc(e.exception)).append("\"")
                if (e.exceptionStack.isNotBlank()) sb.append(",\"exceptionStack\":\"").append(esc(e.exceptionStack)).append("\"")
            }
            evt.hasLogEvent() -> {
                val e = evt.logEvent
                sb.append("{\"type\":\"LOG_EVENT\"")
                sb.append(",\"timestamp\":\"").append(esc(e.timestamp)).append("\"")
                sb.append(",\"eventName\":\"").append(esc(e.eventName)).append("\"")
                if (e.fieldsMap.isNotEmpty()) sb.append(",\"fields\":").append(mapToJson(e.fieldsMap))
            }
            else -> sb.append("{\"type\":\"UNKNOWN\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun mapToJson(map: Map<String, String>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(esc(k)).append("\":\"").append(esc(v)).append("\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    companion object {
        private const val DEFAULT_BATCH_SIZE = 100
        private const val DEFAULT_INTERVAL_MS = 500L

        @Volatile
        private var instance: BatchTransporter? = null

        @JvmStatic
        fun getInstance(): BatchTransporter {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val batch = System.getProperty("logfriends.batch.size", DEFAULT_BATCH_SIZE.toString()).toInt()
                    val interval = System.getProperty("logfriends.batch.interval.ms", DEFAULT_INTERVAL_MS.toString()).toLong()
                    BatchTransporter(batch, interval).also { instance = it }
                }
            }
        }
    }
}
