# Module parikshan-core

The core module contains the shared logic that powers the entire Parikshan ecosystem. It is a pure Kotlin Multiplatform module with zero external platform dependencies.

## Key Components

- **Protocol**: Defines the JSON-based communication schema between the test runner and the application-under-test.
- **Selector Engine**: Implements the smart resolution logic that matches high-level intents (like "Login Button") to physical nodes using tags, text, and substrings.
- **Models**: Defines the `NodeSnapshot` and `Bounds` structures that represent the UI state.
