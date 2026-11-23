#!/usr/bin/env bash
set -euo pipefail

PARIKSHAN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PARIKSHAN_ROOT"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "Usage: $0 <new-version> [--dry-run]"
  echo "Example: $0 0.0.13"
  exit 1
fi

NEW_VERSION="$1"
DRY_RUN="false"
if [ "${2:-}" = "--dry-run" ]; then
  DRY_RUN="true"
elif [ $# -eq 2 ]; then
  echo "ERROR: Unknown option '$2'. Expected '--dry-run'."
  exit 1
fi

if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z._-]+)?$ ]]; then
  echo "ERROR: Invalid semantic version '$NEW_VERSION' (expected e.g. 0.0.13)."
  exit 1
fi

OLD_VERSION=$(grep -E '^libVersion=' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')
if [ -z "$OLD_VERSION" ]; then
  echo "ERROR: Could not read current libVersion from gradle.properties."
  exit 1
fi

if [ "$OLD_VERSION" = "$NEW_VERSION" ]; then
  echo "ERROR: gradle.properties already has libVersion=$NEW_VERSION."
  exit 1
fi

RELEASE_DATE=$(date +%Y-%m-%d)
LATEST_TAG=$(git tag --list 'v*' --sort=-v:refname | head -n 1 || echo "")
COMMIT_NOTES=""
if [ -n "$LATEST_TAG" ]; then
  COMMIT_NOTES=$(git log "${LATEST_TAG}..HEAD" --no-merges --pretty=format:'- %s' || true)
fi

echo "==> Bumping Parikshan version: ${OLD_VERSION} -> ${NEW_VERSION} (date: ${RELEASE_DATE}, dry-run: ${DRY_RUN})"

python3 - "$OLD_VERSION" "$NEW_VERSION" "$RELEASE_DATE" "$DRY_RUN" "$COMMIT_NOTES" <<'PY'
import pathlib
import re
import sys

old_ver, new_ver, release_date, dry_run_str, commit_notes = sys.argv[1:6]
dry_run = dry_run_str == "true"

def replace_exact(path_str: str, replacements: list[tuple[str, str]]) -> None:
    p = pathlib.Path(path_str)
    if not p.is_file():
        print(f"WARN: Missing expected file: {path_str}", file=sys.stderr)
        sys.exit(1)
    content = p.read_text(encoding="utf-8")
    updated = content
    for pattern, repl in replacements:
        new_text, count = re.subn(pattern, repl, updated)
        if count == 0:
            print(f"ERROR: Pattern {pattern!r} matched 0 times in {path_str}", file=sys.stderr)
            sys.exit(1)
        updated = new_text
    if dry_run:
        print(f"[dry-run] Would update {path_str}")
    else:
        p.write_text(updated, encoding="utf-8")
        print(f"Updated {path_str}")

plugin_pat = r'(id\("io\.github\.aryapreetam\.parikshan"\)\s+version\s+")' + re.escape(old_ver) + r'(")'
plugin_repl = rf'\g<1>{new_ver}\g<2>'

# 1. gradle.properties
replace_exact("gradle.properties", [(r"(?m)^libVersion=" + re.escape(old_ver) + r"$", f"libVersion={new_ver}")])

# 2. Sample Gradle build scripts
for sample_file in [
    "samples/cmp-latest/app/shared/build.gradle.kts",
    "samples/composables-sample/shared/build.gradle.kts",
    "samples/issue-playground/shared/build.gradle.kts",
    "samples/standalone-android/app/build.gradle.kts",
]:
    replace_exact(sample_file, [(plugin_pat, plugin_repl)])

# 3. Docs & README
for doc_file in [
    "README.md",
    "docs/index.md",
    "docs/getting-started/installation.md",
    "docs/guides/your-first-test.md",
    "docs/guides/first-test-android.md",
]:
    replace_exact(doc_file, [(plugin_pat, plugin_repl)])

# 4. Bug report template
replace_exact(
    ".github/ISSUE_TEMPLATE/bug_report.yml",
    [
        (r"\(e\.g\. " + re.escape(old_ver) + r"\)", f"(e.g. {new_ver})"),
        (r'(placeholder:\s*")' + re.escape(old_ver) + r'(")', rf'\g<1>{new_ver}\g<2>'),
    ],
)

# 5. SECURITY.md supported versions table
major_minor_x = ".".join(new_ver.split(".")[:2]) + ".x"
replace_exact(
    "SECURITY.md",
    [
        (r"(?m)^\| \d+\.\d+\.x\s+\| :white_check_mark: \|$", f"| {major_minor_x}   | :white_check_mark: |"),
        (r"(?m)^\| < " + re.escape(old_ver) + r"\s+\| :x:\s+\|$", f"| < {new_ver} | :x:                |"),
    ],
)

# 6. CHANGELOG.md
changelog_path = pathlib.Path("CHANGELOG.md")
changelog = changelog_path.read_text(encoding="utf-8")
version_header = f"## [{new_ver}] - {release_date}"
if re.search(rf"(?m)^## \[{re.escape(new_ver)}\]", changelog):
    print(f"CHANGELOG.md already has an entry for [{new_ver}]; leaving unchanged.")
else:
    unreleased_match = re.search(r"(?m)^## \[Unreleased\]\s*$", changelog)
    if unreleased_match:
        updated_changelog = re.sub(r"(?m)^## \[Unreleased\]\s*$", version_header, changelog, count=1)
        action_desc = f"Promoted [Unreleased] section in CHANGELOG.md to {version_header}"
    else:
        first_release = re.search(r"(?m)^## \[\d+\.\d+\.\d+", changelog)
        if not first_release:
            print("ERROR: Could not locate existing release header in CHANGELOG.md", file=sys.stderr)
            sys.exit(1)
        bullets = commit_notes.strip() if commit_notes.strip() else "- TODO: Document notable changes before committing."
        section = (
            f"{version_header}\n\n"
            "### Fixed & Resolved Issues\n"
            f"{bullets}\n\n"
        )
        idx = first_release.start()
        updated_changelog = changelog[:idx] + section + changelog[idx:]
        action_desc = f"Inserted draft {version_header} section into CHANGELOG.md from git history"

    if dry_run:
        print(f"[dry-run] {action_desc}")
    else:
        changelog_path.write_text(updated_changelog, encoding="utf-8")
        print(action_desc)
PY

if [ "$DRY_RUN" = "true" ]; then
  echo "==> Dry-run complete. No files were modified."
  exit 0
fi

echo ""
echo "==> Auditing for stale references to ${OLD_VERSION} (excluding CHANGELOG.md):"
if git grep -n "${OLD_VERSION}" -- . ':!CHANGELOG.md'; then
  echo "WARN: Remaining references to ${OLD_VERSION} found above. Inspect before committing."
else
  echo "OK: Zero stale references to ${OLD_VERSION} outside CHANGELOG.md."
fi

echo ""
echo "==> IMPORTANT: Review and curate CHANGELOG.md entries for [${NEW_VERSION}] before committing!"
echo "==> Next steps:"
echo "    1. Curate CHANGELOG.md (categorize Added / Fixed & Resolved Issues / CI & Tooling)"
echo "    2. Inspect 'git diff'"
echo "    3. Commit on a release branch (e.g. chore/release-${NEW_VERSION}) and open PR to main"
echo "    4. After PR merges and 'CI / CI Success' passes on main, tag and push: git tag v${NEW_VERSION} && git push origin v${NEW_VERSION}"
