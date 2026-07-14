package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.logging.Logger
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortConflictHandlerTest {

    private val mockLogger = org.gradle.api.logging.Logging.getLogger(PortConflictHandlerTest::class.java)

    @Test
    fun testIsPortAvailable() {
        val port = findFreeLocalPort()
        assertTrue(PortConflictHandler.isPortAvailable("127.0.0.1", port), "Port $port should be available")

        ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).use {
            assertFalse(PortConflictHandler.isPortAvailable("127.0.0.1", port), "Port $port should be busy")
        }

        assertTrue(PortConflictHandler.isPortAvailable("127.0.0.1", port), "Port $port should be available again after closing ServerSocket")
    }

    @Test
    fun testFindPidUsingPort() {
        val port = findFreeLocalPort()
        ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).use {
            val pid = PortConflictHandler.findPidUsingPort(port, excludeCurrentPid = false)
            assertNotNull(pid, "PID should not be null when server is listening on port $port")
            assertEquals(ProcessHandle.current().pid(), pid, "Resolved PID should match the current process PID")
        }
    }

    @Test
    fun testIsStaleParikshanServerWithFakeStaleServer() {
        val port = findFreeLocalPort()
        
        val mockServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", port), 0)
        mockServer.createContext("/") { exchange ->
            val body = "Unauthorized: Token mismatch"
            exchange.sendResponseHeaders(401, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        mockServer.start()

        try {
            assertTrue(PortConflictHandler.isStaleTestServer("127.0.0.1", port), "Should detect fake stale server")
        } finally {
            mockServer.stop(0)
        }
    }

    @Test
    fun testIsStaleParikshanServerWithNonParikshanServer() {
        val port = findFreeLocalPort()
        
        val mockServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", port), 0)
        mockServer.createContext("/") { exchange ->
            val body = "Hello world"
            exchange.sendResponseHeaders(200, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        mockServer.start()

        try {
            assertFalse(PortConflictHandler.isStaleTestServer("127.0.0.1", port), "Should not flag generic server as stale Parikshan instance")
        } finally {
            mockServer.stop(0)
        }
    }

    @Test
    fun testResolvePortAndCleanStaleWithAvailablePort() {
        val port = findFreeLocalPort()
        val resolved = PortConflictHandler.resolvePortAndCleanStale(port, "127.0.0.1", mockLogger)
        assertEquals(port, resolved)
    }

    @Test
    fun testResolvePortAndCleanStaleWithDynamicFallback() {
        val busyPort = findFreeLocalPort()
        ServerSocket(busyPort, 50, java.net.InetAddress.getByName("127.0.0.1")).use {
            val resolved = PortConflictHandler.resolvePortAndCleanStale(busyPort, "127.0.0.1", mockLogger)
            assertTrue(resolved != busyPort, "Resolved port $resolved should not match busy port $busyPort")
            assertTrue(PortConflictHandler.isPortAvailable("127.0.0.1", resolved), "Resolved port $resolved should be free")
        }
    }

    @Test
    fun testResolvePortAndCleanStaleWithStaleProcessAutoKill() {
        val port = findFreeLocalPort()
        val javaExecutable = System.getProperty("java.home") + "/bin/java"
        val classpath = System.getProperty("java.class.path")
        val process = ProcessBuilder(
            javaExecutable,
            "-cp", classpath,
            "io.github.aryapreetam.parikshan.gradle.MockServerApp",
            port.toString()
        ).start()

        try {
            var serverStarted = false
            for (i in 1..50) {
                Thread.sleep(100)
                if (!PortConflictHandler.isPortAvailable("127.0.0.1", port)) {
                    serverStarted = true
                    break
                }
            }
            assertTrue(serverStarted, "Mock server process should have started and bound to port $port")

            val resolved = PortConflictHandler.resolvePortAndCleanStale(port, "127.0.0.1", mockLogger)
            assertEquals(port, resolved, "Should resolve to the same port after auto-killing stale server")
            assertTrue(PortConflictHandler.isPortAvailable("127.0.0.1", port), "Port $port should be free and available again")
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun findFreeLocalPort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}

class MockServerApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = args[0].toInt()
            val mockServer = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", port), 0)
            mockServer.createContext("/") { exchange ->
                val body = "Unauthorized: Token mismatch"
                exchange.sendResponseHeaders(401, body.length.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
            }
            mockServer.start()
            try {
                Thread.sleep(60_000L)
            } catch (_: InterruptedException) {
                // Exit
            }
        }
    }
}
