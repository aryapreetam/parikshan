---
date: 2026-08-16
description: Introducing runtime window sizing and positioning, synchronized multi-target execution, and continuous watch mode in Parikshan 0.0.7.
categories:
  - Releases
  - E2E Testing
---

# Supporting Window Layouts (Size, Position), Sync, and Watch Mode

Parikshan 0.0.7 adds runtime window layout and sizing controls, synchronized multi-target execution (`--sync`), continuous watch mode (`--watch`), and fixes several community-reported edge cases.

<!-- more -->

Here is a breakdown of why these features were built, how they work, and what is coming next.

---

## Window Sizing & Screen Positioning

### The Problem
When running tests during active development, Desktop JVM and Web (WasmJs) windows default to opening in the center of the screen, covering the IDE or terminal. Additionally, testing how an application renders on mobile viewports previously required booting up heavy Android emulators or iOS simulators rather than simply resizing Desktop or Web windows to mobile dimensions.

### The Solution
Parikshan 0.0.7 introduces direct CLI options to control window geometry and screen placement:

* **Uniform Sizing:** `--window-size=<width>x<height>` resizes both Desktop and Web viewports (for example, `--window-size=360x720` for mobile testing).
* **Target-Specific Overrides:** `--desktop-window-size`, `--desktop-window-position`, `--wasm-window-size`, and `--wasm-window-position` give granular control over individual targets.
* **Side-by-Side Mode:** `--layout=side-by-side` automatically tiles Desktop and Web windows neatly next to each other and enables standalone app mode (hiding browser navigation bars for Wasm).

```bash
# Dock Desktop and Wasm side-by-side in mobile dimensions
./gradlew e2eTest \
  --targets=desktop,wasm \
  --window-size=360x720 \
  --layout=side-by-side \
  --tests="sample.app.LoginTest"
```

*(Note: Window sizing and screen positioning options apply to Desktop JVM and Web WasmJs targets).*

---

## Synchronized Multi-Target Execution (`--sync`)

### The Problem
When testing across Android, iOS, Desktop, and Wasm concurrently, targets execute at different speeds. Desktop JVM tests often finish in 2 to 3 seconds, while mobile simulators take longer to process UI animations. If you want to visually observe how a UI flow, gesture, or animation behaves across all four platforms at the exact same moment, standard asynchronous execution creates friction.

### The Solution
Passing `--sync` enables a step-barrier lockstep engine. Every test command (`click`, `input`, `drag`) is dispatched to all active targets in parallel, and the test runner waits for all target instances to complete the current step before advancing to the next:

```bash
./gradlew e2eTest \
  --targets=desktop,wasm,android,ios \
  --sync \
  --tests="sample.app.FormIntegrationTest"
```

This guarantees visual parity during development and makes cross-platform UI differences immediately obvious.

---

## Continuous Watch Mode (`--watch`) & Keep-Alive (`--keep-alive`)

### The Problem
Re-running Gradle test tasks manually after every 1-line UI tweak or assertion edit introduces build overhead, compilation latency, and constant context switching.

### The Solution
* **Watch Mode (`--watch`):** Starts a background file watcher that monitors project source directories and re-executes tests automatically upon file save. If a code change introduces a compiler error, watch mode displays the error in the console and waits for the next edit without terminating.
* **Keep-Alive Engine (`--keep-alive`):** Preserves active application instances and server connections across test runs. On subsequent runs, Parikshan reuses the running app instance, dropping re-run execution time to ~1–2 seconds.

```bash
# Continuous TDD on Desktop with instant re-runs
./gradlew e2eTest --targets=desktop --watch --tests="sample.app.LoginTest"
```

---

## Community Bug Fixes

We want to thank the community members who reported edge cases and helped make Parikshan more robust:

* **Issue #10 (iOS Focus & Input Safety):** Fixed iOS text input focus crashes on un-tagged input fields by adding parent label text fallback and KVC input safety.
* **Issues #12 & #13 (Mobile-Only KMP Support):** Resolved plugin configuration failures on mobile-only KMP projects lacking explicit Desktop/JVM targets, and added safe host test task resolution for `com.android.kotlin.multiplatform.library` modules.
* **Issue #15 (Configuration Cache & Lazy Tasks):** Refactored test tasks to use Gradle `TaskProvider` APIs, deferring Android application ID and iOS bundle ID extraction to ensure 100% Gradle Configuration Cache compatibility.
* **Issue #16 (Host Unit Test Safety):** Prevented false-positive test passes by ignoring `e2eTest` blocks when standard non-E2E host unit test tasks (such as `testAndroidHostTest`) are executed.

---

## What's Next

The next milestones on our roadmap include:

1. **Multi-Instance Testing:** Launching and controlling multiple window instances of the same application (e.g., testing multi-user messaging and collaborative workflows).
2. **Multi-Target Interaction:** Cross-platform interactive tests where different targets (e.g., Desktop client and Web/Mobile client) communicate and interact within a single test block.
3. **Wasm Stability & Display Scaling:** Standardizing Wasm canvas viewport rendering across all host display scaling, resolutions, and headless CI runners.
4. **IDE Gutter Integration:** An IntelliJ IDEA and Android Studio plugin enabling direct test execution from editor gutter icons.

---

## Getting Started

To get started with Parikshan in your project, follow the [Your First Test](../../guides/your-first-test.md) guide or visit the [Quickstart Guide](../../getting-started/quickstart.md). If you encounter issues or have suggestions, please open an issue on [GitHub](https://github.com/aryapreetam/parikshan).
