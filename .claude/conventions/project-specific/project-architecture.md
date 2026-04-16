# Project Architecture

## Components

- **Entry Point** — `FullSpringServiceConfig` is an `@Import`-only class that pulls in every
  feature-level `@Configuration` (`SecurityConfig`, `TenantConfig`, `ApiKeyConfig`,
  `SettingsConfig`, `TagConfig`, `WebhookConfig`). Consumers can also import individual
  feature configs for à-la-carte usage.
- **Base Data Layer** (`com.openelements.spring.base.data`) — Three layers per domain object:
  DTOs (immutable transport, implement `WithId`), JPA entities (extend `AbstractEntity`: UUID PK,
  `@CreationTimestamp`, `@UpdateTimestamp`), and services (extend
  `AbstractDbBackedDataService<E, D>`, which performs the entity↔DTO mapping, distinguishes
  insert from update on `id() == null`, wraps mutations in `@Transactional`, and publishes
  lifecycle events).
- **Event System** (`com.openelements.spring.base.events`) — Spring `ApplicationEvent` subclasses
  `OnObjectCreate`, `OnObjectUpdate`, `OnObjectDelete` (all extend `GenericDataEvent<T>` which
  also captures the runtime DTO type for type-safe filtering). Events are published synchronously
  from `postSave` / `postDelete` hooks inside the originating transaction.
- **Security Layer** (`com.openelements.spring.base.security`) — Stateless filter chain. The
  custom `ApiKeyAuthenticationFilter` runs *before* Spring Security's standard
  `BearerTokenAuthenticationFilter`. API keys grant **read-only** access (`GET` / `HEAD` /
  `OPTIONS`) and are validated against a SHA-256 hash; JWTs grant full access and contribute
  `SCOPE_*` and `ROLE_*` authorities. Anonymous access is restricted to `/api/health/**` and
  Swagger UI / `/v3/api-docs/**`.
- **Domain Services** (`com.openelements.spring.base.services`) — Each feature follows the same
  shape (entity + repository + DTO + service + `@Configuration`):
  - **API keys** — generate (`crm_` prefix + 48 chars from `SecureRandom`), hash, lookup by hash,
    delete; raw key is returned only once.
  - **Tags** — CRUD plus `PreTagDeleteEvent` so other features can detach references or veto
    deletion by throwing.
  - **Settings** — flat string-key/string-value store via `SettingsDataService`.
  - **Webhooks** — see "Webhook Flow" below.
- **Multi-Tenancy** (`com.openelements.spring.base.tenant`) — Row-level isolation in a shared
  schema. `AbstractMultitenantEntity` adds a non-null `tenantId` column with a `@PrePersist` /
  `@PreUpdate` guard. `RepositoryWithTenantSupport` deliberately does *not* extend
  `JpaRepository` — only tenant-filtered finders are exposed, so cross-tenant reads are
  impossible by construction. `AbstractMultitenantDbBackedDataService` injects the current
  tenant from `TenantService.getCurrentTenant()` (which reads it from the authenticated
  principal's name).

## Component Communication

- **Inbound:** HTTP requests reach the filter chain → `ApiKeyAuthenticationFilter` →
  `BearerTokenAuthenticationFilter` → controllers → services → repositories → JDBC.
- **Intra-process events:** services publish `ApplicationEvent`s; listeners receive them
  in-process via Spring's `ApplicationEventPublisher`. Listeners that should run only after the
  transaction commits use `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Outbound:** webhook delivery uses Spring's `RestClient` with a `SimpleClientHttpRequestFactory`
  configured with 10 s connect/read timeout. Calls run on Spring's `@Async` executor; the
  resulting status code is persisted back on the webhook row.

## High-Level Architecture

```mermaid
graph LR
    Client -->|JWT or X-API-Key| FilterChain
    FilterChain -->|ApiKeyAuthenticationFilter| ApiKeyAuth
    FilterChain -->|BearerTokenAuthenticationFilter| JwtAuth
    ApiKeyAuth --> Controller
    JwtAuth --> Controller
    Controller --> Service
    Service -->|JPA| Database[(PostgreSQL)]
    Service -->|publishEvent| Events[OnObjectCreate/Update/Delete]
    Events -->|@TransactionalEventListener AFTER_COMMIT| WebhookListener
    WebhookListener -->|@Async| WebhookSender
    WebhookSender -->|HTTP POST| ExternalEndpoint
    WebhookSender -->|persist status| Database
    Service -->|getCurrentTenant| TenantService
    TenantService -->|principal name| AuthService
```

## Webhook Flow

1. A data service `save(...)` or `delete(...)` succeeds and publishes `OnObjectCreate`,
   `OnObjectUpdate` or `OnObjectDelete` synchronously inside its own transaction.
2. `WebhookEventListener` receives the event with
   `@TransactionalEventListener(phase = AFTER_COMMIT)` — so the webhook is only sent once the
   originating transaction has actually been committed (no phantom notifications on rollback).
3. `WebhookSender` looks up all active webhook subscriptions and calls `sendAndTrack` for each.
4. The HTTP POST runs on a Spring `@Async` thread via `RestClient`; the response status code
   (or a sentinel for connection/timeout failures) is written back to the webhook row.
5. A `ping(UUID)` operation is exposed to test a single subscription on demand.

## Multi-Tenancy Flow

1. The authenticated principal's name (`Jwt.sub` or the API key entity's name) is used as the
   tenant id by `TenantService.getCurrentTenant()`.
2. `AbstractMultitenantDbBackedDataService` reads the tenant id once per operation:
   - On insert: assigns it to the new entity's `tenantId` column before save.
   - On update / read / delete: looks up the row via
     `RepositoryWithTenantSupport.findByIdAndTenantId`, treating cross-tenant rows as
     non-existent.
3. The JPA `@PrePersist` / `@PreUpdate` guard on `AbstractMultitenantEntity` is a safety net —
   it throws `IllegalStateException` if any code path reaches the database without a tenant id.

## Release & CI/CD Flow

- **PR / push** — `build.yml` runs `spotless:check` followed by `mvn clean verify`.
- **Push to `main`** — `snapshot.yml` runs the full build and then
  `./mvnw -Ppublication deploy`, publishing the SNAPSHOT to GitHub Packages.
- **Release** — `./release.sh <release-version> <next-snapshot-version>` sets the release version,
  builds, commits, stages artifacts (JAR + sources + Javadoc + SBOM via CycloneDX), invokes
  JReleaser to GPG-sign and publish to Maven Central via the Sonatype Central Portal, creates a
  GitHub Release with a conventional-commits changelog, then sets and commits the next
  `-SNAPSHOT` version.
