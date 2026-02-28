# Template Setup System

This document describes the automated template setup system for the Compose Multiplatform Library Template.

## Overview

When someone uses this template to create a new library, they need to customize:
- Library name (human-readable for Maven Central)
- Project name (repository name - auto-detected)
- Maven coordinates (group ID, artifact name, version)
- Package structure
- Developer information
- GitHub URLs

Instead of manual find-replace, we provide an **automated setup script** that handles everything.

## Components

### 1. Setup Scripts

#### `setup-template.sh` (Primary - Unix/Linux/macOS)
- Interactive shell script
- Auto-detects repository name from git remote
- Prompts for all configuration values with smart defaults
- Automatically updates all files
- Creates package directory structure
- Can be run multiple times safely
- Saves configuration to `.template-config.json`

#### `setup-template.bat` (Windows fallback)
- Basic batch script for Windows users
- Same functionality as bash version
- Recommends using Git Bash or WSL for better experience

### 2. Configuration File

#### `.template-config.json`
- Stores user's configuration
- Allows script to detect if already configured
- Shows current values when re-running
- **Gitignored** - each user has their own config

Example:
```json
{
  "configured": true,
  "repo_name": "cmp-mediaviewer",
  "library_name": "Media Viewer Library",
  "artifact_name": "cmp-mediaviewer",
  "github_org": "johnsmith",
  "group_id": "io.github.johnsmith",
  "developer_name": "John Smith",
  "library_description": "A modern media viewer",
  "package_name": "io.github.johnsmith.cmpmediaviewer",
  "package_path": "io/github/johnsmith/cmpmediaviewer",
  "version": "0.0.1"
}
```

### 3. Gradle Validation

#### `gradle/check-template-setup.gradle.kts`
- Gradle task that checks if template is configured
- Looks for "cmp-lib-template" in `settings.gradle.kts`
- **Automatically runs** before build tasks
- Shows prominent error if not configured

Applied in `build.gradle.kts`:
```kotlin
apply(from = "gradle/check-template-setup.gradle.kts")
```

### 4. README Warning

#### Conditional warning banner in `README.MD`
- Shows prominent warning at top of README
- Visible on GitHub repository page
- Instructs users to run `./setup-template.sh`
- Can be removed manually after setup or automatically by script

## User Flow

### First-Time Setup

1. User clicks "Use this template" on GitHub
2. Creates repository: `cmp-mediaviewer`
3. Clones the repository
4. Opens README → sees setup instructions
5. Runs `./setup-template.sh`
6. Script auto-detects repository name from git remote
7. Script prompts for values (with smart defaults):
   - Library name (e.g., "Media Viewer Library")
   - Artifact name (defaults to repo name)
   - GitHub org (auto-detected)
   - Maven group ID (auto-generated)
   - Developer name
   - Library description
   - Initial version
8. Script updates all files automatically
9. Script creates package structure
10. User commits changes

### What Gets Updated Automatically

The script updates:
- ✅ `settings.gradle.kts` - Project name
- ✅ `lib/build.gradle.kts` - Maven coordinates (group, artifact, version), library name (pom.name), URLs, POM metadata
- ✅ `CONTRIBUTING.md` - Repository URLs and examples
- ✅ `README.MD` - All references to template names

The script creates:
- ✅ `lib/src/commonMain/kotlin/{package-path}/` - New package directory
- ✅ `lib/src/commonTest/kotlin/{package-path}/` - Test package directory
- ✅ README.md in new package with setup instructions

### Key Improvements (January 2025)

1. **Auto-Detection of Repository Name**
   - No longer asks user to enter repository name
   - Detects from git remote URL automatically
   - Validates that git remote exists

2. **New Library Name Field**
   - Separate field for human-readable library name
   - Used in `mavenPublishing.pom.name`
   - Displays properly on Maven Central

3. **Fixed Package Name Generation**
   - Correctly combines groupId + sanitized artifact name
   - Removes hyphens and special characters
   - Example: `cmp-mediaviewer` → `io.github.johnsmith.cmpmediaviewer`

4. **Better Defaults**
   - Artifact name defaults to full repository name (not stripped)
   - All git-detectable values are auto-filled
   - User only needs to press Enter for most fields

## Configuration Details

### Input Fields

| Field | Example | Description | Default |
|-------|---------|-------------|---------|
| **Library name** | `Media Viewer Library` | Human-readable name for Maven Central (pom.name) | Required input |
| **Artifact name** | `cmp-mediaviewer` | Technical name for Maven dependencies (artifactId) | Repository name (detected) |
| **GitHub org** | `johnsmith` | GitHub username or organization | Detected from git remote |
| **Maven group ID** | `io.github.johnsmith` | Maven group identifier | `io.github.{org}` |
| **Developer name** | `John Smith` | Name for POM metadata | Required input |
| **Library description** | `A media viewer for Compose Multiplatform` | Brief description for Maven Central | Required input |
| **Initial version** | `0.0.1` | Starting version | `0.0.1` |

### Generated Values

| Value | Example | How Generated |
|-------|---------|---------------|
| **Repository name** | `cmp-mediaviewer` | Detected from git remote URL |
| **Package name** | `io.github.johnsmith.cmpmediaviewer` | `{groupId}.{sanitized(artifactName)}` |
| **Package path** | `io/github/johnsmith/cmpmediaviewer` | Package name with dots → slashes |

### Sanitization Rules

Artifact name → Package name:
- Remove hyphens: `cmp-mediaviewer` → `cmpmediaviewer`
- Remove underscores: `my_lib` → `mylib`
- Convert to lowercase: `MyLib` → `mylib`
- Remove special characters

## File Replacements

### Old Template Values

```kotlin
OLD_REPO = "cmp-lib-template"
OLD_ORG = "aryapreetam"
OLD_ARTIFACT = "fiblib"
OLD_GROUP = "io.github.aryapreetam"
OLD_NAMESPACE = "io.github.aryapreetam.fiblib"
OLD_DEVELOPER = "Preetam Bhosle"
OLD_DESCRIPTION = "Compose Multiplatform library for fibonacci numbers"
OLD_LIB_NAME = "Fibonacci Library"
OLD_VERSION = "0.0.3"
```

These get replaced with user's values across:
- `settings.gradle.kts`
- `lib/build.gradle.kts`
- `CONTRIBUTING.md`
- `README.MD`
- `LICENSE`
- `.github/workflows/release.yml`

## Error Handling

### Git Remote Not Found
```bash
Error: Could not detect repository name from git remote!
Please make sure you've created this repository from the template 
and have a git remote configured.
```

**Solution:** Ensure you've cloned from GitHub and have origin remote set.

### Empty Required Fields
Script validates that required fields are not empty:
- Library name
- Developer name
- Library description

**Solution:** Re-run and provide values (cannot skip these fields).

### "tr: empty string2" Warning
**Fixed:** Changed `tr '-' ''` to `tr -d '-'` in bash script.

## Re-configuration

Users can re-run the setup script anytime:

```bash
./setup-template.sh
```

The script will:
1. Detect existing `.template-config.json`
2. Show current configuration
3. Ask: "Do you want to reconfigure? (y/n)"
4. If yes, run setup again with new values
5. If no, exit without changes

## Verification

Users can verify setup completion:

```bash
./gradlew checkTemplateSetup
```

**Output if configured:**
```
✅ Template is properly configured!
```

**Output if not configured:**
```
❌ ERROR: Template not configured!
Please run: ./setup-template.sh
```

## Design Decisions

### Why Auto-Detect Repository Name?

**Reasoning:**
1. Repository is already created from template
2. User knows the repository name (they just created it)
3. Git remote URL contains this information
4. Reduces user input and potential typos
5. One less field to think about

### Why Separate Library Name and Artifact Name?

**Reasoning:**
1. Maven Central displays `pom.name` prominently
2. Artifact IDs are technical (e.g., `cmp-mediaviewer`)
3. Library names should be human-readable (e.g., `Media Viewer Library`)
4. Following Maven best practices
5. Better user experience on Maven Central

### Why Keep Example Code?

**Reasoning:**
1. Provides working reference implementation
2. Shows multiplatform best practices
3. Tests verify the template works
4. Users can compare their code structure
5. Easy to delete when ready

## Future Improvements

Potential enhancements:
- [ ] Support for custom package structure beyond groupId
- [ ] Interactive platform selection (disable unused platforms)
- [ ] Automated GitHub Pages setup (via API)
- [ ] Validation of Maven Central credentials before release
- [ ] Support for non-GitHub hosting (GitLab, Bitbucket)

## See Also

- [Using This Template](using-this-template.md) - Complete setup guide
- [GitHub Secrets Setup](github-secrets-setup.md) - Publishing configuration
- [Template Setup Improvements](template-setup-improvements.md) - Recent changes and improvements
