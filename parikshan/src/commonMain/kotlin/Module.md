# Parikshan

The **Parikshan** ecosystem provides a unified, cross-platform end-to-end (E2E) testing framework for **Compose Multiplatform**.

## Architecture

Parikshan is divided into several specialized modules:

| Module | Description |
| :--- | :--- |
| <a href="parikshan-core/index.html"><strong>parikshan-core</strong></a> | The shared communication protocol and selector resolution engine. |
| <a href="parikshan-client/index.html"><strong>parikshan-client</strong></a> | The developer-facing DSL and cross-platform drivers (Android, iOS, Wasm, Desktop). |
| <a href="parikshan-server/index.html"><strong>parikshan-server</strong></a> | The in-app orchestration server that bridges Compose semantics to the test runner. |


## Design

- Tests read like user intent — `click("Login")`, `assertText("Welcome")` — not platform internals.
- Same API, same behavior on Android, iOS, Desktop, and Wasm.
- APIs are named consistently and documented well enough for both humans and code-generation tools to work with.
- No testing infrastructure in production builds.
