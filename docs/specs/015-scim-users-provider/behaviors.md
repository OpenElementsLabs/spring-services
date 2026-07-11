# Behaviors: SCIM 2.0 Users Provider

All scenarios are backend/HTTP scenarios against the `/scim/v2/**` surface. Media type is
`application/scim+json` throughout unless stated otherwise.

## Authentication (SCIM filter chain)

### Valid SCIM token is accepted

- **Given** `openelements.scim.token` is configured to `secret-123`
- **When** a request arrives with `Authorization: Bearer secret-123`
- **Then** the request is authenticated as the reserved SCIM service principal and proceeds to the controller

### Missing bearer token is rejected

- **Given** the SCIM module is active
- **When** a request to `/scim/v2/Users` arrives with no `Authorization` header
- **Then** the response is `401 Unauthorized`, `Content-Type: application/scim+json`, an `Error` envelope,
  and a `WWW-Authenticate: Bearer` header

### Wrong bearer token is rejected

- **Given** `openelements.scim.token = secret-123`
- **When** a request arrives with `Authorization: Bearer wrong`
- **Then** the response is `401 Unauthorized` with the SCIM `Error` envelope, and the token comparison is
  constant-time (no early return on first differing byte)

### Module is inert without a configured token

- **Given** `openelements.scim.token` is not set
- **When** the application context starts
- **Then** no SCIM filter chain, controller, or bean is registered, and `/scim/v2/**` is handled by the
  default JWT chain (i.e. `401` as an unknown protected path)

### SCIM chain does not leak into the JWT chain

- **Given** a valid SCIM token and a valid JWT
- **When** the SCIM token is presented to `/api/foo` (a JWT-chain path)
- **Then** the request is rejected `401` — the SCIM token is not accepted outside `/scim/v2/**`

## Discovery

### ServiceProviderConfig advertises honest capabilities

- **Given** an authenticated SCIM request
- **When** `GET /scim/v2/ServiceProviderConfig` is called
- **Then** the document reports `patch.supported = false`, `filter.supported = true`, `bulk.supported = false`,
  `etag.supported = false`, and an `oauthbearertoken` authentication scheme

### ResourceTypes and Schemas are served

- **Given** an authenticated SCIM request
- **When** `GET /scim/v2/ResourceTypes` and `GET /scim/v2/Schemas` are called
- **Then** each returns `200` with the `User` resource type / core User schema in valid SCIM shape

## Users — create (POST)

### Create a brand-new user

- **Given** no `UserEntity` matches `externalId = "ext-1"` or `userName = "alice"`
- **When** `POST /scim/v2/Users` arrives with `{userName:"alice", externalId:"ext-1", displayName:"Alice",
  emails:[{value:"alice@example.com", primary:true}], active:true}`
- **Then** a new `UserEntity` is persisted with `userName="alice"`, `externalId="ext-1"`, `name="Alice"`,
  `email="alice@example.com"`, `active=true`, `sub=NULL`, `deleted=false`, and the response is `201 Created`
  with a server-assigned `id` (the row UUID) and a `Location` header

### Create does not populate sub

- **Given** a successful `POST` create
- **When** the row is inspected
- **Then** `sub` is `NULL` — SCIM never writes `sub`; it is populated only by a later OIDC JIT-login

### POST with an existing externalId returns 409 uniqueness

- **Given** a `UserEntity` already exists with `externalId = "ext-1"` (e.g. created by an earlier JIT-login)
- **When** `POST /scim/v2/Users` arrives with `externalId = "ext-1"`
- **Then** the response is `409 Conflict` with an `Error` envelope carrying `scimType: "uniqueness"`, and no
  new row is created

### POST with an existing userName returns 409 uniqueness

- **Given** a `UserEntity` already exists with `userName = "alice"`
- **When** `POST /scim/v2/Users` arrives with `userName = "alice"` and a different `externalId`
- **Then** the response is `409 Conflict` with `scimType: "uniqueness"`, and no new row is created

### POST missing required userName is rejected

- **Given** an authenticated SCIM request
- **When** `POST /scim/v2/Users` arrives with no `userName`
- **Then** the response is `400 Bad Request` with an `Error` envelope (`scimType: "invalidValue"`)

## Users — read (GET)

### Get an existing user by id

- **Given** a `UserEntity` with `id = U`, `active = true`, `deleted = false`
- **When** `GET /scim/v2/Users/U` is called
- **Then** the response is `200` with a SCIM `User` where `id = U`, `userName`, `externalId`, `displayName`,
  `emails`, and `active` reflect the row

### Get a soft-deleted user returns 404

- **Given** a `UserEntity` with `deleted = true`
- **When** `GET /scim/v2/Users/{id}` is called for that row
- **Then** the response is `404 Not Found` with an `Error` envelope — a soft-deleted user is absent from the
  SCIM view

### Get an unknown id returns 404

- **Given** no row with `id = ghost`
- **When** `GET /scim/v2/Users/ghost` is called
- **Then** the response is `404 Not Found`

## Users — list & filter

### List returns a ListResponse envelope

- **Given** three active, non-deleted users exist
- **When** `GET /scim/v2/Users` is called
- **Then** the response is a `urn:ietf:params:scim:api:messages:2.0:ListResponse` with `totalResults`,
  `itemsPerPage`, `startIndex`, and a `Resources` array

### Filter by userName eq resolves an existing user

- **Given** a user with `userName = "alice"` exists
- **When** `GET /scim/v2/Users?filter=userName eq "alice"` is called
- **Then** the `ListResponse` contains exactly that one user with its `id`

### Filter by externalId eq resolves an existing user

- **Given** a user with `externalId = "ext-1"` exists
- **When** `GET /scim/v2/Users?filter=externalId eq "ext-1"` is called
- **Then** the `ListResponse` contains exactly that one user

### Filter with no match returns an empty ListResponse

- **Given** no user matches `userName = "ghost"`
- **When** `GET /scim/v2/Users?filter=userName eq "ghost"` is called
- **Then** the response is a `ListResponse` with `totalResults = 0` and an empty `Resources` array (not `404`)

### Soft-deleted and reserved principals are excluded from listings

- **Given** one soft-deleted user, the reserved System user, and the reserved SCIM service principal exist
  alongside two normal users
- **When** `GET /scim/v2/Users` is called
- **Then** only the two normal users appear; the deleted row and both reserved principals are excluded

### Pagination honours startIndex and count

- **Given** five users exist
- **When** `GET /scim/v2/Users?startIndex=3&count=2` is called
- **Then** the `ListResponse` returns users 3–4 with `startIndex = 3`, `itemsPerPage = 2`, `totalResults = 5`

## Users — replace (PUT)

### PUT replaces mutable fields

- **Given** a user `id = U` with `name = "Alice"`, `email = "alice@example.com"`, `active = true`
- **When** `PUT /scim/v2/Users/U` arrives with `displayName = "Alice Changed"`,
  `emails = [{value:"changed@example.com", primary:true}]`, `active = true`
- **Then** the row is updated to `name = "Alice Changed"`, `email = "changed@example.com"`, and the response is
  `200` with the updated `User`

### PUT with active:false deactivates the user

- **Given** a user `id = U` with `active = true`
- **When** `PUT /scim/v2/Users/U` arrives with `active = false`
- **Then** `active` becomes `false`, and subsequent JWT/opaque-token auth for that user is refused via the
  spec-012 gate (`403`/`401` respectively)

### PUT on a soft-deleted user with active:true undeletes it

- **Given** a user `id = U` with `deleted = true`, `deleted_at` set, `active = false`
- **When** `PUT /scim/v2/Users/U` arrives with `active = true`
- **Then** `deleted` becomes `false`, `deleted_at` becomes `NULL`, `active` becomes `true`, and the user is
  visible again via `GET`

### PUT on an unknown id returns 404

- **Given** no row with `id = ghost`
- **When** `PUT /scim/v2/Users/ghost` is called
- **Then** the response is `404 Not Found`

### PUT does not write sub

- **Given** a user `id = U` with `sub = NULL`
- **When** any `PUT /scim/v2/Users/U` is applied
- **Then** `sub` remains `NULL` — SCIM never writes `sub`

## Users — delete (DELETE, soft)

### DELETE soft-deactivates and marks the row

- **Given** a user `id = U` with `active = true`, `deleted = false`
- **When** `DELETE /scim/v2/Users/U` is called
- **Then** the row persists with `active = false`, `deleted = true`, `deleted_at` set to now, the response is
  `204 No Content`, and no row is physically removed

### DELETE preserves foreign-key references

- **Given** a user `id = U` who authored a comment and appears as an audit-log actor
- **When** `DELETE /scim/v2/Users/U` is called
- **Then** the comment and audit-log rows still reference `U` — no FK violation, no cascade delete

### DELETE distinguishes deletion from mere deactivation

- **Given** user A was `PUT` with `active:false` (deactivated) and user B was `DELETE`d (soft-deleted)
- **When** the rows are inspected
- **Then** A has `active=false, deleted=false, deleted_at=NULL`; B has `active=false, deleted=true,
  deleted_at` set — the future GDPR module can select B and not A

### DELETE of an unknown id returns 404

- **Given** no row with `id = ghost`
- **When** `DELETE /scim/v2/Users/ghost` is called
- **Then** the response is `404 Not Found`

## Audit & principal attribution

### SCIM writes are attributed to the SCIM service principal

- **Given** a valid SCIM token
- **When** `POST`, `PUT`, or `DELETE` on `/scim/v2/Users` succeeds
- **Then** an audit-log entry is written with the actor set to the reserved SCIM service principal (not the
  System user, not a human user), recording the correct action (INSERT/UPDATE/DELETE)

### SCIM DELETE records a DELETE (not UPDATE) audit action

- **Given** a valid SCIM token
- **When** `DELETE /scim/v2/Users/{id}` soft-deletes a row
- **Then** the audit entry records action `DELETE` even though the underlying operation is an UPDATE of the
  `deleted` flag *(behavior note: the SCIM controller emits the delete event explicitly so the audit trail
  reflects intent)*

## Correlation with JIT-login (spec 012 interaction)

### SCIM-created user is adopted on first OIDC login

- **Given** a `UserEntity` created via SCIM with `externalId = "ext-1"`, `userName = "alice"`, `sub = NULL`
- **When** the same human logs in interactively with a JWT carrying `sub = "ext-1"`
- **Then** spec 012's tiered lookup finds the row by `externalId`, writes `sub = "ext-1"` onto it, and no new
  row is created — SCIM row and JIT row are one

### JIT-created user then provisioned by SCIM collides deterministically

- **Given** a `UserEntity` created by JIT-login with `externalId = "ext-1"`, `sub = "ext-1"`
- **When** Authentik later `POST`s `/scim/v2/Users` with `externalId = "ext-1"`
- **Then** the server returns `409 uniqueness` (the row exists); recovery is the client's responsibility

### Group stub does not break a Users-only reconcile

- **Given** the Users-only slice is deployed (no Groups implementation)
- **When** Authentik calls `GET /scim/v2/Groups` during reconcile
- **Then** the response is `200` with an empty `ListResponse`, and any group write (`POST/PUT/PATCH/DELETE
  /scim/v2/Groups`) returns `501 Not Implemented`

## Field ownership (Last-Writer-Wins — documented simplification)

### SCIM PUT and JIT drift-sync both write name/email without a guard

- **Given** a SCIM-managed user whose `name` was set to "X" by a SCIM `PUT`
- **When** the same user logs in with a JWT asserting `name = "Y"`
- **Then** the JIT drift-sync overwrites `name` to "Y" (last writer wins); a subsequent SCIM `PUT` may set it
  back to "X" — no ownership guard exists in this slice (single-IdP setups keep both values equal)
