# Design: SCIM 2.0 Users Provider

## GitHub Issue

[#21 — SCIM traffic capture & provider](https://github.com/OpenElementsLabs/spring-services/issues/21).
The verification test plan this spec depends on is
[issuecomment-4943755149](https://github.com/OpenElementsLabs/spring-services/issues/21#issuecomment-4943755149).

## Summary

Implement the first slice of a SCIM 2.0 service provider in `spring-services`: a bearer-token-authenticated
`/scim/v2/**` endpoint surface that lets an external identity provider (Authentik, and any RFC 7644 client)
**push-provision users** into the library's existing `UserEntity` model. This slice delivers three things:

1. A **dedicated, isolated security filter chain** for `/scim/v2/**` authenticated by a single static
   shared-secret token (`openelements.scim.token`) — not JWT, because Authentik's SCIM provider sends one
   fixed opaque bearer token verbatim on every request.
2. The **SCIM discovery endpoints** (`ServiceProviderConfig`, `ResourceTypes`, `Schemas`) so RFC-compliant
   clients can negotiate capabilities.
3. The **Users resource**: `GET` (list + filter), `POST` (create), `GET /{id}`, `PUT /{id}` (full replace),
   `DELETE /{id}` (soft) — mapped onto the `UserEntity` fields introduced by spec 012
   (`sub`, `externalId`, `userName`, `active`).

This is deliberately the *Users-only* slice. `GroupEntity`, group membership, PATCH-based membership churn,
and Group→Role mapping are separated into follow-up issues (see **Non-goals**). The design is grounded in
**real captured Authentik 2026.5.4 traffic** (issue #21), not in a reading of the RFC alone.

### A note on the load-bearing assumption

The design assumes the two verification tests defined in
[issuecomment-4943755149](https://github.com/OpenElementsLabs/spring-services/issues/21#issuecomment-4943755149)
pass as *Expected*: namely that Authentik recovers from a `409 uniqueness` on `POST` by issuing
`GET /Users?filter=userName eq "…"` and then `PUT`. **If those tests deviate, a follow-up spec is opened based
on the observed behavior** — this spec is not amended in place. The expected results are stated explicitly in
the issue so a deviation is trivial to spot.

## Goals

- Let an IdP create, read, update, list, and delete users over SCIM 2.0 without any prior interactive login.
- Reuse spec 012's `UserEntity` as the single source of truth — a SCIM user and a JIT-login user are the
  **same row**, correlated by `externalId`. No parallel "SCIM user" table.
- Converge correctly regardless of which provisioning path (SCIM push vs. OIDC JIT-login) reaches a given
  human first.
- Keep the SCIM surface isolated from the JWT and API-key chains (spec 006 discipline): a third chain,
  its own token, its own JSON error entry point.
- Make every SCIM write auditable and attributable to a distinct SCIM service principal.
- Be an **optional feature module** (`spring-services-scim`) that is inert unless `openelements.scim.token`
  is configured.

## Non-goals

- **`GroupEntity`, group membership, and `PATCH /scim/v2/Groups`** — follow-up issue A. This slice ships only
  a *graceful stub* for `GET /scim/v2/Groups` (empty `ListResponse`) so a Users-only Authentik reconcile does
  not error; all group writes return `501 Not Implemented`.
- **Group→Role mapping / role-aware USER tokens** — follow-up issue B. `UserEntityPrincipalDirectory` keeps
  returning empty `roles`/`groups` (unchanged from spec 012).
- **GDPR erasure / PII scrubbing.** `DELETE` is soft (see Data model). Physical anonymization is a separate,
  already-planned module (tracked in `docs/TODO.md`).
- **`PATCH /scim/v2/Users`.** The capture shows Authentik updates users exclusively with `PUT` (full replace).
  User-level PATCH is not implemented in this slice; it returns `501 Not Implemented`.
- **Multi-tenancy / one-SCIM-instance-per-tenant**, ETag/optimistic concurrency, and filter grammar beyond
  `userName eq` / `externalId eq` — deferred (see `docs/TODO.md`).
- **Library-shipped Flyway migrations.** As with every prior spec, the library ships an SQL *template* in the
  upgrade doc; consuming apps own their migration timeline (spec 013 contract).

## Technical approach

### Part A — the SCIM security filter chain

A third `SecurityFilterChain` bean, ordered **between** the external API-key chain (`@Order(1)`) and the
default JWT chain (`@Order(2)`) — so `securityMatcher("/scim/v2/**")` claims those requests before the JWT
catch-all:

```java
@Bean
@Order(1)   // registered ahead of the default chain; external API-key chain keeps its own matcher
public SecurityFilterChain scimFilterChain(HttpSecurity http,
        ScimTokenAuthenticationFilter scimTokenFilter,
        JsonAuthenticationEntryPoint scimAuthenticationEntryPoint) throws Exception {
    http.securityMatcher("/scim/v2/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .addFilterBefore(scimTokenFilter, BearerTokenAuthenticationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint(scimAuthenticationEntryPoint))
        .csrf(csrf -> csrf.disable());
    return http.build();
}
```

`ScimTokenAuthenticationFilter` extracts the `Authorization: Bearer <token>` header and compares it against
the configured `openelements.scim.token` using a **constant-time comparison** (`MessageDigest.isEqual` on
UTF-8 bytes) to avoid timing side channels. On success it sets an `Authentication` whose principal is the
reserved **SCIM service principal** (see Part D). On failure it delegates to `scimAuthenticationEntryPoint`,
which emits a SCIM-shaped `401` (`application/scim+json`, `Error` envelope, `WWW-Authenticate: Bearer`).

**Ordering rationale.** The existing external chain uses `securityMatcher("/api/external/**")` and the default
chain has no matcher (it is the fallback). Spring evaluates ordered chains until the first whose matcher
accepts the request. Registering the SCIM chain with a matcher and an order lower than the default guarantees
`/scim/v2/**` is handled here and never reaches the JWT resource-server chain.

**Rationale for a static token, not JWT (Branch A of the grill).** The capture shows Authentik sends one
fixed opaque token (`Authorization: Bearer dummy-token-for-experiment`) on every request — it is a
provider-level secret, not a per-request JWT. Validating it as a shared secret is exactly the model Authentik
implements and adds no dependency on the (not-yet-landed) opaque-token module (spec 010). If the property is
unset, the whole SCIM module is inactive (`@ConditionalOnProperty(openelements.scim.token)`), so a consumer
who does not use SCIM ships nothing extra.

### Part B — discovery endpoints

Three read-only `GET` endpoints returning static, capability-honest documents:

| Endpoint | Purpose |
|---|---|
| `GET /scim/v2/ServiceProviderConfig` | advertises supported features: `patch:false` (Users), `filter:{supported:true, maxResults:200}`, `bulk:false`, `changePassword:false`, `sort:false`, `etag:false`, authenticationSchemes = oauthbearertoken |
| `GET /scim/v2/ResourceTypes` | lists the `User` resource type (and `Group` as a declared-but-stubbed type, so clients don't choke) |
| `GET /scim/v2/Schemas` | returns the core `urn:ietf:params:scim:schemas:core:2.0:User` schema |

Authentik in the capture did **not** call these before provisioning, but shipping them is cheap RFC
compliance and other IdPs (Okta, Entra) do probe them. The documents are honest about what this slice does not
support (`patch:false`, no group writes), so a client that reads them won't attempt unsupported operations.

### Part C — the Users resource

Mapping between the SCIM `User` representation (as observed in the capture) and `UserEntity`:

| SCIM attribute | `UserEntity` field | Notes |
|---|---|---|
| `id` (server-assigned) | `id` (UUID PK) | We generate it; RFC 7643 §3.1. Returned on create, echoed by client on `PUT`/`DELETE`. |
| `externalId` (client-assigned) | `externalId` | Authentik's stable id; the correlation key. |
| `userName` | `userName` | RFC unique business key. Uniqueness enforced by DB constraint (spec 012). |
| `name.formatted` / `displayName` | `name` | We store the display name; `givenName`/`familyName` are accepted but not persisted separately in this slice. |
| `emails[].value` (primary) | `email` | The primary email; non-primary entries ignored. |
| `active` | `active` | Directly maps to spec 012's activation gate. |
| — | `sub` | **Never written by SCIM.** Stays `NULL` until first OIDC JIT-login (spec 012 correlates then). |

Endpoint behavior:

- **`POST /scim/v2/Users`** — validate the payload, then:
  - If a row already exists with the same `externalId` **or** `userName` → **`409 Conflict`** with
    `scimType: "uniqueness"` (RFC 7644 §3.3). This is the collision case; recovery is the client's job
    (verified against Authentik at the #21 harness).
  - Otherwise create a new `UserEntity` (`sub = NULL`, `active` from payload, `deleted = false`) and return
    `201 Created` with the assigned `id` and a `Location` header.
- **`GET /scim/v2/Users/{id}`** — return the user as a SCIM `User`, or `404` if not found **or**
  `deleted = true` (a soft-deleted user is "gone" from the SCIM view).
- **`GET /scim/v2/Users`** — return a `ListResponse`. Supports `filter=userName eq "…"` and
  `filter=externalId eq "…"`, plus `startIndex`/`count` pagination. Soft-deleted rows are excluded. Both the
  reserved System user and the SCIM service principal are excluded from results.
- **`PUT /scim/v2/Users/{id}`** — full replace of `userName`, `name`, `email`, `active` from the payload
  (RFC 7644 §3.5.1). If the target row is soft-deleted, a `PUT` carrying `active:true` **undeletes** it
  (clears `deleted`/`deleted_at`). `404` if the id is unknown.
- **`DELETE /scim/v2/Users/{id}`** — **soft delete**: set `active = false`, `deleted = true`,
  `deleted_at = now()`. Returns `204 No Content`. The row and its PII are retained for referential integrity
  (audit-log FK from spec 008, comment author FK from spec 009); PII scrubbing is the future GDPR module's job.

The active flag flows straight into spec 012's gate: a SCIM-deactivated or SCIM-deleted user is refused on
both the JWT and opaque-token auth paths with no extra code.

**Collision handling isolation (Branch B).** The `409`-vs-adopt decision lives behind a single seam
(`ScimUserService.create`). Should the #21 verification show Authentik cannot recover from `409`, switching to
"adopt the existing row and return it" is a one-method change, not a redesign.

### Part D — the SCIM service principal (audit attribution)

Every write through `AbstractDbBackedDataService` fires an `OnObjectCreate/Update/Delete` event, which the
`AuditLogEventListener` (spec 005/008) records against a `UserEntity` actor resolved from the security context.
A static SCIM token carries no human principal, so this slice bootstraps a **reserved SCIM service principal**
— a `UserEntity` with a fixed reserved UUID, `active = false` (it can never log in), `userName = "scim"` —
mirroring the System user from spec 008. The `ScimTokenAuthenticationFilter` puts this principal into the
security context, so every SCIM-driven audit entry is unambiguously attributable to "SCIM" and distinguishable
from internal System actions and from human users.

### Part E — content type & error envelope (RFC compliance)

- All request/response bodies use `application/scim+json`. A `WebMvcConfigurer` registers a
  `MappingJackson2HttpMessageConverter` bound to that media type; controllers declare
  `produces = "application/scim+json"`.
- List responses use `urn:ietf:params:scim:api:messages:2.0:ListResponse`
  (`totalResults`, `itemsPerPage`, `startIndex`, `Resources`).
- All 4xx/5xx use `urn:ietf:params:scim:api:messages:2.0:Error` (`status`, `scimType`, `detail`) via a
  SCIM-scoped `@RestControllerAdvice`, reusing the uniform-error philosophy of spec 011 but in the SCIM shape.

### Module & dependency choice

A new optional Maven module `spring-services-scim` depending on `spring-services-core`, following the
spec-014 feature-module pattern (slack/email/mcp): a `ScimAutoConfiguration` registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, guarded by
`@ConditionalOnProperty("openelements.scim.token")`.

**SCIM model types: hand-rolled Jackson records, not an external SCIM SDK.** The observed Authentik surface is
small and precisely known from the capture; the codebase convention is record-DTOs with `@Schema`. Hand-rolled
records keep full control over the exact dialect, avoid a heavy transitive dependency in an optional module,
and let the `@ConditionalOnProperty` guard stand alone (an SDK would tempt an `@ConditionalOnClass` marker with
no functional benefit). *(Open question — see below — if a later Groups/PATCH slice makes RFC filter/patch
parsing costly to hand-maintain, revisit adopting a SCIM SDK then.)*

## Data model

### `UserEntity` additions

Two new columns on the existing `users` table (no new entity — SCIM users *are* `UserEntity` rows):

```java
@Column(name = "deleted", nullable = false)
private boolean deleted = false;          // NEW — true after a SCIM DELETE (soft)

@Column(name = "deleted_at")
private Instant deletedAt;                // NEW — timestamp of the SCIM DELETE, else NULL
```

Rationale (Branch D): `active = false` alone cannot distinguish a *deleted* user from a merely *deactivated*
one (`PUT active:false`). The future GDPR module needs to find explicitly-deleted rows; two markers
(`deleted` + `deleted_at`) record both the fact and the time, so retention policy can act on either.

### Entity-relationship (delta only)

```mermaid
erDiagram
    USERS {
        UUID id PK "SCIM id (server-assigned)"
        string external_id UK "SCIM externalId (Authentik)"
        string user_name UK "SCIM userName"
        string sub UK "NULL for SCIM-only rows until first JIT login"
        boolean active "SCIM active + spec-012 gate"
        boolean deleted "NEW — soft-delete marker"
        timestamp deleted_at "NEW — soft-delete time"
        string name
        string email
    }
```

### Migration template (for consuming apps' Flyway)

```sql
-- Vx__scim_users_provider.sql
ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
-- reserved SCIM service principal (fixed UUID, inactive); adapt to your bootstrap approach
INSERT INTO users (id, user_name, name, active, deleted)
VALUES ('00000000-0000-0000-0000-0000000000cf', 'scim', 'SCIM Provisioning', FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;
```

Library integration tests use Hibernate DDL generation against Testcontainers, validating the JPA mapping
independently of the app-side script.

## Key flows

### Flow 1 — POST create (no collision)

```mermaid
sequenceDiagram
    participant A as Authentik
    participant C as ScimUserController
    participant S as ScimUserService
    participant DB as DB
    A->>C: POST /scim/v2/Users {userName, externalId, active, ...}
    C->>S: create(scimUser)
    S->>DB: findByExternalId / findByUserName
    DB-->>S: empty
    S->>DB: insert UserEntity (sub=NULL, deleted=false)
    DB-->>S: row (id=UUID)
    S-->>C: created
    C-->>A: 201 Created {id}, Location header
```

### Flow 2 — POST collision → 409 → client recovery (assumed Authentik behavior)

```mermaid
sequenceDiagram
    participant A as Authentik
    participant C as ScimUserController
    participant S as ScimUserService
    A->>C: POST /scim/v2/Users {externalId already known}
    C->>S: create(scimUser)
    S-->>C: UniquenessConflict
    C-->>A: 409 Conflict {scimType:"uniqueness"}
    Note over A: assumed recovery (verified at #21 harness)
    A->>C: GET /scim/v2/Users?filter=userName eq "…"
    C-->>A: 200 ListResponse [existing user, id]
    A->>C: PUT /scim/v2/Users/{id}
    C-->>A: 200 OK
```

### Flow 3 — DELETE (soft) and later undelete

```mermaid
sequenceDiagram
    participant A as Authentik
    participant C as ScimUserController
    participant DB as DB
    A->>C: DELETE /scim/v2/Users/{id}
    C->>DB: set active=false, deleted=true, deleted_at=now
    C-->>A: 204 No Content
    Note over A,C: user re-added later → POST → 409 → recovery PUT active:true
    A->>C: PUT /scim/v2/Users/{id} {active:true}
    C->>DB: set active=true, deleted=false, deleted_at=NULL
    C-->>A: 200 OK
```

## Dependencies

- **Spec 012 (SCIM foundation)** — hard prerequisite. Provides `externalId`/`userName`/`active`, the tiered
  JIT correlation, and the activation gate this slice writes into.
- **Spec 006 (split filter chains)** — the isolation pattern the SCIM chain follows.
- **Spec 011 (uniform JSON errors)** — the error-envelope philosophy, re-shaped for SCIM.
- **Spec 005/008 (audit log + user FK)** — the audit path SCIM writes flow through; also the reason DELETE is
  soft (FK integrity).
- **Spec 013 / 014 (schema + multi-module)** — SCIM ships as an optional `spring-services-scim` module in the
  fixed `oe_spring_services` schema.
- No dependency on spec 010 (opaque-token) — the SCIM token is a standalone shared secret.
- No new external runtime dependency (hand-rolled SCIM records).

## Security considerations

- **Static token is a high-value secret.** It grants full user-provisioning power. It is compared in constant
  time, never logged, and the module is inert without it. Operators must supply it via a secret manager, not
  in plaintext config. Rotation = change the property + the Authentik provider token (no server state).
- **SCIM chain is fully isolated.** No JWT resource-server, no API-key filter on `/scim/v2/**`; the JWT chain
  never sees these requests. A leaked SCIM token cannot be used against `/api/**` and vice versa.
- **Activation gate reuse.** SCIM deactivation/deletion immediately blocks all other auth paths via spec 012 —
  no window where a SCIM-disabled user still holds a valid session on the JWT side.
- **Enumeration.** List/filter is authenticated; `404` for deleted users avoids leaking soft-deleted PII.

## GDPR (DSGVO) considerations

Personal data handled: `userName`, `name`, `email`, `externalId` (all already present via spec 012 / JWT —
no new *category* of PII). Legal basis is the operator's employment/contract relationship with the IdP-managed
user (Art. 6(1)(b)/(f)). **Data-subject erasure is deliberately deferred**: `DELETE` soft-deactivates and marks
the row; it does **not** scrub PII, because the audit-log (spec 008) and comment (spec 009) foreign keys must
stay referentially intact. The `deleted`/`deleted_at` markers exist precisely so the planned anonymization
module can find and scrub these rows on a retention schedule (tracked in `docs/TODO.md`). This spec must not be
presented as GDPR-erasure-complete on its own.

## Backwards compatibility

- Two additive nullable/default columns (`deleted`, `deleted_at`) — no existing data invalidated.
- No change to `UserDto` or any existing REST contract; SCIM is a new, separately-authenticated surface.
- New optional module — existing consumers who don't set `openelements.scim.token` see no behavior change.

## Open questions

- **Authentik `409` recovery (blocking-for-confidence, not blocking-for-authoring).** Verified via the #21
  harness test plan. If Authentik does not recover, follow-up spec switches `POST` to adopt-existing.
- **SCIM SDK vs. hand-rolled** — hand-rolled for this slice; revisit if the Groups/PATCH follow-up makes RFC
  filter/patch parsing expensive to maintain by hand.
- **`GET /scim/v2/Groups` stub shape** — empty `ListResponse` chosen to keep a Users-only Authentik reconcile
  from erroring; confirm at the harness that an empty group list does not trip Authentik.
- **Reserved SCIM principal UUID** — proposed `…0000cf`; must not collide with the System user's reserved nil
  UUID (spec 008). Final value fixed at implementation.
