package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit
import org.gradle.api.logging.Logger

internal object PortConflictHandler {
    fun resolvePortAndCleanStale(originalPort: Int, host: String, logger: Logger): Int {
        if (isPortAvailable(host, originalPort)) {
            return originalPort
        }
        
        logger.lifecycle("[Parikshan] Port $originalPort is in use on $host. Probing for stale test server instance...")
        if (isStaleTestServer(host, originalPort)) {
            logger.lifecycle("[Parikshan] Detected stale Parikshan instance on port $originalPort. Attempting to auto-terminate...")
            val pid = findPidUsingPort(originalPort)
            if (pid != null) {
                logger.lifecycle("[Parikshan] Found stale process PID: $pid. Terminating...")
                terminateProcess(pid)
                var released = false
                for (i in 1..20) {
                    Thread.sleep(100)
                    if (isPortAvailable(host, originalPort)) {
                        released = true
                        break
                    }
                }
                if (released) {
                    logger.lifecycle("[Parikshan] Stale process terminated and port $originalPort released successfully.")
                    return originalPort
                } else {
                    logger.warn("[Parikshan] Failed to release port $originalPort after terminating PID $pid.")
                }
            } else {
                logger.warn("[Parikshan] Could not resolve PID for stale Parikshan instance on port $originalPort.")
            }
        } else {
            logger.lifecycle("[Parikshan] Port $originalPort is occupied by a non-Parikshan process or is unresponsive.")
        }
        
        var fallbackPort = originalPort + 1
        while (fallbackPort <= 65535) {
            if (isPortAvailable(host, fallbackPort)) {
                logger.lifecycle("[Parikshan] Port $originalPort is busy. Fallback chosen: $fallbackPort")
                return fallbackPort
            }
            fallbackPort++
        }
        
        error("No available ports found in range $originalPort to 65535 on $host")
    }

    internal fun isPortAvailable(host: String, port: Int): Boolean {
        return try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(host, port))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun isStaleTestServer(host: String, port: Int): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("http://$host:$port/")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Connection", "close")
            conn.doOutput = true
            conn.connectTimeout = 500
            conn.readTimeout = 500
            val probePayload = """{"type":"ping","id":"probe","token":"probe"}"""
            conn.outputStream.use { it.write(probePayload.toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 401) {
                val body = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
                body.contains("Unauthorized") || body.contains("Token mismatch")
            } else if (responseCode == 200) {
                val body = conn.inputStream?.use { it.bufferedReader().readText() }.orEmpty()
                body.contains("\"type\":\"Ok\"") || body.contains("pong")
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun queryApplicationId(host: String, port: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("http://$host:$port/")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Connection", "close")
            conn.connectTimeout = 500
            conn.readTimeout = 500
            if (conn.responseCode == 200) {
                val body = conn.inputStream?.use { it.bufferedReader().readText() }.orEmpty()
                val match = Regex("\"applicationId\"\\s*:\\s*\"([^\"]+)\"").find(body)
                match?.groupValues?.get(1)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    internal fun findPidUsingPort(port: Int, excludeCurrentPid: Boolean = true): Long? {
        val os = System.getProperty("os.name").lowercase()
        val currentPid = ProcessHandle.current().pid()
        return try {
            if (os.contains("win")) {
                val process = ProcessBuilder("cmd", "/c", "netstat -ano").start()
                val output = process.inputStream.bufferedReader().readText()
                output.lineSequence()
                    .filter { it.contains(":$port") && it.contains("LISTENING", ignoreCase = true) }
                    .map { it.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull() }
                    .filterNotNull()
                    .filter { !excludeCurrentPid || it != currentPid }
                    .firstOrNull()
            } else {
                val process = ProcessBuilder("lsof", "-t", "-i", ":$port").start()
                val output = process.inputStream.bufferedReader().readText().trim()
                output.lineSequence()
                    .map { it.trim().toLongOrNull() }
                    .filterNotNull()
                    .filter { !excludeCurrentPid || it != currentPid }
                    .firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun terminateProcess(pid: Long) {
        if (pid == ProcessHandle.current().pid()) {
            return
        }
        try {
            val handle = ProcessHandle.of(pid).orElse(null) ?: return
            if (!handle.isAlive) return
            handle.destroy()
            runCatching {
                handle.onExit().get(2, TimeUnit.SECONDS)
            }.onFailure {
                if (handle.isAlive) {
                    handle.destroyForcibly()
                }
            }
        } catch (_: Exception) {
            // Ignore security or permission exceptions to let the port fallback happen gracefully
        }
    }
}
