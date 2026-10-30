# Continuous Watch Mode (`--watch`)

Re-running `./gradlew e2eTest` manually after every code edit introduces turnaround latency from Gradle task configuration and application cold boots. Continuous watch mode (`--watch`) keeps the test runner resident, monitors your project source directories, and re-executes tests automatically when files change.

---

## Running in Watch Mode

Pass the `--watch` flag to the `e2eTest` task:

```bash
./gradlew e2eTest --targets=jvm --tests="sample.app.FormIntegrationTest" --watch
```

---

## Interactive TDD Feedback Loop (Demo)

Watch mode monitors project source directories and re-executes tests automatically upon file save without restarting target applications:

```bash
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest \
  --targets=jvm,wasm \
  --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching" \
  --desktop-window-position=1070x0 \
  --app-mode \
  --layout=side-by-side \
  --window-size=360x720 \
  --watch
```

<video controls autoplay loop muted playsinline onloadedmetadata="this.playbackRate = 2.0;" style="width: 100%; border-radius: 6px; margin: 1rem 0;">
  <source src="../../assets/watch-mode.mp4" type="video/mp4" />
</video>

### How Watch Mode Works (TDD Cycle)
1. **Initial Run (Pass):** Parikshan boots the target applications, docks them according to layout flags (e.g. side-by-side in `--app-mode`), and executes the baseline test pass. Application windows remain active.
2. **Code Edit (Fail):** Changing source or test code (e.g. updating an assertion or UI text) triggers an automatic background compile upon saving. Tests re-execute immediately without relaunching applications, showing failure feedback in the terminal.
3. **Correction (Pass):** Restoring or fixing the code and saving triggers an instant re-run (~1–2 seconds), turning the suite green.

### Exiting Watch Mode
Terminate watch mode at any time using `Ctrl + C` in your terminal, or by manually closing any running application window (Desktop or browser window) — Parikshan detects window closure and terminates the daemon cleanly.

---

## Customizing Recompilation (`--compile-command`)

By default, watch mode invokes `./gradlew assemble` or the target-specific compilation task upon detecting file modifications. You can override the recompilation command:

```bash
./gradlew e2eTest \
  --targets=jvm \
  --watch \
  --compile-command="./gradlew :composeApp:jvmProcessResources :composeApp:jvmJar"
```

---

## Faster Turnaround with Keep-Alive (`--keep-alive`)

Combining `--watch` with `--keep-alive` leaves the running application process alive between test iterations, avoiding full JVM restarts or browser page reloads:

```bash
./gradlew e2eTest --targets=jvm --watch --keep-alive
```
