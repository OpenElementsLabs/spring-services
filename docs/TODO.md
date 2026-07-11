# TODOs

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

SCIM 2.0 Provider (Schritt 2, nach Foundation) — **in Teil-Specs aufgeteilt** (Grill-Session 2026-07-11):
- **Spec 015 — SCIM Users Provider** (in Arbeit): dedizierter dritter Filter-Chain für `/scim/v2/**` mit
  **statischem Bearer-Token** (`openelements.scim.token`, **nicht** JWT — Authentik sendet ein festes
  opakes Token), Discovery-Endpoints (ServiceProviderConfig/ResourceTypes/Schemas), Users-Resource
  (List+Filter, POST, GET, PUT-Replace, DELETE) gemappt auf `UserEntity` aus Spec 012. `POST`-Kollision →
  `409 uniqueness` (RFC), DELETE → soft (`active=false` + `deleted`/`deleted_at`). Audit-Einträge unter
  reserviertem **SCIM-Service-Principal**. Vor-Merge-Verifikation der Authentik-`409`-Recovery am
  #21-Harness (siehe Issue-Kommentar).
- **Folge-Issue A — SCIM Groups + Membership**: `GroupEntity` + Membership-Tabelle, `/scim/v2/Groups`
  (POST/GET/PUT/**PATCH** mit PatchOp add/remove/replace auf `members`, DELETE). Liefert
  `ResolvedPrincipal.groups()` aus `UserEntityPrincipalDirectory` (heute leer).
- **Folge-Issue B — Group→Role-Mapping**: konfigurierbare Ableitung von `ResolvedPrincipal.roles()` aus
  Gruppen-Mitgliedschaft; macht USER-Tokens role-aware.
- Verbleibende offene Designfragen für die Folge-Issues (aus Grill-Sessions): PATCH-Mechanik,
  Filter-Grammatik jenseits `eq` (sw/co/AND/OR), ETag/Optimistic-Concurrency, Tenant-Interaktion
  (eine SCIM-Instance pro Tenant?), Authentik-Vendor-Extensions, Discovery-Ehrlichkeit, Group-Rename/Delete
  und Auswirkungen auf abgeleitete Rollen, ob `revokeAllForSubject` bei SCIM-Deactivation als
  zusätzlicher Hard-Revoke-Hook gewünscht ist.