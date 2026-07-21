package io.github.aryapreetam.parikshan

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BeforeAll

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterAll


/**
 * Interface to provide per-test E2E lifecycle hooks.
 * Implement this on your test class to automatically wrap [e2eTest] blocks.
 */
interface E2ETestLifecycle {
    /**
     * Executes inside the active [E2ETestScope] before every test block runs.
     */
    suspend fun E2ETestScope.beforeEach() {}

    /**
     * Executes inside the active [E2ETestScope] after every test block completes (success or failure).
     */
    suspend fun E2ETestScope.afterEach() {}
}
