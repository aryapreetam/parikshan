# Roadmap

This document outlines accomplished milestones, active capabilities, and upcoming feature developments for Parikshan.

---

## Accomplished Milestones

* **Multi-Target End-to-End Execution:** End-to-End UI test automation implemented and verified across all target platforms: Desktop (JVM), Web (WasmJs), Android, and iOS Simulator.

---

## Active & Upcoming Features

The following features are undergoing stabilization and will be included in the next release:

1. **Window Sizing & Layout Control**: Dynamic runtime resizing and positioning of Desktop JVM and Web (WasmJs) application windows during test execution.
2. **Synchronized Multi-Target Mode**: Concurrent execution mode where test steps synchronize across targets, ensuring all target instances complete a given action before advancing to the next command.
3. **Watch Mode Engine**: Continuous test runner watching source file modifications and automatically re-executing target test suites without manual Gradle invocation.

---

## Planned

The following features are planned for future development:

1. **Cross-Display Wasm Stability**: Standardized Wasm viewport rendering across varying host display scaling, resolutions, and headless CI runners.
2. **IDE Gutter Integration**: IntelliJ IDEA / Android Studio plugin enabling direct test execution from editor gutter icons.
3. **Multi-Instance Testing**: Capabilities to spin up and control multiple window instances of the same application (e.g., testing chat apps with two desktop client windows).
4. **Multi-Target Testing**: Orchestrated test runs targeting multiple platform instances concurrently (e.g., executing and verifying a flow spanning a Desktop app and a Web WasmJs client).
