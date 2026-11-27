# Roadmap

This document outlines accomplished milestones, active capabilities, and upcoming feature developments for Parikshan.

---

## Accomplished Milestones

* **Multi-Instance Testing for Desktop & Web (0.1.0):** Multiple application windows or browser contexts on Desktop (JVM) and Web (Wasm) with ephemeral ports and Just-In-Time window lifecycle.
* **Multi-Target Testing for Desktop & Web (0.1.0):** Cross-platform actor coordination (`instances = mapOf(...)`) between Desktop (JVM) and Web (Wasm) with build-time source scanning, method filtering (`--select-method`), and runtime target affinity guards.
* **File Picker Automation (0.0.11):** Non-blocking native OS file dialog and browser file chooser interception (`withFilePicker`, `withFilePickerCancel`) across Desktop, Web, Android, and iOS.
* **CMP 1.11–1.12 & Android 17 (SDK 36) Support (0.0.10):** Target SDK 36 qualification, Compose Multiplatform 1.11–1.12 compatibility, and declarative soft keyboard dismissal (`hideKeyboard()`).
* **Standalone Android Support & Adaptive Latches (0.0.9):** Zero-configuration testing for standalone Android apps (`com.android.application` without Desktop targets) and non-blocking video recording latches.
* **Window Sizing & Layout Control (0.0.8):** Dynamic runtime resizing and positioning of Desktop JVM and Web (Wasm) application windows (`--layout=side-by-side`, `--window-size=<w>x<h>`, `--app-mode`).
* **Synchronized Multi-Target Mode (0.0.8):** Concurrent execution mode (`--sync`) where test steps broadcast across targets in lockstep barrier coordination.
* **Continuous Watch Mode & Keep-Alive Engine (0.0.8):** Continuous test runner watching source file modifications (`--watch`) and persistent test sessions (`--keep-alive`).
* **Unified Intent DSL & Cross-Platform Drivers (0.0.1 – 0.0.7):** Core intent DSL (`click`, `input`, `assertVisible`, `scrollUntilVisible`) across Desktop (JVM), Web (Wasm), Android, and iOS.

---

## Active & Upcoming Features

The following features are undergoing design and active implementation for upcoming releases:

1. **Mobile Multi-Instance & Multi-Target Testing (Android & iOS):** Extending multi-instance and multi-target orchestration to physical devices and emulators/simulators (`Target.Android`, `Target.Ios`) coordinated with Desktop and Web.
2. **Deterministic Concurrent Action Orchestration (`inParallel`):** Multi-actor race condition and simultaneous interaction testing with two-phase prepared barrier dispatching (ARM/FIRE) and mutual exclusion assertion matchers (`assertMutualExclusion`).
3. **Cross-Display Wasm Stability**: Standardized Wasm viewport rendering across varying host display scaling, resolutions, and headless CI runners.
4. **IDE Gutter Integration**: IntelliJ IDEA / Android Studio plugin enabling direct test execution from editor gutter icons.

---

## Planned

The following features and learning tracks are scheduled for upcoming development:

1. **Compose Material 3 Plugin (`parikshan-material3`):** High-level DSL extensions for automating complex Material 3 components (Modal Bottom Sheet dragging, DatePicker calendar/input modes, TimePicker clock dials, and continuous slider gestures).
2. **Unified Parikshan Masterclass (Interactive Course):** A complete, module-based curriculum taking developers from zero to production E2E testing in Compose Multiplatform, covering all features, multi-target orchestration, and CI automation.
3. **Network Throttling & Offline Simulation**: DSL primitives to simulate offline mode, flaky network connections, and latency spikes across test actors.
4. **Visual Regression & Snapshot Diffing**: Automated pixel-level baseline comparison with diff visualization in HTML reports.
