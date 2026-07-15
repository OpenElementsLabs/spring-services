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
# -DprocessAllModules keeps the reactor lockstep: it bumps the parent and every
# module (each references the parent version explicitly, not ${revision}).
./mvnw versions:set -DnewVersion=$NEW_VERSION -DprocessAllModules

# Build and test locally so we never push a tag that fails CI.
#
# Use the SAME profile the release workflow's build step uses (-Pfull-build) and
# run through the `package` phase via `verify`. That triggers the full-build
# executions — Javadoc jar, sources jar and the CycloneDX SBOM — so a broken @link,
# a missing source attachment, or an SBOM failure breaks HERE, locally, instead of
# after the tag is already pushed and the release workflow is running.
#
# Safe to run without release secrets: GPG signing and the JReleaser deploy live in
# the separate deploy-release profile (not activated here), so `verify` does not
# sign or deploy anything — it just builds the artifacts the release will later
# sign and publish.
#
# This runs BEFORE the release doc is generated on purpose: if the build fails,
# `set -e` aborts the script here and we never spend AI tokens documenting a
# release that was never cut.
./mvnw -Pfull-build clean verify

# Validate every deployed POM against Maven Central's rules BEFORE we tag.
#
# This runs PomChecker — the SAME validation JReleaser performs during the
# release workflow's deploy step (the "<url> is not defined in POM" /
# "Rules for publishing to Maven Central were not met" failures come from here).
# JReleaser only runs it AFTER the tag is pushed, so without this gate a POM
# metadata bug (missing <url>/<name>/<description>/<licenses>/<developers>/<scm>)
# is only discovered once the release workflow is already running against a tag.
#
# PomChecker needs no secrets, no signing and no staging, so it is safe to run
# locally. It runs AFTER versions:set on purpose: the version is now the real
# (non-SNAPSHOT) release version, so the strict release rules are enforced.
# It runs across the whole reactor (no -N) so every module — core, the feature
# modules, spring-services-all and the BOM — is checked, exactly as JReleaser does.
echo "Validating POMs against Maven Central rules (PomChecker)..."
./mvnw -B org.kordamp.maven:pomchecker-maven-plugin:1.14.0:check-maven-central

# Generate the release doc only after a green build, but still before committing
# so it ships inside the release commit (and therefore the v$NEW_VERSION tag).
# The version is already set in the build file, so the skill derives the correct
# version delta.
generate_release_doc

# `commit -am` only stages modified tracked files, but the release doc is a NEW
# (untracked) file — and its exact path depends on the project's convention
# (this repo uses docs/upgrade-to-X.Y.md, others use docs/releases/vX.Y.md). Stage
# the whole docs/ tree so the freshly generated doc is included regardless of its
# name or sub-directory (guarded — docs/ should exist, but never block the release
# if it doesn't).
git add docs 2>/dev/null || true
git commit -am "Version $NEW_VERSION"
git push

# Tag and push. The vA.B.C tag triggers the release workflow that deploys
# to Maven Central and creates the GitHub release.
echo "Tagging v$NEW_VERSION (this triggers the release workflow)"
git tag "v$NEW_VERSION"
git push origin "v$NEW_VERSION"

echo "Setting version to $NEXT_VERSION"
./mvnw versions:set -DnewVersion=$NEXT_VERSION -DprocessAllModules
git commit -am "Version $NEXT_VERSION"
git push
