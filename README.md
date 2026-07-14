# Open Elements Spring Boot Services

Reusable Spring Boot building blocks for backend applications: authentication (JWT + API key),
local user mirror, tags, key/value settings, outbound webhooks, and row-level multi-tenancy. The
library bundles each feature as an independent Spring `@Configuration`, so applications can opt
into the full stack with a single import or pick the parts they need.

## Requirements

- Java 21
- Spring Boot 3.5.x
- A relational database supported by Spring Data JPA (PostgreSQL is used in CI / integration tests)

## Installation

Releases are published to Maven Central; SNAPSHOTs are published to the Sonatype Central Portal
snapshot repository on every push to `main`.

`spring-services` is a **Maven multi-module reactor**. The bare `com.open-elements:spring-services`
coordinate is the reactor parent (a `pom`, no classes) — depend on one of the modules instead.

**Everything (drop-in):** `spring-services-all` bundles the core plus every optional feature module.

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-all</artifactId>
    <version><!-- latest released version --></version>
</dependency>
```

**À la carte:** import the BOM once, then declare `spring-services-core` plus only the feature
modules you need (`spring-services-slack`, `spring-services-mcp`, `spring-services-email`,
`spring-services-search`, `spring-services-dbbackup`) without versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.open-elements</groupId>
            <artifactId>spring-services-bom</artifactId>
            <version><!-- latest released version --></version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.open-elements</groupId>
        <artifactId>spring-services-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.open-elements</groupId>
        <artifactId>spring-services-slack</artifactId>
    </dependency>
</dependencies>
```

Each feature module self-activates when present on the classpath, so consumers pull only the
dependencies they use. Migrating from the pre-split `spring-services` artifact? See
[`docs/releases/upgrade-to-1.4.md`](docs/releases/upgrade-to-1.4.md).

To consume a `-SNAPSHOT` build, also declare the Central Portal snapshot repository (releases still
come from Maven Central):

```xml
<repositories>
    <repository>
        <id>central-portal-snapshots</id>
        <name>Central Portal Snapshots</name>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

## Quick Start

`spring-services` is an auto-configured **Spring Boot starter**: adding the dependency is enough.
You do **not** need `@Import(FullSpringServiceConfig.class)`, `@EntityScan`, or
`@EnableJpaRepositories` — the library registers its own package additively, so both your
application's entities/repositories and the library's are discovered automatically.

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

The library's tables live in a dedicated database schema, **`oe_spring_services`** (a single JPA
persistence unit — one `EntityManagerFactory`/`TransactionManager`, shared with your application's
entities). The library ships **no** runtime migrations: each release ships ready-to-apply SQL in its
upgrade guide under [`docs/releases/`](docs/releases), which you apply with your own
Flyway/Liquibase. See [`upgrade-to-1.3.md`](docs/releases/upgrade-to-1.3.md) for the schema-move
migration.

> **Note on `@EntityScan`.** If your application declares its own `@EntityScan("com.example…")`, that
> suppresses the additive fallback for entities — add the library root to it, e.g.
> `@EntityScan({"com.example…", "com.openelements.spring.base"})`.

Explicit wiring via `@Import(FullSpringServiceConfig.class)` remains supported and is now optional —
use it if you prefer to opt out of the auto-configuration. Applications that only need a subset can
import individual feature configurations directly — for example `SecurityConfig`, `TenantConfig`,
`TagConfig`, `WebhookConfig`, `SettingsConfig`, `ApiKeyConfig`. Multi-tenancy can also be enabled
declaratively via the `@EnableTenant` meta-annotation.

### Optional sidecar features (opt-in)

The two features that talk to an external sidecar — **Search** (Meilisearch) and **DB-Backup** — are
**disabled by default**, even when you import `FullSpringServiceConfig`. Importing the config does not
activate them; you enable each explicitly. When a feature is enabled, its host is **required** and a
missing/blank host **fails startup** (no silent `localhost` fallback):

```properties
# Search (Meilisearch) — off by default
openelements.meilisearch.enabled=true
openelements.meilisearch.host=https://meilisearch.internal:7700   # required when enabled
openelements.meilisearch.master-key=${MEILI_MASTER_KEY}
openelements.meilisearch.index-prefix=                            # optional, default ""

# DB-Backup sidecar — off by default
openelements.db-backup.enabled=true
openelements.db-backup.base-url=https://db-backup.internal:8081   # required when enabled
openelements.db-backup.api-token=${DB_BACKUP_API_TOKEN}          # required for authenticated calls
```

If a feature stays disabled, none of its beans are created and no connection settings are needed.
Secrets (`master-key`, `api-token`) must come from environment variables or secret management, never
from committed configuration.

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
  `name`, `email` and `avatarUrl` in sync with the matching JWT claims. Carries three SCIM-
  aligned identifying fields — `sub` (OIDC subject, nullable for SCIM-pre-provisioned rows),
  `externalId` (stable IdP-side id), and `userName` (RFC 7643 unique business key, with
  `preferred_username → email → sub → "user-<UUID>"` fallback chain) — with a tiered JIT-login
  lookup (`findBySub → findByExternalId → findByUserName → provision`) that opportunistically
  backfills the new identifiers on existing rows. The avatar URL points directly at the
  identity provider; clients render it without any proxying by this library. Concurrent
  first-logins for the same `sub` are coordinated through the database's unique constraint + a
  `REQUIRES_NEW`-transactional `UserProvisioner` helper that lets the outer transaction
  recover from the race without poisoning. See the `services.user` package documentation for
  the full design.
- **User Account Deactivation** — `UserEntity.active` is the canonical deactivation flag,
  honored uniformly across all authentication paths. JWT chain: `UserService.getCurrentUserEntity()`
  throws `AccessDeniedException` for `active = false` → HTTP `403 Forbidden`. Opaque-token chain
  (planned for spec 010): `PrincipalDirectory.resolveUser(...)` returns `active = false` →
  `BadOpaqueTokenException` → HTTP `401 Unauthorized`. JIT provisioning of a SCIM-pre-provisioned
  inactive user is also blocked — no `sub` is ever written onto a deactivated row.
- **`PrincipalDirectory` port** — Bridge between the (planned) opaque-token validation layer
  and the local user mirror. Default implementation `UserEntityPrincipalDirectory` queries by
  `externalId` and returns the live `active` flag. Role/group population deferred to the
  SCIM-Provider spec.
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
- **Search** *(opt-in, off by default)* — Meilisearch-backed full-text search via a thin `RestClient`
  wrapper (`MeilisearchClient`). Enabled with `openelements.meilisearch.enabled=true`; `host` is then
  required. At startup the lib exchanges the master key for a scoped runtime key, applies declarative
  per-index settings, and runs a full reindex: the application contributes one `SearchIndexBootstrapStep`
  bean per index (streaming already-mapped documents) and an optional `IndexSettings` / `ScopedKeySpec`
  bean. The lib never sees domain types. `Highlighter` turns Meilisearch's `_formatted` output into
  HTML-safe highlighted fragments. Once enabled, an unreachable sidecar is skipped with a warning, so
  the feature is inert until Meilisearch is available. Connection settings bind from
  `openelements.meilisearch.*` (see *Optional sidecar features*).
- **DB-Backup client** *(opt-in, off by default)* — thin `RestClient` wrapper (`DbBackupClient`) for the
  db-backup-service sidecar: trigger backups, poll job status, list and stream backup artefacts.
  Enabled with `openelements.db-backup.enabled=true`; `base-url` is then required. Connection settings
  bind from `openelements.db-backup.*` (see *Optional sidecar features*).

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

### From 1.1.x to 1.2.x — SCIM Foundation user-model refactor

`spring-services` 1.2.0 extends `UserEntity` with three new identifying fields and an active
flag to prepare for SCIM 2.0 provisioning (the SCIM endpoints themselves will follow in a
later release). Consumers must run one Flyway migration when upgrading.

- **`UserInformation` record gained two fields**: `userName` and `externalId`. Direct callers
  that construct the record themselves (test factories, ad-hoc instantiations) need to pass
  the two new fields. The default value mapping `AuthService` applies on JIT login is
  `userName = preferred_username` (with `email → sub → "user-<UUID>"` fallback) and
  `externalId = sub`. The library is at `1.2.0-SNAPSHOT`; record extensions are tolerated.

- **`UserEntity` schema change.** Consuming apps' Flyway timeline gains one migration:

  ```sql
  -- Vx__scim_foundation_user_model.sql
  ALTER TABLE users ALTER COLUMN sub DROP NOT NULL;

  ALTER TABLE users ADD COLUMN external_id VARCHAR(255);
  ALTER TABLE users ADD CONSTRAINT users_external_id_uk UNIQUE (external_id);

  ALTER TABLE users ADD COLUMN user_name VARCHAR(255);
  UPDATE users
     SET user_name = COALESCE(email, sub, 'user-' || id::text)
   WHERE user_name IS NULL;
  ALTER TABLE users ALTER COLUMN user_name SET NOT NULL;
  ALTER TABLE users ADD CONSTRAINT users_user_name_uk UNIQUE (user_name);

  ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
  ```

  Existing rows: `active` defaults to `TRUE`, `external_id` and `user_name` are backfilled
  on next interactive login (`external_id ← jwt.sub`, `user_name ← preferred_username` with
  fallback). The Flyway script above also seeds `user_name` from `email` / `sub` to satisfy
  the `NOT NULL` constraint at migration time.

- **Deactivation surface.** `UserEntity.setActive(false)` blocks the user across all
  authentication paths from the very next request — no logout / token re-issue needed when
  flipped back. Use it from your SCIM deprovisioning handler.

- **`AuthService.getUserInformation()` now requires `preferred_username` for unambiguous
  user-name correlation.** If two users share the same email and the IdP omits
  `preferred_username`, both fall back to `userName = email` and the second JIT login fails
  with `IllegalStateException` (the library refuses to silently merge two identities). Resolution:
  configure the IdP to assert `preferred_username` for every user.

- **New `PrincipalDirectory` port** in `services.apitoken` (temporarily owned by this spec,
  superseded by the upcoming API Token Module spec). Default implementation
  `UserEntityPrincipalDirectory` resolves users live from `UserEntity` by `external_id`.
  Consumers can override the bean to plug in a custom source (e.g. cache, IdP live-call).

## Building

```bash
./mvnw clean verify       # build + run tests
./mvnw spotless:apply     # apply Google Java Format
./mvnw spotless:check     # verify formatting (also runs in CI)
```

## Project Layout

A Maven reactor: `spring-services` (parent, `pom`) aggregates the modules below. All classes stay
under `com.openelements.spring.base.*` regardless of the module that ships them.

```
spring-services/                    — reactor parent (packaging=pom)
├── spring-services-core            — all 7 @Entity + single persistence unit, the core
│                                      auto-configuration (SpringServicesCoreAutoConfiguration) and
│                                      the always-present features:
│                                        data/       — generic CRUD abstractions
│                                        events/     — lifecycle events
│                                        security/   — filter chains, JWT converter, AuthService
│                                          apikey/, user/
│                                        services/   — apikey, settings, tag, comment, audit,
│                                                      webhook, translation, system
│                                        tenant/, apitoken/
├── spring-services-slack           — Slack messaging (→ slack-api-client)
├── spring-services-mcp             — MCP server (→ MCP SDK)
├── spring-services-email           — SMTP email (→ spring-boot-starter-mail)
├── spring-services-search          — Meilisearch full-text search (RestClient, no extra dep)
├── spring-services-dbbackup        — db-backup sidecar client (RestClient, no extra dep)
├── spring-services-all             — everything bundle (depends on all modules; no config of its own)
└── spring-services-bom             — bill of materials for lockstep versioning
```

Each optional feature module ships its own `@AutoConfiguration` guarded by `@ConditionalOnClass`, so
it self-activates when present and never pulls its heavy dependency into a consumer that skips it.

## Release Process

### SNAPSHOT Publishing (automatic)

Every push to `main` triggers the `snapshot.yml` GitHub Actions workflow, which builds the project, runs all tests, and
publishes a SNAPSHOT artifact to the Sonatype Central Portal snapshot repository at:

```
https://central.sonatype.com/repository/maven-snapshots/
```

Publishing is gated on the POM version ending in `-SNAPSHOT`: the brief release-version commit that `release.sh`
pushes to `main` is skipped cleanly (not failed) rather than published. Authentication uses the same
`MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` secrets as the full release. No manual steps are required, and
SNAPSHOT publishing only happens on `main` — feature branches only run the build workflow.

### Full Release (tag-triggered)

Releases are published to Maven Central via JReleaser, executed by the `release.yml` GitHub Actions
workflow. The workflow runs whenever a `vA.B.C` tag is pushed: it verifies the POM version matches the
tag, builds and signs the artifacts, uploads them to Maven Central, and creates a GitHub Release with a
changelog. **The deployment runs in CI, not on your machine** — no local GPG key or Maven Central
credentials are required to cut a release.

#### Prerequisites (one-time, repository maintainer)

The workflow reads all credentials from GitHub Actions secrets. Configure these once under
**Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Maven Central portal username (token user) |
| `MAVEN_CENTRAL_PASSWORD` | Maven Central portal token |
| `GPG_PASSPHRASE` | GPG key passphrase |
| `GPG_PUBLIC_KEY` | GPG public key, ASCII-armored (`gpg --armor --export <KEY_ID>`) |
| `GPG_SECRET_KEY` | GPG private key, ASCII-armored (`gpg --armor --export-secret-key <KEY_ID>`) |

`GITHUB_TOKEN` is provided automatically by Actions and needs no setup.

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
2. Build, test, and assemble the publication artifacts locally with `mvn -Ppublication clean verify`
   (the same profile the release uses, so a broken Javadoc `@link`, a missing source jar, or an SBOM
   failure is caught here — before any tag is pushed)
3. Generate the release/upgrade documentation under `docs/` via Claude Code — best-effort: if the
   `claude` CLI is not installed the step logs a warning and continues, so it never blocks a release
4. Commit and push the release version (including the generated doc)
5. Create and push the `v1.0.0` tag — **this triggers the `release.yml` workflow**, which deploys to
   Maven Central and creates the GitHub Release
6. Set the project version to `1.1.0-SNAPSHOT`
7. Commit and push the next snapshot version

The actual Maven Central deployment happens asynchronously in CI — watch the **Actions** tab for the
"Release Artifacts" run. If any local step fails the script stops immediately (`set -e`); if it fails
after the tag has been pushed, the workflow can be re-run from the Actions tab.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
