# Module gradle-plugins

The Gradle plugin automates environment preparation, app installation, and test execution.

## Key Components

- **Task Orchestration**: Registers tasks like `e2eAndroidTest` and `e2eIosTest` that manage the E2E execution lifecycle.
- **Boot Source Generation**: Injects the Parikshan bridge initialization into your application during debug builds.
- **CI Support**: Configures headless execution environments (e.g., XVFB) and manages video artifact retention.
