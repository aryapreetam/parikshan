# CI Workflow Optimization Guide

## 📊 Path-Based Filtering Strategy

### Overview
To save compute resources and time, CI workflows use path-based filtering to only run when relevant files change.

---

## 🔄 Regular Pushes & PRs (push-ci.yml)

### Triggers CI When:
- `lib/**` - Library source code changes
- `sample/**` - Sample app changes
- `gradle/**` - Gradle wrapper or version catalog changes
- `gradle.properties` - Project properties
- `settings.gradle.kts` - Project settings
- `build.gradle.kts` - Root build configuration
- `.github/workflows/ci.yml` - CI workflow itself
- `.github/workflows/push-ci.yml` - Push CI workflow
- `maestro-e2e/**` - E2E test definitions

### Skips CI When:
- `README.MD` - Documentation changes
- `docs/**` - Documentation files
- `*.md` - Other markdown files
- `LICENSE` - License file
- `CONTRIBUTING.md` - Contribution guidelines
- Images, diagrams, etc.

### Result:
✅ Saves ~10-15 minutes of CI time per docs-only commit
✅ Reduces GitHub Actions usage
✅ Faster feedback for documentation updates

---

## 🏷️ Tagged Releases (release.yml)

### Always Runs Full CI - No Path Filtering

**Why?**
1. **Full Validation Required** - Releases need 100% confidence
2. **External Changes** - Dependencies might have updated
3. **Environment Changes** - GitHub runners/tools might have changed
4. **Build Artifacts** - Must verify all platform builds work
5. **User Trust** - Published versions should be fully tested

### Triggers:
- `push: tags: - 'v*'` (e.g., v1.0.0, v2.1.3)
- `workflow_dispatch` (manual trigger)

### No Path Filters:
```yaml
on:
  push:
    tags:
      - 'v*'
  # No 'paths' key - runs for ALL changes
```

### Result:
✅ Every release is fully tested
✅ Confidence in published artifacts
✅ No surprises for users

---

## 📋 Path Filter Maintenance

### When to Update Filters

Add paths when you introduce:
- New source directories
- New build configuration files
- New test directories
- New dependency management files

### Current Coverage:

**Source Code:**
- ✅ `lib/**` (library code)
- ✅ `sample/**` (sample app)

**Build System:**
- ✅ `gradle/**` (wrapper, libs.versions.toml)
- ✅ `gradle.properties`
- ✅ `settings.gradle.kts`
- ✅ `build.gradle.kts`

**Tests:**
- ✅ `maestro-e2e/**` (E2E tests)

**Workflows:**
- ✅ `.github/workflows/ci.yml`
- ✅ `.github/workflows/push-ci.yml`

### Not Covered (Intentionally):
- ❌ Documentation files
- ❌ README, LICENSE, CONTRIBUTING
- ❌ Images and assets not used in builds
- ❌ Setup scripts (setup-template.sh, etc.)

---

## 🎯 Best Practices

### For Contributors:
1. **Documentation-only changes** will skip CI (faster merge)
2. **Code changes** always trigger full CI
3. **Mixed changes** (code + docs) trigger CI

### For Maintainers:
1. **Review path filters** when adding new directories
2. **Test filters** by making docs-only changes
3. **Monitor CI usage** in GitHub Actions insights

### For Releases:
1. **Always tag** when ready to release
2. **CI runs automatically** on tag push
3. **Full test suite** runs regardless of changes
4. **Artifacts generated** for all platforms

---

## 📈 Expected Savings

### Typical Documentation Update:
- **Before:** ~15 minutes (full CI + tests)
- **After:** ~30 seconds (workflow skip)
- **Savings:** 14.5 minutes per docs commit

### Typical Code Change:
- **Before:** ~15 minutes
- **After:** ~15 minutes (same - runs full CI)
- **Savings:** None (and that's correct!)

### Release Tag:
- **Before:** ~20 minutes (CI + build all platforms)
- **After:** ~20 minutes (same - always full CI)
- **Savings:** None (and that's intentional!)

---

## ⚠️ Important Notes

1. **Release tags bypass filters** - This is intentional and critical
2. **Path filters are OR conditions** - Any match triggers workflow
3. **Workflow changes** are included - So CI updates are tested
4. **Template check** runs regardless of paths

---

## 🔍 Debugging

### Workflow Not Running?
Check if your changes match any path filter:
```bash
# Your changes
git diff --name-only HEAD~1

# Should match at least one pattern in 'paths'
```

### Workflow Running Unexpectedly?
Verify path filters include all relevant directories:
```yaml
paths:
  - 'your-new-directory/**'
```

### Release CI Not Running?
Ensure tag format is correct:
```bash
git tag v1.0.0  # ✅ Correct
git tag 1.0.0   # ❌ Wrong (no 'v' prefix)
```

---

## 🚀 Future Improvements

Potential optimizations to consider:

1. **Conditional Test Matrix**
   - Run iOS tests only when iOS code changes
   - Run wasm tests only when wasm code changes

2. **Smart Caching**
   - Cache dependencies per path hash
   - Reuse build artifacts across jobs

3. **Parallel Documentation**
   - Deploy docs without waiting for full CI
   - Separate docs workflow for faster updates

4. **Branch Protection**
   - Require CI only for source changes
   - Allow docs PRs to merge without CI

