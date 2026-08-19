# Roadmap

This document outlines accomplished milestones, active capabilities, and upcoming feature developments for Parikshan.

---

## Accomplished Milestones

* **Multi-Target End-to-End Execution:** End-to-End UI test automation implemented and verified across all target platforms: Desktop (JVM), Web (WasmJs), Android, and iOS Simulator.
* **Window Sizing & Layout Control (0.0.7):** Dynamic runtime resizing and positioning of Desktop JVM and Web (WasmJs) application windows (`--layout=side-by-side`, `--window-size=<w>x<h>`, `--app-mode`).
* **Synchronized Multi-Target Mode (0.0.7):** Concurrent execution mode (`--sync`) where test steps synchronize across targets via lockstep barrier coordination.
* **Watch Mode & Keep-Alive Engine (0.0.7):** Continuous test runner watching source file modifications (`--watch`) and persistent test sessions (`--keep-alive`).
* **Configuration Cache & Mobile-Only KMP Support (0.0.7):** Full Gradle Configuration Cache compatibility and support for mobile-only projects lacking explicit Desktop/JVM targets.

---

## Active & Upcoming Features

The following features are undergoing design and active implementation for upcoming releases:

1. **Cross-Display Wasm Stability**: Standardized Wasm viewport rendering across varying host display scaling, resolutions, and headless CI runners.
2. **IDE Gutter Integration**: IntelliJ IDEA / Android Studio plugin enabling direct test execution from editor gutter icons.
3. **Multi-Instance Testing**: Capabilities to spin up and control multiple window instances of the same application (e.g., testing chat apps with two desktop client windows).
4. **Multi-Target Testing**: Orchestrated test runs targeting multiple platform instances concurrently (e.g., executing and verifying a flow spanning a Desktop app and a Web WasmJs client).

---

## Planned

The following features are planned for future development:

1. **Cross-Display Wasm Stability**: Standardized Wasm viewport rendering across varying host display scaling, resolutions, and headless CI runners.
2. **IDE Gutter Integration**: IntelliJ IDEA / Android Studio plugin enabling direct test execution from editor gutter icons.
3. **Multi-Instance Testing**: Capabilities to spin up and control multiple window instances of the same application (e.g., testing chat apps with two desktop client windows).
4. **Multi-Target Testing**: Orchestrated test runs targeting multiple platform instances concurrently (e.g., executing and verifying a flow spanning a Desktop app and a Web WasmJs client).
