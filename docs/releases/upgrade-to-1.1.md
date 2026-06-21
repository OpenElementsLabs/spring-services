# Upgrade prompt: `com.open-elements:spring-services` 1.0.x → 1.1.0

`spring-services` 1.1.0 reshapes user identity around **SCIM 2.0 provisioning**. The `UserEntity`
gains `externalId`, `userName`, and an `active` flag; `sub` becomes nullable so SCIM can
pre-provision rows before the user ever logs in interactively. `UserService` resolves users through
a three-tier lookup (`sub → externalId → userName`), rejects deactivated users with HTTP `403`, and
provisions first-login rows in a race-safe inner transaction. `AuthService.getUserInformation()`
now returns an `Optional`, and `UserInformation` carries two new components. Authentication failures
across both filter chains now emit a **uniform JSON `401` body**. There are **no Spring Boot or
Testcontainers BOM bumps** (Spring Boot stays `3.5.14`, Testcontainers stays `2.0.5`).

The high-stakes part of this upgrade is a **`users`-table schema migration with a `NOT NULL`
backfill** — get the ordering wrong and the app will not start. The compile-breaking changes are
small and mechanical.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade to `1.1.0`. The mandatory parts are: a dependency bump, a `users`-table schema
migration (new columns + a `NOT NULL` backfill on `user_name`), and a handful of call-site fixes
where the consumer calls `AuthService.getUserInformation()` or constructs `UserInformation` /
`UserService` directly. Everything else is additive and safe to ignore.

### What changed in 1.1.0

#### Dependencies

- No version bumps. Spring Boot stays at `3.5.14`, Testcontainers stays at `2.0.5`. Do **not** change
  those coordinates as part of this upgrade.
- No new transitive runtime dependency. The new `JsonAuthenticationEntryPoint` uses the application's
  existing Jackson `ObjectMapper` (always present in a `spring-boot-starter-web` app).

#### Breaking: `AuthService.getUserInformation()` now returns `Optional<UserInformation>`

API-key-authenticated requests used to receive a sentinel `UserInformation("UNKNOWN", "UNKNOWN",
null, null)`; that sentinel is gone. Non-JWT requests now yield an empty `Optional`, forcing callers
to handle the "not a JWT request" case explicitly instead of silently persisting `"UNKNOWN"`.

```java
// Before (1.0.0)
UserInformation user = authService.getUserInformation();
return new Note(user.id(), body);

// After (1.1.0)
UserInformation user = authService.getUserInformation()
    .orElseThrow(() -> new IllegalStateException("Not a JWT-authenticated request"));
return new Note(user.id(), body);
```

Pick `.orElseThrow(...)`, `.map(...)`, or `.ifPresent(...)` to match the call site's intent. Do
**not** reintroduce an `"UNKNOWN"` sentinel.

#### Breaking: `UserInformation` record gained two components (4 → 6)

```java
// Before (1.0.0)
public record UserInformation(String id, String name, String email, String avatarUrl) {}

// After (1.1.0)
public record UserInformation(
    String id, String name, String email, String avatarUrl, String userName, String externalId) {}
```

- `userName` — the `preferred_username` claim, with a fallback chain
  (`preferred_username → email → sub → "user-<UUID>"`) applied by `AuthService`. Guaranteed
  non-null and non-blank for `AuthService`-produced records.
- `externalId` — the stable IdP-side identifier; mirrors `id` (the JWT `sub`) for IdPs that use the
  same value for OIDC `sub` and SCIM `externalId` (Authentik, Okta, Entra ID, Keycloak in default
  configs).

Any consumer code that **constructs** `new UserInformation(id, name, email, avatarUrl)` (most often
in tests or test fixtures) no longer compiles — add the two trailing arguments. Any **record
pattern** / destructuring with four components (`case UserInformation(var id, ...)`) must add the
two new components. Reading the record (`user.id()`, `user.email()`, …) is unaffected.

#### Breaking: `UserService` constructor gained a `UserProvisioner` parameter

```java
// Before (1.0.0)
public UserService(UserRepository userRepository,
                   ApplicationEventPublisher eventPublisher,
                   AuthService authService)

// After (1.1.0)
public UserService(UserRepository userRepository,
                   ApplicationEventPublisher eventPublisher,
                   AuthService authService,
                   UserProvisioner userProvisioner)
```

This only affects code that **constructs `UserService` by hand** (typically a unit test). Add a
`UserProvisioner` argument (it is a Spring `@Component`, so injected automatically in production —
nothing to wire). In tests, pass a real `UserProvisioner(userRepository)` or a mock. Applications
that obtain `UserService` via dependency injection need no change.

#### Breaking-light: deactivated users are now rejected with `403`

`getCurrentUserEntity()` and `getCurrentUser()` now check `UserEntity.isActive()` after resolving
the user and throw `org.springframework.security.access.AccessDeniedException` (→ HTTP `403`) when
the user is inactive. The check fires on every call. New `users` rows default to `active = true`, so
existing users are unaffected unless something sets `active = false`.

Two more behavioural notes on the same methods:

- They are **no longer `synchronized`** — first-login concurrency is now handled by an inner
  `REQUIRES_NEW` transaction plus a `findBySub` retry, not by a method-level lock. No consumer action
  required; do not re-add `synchronized`.
- If the three-tier lookup correlates a row whose existing `sub` disagrees with the request's `sub`
  (two identities colliding on `externalId`/`userName`, typically because the IdP omits
  `preferred_username` and both users fall back to the same email), an `IllegalStateException` is
  thrown rather than silently merging the identities. The fix is at the IdP (assert
  `preferred_username`), not in consumer code.

If you have integration tests asserting that an inactive user still gets through, or relying on
`getCurrentUserEntity()` being `synchronized`, update those expectations.

#### Breaking-light: uniform JSON `401` body on both filter chains

A new `JsonAuthenticationEntryPoint` is wired into both the JWT chain and the external API-key chain.
Every `401 Unauthorized` now returns a stable body plus a `WWW-Authenticate` header:

```json
{ "status": 401, "error": "Unauthorized", "message": "<reason>" }
```

- JWT chain sends `WWW-Authenticate: Bearer`.
- External API-key chain sends `WWW-Authenticate: ApiKey realm="external"`.

The previous API-key failure body was `{"error":"Invalid API key"}`. Any client or test asserting on
that old shape must be updated to the new envelope (the `message` field carries `"Invalid API key"`
for that case). This is wired automatically — no consumer configuration is needed beyond having an
`ObjectMapper` bean on the context (Spring Boot provides one by default).

#### Schema: `users` table — new columns and a `NOT NULL` backfill

`UserEntity` changed as follows (all on the `users` table):

| Column        | Before (1.0.0)            | After (1.1.0)                                  |
|---------------|---------------------------|------------------------------------------------|
| `sub`         | `varchar(255) NOT NULL`, unique | `varchar(255)` **nullable**, unique     |
| `external_id` | —                         | `varchar(255)` nullable, **unique** (new)      |
| `user_name`   | —                         | `varchar(255)` **NOT NULL**, **unique** (new)  |
| `active`      | —                         | `boolean NOT NULL DEFAULT true` (new)          |

This requires a real migration on any consumer that already has a populated `users` table. The
ordering matters: `user_name` is `NOT NULL` and `unique`, so existing rows must be backfilled with a
unique value **before** the constraint is enforced. Use the consumer's existing migration tool
(Flyway, Liquibase, …). Do **not** rely on `spring.jpa.hibernate.ddl-auto=update` — Hibernate will
attempt to add the columns but cannot backfill, so the `NOT NULL` constraint on `user_name` will fail
against a non-empty table.

Canonical PostgreSQL migration (adjust dialect as needed):

```sql
-- users: relax sub, add external_id / user_name / active
ALTER TABLE users ALTER COLUMN sub DROP NOT NULL;

ALTER TABLE users ADD COLUMN external_id varchar(255);
ALTER TABLE users ADD COLUMN user_name   varchar(255);
ALTER TABLE users ADD COLUMN active      boolean NOT NULL DEFAULT true;

-- Backfill user_name for existing rows BEFORE adding the NOT NULL + unique constraints.
-- Use the existing sub as the handle; it is already unique and non-null on legacy rows.
UPDATE users SET user_name = sub WHERE user_name IS NULL;

ALTER TABLE users ALTER COLUMN user_name SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_user_name   UNIQUE (user_name);
ALTER TABLE users ADD CONSTRAINT uq_users_external_id UNIQUE (external_id);
```

`external_id` stays nullable and is backfilled lazily: the JIT-login flow sets it from the JWT once
each user next authenticates. Do not invent values for it.

Note: `SystemUserInitializer` now inserts into `user_name` and `active` as well. If your service uses
it, the migration above must be applied **before** the new version starts, or the system-user insert
will fail.

#### Additive: new read APIs on `UserEntity` and `UserRepository`

Safe to ignore unless you want them; old behaviour is unchanged.

- `UserEntity`: `getExternalId()` / `setExternalId(String)`, `getUserName()` / `setUserName(String)`,
  `isActive()` / `setActive(boolean)`.
- `UserRepository`: `Optional<UserEntity> findByExternalId(String externalId)` and
  `Optional<UserEntity> findByUserName(String userName)` (derived queries; nothing to implement).

#### Additive: `UserProvisioner` bean

A new `@Component` in the `…services.user` package that performs the race-safe first-login insert in
a `REQUIRES_NEW` transaction. It is component-scanned automatically by `UserConfig` — no consumer
wiring. You generally never call it directly; `UserService` uses it internally.

#### Additive (scaffolding): `PrincipalDirectory` port

A new interface `com.openelements.spring.base.services.apitoken.PrincipalDirectory` and its default
implementation `…services.user.UserEntityPrincipalDirectory` ship as scaffolding for the planned
opaque-token module (spec 010). There is **no opaque-token authentication in 1.1.0** — nothing to
wire up or migrate. The interface lives in a `apitoken` package today and is documented as likely to
move to spec 010's package in a future release; only adopt it if you are knowingly building ahead.

#### Non-breaking cleanup: feature `@Configuration` classes normalised

The feature configs (`SecurityConfig`, `UserConfig`, `ApiKeyConfig`, `AuditConfig`, … ) dropped their
`@AutoConfiguration` / `@EnableAutoConfiguration` annotations and switched to
`@Configuration(proxyBeanMethods = false)` + `@ComponentScan(basePackageClasses = <ThatConfig>.class)`.
This library has **no** `META-INF/spring/…AutoConfiguration.imports` file, so those annotations were
inert — the bootstrap path was, and remains, an explicit `@Import(FullSpringServiceConfig.class)` (or
importing individual feature configs). **Do not change your import setup**; this is internal cleanup
with no behavioural effect. `FullSpringServiceConfig` is unchanged.

Internally, `AuthService` and `ApiKeyAuthenticationFilter` now read the `SecurityContext` via
`SecurityContextHolderStrategy` instead of the static holder. This honours any custom strategy a
consumer installed via `SecurityContextHolder.setStrategyName(...)`; no action required.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is
   listed.

2. **Bump `spring-services` to `1.1.0`** in that `pom.xml`. Do not edit Spring Boot or Testcontainers
   versions in the same change.

3. **Write and apply the `users` migration** (see the SQL above) with the consumer's migration tool,
   in this order: relax `sub`, add the three columns, **backfill `user_name`**, then enforce
   `NOT NULL` + the unique constraints. Apply it before the new version starts.

4. **Fix `AuthService.getUserInformation()` call sites.** Grep for them:

   ```bash
   grep -rn "getUserInformation" --include="*.java" src
   ```

   Each call now yields `Optional<UserInformation>` — add `.orElseThrow(...)` / `.map(...)` /
   `.ifPresent(...)` per the call site's intent. Remove any handling that depended on the old
   `"UNKNOWN"` sentinel.

5. **Fix direct `UserInformation` construction and record patterns.** Grep:

   ```bash
   grep -rn "new UserInformation(" --include="*.java" src
   grep -rn "UserInformation(" --include="*.java" src   # also catches record patterns
   ```

   Add the two trailing components (`userName`, `externalId`) to constructor calls and to any
   four-component record pattern. If there are none — common in production code — this is a no-op.

6. **Fix direct `UserService` construction** (typically tests). Grep:

   ```bash
   grep -rn "new UserService(" --include="*.java" src
   ```

   Add a `UserProvisioner` argument. DI-obtained `UserService` needs no change.

7. **Update auth-failure expectations.** If any test or client asserts on the old `401` body
   `{"error":"Invalid API key"}`, update it to `{"status":401,"error":"Unauthorized","message":"Invalid API key"}`
   and expect a `WWW-Authenticate` header. If any test asserts an inactive user can still call
   `getCurrentUser()`, update it to expect `403` / `AccessDeniedException`.

8. **Verify the build:**

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; application context should come up cleanly
   ```

   The context comes up cleanly with the migrated schema; `SystemUserInitializer` (if used) inserts
   the system user with the new `user_name` / `active` columns.

9. **Commit** the version bump, migration, and call-site fixes together:

   ```
   chore(deps): upgrade spring-services to 1.1.0
   ```

### Guard rails

- **Do not** bump Spring Boot, Testcontainers, or any other dependency in the same change. 1.1.0
  ships against the same BOMs as 1.0.0 — leave them alone.
- **Do not** add `user_name` (or `active`) with `NOT NULL` before backfilling. The entity declares
  `user_name NOT NULL` and Hibernate validates the schema at startup; add the column nullable,
  backfill a unique value, then set `NOT NULL`. The reliable backfill is `user_name = sub`.
- **Do not** rely on `spring.jpa.hibernate.ddl-auto=update` for this migration. Hibernate adds the
  columns but cannot backfill, so the subsequent `NOT NULL` constraint fails on a populated table.
- **Do not** re-tighten `sub` to `NOT NULL`. SCIM pre-provisioning intentionally creates rows with a
  null `sub`; the JIT-login flow backfills it on first interactive login.
- **Do not** reintroduce a `UserInformation("UNKNOWN", …)` sentinel to dodge the `Optional`. Handle
  the empty case explicitly at each call site.
- **Do not** re-add `synchronized` to `getCurrentUserEntity()`. First-login races are handled by the
  `UserProvisioner` `REQUIRES_NEW` transaction and the `findBySub` retry; a method lock does not
  protect across multiple instances and would only add contention.
- **Do not** make `getCurrentUser()`/`getCurrentUserEntity()` swallow the new `AccessDeniedException`
  — a deactivated user must surface as `403`.

### Don't do this

- Do not adopt `PrincipalDirectory` / opaque tokens as if they were a shipping feature. 1.1.0 ships
  only the port and a default implementation as scaffolding; there is no opaque-token authentication
  to wire up, and the port may move packages when spec 010 lands.
- Do not change your `@Import(FullSpringServiceConfig.class)` setup or add an
  `AutoConfiguration.imports` file because the configs dropped `@AutoConfiguration`. The annotations
  were inert; the explicit-import bootstrap is unchanged.
- Do not backfill `external_id` with a made-up value. Leave it null; the JIT-login flow populates it
  from the JWT `sub` on each user's next login.
- Do not edit `spring-services` from the consumer side. If a behaviour you depended on is missing,
  open an issue against the library.
- Do not bundle unrelated refactors with this upgrade. Ship the bump, migration, and the mechanical
  call-site fixes together; keep anything else in a separate change.
