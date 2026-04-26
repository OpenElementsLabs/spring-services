# Project Features

## Overview

Spring Services is a reusable Spring Boot library that bundles common backend building blocks for
web applications: authentication, user management, tagging, webhook delivery, key/value settings
and multi-tenancy. It is published as a Maven artifact and consumed as a dependency in Spring Boot
applications. Importing the aggregate configuration `FullSpringServiceConfig` enables every
feature at once; individual feature configurations can be imported separately.

## Core Features

- **User Management** — JWT-driven user resolution that lazily provisions a local mirror of the
  authenticated subject and keeps `name`, `email` and `avatarUrl` in sync with the matching JWT
  claims. The avatar URL is exposed on `UserDto` and rendered directly by clients — no binary
  upload, storage or proxying happens in the library.
- **API Key Authentication** — Random API keys (`crm_` prefix + 48 random alphanumeric chars from
  `SecureRandom`); only their SHA-256 hash and a short display prefix are persisted. A custom
  Spring Security filter validates the `X-API-Key` header and grants **read-only** access
  (`GET` / `HEAD` / `OPTIONS`) — mutating operations require a JWT.
- **JWT / OAuth2 Resource Server** — Standard Spring Security JWT validation with custom claim
  mapping: the `roles` claim is exposed as `ROLE_*` authorities in addition to the default
  `SCOPE_*` mapping from `scope`/`scp`.
- **Tag Service** — CRUD for categorization tags (unique name, color, optional description). A
  `PreTagDeleteEvent` is published before deletion so that other features can detach references —
  or veto the deletion by throwing.
- **Webhook System** — Outbound webhook subscriptions. Data lifecycle events trigger a webhook
  dispatch *after the originating transaction commits*; HTTP POST is performed asynchronously via
  `RestClient` (10 s connect/read timeout) and the last response status is persisted back on the
  webhook row. A `ping` operation lets administrators test a subscription on demand.
- **Settings Service** — Simple string key / string value store for application-level
  configuration that lives in the database rather than in `application.yml`.
- **Multi-Tenancy** — Row-level isolation in a shared schema. Tenant id is taken from the
  authenticated principal; a JPA pre-persist guard fails fast if a row would be written without
  one. Tenant-aware base classes mirror the non-tenant data abstractions and keep cross-tenant
  reads/writes impossible by construction.
- **Event System** — Spring `ApplicationEvent`-based publish/subscribe for data lifecycle
  (`OnObjectCreate`, `OnObjectUpdate`, `OnObjectDelete`). Events are fired synchronously inside
  the originating transaction; consumers opt into post-commit behaviour with
  `@TransactionalEventListener`.
- **Aggregate / À-la-carte Configuration** — `FullSpringServiceConfig` imports every feature in
  one shot; applications that only need a subset can import the individual `*Config` classes
  directly.
- **Release Pipeline** — Local release to Maven Central via JReleaser (`release.sh`), automatic
  SNAPSHOT publishing to GitHub Packages on every push to `main`, and CI build validation
  (Spotless + tests) on PRs and pushes.
