# Implementation Steps: Security Configuration Hygiene

Spec: [`011-security-config-hygiene`](./design.md) · Behaviors: [`behaviors.md`](./behaviors.md)

The work is structured as six refactoring steps, each independently verifiable by running the
existing test suite, followed by a documentation step. The ordering starts with the lowest-risk
isolated changes (Roles, `synchronized` removal) and ends with the highest-impact one (the
centralised `AuthenticationEntryPoint`, which changes error-body shape).

After each step the project must compile and the full test suite must pass before moving on.

---

## Step 1: Lock `Roles` utility class

**Changes:**

- [x] Add a `private Roles() {}` constructor to
  `src/main/java/com/openelements/spring/base/security/roles/Roles.java`.
- [x] No other modifications — constants and Javadoc stay as is.

**Acceptance criteria:**

- [x] Project builds successfully (`./mvnw clean compile`).
- [x] Spotless formatting passes for the touched files (pre-existing violations in `dbbackup`
  are out of scope for this step).
- [x] Existing unit tests still pass (`./mvnw test`).
- [x] A new unit test `RolesTest` exists in
  `src/test/java/com/openelements/spring/base/security/roles/` verifying that the constants still
  carry their expected string values.

**Related behaviors:**

- *Roles constants — Reflective instantiation is prevented*
- *Roles constants — Constants remain unchanged*

---

## Step 2: Remove `synchronized` from `UserService.getCurrentUserEntity()`

**Changes:**

- [ ] In `src/main/java/com/openelements/spring/base/services/user/UserService.java`,
  remove the `synchronized` modifier from `getCurrentUserEntity()`. No body changes.
- [ ] Update the method Javadoc to drop any references to monitor-based serialisation (none
  exists today, but cross-check). Keep the existing note that the `DataIntegrityViolationException`
  catch is the race-recovery path.

**Acceptance criteria:**

- [ ] Project builds and existing tests pass.
- [ ] A new integration test in `UserServiceTest` (or a new `UserServiceConcurrencyTest`)
  spawns ten threads calling `getCurrentUserEntity()` simultaneously with the **same** JWT
  `sub`. Exactly one row is inserted, every thread observes the same `UserEntity.id`. Use
  `Testcontainers` Postgres (existing `PostgresTestConfiguration`) — no mocking of the DB.
- [ ] A second concurrency test spawns ten threads with ten **different** JWT `sub` values.
  All ten rows are created. Measure total wall-clock time; assert it is significantly less than
  10 × single-thread time (e.g. `< 5 × single-thread`) — relaxed enough to be stable on CI, tight
  enough to detect re-introduction of `synchronized`.
- [ ] Existing drift-sync test still passes (regression for the transactional drift update).

**Related behaviors:**

- *UserService concurrency — Concurrent first logins for the same subject still produce exactly
  one row*
- *UserService concurrency — Concurrent logins for different subjects do not block each other*
- *UserService concurrency — Drift sync remains transactional*

---

## Step 3: Use `SecurityContextHolderStrategy` instead of direct `SecurityContextHolder`

**Changes:**

- [ ] In `src/main/java/com/openelements/spring/base/security/AuthService.java`:
  - Introduce a `private final SecurityContextHolderStrategy strategy =
    SecurityContextHolder.getContextHolderStrategy();` field.
  - Replace every `SecurityContextHolder.getContext()` call with `strategy.getContext()`.
- [ ] In
  `src/main/java/com/openelements/spring/base/security/apikey/ApiKeyAuthenticationFilter.java`:
  - Add the same `strategy` field.
  - Replace `SecurityContextHolder.getContext().setAuthentication(authentication)` with
    `SecurityContext context = strategy.createEmptyContext(); context.setAuthentication(authentication);
    strategy.setContext(context);`.

**Acceptance criteria:**

- [ ] Project builds and the existing `AuthServiceTest` and `ApiKeyAuthenticationFilterTest`
  pass without modification.
- [ ] No new test is strictly needed — existing tests cover the runtime behaviour, which is
  unchanged. Confirm coverage by ticking through the existing tests once.

**Related behaviors:**

- (No dedicated behavior scenario — this is a structural change covered by existing tests
  continuing to pass.)

---

## Step 4: `AuthService.getUserInformation()` returns `Optional<UserInformation>`

**Changes:**

- [ ] In `src/main/java/com/openelements/spring/base/security/AuthService.java`:
  - Change return type of `getUserInformation()` to `Optional<UserInformation>`.
  - For non-JWT principals, return `Optional.empty()` (drop the `"UNKNOWN"` sentinel).
  - Remove the dead `if (principal == null)` check in `getPrincipalJwt()`.
- [ ] In `src/main/java/com/openelements/spring/base/services/user/UserService.java`:
  - Update the single call site to `authService.getUserInformation().orElseThrow(() ->
    new IllegalStateException("No JWT bound to current request"));`.
- [ ] Update Javadoc on `AuthService.getUserInformation()` to describe the new contract:
  present for JWT-authenticated requests, empty for non-JWT principals (API key, primitive).
- [ ] Search the project for any other callers of `getUserInformation()` and update them.
  Likely none outside `UserService`, but verify via `grep -r "getUserInformation"`.

**Acceptance criteria:**

- [ ] Project builds and existing tests pass.
- [ ] `AuthServiceTest` is extended with:
  - A test that calls `getUserInformation()` with a JWT-authenticated context and asserts
    `isPresent() == true` plus the expected field values.
  - A test that calls it with an API-key-authenticated context (use the existing
    `ApiKeyAuthentication` token) and asserts `isEmpty()`.
  - A test that `getPrincipalJwt()` still throws `IllegalStateException("Principal is not a
    JWT")` for a non-JWT principal — covers the post-removal of the dead `null` check.
- [ ] `UserServiceTest` is extended with a test that calling `getCurrentUserEntity()` without a
  JWT-authenticated context throws `IllegalStateException`.

**Related behaviors:**

- *AuthService.getUserInformation() contract change — JWT-authenticated request returns a present
  Optional*
- *AuthService.getUserInformation() contract change — API-key-authenticated request returns an
  empty Optional*
- *AuthService.getUserInformation() contract change — UserService still fails fast when called
  without a JWT*
- *AuthService.getUserInformation() contract change — `getPrincipalJwt()` still throws on
  non-JWT principals*

---

## Step 5: Normalise auto-configuration annotations

**Changes:**

- [ ] In `src/main/java/com/openelements/spring/base/security/SecurityConfig.java`:
  - Remove `@AutoConfiguration` and `@EnableAutoConfiguration`.
  - Remove `@ComponentScan`.
  - Add `@Configuration(proxyBeanMethods = false)`.
  - Keep `@EnableWebSecurity`, `@EnableMethodSecurity`, `@Import({...})`.
- [ ] In `src/main/java/com/openelements/spring/base/services/user/UserConfig.java`:
  - Replace the full annotation set with
    `@Configuration(proxyBeanMethods = false)` and
    `@ComponentScan(basePackageClasses = UserConfig.class)`.
- [ ] In `src/main/java/com/openelements/spring/base/services/apikey/ApiKeyConfig.java`:
  - Same replacement as `UserConfig`, anchored to `ApiKeyConfig.class`.

**Acceptance criteria:**

- [ ] Project builds and all existing tests pass.
- [ ] A new integration test `LibraryWiringTest` (or extension of an existing context-loading
  test) imports `FullSpringServiceConfig` and asserts that every previously-published bean
  is present: `UserService`, `AuthService`, `ApiKeyDataService`,
  `ApiKeyAuthenticationFilter`, both `SecurityFilterChain` beans, `JwtAuthenticationConverter`.
  Use `@SpringBootTest` with the existing `TestApplication` setup.
- [ ] Boot the `TestApplication` once with the new annotations and confirm the context loads
  without warnings about recursive auto-configuration or duplicate component scans.

**Related behaviors:**

- *Configuration wiring — Library still integrates via `@Import(FullSpringServiceConfig.class)`*
- *Configuration wiring — Removing `@EnableAutoConfiguration` does not break startup*
- *Configuration wiring — `proxyBeanMethods = false` does not break bean wiring*

---

## Step 6: Centralised `JsonAuthenticationEntryPoint`

**Changes:**

- [ ] Create
  `src/main/java/com/openelements/spring/base/security/JsonAuthenticationEntryPoint.java`:
  - Implements `org.springframework.security.web.AuthenticationEntryPoint`.
  - Constructor takes a Jackson `ObjectMapper`.
  - `commence(...)` sets `status = 401`, `Content-Type = application/json`, and writes
    `{"status": 401, "error": "Unauthorized", "message": <reason>}` via the `ObjectMapper`.
  - Sets `WWW-Authenticate` header: `"Bearer"` for the default chain,
    `"ApiKey realm=\"external\""` for the external chain. Make the header value
    constructor-configurable so the same class serves both chains.
- [ ] In `src/main/java/com/openelements/spring/base/security/SecurityConfig.java`:
  - Add a `@Bean JsonAuthenticationEntryPoint jwtAuthenticationEntryPoint(ObjectMapper)` for the
    JWT chain (Bearer scheme).
  - Add a `@Bean JsonAuthenticationEntryPoint apiKeyAuthenticationEntryPoint(ObjectMapper)` for
    the external chain (ApiKey scheme).
  - Wire `jwtAuthenticationEntryPoint` into `oauth2ResourceServer` via
    `.authenticationEntryPoint(...)`.
  - Wire `apiKeyAuthenticationEntryPoint` into the external chain via
    `http.exceptionHandling(e -> e.authenticationEntryPoint(...))`.
  - Pass `apiKeyAuthenticationEntryPoint` into the `ApiKeyAuthenticationFilter` constructor.
- [ ] In
  `src/main/java/com/openelements/spring/base/security/apikey/ApiKeyAuthenticationFilter.java`:
  - Constructor takes an additional `AuthenticationEntryPoint` argument.
  - On invalid key: replace the manual JSON write with
    `entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
    return;`.

**Acceptance criteria:**

- [ ] Project builds and the full test suite passes.
- [ ] `ApiKeyAuthenticationFilterTest` is updated:
  - The "invalid key" case now asserts the new JSON body shape (`{"status":401,"error":
    "Unauthorized","message":"Invalid API key"}`) and the presence of the
    `WWW-Authenticate: ApiKey realm="external"` header.
- [ ] A new test `JsonAuthenticationEntryPointTest` (plain JUnit 5, no Spring context) directly
  exercises `commence(...)` and verifies status code, body shape via `ObjectMapper.readTree`,
  and the `WWW-Authenticate` header value.
- [ ] A new integration test (extension of `SecurityConfigRoleTest` or a new
  `JwtChainErrorBodyTest`) issues a request to a protected default-chain endpoint without an
  `Authorization` header and asserts the same body shape and a `WWW-Authenticate: Bearer`
  header.
- [ ] A new integration test verifies that a request with a **valid** API key continues to
  pass through and is handled normally (no entry point invoked).
- [ ] A new integration test verifies that a request with a **valid** JWT continues to pass
  through and is handled normally.

**Related behaviors:**

- *Authentication-failure JSON responses — Invalid API key produces uniform JSON error*
- *Authentication-failure JSON responses — Missing JWT on default chain produces same error
  shape*
- *Authentication-failure JSON responses — 401 responses include `WWW-Authenticate` header*
- *Authentication-failure JSON responses — Successful authentication is unaffected*

---

## Step 7: Update project documentation

**Changes:**

- [ ] Update `README.md`:
  - Note the new error-body shape on `401` responses under "Features" (a single line in the
    JWT/API-key bullets is enough).
  - Note that `AuthService.getUserInformation()` now returns `Optional<UserInformation>` in
    the same section.
- [ ] Update or add to `CHANGELOG` (if one exists; otherwise add a brief migration paragraph in
  README's "Quick Start" section or a new "Upgrade Notes" subsection):
  - Auth-failure JSON body shape change.
  - `AuthService.getUserInformation()` signature change.
- [ ] Cross-check `CLAUDE.md` and the `.claude/conventions/project-specific/*.md` files (if
  present) — update only if any rule about the security stack has changed. None of the new
  changes introduce a new convention category, so most files likely require no edit.
- [ ] No update needed for `TODO.md` — the deferred items (auto-config imports file,
  `@ConditionalOnProperty` toggles, actuator-health) are tracked in the design's *Open
  Questions* section.

**Acceptance criteria:**

- [ ] README compiles (markdown-lint clean if linter is configured).
- [ ] Migration paragraph mentions both the JSON-body and the `Optional` signature change.

**Related behaviors:**

- (Documentation step — no behavior scenarios.)

---

## Behavior Coverage

All scenarios from `behaviors.md` are backend scenarios — this library has no UI. Every scenario
is assigned to exactly one step.

| Scenario                                                                                                                      | Layer   | Covered in Step |
|-------------------------------------------------------------------------------------------------------------------------------|---------|-----------------|
| Authentication-failure — Invalid API key produces uniform JSON error                                                          | Backend | Step 6          |
| Authentication-failure — Missing JWT on default chain produces same error shape                                               | Backend | Step 6          |
| Authentication-failure — 401 responses include `WWW-Authenticate` header                                                      | Backend | Step 6          |
| Authentication-failure — Successful authentication is unaffected                                                              | Backend | Step 6          |
| UserService concurrency — Concurrent first logins for the same subject still produce exactly one row                          | Backend | Step 2          |
| UserService concurrency — Concurrent logins for different subjects do not block each other                                    | Backend | Step 2          |
| UserService concurrency — Drift sync remains transactional                                                                    | Backend | Step 2          |
| `AuthService.getUserInformation()` — JWT-authenticated request returns a present Optional                                     | Backend | Step 4          |
| `AuthService.getUserInformation()` — API-key-authenticated request returns an empty Optional                                  | Backend | Step 4          |
| `AuthService.getUserInformation()` — UserService still fails fast when called without a JWT                                   | Backend | Step 4          |
| `AuthService.getUserInformation()` — `getPrincipalJwt()` still throws on non-JWT principals                                   | Backend | Step 4          |
| Configuration wiring — Library still integrates via `@Import(FullSpringServiceConfig.class)`                                  | Backend | Step 5          |
| Configuration wiring — Removing `@EnableAutoConfiguration` does not break startup                                             | Backend | Step 5          |
| Configuration wiring — `proxyBeanMethods = false` does not break bean wiring                                                  | Backend | Step 5          |
| Roles constants — Reflective instantiation is prevented                                                                       | Backend | Step 1          |
| Roles constants — Constants remain unchanged                                                                                  | Backend | Step 1          |

Every scenario has a step. No gaps.

---

## Execution Options

- **Manual** — work through the steps as a developer; each step is small enough to land as a
  single commit.
- **Guided** — implement step by step and ask for help on the tricky parts (Step 6's entry-point
  wiring is the most subtle).
- **Automated** — Claude Code executes each step in order, ticks off the boxes in this file, and
  pauses for review before the next step. Recommended for this spec because the changes are
  mechanical and well-scoped.
