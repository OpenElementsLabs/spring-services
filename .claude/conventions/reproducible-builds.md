# Reproducible Builds Convention for Claude Code

Reproducible builds ensure that building the same source code with the same instructions always produces byte-identical artifacts, regardless of when, where, or by whom the build is executed. This is a core quality requirement for all Open Elements projects.

## Core Principle

- **IMPORTANT**: Never produce code, configuration, or build setups that prevent reproducible builds. Every build from the same source commit must yield identical output.

## Common Sources of Non-Reproducibility

Avoid these well-known pitfalls in all code and build configurations:

1. **OS-specific line endings** -- Use `.editorconfig` and `.gitattributes` to enforce consistent line endings (`LF`). Never rely on OS defaults.
2. **Timestamps embedded in artifacts** -- Do not store build timestamps, dates, or "built at" strings inside compiled artifacts, manifests, or generated code. If a build date is required for display purposes, derive it from the Git commit timestamp (`SOURCE_DATE_EPOCH`), not from `System.currentTimeMillis()` or `Date.now()`.
3. **Time without timezone** -- Always use UTC for any time-related operation in build scripts and generated code. Never use the local timezone of the build machine.
4. **Non-deterministic file ordering** -- Archive tools (JAR, ZIP, TAR) may list files in filesystem order, which varies across OS and filesystem. Ensure deterministic file ordering in all archive steps.
5. **Non-deterministic map/set iteration** -- When generating code or configuration from collections, sort entries before writing output.
6. **Locale-dependent formatting** -- Use a fixed locale (e.g., `Locale.ROOT` in Java, `en-US` in JavaScript) for any formatting that ends up in build output.
7. **Absolute paths in artifacts** -- Never embed absolute paths of the build machine in compiled output, debug info, or generated files.
8. **Randomized or non-deterministic identifiers** -- Do not use `UUID.randomUUID()`, `Math.random()`, or similar sources for values that end up in build output.

## Version Pinning

- **IMPORTANT**: All versions must be hard-pinned to exact values. Version ranges, floating tags, and snapshot/pre-release references are forbidden in release builds.
- This applies to everything that influences the build output: dependencies, build tool plugins, compiler versions, base Docker images, CI action versions, and tool versions.

### What must be pinned

| Category | Example of correct pinning |
|---|---|
| Dependencies | `1.2.3` (not `^1.2.3`, `~1.2.3`, `1.x`, `latest`) |
| Build plugins / toolchain | Explicit version in `pluginManagement` / `packageManager` field |
| Docker base images | `eclipse-temurin:21.0.4_7-jre` (not `eclipse-temurin:21-jre` or `latest`) |
| CI actions | `actions/checkout@v6` with exact major version, or SHA pin |
| Node.js / Java / Python | Exact version in `.nvmrc`, `.sdkmanrc`, `.python-version` |
| Package manager | `corepack` with `packageManager` field in `package.json` |

### Development vs. Release

- **Development branches (SNAPSHOT / pre-release)**: SNAPSHOT dependencies and version ranges are tolerated during active development to ease integration work. This is the only context where non-pinned versions are acceptable.
- **IMPORTANT**: Release builds (triggered from Git tags) must never depend on SNAPSHOT versions, version ranges, `latest` tags, or any other floating reference. The CI pipeline must fail if a release build resolves a non-pinned dependency.

## Lockfiles

- **IMPORTANT**: Always commit lockfiles (`pnpm-lock.yaml`, `package-lock.json`, `yarn.lock`, `Cargo.lock`, etc.) to version control.
- CI builds must use the frozen lockfile flag (`--frozen-lockfile` for pnpm, `--ci` for npm) to guarantee that the exact locked versions are used.
- For Maven projects, use the `maven-enforcer-plugin` with `requireUpperBoundDeps` and consider the `reproducible-build-maven-plugin` to strip non-deterministic metadata.

## Verification Tasks

Projects should provide optional verification tasks that confirm build reproducibility by building the same source twice and comparing the outputs.

### Java / Maven

Add a Maven profile or script that:

1. Runs `./mvnw clean verify` twice into separate output directories.
2. Compares the resulting artifacts byte-for-byte (e.g., using `diff` or `sha256sum`).
3. Fails if any artifact differs.

Example shell script (`scripts/verify-reproducible-build.sh`):

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== Build 1 ==="
./mvnw clean verify -DskipTests -Dmaven.build.outputTimestamp=$(git log -1 --format=%ct)
cp -r target target-build1

echo "=== Build 2 ==="
./mvnw clean verify -DskipTests -Dmaven.build.outputTimestamp=$(git log -1 --format=%ct)
cp -r target target-build2

echo "=== Comparing artifacts ==="
diff_found=0
for f in target-build1/*.jar target-build1/*.war 2>/dev/null; do
  filename=$(basename "$f")
  if [ -f "target-build2/$filename" ]; then
    if ! diff -q "$f" "target-build2/$filename" > /dev/null 2>&1; then
      echo "MISMATCH: $filename"
      diff_found=1
    else
      echo "OK: $filename"
    fi
  fi
done

rm -rf target-build1 target-build2

if [ "$diff_found" -eq 1 ]; then
  echo "FAIL: Artifacts are not reproducible."
  exit 1
fi
echo "PASS: All artifacts are byte-identical."
```

### TypeScript / Node.js

Add a script that:

1. Runs the production build twice into separate output directories.
2. Compares all output files byte-for-byte.
3. Fails if any file differs.

Example shell script (`scripts/verify-reproducible-build.sh`):

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== Build 1 ==="
pnpm run build
cp -r dist dist-build1 2>/dev/null || cp -r .next .next-build1

echo "=== Build 2 ==="
pnpm run build
cp -r dist dist-build2 2>/dev/null || cp -r .next .next-build2

echo "=== Comparing outputs ==="
build_dir1=$([ -d dist-build1 ] && echo "dist-build1" || echo ".next-build1")
build_dir2=$([ -d dist-build2 ] && echo "dist-build2" || echo ".next-build2")

if diff -rq "$build_dir1" "$build_dir2" > /dev/null 2>&1; then
  echo "PASS: All outputs are byte-identical."
else
  echo "FAIL: Outputs differ:"
  diff -rq "$build_dir1" "$build_dir2"
  rm -rf "$build_dir1" "$build_dir2"
  exit 1
fi

rm -rf "$build_dir1" "$build_dir2"
```

### Integration into CI

- These verification scripts are optional per project but recommended for all release pipelines.
- Add them as a separate CI job or step that runs after the main build succeeds.
- The verification step should be clearly labeled (e.g., `verify-reproducible-build`) so failures are easy to diagnose.