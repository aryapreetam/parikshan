# Module parikshan-server

The server module provides the bridge that lives inside your application during a test run. It is responsible for exposing Compose semantics to the external test runner.

## Key Components

- **In-App Bridge**: A lightweight HTTP/WebSocket server that runs inside the debug build of your application.
- **Semantics Accessor**: Interacts with the Compose Multiplatform semantics tree to find nodes and execute physical actions.
- **Ghost Infrastructure**: The server is designed to be completely absent from production builds, ensuring zero impact on your users.
