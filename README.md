# Open Elements Spring Boot Services

Reusable Spring Boot building blocks for backend applications: authentication (JWT + API key),
local user mirror, tags, key/value settings, outbound webhooks, and row-level multi-tenancy. The
library bundles each feature as an independent Spring `@Configuration`, so applications can opt
into the full stack with a single import or pick the parts they need.

## Requirements

- Java 21
- Spring Boot 3.3.x
- A relational database supported by Spring Data JPA (PostgreSQL is used in CI / integration tests)

## Installation

Releases are published to Maven Central; SNAPSHOTs are published to GitHub Packages on every push
to `main`.

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version><!-- latest released version --></version>
</dependency>
```

## Quick Start

The simplest way to enable every feature is to import `FullSpringServiceConfig`:

```java
@SpringBootApplication
@Import(FullSpringServiceConfig.class)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

Applications that only need a subset can import the individual feature configurations directly —
for example `SecurityConfig`, `TenantConfig`, `TagConfig`, `WebhookConfig`, `SettingsConfig`,
`ApiKeyConfig`. Multi-tenancy can also be enabled declaratively via the `@EnableTenant`
meta-annotation.

## Features

- **JWT / OAuth2 Resource Server** — Standard Spring Security JWT validation with `roles`-claim
  to `ROLE_*` mapping in addition to the default `SCOPE_*` mapping from `scope`/`scp`.
- **API Key Authentication** — `X-API-Key` header authentication via a custom Spring Security
  filter that runs ahead of the bearer-token filter. Keys (`crm_` prefix + 48 random alphanumeric
  characters from `SecureRandom`) are stored as a SHA-256 hash plus a short display prefix; the
  raw key is returned exactly once at creation. API keys grant **read-only** access
  (`GET` / `HEAD` / `OPTIONS`) — mutating operations require a JWT.
- **Uniform Authentication-Failure Responses** — Both filter chains share a single
  `JsonAuthenticationEntryPoint`. Every `401 Unauthorized` carries a stable JSON body
  `{"status":401,"error":"Unauthorized","message":"<reason>"}` and an RFC 7235 §3.1
  `WWW-Authenticate` header (`Bearer` on the JWT chain, `ApiKey realm="external"` on the
  external chain).
- **User Service** — Lazily provisions a local user row from the JWT subject (`sub`) and keeps
  `name`, `email` and `avatarUrl` in sync with the matching JWT claims. The avatar URL points
  directly at the identity provider; clients render it without any proxying by this library.
  Concurrent first-logins for the same `sub` are coordinated through the database's unique
  constraint + a `REQUIRES_NEW`-transactional `UserProvisioner` helper that lets the outer
  transaction recover from the race without poisoning. See the
  `services.user` package documentation for the full design.
- **Typed Authentication Surface** — `AuthService.getUserInformation()` returns
  `Optional<UserInformation>`: present for JWT-authenticated requests, empty for non-JWT
  principals (API keys, primitive auth). Callers must explicitly handle the empty case rather
  than receiving a sentinel value that could be silently persisted.
- **Tags** — CRUD service for color-coded labels. A `PreTagDeleteEvent` is published before
  deletion so other features can detach references — or veto the deletion by throwing.
- **Settings** — Plain string-key / string-value store via `SettingsDataService`, useful for
  configuration that lives in the database rather than in `application.yml`.
- **Webhooks** — Outbound webhook subscriptions. Data lifecycle events trigger a webhook dispatch
  *after the originating transaction commits*; HTTP POST is performed asynchronously via Spring
  `RestClient` (10 s connect/read timeout) and the last response status is persisted on the
  webhook row. A `ping` operation is exposed for testing a subscription on demand.
- **Multi-Tenancy** — Row-level isolation in a shared schema. Tenant id is taken from the
  authenticated principal; a JPA pre-persist guard fails fast if a row would be written without
  one. Tenant-aware base classes mirror the non-tenant data abstractions and make cross-tenant
  reads/writes impossible by construction.
- **Generic Data Layer** — Reusable abstractions (`AbstractEntity` with UUID primary key and
  audit timestamps, `AbstractDbBackedDataService` template) that wire CRUD, transactions and
  lifecycle events for any domain object.
- **Lifecycle Events** — `OnObjectCreate`, `OnObjectUpdate` and `OnObjectDelete` are published
  synchronously inside the originating transaction; consumers opt into post-commit behaviour
  with `@TransactionalEventListener`.
- **Search** — Meilisearch-backed full-text search via a thin `RestClient` wrapper
  (`MeilisearchClient`). At startup the lib exchanges the master key for a scoped runtime key,
  applies declarative per-index settings, and runs a full reindex: the application contributes one
  `SearchIndexBootstrapStep` bean per index (streaming already-mapped documents) and an optional
  `IndexSettings` / `ScopedKeySpec` bean. The lib never sees domain types. `Highlighter` turns
  Meilisearch's `_formatted` output into HTML-safe highlighted fragments. An unreachable sidecar is
  skipped with a warning, so the feature is inert until Meilisearch is available. Connection settings
  bind from `openelements.meilisearch.*`.

For per-package overviews, see the `package-info.java` files under
`src/main/java/com/openelements/spring/base/`.

## Upgrade Notes

### From 1.0.x to 1.1.x — Security Configuration Hygiene

`spring-services` 1.1.0 tightens the security stack to match Spring Boot best practices. Three
changes are visible to consumers; everything else is internal cleanup.

- **`AuthService.getUserInformation()` now returns `Optional<UserInformation>`** instead of
  `UserInformation` with a `"UNKNOWN"` sentinel for non-JWT principals. Migration:

  ```java
  // Before
  UserInformation info = authService.getUserInformation();

  // After
  UserInformation info = authService.getUserInformation()
      .orElseThrow(() -> new IllegalStateException("Not a JWT request"));
  // or, when you want to tolerate API-key / primitive auth:
  Optional<UserInformation> info = authService.getUserInformation();
  ```

- **`401 Unauthorized` responses changed shape.** Both the JWT and external API-key chains now
  return:

  ```json
  { "status": 401, "error": "Unauthorized", "message": "<reason>" }
  ```

  …with a `WWW-Authenticate` header (`Bearer` or `ApiKey realm="external"`). Consumers that
  parse the failure body must adapt; consumers that only check status codes are unaffected.

- **Connection pool sizing for first-login concurrency.** The new `UserProvisioner` runs the
  initial `INSERT` in a `REQUIRES_NEW` inner transaction, which holds two pool connections per
  thread for the duration of the provisioning hop (outer suspended + inner active). Size the
  HikariCP pool with the formula `peak_concurrent_first_logins × 2 + steady-state` to avoid
  deadlock under bulk-onboarding bursts. Steady-state traffic is unaffected — the second
  connection is only held during the brief provisioning window for a previously-unknown user.

No schema migration, no property rename, no API path change.

## Building

```bash
./mvnw clean verify       # build + run tests
./mvnw spotless:apply     # apply Google Java Format
./mvnw spotless:check     # verify formatting (also runs in CI)
```

## Project Layout

```
src/main/java/com/openelements/spring/base/
├── FullSpringServiceConfig.java   — Aggregate @Import of every feature config
├── data/                          — Generic CRUD abstractions
├── events/                        — Lifecycle events
├── security/                      — Filter chain, JWT converter, AuthService
│   ├── apikey/                    — X-API-Key authentication filter
│   └── user/                      — Local user mirror
├── services/
│   ├── apikey/                    — API key data layer
│   ├── search/                    — Meilisearch client, startup reindex, highlighter
│   ├── settings/                  — Key/value settings
│   ├── tag/                       — Tag CRUD + PreTagDeleteEvent
│   └── webhook/                   — Outbound webhook delivery
└── tenant/                        — Multi-tenancy primitives
```

## Release Process

### SNAPSHOT Publishing (automatic)

Every push to `main` triggers the `snapshot.yml` GitHub Actions workflow, which builds the project, runs all tests, and
publishes a SNAPSHOT artifact to GitHub Packages at:

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

If any step fails, the script stops immediately (`set -e`). If the failure occurs after the release version commit has
been pushed, manual recovery may be required.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
