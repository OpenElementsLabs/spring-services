# Open Elements Spring Boot Services

## Release Process

### SNAPSHOT Publishing (automatic)

Every push to `main` triggers the `snapshot.yml` GitHub Actions workflow, which builds the project, runs all tests, and publishes a SNAPSHOT artifact to GitHub Packages at:

```
https://maven.pkg.github.com/OpenElementsLabs/spring-services
```

No manual steps are required. SNAPSHOT publishing only happens on `main` — feature branches only run the build workflow.

### Full Release (manual)

Releases are published to Maven Central via JReleaser. The process is triggered locally using `release.sh`.

#### Prerequisites

1. Create `~/.jreleaser/config.toml` with the following content and fill in your credentials:

   ```toml
   # JReleaser configuration for local releases.
   #
   # IMPORTANT: The public and secret key values must be valid ASCII-armored PGP keys.
   # There must be a blank line between the "-----BEGIN ..." header and the key data.

   JRELEASER_GITHUB_TOKEN = "ghp_your_github_personal_access_token"

   JRELEASER_MAVENCENTRAL_USERNAME = "your_maven_central_username"
   JRELEASER_MAVENCENTRAL_TOKEN = "your_maven_central_token"

   JRELEASER_GPG_PASSPHRASE = "your_gpg_passphrase"

   JRELEASER_GPG_PUBLIC_KEY = """-----BEGIN PGP PUBLIC KEY BLOCK-----

   <your base64-encoded public key data>
   =XXXX
   -----END PGP PUBLIC KEY BLOCK-----"""

   JRELEASER_GPG_SECRET_KEY = """-----BEGIN PGP PRIVATE KEY BLOCK-----

   <your base64-encoded secret key data>
   =XXXX
   -----END PGP PRIVATE KEY BLOCK-----"""
   ```

   | Variable | Description |
   |---|---|
   | `JRELEASER_GITHUB_TOKEN` | GitHub personal access token (Fine-grained PAT with **Contents: Read and Write** permission on this repository) |
   | `JRELEASER_MAVENCENTRAL_USERNAME` | Maven Central portal username |
   | `JRELEASER_MAVENCENTRAL_TOKEN` | Maven Central portal token |
   | `JRELEASER_GPG_PASSPHRASE` | GPG key passphrase |
   | `JRELEASER_GPG_PUBLIC_KEY` | GPG public key (ASCII-armored) |
   | `JRELEASER_GPG_SECRET_KEY` | GPG private key (ASCII-armored) |

2. Ensure your GPG key is available and the passphrase matches.

#### Running a Release

```bash
./release.sh <release-version> <next-snapshot-version>
```

Example:

```bash
./release.sh 1.0.0 1.1.0-SNAPSHOT
```

This will:

1. Set the project version to `1.0.0`
2. Build and run all tests (`mvn clean verify`)
3. Commit and push the release version
4. Stage artifacts locally (JAR, sources, Javadoc, SBOM)
5. Run JReleaser to sign artifacts with GPG, upload to Maven Central, and create a GitHub Release with a changelog
6. Set the project version to `1.1.0-SNAPSHOT`
7. Commit and push the next snapshot version

If any step fails, the script stops immediately (`set -e`). If the failure occurs after the release version commit has been pushed, manual recovery may be required.