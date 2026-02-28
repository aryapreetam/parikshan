# 🚀 Quick Start - Template Setup

## One Command to Rule Them All

```bash
./setup-template.sh
```

That's it! The script will guide you through the rest.

---

## What You'll Be Asked

The script will prompt you for:

1. **Library name** (e.g., `Media Viewer Library`)
   - Human-readable name shown on Maven Central
   - Used in POM metadata (see image below)
   
   ![Maven POM Name Display](readme_images/maven-lib-pom-name.png)

2. **Artifact name** (e.g., `cmp-mediaviewer`)
   - The technical name used in Maven dependencies
   - Default: your repository name (detected automatically)

3. **GitHub username/org** (e.g., `johnsmith`)
   - Your GitHub username or organization
   - Auto-detected from git remote

4. **Maven group ID** (e.g., `io.github.johnsmith`)
   - Follows reverse domain convention
   - Default: `io.github.<your-username>`

5. **Developer name** (e.g., `John Smith`)
   - Your name for POM metadata

6. **Library description** (e.g., `A media viewer for Compose Multiplatform`)
   - Brief description for Maven Central

7. **Initial version** (e.g., `0.0.1`)
   - Starting version for your library
   - Default: `0.0.1`
   - Press Enter for default or enter custom (e.g., `1.0.0`)

> **Note:** Repository name is automatically detected from your git remote URL, so you don't need to enter it manually!

---

## What Happens Automatically

✅ Updates `settings.gradle.kts` with your project name  
✅ Updates `lib/build.gradle.kts` with Maven coordinates, library name, and version  
✅ Creates your package: `lib/src/commonMain/kotlin/io/github/yourname/yourlib/`  
✅ Updates all documentation with your project info  
✅ Keeps example `fiblib` code as reference  

---

## After Setup

1. **Add your code** in the new package directory
2. **Delete example `fiblib`** code when you're ready
3. **Commit changes:**
   ```bash
   git add .
   git commit -m "Configure template for my-awesome-lib"
   git push
   ```

---

## Made a Mistake?

Just run the script again! It's safe to run multiple times.

```bash
./setup-template.sh
```

It will show your current config and let you reconfigure.

---

## Windows Users

Use one of these:
- **Git Bash** (recommended): `./setup-template.sh`
- **WSL**: `./setup-template.sh`
- **Command Prompt**: `setup-template.bat` (basic)

---

## Need More Details?

See the full guide: [docs/using-this-template.md](docs/using-this-template.md)

---

## Verification

Want to check if setup is complete?

```bash
./gradlew checkTemplateSetup
```

✅ = You're good to go!  
❌ = Run `./setup-template.sh`
