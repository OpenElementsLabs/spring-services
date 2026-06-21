#!/bin/bash
set -e  # Exit the script if any command fails

# Cuts a release by setting the version, tagging, and pushing.
# Pushing the tag (vA.B.C) triggers .github/workflows/release.yml, which
# builds, signs, and deploys to Maven Central and creates the GitHub
# release. This script only prepares git state; it does NOT deploy.

if [ -z "$1" ]; then
  echo "Please provide version that should be released and the next snapshot version. Example: ./release.sh 0.1.0 0.2.0-SNAPSHOT"
  exit 1
fi

if [ -z "$2" ]; then
  echo "Please provide version that should be released and the next snapshot version. Example: ./release.sh 0.1.0 0.2.0-SNAPSHOT"
  exit 1
fi

if ! [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "The release version must be in the format A.B.C. Example: 0.1.0"
  exit 1
fi

if ! [[ "$2" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  echo "The next snapshot version must be in the format A.B.C-SNAPSHOT. Example: 0.2.0-SNAPSHOT"
  exit 1
fi

NEW_VERSION="$1"
NEXT_VERSION="$2"

# Best-effort: generate release/upgrade documentation with Claude Code.
# Requires the `claude` CLI (Claude Code) on this machine. Documentation is a
# "nice to have" for the release, not a gate: if the CLI is missing or the run
# fails, we log a warning and continue so a doc problem never blocks a release.
generate_release_doc() {
  if ! command -v claude >/dev/null 2>&1; then
    echo "WARNING: 'claude' CLI not found; skipping release documentation."
    echo "         Install Claude Code to auto-generate docs/releases/ on release."
    return 0
  fi
  echo "Generating release documentation for v$NEW_VERSION with Claude Code..."
  # Runs headless (-p). The /release-doc skill reads git history (Bash) and
  # writes the doc into docs/releases/, so we allow exactly those tools and
  # auto-accept edits — no interactive prompt is possible in a script.
  if claude -p "/release-doc v$NEW_VERSION" \
      --permission-mode acceptEdits \
      --allowedTools "Bash Read Write Edit Glob Grep"; then
    echo "Release documentation generated under docs/releases/."
  else
    echo "WARNING: release documentation generation failed; continuing release."
  fi
}

echo "Releasing version $NEW_VERSION"
./mvnw versions:set -DnewVersion=$NEW_VERSION

# Generate the release doc before committing so it ships inside the release
# commit (and therefore the v$NEW_VERSION tag). The version is already set in
# the build file, so the skill can derive the correct version delta.
generate_release_doc

# Build and test locally so we never push a tag that fails CI.
./mvnw clean verify
# `commit -am` only stages modified tracked files; the new doc is untracked,
# so add docs/releases/ explicitly (guarded — the dir may not exist if the
# doc step was skipped or failed).
git add docs/releases 2>/dev/null || true
git commit -am "Version $NEW_VERSION"
git push

# Tag and push. The vA.B.C tag triggers the release workflow that deploys
# to Maven Central and creates the GitHub release.
echo "Tagging v$NEW_VERSION (this triggers the release workflow)"
git tag "v$NEW_VERSION"
git push origin "v$NEW_VERSION"

echo "Setting version to $NEXT_VERSION"
./mvnw versions:set -DnewVersion=$NEXT_VERSION
git commit -am "Version $NEXT_VERSION"
git push
