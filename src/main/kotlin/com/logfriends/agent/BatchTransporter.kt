package com.logfriends.agent

import com.logfriends.agent.proto.AgentEvent
import com.logfriends.agent.proto.AgentMessage
import com.logfriends.agent.proto.BatchPayload
import com.logfriends.agent.proto.HttpEvent
import com.logfriends.agent.proto.LogEvent
import com.logfriends.agent.proto.LogEventCapture
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Instant
import java.util.Properties
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BatchTransporter private constructor(
    private val brokers: String,
    private val batchSize: Int,
    private val intervalMs: Long
) {

    private val queue: BlockingQueue<AgentEvent>
    private val scheduler: ScheduledExecutorService
    private val running = AtomicBoolean(true)
    private val sentCount = AtomicLong(0)
    private val dropCount = AtomicLong(0)

    @Volatile
    private var workerId: String = "unknown"

    // 첫 전송 시점까지 KafkaProducer 초기화를 지연 (slf4j 로드 보장)
    private val producer: KafkaProducer<String, ByteArray> by lazy {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "1")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.LINGER_MS_CONFIG, 5)
            put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000)
        }
        KafkaProducer(props)
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
        durationMs: Long, traceId: String?
    ) {
        val proto = HttpEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setMethod(method)
            .setUri(uri)
            .setStatusCode(statusCode)
            .setDurationMs(durationMs)
            .apply { if (!traceId.isNullOrEmpty()) setTraceId(traceId) }
            .build()
        enqueue(AgentEvent.newBuilder().setHttp(proto).build())
    }

    fun enqueueJdbc(
        sql: String, durationMs: Long, rowCount: Int,
        traceId: String?, exception: String?
    ) {
        val proto = com.logfriends.agent.proto.JdbcEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setSql(sql)
            .setDurationMs(durationMs)
            .setRowCount(rowCount)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!exception.isNullOrEmpty()) setException(exception)
            }
            .build()
        enqueue(AgentEvent.newBuilder().setJdbc(proto).build())
    }

    fun enqueueMethodTrace(
        className: String, methodName: String, durationMs: Long,
        traceId: String?, exception: String?
    ) {
        val proto = com.logfriends.agent.proto.MethodTraceEvent.newBuilder()
            .setTimestamp(Instant.now().toString())
            .setClassName(className)
            .setMethodName(methodName)
            .setDurationMs(durationMs)
            .apply {
                if (!traceId.isNullOrEmpty()) setTraceId(traceId)
                if (!exception.isNullOrEmpty()) setException(exception)
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
        producer.close()
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

        val batch = BatchPayload.newBuilder().addAllEvents(buffer).build()
        val msg = AgentMessage.newBuilder()
            .setWorkerId(workerId)
            .setBatch(batch)
            .build()

        try {
            val record = ProducerRecord("log-friends.batch", workerId, msg.toByteArray())
            producer.send(record) { _, ex ->
                if (ex != null) {
                    System.err.println("[Log Friends] Batch send failed: ${ex.message}")
                    buffer.forEach { queue.offer(it) }
                } else {
                    sentCount.addAndGet(buffer.size.toLong())
                }
            }
        } catch (e: Exception) {
            System.err.println("[Log Friends] Batch flush error: ${e.message}")
            buffer.forEach { queue.offer(it) }
        }
    }

    companion object {
        private const val DEFAULT_BROKERS = "localhost:9092"
        private const val DEFAULT_BATCH_SIZE = 100
        private const val DEFAULT_INTERVAL_MS = 500L

        @Volatile
        private var instance: BatchTransporter? = null

        @JvmStatic
        fun getInstance(): BatchTransporter {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val brokers = System.getenv("LOGFRIENDS_KAFKA_BROKERS")
                        ?: System.getProperty("logfriends.kafka.brokers", DEFAULT_BROKERS)
                    val batch = System.getProperty("logfriends.batch.size", DEFAULT_BATCH_SIZE.toString()).toInt()
                    val interval = System.getProperty("logfriends.batch.interval.ms", DEFAULT_INTERVAL_MS.toString()).toLong()
                    BatchTransporter(brokers, batch, interval).also { instance = it }
                }
            }
        }
    }
}
