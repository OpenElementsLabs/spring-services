# Behaviors: Tenant feature module

## Module extraction & build

### Tenant package no longer in core

- **Given** the reactor is built
- **When** `spring-services-core` is packaged
- **Then** its jar contains **no** `com/openelements/spring/base/tenant/**` classes, and
  `spring-services-core` compiles with no reference to the `tenant` package.

### Core Javadoc build stays green

- **Given** the `-Pfull-build` profile runs the Javadoc build with `failOnWarnings`
- **When** `spring-services-core` Javadoc is generated
- **Then** it succeeds with no unresolved-reference warning (the former
  `{@link com.openelements.spring.base.tenant}` in `data/package-info.java` is now plain text).

### New module carries the tenant types

- **Given** the `spring-services-tenant` module is built
- **When** its jar is produced
- **Then** it contains `TenantService`, `AbstractMultitenantEntity`,
  `EntityWithMultitenantSupport`, `RepositoryWithTenantSupport`,
  `AbstractMultitenantDbBackedDataService`, `EnableTenant`, `TenantConfig`, and `TenantAutoConfiguration`.

### POM is Maven-Central compliant

- **Given** the release version (non-SNAPSHOT) is set
- **When** PomChecker's `check-maven-central` runs across the reactor
- **Then** `spring-services-tenant-<version>.pom` passes (explicit `<url>`, `<name>`,
  `<description>` present; inherited licenses/developers/scm).

### BOM manages the new module

- **Given** a consumer imports `spring-services-bom`
- **When** it declares `spring-services-tenant` without a `<version>`
- **Then** the version resolves from the BOM in lockstep with the other modules.

## Self-activation

### Feature self-activates when the module is present

- **Given** an application with `spring-services-tenant` on the classpath and no explicit
  `@EnableTenant` / `@Import(TenantConfig.class)`
- **When** the Spring context starts
- **Then** `TenantAutoConfiguration` is applied and a single `TenantService` bean is present.

### Feature absent when the module is absent

- **Given** an application that depends on `spring-services-core` but **not** `spring-services-tenant`
- **When** the Spring context starts
- **Then** no `TenantService` bean exists and the tenant types are not on the compile classpath.

### No double registration

- **Given** `spring-services-tenant` is on the classpath (self-activated)
- **When** the context starts
- **Then** exactly **one** `TenantService` bean is registered (the dropped `@ComponentScan` does not
  re-register the auto-configured bean).

### Everything-bundle stays transparent

- **Given** a consumer depends on `spring-services-all`
- **When** the context starts
- **Then** `TenantService` is present — the tenant feature is included exactly as before the split.

### Explicit opt-in still works

- **Given** an application on `spring-services-tenant` that annotates a config class with
  `@EnableTenant` (or `@Import(TenantConfig.class)`)
- **When** the context starts
- **Then** it starts successfully with a single `TenantService` bean (no conflict with
  auto-configuration).

## Preserved tenancy behavior (moved test)

### Current tenant is derived from the principal

- **Given** an authenticated request whose principal name is `tenant-A`
- **When** `TenantService.getCurrentTenant()` is called
- **Then** it returns `tenant-A`.

### Missing authentication fails fast

- **Given** no authentication bound to the request (or a blank principal name)
- **When** `TenantService.getCurrentTenant()` is called
- **Then** an `IllegalStateException` is thrown ("No tenant id found").

### Insert stamps the current tenant

- **Given** a tenant-scoped service extending `AbstractMultitenantDbBackedDataService` and current
  tenant `tenant-A`
- **When** a new entity is saved
- **Then** the persisted row's `tenantId` is `tenant-A` (assigned by the service, not the caller).

### Cross-tenant read is invisible

- **Given** a row owned by `tenant-B`
- **When** a caller authenticated as `tenant-A` looks it up by id through the tenant-aware service
- **Then** the result is empty — indistinguishable from a non-existent row.

### Persist guard rejects a missing tenant id

- **Given** an `AbstractMultitenantEntity` with no `tenantId` set
- **When** it is flushed to the database
- **Then** the `@PrePersist`/`@PreUpdate` guard aborts the write (fails fast) rather than persisting
  an unowned row.
