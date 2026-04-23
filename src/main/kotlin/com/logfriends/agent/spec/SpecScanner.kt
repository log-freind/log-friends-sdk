package com.logfriends.agent.spec

import com.logfriends.agent.BatchTransporter
import java.net.InetAddress

object SpecScanner {

    @JvmStatic
    fun scan() {
        try {
            val workerName = System.getProperty("spring.application.name") ?:
                             System.getProperty("logfriends.worker.name", "unknown")
            val host = try { InetAddress.getLocalHost().hostAddress } catch (e: Exception) { "unknown" }
            val pid = ProcessHandle.current().pid()
            val workerId = "$workerName-$host-$pid"
            BatchTransporter.getInstance().setWorkerId(workerId)

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
