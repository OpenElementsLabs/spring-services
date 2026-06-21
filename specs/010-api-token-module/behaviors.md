# Behaviors: API Token Module

## Token issuance

### Issuing a SERVICE token returns the plaintext exactly once

- **Given** a caller invokes `ApiTokenService.issueServiceToken("svc-A", "ETL", {"billing:read"},
  Duration.ofDays(90))`
- **When** the call returns
- **Then** the returned `IssuedToken.plaintext()` begins with `oe_svc_`, the `tokenId` is the
  middle segment, and a new row exists in `api_token` with `subjectType=SERVICE`,
  `subjectRef="svc-A"`, `scopes={"billing:read"}`, `expiresAt = now + 90d`, `revoked=false`,
  and `tokenHash = SHA-256-hex(plaintext)`

### Issuing a USER token persists only the user reference

- **Given** a caller invokes `ApiTokenService.issueUserToken("alice-sub", "CI", {}, null)`
- **When** the call returns
- **Then** the returned `IssuedToken.plaintext()` begins with `oe_pat_`, the row has
  `subjectType=USER`, `subjectRef="alice-sub"`, `scopes=[]`, `expiresAt=null` — no role, group,
  or active-status data is stored on the row

### Issuing a SERVICE token without scopes logs a warning

- **Given** `issueServiceToken(..., scopes = Set.of(), ...)`
- **When** the call runs
- **Then** the call succeeds and a `WARN` log line records that an unrestricted service token
  was issued (token still works; the warning is operational not enforcement)

### Token plaintext never leaves the system again

- **Given** a successfully issued token
- **When** any subsequent API on `ApiTokenService` or `ApiTokenRepository` returns information
  about that token
- **Then** the plaintext is absent — only the non-secret `tokenId` and metadata are exposed.
  `ApiTokenEntity.toString()` does not include `tokenHash`; `IssuedToken.toString()` redacts
  `plaintext`.

## Token validation — SERVICE

### Valid SERVICE token authenticates the request with its scopes

- **Given** a SERVICE token `oe_svc_<tokenId>_<secret>` issued with `scopes = {"billing:read"}`
- **When** a request arrives with `Authorization: Bearer oe_svc_<tokenId>_<secret>`
- **Then** Spring Security exposes an `OAuth2AuthenticatedPrincipal` whose name is the
  `subjectRef`, whose attributes contain `subject_type=SERVICE` and `scope="billing:read"`, and
  whose authorities contain `SCOPE_billing:read` (and no `ROLE_*`)

### Valid SERVICE token updates last_used_at

- **Given** a SERVICE token with `last_used_at = null`
- **When** a request authenticates with it successfully
- **Then** `last_used_at` on the row is updated to the current instant

## Token validation — USER

### Valid USER token resolves the principal live

- **Given** a USER token for `userRef="alice-sub"`, a `PrincipalDirectory` that returns
  `ResolvedPrincipal(active=true, roles={"editor"}, groups={"finance"}, displayName="Alice")`
- **When** a request authenticates with the token
- **Then** the authenticated principal carries authorities `ROLE_editor` and `ROLE_finance`
  (no `SCOPE_*` because the token has no scopes), and the principal's name is `"alice-sub"`

### Valid USER token with scopes composes live roles AND token scopes

- **Given** a USER token for `userRef="alice-sub"` with `scopes={"reports:export"}`, a
  `PrincipalDirectory` returning `roles={"editor"}, groups={"finance"}, active=true`
- **When** the token authenticates a request
- **Then** the authorities are `ROLE_editor`, `ROLE_finance`, **AND** `SCOPE_reports:export`

### USER token for an inactive user is rejected

- **Given** a USER token for `userRef="bob-sub"`, a `PrincipalDirectory` returning
  `ResolvedPrincipal(active=false, ...)`
- **When** the request is authenticated
- **Then** Spring Security returns `401 Unauthorized` with the `JsonAuthenticationEntryPoint`
  body `{"status":401,"error":"Unauthorized","message":"User is not active"}` and
  `WWW-Authenticate: Bearer`

### USER token referencing an unknown user is rejected

- **Given** a USER token whose `subjectRef` does not resolve via `PrincipalDirectory`
- **When** validation runs
- **Then** the request is rejected with `401` and message `"Token references an unknown user"`

### Role changes propagate on the next request — no logout required

- **Given** a USER token authenticated successfully on request N with authority `ROLE_editor`
- **When** between request N and N+1 the user's `roles` set changes from `{"editor"}` to
  `{"viewer"}` in the `PrincipalDirectory`
- **Then** request N+1 authenticated with the same token sees authority `ROLE_viewer` only —
  the token never carried the roles; only the live lookup does

## Token validation — failure modes

### Malformed token is rejected without DB access

- **Given** an `Authorization: Bearer not-a-real-token` header (no `oe_` prefix)
- **When** the request hits the security chain
- **Then** the token is routed to the JWT path, which rejects it with `401` — the opaque
  introspector is not invoked

### Token with valid prefix but malformed structure is rejected

- **Given** `Authorization: Bearer oe_xyz` (only two segments)
- **When** the request hits the opaque path
- **Then** the introspector throws `BadOpaqueTokenException("Malformed token")` → `401`

### Token with unknown tokenId is rejected

- **Given** an `oe_svc_<tokenId>_<secret>` whose `tokenId` does not exist in `api_token`
- **When** the introspector runs
- **Then** it throws `BadOpaqueTokenException("Unknown token")` → `401`

### Token with valid tokenId but mismatched hash is rejected

- **Given** an `oe_svc_<tokenId>_<wrong-secret>` whose `tokenId` *does* exist but whose
  computed hash does not match the stored `tokenHash`
- **When** the introspector runs
- **Then** the constant-time comparison returns false; the introspector throws
  `BadOpaqueTokenException("Invalid token")` → `401`

### Revoked token is rejected

- **Given** a token row with `revoked=true`
- **When** the holder presents it
- **Then** `BadOpaqueTokenException("Revoked token")` → `401`

### Expired token is rejected

- **Given** a token row with `expiresAt = now - 1 minute`
- **When** the holder presents it
- **Then** `BadOpaqueTokenException("Expired token")` → `401`

### Failed validation does not update last_used_at

- **Given** a revoked, expired, hash-mismatched, or unknown-user token
- **When** validation fails
- **Then** the row's `last_used_at` is not modified (only successful validation touches it)

## Revocation

### Revoking a token by tokenId stops it being accepted

- **Given** an active token with `tokenId=T`
- **When** `ApiTokenService.revoke("T")` is called
- **Then** the row has `revoked=true`, and any subsequent presentation of that token returns
  `401 "Revoked token"`

### Revoking a non-existent tokenId returns false

- **Given** no row exists for `tokenId=X`
- **When** `ApiTokenService.revoke("X")` is called
- **Then** the method returns `false` and no row is modified

### Revoking an already-revoked token returns false

- **Given** a row with `revoked=true`
- **When** `revoke(tokenId)` is called again
- **Then** the method returns `false` (revocation is idempotent at the data level but the API
  reports "no change made")

### revokeAllForSubject revokes every active token of a user

- **Given** four tokens for `subjectRef="alice-sub"`: two `revoked=false`, two `revoked=true`
- **When** `ApiTokenService.revokeAllForSubject("alice-sub")` is called
- **Then** the two previously-active tokens are now `revoked=true`, the call returns `2`, the
  already-revoked rows are unchanged

### revokeAllForSubject is a SCIM-deactivation hook

- **Given** an external SCIM event signals user deactivation
- **When** the SCIM handler calls `revokeAllForSubject(userRef)` and any pending request from
  that user arrives afterwards
- **Then** every token of that user is immediately rejected at the token layer (in addition
  to the `active=false` check via `PrincipalDirectory`, providing defence in depth)

## Listing

### listActiveTokens returns only non-revoked tokens of the subject

- **Given** five tokens for `subjectRef="alice-sub"`: three active, two revoked
- **When** `ApiTokenService.listActiveTokens("alice-sub")` is called
- **Then** exactly three entities are returned, none with `revoked=true`

### Listing exposes no secret material

- **Given** a list of tokens returned from `listActiveTokens(...)`
- **When** any consumer serialises or logs the result
- **Then** no field holds plaintext or hash — only `id`, `tokenId`, `name`, `scopes`,
  timestamps, `revoked`

## Migration

### The `api_key` table is no longer read or written

- **Given** the library is upgraded with this spec implemented
- **When** the application starts
- **Then** the `ApiKeyDataService`, `ApiKeyRepository`, `ApiKeyAuthenticationFilter`,
  `ApiKeyEntity`, the `api_key`/`api_key_scope` tables (from the application's side), and the
  external filter chain are all absent — no startup errors, no fallback path

### Legacy `X-API-Key` header is not honoured

- **Given** a request with `X-API-Key: crm_<legacy-key>` and no `Authorization` header
- **When** the request hits any endpoint
- **Then** Spring Security treats the request as unauthenticated; `X-API-Key` is plain HTTP
  header data with no special handling
