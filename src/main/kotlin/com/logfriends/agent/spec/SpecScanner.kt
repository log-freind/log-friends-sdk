package com.logfriends.agent.spec

import com.logfriends.agent.LogFriendsRuntime

object SpecScanner {

    @JvmStatic
    fun scan() {
        try {
            val workerId = LogFriendsRuntime.workerId ?: run {
                System.err.println("[Log Friends] SpecScanner skipped: workerId is not configured")
                return
            }

            val specs = LogSpecRegistry.getAll()
            println("[Log Friends] SpecScanner — workerId=$workerId, specs=${specs.size}")
            specs.forEach { spec ->
                println("[Log Friends]   spec: ${spec.name} (${spec.levels.joinToString()})")
            }
        } catch (e: Exception) {
            System.err.println("[Log Friends] SpecScanner error: ${e.message}")
        }
    }
}
