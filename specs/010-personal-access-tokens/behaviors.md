# Behaviors: Personal Access Tokens

## User Role Synchronization

### First login provisions roles

- **Given** a JWT with `sub = "u-1"` and `roles = ["APP_USER", "EMPLOYEE"]` for a
  previously unknown user
- **When** the request is processed and `UserService.getCurrentUserEntity()` is called
- **Then** a new `UserEntity` is created with `roles = {"APP_USER", "EMPLOYEE"}`

### Subsequent login adds a new role

- **Given** an existing user `u-1` with persisted `roles = {"APP_USER"}`, and a JWT with
  `roles = ["APP_USER", "EMPLOYEE"]`
- **When** `getCurrentUserEntity()` is called
- **Then** the persisted roles become `{"APP_USER", "EMPLOYEE"}`

### Subsequent login removes a role no longer in the JWT

- **Given** an existing user `u-1` with persisted `roles = {"APP_USER", "EMPLOYEE"}`,
  and a JWT with `roles = ["APP_USER"]`
- **When** `getCurrentUserEntity()` is called
- **Then** the persisted roles become `{"APP_USER"}`

### No DB write when roles are unchanged

- **Given** an existing user with persisted `roles = {"APP_USER"}` and a JWT with
  `roles = ["APP_USER"]`
- **When** `getCurrentUserEntity()` is called
- **Then** no UPDATE is issued for the `user_roles` table (verified via Hibernate
  statistics or a flush listener)

### Missing roles claim is treated as empty

- **Given** a JWT without a `roles` claim, for an existing user `u-1` with persisted
  roles `{"APP_USER"}`
- **When** `getCurrentUserEntity()` is called
- **Then** the persisted roles become an empty set

### Roles are persisted exactly as asserted, without the `ROLE_` prefix

- **Given** a JWT with `roles = ["APP_USER"]`
- **When** the user is provisioned
- **Then** the persisted value in `user_roles.role` is exactly `"APP_USER"`
- **And** the `ROLE_` prefix is applied only when constructing Spring `GrantedAuthority`
  instances at request time

## PAT Creation

### Authenticated user creates a PAT with a name

- **Given** user `u-1` authenticated via JWT, calling
  `PatDataService.create(new PatCreateDto("ci-pipeline", null))`
- **When** the call returns
- **Then** a new `PatEntity` is persisted with `userId = u-1`, `name = "ci-pipeline"`,
  `expiresAt = null`, `revokedAt = null`
- **And** the response `rawToken` starts with `pat_` and contains exactly 48
  alphanumeric characters after the prefix
- **And** the response `tokenPrefix` has the shape `pat_xxxx...yyyy`

### Authenticated user creates a PAT with an expiration date

- **Given** user `u-1` authenticated via JWT, calling
  `PatDataService.create(new PatCreateDto("ci-pipeline", instantOneYearFromNow))`
- **When** the call returns
- **Then** the persisted `expiresAt` equals the requested instant

### Stored hash matches SHA-256 of the raw token

- **Given** a freshly created PAT
- **When** the raw token in the response is SHA-256 hashed
- **Then** the resulting hex equals the persisted `tokenHash`

### Raw tokens are unique across creations

- **Given** two PATs created back-to-back by the same user
- **When** the raw tokens are compared
- **Then** they differ

### Creating a PAT with `expiresAt` in the past is rejected

- **Given** a `PatCreateDto("test", instantInThePast)`
- **When** `create()` is called
- **Then** the call fails with an `IllegalArgumentException` (or a `ResponseStatusException`
  mapped to 400)

### Creating a PAT with a blank name is rejected

- **Given** a `PatCreateDto("", null)`
- **When** `create()` is called
- **Then** the call fails with an `IllegalArgumentException`

### Creating a PAT without an authenticated user fails

- **Given** an empty `SecurityContext`
- **When** `create()` is called
- **Then** the call fails with an `IllegalStateException`, matching the contract of
  `UserService.getCurrentUser()`

## PAT Listing

### User sees only their own PATs

- **Given** user `u-1` owns PATs `p1` and `p2`, and user `u-2` owns `p3`
- **When** `u-1` calls `listForCurrentUser(...)`
- **Then** the result contains `p1` and `p2`, and never `p3`

### Listing never returns the raw token

- **Given** any number of PATs owned by the current user
- **When** `listForCurrentUser(...)` is called
- **Then** every entry in the returned page is a `PatDto` whose fields contain
  `tokenPrefix` but no `rawToken`

### Listing reflects revoked status

- **Given** a PAT `p1` for user `u-1` with `revokedAt = T1`
- **When** `u-1` calls `listForCurrentUser(...)`
- **Then** the returned `PatDto` for `p1` has `revokedAt = T1` (revoked PATs are
  visible, not hidden)

## PAT Revocation

### Owner can revoke their own PAT

- **Given** user `u-1` owns PAT `p1` with `revokedAt = null`
- **When** `u-1` calls `revoke(p1.id)`
- **Then** `p1.revokedAt` becomes a non-null timestamp

### Owner cannot revoke another user's PAT

- **Given** user `u-1` and a PAT `p3` owned by `u-2`
- **When** `u-1` calls `revoke(p3.id)`
- **Then** a `ResponseStatusException(404)` is raised
- **And** `p3.revokedAt` remains `null`

### Revoking a non-existent PAT returns 404

- **Given** no PAT exists for `someRandomUuid`
- **When** `revoke(someRandomUuid)` is called
- **Then** a `ResponseStatusException(404)` is raised

### Revoking an already-revoked PAT is idempotent

- **Given** a PAT with `revokedAt = T1` owned by the caller
- **When** `revoke()` is called again
- **Then** `revokedAt` is unchanged (still `T1`) and no error is raised

## PAT Authentication

### Valid PAT authenticates as owner with owner's roles

- **Given** user `u-1` has persisted `roles = {"APP_USER", "EMPLOYEE"}` and owns a
  non-expired, non-revoked PAT with raw token `pat_abc...xyz`
- **When** a request arrives with `Authorization: Bearer pat_abc...xyz`
- **Then** the resulting `SecurityContext` contains a `PatAuthentication` whose
  principal is `u-1`'s `UserEntity`
- **And** the authorities contain `ROLE_APP_USER` and `ROLE_EMPLOYEE`
- **And** existing handlers protected with `@RequiresAppUser` or `@RequiresEmployee`
  pass authorization

### Revoked PAT is rejected

- **Given** a PAT with `revokedAt != null`
- **When** a request arrives with that token
- **Then** the request is rejected (401, the chain's default for unauthenticated)

### Expired PAT is rejected

- **Given** a PAT with `expiresAt < now()`
- **When** a request arrives with that token
- **Then** the request is rejected (401)

### Unknown PAT is rejected

- **Given** a request with `Authorization: Bearer pat_nosuchtoken123...`
- **When** the request is processed
- **Then** the request is rejected (401)

### JWT bearer tokens bypass the PAT filter

- **Given** a request with `Authorization: Bearer eyJhbGciOiJSUzI1NiI...` (a JWT, no
  `pat_` prefix)
- **When** the request is processed
- **Then** the PAT filter is a no-op
- **And** the JWT filter validates the token as before
- **And** the resulting `Authentication` has authorities derived from the JWT `roles`
  claim (unchanged from today's behavior)

### Role drift between login and PAT use

- **Given** user `u-1` has persisted `roles = {"APP_USER", "EMPLOYEE"}` (from the last
  interactive login) and has created a PAT
- **And** the IdP has since removed `EMPLOYEE` from `u-1`, but the user has not logged
  in again
- **When** a request arrives with the PAT
- **Then** the authentication grants `ROLE_APP_USER` and `ROLE_EMPLOYEE` (the snapshot
  from the last login)
- **And** this is documented as an accepted limitation pending SCIM push sync

### No Authorization header — PAT filter is a no-op

- **Given** a request on the default chain with no `Authorization` header
- **When** the request is processed
- **Then** the PAT filter does not set a `SecurityContext`
- **And** the existing JWT chain returns 401, unchanged

### Token lookup is by hash, never by raw equality

- **Given** any request with a `pat_`-prefixed bearer token
- **When** the token is looked up
- **Then** the raw token is SHA-256 hashed first and the database lookup compares
  hashes — at no point is a raw-token equality check performed against persisted data

## External API Chain Isolation

### PAT on `/api/external/**` is rejected

- **Given** a request `GET /api/external/foo` with `Authorization: Bearer pat_xxx`
- **When** the request is processed
- **Then** the external API chain handles it (the PAT filter is not registered on this
  chain)
- **And** the request is rejected with 401, because the chain accepts only `X-API-Key`

### `X-API-Key` on the default chain is still ignored

- **Given** a request `GET /api/v1/foo` (any URL outside `/api/external/**`) with
  `X-API-Key: crm_xxx` and no `Authorization` header
- **When** the request is processed
- **Then** the default chain ignores `X-API-Key` (unchanged from today)
- **And** the request is rejected with 401

## Token Format

### Raw token shape

- **Given** a freshly generated raw PAT
- **When** the value is inspected
- **Then** it starts with the literal prefix `pat_`
- **And** it is followed by exactly 48 characters from the set
  `[A-Za-z0-9]`
- **And** the characters after the prefix come from a `SecureRandom` source

### Display prefix shape

- **Given** a raw token `pat_AbCdEfGh.....x9Yz`
- **When** the display prefix is built
- **Then** the result is `pat_AbCd...x9Yz` (first 4 chars after `pat_`, then `...`,
  then last 4 chars of the raw token)
