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
                for (i in 1..50) {
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
                val lsofPid = runCatching {
                    val process = ProcessBuilder("lsof", "-t", "-i", ":$port").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    output.lineSequence()
                        .map { it.trim().toLongOrNull() }
                        .filterNotNull()
                        .filter { !excludeCurrentPid || it != currentPid }
                        .firstOrNull()
                }.getOrNull()

                if (lsofPid != null) return lsofPid

                // Fallback to Linux /proc filesystem scanning (rootless socket inode lookup)
                val procPid = findLinuxProcPidForPort(port, currentPid, excludeCurrentPid)
                if (procPid != null) return procPid

                // Fallback to ss command for minimal Linux runners lacking lsof
                runCatching {
                    val process = ProcessBuilder("ss", "-tulpn").start()
                    val output = process.inputStream.bufferedReader().readText()
                    output.lineSequence()
                        .filter { it.contains(":$port") }
                        .mapNotNull { line ->
                            Regex("""pid=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()
                        }
                        .filter { !excludeCurrentPid || it != currentPid }
                        .firstOrNull()
                }.getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findLinuxProcPidForPort(port: Int, currentPid: Long, excludeCurrentPid: Boolean): Long? {
        return runCatching {
            val hexPort = port.toString(16).uppercase().padStart(4, '0')
            val tcpFiles = listOf(File("/proc/net/tcp"), File("/proc/net/tcp6"))
            val socketInodes = tcpFiles.filter { it.exists() }.flatMap { file ->
                file.useLines { lines ->
                    lines.mapNotNull { line ->
                        val tokens = line.trim().split(Regex("\\s+"))
                        if (tokens.size > 9) {
                            val localAddress = tokens[1]
                            val state = tokens[3]
                            val inode = tokens[9]
                            if (localAddress.endsWith(":$hexPort") && state == "0A") inode else null
                        } else null
                    }.toList()
                }
            }.toSet()

            if (socketInodes.isEmpty()) return null

            val procDir = File("/proc")
            if (!procDir.exists() || !procDir.isDirectory) return null

            val pidsToScan = mutableListOf<Long>()
            // Scan current pid first, then parent/other pids
            pidsToScan.add(currentPid)
            procDir.listFiles { _, name -> name.all { it.isDigit() } }?.forEach { f ->
                f.name.toLongOrNull()?.let { pid ->
                    if (pid != currentPid) pidsToScan.add(pid)
                }
            }

            for (pid in pidsToScan) {
                if (excludeCurrentPid && pid == currentPid) continue
                val fdDir = File("/proc/$pid/fd")
                if (!fdDir.exists()) continue
                val fds = fdDir.listFiles() ?: continue
                for (fd in fds) {
                    val linkTarget = runCatching {
                        java.nio.file.Files.readSymbolicLink(fd.toPath()).toString()
                    }.getOrNull().orEmpty()
                    if (socketInodes.any { inode -> linkTarget.contains("socket:[$inode]") }) {
                        return pid
                    }
                }
            }
            null
        }.getOrNull()
    }

    internal fun terminateProcess(pid: Long) {
        val currentPid = ProcessHandle.current().pid()
        val parentPid = ProcessHandle.current().parent().orElse(null)?.pid()
        if (pid == currentPid || (parentPid != null && pid == parentPid)) {
            return
        }
        try {
            val handle = ProcessHandle.of(pid).orElse(null) ?: return
            if (!handle.isAlive) return
            val cmd = handle.info().command().orElse("").lowercase()
            if (cmd.contains("gradle")) {
                return
            }
            handle.destroyForcibly()
            runCatching {
                handle.onExit().get(2, TimeUnit.SECONDS)
            }
        } catch (_: Exception) {
            // Ignore security or permission exceptions to let the port fallback happen gracefully
        }
    }
}
