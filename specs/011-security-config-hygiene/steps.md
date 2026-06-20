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

- [x] In `src/main/java/com/openelements/spring/base/services/user/UserService.java`,
  remove the `synchronized` modifier from `getCurrentUserEntity()`.
- [x] **Discovered bug fix:** the original race-recovery (`try { save() } catch
  DataIntegrityViolationException`) does not actually work — `save()` does not flush, so the
  unique-constraint violation surfaces only at outer-transaction commit, outside the catch.
  `synchronized` was masking this. Fix: extract the insert into a new `UserProvisioner`
  component with `@Transactional(Propagation.REQUIRES_NEW)` and use `saveAndFlush`, so the
  conflict is detected inside the inner transaction (which rolls back), leaving the outer
  transaction alive for the retry `findBySub`.
- [x] New file:
  `src/main/java/com/openelements/spring/base/services/user/UserProvisioner.java`.
- [x] `UserService` constructor gains a `UserProvisioner` parameter; the `.orElseGet(...)`
  branch calls `userProvisioner.provision(userInformation)` instead of inlining the insert.
- [x] Bumped `spring.datasource.hikari.maximum-pool-size=30` in
  `application-testcontainers.properties` — `REQUIRES_NEW` doubles the connection requirement
  per thread (suspended outer + active inner); the default pool of 10 deadlocked the
  10-thread concurrency test.

**Acceptance criteria:**

- [x] Project builds and existing tests pass (311 tests, 0 failures, 2 pre-existing skips).
- [x] New `UserServiceConcurrencyTest` (Testcontainers Postgres, `@SpringBootTest`):
  - `concurrentFirstLoginsForSameSubProduceOneRow` — 10 threads × same sub → one row, all
    threads observe the same `UserEntity.id`.
  - `concurrentLoginsForDifferentSubsDoNotBlock` — 10 threads × different subs → 10 rows,
    wall-clock < 5 × single-thread baseline.
  - `driftSyncIsTransactional` — drift sync remains atomic across name/email/avatarUrl.
- [x] Existing `UserServiceTest` updated to mock the new `UserProvisioner` dependency in the
  create-path tests (`shouldCreateNewUserWhenNotExists`, `shouldStoreAvatarUrlOnFirstLogin`,
  `shouldCreateUserWithoutAvatarUrlWhenClaimMissing`, `shouldProvisionNewUserOnFirstCall`).

**Related behaviors:**

- *UserService concurrency — Concurrent first logins for the same subject still produce exactly
  one row*
- *UserService concurrency — Concurrent logins for different subjects do not block each other*
- *UserService concurrency — Drift sync remains transactional*

---

## Step 3: Use `SecurityContextHolderStrategy` instead of direct `SecurityContextHolder`

**Changes:**

- [x] In `src/main/java/com/openelements/spring/base/security/AuthService.java`:
  - Introduced a `private final SecurityContextHolderStrategy securityContextHolderStrategy =
    SecurityContextHolder.getContextHolderStrategy();` field with Javadoc.
  - Replaced the direct `SecurityContextHolder.getContext()` call in `getAuthentication()` with
    `securityContextHolderStrategy.getContext()`.
- [x] In
  `src/main/java/com/openelements/spring/base/security/apikey/ApiKeyAuthenticationFilter.java`:
  - Added the same strategy field with Javadoc.
  - Replaced `SecurityContextHolder.getContext().setAuthentication(authentication)` with
    `SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);` — matches the Spring Security 6
    documented pattern.

**Acceptance criteria:**

- [x] Project builds and the existing `AuthServiceTest` and `ApiKeyAuthenticationFilterTest`
  pass without modification.
- [x] Full test suite passes (311 tests, 0 failures, 2 pre-existing skips).

**Related behaviors:**

- (No dedicated behavior scenario — this is a structural change covered by existing tests
  continuing to pass.)

---

## Step 4: `AuthService.getUserInformation()` returns `Optional<UserInformation>`

**Changes:**

- [x] `AuthService.getUserInformation()` returns `Optional<UserInformation>`; non-JWT principals
  return `Optional.empty()` instead of the `"UNKNOWN"` sentinel record.
- [x] Removed the dead `if (principal == null)` check in `getPrincipalJwt()`.
- [x] `UserService.getCurrentUserEntity()` calls `.orElseThrow(...)` with a clear message
  pointing the caller at the JWT-only contract.
- [x] **Second caller discovered:** `AuditLogEventListener.resolveUser()` also called
  `getUserInformation()` and had its own `UNKNOWN_PRINCIPAL_ID` constant to detect the sentinel.
  Refactored to consume the `Optional` directly; the local `UNKNOWN_PRINCIPAL_ID` constant
  removed. Behaviour identical: API-key-authenticated requests still attribute audit rows to
  the System User.
- [x] Javadoc updated on `AuthService.getUserInformation()`, on the `AuthService` class-level
  usage example, and in `security/package-info.java` to reflect the `Optional` contract.

**Acceptance criteria:**

- [x] Project builds; full test suite passes (312 tests, 0 failures, 2 pre-existing skips).
- [x] `AuthServiceTest`:
  - `shouldExtractUserInformationFromJwt` / avatar-claim tests now use `.orElseThrow()` to
    unwrap, implicitly asserting `isPresent() == true`.
  - `shouldReturnEmptyForApiKeyPrincipal` (renamed from `…UnknownUser…`) asserts
    `isEmpty()`.
  - `shouldReturnEmptyForStringPrincipal` asserts `isEmpty()`.
  - `shouldThrowWhenPrincipalIsNotJwt` (pre-existing) covers `getPrincipalJwt()` behaviour
    after the dead-null-check removal.
- [x] New `UserServiceTest.shouldThrowWhenNoJwtBoundToRequest` covers the
  `Optional.empty() → IllegalStateException` path; verifies neither the repository nor the
  provisioner is touched.
- [x] All other test files (`UserServiceConcurrencyTest`, `UserServiceTest.setupAuth`,
  `CommentServiceIntegrationTest`, `AuditLogIntegrationTest`, `AuditLogEventListenerTest`)
  updated for the new `Optional` return type. Test helpers (`userNamed`, `userInfo`) now
  return `Optional<UserInformation>` so call sites stay uniform.

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

**Scope-Expansion:** original step listed only `SecurityConfig` / `UserConfig` / `ApiKeyConfig`;
the design goal says "every `@Configuration` / auto-configuration class in the library".
Applied uniformly to all 14 config classes for consistency.

**Changes:**

- [x] All 14 `@Configuration` classes normalised to the uniform pattern:
  - Removed `@AutoConfiguration` and `@EnableAutoConfiguration` (both were inert without an
    `AutoConfiguration.imports` file, and `@EnableAutoConfiguration` on a library config is
    an anti-pattern).
  - Replaced `@Configuration` with `@Configuration(proxyBeanMethods = false)` — no `@Bean`
    methods in this library invoke each other internally, so the CGLIB proxy is wasted cost.
  - Anchored `@ComponentScan` to `basePackageClasses = <Self>.class` — survives package
    renames, no fragile implicit "package of the annotated class" semantic.
  - Kept feature-specific annotations untouched: `@EnableConfigurationProperties` (Slack,
    DbBackup, Search), `@EnableScheduling` (Audit), `@Import` (Audit, Comment, Security),
    `@EnableWebSecurity` + `@EnableMethodSecurity` (Security), `@Bean` methods (Slack,
    Webhook, Security).
- [x] Files touched: `SecurityConfig`, `UserConfig`, `ApiKeyConfig`, `AuditConfig`,
  `CommentConfig`, `DbBackupConfig`, `EmailConfig`, `SearchConfig`, `SettingsConfig`,
  `SlackConfig`, `TagConfig`, `LanguageConfig`, `WebhookConfig`, `TenantConfig`.
- [x] **Two latent test-setup bugs surfaced** (both caused by the pre-change annotations
  silently disabling parts of the library in tests):
  - `WebhookConfig.webhookRestClient` conflicted with `PostgresTestConfiguration`'s same-named
    test bean once `WebhookConfig` started loading properly. Fixed by enabling
    `spring.main.allow-bean-definition-overriding=true` in the test profile — standard pattern
    when test configs intentionally override production beans.
  - `SecurityConfig.defaultFilterChain` required a `JwtDecoder` that the test setup explicitly
    excluded (`OAuth2ResourceServerAutoConfiguration.class`). Plus the test had its own
    `testSecurityFilterChain` matching `anyRequest()`, which Spring Security 6.2+ rejects
    as conflicting with the library's `defaultFilterChain`. Fixed by adding a stub
    `JwtDecoder` bean to `PostgresTestConfiguration` and removing the redundant test chain
    (tests invoke services directly, not via HTTP, so the production chain is harmless).

**Acceptance criteria:**

- [x] Project builds and all 312 tests pass (no failures, 2 pre-existing skips).
- [x] All `@SpringBootTest`-based integration tests still construct their application context
  cleanly with the new annotations — `AuditLogIntegrationTest`, `ApiKeyDataServiceIntegrationTest`,
  `CommentServiceIntegrationTest`, `SettingsDataServiceIntegrationTest`, `TagDataServiceIntegrationTest`,
  `UserServiceConcurrencyTest`, `WebhookDataServiceIntegrationTest`, and all
  `Meilisearch*Test`-suite tests pass.
- [x] No new context-loading warnings about recursive auto-configuration or duplicate scans.

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
