@file:OptIn(InternalParikshanApi::class)

package io.github.aryapreetam.parikshan

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Represents an independent actor or application instance within a multi-instance E2E test.
 */
typealias Actor = E2ETestScope

/**
 * Test execution scope orchestrating multiple independent application instances.
 *
 * Each instance corresponds to an isolated application process, browser context,
 * or device session, identified by a unique name (such as `"alice"` or `"bob"`).
 *
 * Inside this scope, commands can be dispatched sequentially to individual instances
 * using [instance] or array indexing `scope["name"]`, or concurrently using [inParallel].
 *
 * ### Example Usage
 * ```kotlin
 * @Test
 * fun testCollaboration() = e2eTest(instances = listOf("alice", "bob")) {
 *   val alice = instance("alice")
 *   val bob = instance("bob")
 *
 *   alice.click("create_document")
 *   val docId = alice.getText("document_id")
 *
 *   bob.input("document_id_input", docId)
 *   bob.click("join_document")
 *
 *   alice.assertVisible("User Bob joined")
 * }
 * ```
 *
 * @param instances Map of instance identifiers to their active [E2ETestScope] instances.
 */
class E2EMultiInstanceScope(
  private val instances: Map<String, E2ETestScope>
) {
  init {
    require(instances.isNotEmpty()) { "E2EMultiInstanceScope requires at least one registered instance" }
    instances.keys.forEach { validateActorIdentifier(it) }
  }

  /**
   * Retrieves the test driver scope for a named application instance.
   *
   * @param name Unique instance identifier configured in the test declaration.
   * @throws IllegalArgumentException if no instance exists with the specified name.
   */
  fun instance(name: String): E2ETestScope {
    return instances[name]
      ?: throw IllegalArgumentException(
        "No application instance named '$name' found. Available instances: [${instances.keys.joinToString()}]"
      )
  }

  /**
   * Convenience operator allowing array-like indexing: `val alice = scope["alice"]`.
   */
  operator fun get(name: String): E2ETestScope = instance(name)

  /**
   * All registered instance names available in this multi-instance test scope.
   */
  val instanceNames: Set<String> get() = instances.keys

  /**
   * Returns the count of registered instances.
   */
  val size: Int get() = instances.size

  /**
   * Executes the specified suspending [block] concurrently across all registered instances.
   */
  suspend fun inParallel(block: suspend E2ETestScope.() -> Unit) {
    coroutineScope {
      instances.values.map { scope ->
        async {
          scope.block()
        }
      }.awaitAll()
    }
  }

  /**
   * Executes distinct action blocks concurrently across named instances in parallel.
   *
   * ### Example Usage
   * ```kotlin
   * inParallel(
   *   "alice" to { click("button_a") },
   *   "bob" to { click("button_b") }
   * )
   * ```
   */
  suspend fun inParallel(vararg actions: Pair<String, suspend E2ETestScope.() -> Unit>) {
    coroutineScope {
      actions.map { (name, action) ->
        async {
          instance(name).action()
        }
      }.awaitAll()
    }
  }

  operator fun component1(): E2ETestScope = elementAtOrFail(0)
  operator fun component2(): E2ETestScope = elementAtOrFail(1)
  operator fun component3(): E2ETestScope = elementAtOrFail(2)
  operator fun component4(): E2ETestScope = elementAtOrFail(3)
  operator fun component5(): E2ETestScope = elementAtOrFail(4)

  private fun elementAtOrFail(index: Int): E2ETestScope {
    if (index >= instances.size) {
      throw IllegalArgumentException(
        "Cannot destructure instance #${index + 1}: only ${instances.size} instance(s) declared [${instances.keys.joinToString()}]."
      )
    }
    return instances.values.elementAt(index)
  }
}

private val ACTOR_ID_PATTERN = Regex("^[a-zA-Z0-9_-]+$")

/**
 * Validates that [name] is a safe, portable actor identifier (`[a-zA-Z0-9_-]+`).
 */
@InternalParikshanApi
fun validateActorIdentifier(name: String) {
  require(name.isNotBlank() && ACTOR_ID_PATTERN.matches(name)) {
    "Invalid instance identifier '$name'. Instance identifiers must be non-empty and match [a-zA-Z0-9_-]+."
  }
}
