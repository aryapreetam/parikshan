# Module parikshan-core

The core module contains the shared logic that powers the Parikshan ecosystem. It is a Kotlin Multiplatform module with no external platform dependencies.

## Key Components

- **Protocol**: Defines the JSON-based communication schema between the test runner and the application.
- **Selector Engine**: Resolves high-level intent queries (like "Login Button") to specific UI nodes using tags, text, or substrings.
- **Models**: Defines the `NodeSnapshot` and `Bounds` structures that represent the UI state.
