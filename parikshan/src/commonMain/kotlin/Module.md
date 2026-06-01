# Parikshan

The **Parikshan** ecosystem provides a unified, cross-platform end-to-end (E2E) testing framework for **Compose Multiplatform**.

It is designed with a **Zero-Pollution** philosophy, ensuring that your production application remains clean of testing infrastructure while providing a powerful, intent-based DSL for UI automation.

## Architecture

Parikshan is divided into several specialized modules:

| Module | Description |
| :--- | :--- |
| <a href="parikshan-core/index.html"><strong>parikshan-core</strong></a> | The shared communication protocol and smart selector resolution engine. |
| <a href="parikshan-client/index.html"><strong>parikshan-client</strong></a> | The developer-facing DSL and cross-platform drivers (Android, iOS, Wasm, Desktop). |
| <a href="parikshan-server/index.html"><strong>parikshan-server</strong></a> | The in-app orchestration server that bridges Compose semantics to the test runner. |


## Core Principles

- **Customer Obsessed**: The DSL is designed to be readable and straightforward, focusing on user intent (e.g., `click("Login")`) rather than implementation details.
- **AI Ready**: The APIs are semantically named and well-documented, allowing AI agents to understand and generate tests with minimal context.
- **Pillar Parity**: Parikshan provides a consistent experience across all 4 major platforms supported by Compose Multiplatform.
