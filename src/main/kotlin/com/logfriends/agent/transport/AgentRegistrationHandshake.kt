package com.logfriends.agent.transport

data class AgentRegistrationHandshake(
    val agentId: Long?,
    val workerId: String,
    val appName: String?,
    val knownLogSpecs: List<KnownLogSpec>
) {
    val knownLogSpecCount: Int
        get() = knownLogSpecs.size
}

data class KnownLogSpec(
    val eventName: String,
    val updatedAt: String?
)
