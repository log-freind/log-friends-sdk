package com.logfriends.agent.discovery

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object DiscoveredLogEventRegistry {
    private val candidates = ConcurrentHashMap<String, DiscoveredLogEventCandidate>()

    fun register(candidate: DiscoveredLogEventCandidate) {
        candidates[key(candidate)] = candidate
    }

    fun replaceAll(discovered: Collection<DiscoveredLogEventCandidate>) {
        discovered.forEach { register(it) }
    }

    fun getAll(): List<DiscoveredLogEventCandidate> {
        return Collections.unmodifiableCollection(candidates.values)
            .sortedWith(
                compareBy<DiscoveredLogEventCandidate> { it.eventName }
                    .thenBy { it.sourceClass }
                    .thenBy { it.sourceMethod }
            )
    }

    fun clear() {
        candidates.clear()
    }

    private fun key(candidate: DiscoveredLogEventCandidate): String =
        "${candidate.eventName}|${candidate.sourceClass}|${candidate.sourceMethod}"
}
