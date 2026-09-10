# TODO

## Property toggles and consumer overridability for core security beans

Per-feature `@ConditionalOnMissingBean` / `@ConditionalOnProperty` for all library beans, so
consumers can register their own `JwtAuthenticationConverter` or disable individual features by
property (e.g. `openelements.security.api-key.enabled=false`). Needs a property-naming convention
and a coherent toggle plan first.

- Module-level guards already exist (`@ConditionalOnClass` on core/email/slack/mcp;
  `@ConditionalOnProperty` on scim/db-backup/search/mcp), absorbed from Spec 014's per-module
  `@AutoConfiguration` guarding.
- Still missing (verified 2026-07-31: no `@ConditionalOnMissingBean` exists anywhere in the
  reactor): property toggles and `@ConditionalOnMissingBean` overridability for the **core security
  beans** — the API-key chain and `JwtAuthenticationConverter`.

**Context:** Deferred from the `/spec-review` of Spec 011 (security-config-hygiene, done); should
reconcile with the Spec 013/014 autoconfiguration setup.

## Document the test connection-pool sizing convention

Integration tests set `spring.datasource.hikari.maximum-pool-size=30` because `UserProvisioner`
uses `REQUIRES_NEW`, holding two connections per provisioning thread. Production apps need to size
their own pool accordingly (`peak_concurrent_first_logins × 2 + steady-state`).

- The formula is already documented in `README.md` (upgrade-notes section, ~line 245). Remaining
  work is only to lift it into a dedicated "Production Deployment Notes" section.

**Context:** Surfaced while writing integration tests; deferred until a deployment-notes doc
section exists to host it.

**Prerequisite:** A "Production Deployment Notes" documentation section (does not exist yet).

## Spec candidate: test hygiene (over-mocking cleanup)

Findings from documenting all 45 test files (class Javadoc + `@DisplayName` + mock audit, done
after Spec 012). They justify their own spec, to be worked out via `/spec-create` with a preceding
`/grill-me`; the points below are raw findings, **not** a finished design. (A third finding — the
`WebhookEventListener` `eventType` bug — has already been fixed directly, commit `c35ccea`.)

Over-mocking in integration tests:

- `ApiKeyDataServiceIntegrationTest` — `@MockitoBean UserService` in a test that otherwise uses a
  real Postgres via Testcontainers. Should seed a real user via `userRepository.save(...)` so the
  "integration" label is honest.
- `ApiKeyDataServiceIntegrationTest` — `@MockitoBean AuthService` is declared only for transitive
  wiring and never read by the tests. Scope it more tightly or replace it with a no-op
  `SecurityContext` setup.
- `CommentServiceIntegrationTest` — `@MockitoSpyBean UserService` is used only for a single
  `verify(never()).findById(...)` assertion. The N+1 claim can be expressed via the Hibernate
  `Statistics` counter (already used in the same class), making the spy obsolete.

Cosmetic: mocks replaceable with real implementations:

- `SlackServiceTest` + `EmailServiceTest` — `mock(ObjectProvider.class)` with
  `@SuppressWarnings("unchecked")`. `ObjectProvider` is a functional interface —
  `(ObjectProvider<X>) () -> instance` is more readable and avoids the cast.
- `ApiKeyAuthenticationFilterTest` — the `FilterChain` mock is conventional, but a
  "RecordingFilterChain" (a real impl recording calls in a list) would be more robust against
  double-invocation bugs.
- `SecurityConfigRoleTest` — the `ApiKeyDataService` mock is a no-op constructor argument. Replace
  with a no-op impl, or remove via factory extraction (`jwtAuthenticationConverter()` as a static
  helper without the `ApiKeyDataService` dependency).
- `AuditLogDataServiceTest` — `UserRepository` + `ApplicationEventPublisher` are dead mock ballast
  in the reader tests; a reader/writer split of the data services would let both go.

Open grill questions for the spec: are there other integration tests with hidden mocking? Should
the "real beans in integration tests, mocks only in unit tests" convention be captured in
`CLAUDE.md`? Is a general "no `mock(...)` for functional interfaces" rule worth enforcing via
SonarQube/PMD? One spec for everything, or split the cleanup in two?

**Context:** Surfaced during the post-Spec-012 test-documentation pass; deferred because it needs a
`/grill-me` + `/spec-create` cycle before implementation. All findings re-verified against the code
on 2026-07-31.

## Clarify the Authentik `name`-claim guarantee

Confirm whether the Authentik configuration guarantees a `name` claim for every user who signs in.
Concretely: does every Authentik user have a maintained `name` attribute, and is the `profile`
scope mapping in the Authentik provider set so that `name` always ends up in the JWT — or can there
be users (e.g. SSO-imported, freshly created without a profile) whose `name` is missing? If
uncertain, we likely need a server-side fallback (`name ← preferred_username ← sub`) before the JWT
reaches spring-services.

**Context:** Module-usage question that needs investigation before we can rely on the `name`
claim.

## Machine-readable module boundaries — ArchUnit as the option, Spring Modulith rejected

Module boundaries in the reactor are pure convention: plain Maven classpath modules without JPMS
(chosen deliberately, since Spring interacts poorly with JPMS), so every `public` type in
`spring-services-core` is reachable from every other module. The gap that actually matters is
**core-internal**: feature-to-feature dependencies are already impossible at the Maven level, so the
realistic violation is a class in `data` reaching into `services.user` — inside one artifact, where
neither the compiler nor the reactor notices. The intended layering (`data` sits *below*
`services.user`) is written down nowhere; it exists only in the maintainer's head, while the likely
authors of a violation are coding agents and new contributors who cannot know it.

**Spring Modulith was evaluated and rejected** — it adds little over plain ArchUnit here. Verified
by a throwaway spike against `spring-modulith-core` 1.4.13 on this reactor (2026-09-10, reverted):

| Module model | Modules detected | Violations today |
| --- | --- | --- |
| Default (`direct-sub-packages` of `com.openelements.spring.base`) | 11 — including a `services` blob of 129 types spanning 6 Maven modules | 33 (2 cycles + 31 non-exposed-type hits, incl. every feature's own auto-config reaching its own `services.<feature>` package) |
| Fine-grained (every logical package declared a module) | 25 | 4 |
| Features-only (core left unmodularized) | 12 | 0 — adoptable without any refactor, but enforces nothing core-internal |

The four fine-grained violations: cycle `data ↔ events`; cycle
`security → services.apikey → services.user → security`; cycle `security → services.user → security`;
and `mcp → security.apikey.ApiKeyAuthenticationFilter` (non-exposed, because `security` has
sub-packages).

Why it was rejected:

- **No consumer concept.** The reference is explicit — Modulith is *"an opinionated toolkit to build
  domain-driven, modular **applications** with Spring Boot"*; there is no library guidance, and
  `@NamedInterface` opens a package only to *other application modules*, never to external consumers.
  Nothing in a library-side test can police consumer access.
- **The model would permanently disagree with the artifacts.** The Maven modules deliberately do not
  own package subtrees, so a Modulith decomposition never matches the jars — every future reader has
  to hold both models in their head.
- **Cost in main sources.** The annotations live in `package-info.java`, which makes
  `spring-modulith-api` a `provided`-scope compile dependency of ~9 modules; the detection strategy is
  the global property `spring.modulith.detection-strategy` (safe only in test scope — in a library
  `application.properties` it would leak into consumer apps); and 1.4.x is pinned to the Boot 3.5 line
  (Boot 4 requires Modulith 2.x).
- **The two benefits that remain are free elsewhere.** A declared dependency direction and a cycle
  guard are fully expressible in plain ArchUnit; only the `@NamedInterface` "exports list" would be
  lost, and nobody enforces it against consumers anyway.

Recorded mechanics, so nobody has to re-spike: `ApplicationModules.of(String package)` needs no
`@SpringBootApplication`; `verify()` *always* asserts freedom of cycles (`beFreeOfCycles`), so it
cannot be adopted while the two cycles exist; `explicitly-annotated` detection resolves
`@ApplicationModule` at any package depth (not just direct sub-packages); `allowedDependencies`
enforcement works and reports precisely.

**The option for later — a plain ArchUnit test** (ArchUnit 1.5.0, test scope only, no main-source
changes, hosted in `spring-services-all/src/test` where the full classpath is present):

- Write the layering down first — that step is independently useful and the cheapest thing here,
  since agents read `CLAUDE.md`. A rule nobody has stated cannot be verified.
- Rule 1: the declared direction, e.g. no class in `..base.data..` may depend on `..base.services..`.
  The parts that are already clean can be enforced immediately.
- Rule 2: `slices().should().beFreeOfCycles()` — red today because of the two known cycles, so it
  either waits for the cycle repayment below or ships `@Disabled` with that reason.
- An encapsulation rule (`..internal..` unreachable from outside its module) presupposes an
  API/internal split that **does not exist today**: every type in the library is public and flat.
  Unresolved: whether consumer apps already import packages that would become internal (e.g.
  `services.webhook.payload`, `services.scim.model`, `data.image.util`).

**Context:** Surfaced in the `/grill-me` session for Spec 014 (multi-module restructuring), Branch E
(API surface between modules), where JPMS was rejected and Modulith parked as the alternative.
Re-examined in a dedicated `/grill-me` session on 2026-09-10 that ran the spike above and concluded:
no spec, keep ArchUnit as the option, and keep this entry as the record.

## Repay the two package cycles in `spring-services-core` (breaking)

`data ↔ events` and `security ↔ services.user` are real import cycles inside
`spring-services-core`. Spec 014 accepted them as permanent design ("cyclically entangled clusters —
each must stay in one module"); that reading is hereby reversed — they are **debt to be repaid**, and
they are the reason no cycle-based architecture test can be switched on today.

- `data → events`: `AbstractDbBackedDataService` constructs `OnObject{Create,Update,Delete}`;
  `events → data`: every event type is parameterized on `WithId` and calls it.
- `security → services.user` / `services.apikey`: `SecurityConfig` `@Import`s `UserConfig` and
  `ApiKeyConfig` and takes `ApiKeyDataService`; `ApiKeyAuthenticationFilter` uses `ApiKeyDataService`
  and `ApiKeyEntity`. Back: `UserService` depends on `AuthService`, `UserProvisioner` and
  `UserService` on `UserInformation`.
- Repaying it means moving public types (`WithId`, `AuthService`, `UserInformation`) into neutral
  packages, so consumer imports break — this needs its own spec and a release that may break.
  The `SecurityConfig` `@Import` half is the cheap part and may be separable: config wiring belongs
  to the auto-configuration, not to `SecurityConfig`.

**Context:** Split out of the Spring Modulith grill session on 2026-09-10 (previous entry), which
required its own spec to stay non-breaking and therefore could not fix the cycles.

## SCIM Groups + membership

Follow-up A from the SCIM provider split. Spec 015 (SCIM Users provider) shipped without Groups —
today `ScimGroupController` is a 501 stub (`GET /Groups` returns an empty list). Needed:
`GroupEntity` + membership table, `/scim/v2/Groups` (POST/GET/PUT/**PATCH** with PatchOp
add/remove/replace on `members`, DELETE), and `ResolvedPrincipal.groups()` served from
`UserEntityPrincipalDirectory` (returns an empty set today).

- Open design questions from the grill sessions: PATCH mechanics, filter grammar beyond `eq`
  (sw/co/AND/OR), ETag/optimistic concurrency, tenant interaction (one SCIM instance per tenant?),
  Authentik vendor extensions, discovery honesty, and group rename/delete effects on derived roles.

**Context:** Deferred from the grill session of 2026-07-11 that split the SCIM 2.0 provider into
part-specs; Spec 015 (done, PR #33 merged 2026-07-15) deliberately excluded Groups.

## SCIM Group→Role mapping

Follow-up B from the SCIM provider split: configurable derivation of `ResolvedPrincipal.roles()`
from group membership, making USER tokens role-aware. `UserEntityPrincipalDirectory` still returns
empty `roles()`/`groups()`.

- Open design question: whether `revokeAllForSubject` on SCIM deactivation is wanted as an
  additional hard-revoke hook.

**Context:** Deferred from the grill session of 2026-07-11 that split the SCIM 2.0 provider into
part-specs.

**Prerequisite:** SCIM Groups + membership (previous entry) must land first.

## Extract an integration module for API keys, PATs & webhooks

API keys, PATs, and webhooks should move out of `spring-services-core` into a separate optional
feature module, following the feature-module pattern established by Spec 014 and applied again in
Spec 016 (`spring-services-tenant`).

**Context:** Captured as a quick note during general work (no detailed provenance recorded); fits
the Spec 014/016 module-extraction pattern.

## Spec candidate: a property naming convention for the whole reactor

The configuration prefixes are inconsistent and nothing decides which is right. Verified inventory of
every `@ConfigurationProperties` in the reactor:

| Prefix | Module |
| --- | --- |
| `open-elements.email` | `spring-services-email` |
| `open-elements.slack` | `spring-services-slack` |
| `openelements.mcp` | `spring-services-mcp` |
| `openelements.scim` | `spring-services-scim` |
| `openelements.db-backup` | `spring-services-dbbackup` |
| `openelements.meilisearch` | `spring-services-search` |

Two spellings of the vendor prefix, and the leaf name follows neither the module name
(`dbbackup` → `db-backup`) nor the technology consistently (`search` → `meilisearch`, i.e. the
implementation leaks into the configuration surface).

This needs its own spec, because it is a coordinated rename across modules:

- Decide the vendor prefix (`openelements` is the majority, 4:2) and the leaf-naming rule — module
  name or feature name, and whether an implementation may appear in a property name at all.
- Decide the deprecation mechanism. Renaming a property is breaking for every consumer; Spring Boot
  offers `additional-spring-configuration-metadata.json` with `deprecation.replacement`, plus
  `@DeprecatedConfigurationProperty`, so the old names can keep working for one release with a
  warning instead of breaking silently.
- Cover the properties that do not exist yet but are already designed: spec 020's
  `openelements.security.own-client-id` and `openelements.token-exchange.targets.*`.

**Context:** Named as its own spec on 2026-09-10 while planning spec 020 (token exchange), which
needs two new properties and should not settle the convention on its own. The naming question was
previously only a side note in *Property toggles and consumer overridability for core security beans*
and a prerequisite of *Caller groups as a first-class type*; both entries should reference this spec
once it exists.

## Distinguish "delegated, actor known" from "delegated, actor unknown"

Spec 020 answers `CallerOrigin.DELEGATED` whether or not the token names the acting party, and
exposes the actor separately via `findActorSubject()`. That is deliberate for step 1: Keycloak never
supplies an actor, so a distinction in the enum would be an Authentik-only value, and an empty
`findActorSubject()` already carries the information.

Worth revisiting if application code turns out to branch on it — e.g. "a delegated call must name its
actor, otherwise reject". That is an authorization rule, so the decision belongs to whoever needs it:
either an application-side check on `findActorSubject().isEmpty()`, or a library-side constant
(`DELEGATED_ANONYMOUS`?), which would grow the closed enum again.

**Context:** Explicitly deferred out of spec 020 (`design.md`, *Open questions*) on 2026-09-10 —
"muss nicht in step 1".

## Enforce audience validation on the JWT chain (**important**)

The library sets **no** audience validator. Spring Boot only adds one when
`spring.security.oauth2.resourceserver.jwt.audiences` is set — `OAuth2ResourceServerJwtConfiguration.getValidators(...)`
(verified in the Boot 3.5.14 sources) otherwise returns the plain default validator, i.e. issuer and
timestamps only. The library reads just `name`, `email`, `picture`, `preferred_username` and `roles`
today; `aud`, `azp` and `client_id` are untouched claim surface.

**Consequence:** all applications share one Authentik issuer, so backend B accepts a token that was
issued for backend A and simply forwarded. To B it looks like a direct user call — including the
audit-log entry, which names the user. As long as that holds, token exchange (see
`docs/ideas/pat-landschaftsanalyse.md`) is **bypassable**: whoever forwards instead of exchanging gets
through, and any recognition of intermediary systems is a label rather than a control.

**The decision to make:** (a) enforce — fail startup without `audiences`; a breaking change that locks
every application out until its IdP side is configured; (b) document and recommend; (c) ratchet — a
property defaulting to off, a startup warning, mandatory from the next major.

**Two measurements are missing first** (fetch one token per IdP and decode it — not a documentation
exercise):

- What does **authentik** put into an access token's `aud`? Not provable from the docs, and no
  recorded token exists in this repository (not even the spec-015 material taken from real Authentik
  traffic contains `aud`).
- What does the **Keycloak** audience mapper actually write? Keycloak requires an *Audience* protocol
  mapper (on the client or on an assigned client scope, *Included Client Audience* + *Add to access
  token*); without it the access token does not name the resource server. The Keycloak mailing list
  reports the mapper inserting a client's internal **UUID** instead of its client ID — so the value
  must be checked on the token itself.

**Context:** Surfaced in the `/grill-me` session of 2026-09-10 on PAT versus token exchange
(`docs/ideas/pat-landschaftsanalyse.md`). Deliberately not implemented right away because token
exchange is being cleaned up first — the gap stays open until then, which is why this is marked
*important*.

## A dedicated `Authentication` type for the SCIM service principal

`ScimTokenAuthenticationFilter` authenticates the SCIM provisioning caller as a plain
`UsernamePasswordAuthenticationToken` with the `String` principal `ScimServicePrincipal.USER_NAME`
and no authorities — the exact shape `@WithMockUser` produces. The two are therefore
**indistinguishable**, which is why Spec 019 classifies the SCIM caller as
`AuthenticationType.OTHER` rather than giving it a constant of its own.

Making it classifiable needs three things, in two modules:

- an interface or marker owned by `spring-services-core` that the SCIM token implements — `core`
  must not depend on `spring-services-scim` (the dependency runs the other way, and the module is
  optional), so `core` cannot match on the SCIM class;
- a new `AuthenticationType` constant, generic (`SERVICE`) rather than SCIM-specific, so a future
  machine principal fits without extending the enum again;
- a `default`-branch audit in consumer code: adding an enum constant breaks an exhaustive `switch`
  without one. Spec 019's Javadoc declares the set extensible precisely to keep this possible in a
  minor release.

It cannot be retrofitted by inspecting today's authentication — the dedicated token type is the
prerequisite, not an implementation detail.

**Context:** Deliberately parked during the `/grill-me` session for Spec 019 (authentication type
probe) on 2026-09-10: no application logic branches on the SCIM caller today, so the two-module
change and the new public API in `core` were not justified. Spec 019 (`design.md`, D6) records the
reasoning.

## Caller groups as a first-class type (`CallerGroups`)

Group membership from a configurable token claim is the natural counterpart to Spec 017's
`AuthService.getRoles()`: read the claim, trim, de-duplicate, fail closed. A reference
implementation exists in `open-tasks` as `TokenGroupsProvider`.

Unlike roles, groups should probably **not** be a bare `Set<String>`. Spec 017 dropped its
`CallerRoles` value type because a role name is only ever a name, and `Set<String>` composes better.
Groups plausibly carry more — id, display name, nesting — which is what would justify a real type.
That distinction is also what removes the "two `Set<String>` constructor arguments are silently
swappable" hazard: once groups are their own type, roles and groups can no longer be interchanged by
accident.

Open questions before this can become a spec: the property name for the claim (which collides with
the still-unresolved property-naming convention in the *Property toggles* entry above), whether
groups need to reach `SCIM Groups + membership`, and whether an empty/absent claim is distinguishable
from "no caller".

**Context:** Explicitly scoped out of Spec 017 (caller role lookup) at the start of its
`/spec-create` session, to keep that spec purely additive and free of new configuration surface.

**Prerequisite:** A property-naming convention for the library (see *Spec candidate: a property
naming convention for the whole reactor*).

## `spring-services-actuator` module

An optional feature module depending on `spring-boot-starter-actuator` and `spring-services-core`.
It would carry the *transport* concerns that spec 018 deliberately left out: serving the unmodified
CycloneDX SBOM (the form a compliance scanner consumes), a Git/Build `InfoContributor` feeding
`/actuator/info`, and — the reason Actuator is wanted in the first place — health checks and
Prometheus metrics. The parsing and the model stay in core; this module only exposes them.

- Must reuse the same SBOM location probe order as `ApplicationInfoService`, so the parsed view and
  the raw download never describe different files.
- Needs a decision on management port and on securing `/actuator/**`, which no app does today.

**Context:** Split out during the `/grill-me` for spec 018 (application build + SBOM info). Actuator
is wanted "later" for health/Prometheus anyway, so SBOM transport rides along rather than pulling
Micrometer into every consuming application now.

**Prerequisite:** Spec 018 (provides the model this module would expose).

## Build wiring for application build metadata

Spec 018 reads three files that no Open Elements build currently produces. The wiring is a
`java-parent` concern plus one line per application repository:

- `project.build.outputTimestamp` fixed in `java-parent` — **in progress separately**; without it
  no Maven build in the org is byte-reproducible, independent of spec 018.
- `spring-boot-maven-plugin:build-info` in `java-parent`'s `pluginManagement`, with
  `additionalProperties` carrying `commit`, activated per application.
- `cyclonedx-maven-plugin` output redirected to
  `${project.build.outputDirectory}/META-INF/sbom/application.cdx.json` for applications.
- `ARG GIT_COMMIT` in the Dockerfiles of `open-crm`, `open-tasks`, `open-expenses`,
  `KnowledgeForge` and `Octobird`, passed into Maven — their `.dockerignore` excludes `.git`, so the
  in-container build produces no Git metadata today.

`META-INF/build-info.properties`, `META-INF/git.properties` and `META-INF/sbom/*` are single-slot
classpath resources: if two jars ship one, only the first is read. Their generation must therefore
sit in an application-level activation, never in a profile shared with library modules — the same
trap that already forced `generateGitPropertiesFile=false` in `java-parent`'s `full-build` profile.

**Context:** Surfaced during the `/grill-me` for spec 018; deferred because `java-parent` is a
separate repository and its `outputTimestamp` change is already being made in parallel.
