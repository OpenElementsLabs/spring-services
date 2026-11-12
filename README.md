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
`spring-services-search`, `spring-services-dbbackup`, `spring-services-scim`,
`spring-services-tenant`, `spring-services-storage`, `spring-services-altcha`) without versions:

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
[`docs/releases/upgrade-to-1.3.md`](docs/releases/upgrade-to-1.3.md).

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

The **object store** (`spring-services-storage`) opts in the same way, but with a choice rather than a
switch: the module ships three implementations and `openelements.storage.type` says which one to
register. With the property unset no `ObjectStore` bean exists at all.

```properties
# S3 or any S3-compatible endpoint
openelements.storage.type=s3
openelements.storage.s3.endpoint=https://s3.eu-central-1.amazonaws.com   # required for type=s3
openelements.storage.s3.bucket=my-objects                                # required
openelements.storage.s3.access-key=${S3_ACCESS_KEY}                      # required
openelements.storage.s3.secret-key=${S3_SECRET_KEY}                      # required
openelements.storage.s3.region=eu-central-1                              # optional, default us-east-1

# …or a local directory
openelements.storage.type=file
openelements.storage.file.root=/var/lib/my-app/objects                   # required for type=file

# …or on the heap, for tests and local development only — objects do not survive a restart
openelements.storage.type=memory
```

Declaring your own `ObjectStore` bean makes the library back off, whatever `type` says.

**Bot protection** for public forms (`spring-services-altcha`) is off by default as well. It uses
[ALTCHA](https://altcha.org), a self-hosted proof of work without an external service or cookies.
When enabled, the module serves challenges for the widget at the challenge path (anonymous `GET` only,
through its own filter chain), and the application's form endpoint calls
`AltchaService.requireVerified(payload)`. A failed check is answered with `400` and
`{"code": "altcha-missing", "message": "…"}` in the language of the request.

```properties
openelements.altcha.enabled=true
openelements.altcha.hmac-key=${ALTCHA_HMAC_KEY}               # required when enabled, >= 32 characters
openelements.altcha.challenge-path=/api/altcha/challenge     # optional, this is the default
openelements.altcha.expires=PT10M                            # optional
openelements.altcha.cost=5000                                # optional, PBKDF2 iterations per attempt
openelements.altcha.key-prefix-length=2                      # optional
```

If a feature stays disabled, none of its beans are created and no connection settings are needed.
Secrets (`master-key`, `api-token`, `hmac-key`) must come from environment variables or secret management, never
from committed configuration.

## Features

- **JWT / OAuth2 Resource Server** — Standard Spring Security JWT validation with `roles`-claim
  to `ROLE_*` mapping in addition to the default `SCOPE_*` mapping from `scope`/`scp`.
- **API Key Authentication** — `X-API-Key` header authentication via a custom Spring Security
  filter that runs ahead of the bearer-token filter. Keys (`crm_` prefix + 48 random alphanumeric
  characters from `SecureRandom`) are stored as a SHA-256 hash plus a short display prefix; the
  raw key is returned exactly once at creation. API keys grant **read-only** access
  (`GET` / `HEAD` / `OPTIONS`) — mutating operations require a JWT.
- **Problem Details (RFC 9457)** — Every error response is `application/problem+json` with a
  stable `type` URI and a `traceId`. Covers the security `401`/`403`, validation, malformed
  bodies, unhandled exceptions and errors the servlet container raises on its own. A `401` still
  carries its RFC 7235 §3.1 `WWW-Authenticate` header (`Bearer` on the JWT chain,
  `ApiKey realm="external"` on the external chain). See [Problem details](#problem-details) below.
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
- **Database Reachability Check** — `DbHealthService.isDatabaseReachable()` borrows a connection
  from the pool and executes a `SELECT 1` round-trip, so an application whose database died after
  startup reports `DOWN` instead of a bean-level "up". Never throws; failures (including a pool
  connection-acquisition timeout) are logged and returned as `false`. The library ships no endpoint
  and no status enum — path, authorization and response shape stay with the application.
- **Lifecycle Events** — `OnObjectCreate`, `OnObjectUpdate` and `OnObjectDelete` are published
  synchronously inside the originating transaction; consumers opt into post-commit behaviour
  with `@TransactionalEventListener`. `OnObjectUpdate` carries the state before the update
  (`getPrevious()`) when the publisher knows it, and the data services publish no event for an
  update that changed nothing. Typed listeners (`OnObjectUpdate<BookDto>`) receive only their own
  DTO type.
- **Application Build & SBOM Info** — `ApplicationInfoService` answers *"which build is running,
  and what is it made of?"* from build-produced metadata: artifact coordinates and Git commit
  (from Spring Boot's `BuildProperties` / `GitProperties`) plus a parsed CycloneDX SBOM.
  `getApplicationInfo()` returns an immutable snapshot (coordinates, Git info, SBOM *summary*) and
  never returns `null` or throws when the build produced no metadata; `findSbom()` returns the
  full component list. The Git commit is read from `git.properties`, falling back to `build.commit`
  for container builds with no `.git` directory (`git.properties` wins on conflict). No build
  timestamp is exposed (it would be a fixed reproducible-build constant), no new dependency is
  added, and the SBOM is read once at startup. Configured via `openelements.info.sbom.enabled`
  (default `true`) and `openelements.info.sbom.location` (blank = autodetect, matching Spring
  Boot's `SbomEndpoint` probe order). The bean works even in an application without JPA, and can
  be overridden by declaring your own `ApplicationInfoService`.
  **Security:** the library exposes *no* REST endpoint — an SBOM is a precise attack-surface map
  (the exact version of every dependency), so any application-defined info/SBOM endpoint must sit
  behind an admin-level guard.
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

## Problem details

Every error response the library produces or influences is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
problem: `Content-Type: application/problem+json` with a stable, machine-readable `type` URI. The
format follows the GovStack API Design Guide, chapter 11, so an application can implement a GovStack
building block without writing an error layer of its own.

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://open-elements.com/problems/spring-services/duplicate-contact",
  "title": "Duplicate contact",
  "status": 409,
  "detail": "A contact with this e-mail address already exists",
  "instance": "/api/contacts",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "existingContactId": "0b6f3c1e-8d9a-4a57-9a43-1b0d1f3e7c21"
}
```

**Clients compare the full `type` URI.** `status` is too coarse to branch on and `title` is prose.
Slugs are published contract, as binding as a REST path.

SCIM (`/scim/v2/**`) is deliberately untouched: RFC 7644 defines its own error envelope.

### Configuration

```properties
openelements.problem-details.enabled=true
openelements.problem-details.type-base=https://open-elements.com/problems/spring-services/
openelements.problem-details.include-instance=true
```

`type-base` must be an absolute URI whose path ends with `/`; anything else fails start-up with a
message naming the property. It applies to **all** types — the library's and the application's — so
an application that publishes its own problem documentation sets its own base and the library's types
move under it.

`include-instance=false` drops the `instance` member. The query string is never included either way,
because it routinely carries personal data.

`enabled=false` restores the behaviour from before this feature in full: the previous JSON `401`, an
empty `403`, Spring Boot's `BasicErrorController`, and the ALTCHA module's `{code, message}` body.
The trace filter keeps running, so `traceId` stays in the log MDC regardless.

### Standard types

`StandardProblemType` has one constant per HTTP error status, with slugs frozen as literals (RFC 9110
names, kebab-cased — not Spring's constant names, which have been renamed between versions), plus
four that describe a cause rather than a status:

| Slug | Status | When |
| --- | --- | --- |
| `validation-failed` | 400 | one or more request-body fields failed validation; carries `errors` |
| `malformed-request` | 400 | the body could not be parsed at all |
| `invalid-parameter` | 400 | a query, path or header parameter is missing or unusable |
| `internal-error` | 500 | an unexpected failure |

A status without a constant — `418`, or a code outside the registry — yields `http-<code>`.

### Producing a problem

Define the application's types, then throw:

```java
public enum CrmProblemType implements ProblemType {

  DUPLICATE_CONTACT("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact");

  private final String slug;
  private final HttpStatus status;
  private final String title;

  CrmProblemType(String slug, HttpStatus status, String title) {
    this.slug = slug;
    this.status = status;
    this.title = title;
  }

  @Override public String slug() { return slug; }
  @Override public HttpStatus status() { return status; }
  @Override public String title() { return title; }
}

throw new ProblemException(
    CrmProblemType.DUPLICATE_CONTACT,
    "A contact with this e-mail address already exists",
    Map.of("existingContactId", existing.id()));
```

No handler is needed. `ProblemException` is not `final`, so a domain exception can extend it. An
exception annotated `@ResponseStatus`, and a `ResponseStatusException`, are rendered as the problem
for their status.

Call `ProblemType.validate(type)` from a test over `values()` — an enum bypasses the validation that
`ProblemType.of(...)` performs, and a typo in a slug is a published contract error.

### Validation errors

Request-**body** failures become `errors` entries whose `pointer` addresses the request the client
sent, honouring `@JsonProperty` and `spring.jackson.property-naming-strategy`:

```json
{
  "type": "https://open-elements.com/problems/spring-services/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "errors": [
    { "pointer": "/address/postal_code", "message": "must match \"[0-9]{5}\"" }
  ]
}
```

Query, path and header failures are described in `detail` instead and carry **no** `errors` entry: a
JSON pointer addresses a location in a document, and a parameter has none (Guide §11.4).

| Failures | Type | Members |
| --- | --- | --- |
| body only | `validation-failed` | `errors[]` |
| parameters only | `invalid-parameter` | `detail` |
| both | `validation-failed` | `errors[]` and `detail` |

> **Rejected values are never echoed by the library** — not in `detail`, not in an `errors` entry.
> A Bean Validation message template that uses `${validatedValue}` *will* echo it, because the
> interpolated message is passed through verbatim. That is the developer's choice.

### Overriding

An application `@ExceptionHandler` always wins; inject `ProblemFactory` to build the body from it:

```java
@RestControllerAdvice
class SdkExceptionHandler {

  private final ProblemFactory problems;

  SdkExceptionHandler(ProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(SdkTimeoutException.class)
  ProblemDetail handle(SdkTimeoutException e, HttpServletRequest request) {
    return problems.create(StandardProblemType.SERVICE_UNAVAILABLE, "Upstream timed out", request);
  }
}
```

Three override points, in increasing order of reach:

- **An advice for an exception the library does not map** — nothing to watch out for.
- **An advice for a *standard MVC* exception** (`MethodArgumentNotValidException`, say) — give it an
  explicit `@Order`. The library's advice sits at `LOWEST_PRECEDENCE`, which is also the default for
  an advice without one, and the winner on a tie depends on bean registration order.
- **Your own `ResponseEntityExceptionHandler` subclass** — the library's advice is then not
  registered at all. Unexpected exceptions still become `500 internal-error`.

Declaring a `ProblemFactory` bean replaces the body everywhere, including the `401` and `403` written
by the security filter chain. A `TraceIdResolver` bean replaces where the trace ID comes from.

### Trace ID

Every problem carries a `traceId`, which is also in the SLF4J MDC under `traceId` for the duration of
the request. A valid incoming W3C `traceparent` header is honoured, so a caller that already traces
its request sees its own ID; otherwise one is generated. Add it to the log pattern:

```properties
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-}]
```

A `5xx` is logged at `ERROR` with its exception and that trace ID; the client only ever receives
`"An unexpected error occurred."` — no exception message, no class name, no stack trace.

### No HTML error pages

Error responses are `application/problem+json` regardless of the request's `Accept` header, including
for a browser asking for `text/html`. This library serves APIs; an application that needs HTML error
pages must supply its own `ErrorController`.

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
├── spring-services-scim            — SCIM 2.0 Users provider (opt-in via openelements.scim.token)
├── spring-services-tenant          — row-level multi-tenancy (self-activates on the classpath)
├── spring-services-storage         — object store: S3, file, in-memory (opt-in via openelements.storage.type)
├── spring-services-all             — everything bundle (depends on all modules; no config of its own)
└── spring-services-bom             — bill of materials for lockstep versioning
```

Each optional feature module ships its own `@AutoConfiguration` guarded by `@ConditionalOnClass`, so
it self-activates when present and never pulls its heavy dependency into a consumer that skips it.

`spring-services-storage` is guarded differently, because `@ConditionalOnClass` cannot help there: its
three `ObjectStore` implementations are all on the classpath at once, so nothing about the classpath
distinguishes them. `openelements.storage.type` makes the choice explicit instead, and an application
that declares its own `ObjectStore` overrides it.

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
