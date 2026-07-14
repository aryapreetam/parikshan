# Module parikshan-server

The server module provides the bridge that lives inside your application during a test run. It is responsible for exposing Compose semantics to the external test runner.

## Key Components

- **In-App Bridge**: An HTTP/WebSocket server that runs inside the debug build of your application.
- **Semantics Accessor**: Interacts with the Compose Multiplatform semantics tree to find nodes and execute actions.
- **Production Isolation**: The server components are excluded from release builds to ensure no impact on production users.
