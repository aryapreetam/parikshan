# 🛠️ Using This Template for Your Own Library

This guide walks you through adapting this template for your own Compose Multiplatform library.

---

## 📋 Quick Checklist

- [ ] Use this template to create your repository
- [ ] Clone your new repository
- [ ] **Run `./setup-template.sh`** (automated setup - recommended!)
- [ ] Delete the example `fiblib` code when ready
- [ ] Setup GitHub secrets for publishing
- [ ] Write your library code
- [ ] Update documentation
- [ ] Test publishing flow
- [ ] Create your first release

---

## Step 1: Create Your Repository from Template

1. Click "Use this template" button on GitHub
2. Create a new repository with your library name (e.g., `cmp-mediaviewer`)
3. Clone your new repository:
   ```bash
   git clone https://github.com/yourname/cmp-mediaviewer.git
   cd cmp-mediaviewer
   ```

---

## Step 2: Run Automated Setup (Recommended)

We provide an interactive setup script that configures everything for you!

### **🚀 Quick Setup:**

```bash
./setup-template.sh
```

The script will prompt you for:
- **Library name** (e.g., `Media Viewer Library`) - Human-readable name for Maven Central
- **Maven artifact name** (e.g., `cmp-mediaviewer`) - Technical dependency name
- **GitHub username/organization** - Auto-detected from git remote
- **Maven group ID** (e.g., `io.github.yourname`)
- **Developer name** (for POM metadata)
- **Library description** (for POM metadata)
- **Initial version** (default: `0.0.1`)

> **Note:** The repository name is automatically detected from your git remote, so you don't need to enter it!

**What it does automatically:**
- ✅ Updates `settings.gradle.kts` with your project name
- ✅ Updates `lib/build.gradle.kts` with your Maven coordinates, library name, version, and URLs
- ✅ Updates `CONTRIBUTING.md` and `README.MD` with your project info
- ✅ **Creates your package structure:** `lib/src/commonMain/kotlin/io/github/yourname/yourlibname/`
- ✅ Keeps the `fiblib` example code for reference (delete when ready)
- ✅ Saves configuration to `.template-config.json` (for re-runs)

**Example run:**
```bash
$ ./setup-template.sh

╔═══════════════════════════════════════════════════════════╗
║   Compose Multiplatform Library Template Setup            ║
╚═══════════════════════════════════════════════════════════╝

Repository detected: cmp-mediaviewer

Library name [e.g., Media Viewer Library]: Media Viewer
Maven artifact name [cmp-mediaviewer]: 
GitHub username/organization [johnsmith]: 
Maven group ID [io.github.johnsmith]: 
Developer name (for POM): John Smith
Library description (for POM): A modern media viewer for Compose Multiplatform
Initial version [0.0.1]: 

Configuration Summary:
═══════════════════════════════════════════════════════════
Library name:         Media Viewer
Artifact name:        cmp-mediaviewer
Version:              0.0.1
GitHub org:           johnsmith
Maven group ID:       io.github.johnsmith
Developer name:       John Smith
Library description:  A modern media viewer for Compose Multiplatform
Package name:         io.github.johnsmith.cmpmediaviewer
Package path:         io/github/johnsmith/cmpmediaviewer
═══════════════════════════════════════════════════════════

✅ Setup Complete!
```

### **Windows Users:**

For Windows, you have a few options:
- **Git Bash:** Run `./setup-template.sh` (recommended)
- **WSL (Windows Subsystem for Linux):** Run `./setup-template.sh`
- **Basic batch script:** Run `setup-template.bat` (limited functionality)

### **Re-running the Setup:**

Made a mistake? No problem! The script can be run multiple times:
```bash
./setup-template.sh
```

It will detect the existing configuration and ask if you want to reconfigure.

---

## Step 3: Start Building Your Library

After running the setup script:

1. **Your new package structure is ready:**
   ```
   lib/src/commonMain/kotlin/io/github/yourname/yourlibname/
   ```

2. **Add your library code** in the new package directory

3. **Keep or delete the `fiblib` example:**
   - The `fiblib` package is kept as a working reference
   - Delete it when you're ready:
     ```bash
     rm -rf lib/src/commonMain/kotlin/fiblib
     rm -rf lib/src/commonTest/kotlin/fiblib
     ```

4. **Update the sample app** to use your library:
   - Edit `sample/composeApp/src/commonMain/kotlin/sample/app/App.kt`
   - Replace `import fiblib.*` with your package imports

5. **Commit your changes:**
   ```bash
   git add .
   git commit -m "Configure template for cmp-mediaviewer"
   git push
   ```

---

## Step 4 (Alternative): Manual Setup

If you prefer to configure manually without the script:

### 4.1 Update Package Names

**In `settings.gradle.kts`:**
```kotlin
rootProject.name = "your-repo-name"
```

**In `lib/build.gradle.kts`:**
```kotlin
android {
  namespace = "io.github.yourname.yourlibname"
  // ...
}

mavenPublishing {
  coordinates("io.github.yourname", "yourlibname", "0.0.1")
  
  pom {
    name = "Your Library Name"
    description = "Your library description"
    url = "https://yourname.github.io/your-repo-name"
    
    developers {
      developer {
        id = "yourname"
        name = "Your Name"
      }
    }
    
    scm {
      url = "https://github.com/yourname/your-repo-name"
    }
  }
}
```

### 4.2 Create Package Structure

Create your package directories:
```bash
mkdir -p lib/src/commonMain/kotlin/io/github/yourname/yourlibname
mkdir -p lib/src/commonTest/kotlin/io/github/yourname/yourlibname
```

### 4.3 Update Documentation Files

- Find: `cmp-lib-template` → Replace: `your-repo-name`
- Find: `aryapreetam` → Replace: `yourname`
- Find: `fiblib` → Replace: `yourlibname`
- Find: `io.github.aryapreetam` → Replace: `io.github.yourname`

Files to update:
- `README.MD`
- `CONTRIBUTING.md`
- `docs/using-this-template.md` (this file)

---

## Step 5: Setup GitHub Secrets

### 5.1 Create Sonatype Account

1. Go to https://central.sonatype.com/
2. Sign up for an account
3. Verify your namespace (e.g., `io.github.yourname`)
   - For GitHub: Use `io.github.yourname` and verify via a public repo

### 5.2 Generate GPG Key

```bash
# Generate a new GPG key
gpg --full-generate-key
# Choose: RSA, 4096 bits, no expiration
# Enter your name and email

# List your keys to find the key ID
gpg --list-secret-keys --keyid-format=long
# Output looks like: sec   rsa4096/ABCD1234EFGH5678 2024-01-01
# Key ID is: ABCD1234EFGH5678

# Export the private key (ASCII-armored)
gpg --export-secret-keys --armor ABCD1234EFGH5678 > private-key.asc

# Upload public key to keyserver (required by Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678
```

### 5.3 Add GitHub Secrets

Go to: **Your Repo → Settings → Secrets and variables → Actions → New repository secret**

Add these 5 secrets:

| Secret Name | Value | How to Get |
|------------|-------|------------|
| `MAVEN_CENTRAL_USERNAME` | Your Sonatype username | From step 5.1 |
| `MAVEN_CENTRAL_PASSWORD` | Your Sonatype password | From step 5.1 (or generate token) |
| `SIGNING_KEY_ID` | Last 8 chars of GPG key ID | e.g., `EFGH5678` |
| `SIGNING_PASSWORD` | Your GPG key passphrase | What you entered when creating key |
| `GPG_KEY_CONTENTS` | Contents of `private-key.asc` | Copy entire file including BEGIN/END lines |

**Important:** For `GPG_KEY_CONTENTS`, copy the ENTIRE content including:
```
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQdGBF...base64-encoded-content...
-----END PGP PRIVATE KEY BLOCK-----
```

---

## Step 6: Update Documentation

### 6.1 Update README.MD

Replace template-specific content:

```markdown
# Your Library Name

Brief description of what your library does.

## Features

- Feature 1
- Feature 2
- - Feature 3

## Installation

[Keep the version catalog and dependency instructions, update coordinates]

## Usage

[Add your usage examples]

## Download Sample Apps

[Keep the download badges, they'll work automatically with releases]
```

**Find and replace in README.MD:**
- `Compose Multiplatform Library Template` → `Your Library Name`
- `cmp-lib-template` → `your-repo-name`
- `aryapreetam` → `yourname`
- `fiblib` → `yourlibname`
- All usage examples with your actual library code

### 6.2 Update LICENSE

Replace the name and year in `LICENSE` file:
```
MIT License

Copyright (c) 2025 Your Name

[Rest of MIT license text]
```

### 6.3 Update CONTRIBUTING.md

Replace repository-specific URLs:
- `https://github.com/aryapreetam/cmp-lib-template` → Your repo URL

---

## Step 7: Write Your Library Code

### 7.1 Remove Template Code

1. Delete `lib/src/commonMain/kotlin/fiblib/Fibonacci.kt`
2. Delete `lib/src/commonTest/kotlin/fiblib/FibonacciTest.kt`

### 7.2 Add Your Code

Create your library files in `lib/src/commonMain/kotlin/yourpackage/`:

```kotlin
// lib/src/commonMain/kotlin/io/github/yourname/yourlibname/YourClass.kt
package io.github.yourname.yourlibname

/**
 * Your main library class or function
 */
fun yourAwesomeFunction(): String {
    return "Hello from your library!"
}
```

### 7.3 Add Tests

Create tests in `lib/src/commonTest/kotlin/yourpackage/`:

```kotlin
// lib/src/commonTest/kotlin/io/github/yourname/yourlibname/YourTest.kt
package io.github.yourname.yourlibname

import kotlin.test.Test
import kotlin.test.assertEquals

class YourTest {
  @Test
  fun testYourAwesomeFunction() {
    val result = yourAwesomeFunction()
    assertEquals("Hello from your library!", result)
  }
}
```

### 7.4 Update Sample App

Update `sample/composeApp/src/commonMain/kotlin/sample/app/App.kt` to demonstrate your library:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.yourname.yourlibname.yourAwesomeFunction

@Composable
fun App() {
  Text(yourAwesomeFunction())
}
```

---

## Step 8: Test Locally

### 8.1 Build & Test

```bash
# Clean build
./gradlew clean build

# Run all tests
./gradlew test

# Test on specific platforms
./gradlew :lib:jvmTest
./gradlew :lib:iosSimulatorArm64Test
./gradlew :lib:wasmJsBrowserTest
```

### 8.2 Test Sample Apps

```bash
# Android
./gradlew :sample:composeApp:assembleDebug

# Desktop
./gradlew :sample:composeApp:run

# Web/wasm
./gradlew :sample:composeApp:wasmJsBrowserDevelopmentRun --continuous
```

### 8.3 Test Publishing Locally

```bash
# Publish to Maven Local
./gradlew :lib:publishToMavenLocal

# Check it exists
ls ~/.m2/repository/io/github/yourname/yourlibname/
```

---

## Step 9: Test CI/CD Pipeline

### 9.1 Push Changes

```bash
git add .
git commit -m "Initial library setup"
git push origin main
```

This will trigger the `push-ci.yml` workflow which runs:
- Lint checks
- All platform tests (JVM, iOS, wasm, Android)
- Android UI tests

### 9.2 Monitor Workflow

Go to: **Your Repo → Actions tab**

Verify all jobs pass:
- ✅ lint
- ✅ lib-tests (jvm, ios, wasm)
- ✅ test-android-unit
- ✅ android-ui-tests

### 9.3 Fix Any Failures

Common issues:
- Package name mismatches
- Missing imports
- Test failures due to removed template code

---

## Step 10: Create Your First Release

### 10.1 Verify Secrets

Double-check all 5 GitHub secrets are set correctly:
- MAVEN_CENTRAL_USERNAME
- MAVEN_CENTRAL_PASSWORD
- SIGNING_KEY_ID
- SIGNING_PASSWORD
- GPG_KEY_CONTENTS

### 10.2 Create Release Tag

```bash
# Ensure version in lib/build.gradle.kts is correct
# coordinates("io.github.yourname", "yourlibname", "0.1.0")

git tag v0.1.0
git push origin v0.1.0
```

### 10.3 Monitor Release Workflow

Go to: **Actions → Publish Multiplatform Release**

The workflow will:
1. ✅ Run CI (lint + tests)
2. ✅ Build artifacts (APK, DMG x2, wasm, iOS)
3. ✅ Create GitHub Release
4. ✅ Publish to Maven Central
5. ✅ Deploy docs to GitHub Pages

### 10.4 Verify Publication

**GitHub Release:**
- Go to: **Your Repo → Releases**
- Verify artifacts are attached

**Maven Central:**
- Wait ~30 minutes for sync
- Check: https://central.sonatype.com/artifact/io.github.yourname/yourlibname

**GitHub Pages:**
- Go to: **Settings → Pages**
- Enable Pages if not already enabled
- Visit: `https://yourname.github.io/your-repo/`
- Check `/demo/` and `/api/` work

---

## Step 11: Update Version Badge (Optional)

Add a Maven Central version badge to your README:

```markdown
[![Maven Central](https://img.shields.io/maven-central/v/io.github.yourname/yourlibname.svg)](https://central.sonatype.com/artifact/io.github.yourname/yourlibname)
```

---

## 🎉 You're Done!

Your library is now:
- ✅ Published on Maven Central
- ✅ Documented with API docs
- ✅ Demo-able via wasm
- ✅ Downloadable as sample apps
- ✅ Fully automated CI/CD

---

## 🔄 For Future Releases

1. Make changes to library code
2. Update version in `lib/build.gradle.kts`
3. Commit and push
4. Create and push new tag: `git tag v0.2.0 && git push origin v0.2.0`
5. Watch the automation work!

---

## 📚 Additional Resources

- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform Docs](https://www.jetbrains.com/compose-multiplatform/)
- [Publishing to Maven Central](https://central.sonatype.org/publish/publish-guide/)
- [GitHub Actions Docs](https://docs.github.com/en/actions)

---

## ❓ Need Help?

- Check CONTRIBUTING.md for development guidelines
- Open an issue if you encounter problems
- Review existing issues for common problems
