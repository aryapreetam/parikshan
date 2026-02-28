@echo off
setlocal enabledelayedexpansion

REM Compose Multiplatform Library Template Setup Script (Windows)
REM This script configures the template with your library's information

echo ============================================================
echo    Compose Multiplatform Library Template Setup
echo ============================================================
echo.

REM Configuration file
set CONFIG_FILE=.template-config.json

REM Check if already configured
if exist "%CONFIG_FILE%" (
    echo [WARNING] Template already configured!
    echo.
    echo Current configuration:
    type "%CONFIG_FILE%"
    echo.
    set /p RECONFIGURE="Do you want to reconfigure? (y/n): "
    if /i not "!RECONFIGURE!"=="y" (
        echo Setup cancelled. No changes made.
        exit /b 0
    )
    echo.
)

REM Detect git remote URL for auto-detecting repository name and org
set DETECTED_ORG=
set DETECTED_REPO=
for /f "delims=" %%i in ('git remote get-url origin 2^>nul') do set GIT_REMOTE=%%i
if defined GIT_REMOTE (
    REM Extract org and repo from github.com URLs
    echo !GIT_REMOTE! | findstr /i "github.com" >nul
    if !errorlevel! equ 0 (
        REM Parse github.com/org/repo or github.com:org/repo
        for /f "tokens=4,5 delims=/:." %%a in ("!GIT_REMOTE!") do (
            set DETECTED_ORG=%%a
            set DETECTED_REPO=%%b
        )
    )
)

REM Validate that we have detected a repository
if not defined DETECTED_REPO (
    echo [ERROR] Could not detect repository name from git remote!
    echo Please make sure you've created this repository from the template and have a git remote configured.
    exit /b 1
)

REM Use detected repository name (no user input)
set REPO_NAME=!DETECTED_REPO!

echo Please provide your library configuration:
echo.
echo Repository detected: !REPO_NAME!
echo.

REM Library name (human-readable, for POM)
set /p LIBRARY_NAME="Library name [e.g., Media Viewer Library]: "
if "!LIBRARY_NAME!"=="" (
    echo [ERROR] Library name cannot be empty!
    exit /b 1
)

REM Artifact name (default: repo name)
set /p ARTIFACT_NAME="Maven artifact name [!REPO_NAME!]: "
if "!ARTIFACT_NAME!"=="" set ARTIFACT_NAME=!REPO_NAME!

REM GitHub org/username
if defined DETECTED_ORG (
    set /p GITHUB_ORG="GitHub username/organization [!DETECTED_ORG!]: "
    if "!GITHUB_ORG!"=="" set GITHUB_ORG=!DETECTED_ORG!
) else (
    set /p GITHUB_ORG="GitHub username/organization: "
    if "!GITHUB_ORG!"=="" (
        echo [ERROR] GitHub org cannot be empty!
        exit /b 1
    )
)

REM Maven group ID
set DEFAULT_GROUP_ID=io.github.!GITHUB_ORG!
set /p GROUP_ID="Maven group ID [!DEFAULT_GROUP_ID!]: "
if "!GROUP_ID!"=="" set GROUP_ID=!DEFAULT_GROUP_ID!

REM Developer name
set /p DEVELOPER_NAME="Developer name (for POM): "
if "!DEVELOPER_NAME!"=="" (
    echo [ERROR] Developer name cannot be empty!
    exit /b 1
)

REM Library description
set /p LIBRARY_DESCRIPTION="Library description (for POM): "
if "!LIBRARY_DESCRIPTION!"=="" (
    echo [ERROR] Library description cannot be empty!
    exit /b 1
)

REM Version
set DEFAULT_VERSION=0.0.1
set /p VERSION="Initial version [!DEFAULT_VERSION!]: "
if "!VERSION!"=="" set VERSION=!DEFAULT_VERSION!

REM Generate package name (remove hyphens and special chars, lowercase)
set PACKAGE_NAME=!ARTIFACT_NAME:-=!
REM Convert to lowercase
for %%a in (a b c d e f g h i j k l m n o p q r s t u v w x y z) do (
    call set PACKAGE_NAME=%%PACKAGE_NAME:%%a=%%a%%
)
set PACKAGE_PATH=!GROUP_ID:.=/!/!PACKAGE_NAME!

echo.
echo ============================================================
echo Configuration Summary:
echo ============================================================
echo Library name:         !LIBRARY_NAME!
echo Artifact name:        !ARTIFACT_NAME!
echo Version:              !VERSION!
echo GitHub org:           !GITHUB_ORG!
echo Maven group ID:       !GROUP_ID!
echo Developer name:       !DEVELOPER_NAME!
echo Library description:  !LIBRARY_DESCRIPTION!
echo Package name:         !GROUP_ID!.!PACKAGE_NAME!
echo Package path:         !PACKAGE_PATH!
echo ============================================================
echo.

set /p CONFIRM="Proceed with this configuration? (y/n): "
if /i not "!CONFIRM!"=="y" (
    echo Setup cancelled.
    exit /b 1
)

echo.
echo Starting template configuration...
echo.

REM Save configuration to JSON
(
echo {
echo   "configured": true,
echo   "repo_name": "!REPO_NAME!",
echo   "library_name": "!LIBRARY_NAME!",
echo   "artifact_name": "!ARTIFACT_NAME!",
echo   "github_org": "!GITHUB_ORG!",
echo   "group_id": "!GROUP_ID!",
echo   "developer_name": "!DEVELOPER_NAME!",
echo   "library_description": "!LIBRARY_DESCRIPTION!",
echo   "package_name": "!GROUP_ID!.!PACKAGE_NAME!",
echo   "package_path": "!PACKAGE_PATH!",
echo   "version": "!VERSION!"
echo }
) > "%CONFIG_FILE%"

echo [OK] Configuration saved to %CONFIG_FILE%

REM Backup old values for replacement
set OLD_REPO=cmp-lib-template
set OLD_ORG=aryapreetam
set OLD_ARTIFACT=fiblib
set OLD_GROUP=io.github.aryapreetam
set OLD_NAMESPACE=io.github.aryapreetam.fiblib
set OLD_DEVELOPER=Preetam Bhosle
set OLD_DESCRIPTION=Compose Multiplatform library for fibonacci numbers
set OLD_LIB_NAME=Fibonacci Library
set OLD_VERSION=0.0.3

REM Replace in settings.gradle.kts
echo [OK] Updating settings.gradle.kts...
powershell -Command "(Get-Content 'settings.gradle.kts') -replace 'rootProject.name = \"%OLD_REPO%\"', 'rootProject.name = \"!REPO_NAME!\"' | Set-Content 'settings.gradle.kts'"

REM Replace in lib/build.gradle.kts
echo [OK] Updating lib/build.gradle.kts...
powershell -Command "$content = Get-Content 'lib/build.gradle.kts' -Raw; $content = $content -replace 'namespace = \"%OLD_NAMESPACE%\"', 'namespace = \"!GROUP_ID!.!PACKAGE_NAME!\"'; $content = $content -replace 'coordinates\(\"%OLD_GROUP%\", \"%OLD_ARTIFACT%\", \"%OLD_VERSION%\"', 'coordinates(\"!GROUP_ID!\", \"!ARTIFACT_NAME!\", \"!VERSION!\"'; $content = $content -replace 'name = \"%OLD_LIB_NAME%\"', 'name = \"!LIBRARY_NAME!\"'; $content = $content -replace 'description = \"%OLD_DESCRIPTION%\"', 'description = \"!LIBRARY_DESCRIPTION!\"'; $content = $content -replace 'url = \"https://%OLD_ORG%.github.io/%OLD_REPO%\"', 'url = \"https://!GITHUB_ORG!.github.io/!REPO_NAME!\"'; $content = $content -replace 'id = \"%OLD_ORG%\"', 'id = \"!GITHUB_ORG!\"'; $content = $content -replace 'name = \"%OLD_DEVELOPER%\"', 'name = \"!DEVELOPER_NAME!\"'; $content = $content -replace 'url = \"https://github.com/%OLD_ORG%/%OLD_REPO%\"', 'url = \"https://github.com/!GITHUB_ORG!/!REPO_NAME!\"'; Set-Content 'lib/build.gradle.kts' $content"

REM Replace in CONTRIBUTING.md
echo [OK] Updating CONTRIBUTING.md...
powershell -Command "(Get-Content 'CONTRIBUTING.md') -replace '%OLD_REPO%', '!REPO_NAME!' -replace '%OLD_ORG%', '!GITHUB_ORG!' -replace '%OLD_GROUP%', '!GROUP_ID!' -replace '%OLD_ARTIFACT%', '!ARTIFACT_NAME!' | Set-Content 'CONTRIBUTING.md'"

REM Replace in README.MD
echo [OK] Updating README.MD...
powershell -Command "(Get-Content 'README.MD') -replace '%OLD_REPO%', '!REPO_NAME!' -replace '%OLD_ORG%', '!GITHUB_ORG!' -replace '%OLD_ARTIFACT%', '!ARTIFACT_NAME!' -replace '%OLD_GROUP%', '!GROUP_ID!' | Set-Content 'README.MD'"

REM Remove the template setup warning section from README.MD
echo [OK] Removing template setup warning from README.MD...
powershell -Command "$content = Get-Content 'README.MD' -Raw; $content = $content -replace '(?s)<!-- ⚠️ TEMPLATE SETUP WARNING.*?<!-- END TEMPLATE SETUP WARNING -->', ''; Set-Content 'README.MD' $content"

REM Replace in LICENSE
echo [OK] Updating LICENSE...
powershell -Command "(Get-Content 'LICENSE') -replace 'Copyright \(c\) 2025 %OLD_ORG%', 'Copyright (c) 2025 !GITHUB_ORG!' | Set-Content 'LICENSE'"

REM Replace in .github/workflows/release.yml
echo [OK] Updating .github/workflows/release.yml...
powershell -Command "(Get-Content '.github/workflows/release.yml') -replace 'name: ''%OLD_REPO%''', 'name: ''!REPO_NAME!''' -replace 'repo: ''https://github.com/%OLD_ORG%/%OLD_REPO%''', 'repo: ''https://github.com/!GITHUB_ORG!/!REPO_NAME!''' -replace '<title>Compose Multiplatform Library Template</title>', '<title>!REPO_NAME!</title>' | Set-Content '.github/workflows/release.yml'"

REM Create new package directory structure
echo [OK] Creating package structure: lib/src/commonMain/kotlin/!PACKAGE_PATH!
mkdir "lib\src\commonMain\kotlin\!PACKAGE_PATH!" 2>nul
mkdir "lib\src\commonTest\kotlin\!PACKAGE_PATH!" 2>nul

REM Create README in new package directory
(
echo # Your Library Code Goes Here
echo.
echo This is your library's main package: `!GROUP_ID!.!PACKAGE_NAME!`
echo.
echo ## Getting Started
echo.
echo Add your library code in this directory. The example `fiblib` package is kept as a reference - you can delete it when you're ready.
echo.
echo ## Package Structure
echo.
echo ```
echo lib/src/
echo ├── commonMain/kotlin/
echo │   ├── !PACKAGE_PATH!/          # Your library code ^(THIS DIRECTORY^)
echo │   └── fiblib/                  # Example code ^(can be deleted^)
echo └── commonTest/kotlin/
echo     ├── !PACKAGE_PATH!/          # Your tests
echo     └── fiblib/                  # Example tests ^(can be deleted^)
echo ```
echo.
echo ## Next Steps
echo.
echo 1. Add your library code here
echo 2. Update the sample app to use your library
echo 3. Delete the `fiblib` example when ready
echo 4. Write tests in `lib/src/commonTest/kotlin/!PACKAGE_PATH!/`
) > "lib\src\commonMain\kotlin\!PACKAGE_PATH!\README.md"

echo [OK] Created package structure with README

echo.
echo ============================================================
echo                  Setup Complete!
echo ============================================================
echo.
echo What's next?
echo.
echo 1. Your new package is ready at:
echo    lib/src/commonMain/kotlin/!PACKAGE_PATH!/
echo.
echo 2. The example 'fiblib' code is still available for reference
echo    You can delete it when you're ready
echo.
echo 3. Update the sample app to use your library:
echo    sample/composeApp/src/commonMain/kotlin/sample/app/App.kt
echo.
echo 4. Commit your changes:
echo    git add .
echo    git commit -m "Configure template for !REPO_NAME!"
echo.
echo Happy coding!
echo.

endlocal
