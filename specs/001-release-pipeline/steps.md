# Implementation Steps: Release Pipeline

## Step 1: Fix `.gitignore` and add `.env.example`

- [x] Fix `.gitignore` — `.env` is currently merged with another entry on the same line. Add `.env` as its own line so credentials are properly excluded from version control.
- [x] Create `.env.example` with placeholder entries for all required credentials (`JRELEASER_GITHUB_TOKEN`, `JRELEASER_MAVENCENTRAL_USERNAME`, `JRELEASER_MAVENCENTRAL_TOKEN`, `JRELEASER_GPG_PASSPHRASE`, `JRELEASER_GPG_SECRET_KEY`) so developers know what to configure.

**Acceptance criteria:**
- [x] `.env` appears as its own line in `.gitignore`
- [x] A `.env` file placed in the project root is not shown by `git status`
- [x] `.env.example` exists with all five credential placeholders and comments
- [x] Project builds successfully (`mvn clean compile` — `mvn clean verify` fails due to pre-existing Docker/Testcontainers issue unrelated to this spec)

**Related behaviors:** `.env` file is excluded from version control

---

## Step 2: Add `distributionManagement` to `pom.xml`

- [x] Add `<distributionManagement>` block to `pom.xml` pointing both `<repository>` and `<snapshotRepository>` to GitHub Packages (`https://maven.pkg.github.com/OpenElementsLabs/spring-services`), using `id=github`.

**Acceptance criteria:**
- [x] `distributionManagement` block is present in `pom.xml` with correct URLs
- [x] Project builds successfully (`mvn clean compile`)

**Related behaviors:** SNAPSHOT is published on push to main (prerequisite — `mvn deploy` needs a target)

---

## Step 3: Configure publication profile (JReleaser plugin)

- [x] Add `jreleaser-maven-plugin` declaration to the `publication` profile
- Note: `altDeploymentRepository` is passed via command line in `release.sh` (not hardcoded in profile) so the profile works for both SNAPSHOT CI (deploys to GitHub Packages) and local release (stages locally). GPG signing is handled by JReleaser, not the maven-gpg-plugin.

**Acceptance criteria:**
- [x] The publication profile includes JReleaser plugin declaration alongside existing javadoc, sources, and SBOM plugins
- [x] Project builds successfully (`mvn clean compile`)

**Related behaviors:** Release artifacts include all required components, Successful release publishes to Maven Central and creates GitHub Release (prerequisite — profile must produce the right artifacts)

---

## Step 4: Create `jreleaser.toml`

- [x] Create `jreleaser.toml` in the project root with:
  - `[signing]` — GPG signing, armored, always active
  - `[deploy.maven.mavenCentral.release-deploy]` — Central Portal URL, staging repository path, `applyMavenCentralRules = true`
  - `[release.github]` — owner/name, changelog with conventional-commits preset

**Acceptance criteria:**
- [x] `jreleaser.toml` exists and is valid TOML
- [x] Configuration matches the design document (signing, Maven Central deploy, GitHub release with changelog)
- [x] Project builds successfully (`mvn clean compile`)

**Related behaviors:** Successful release publishes to Maven Central and creates GitHub Release (prerequisite — JReleaser needs config)

---

## Step 5: Update `release.sh`

- [x] Update `release.sh` to run `mvn -Ppublication deploy` with `-DaltDeploymentRepository=local::file:./target/staging-deploy` (staging artifacts locally) after the verify step
- [x] Add `mvn jreleaser:full-release` invocation after staging
- [x] Ensure the script follows the exact flow from the design: set version -> verify -> commit+push -> deploy (stage) -> jreleaser -> set snapshot version -> commit+push
- [x] Verify `set -e` is in place so failures stop the script
- [x] Removed redundant `./mvnw clean` call (already done by `clean verify`)

**Acceptance criteria:**
- [x] `release.sh` contains all steps from the design document in the correct order
- [x] Script uses `set -e` to fail fast
- [x] Script validates version arguments before any work (existing behavior preserved)
- [x] Project compiles successfully

**Related behaviors:** Successful release publishes to Maven Central and creates GitHub Release, Release fails on invalid release version format, Release fails on invalid snapshot version format, Release fails on missing version arguments, Release stops if build fails, Release stops if Maven Central upload fails, Release stops if GPG signing fails

---

## Step 6: Create `build.yml` GitHub Actions workflow

- [x] Create `.github/workflows/build.yml` triggered on pull requests and pushes to `main`
- [x] Steps: checkout, setup JDK 21 (Temurin), cache Maven dependencies, run `mvn spotless:check`, run `mvn clean verify`
- [x] No publishing or deployment occurs

**Acceptance criteria:**
- [x] `.github/workflows/build.yml` exists with correct triggers (pull_request + push to main)
- [x] Workflow includes `mvn spotless:check` before `mvn clean verify`
- [x] No `mvn deploy` or artifact publishing in this workflow
- [x] YAML is valid

**Related behaviors:** PR triggers build and test, Build fails on formatting violations, Build fails on test failures

---

## Step 7: Create `snapshot.yml` GitHub Actions workflow

- [x] Create `.github/workflows/snapshot.yml` triggered only on push to `main`
- [x] Steps: checkout, setup JDK 21 (Temurin), cache Maven dependencies, run `mvn clean verify`, run `mvn -Ppublication deploy`
- [x] Set minimal permissions: `packages: write`, `contents: read`
- [x] Use `GITHUB_TOKEN` for authentication with GitHub Packages

**Acceptance criteria:**
- [x] `.github/workflows/snapshot.yml` exists, triggered only on `push` to `main`
- [x] Workflow publishes to GitHub Packages via `mvn -Ppublication deploy`
- [x] Permissions block restricts `GITHUB_TOKEN` scope to `packages: write` and `contents: read`
- [x] YAML is valid

**Related behaviors:** SNAPSHOT is published on push to main, SNAPSHOT publish is skipped on non-main branches, SNAPSHOT publish fails if tests fail, CI workflows use minimal token permissions

---

## Step 8: Verify release script behavior (manual verification checklist)

Since release pipeline scenarios involve external services (Maven Central, GitHub Releases, GPG signing), they cannot be verified with automated unit tests. This step provides a manual verification checklist.

- [x] Run `./release.sh` with no arguments — exits with usage message and code 1
- [x] Run `./release.sh 1.0 1.1.0-SNAPSHOT` — exits with version format error and code 1
- [x] Run `./release.sh 1.0.0 1.1.0` — exits with snapshot format error and code 1
- [x] Review `release.sh` line-by-line against the design document flow — matches
- [x] Verify `set -e` ensures the script stops on any command failure (covers "Release stops if build fails", "Release stops if Maven Central upload fails", "Release stops if GPG signing fails")

**Acceptance criteria:**
- [x] All three validation error scenarios produce correct error messages and exit without side effects
- [x] Script flow matches design document exactly

**Related behaviors:** Release fails on invalid release version format, Release fails on invalid snapshot version format, Release fails on missing version arguments, Release stops if build fails, Release stops if Maven Central upload fails, Release stops if GPG signing fails

---

## Step 9: Update project documentation

- [x] Update `.claude/conventions/project-specific/project-features.md` — populated with full project features including release pipeline
- [x] Update `.claude/conventions/project-specific/project-tech.md` — populated with full tech stack including JReleaser, GitHub Actions, GitHub Packages, Maven Central
- [x] Update `.claude/conventions/project-specific/project-structure.md` — populated with full repo layout including `.github/workflows/`, `jreleaser.toml`, `release.sh`, `.env.example`
- [x] Update `.claude/conventions/project-specific/project-architecture.md` — populated with full architecture including release and CI/CD flow
- [x] `README.md` not updated — it is a single-line title and a full rewrite is outside this spec's scope

**Acceptance criteria:**
- [x] All project-specific docs reflect the new release pipeline
- [x] Documentation is accurate and matches the implemented configuration
- [x] Project compiles successfully

**Related behaviors:** (documentation step — supports all behaviors indirectly)

---

## Step 10: Update spec index status

- [x] Update `specs/INDEX.md` to change status from `open` to `done`

**Acceptance criteria:**
- [x] `INDEX.md` shows spec 001 with status `done`

**Related behaviors:** (process step)
