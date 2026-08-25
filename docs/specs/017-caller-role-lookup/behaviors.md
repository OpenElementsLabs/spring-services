# Behaviors: Caller role lookup

Scenarios marked **[chain]** must be exercised through the real Spring Security filter chain; all
others are unit-level, with the `Authentication` bound to the `SecurityContextHolder` by hand.

## Reading the caller's roles

### Roles from a JWT are returned without the authority prefix

- **Given** an `Authentication` whose authorities are `ROLE_IT-ADMIN` and `ROLE_EMPLOYEE`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"IT-ADMIN", "EMPLOYEE"}`
- **And** `result.contains(Roles.ROLE_IT_ADMIN)` is `true`
- **And** `result.contains(Roles.ROLE_EMPLOYEE)` is `true`

### An API-key caller holds the API_KEY role

- **Given** an `Authentication` whose only authority is `ROLE_API_KEY`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"API_KEY"}`
- **And** `result.contains(Roles.ROLE_API_KEY)` is `true`

### An anonymous caller holds the ANONYMOUS role

- **Given** an `AnonymousAuthenticationToken` whose only authority is `ROLE_ANONYMOUS`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"ANONYMOUS"}`
- **And** `result.contains(Roles.ROLE_ANONYMOUS)` is `true`
- **And** the result is **not** empty — an anonymous caller is not represented by an empty set

### An authenticated caller with no authorities yields an empty set

- **Given** an `Authentication` with an empty authority collection (as produced by
  `ScimTokenAuthenticationFilter` for the SCIM service principal)
- **When** `authService.getRoles()` is called
- **Then** the result is empty
- **And** it is therefore distinguishable from the anonymous caller, who yields `{"ANONYMOUS"}`

### A role name not declared in `Roles` is preserved

- **Given** an `Authentication` with the authority `ROLE_SOME-FUTURE-ROLE`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"SOME-FUTURE-ROLE"}`
- **And** `result.contains("SOME-FUTURE-ROLE")` is `true` — no whitelist is applied

### Duplicate authorities collapse into one role name

- **Given** an `Authentication` with the authorities `ROLE_EMPLOYEE` and `ROLE_EMPLOYEE`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"EMPLOYEE"}` and has size 1

## Fail closed

### No authentication in the context yields an empty set

- **Given** an empty `SecurityContext` — no `Authentication` is bound
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set
- **And** no exception is thrown

### A null authority collection yields an empty set

- **Given** an `Authentication` whose `getAuthorities()` returns `null`
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set
- **And** no `NullPointerException` is thrown

### An empty authority collection yields an empty set

- **Given** an `Authentication` whose `getAuthorities()` returns an empty collection
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set

### A null entry inside the authority collection is skipped

- **Given** an `Authentication` whose authorities are `ROLE_EMPLOYEE` and a `null` element
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"EMPLOYEE"}`
- **And** no `NullPointerException` is thrown

### A GrantedAuthority returning a null authority string is skipped

- **Given** an `Authentication` whose authorities are `ROLE_EMPLOYEE` and a `GrantedAuthority` whose
  `getAuthority()` returns `null`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"EMPLOYEE"}`
- **And** no `NullPointerException` is thrown

## Filtering rules

### Scope authorities are not roles

- **Given** an `Authentication` whose authorities are `SCOPE_openid`, `SCOPE_profile` and
  `ROLE_EMPLOYEE`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"EMPLOYEE"}`
- **And** neither `"openid"` nor `"SCOPE_openid"` appears in the result

### An authority carrying only the bare prefix is discarded

- **Given** an `Authentication` whose only authority is exactly `ROLE_`
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set
- **And** no empty-string role name is present

### An authority whose name is only whitespace is discarded

- **Given** an `Authentication` whose only authority is `ROLE_   ` (prefix followed by whitespace)
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set — a whitespace-only name can never match a `Roles` constant

### An authority without the prefix is discarded

- **Given** an `Authentication` whose only authority is `EMPLOYEE` (no `ROLE_` prefix)
- **When** `authService.getRoles()` is called
- **Then** the result is an empty set

### The prefix is stripped exactly once

- **Given** an identity provider declares a role literally named `ROLE_FOO`
- **And** the JWT `roles` claim therefore contains `ROLE_FOO`, which `SecurityConfig` maps to the
  authority `ROLE_ROLE_FOO`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"ROLE_FOO"}` — not `{"FOO"}`
- **And** the role remains matchable by the name the administrator configured

## Case sensitivity

### Role comparison is case-sensitive and agrees with `@RequiresItAdmin`

- **Given** an `Authentication` whose only authority is `ROLE_it-admin`
- **When** `authService.getRoles()` is called
- **Then** the result is exactly `{"it-admin"}`
- **And** `result.contains(Roles.ROLE_IT_ADMIN)` is `false`
- **And** this matches the outcome the same caller would get at a `@RequiresItAdmin` endpoint, which
  expands to the equally exact `hasRole('IT-ADMIN')`

## The returned set

### The returned set is unmodifiable

- **Given** an `Authentication` with the authority `ROLE_EMPLOYEE`
- **When** `authService.getRoles()` is called
- **And** the caller attempts `result.add("IT-ADMIN")` or `result.clear()`
- **Then** an `UnsupportedOperationException` is thrown
- **And** a subsequent call to `authService.getRoles()` still returns `{"EMPLOYEE"}`

### Asking whether the caller holds the null role fails fast

- **Given** any result of `authService.getRoles()`
- **When** `result.contains(null)` is called
- **Then** a `NullPointerException` is thrown — a `null` role name is a programming error at the
  call site, not a security-context state that should degrade closed

### Two calls within the same request return equal sets

- **Given** an `Authentication` with the authorities `ROLE_IT-ADMIN` and `ROLE_EMPLOYEE`
- **When** `authService.getRoles()` is called twice
- **Then** both results are equal
- **And** either result can be held as a snapshot and passed through the request without further
  security-context access

## `findAuthentication()` and `getAuthentication()`

### findAuthentication returns the bound authentication

- **Given** an `Authentication` is bound to the `SecurityContextHolder`
- **When** `authService.findAuthentication()` is called
- **Then** an `Optional` containing exactly that instance is returned

### findAuthentication returns empty when nothing is bound

- **Given** an empty `SecurityContext`
- **When** `authService.findAuthentication()` is called
- **Then** an empty `Optional` is returned
- **And** no exception is thrown

### getAuthentication keeps its throwing contract

- **Given** an empty `SecurityContext`
- **When** `authService.getAuthentication()` is called
- **Then** an `IllegalStateException` with the message `No authentication found` is thrown
- **And** the method is **not** marked `@Deprecated` — both variants remain supported

## Role constants

### All `Roles` constants resolve to their documented literal values

- **Given** the `Roles` utility class
- **When** its constants are read
- **Then** `AUTHORITY_PREFIX` is `"ROLE_"`
- **And** `ROLE_API_KEY` is `"API_KEY"`
- **And** `ROLE_ANONYMOUS` is `"ANONYMOUS"`
- **And** the seven pre-existing business role constants are unchanged

## Authority producers

### The JWT converter builds authorities from the prefix constant

- **Given** a JWT whose `roles` claim contains `IT-ADMIN`
- **When** the `JwtAuthenticationConverter` from `SecurityConfig` converts it
- **Then** the resulting authorities contain `ROLE_IT-ADMIN`
- **And** the produced string is identical to the one produced before this change

### The API-key filter builds its authority from the constants

- **Given** a request carrying a valid `X-API-Key` header
- **When** `ApiKeyAuthenticationFilter` authenticates it
- **Then** the resulting `Authentication` carries exactly the authority `ROLE_API_KEY`
- **And** the produced string is identical to the one produced before this change

## Through the real filter chain

### [chain] An anonymous request to a permitAll path arrives as ANONYMOUS

- **Given** the application boots with the library's production filter chains
- **And** no `Authorization` and no `X-API-Key` header is sent
- **When** an endpoint under `/api/health/**` is called and reports `authService.getRoles()`
- **Then** the response is `200 OK`
- **And** the reported roles are exactly `{"ANONYMOUS"}`
- **And** this proves Spring's `AnonymousAuthenticationFilter` is active on the default chain

### [chain] A JWT roles claim arrives prefix-mapped and stripped

- **Given** the application boots with the library's production filter chains
- **And** a bearer token is presented whose `roles` claim contains `IT-ADMIN` and `EMPLOYEE`
- **And** the token also carries the scope `openid`
- **When** a protected endpoint is called that reports `authService.getRoles()`
- **Then** the reported roles are exactly `{"IT-ADMIN", "EMPLOYEE"}`
- **And** no `SCOPE_openid` or `openid` entry is present

### [chain] A JWT without a roles claim arrives with no roles

- **Given** a bearer token carrying no `roles` claim and the scope `openid`
- **When** a protected endpoint is called that reports `authService.getRoles()`
- **Then** the reported roles are empty

### [chain] An API-key request to the external chain arrives as API_KEY

- **Given** an API key created through `ApiKeyDataService.create(...)`, whose raw value is returned
  exactly once
- **When** an endpoint under `/api/external/**` is called with that value in the `X-API-Key` header
  and reports `authService.getRoles()`
- **Then** the response is `200 OK`
- **And** the reported roles are exactly `{"API_KEY"}`

### [chain] An unauthenticated request to a protected path is still rejected with 401

- **Given** the application boots with the library's production filter chains
- **And** no credentials are sent
- **When** a protected endpoint is called
- **Then** the response is `401 Unauthorized` with the library's JSON error body
- **And** a `WWW-Authenticate: Bearer` header is present
- **And** this confirms that leaving anonymous authentication enabled preserved the error semantics
  established by specs 006 and 011
