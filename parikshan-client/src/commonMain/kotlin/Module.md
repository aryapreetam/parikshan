# Module parikshan-client

The client module contains the host-side platform-specific drivers and test runner orchestration logic that executes the E2E tests.

## Key Components

- **Drivers**: Platform-specific implementations (Android, iOS, Wasm, Desktop) that establish communication channels with the application under test and send protocol commands.
- **Session Management**: Manages video recording streams, screenshot captures, and application process keep-alive states during test execution.
