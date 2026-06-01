# Module parikshan-client

The client module is the primary entry point for developers. it provides the end-to-end (E2E) testing DSL and the platform-specific drivers that orchestrate test execution.

## Key Components

- **E2E DSL**: A powerful, readable Kotlin DSL (e.g., `click()`, `inputText()`, `assertVisible()`) for writing test scenarios in `commonTest`.
- **Drivers**: Multiplatform drivers that handle the technical details of communicating with Android, iOS, Wasm, and Desktop applications.
- **Session Management**: Automatically handles video recording lifecycles and app relaunching.
