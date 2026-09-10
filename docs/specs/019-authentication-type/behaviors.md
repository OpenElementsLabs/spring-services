# Behaviors: Authentication type probe

Scenarios marked **[chain]** must be exercised through the real Spring Security filter chain; all
others are unit-level, with the `Authentication` bound to the `SecurityContextHolder` by hand. The
scenario marked **[chain: all]** lives in `spring-services-all`, the only module whose classpath
holds both `spring-services-core` and `spring-services-scim`.

## Classifying the caller

### A bearer-token caller is a JWT account

- **Given** an `Authentication` whose principal is a `Jwt`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.JWT_ACCOUNT`
- **And** `authService.getUserInformation()` is present — the constant's documented promise holds

### An API-key caller is recognised by its principal

- **Given** an `Authentication` whose principal is an `ApiKeyEntity`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.API_KEY`
- **And** `authService.getUserInformation()` is empty — an API-key caller has no user

### An anonymous caller is `ANONYMOUS`

- **Given** an `AnonymousAuthenticationToken`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.ANONYMOUS`

### A form-login-shaped caller falls into `OTHER`

- **Given** a `UsernamePasswordAuthenticationToken` whose principal is a `UserDetails` (the shape
  `@WithMockUser` produces)
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.OTHER`

### The SCIM service principal falls into `OTHER`

- **Given** a `UsernamePasswordAuthenticationToken` with the `String` principal
  `ScimServicePrincipal.USER_NAME` and an empty authority collection (the shape
  `ScimTokenAuthenticationFilter` produces)
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.OTHER`
- **And** it is indistinguishable from the previous scenario — the library recognises neither

### No security context yields `NONE`

- **Given** an empty `SecurityContext` (an `@Async` execution, a `@Scheduled` job, a startup runner)
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.NONE`
- **And** no exception is thrown

### The anonymous check wins over the principal type

- **Given** an `AnonymousAuthenticationToken` whose principal is a `Jwt`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.ANONYMOUS`, not `JWT_ACCOUNT`

### A renamed anonymous authority is still `ANONYMOUS`

- **Given** an `AnonymousAuthenticationToken` whose single authority is `ROLE_GUEST` instead of
  `ROLE_ANONYMOUS` (a consumer used `http.anonymous(a -> a.authorities(...))`)
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.ANONYMOUS` — the classification is class-based, not
  authority-based
- **And** `authService.getRoles()` returns `{"GUEST"}`, so the two answers legitimately differ:
  Spec 017 reports role *names*, this spec reports the *kind of caller*

### A JWT carrying the API_KEY role is still a JWT account

- **Given** an `Authentication` whose principal is a `Jwt` and whose authorities include
  `ROLE_API_KEY`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.JWT_ACCOUNT`, not `API_KEY`
- **And** the mechanism is therefore never inferred from a role name (Spec 017, D3)

### A `null` principal does not break the classification

- **Given** an `Authentication` whose `getPrincipal()` returns `null`
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is `AuthenticationType.OTHER`
- **And** no `NullPointerException` is thrown

### The result is never `null`

- **Given** any of the states above
- **When** `authService.getAuthenticationType()` is called
- **Then** the result is a non-`null` `AuthenticationType` constant

## Asking whether the caller is authenticated

### A JWT caller is authenticated

- **Given** an `Authentication` whose principal is a `Jwt` and which reports
  `isAuthenticated() == true`
- **When** `authService.isAuthenticated()` is called
- **Then** the result is `true`

### An API-key caller is authenticated

- **Given** the `Authentication` produced by `ApiKeyAuthenticationFilter`
- **When** `authService.isAuthenticated()` is called
- **Then** the result is `true`

### An `OTHER` caller is authenticated

- **Given** a `UsernamePasswordAuthenticationToken` reporting `isAuthenticated() == true` (SCIM, form
  login, `@WithMockUser`)
- **When** `authService.isAuthenticated()` is called
- **Then** the result is `true`
- **And** the Javadoc therefore must not claim that "authenticated" means `JWT_ACCOUNT` or `API_KEY`

### An anonymous caller is not authenticated

- **Given** an `AnonymousAuthenticationToken`
- **When** `authService.isAuthenticated()` is called
- **Then** the result is `false`
- **And** `authService.findAuthentication()` is **present** — the two answers differ, which is the
  trap this spec closes

### No security context is not authenticated

- **Given** an empty `SecurityContext`
- **When** `authService.isAuthenticated()` is called
- **Then** the result is `false`
- **And** no exception is thrown

### An untrusted token is not authenticated, but keeps its type

- **Given** an `Authentication` whose principal is a `Jwt` and which reports
  `isAuthenticated() == false`
- **When** both new methods are called
- **Then** `getAuthenticationType()` is `AuthenticationType.JWT_ACCOUNT`
- **And** `isAuthenticated()` is `false` — the type says what kind of caller, the probe says whether
  Spring trusts it

## Agreement with method security

### `isAuthenticated()` agrees with `@PreAuthorize("isAuthenticated()")` **[chain]**

- **Given** a test endpoint annotated `@PreAuthorize("isAuthenticated()")` that returns
  `authService.isAuthenticated()`
- **When** it is called with a valid bearer token
- **Then** the endpoint is entered and returns `true`
- **When** it is called on a `permitAll` path variant without credentials
- **Then** SpEL denies access **and** `authService.isAuthenticated()` — observed on a `permitAll`
  endpoint in the same application — is `false`
- **And** the two never disagree

## Through the real filter chains

### A request with a valid bearer token is a JWT account **[chain]**

- **Given** the default filter chain and a valid JWT
- **When** a secured endpoint reads `authService.getAuthenticationType()`
- **Then** the value is `AuthenticationType.JWT_ACCOUNT`
- **And** `authService.isAuthenticated()` is `true`

### A request with a valid API key is an API-key caller **[chain]**

- **Given** the external API chain (`/api/external/**`) and a valid `X-API-Key` header
- **When** the endpoint reads `authService.getAuthenticationType()`
- **Then** the value is `AuthenticationType.API_KEY`
- **And** `authService.isAuthenticated()` is `true`
- **And** `authService.getUserInformation()` is empty

### An unauthenticated request to a `permitAll` path is anonymous **[chain]**

- **Given** the default filter chain and a request to `/api/health/**` without credentials
- **When** the endpoint reads `authService.getAuthenticationType()`
- **Then** the value is `AuthenticationType.ANONYMOUS`
- **And** `authService.isAuthenticated()` is `false`

### A valid bearer token on a `permitAll` path wins over the anonymous filter **[chain]**

- **Given** the default filter chain and a request to a `permitAll` path **with** a valid JWT
- **When** the endpoint reads `authService.getAuthenticationType()`
- **Then** the value is `AuthenticationType.JWT_ACCOUNT`, not `ANONYMOUS`

### A request on the SCIM chain is `OTHER` **[chain: all]**

- **Given** the SCIM filter chain (`/scim/v2/**`) and the configured static SCIM token
- **When** a component reads `authService.getAuthenticationType()` inside that request
- **Then** the value is `AuthenticationType.OTHER`
- **And** `authService.isAuthenticated()` is `true`
- **And** the value is *not* a SCIM-specific constant — see design D6

## API contract

### The constant set is stable

- **Given** the enum `AuthenticationType`
- **When** its constants are enumerated
- **Then** they are exactly `JWT_ACCOUNT`, `API_KEY`, `ANONYMOUS`, `OTHER`, `NONE`
- **And** a future addition is allowed, but a rename or removal must fail this test — both are
  breaking changes for consumers

### Neither new method ever throws

- **Given** any security-context state: absent, anonymous, JWT, API key, `OTHER`, a token with a
  `null` principal, a token reporting `isAuthenticated() == false`
- **When** `getAuthenticationType()` and `isAuthenticated()` are called
- **Then** neither throws
- **And** the fail-closed guarantee documented for `getRoles()` now covers three members

## Regression

### The fail-fast accessors are unchanged

- **Given** an empty `SecurityContext`
- **When** `authService.getAuthentication()` is called
- **Then** it still throws `IllegalStateException`
- **And** `getPrincipalObject()`, `getPrincipalJwt()`, `getUserInformation()` and `getPrincipal()`
  keep their existing fail-fast behaviour
