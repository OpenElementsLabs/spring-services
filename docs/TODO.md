# TODO

A lightweight backlog of open items that are worth keeping but are **not yet ready** to become a
specification or a GitHub issue. Each entry is a candidate, not a commitment — when it matures it
graduates into a spec (via `/spec-create`) or a GitHub issue and is then removed from this file.

## Implement the `services/system` repository for external service configuration

Provide a system-level repository/service that lets applications configure external services (the
concrete integrations the library talks to) instead of hard-wiring them. This is the foundation for
making the external-service wiring configurable per app.

**Context:** Long-standing backlog item; parked because there is no concrete design yet.

## Add logging across the codebase

Several places currently have no logging. Add structured logging where it aids operability and
debugging (service boundaries, error paths, security-relevant events).

**Context:** General observability gap noticed during ongoing work; deferred as cross-cutting cleanup.

## Add application metrics

Expose application metrics (e.g. via Micrometer/Prometheus) so consumers can observe the library in
production. Complements the logging item above.

**Context:** General observability gap; deferred until an owner picks up the observability story.

## Hibernate Search (full-text search) foundation

Lay the groundwork for full-text search using Hibernate Search, so features that need search can build
on a shared foundation.

**Context:** Anticipated future capability; parked until a concrete feature requires it.

## Migrate remaining data services to the `AbstractDbBackedDataService` pattern

`ApiKeyDataService` and `SettingsDataService` do not yet follow the shared data-service pattern used by
the rest of the persistence layer.

- `src/main/java/com/openelements/spring/base/services/apikey/ApiKeyDataService.java`
- `src/main/java/com/openelements/spring/base/services/settings/SettingsDataService.java`
- Target pattern: `src/main/java/com/openelements/spring/base/data/AbstractDbBackedDataService.java`

**Context:** Consistency cleanup surfaced while working on the data layer.

**Prerequisite:** For `ApiKeyDataService`, coordinate with Spec 010 (API Token Module, open), which
replaces the existing ApiKey infrastructure — migrating it first may be wasted effort.

## Make `ApiKeyDataService.KEY_PREFIX` configurable per app

`ApiKeyDataService.KEY_PREFIX` is currently a constant. It should be configurable per application so
different apps can use distinct key prefixes.

**Context:** Flexibility gap noticed in the ApiKey code.

**Prerequisite:** Coordinate with Spec 010 (API Token Module, open), which replaces the ApiKey
infrastructure and may make this obsolete or move it.

## Evaluate a per-module DB schema with Flyway

Investigate giving each module its own DB schema managed by Flyway, so module persistence is isolated.

- Reference: <https://stackoverflow.com/questions/49303184/how-to-handle-a-modular-spring-project-with-flyway-and-single-db>

**Context:** Original modularity idea. Note that Spec 013 (open) deliberately chose a **single** fixed
`oe_spring_services` schema (single persistence unit), so this is now a "revisit only if the
single-schema approach proves limiting" item rather than an active plan.

**Prerequisite:** Spec 013 (dedicated schema + starter) and Spec 014 (multi-module restructuring).

## SCIM 2.0 Provider (Step 2)

Add SCIM 2.0 server endpoints for push-provisioning from Authentik / an IdP: `/scim/v2/Users`,
`/scim/v2/Groups` and discovery (ServiceProviderConfig, Schemas, ResourceTypes). Extends the
`UserEntityPrincipalDirectory` foundation from Spec 012 so USER tokens become role-aware.

- Library base: UnboundID / Ping SCIM 2 SDK plus custom Spring MVC controllers.
- Auth: JWT in a dedicated third filter chain, isolated from the default chain.
- Soft deactivation via `active=false`; DELETE is a soft delete. One audit-log entry per SCIM write.
- Adds `GroupEntity`; group membership is delivered in `ResolvedPrincipal.groups()`, and optionally in
  `ResolvedPrincipal.roles()` via a configurable Group→Role mapping.
- Open design questions (from the grill session): PATCH mechanics; filter-grammar scope
  (eq/sw/co/AND/OR); ETag / optimistic concurrency; pagination; tenant interaction (one SCIM instance
  per tenant?); Authentik vendor extensions; discovery honesty; group rename/delete and the effect on
  derived roles; whether `revokeAllForSubject` should fire as a hard-revoke hook on SCIM deactivation
  in addition to live resolution.

**Context:** Step 2 of the SCIM work. Step 1 (Foundation) was implemented via Spec 012
(`UserEntity` extended, JIT correlation, active-gate, `UserEntityPrincipalDirectory`). Deferred because
Step 2 still has open design questions that need a grill session and a dedicated spec.

**Prerequisite:** Spec 012 (SCIM foundation user model, done).

## Spec 011 (Security Configuration Hygiene) follow-ups

Follow-up items surfaced during the `/spec-review` of Spec 011 that were out of scope for that spec.

- **MockMvc integration test for the JWT chain `JsonAuthenticationEntryPoint`** (Bearer scheme over a
  real HTTP round-trip). Currently only covered at unit level (`JsonAuthenticationEntryPointTest`).
  Requires a JWT-issuing test fixture (a custom `JwtDecoder` that accepts hand-crafted tokens, plus a
  small test controller). Rounds out the behavior scenario "Missing JWT on default chain produces same
  error shape".
- **Ship a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`** for
  zero-config integration (no more `@Import(FullSpringServiceConfig.class)`). Note: Spec 013 (open)
  already ships a real Spring Boot starter via `AutoConfigurationPackages`, so this may be absorbed
  there — reconcile before acting.
- **Per-feature `@ConditionalOnMissingBean` / `@ConditionalOnProperty`** for all library beans, so
  consumers can register their own `JwtAuthenticationConverter` or disable individual features by
  property (`openelements.security.api-key.enabled=false`). Needs a property-naming convention and a
  coherent toggle plan first. Related to Spec 014's per-module `@AutoConfiguration` guarding.

**Context:** Deferred from the `/spec-review` of Spec 011 (security-config-hygiene, done). (The
"flip Spec 011 INDEX status to done" follow-up is already resolved — the INDEX shows `done`.)

**Prerequisite:** The autoconfiguration and toggle items should reconcile with Spec 013 and Spec 014.

## Document the test connection-pool sizing convention

Integration tests set `spring.datasource.hikari.maximum-pool-size=30` because `UserProvisioner` uses
`REQUIRES_NEW`, holding two connections per provisioning thread. Production apps need to size their own
pool accordingly (`peak_concurrent_first_logins × 2 + steady-state`). This is currently only mentioned
in the README upgrade notes and should also live in a dedicated "Production Deployment Notes" section
once one exists.

**Context:** Surfaced while writing integration tests; deferred until a deployment-notes doc section
exists to host it.

## Spec candidate: Test Hygiene & Bug Fixes

Three separate findings surfaced while documenting all 45 test files (class Javadoc + `@DisplayName` +
mock audit, done after Spec 012). They justify their own spec, to be worked out via `/spec-create` with
a preceding `/grill-me`. The points below are the raw findings, **not** a finished spec design.

**Finding 1 — Real bug: `WebhookEventListener.handle(...)` ignores `eventType`**

- File: `src/main/java/com/openelements/spring/base/services/webhook/WebhookEventListener.java`
- Symptom: the method takes `eventType` as a parameter but, in both branches (payload build +
  `WebhookSupport.supports(...)`, around lines 45–47), hardcodes `WebhookDataEventType.DELETED` instead
  of using the parameter. As a result CREATED and UPDATED events are sent to subscribers as DELETED.
- Test status: the existing `WebhookEventListenerTest.shouldHandleCreate` passes only by luck — it just
  checks "sender was called". The test-doc pass documented the bug in the test's class Javadoc rather
  than silently fixing it.
- Grill questions for the spec: do subscriber signatures need to change? Do any apps already rely on the
  buggy behaviour (DELETED for every lifecycle event)? How do we test the three variants reliably?

**Finding 2 — Over-mocking in integration tests**

- `ApiKeyDataServiceIntegrationTest` — `@MockBean UserService` in a test that otherwise uses a real
  Postgres via Testcontainers. Should seed a real user via `userRepository.save(...)` and stub
  `AuthService` minimally, so the "integration" label is honest.
- `ApiKeyDataServiceIntegrationTest` — `@MockBean AuthService` is declared only for transitive wiring
  and never read by the tests. Either scope it more tightly or replace it with a no-op `SecurityContext`
  setup.
- `CommentServiceIntegrationTest` — `@MockitoSpyBean UserService` is used only for a single
  `verify(never()).findById(...)` assertion. The N+1 claim can be expressed more directly via the
  Hibernate `Statistics` counter (already used in the same class for other assertions), making the spy
  obsolete.
- Grill questions for the spec: are there other integration tests with hidden mocking we missed? Is the
  "real beans in integration tests, mocks only in unit tests" convention already captured in
  `CLAUDE.md`, and should it be?

**Finding 3 — Cosmetic: ObjectProvider / FilterChain mocks replaceable with real implementations**

- `SlackServiceTest` + `EmailServiceTest` — `mock(ObjectProvider.class)` with
  `@SuppressWarnings("unchecked")`. Spring's `ObjectProvider` is a functional interface —
  `(ObjectProvider<X>) () -> instance` (or `() -> null`) is more readable and avoids the cast.
- `ApiKeyAuthenticationFilterTest` — the `FilterChain` mock is conventional, but a "RecordingFilterChain"
  (a real impl that records calls in a list) would be more robust against double-invocation bugs.
- `SecurityConfigRoleTest` — the `ApiKeyDataService` mock is a no-op constructor argument. It could be
  replaced with a no-op impl, or removed entirely via factory extraction (`jwtAuthenticationConverter()`
  as a static helper without the `ApiKeyDataService` dependency).
- `AuditLogDataServiceTest` — `UserRepository` + `ApplicationEventPublisher` are dead mock ballast in the
  reader tests. If a reader/writer split of the data services lands (see the migration item above), both
  mocks can go.
- Grill questions for the spec: is a general "no `mock(...)` for functional interfaces" rule worth adding
  to `CLAUDE.md`? Should we configure SonarQube / PMD rules for it?

**Scope question for the spec itself:** should all three findings land in one spec, or be split in two
(bug fix #1 isolated, cleanup #2 + #3 together)? The grill session decides.

**Context:** Surfaced during the post-Spec-012 test-documentation pass; deferred because it needs a
`/grill-me` + `/spec-create` cycle before implementation.

## Clarify the Authentik `name`-claim guarantee

Confirm whether the Authentik configuration guarantees a `name` claim for every user who signs in.
Concretely: does every Authentik user have a maintained `name` attribute, and is the `profile` scope
mapping in the Authentik provider set so that `name` always ends up in the JWT — or can there be users
(e.g. SSO-imported, freshly created without a profile) whose `name` is missing? If uncertain, we likely
need a server-side fallback (`name ← preferred_username ← sub`) before the JWT reaches spring-services.

**Context:** Module-usage question that needs investigation before we can rely on the `name` claim.

## Decide the `OidcHealthIndicator` endpoint after switching to `jwk-set-uri`

The indicator currently reads `issuer-uri` and pings `${issuer-uri}/.well-known/openid-configuration`.
We are switching to `jwk-set-uri` for JWT validation, which raises a choice (grill session, Spec 011
Branch F):

- **(a) Keep both keys** — `issuer-uri` only for the indicator, `jwk-set-uri` for JWT validation. The
  indicator pings discovery as before, but two OIDC URLs must be maintained per environment.
- **(b) Point the indicator at `jwk-set-uri`** — ping the JWKS endpoint directly. One config URL, but we
  lose the check that the discovery document is valid (functionally irrelevant for us as a
  resource server — we only need JWKS).

**Context:** Open decision from the grill session for Spec 011 (Branch F, `OidcHealthIndicator`);
deferred until the `jwk-set-uri` switch is scheduled.

## Evaluate Spring Modulith for real module boundaries

After the multi-module restructuring, module boundaries are pure convention: plain Maven classpath
modules without JPMS (chosen deliberately, since Spring interacts poorly with JPMS), so every `public`
type in `spring-services-core` is reachable across modules. Spring Modulith could declare the logical
Spring module boundaries explicitly and verify them via an `ApplicationModules` test (allowed
dependencies, no access to internal packages).

**Context:** Surfaced in the `/grill-me` session for Spec 014 (multi-module restructuring), Branch E
(API surface between modules) — JPMS was rejected and Modulith parked as the alternative.

**Prerequisite:** Spec 014 (multi-module restructuring) must land first.
