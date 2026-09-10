# Upgrade prompt: spring-services 1.4.0 optional-module changes

`spring-services` 1.4.0 brings five independent changes. Every section below stands on its own —
apply only the ones you need:

| Change | Spec | Nature |
| --- | --- | --- |
| Optional **SCIM 2.0 Users provider** | 015 | new optional module, off by default, two additive columns |
| **Multi-tenancy moved** to its own module | 016 | build-coordinate change only |
| **Caller role lookup** `AuthService.getRoles()` | 017 | purely additive API |
| **Application build & SBOM info** `ApplicationInfoService` | 018 | purely additive API; needs build wiring to produce data |
| **Database reachability check** `DbHealthService` | 021 | purely additive API |

Plus one transitive dependency change, described at the end: `swagger-annotations-jakarta` moves from
2.2.29 to 2.2.47.

## SCIM 2.0 Users provider (spec 015)

`spring-services` 1.4.0 adds an **optional** SCIM 2.0 Users service-provider module,
`spring-services-scim`. It lets an external identity provider (Authentik, or any RFC 7644 client)
push-provision users into the library's existing `UserEntity` over an isolated, bearer-token-secured
`/scim/v2/**` surface.

The feature is **off by default**: unless `openelements.scim.token` is configured, the module ships
nothing (no filter chain, no endpoints, no beans) and there is nothing to migrate. This guide applies
only to consumers who want to turn SCIM on.

> This is not a Java-API break. It adds two additive columns to the `users` table and a reserved
> service-principal row; both are backward-compatible. As with every prior spec the library ships the
> migration SQL below — you apply it through your own Flyway/Liquibase timeline (spec 013 contract).

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

## Prompt

You are working inside a Spring Boot service that depends on `spring-services` and wants to enable
SCIM user provisioning.

### 1. Depend on the SCIM module

If you use `spring-services-all`, the SCIM module is already on the classpath — skip to step 2.
Otherwise add it (version managed by `spring-services-bom`):

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-scim</artifactId>
</dependency>
```

### 2. Apply the database migration

Add a new versioned migration to your own Flyway/Liquibase timeline:

```sql
-- Vx__scim_users_provider.sql
ALTER TABLE oe_spring_services.users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oe_spring_services.users ADD COLUMN deleted_at TIMESTAMPTZ;

-- Reserved SCIM service principal (fixed UUID, inactive) — the audit actor for SCIM writes.
INSERT INTO oe_spring_services.users (id, user_name, name, active, deleted, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000cf', 'scim', 'SCIM Provisioning', FALSE, FALSE,
        now(), now())
ON CONFLICT (id) DO NOTHING;
```

(The library also creates the service-principal row at startup if it is missing, mirroring the System
user; the `INSERT` above makes it explicit for environments that run with `ddl-auto=validate/none`.)

### 3. Configure the SCIM token

Supply the static shared-secret bearer token your IdP will send on every SCIM request. It is a
high-value secret — provide it from a secret manager, never in plaintext config, and never log it:

```properties
openelements.scim.token=${SCIM_TOKEN}
```

Point your IdP's SCIM provider at `https://<your-host>/scim/v2` with the same token as its bearer
credential. Rotation = change this property and the IdP token together; there is no server-side state.

### What you get

- `POST/GET/PUT/DELETE /scim/v2/Users` mapped onto `UserEntity` (correlated with OIDC JIT-login by
  `externalId`; SCIM never writes `sub`). `POST` of an existing `externalId`/`userName` returns
  `409 uniqueness`; the IdP recovers via filter + `PUT`.
- Discovery endpoints (`ServiceProviderConfig`, `ResourceTypes`, `Schemas`).
- A dedicated `/scim/v2/**` security chain, fully isolated from the JWT and API-key chains — the SCIM
  token cannot be used against `/api/**` and vice versa.

### Guard rails / Don't do this

- `DELETE` is a **soft delete** (`active=false`, `deleted=true`, `deleted_at=now`) — it does **not**
  scrub PII. Foreign keys from audit-log and comments stay intact; GDPR erasure is a separate,
  deferred module. Do not assume SCIM `DELETE` satisfies a data-subject erasure request on its own.
- Groups are **not** implemented in this slice: `GET /scim/v2/Groups` returns an empty list and every
  group write returns `501 Not Implemented`. Do not enable group provisioning in your IdP yet.
- User `PATCH` is not implemented (`501`); Authentik updates via `PUT` full-replace.
- Do not point your **application's own** entities at the reserved SCIM principal UUID.

## Multi-tenancy moved to `spring-services-tenant` (spec 016)

The multi-tenancy abstractions (`TenantService`, `AbstractMultitenantEntity`,
`AbstractMultitenantDbBackedDataService`, `RepositoryWithTenantSupport`, `EnableTenant`,
`TenantConfig`, …) previously lived in `spring-services-core`. In 1.4.0 they move, unchanged, into a
new optional feature module `spring-services-tenant`. No API, schema, or runtime-behaviour change to
the tenancy logic — this is a build-coordinate change only.

- **`spring-services-all` consumers: nothing to do.** The everything-bundle now depends on
  `spring-services-tenant`, so the feature is present exactly as before.
- **À-la-carte consumers who used tenant types via `spring-services-core`:** add the module
  (version managed by the BOM):

  ```xml
  <dependency>
      <groupId>com.open-elements</groupId>
      <artifactId>spring-services-tenant</artifactId>
  </dependency>
  ```

The feature self-activates on classpath presence (`TenantAutoConfiguration`); `@EnableTenant` and
`@Import(TenantConfig.class)` continue to work for explicit opt-in. There is no database migration.

## Caller role lookup via `AuthService.getRoles()` (spec 017)

`spring-services-core` 1.4.0 adds a way to ask **which roles the current caller has** from inside
business logic, so applications no longer re-implement Spring Security's `ROLE_` prefix convention
themselves. This is **purely additive** — no existing type, method signature, produced authority
string, or runtime behaviour changes, and there is nothing to migrate. Adopt it only where it helps.

### What is new

- `AuthService.getRoles()` returns an immutable, prefix-free `Set<String>` of the current caller's
  role names, directly comparable against the `Roles` constants. It **degrades closed**: a missing
  or unauthenticated security context yields an empty set rather than throwing.
- `AuthService.findAuthentication()` returns `Optional<Authentication>` — the absent-state-tolerant
  companion to the existing throwing `getAuthentication()` (which is unchanged and **not**
  deprecated).
- `Roles.AUTHORITY_PREFIX` (`"ROLE_"`), `Roles.ROLE_API_KEY` (`"API_KEY"`) and `Roles.ROLE_ANONYMOUS`
  (`"ANONYMOUS"`) — declared constants for names the library already produces at runtime.

### Replace hand-rolled prefix checks

```java
// before — the application re-implements the prefix convention
private static final String IT_ADMIN_AUTHORITY = "ROLE_" + Roles.ROLE_IT_ADMIN;
boolean isItAdmin = authService.getAuthentication().getAuthorities().stream()
    .map(GrantedAuthority::getAuthority)
    .anyMatch(IT_ADMIN_AUTHORITY::equals);

// after
boolean isItAdmin = authService.getRoles().contains(Roles.ROLE_IT_ADMIN);
```

Applications carrying a `boolean itAdmin` through a request-context record can carry the
`Set<String>` snapshot instead, so adding a second role check no longer changes a signature.

### Guard rails / Don't do this

- **`getRoles()` answers *which* names, not *how* the caller authenticated.** A JWT `roles` claim
  containing `API_KEY` or `ANONYMOUS` produces exactly that role name. Determine the mechanism from
  the `Authentication` type, never from a role name.
- **An anonymous caller holds `{ANONYMOUS}` — a non-empty set.** A guard of the shape "caller has at
  least one role ⇒ allow" would admit unauthenticated callers. Only ever ask whether a **specific**
  role is present.
- **Not a replacement for `@RequiresItAdmin` & co.** Declarative endpoint guards and the filter
  chains remain the enforcement mechanism; `getRoles()` informs business decisions behind an
  already-guarded endpoint.
- **IdP-side role names must match the `Roles` constants character for character** — comparison is
  case-sensitive, exactly as Spring Security's own `hasRole(...)`.

## Application build & SBOM info via `ApplicationInfoService` (spec 018)

`spring-services-core` 1.4.0 adds a read-only service that answers **"which build is running, and
what is it made of?"** — artifact coordinates, the Git commit, and a parsed CycloneDX SBOM. It is
**purely additive**: no existing type or behaviour changes, no new dependency, no REST endpoint, and
it works without JPA (its own auto-configuration, guarded by `@ConditionalOnMissingBean` so you can
supply your own implementation).

### What is new

```java
ApplicationInfo info = applicationInfoService.getApplicationInfo();
info.group();      info.artifact();   info.version();   info.name();
info.git();        // GitInfo: commitId, shortCommitId, branch, tag, dirty, commitTime
info.sbom();       // SbomSummary: bomFormat, specVersion, serialNumber, application,
                   //              componentCount, licenses

// the full flat component list, only when you need it
Optional<SbomDocument> sbom = applicationInfoService.findSbom();
```

Every field is nullable and every read is non-throwing: with nothing wired, you get
`ApplicationInfo.empty()` — all `null` — rather than an exception. A malformed or non-CycloneDX SBOM
yields an empty result, not a failure.

### The build wiring you have to add — otherwise it reports nothing

This is the part the library cannot do for you: it *reads* three files that a default Maven build
does not produce. All three belong in your **application** POM, not in a shared parent.

**1. Coordinates and the container-build commit** — `build-info` writes
`META-INF/build-info.properties`, which is Spring Boot's default location:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>build-info</id>
            <goals><goal>build-info</goal></goals>
            <configuration>
                <additionalProperties>
                    <!-- Surfaces as build.commit and is the fallback Git source. Feed it from the
                         CI/Docker build argument, since an in-container build has no .git. -->
                    <commit>${env.GIT_COMMIT}</commit>
                </additionalProperties>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**2. Git metadata, where `.git` exists** — enable the properties file in the application:

```xml
<plugin>
    <groupId>io.github.git-commit-id</groupId>
    <artifactId>git-commit-id-maven-plugin</artifactId>
    <configuration>
        <!-- java-parent sets this to false on purpose: a /git.properties shipped by a library
             collides on the consumer's classpath, where only the first jar's copy is ever read.
             An application is the last consumer, so it may and should write its own. -->
        <generateGitPropertiesFile>true</generateGitPropertiesFile>
    </configuration>
</plugin>
```

The plugin's default output is `${project.build.outputDirectory}/git.properties`, which is exactly
where Spring Boot looks (`spring.info.git.location` defaults to `classpath:git.properties` — note:
**not** under `META-INF/`).

**3. The SBOM** — `java-parent`'s `full-build` profile already runs `cyclonedx:makeBom`, but with the
plugin's default output (`target/bom.json`), which never enters the jar. Redirect it so it lands on
the classpath:

```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <configuration>
        <outputDirectory>${project.build.outputDirectory}/META-INF/sbom</outputDirectory>
        <outputName>application.cdx</outputName>
    </configuration>
</plugin>
```

If `.git` is absent in your Docker build (a `.dockerignore` that excludes it is the common case), pass
the hash in instead and let `build.commit` carry it:

```dockerfile
ARG GIT_COMMIT
RUN ./mvnw -B -DskipTests -Denv.GIT_COMMIT=$GIT_COMMIT package
```

### Where the values come from, and who wins

| Field | Source | Precedence |
| --- | --- | --- |
| `group`, `artifact`, `version`, `name` | `META-INF/build-info.properties` → `BuildProperties` | — |
| `git.*` | `classpath:git.properties` → `GitProperties` | **wins** |
| `git.commitId` (fallback) | `build.commit` from `build-info.properties` | used only when `git.properties` is absent |
| `sbom` | autodetected, in Spring Boot `SbomEndpoint` order: `classpath:META-INF/sbom/bom.json`, then `classpath:META-INF/sbom/application.cdx.json`, then `classpath:META-INF/native-image/sbom.json` | override with `openelements.info.sbom.location` |

### Configuration

```properties
# Both are optional; these are the defaults.
openelements.info.sbom.enabled=true
openelements.info.sbom.location=
```

`location` empty means autodetect (table above). Set `enabled=false` to skip SBOM reading entirely —
coordinates and Git info still work.

### Guard rails / Don't do this

- **There is deliberately no endpoint.** Path, authorization and response shape are yours. Which
  matters, because an SBOM is a **complete dependency inventory with versions** — publishing it
  unauthenticated hands an attacker your patch level. Put it behind the same authorization as your
  other administrative data.
- **There is no build timestamp, and that is not an oversight.** With reproducible builds
  `project.build.outputTimestamp` is a fixed constant (`java-parent` 1.3.0 sets it), so a "build time"
  would describe the parent release, not your build. For "when did this source state come into being",
  use `git.commitTime`. The SBOM's `metadata.timestamp` is not exposed for the same reason.
- **`ApplicationInfo` is a snapshot of classpath resources, not live state.** It cannot tell you
  whether a dependency was patched at runtime.
- **Don't expect the raw SBOM bytes.** This service exposes a *parsed* view; serving the unmodified
  file (what a compliance scanner consumes) and an Actuator `InfoContributor` are planned for a
  separate `spring-services-actuator` module.
- **`META-INF/build-info.properties`, `git.properties` and `META-INF/sbom/*` are single-slot
  classpath resources.** If two jars ship one, only the first is read. Never generate them in a
  profile shared with library modules — that is why `java-parent` disables `git.properties` for
  libraries.

## Database reachability check via `DbHealthService` (spec 021)

`spring-services-core` 1.4.0 registers one more read-only bean: `DbHealthService`, which answers
whether the configured `DataSource` is reachable **right now**. **Purely additive** — nothing to
migrate.

The point is what a bean-level check cannot tell you: an application that booted successfully can
still have a dead database — network partition, exhausted pool, restarted server, rotated
credentials. `isDatabaseReachable()` borrows a pooled connection and executes `SELECT 1`, so it
reports the truth instead of a cached "up".

```java
@GetMapping("/health")
HealthDTO health() {
  return new HealthDTO(
      HealthStatus.UP,
      dbHealthService.isDatabaseReachable() ? HealthStatus.UP : HealthStatus.DOWN);
}
```

### Guard rails / Don't do this

- **It never throws.** Any `SQLException` — and any `RuntimeException` from the pool, such as a
  connection-acquisition timeout — is logged (`WARN`, stack trace at `DEBUG`) and returned as
  `false`. Don't wrap it in `try`/`catch` to reach the same answer.
- **Nothing is cached: every call is a fresh round-trip.** Each call occupies one pooled connection
  for the duration of the query, and against a saturated pool it blocks for up to the pool's
  connection timeout. Size your pool with your probe interval in mind.
- **An unauthenticated, unthrottled health endpoint calling this is a way to occupy your pool.**
  Authorization, caching and rate limiting are the application's job — which is also why the library
  ships no endpoint and no status enum.
- **It is not an Actuator `HealthIndicator`.** No Actuator dependency is added; that integration is
  planned for the separate `spring-services-actuator` module and will reuse this service rather than
  re-probe.
- **It answers reachability only** — no schema check, no migration state, no replica lag.

## Dependency change: `swagger-annotations-jakarta` 2.2.29 → 2.2.47

1.4.0 builds on `com.open-elements:java-parent` 1.3.0, which manages the OpenAPI stack through
`springdoc-openapi-bom` **and** `swagger-bom`. `spring-services-core` no longer pins its own Swagger
version, so the annotations artifact it brings transitively moves from **2.2.29 to 2.2.47**.

Nothing to do in most applications. But if you pin Swagger or springdoc yourself, **align the whole
stack**: `swagger-core` calls annotation members that only exist in its own release, so a split
Swagger stack fails at runtime with `NoSuchMethodError` rather than merely losing a feature. To find
the versions matching a springdoc release, read `<swagger-api.version>` and `<swagger-ui.version>` in
that release's `springdoc-openapi` POM — `springdoc-openapi-bom` manages springdoc artifacts only.

One side effect worth knowing: with `project.build.outputTimestamp` now fixed centrally in
`java-parent` 1.3.0, a third party can rebuild a `spring-services` release byte-identically with
`./mvnw -Pfull-build clean verify`, without knowing any build flag.
