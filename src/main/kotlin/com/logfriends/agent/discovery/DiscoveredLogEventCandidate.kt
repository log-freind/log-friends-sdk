package com.logfriends.agent.discovery

data class DiscoveredLogEventCandidate(
    val eventName: String,
    val sourceClass: String,
    val sourceMethod: String,
    val parameterNames: List<String>
)
