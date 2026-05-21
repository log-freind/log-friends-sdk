package com.logfriends.agent.bootstrap

import org.springframework.core.env.ConfigurableEnvironment

object LogFriendsRuntime {
    @Volatile
    private var disabledReason: String? = null

    @Volatile
    private var configuredWorkerId: String? = null

    @Volatile
    private var configuredIngestUrl: String? = null

    val workerId: String?
        get() = configuredWorkerId

    val ingestUrl: String?
        get() = configuredIngestUrl

    fun isDisabled(): Boolean = disabledReason != null

    fun disabledReason(): String? = disabledReason

    fun disable(reason: String) {
        disabledReason = reason
    }

    fun configureWorkerId(environment: ConfigurableEnvironment): Boolean {
        val workerId = resolveWorkerId(environment)
        if (workerId == null) {
            disable("missing workerId")
            return false
        }
        configuredWorkerId = workerId
        return true
    }

    fun configureIngestUrl(environment: ConfigurableEnvironment): Boolean {
        val ingestUrl = resolveIngestUrl(environment)
        if (ingestUrl == null) {
            disable("missing ingestUrl")
            return false
        }
        configuredIngestUrl = ingestUrl
        return true
    }

    private fun resolveWorkerId(environment: ConfigurableEnvironment): String? {
        return resolveConfiguredValue(
            System.getenv("LOGFRIENDS_WORKER_ID"),
            environment.getProperty("LOGFRIENDS_WORKER_ID"),
            environment.getProperty("logfriends.worker.id")
        )
    }

    private fun resolveIngestUrl(environment: ConfigurableEnvironment): String? {
        return resolveConfiguredValue(
            System.getenv("LOGFRIENDS_INGEST_URL"),
            environment.getProperty("LOGFRIENDS_INGEST_URL"),
            environment.getProperty("logfriends.ingest.url")
        )
    }

    internal fun resolveConfiguredValue(vararg candidates: String?): String? {
        return candidates.asSequence()
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }
}
