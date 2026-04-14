package com.logfriends.agent

import com.logfriends.agent.spec.SpecScanner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@AutoConfiguration
class LogFriendsAutoConfiguration {

    @Bean
    fun logFriendsReadyListener(): ApplicationListener<ApplicationReadyEvent> {
        return ApplicationListener {
            val executor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "log-friends-spec-scanner").apply { isDaemon = true }
            }

            // 5초 딜레이: Spring @Bean 초기화 완료 후 스펙 스캔
            executor.schedule({
                try {
                    SpecScanner.scan()
                } catch (e: Exception) {
                    System.err.println("[Log Friends] SpecScanner failed: ${e.message}")
                }
            }, 5, TimeUnit.SECONDS)

            Runtime.getRuntime().addShutdownHook(Thread({
                val transporter = BatchTransporter.getInstance()
                println("[Log Friends] Shutting down. Stats: ${transporter.stats}")
                transporter.shutdown()
            }, "log-friends-shutdown"))

            val kafkaBrokers = System.getenv("LOGFRIENDS_KAFKA_BROKERS")
                ?: System.getProperty("logfriends.kafka.brokers", "localhost:9092")
            println("[Log Friends] Agent ready. Kafka brokers: $kafkaBrokers")
        }
    }
}
