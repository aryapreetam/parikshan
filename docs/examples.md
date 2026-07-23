# Examples & Sample Applications

This page documents the sample applications included in the repository under `samples/`, detailing test coverage, target setup, execution commands, and recording demos.

---

## Sample Applications Matrix

| Sample Name | Project Type | Kotlin Version | Compose Multiplatform | Supported Targets |
| :--- | :--- | :--- | :--- | :--- |
| **`multiplatform-showcase`** | Multi-Screen Component Showcase | 2.3.21 | 1.10.1 | Desktop, Wasm, Android, iOS |
| **`cmp-latest`** | Full-Stack Storefront App | 2.4.0 | 1.11.0+ | Desktop, Wasm, Android, iOS |
| **`composables-sample`** | Third-Party Design System | 2.4.0 | 1.11.0+ | Desktop, Wasm, Android, iOS |
| **`standalone-android`** | Non-KMP Android App | 2.4.0 | N/A (Android Jetpack) | Android |

---

## 1. `multiplatform-showcase`

This sample demonstrates how to use Parikshan to test different UI components and their interactions from the Compose Material library.

### What This Sample Demonstrates
* **Material UI Component Testing**: Verifies component rendering and user interactions for buttons, text inputs, sliders, date & time pickers, dropdown menus, bottom sheets, radio buttons, checkboxes, drag & drop elements, vertical & horizontal scrolling views, pull-to-refresh indicators, navigation flows, accessibility semantics, and duplicate testTag selectors.

### Execution Commands

```bash
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest
```

### Video & Visual Demos

<table>
  <thead>
    <tr>
      <th width="38%">Desktop &amp; Web (WasmJs)</th>
      <th width="31%">Android</th>
      <th width="31%">iOS</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <img src="../assets/mps-jvm.webp" width="280" alt="Desktop Demo" />
        <br/><br/>
        <video autoplay loop muted playsinline width="280">
          <source src="../assets/mps-wasm.mp4" type="video/mp4" />
        </video>
      </td>
      <td align="center" valign="middle">
        <video autoplay loop muted playsinline width="200">
          <source src="../assets/mps-android.mp4" type="video/mp4" />
        </video>
      </td>
      <td align="center" valign="middle">
        <video autoplay loop muted playsinline width="200">
          <source src="../assets/mps-ios.mp4" type="video/mp4" />
        </video>
      </td>
    </tr>
  </tbody>
</table>

---

## 2. `cmp-latest` (Full-Stack Storefront App)

A real-world full-stack Compose Multiplatform application (**StorefrontApp**) communicating with an embedded Ktor backend server (`:server`).

### What This Sample Demonstrates
* **Server Mocking & API Setup**: Demonstrates how to mock Ktor HTTP endpoints (`GET /api/products`, `POST /api/login`, `POST /api/orders`) by registering a custom client with Ktor `MockEngine` in `@BeforeTest` and resetting the service registry in `@AfterTest`.

### Execution Commands

```bash
./gradlew -p samples/cmp-latest :app:shared:e2eTest
```

### Visual Demos

<table>
  <thead>
    <tr>
      <th width="38%">Desktop &amp; Web (WasmJs)</th>
      <th width="31%">Android</th>
      <th width="31%">iOS</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <img src="../assets/storefront-jvm.webp" width="280" alt="Storefront Desktop" />
        <br/><br/>
        <img src="../assets/storefront-wasm.webp" width="280" alt="Storefront Wasm" />
      </td>
      <td align="center" valign="middle">
        <img src="../assets/storefront-android.webp" width="200" alt="Storefront Android" />
      </td>
      <td align="center" valign="middle">
        <img src="../assets/storefront-ios.webp" width="200" alt="Storefront iOS" />
      </td>
    </tr>
  </tbody>
</table>

---

## 3. `composables-sample`

Generated using the [Composables CLI](https://github.com/composablehorizons/composables-ui). Demonstrates automated UI testing against custom design systems and third-party component libraries.

### What This Sample Demonstrates
* Custom design system UI components.
* Target-independent element selection and input simulation.

### Execution Commands

```bash
./gradlew :samples:composables-sample:shared:e2eTest
```

### Visual Demos

<table>
  <thead>
    <tr>
      <th width="38%">Desktop &amp; Web (WasmJs)</th>
      <th width="31%">Android</th>
      <th width="31%">iOS</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <img src="../assets/composables-sample-jvm.gif" width="280" alt="Composables Desktop" />
        <br/><br/>
        <img src="../assets/composables-sample-wasm.gif" width="280" alt="Composables Wasm" />
      </td>
      <td align="center" valign="middle">
        <img src="../assets/composables-sample-android.gif" width="200" alt="Composables Android" />
      </td>
      <td align="center" valign="middle">
        <img src="../assets/composables-sample-ios.gif" width="200" alt="Composables iOS" />
      </td>
    </tr>
  </tbody>
</table>

---

## 4. `standalone-android`

Demonstrates integrating Parikshan into a standalone, single-module Android project without Kotlin Multiplatform setup.

### Execution Commands

```bash
./gradlew :samples:standalone-android:app:e2eAndroidTest
```

### Visual Demo

<p align="center">
  <img src="../assets/standalone-android.gif" width="200" alt="Standalone Android Demo" />
</p>
