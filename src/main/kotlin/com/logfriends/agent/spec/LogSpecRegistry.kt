package com.logfriends.agent.spec

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object LogSpecRegistry {
    private val REGISTRY = ConcurrentHashMap<String, LogSpecDef>()

    @JvmStatic
    fun register(spec: LogSpecDef) {
        REGISTRY[spec.name] = spec
        println("[Log Friends] LogSpec registered: ${spec.name}")
    }

    @JvmStatic
    fun getAll(): Collection<LogSpecDef> {
        return Collections.unmodifiableCollection(REGISTRY.values)
    }

    @JvmStatic
    fun size(): Int {
        return REGISTRY.size
    }
}
