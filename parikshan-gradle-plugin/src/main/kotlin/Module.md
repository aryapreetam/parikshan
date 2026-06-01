# Module parikshan-gradle-plugin

The Gradle plugin is the automation engine of Parikshan. It handles the complex task of preparing your environment, installing apps, and running tests across all 4 pillars.

## Key Components

- **Task Orchestration**: Registers tasks like `e2eAndroidTest` and `e2eIosTest` that manage the full lifecycle of an E2E run.
- **Boot Source Generation**: Automatically injects the Parikshan bridge into your application at build-time.
- **CI Stabilization**: Provides built-in support for headless environments, XVFB, and automated video retention.
