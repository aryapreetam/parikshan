# Contributing to Compose Multiplatform Library Template

Thank you for your interest in contributing! This document provides guidelines for developers working on this template.

---

## 🏗️ Project Structure

```
cmp-lib-template/
├── .github/workflows/      # CI/CD pipelines
│   ├── ci.yml             # Reusable CI (lint, tests, build)
│   ├── push-ci.yml        # Runs on every push/PR
│   └── release.yml        # Runs on version tags (v*)
├── lib/                   # The actual library code
│   ├── src/
│   │   ├── commonMain/    # Shared Kotlin code
│   │   └── commonTest/    # Shared tests
│   └── build.gradle.kts   # Library build config + publishing
├── sample/                # Sample app demonstrating library usage
│   ├── composeApp/        # Multiplatform sample app
│   └── iosApp/            # iOS wrapper for Compose app
├── docs/                  # Documentation
├── readme_images/         # Images used in README
└── README.MD              # Main documentation (becomes homepage)
```

---

## 🚀 Getting Started

### Prerequisites

- JDK 17 or later
- Android Studio Ladybug or later (for Android development)
- Xcode 15+ (for iOS development, macOS only)
- Node.js (for wasm development)

### Initial Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/aryapreetam/cmp-lib-template.git
   cd cmp-lib-template
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run tests**
   ```bash
   # Run all tests
   ./gradlew test
   
   # Platform-specific tests
   ./gradlew :lib:jvmTest
   ./gradlew :lib:iosSimulatorArm64Test
   ./gradlew :lib:wasmJsBrowserTest
   ./gradlew :lib:testDebugUnitTest  # Android unit tests
   ```

---

## 🔧 Development Workflow

### Working on the Library

1. Make changes in `lib/src/commonMain/kotlin/`
2. Write tests in `lib/src/commonTest/kotlin/`
3. Run tests: `./gradlew :lib:test`
4. Check code style: `./gradlew lintRelease`

### Testing Changes in Sample App

1. Make changes in `lib/`
2. The sample app automatically uses the local library via `implementation(project(":lib"))`
3. Run the sample app on your target platform:

   **Android:**
   ```bash
   ./gradlew :sample:composeApp:assembleDebug
   # Or open in Android Studio and run
   ```

   **Desktop:**
   ```bash
   ./gradlew :sample:composeApp:run
   ```

   **iOS:**
   ```bash
   # Open sample/iosApp/iosApp.xcodeproj in Xcode
   # Select a simulator and press Run
   ```

   **Web (wasm):**
   ```bash
   ./gradlew :sample:composeApp:wasmJsBrowserDevelopmentRun --continuous
   # Opens at http://localhost:8080
   ```

### Publishing to Maven Local (for testing)

Test your library locally before publishing to Maven Central:

```bash
./gradlew :lib:publishToMavenLocal
```

Then in another project, add:
```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.aryapreetam:fiblib:0.0.3")
}
```

---

## 📦 Publishing to Maven Central

### Setup (One-time)

1. **Create Sonatype Account**
   - Sign up at https://central.sonatype.com/
   - Create a namespace (e.g., `io.github.yourusername`)

2. **Generate GPG Key**
   ```bash
   # Generate key
   gpg --gen-key
   
   # List keys to find key ID
   gpg --list-secret-keys --keyid-format=long
   
   # Export for GitHub secrets (ASCII-armored)
   gpg --export-secret-keys --armor <KEY_ID> > private-key.asc
   
   # Upload public key to keyserver
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

3. **Configure GitHub Secrets**

   Go to: `Settings → Secrets and variables → Actions → New repository secret`

   Add these secrets:
   - `MAVEN_CENTRAL_USERNAME`: Your Sonatype username
   - `MAVEN_CENTRAL_PASSWORD`: Your Sonatype password (or token)
   - `SIGNING_KEY_ID`: Last 8 characters of your GPG key ID
   - `SIGNING_PASSWORD`: Passphrase for your GPG key
   - `GPG_KEY_CONTENTS`: Contents of `private-key.asc` (ASCII-armored key)

4. **Configure Local Publishing (optional)**

   Add to `~/.gradle/gradle.properties`:
   ```properties
   signing.keyId=<last 8 chars of key ID>
   signing.password=<your passphrase>
   signing.secretKeyRingFile=/Users/yourname/.gnupg/secring.gpg
   
   mavenCentralUsername=<your username>
   mavenCentralPassword=<your password>
   ```

### Release Process

1. **Update version in `lib/build.gradle.kts`**
   ```kotlin
   coordinates("io.github.aryapreetam", "fiblib", "0.0.4") // Bump this
   ```

2. **Commit and push**
   ```bash
   git add .
   git commit -m "Release v0.0.4"
   git push
   ```

3. **Create and push tag**
   ```bash
   git tag v0.0.4
   git push origin v0.0.4
   ```

4. **Monitor GitHub Actions**
   - Go to `Actions` tab
   - Watch the `Publish Multiplatform Release` workflow
   - It will:
     - Run all tests
     - Build artifacts (APK, DMG, wasm, iOS)
     - Create GitHub Release
     - Publish to Maven Central
     - Deploy docs to GitHub Pages

---

## 🧪 Running Tests

### Unit Tests

```bash
# All platforms
./gradlew test

# Specific platforms
./gradlew :lib:jvmTest
./gradlew :lib:iosSimulatorArm64Test
./gradlew :lib:wasmJsBrowserTest
./gradlew :lib:testDebugUnitTest  # Android
```

### UI Tests

```bash
# Android (requires emulator)
./gradlew :sample:composeApp:connectedAndroidTest
```

### Lint

```bash
./gradlew lintRelease
```

---

## 🎨 Adding New Targets

### Adding a New Platform (e.g., tvOS)

1. **Add target in `lib/build.gradle.kts`**
   ```kotlin
   kotlin {
     // ...existing targets...
     tvosArm64()
     tvosSimulatorArm64()
   }
   ```

2. **Add target in `sample/composeApp/build.gradle.kts`**
   ```kotlin
   kotlin {
     // ...existing targets...
     tvosArm64()
     tvosSimulatorArm64()
   }
   ```

3. **Update CI workflows**
   - Add tvOS testing in `.github/workflows/ci.yml`
   - Add tvOS artifact build in `.github/workflows/release.yml`

4. **Test locally**
   ```bash
   ./gradlew :lib:tvosSimulatorArm64Test
   ```

---

## 📚 Documentation

### Updating API Docs

API documentation is generated automatically via Dokka:

```bash
# Generate locally
./gradlew :lib:dokkaGeneratePublicationHtml

# View at: lib/build/dokka/html/index.html
```

On release, docs are automatically published to: `https://yourusername.github.io/repo-name/api/`

### Updating Homepage

Edit `README.MD` - it's automatically converted to the homepage via Docsify.

---

## 🐛 Troubleshooting

### Common Issues

**Issue: "Task :lib:signKotlinMultiplatformPublication not found"**
- Ensure GPG key is properly configured
- Check `signing.keyId` is set (local) or `signingInMemoryKey` (CI)

**Issue: "iOS simulator tests fail"**
- Make sure Xcode is installed
- Run: `xcodebuild -downloadAllPlatforms`
- Check available simulators: `xcrun simctl list devices`

**Issue: "wasm tests fail with CHROME_BIN not found"**
- Install Chrome: `brew install --cask google-chrome`
- Or set: `export CHROME_BIN=/path/to/chrome`

**Issue: "Maven Central publishing fails"**
- Verify namespace ownership in Sonatype
- Check all secrets are correctly set in GitHub
- Ensure version is unique (not already published)

---

## 📝 Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused
- Write tests for all public APIs

---

## 🤝 Pull Request Guidelines

1. **Fork the repository** and create a branch from `main`
2. **Make your changes** with clear commits
3. **Add tests** for new functionality
4. **Update documentation** if needed
5. **Run all tests** locally before submitting
6. **Submit PR** with clear description

### PR Checklist

- [ ] Tests pass locally (`./gradlew test`)
- [ ] Code style checks pass (`./gradlew lintRelease`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages are clear
- [ ] No merge conflicts with `main`

---

## 📞 Getting Help

- Open an issue for bugs or questions
- Check existing issues before creating new ones
- Provide minimal reproduction steps for bugs

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

