# Behaviors: SCIM Foundation — User Model Refactor

## UserEntity schema

### A new UserEntity has all identifying fields populated on creation

- **Given** a JWT-authenticated request from a previously unknown subject `sub = "abc123"`,
  with claims `preferred_username = "alice"`, `email = "alice@example.com"`,
  `name = "Alice"`
- **When** `UserService.getCurrentUserEntity()` is called for the first time
- **Then** a new `UserEntity` row is persisted with
  `sub = "abc123"`, `externalId = "abc123"`, `userName = "alice"`,
  `email = "alice@example.com"`, `name = "Alice"`, `active = true`

### userName falls back to email when preferred_username is missing

- **Given** a JWT without a `preferred_username` claim but with `email = "bob@example.com"`
  and `sub = "xyz789"`
- **When** the user logs in for the first time
- **Then** the resulting `UserEntity` has `userName = "bob@example.com"`

### userName falls back to sub when both preferred_username and email are missing

- **Given** a JWT carrying only `sub = "svc-123"` and `name = "Service Account"`
- **When** the user logs in for the first time
- **Then** the resulting `UserEntity` has `userName = "svc-123"`

### userName falls back to a generated value when all canonical claims are missing

- **Given** a JWT carrying only `sub = ""` (rejected upstream) — this scenario cannot occur in
  practice because `AuthService` already enforces a non-blank `sub`
- **When** code reads `UserInformation.userName()`
- **Then** the value is never `null` — the synthetic `"user-<UUID>"` fallback applies if every
  other source is empty

### Two users sharing the same email both get unique userNames

- **Given** an existing user with `email = "shared@example.com"` and
  `userName = "shared@example.com"` (created via email fallback)
- **When** a second user logs in with `sub = "second-sub"`, no `preferred_username`, and the
  same `email = "shared@example.com"`
- **Then** the second user's `userName` cannot reuse `"shared@example.com"` (unique constraint
  violation) — the JIT-login flow surfaces the conflict as an `IllegalStateException` and the
  request is rejected as `500 Internal Server Error`. Resolution: the IdP must provide
  `preferred_username` to disambiguate.

## JIT-login correlation

### Pre-foundation user is backfilled on next login

- **Given** an existing `UserEntity` row with `sub = "abc"`, `externalId = NULL`,
  `userName = NULL` (created before this spec)
- **When** the same user logs in with a JWT carrying `sub = "abc"`,
  `preferred_username = "alice"`
- **Then** the row is updated in-place: `externalId = "abc"`, `userName = "alice"` — no new row
  is created, the user's `id` is preserved

### Drift sync still updates name, email, avatarUrl

- **Given** an existing user with `name = "Old Name"` and a JWT now asserting
  `name = "New Name"`
- **When** the user makes any authenticated request
- **Then** the `UserEntity.name` is updated to `"New Name"` and persisted — same behavior as
  before this spec

### Drift sync runs in a single save even with multiple field changes

- **Given** a user whose `name`, `email`, `userName`, and `externalId` have all drifted
- **When** the user makes one authenticated request
- **Then** all changed fields are written in a single transaction by
  `UserService.getCurrentUserEntity()`

### Lookup by sub takes precedence over lookup by externalId

- **Given** two `UserEntity` rows: row A with `sub = "X"`, `externalId = NULL`; row B with
  `sub = NULL`, `externalId = "X"` (anomalous state, only reachable post-SCIM)
- **When** a JWT with `sub = "X"` arrives
- **Then** row A is returned (the `findBySub` lookup wins) — row B is not touched

### Lookup by externalId is used only when lookup by sub misses

- **Given** no row with `sub = "Y"` but one row with `externalId = "Y"`, `sub = NULL`
  (a hypothetical SCIM-pre-provisioned row — flow defined for forward compatibility)
- **When** a JWT with `sub = "Y"` arrives
- **Then** the existing row is returned and its `sub` column is populated with `"Y"`; no new
  row is created

### Lookup by userName is the last resort

- **Given** no row with `sub = "Z"`, no row with `externalId = "Z"`, but one row with
  `userName = "zoe"`
- **When** a JWT arrives with `sub = "Z"` and `preferred_username = "zoe"`
- **Then** the existing row is matched, both `sub` and `externalId` are set to `"Z"`, and the
  row is updated

### Creating a brand-new user only happens after all three lookups miss

- **Given** no row matches `sub`, `externalId`, or `userName` from the JWT
- **When** the JIT flow runs
- **Then** exactly one new row is inserted with all fields populated

### Concurrent first logins for the same subject create exactly one row

- **Given** two simultaneous JIT-login requests for the same `sub` (no prior row exists)
- **When** both threads reach the insert step
- **Then** one insert succeeds, the other catches `DataIntegrityViolationException`, refetches
  by `sub`, and returns the row the winning thread created — same recovery as today

## Activation gate

### Active user passes through every auth path

- **Given** a `UserEntity` with `active = true`
- **When** any authenticated request reaches a handler that calls `getCurrentUserEntity()`
- **Then** the entity is returned and the request proceeds normally

### Inactive user is refused on JWT-authenticated request

- **Given** a `UserEntity` with `active = false` and a valid JWT for that user
- **When** the user makes a request that calls `getCurrentUserEntity()`
- **Then** the method throws `AccessDeniedException("User account is disabled")` and Spring
  Security translates it to HTTP `403 Forbidden`

### Inactive user is refused on PAT-authenticated request

- **Given** a valid, non-revoked, non-expired PAT belonging to a user with `active = false`
- **When** a request is sent with `Authorization: Bearer pat_…`
- **Then** the PAT filter rejects the request with `403 Forbidden`; the PAT row is not
  modified — neither revoked nor flagged

### Inactive user cannot be JIT-provisioned by their first JWT

- **Given** a user pre-provisioned externally with `active = false` and no `sub` yet
  *(flow only reachable once the SCIM follow-up spec lands; documented here so the foundation
  enforces it at the correlation step)*
- **When** that user attempts their first interactive login
- **Then** the JIT flow finds the row by `externalId` or `userName`, observes `active = false`,
  and throws `AccessDeniedException` — `sub` is not written onto the row

### Reactivation restores access without token reissue

- **Given** a user toggled from `active = true` to `active = false` and then back to
  `active = true`, holding the original JWT throughout
- **When** the user makes a request with the same JWT
- **Then** `getCurrentUserEntity()` succeeds — no new token, no re-login required

### Reactivation does not un-expire PATs

- **Given** a PAT that has already passed its `expiresAt` while the owner was deactivated
- **When** the owner is reactivated and the same PAT is presented
- **Then** the PAT filter still rejects the request — `expiresAt` is independent of `active`

### Reactivation reinstates a previously-issued, still-valid PAT

- **Given** a PAT that is non-expired and non-revoked, but whose owner was deactivated
- **When** the owner is reactivated and the same PAT is presented
- **Then** the PAT filter accepts the request — the PAT was never revoked, only gated by the
  owner's status

## Database integrity

### Inserting a UserEntity without userName is rejected

- **Given** application code that constructs a `UserEntity` directly and tries to persist it
  without setting `userName`
- **When** the transaction commits
- **Then** the database raises a `NOT NULL` violation on `user_name` — surfaced as
  `DataIntegrityViolationException`

### externalId uniqueness is enforced

- **Given** an existing user with `externalId = "id-1"`
- **When** code attempts to set `externalId = "id-1"` on a second user and persist
- **Then** a unique-constraint violation is raised

### Two users with NULL externalId can coexist

- **Given** two existing rows with `externalId = NULL` (pre-foundation users not yet logged in
  again)
- **When** the schema is queried
- **Then** both rows persist — `UNIQUE` allows multiple `NULL` values in Postgres

### sub uniqueness is unchanged

- **Given** an existing user with `sub = "abc"`
- **When** another row attempts to insert `sub = "abc"`
- **Then** a unique-constraint violation is raised (behavior preserved from before this spec)
