# Module parikshan-client

The client module is the primary entry point for developers. It provides the end-to-end (E2E) testing DSL and the platform-specific drivers that execute the tests.

## Key Components

- **E2E DSL**: A Kotlin DSL (e.g., `click()`, `input()`, `assertVisible()`) for writing test scenarios in the `commonTest` source set.
- **Drivers**: Modules that handle communication with Android, iOS, Wasm, and Desktop applications.
- **Session Management**: Controls video recording lifecycles and app restarts during test execution.
