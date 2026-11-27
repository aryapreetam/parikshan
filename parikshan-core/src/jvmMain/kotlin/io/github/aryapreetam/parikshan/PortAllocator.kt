package io.github.aryapreetam.parikshan

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ephemeral port allocator and network availability probing utility.
 *
 * Provides deterministic, collision-free local port discovery for multi-instance
 * test drivers and embedded application servers.
 */
@InternalParikshanApi
object PortAllocator {

  /**
   * Default fallback port range for private, unprivileged dynamic allocation.
   */
  val DEFAULT_PORT_RANGE: IntRange = 10000..25000

  private val lock = Any()
  private val rangeOffset = AtomicInteger((0..1000).random())
  private val recentlyAllocated = LinkedHashMap<Int, Long>()
  private const val RESERVATION_TTL_MS = 30_000L

  /**
   * Determines whether the specified port is currently available for binding on the given host address.
   *
   * @param port Port number to probe (valid range 1..65535).
   * @param host Target host address to bind against, defaulting to loopback `127.0.0.1`.
   * @return True if the port is free and can be successfully bound, false otherwise.
   */
  fun isPortFree(port: Int, host: String = "127.0.0.1"): Boolean {
    if (port !in 1..65535) return false
    return try {
      // 1. Probe wildcard binding (detects if any process bound 0.0.0.0:port or 127.0.0.1:port)
      ServerSocket(port).close()
      // 2. Probe host-specific binding if different from wildcard
      if (host != "0.0.0.0") {
        ServerSocket(port, 1, InetAddress.getByName(host)).close()
      }
      true
    } catch (_: IOException) {
      false
    } catch (_: SecurityException) {
      false
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  /**
   * Allocates an ephemeral loopback port using the operating system's socket allocator (`ServerSocket(0)`).
   *
   * @return A free local port number.
   */
  fun allocateEphemeralPort(): Int {
    return ServerSocket(0).use { socket ->
      socket.reuseAddress = true
      socket.localPort
    }
  }

  /**
   * Allocates a free port, preferring [preferredPort] when specified and available,
   * with fallback to [fallbackRange], and finally falling back to an OS ephemeral port.
   *
   * @param preferredPort Desired port number. If specified and free, this port is returned.
   * @param fallbackRange Range of ports to probe if [preferredPort] is null or occupied.
   * @param excludePorts Set of ports to never allocate, avoiding collisions during concurrent batching.
   * @return An allocated, verified available port number.
   */
  fun allocate(
    preferredPort: Int? = null,
    fallbackRange: IntRange? = DEFAULT_PORT_RANGE,
    excludePorts: Set<Int> = emptySet()
  ): Int = synchronized(lock) {
    pruneOldReservations()

    val combinedExcluded = excludePorts + recentlyAllocated.keys

    if (preferredPort != null && preferredPort !in excludePorts && isPortFree(preferredPort)) {
      recordAllocation(preferredPort)
      return preferredPort
    }

    if (fallbackRange != null && !fallbackRange.isEmpty()) {
      val rangeSize = fallbackRange.count()
      val startOffset = Math.floorMod(rangeOffset.getAndIncrement(), rangeSize)
      for (i in 0 until rangeSize) {
        val candidate = fallbackRange.first + (startOffset + i) % rangeSize
        if (candidate !in combinedExcluded && isPortFree(candidate)) {
          recordAllocation(candidate)
          return candidate
        }
      }
    }

    // Ephemeral OS fallback
    repeat(20) {
      val ephemeral = allocateEphemeralPort()
      if (ephemeral !in combinedExcluded && isPortFree(ephemeral)) {
        recordAllocation(ephemeral)
        return ephemeral
      }
    }

    val ultimateFallback = allocateEphemeralPort()
    recordAllocation(ultimateFallback)
    return ultimateFallback
  }

  /**
   * Allocates a collection of [count] unique, mutually non-colliding available ports.
   *
   * @param count Number of distinct ports required.
   * @param preferredPorts List of preferred ports to attempt to fulfill first.
   * @param fallbackRange Range of ports to probe for remaining required ports.
   * @return List of [count] distinct available ports.
   */
  fun allocateUnique(
    count: Int,
    preferredPorts: List<Int> = emptyList(),
    fallbackRange: IntRange? = DEFAULT_PORT_RANGE
  ): List<Int> = synchronized(lock) {
    require(count >= 0) { "Requested port count must be non-negative: $count" }
    if (count == 0) return emptyList()

    val allocated = mutableListOf<Int>()
    val excluded = mutableSetOf<Int>()

    for (pref in preferredPorts) {
      if (allocated.size == count) break
      if (pref !in excluded && isPortFree(pref)) {
        allocated.add(pref)
        excluded.add(pref)
        recordAllocation(pref)
      }
    }

    while (allocated.size < count) {
      val port = allocate(preferredPort = null, fallbackRange = fallbackRange, excludePorts = excluded)
      allocated.add(port)
      excluded.add(port)
    }

    return allocated
  }

  private fun recordAllocation(port: Int) {
    recentlyAllocated[port] = System.currentTimeMillis()
  }

  private fun pruneOldReservations() {
    val cutoff = System.currentTimeMillis() - RESERVATION_TTL_MS
    val iterator = recentlyAllocated.entries.iterator()
    while (iterator.hasNext()) {
      if (iterator.next().value < cutoff) {
        iterator.remove()
      }
    }
  }
}
