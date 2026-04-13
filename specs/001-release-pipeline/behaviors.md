# Behaviors: Release Pipeline

## Local Release via `release.sh`

### Successful release publishes to Maven Central and creates GitHub Release

- **Given** the developer has valid credentials in `.env` (Maven Central token, GitHub token, GPG key)
- **When** the developer runs `./release.sh 1.0.0 1.1.0-SNAPSHOT`
- **Then** the project version is set to `1.0.0`
- **And** the project builds and all tests pass
- **And** a commit with message "Version 1.0.0" is pushed to the remote
- **And** artifacts (JAR, sources, Javadoc, SBOM) are staged to `target/staging-deploy`
- **And** JReleaser signs all artifacts with GPG
- **And** JReleaser uploads the bundle to Maven Central (new portal)
- **And** JReleaser creates a GitHub Release with a changelog
- **And** the project version is set to `1.1.0-SNAPSHOT`
- **And** a commit with message "Version 1.1.0-SNAPSHOT" is pushed to the remote

### Release fails on invalid release version format

- **Given** the developer runs `./release.sh 1.0 1.1.0-SNAPSHOT`
- **When** the version format is validated
- **Then** the script exits with an error message indicating the correct format `A.B.C`
- **And** no commits or pushes occur

### Release fails on invalid snapshot version format

- **Given** the developer runs `./release.sh 1.0.0 1.1.0`
- **When** the version format is validated
- **Then** the script exits with an error message indicating the correct format `A.B.C-SNAPSHOT`
- **And** no commits or pushes occur

### Release fails on missing version arguments

- **Given** the developer runs `./release.sh` without arguments
- **When** the arguments are checked
- **Then** the script exits with a usage example
- **And** no commits or pushes occur

### Release stops if build fails

- **Given** the developer has valid credentials in `.env`
- **And** the project has a compilation error or a failing test
- **When** the developer runs `./release.sh 1.0.0 1.1.0-SNAPSHOT`
- **Then** the script exits with a non-zero exit code during `mvn clean verify`
- **And** no version commit is pushed
- **And** no artifacts are deployed to Maven Central
- **And** no GitHub Release is created

### Release stops if Maven Central upload fails

- **Given** the developer has invalid Maven Central credentials in `.env`
- **When** the developer runs `./release.sh 1.0.0 1.1.0-SNAPSHOT`
- **Then** the build and tests pass
- **And** the version commit "Version 1.0.0" is pushed
- **And** JReleaser fails during Maven Central upload
- **And** the script exits with a non-zero exit code
- **And** the SNAPSHOT version is NOT set (manual recovery required)

### Release stops if GPG signing fails

- **Given** the developer has an invalid or missing GPG key in `.env`
- **When** the developer runs `./release.sh 1.0.0 1.1.0-SNAPSHOT`
- **Then** JReleaser fails during the signing step
- **And** no artifacts are uploaded to Maven Central
- **And** no GitHub Release is created

## SNAPSHOT Publishing (CI)

### SNAPSHOT is published on push to main

- **Given** the project version ends with `-SNAPSHOT`
- **And** GitHub Actions secrets are configured (GITHUB_TOKEN is automatic)
- **When** a commit is pushed to the `main` branch
- **Then** CI runs `mvn clean verify` successfully
- **And** CI runs `mvn deploy` which publishes the SNAPSHOT to GitHub Packages
- **And** the artifact is available at `https://maven.pkg.github.com/OpenElementsLabs/spring-services`

### SNAPSHOT publish is skipped on non-main branches

- **Given** a developer pushes to a feature branch
- **When** the `snapshot.yml` workflow evaluates its trigger
- **Then** the SNAPSHOT publish workflow does NOT run
- **And** only the `build.yml` workflow runs (build + test only)

### SNAPSHOT publish fails if tests fail

- **Given** the project has a failing test
- **When** a commit is pushed to the `main` branch
- **Then** CI runs `mvn clean verify` which fails
- **And** no SNAPSHOT is published to GitHub Packages

## CI Build Validation

### PR triggers build and test

- **Given** a developer creates a pull request against `main`
- **When** the PR is opened or updated
- **Then** CI runs `mvn spotless:check` to validate code formatting
- **And** CI runs `mvn clean verify` to build and run tests
- **And** no artifacts are published

### Build fails on formatting violations

- **Given** a developer pushes code with formatting violations
- **When** CI runs `mvn spotless:check`
- **Then** the workflow fails fast before running tests
- **And** the PR check shows a failure

### Build fails on test failures

- **Given** a developer pushes code with a failing test
- **When** CI runs `mvn clean verify`
- **Then** the workflow fails
- **And** the PR check shows a failure

## Security

### `.env` file is excluded from version control

- **Given** the `.gitignore` file exists
- **When** a developer creates a `.env` file with credentials
- **Then** the file is not tracked by git
- **And** `git status` does not show `.env` as untracked

### CI workflows use minimal token permissions

- **Given** the `snapshot.yml` workflow is configured
- **When** the workflow runs
- **Then** the `GITHUB_TOKEN` has only `packages: write` and `contents: read` permissions

## Artifact Completeness

### Release artifacts include all required components

- **Given** the publication profile is active
- **When** `mvn -Ppublication deploy` runs
- **Then** the following artifacts are produced: main JAR, sources JAR, Javadoc JAR, CycloneDX SBOM
- **And** all artifacts have corresponding `.asc` GPG signature files (for releases)
