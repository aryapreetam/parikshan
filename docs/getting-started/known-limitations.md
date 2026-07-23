# Known Limitations

This document lists known target-specific limitations and recommended workarounds when writing end-to-end tests with Parikshan.

---

## iOS Focus Contamination

### Symptom
Text input actions (`input(...)`) can hang or fail during full test suite execution, specifically on text fields without a `Modifier.testTag` that rely on coordinate-based fallbacks.

### Root Cause
Compose Multiplatform for iOS coordinates its custom keyboard and input logic via an internal `IntermediateTextInputUIView`. When executing sequential tests across multiple suites, focus state transitions can leak the first responder status, causing the main parent view (`OverlayInputView` or `ComposeView`) to retain focus and drop key events.

### Workaround
Call `relaunchApp()` to reset the native window hierarchy and focus state before executing complex overlay text input scenarios on iOS.

You can reference our implementation of this workaround in [OverlayIntegrationTest.kt](file:///Users/preetam/workspace/parikshan/samples/multiplatform-showcase/composeApp/src/commonTest/kotlin/sample/app/OverlayIntegrationTest.kt#L79-L87):

```kotlin
if (isIos()) {
    relaunchApp()
    navigateToSection("nav_overlay_playground")
    assertVisible("overlay_playground_screen")
}
```

---

## WASM Viewport Height Sensitivity

### Symptom
When testing the DatePicker component in input mode, tests fail if the WASM viewport height is configured above `600` or `650` pixels.

### Cause
The DatePicker component's adaptive layout shifts its child elements depending on the viewport size. At heights above 600px, the input field coordinates shift or scale, causing them to fall outside the interactive viewport bounds resolved by the browser driver.

### Workaround
Ensure the WASM viewport is configured to a height of exactly `600` pixels.

You can reference the default WASM configuration limits in [ParikshanWasmConfig.kt](file:///Users/preetam/workspace/parikshan/parikshan-client/src/jvmMain/kotlin/io/github/aryapreetam/parikshan/client/ParikshanWasmConfig.kt#L13-L15):

```kotlin
// In ParikshanWasmConfig.kt
private const val DEFAULT_VIEWPORT_WIDTH = 800
private const val DEFAULT_VIEWPORT_HEIGHT = 600 // Height must remain <= 600 to prevent DatePicker resolution failures
```

---

## Testing Sliders

### Limitation
Programmatic value injection (e.g. directly setting a value) is not supported for standard Compose `Slider` components.

### Cause
Compose Multiplatform's `Slider` does not expose semantic properties (like `ProgressBarRangeInfo`) in a way that allows direct value modification across all platform targets.

### Recommended Workarounds

You can reference our slider integration test implementation in [FormIntegrationTest.kt](file:///Users/preetam/workspace/parikshan/samples/multiplatform-showcase/composeApp/src/commonTest/kotlin/sample/app/FormIntegrationTest.kt#L79-L96).

#### Method A: Drag Gestures with Range Assertions (Recommended & Stable)
Locate the slider's bounding box, execute a coordinate-based horizontal drag gesture along the track, and verify the resulting value using a tolerance range (e.g. ±5%) to absorb layout, padding, and gesture velocity shifts.

```kotlin
// Drag slider to 80% of its track width
dragSlider("form_slider", 0.8f)

// Verify the value falls within a stable range (75% to 85%) to absorb gesture jitter
waitFor(Selector.Text("Range Selector:"))
val labelNode = resolveVisibleNode(Selector.Text("Range Selector:"))
val percentage = Regex("Range Selector: (\\d+)%").find(labelNode.text ?: "")
    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
assert(percentage in 75..85)
```

#### Method B: Calibrated Coordinate Calculation (Exact but Risky)
Calculate exact drag coordinates by offsetting the bounding box width by the slider's internal track padding and thumb radius, then perform a precise drag gesture. While this can target exact values, it is highly sensitive to target-specific screen densities and layout dimension variances, making it prone to breakage across different targets.
