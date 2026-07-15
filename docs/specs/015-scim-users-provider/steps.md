# Implementation Steps: SCIM 2.0 Users Provider

Ordered, atomic steps. The build stays green after each. The feature ships as a new optional module
`spring-services-scim` (spec-014 feature-module pattern) plus two additive columns on core's
`UserEntity`. Reference: `design.md` (Parts A–E), `behaviors.md` (~40 scenarios).

---

## Step 1: Core — soft-delete columns on `UserEntity`

- [ ] Add `deleted` (`boolean`, not null, default false) and `deletedAt` (`Instant`, nullable) to
  `spring-services-core` `UserEntity`, with getters/setters + Javadoc.
- [ ] Confirm the schema-guard/entity conventions still hold; no change to `UserDto` or existing REST.

**Acceptance:** core compiles; `./mvnw -o -Pfull-build -pl spring-services-core verify` green (incl.
schema-guard test + 0 javadoc warnings).

**Behaviors:** DELETE soft-marks; DELETE distinguishes deletion from deactivation (data model).

---

## Step 2: Scaffold `spring-services-scim` module

- [ ] New module `spring-services-scim` (parent = reactor; depends on `spring-services-core`); add to
  root `<modules>`, to `spring-services-bom`, and to `spring-services-all`.
- [ ] `ScimAutoConfiguration` (`@AutoConfiguration`, `@ConditionalOnProperty("openelements.scim.token")`)
  + `META-INF/spring/...AutoConfiguration.imports`, in a package outside any `@ComponentScan` root.
- [ ] `ScimProperties` (`@ConfigurationProperties("openelements.scim")`, `token`).

**Acceptance:** reactor builds all modules; module inert (no beans) without the token.

**Behaviors:** Module is inert without a configured token.

---

## Step 3: SCIM model (hand-rolled Jackson records)

- [ ] `ScimUser` (+ `ScimName`, `ScimEmail`, `ScimMeta`), `ScimListResponse<T>`, `ScimError`,
  `ScimServiceProviderConfig`, `ScimResourceType`, `ScimSchema` records with the observed dialect +
  schema URNs.

**Acceptance:** module compiles; record round-trip unit test (serialize/deserialize a captured payload).

**Behaviors:** ListResponse envelope; Error envelope; discovery shapes.

---

## Step 4: SCIM security chain + service principal

- [ ] `ScimServicePrincipal` constants (reserved UUID `…0000cf`, `userName="scim"`, inactive) +
  `ScimServicePrincipalInitializer` (`ApplicationRunner`, idempotent insert — mirrors spec-008 System user).
- [ ] `ScimTokenAuthenticationFilter` — extract `Authorization: Bearer`, constant-time compare
  (`MessageDigest.isEqual`), set SCIM principal auth on success.
- [ ] `ScimAuthenticationEntryPoint` — `401`, `application/scim+json`, `Error` envelope,
  `WWW-Authenticate: Bearer`.
- [ ] `scimFilterChain` bean, `securityMatcher("/scim/v2/**")`, ordered before the default JWT chain,
  wired in `ScimAutoConfiguration`.

**Acceptance:** integration test — valid token authenticates; missing/wrong token → SCIM `401`; SCIM
token rejected on `/api/**`; token compare is constant-time.

**Behaviors:** all four Authentication scenarios; SCIM chain does not leak into JWT chain.

---

## Step 5: `ScimUserService` (mapping + business rules)

- [ ] Map `UserEntity` ↔ `ScimUser` (id, externalId, userName, name←displayName/name.formatted,
  email←primary, active; never read/write `sub`).
- [ ] `create` → `409 uniqueness` on existing `externalId`/`userName` (single seam per Branch B), else
  insert (`sub=NULL`, `deleted=false`). `get` (404 if missing or deleted). `list` (userName eq /
  externalId eq, startIndex/count, exclude soft-deleted + System + SCIM principals). `replace` (PUT
  full replace; undelete when deleted && active:true; never write sub). `softDelete` (active=false,
  deleted=true, deleted_at=now).

**Acceptance:** service unit/integration tests for create/collision/get/list/filter/pagination/put/
undelete/soft-delete.

**Behaviors:** all Users create/read/list/replace/delete scenarios.

---

## Step 6: Controllers + content type + error advice

- [ ] `ScimUserController` (`produces=application/scim+json`): POST/GET/{id}/GET list/PUT/{id}/DELETE.
- [ ] `ScimDiscoveryController`: ServiceProviderConfig, ResourceTypes, Schemas.
- [ ] `ScimGroupController` stub: `GET /Groups` → empty ListResponse; writes → `501`.
- [ ] `application/scim+json` message converter (`WebMvcConfigurer`); SCIM `@RestControllerAdvice`
  mapping validation/uniqueness/not-found/unsupported to the `Error` envelope with `scimType`.

**Acceptance:** MockMvc integration tests across the endpoints; discovery documents honest.

**Behaviors:** discovery; 400 invalidValue; 404 shapes; 409 uniqueness; group stub + 501.

---

## Step 7: Audit attribution for SCIM writes

- [ ] Ensure SCIM create/replace/delete produce audit-log entries attributed to the SCIM principal
  (actor resolved from the security context the filter set). DELETE emits a `DELETE` audit action
  even though the storage op is an UPDATE of `deleted` (controller/service emits the delete event
  explicitly).

**Acceptance:** integration test asserts audit rows with SCIM actor + correct action for POST/PUT/DELETE.

**Behaviors:** SCIM writes attributed to SCIM principal; DELETE records DELETE action.

---

## Step 8: JIT-login correlation regression (spec 012 interaction)

- [ ] Integration test: SCIM-created user (`sub=NULL`) adopted on first JWT login (spec 012 finds by
  externalId, writes sub, no new row); JIT-then-SCIM `POST` → 409. Last-writer-wins name/email note.

**Acceptance:** tests green; no change needed to spec-012 code (verify only).

**Behaviors:** correlation scenarios; field-ownership LWW note.

---

## Step 9: Docs

- [ ] `docs/releases/upgrade-to-X.Y.md`: migration SQL (2 columns + reserved SCIM principal insert),
  `openelements.scim.token` config, isolation/security notes, group-stub/501 caveat, soft-delete +
  deferred GDPR erasure.
- [ ] README: SCIM module + `openelements.scim.token` opt-in.

**Acceptance:** doc contains migration + config + caveats.

---

## Step 10: Full build + reviews

- [ ] `./mvnw -o -Pfull-build clean verify -Dmaven.javadoc.failOnWarnings=true` green (all modules,
  0 javadoc warnings). spec-review + quality-review clean.

**Note (external, non-blocking):** the #21 Authentik-harness verification (409-recovery, group-stub
tolerance) cannot be run here (no Authentik instance). Implemented to the spec's *expected* behavior;
if the harness later deviates, a follow-up spec is opened per the design — this spec is not amended.

---

## Behavior coverage

| Area | Step |
|---|---|
| Authentication (4) + no-leak | 4 |
| Discovery (2) | 6 |
| Create / 409 / 400 (5) | 5, 6 |
| Read / 404 (3) | 5, 6 |
| List / filter / pagination / exclusions (6) | 5, 6 |
| PUT replace / deactivate / undelete / 404 / no-sub (5) | 5, 6 |
| DELETE soft / FK / distinguish / 404 (4) | 5, 6 |
| Audit attribution (2) | 7 |
| JIT correlation (2) + group stub + LWW | 8, 6 |
