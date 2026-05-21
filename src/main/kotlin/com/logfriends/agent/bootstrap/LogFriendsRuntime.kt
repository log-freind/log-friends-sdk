package com.logfriends.agent.bootstrap

import org.springframework.core.env.ConfigurableEnvironment

object LogFriendsRuntime {
    @Volatile
    private var disabledReason: String? = null

    @Volatile
    private var configuredWorkerId: String? = null

    @Volatile
    private var configuredIngestUrl: String? = null

    @Volatile
    private var configuredAppName: String? = null

    val workerId: String?
        get() = configuredWorkerId

    val ingestUrl: String?
        get() = configuredIngestUrl

    val appName: String?
        get() = configuredAppName

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

    fun configureAppName(environment: ConfigurableEnvironment) {
        configuredAppName = resolveAppName(environment)
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

    private fun resolveAppName(environment: ConfigurableEnvironment): String? {
        return resolveAppName(
            System.getenv("LOGFRIENDS_APP_NAME"),
            environment.getProperty("LOGFRIENDS_APP_NAME"),
            environment.getProperty("logfriends.app.name"),
            environment.getProperty("spring.application.name")
        )
    }

    internal fun resolveAppName(
        envAppName: String?,
        environmentAppName: String?,
        propertyAppName: String?,
        springApplicationName: String?
    ): String? {
        return resolveConfiguredValue(
            envAppName,
            environmentAppName,
            propertyAppName,
            springApplicationName
        )
    }

    internal fun resolveConfiguredValue(vararg candidates: String?): String? {
        return candidates.asSequence()
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }
}
