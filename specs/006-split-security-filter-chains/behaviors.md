# Behaviors: Split SecurityFilterChain into Two Isolated Chains

## External API Chain (`/api/external/**`)

### Valid API key on GET request succeeds

- **Given** a valid API key exists in the database
- **When** a GET request is sent to `/api/external/comments` with the `X-API-Key` header containing the valid key
- **Then** the response status is 200
- **And** the security context contains an `ApiKeyAuthentication` with `ROLE_API_KEY` authority

### Valid API key on HEAD request succeeds

- **Given** a valid API key exists in the database
- **When** a HEAD request is sent to `/api/external/comments` with the `X-API-Key` header containing the valid key
- **Then** the response status is 200
- **And** the security context contains an `ApiKeyAuthentication`

### Valid API key on OPTIONS request succeeds

- **Given** a valid API key exists in the database
- **When** an OPTIONS request is sent to `/api/external/comments` with the `X-API-Key` header containing the valid key
- **Then** the response status is 200

### Valid API key on POST request is denied

- **Given** a valid API key exists in the database
- **When** a POST request is sent to `/api/external/comments` with the `X-API-Key` header containing the valid key
- **Then** the response status is 403 Forbidden
- **And** the request does not reach the controller

### Valid API key on PUT request is denied

- **Given** a valid API key exists in the database
- **When** a PUT request is sent to `/api/external/comments/123` with the `X-API-Key` header containing the valid key
- **Then** the response status is 403 Forbidden

### Valid API key on DELETE request is denied

- **Given** a valid API key exists in the database
- **When** a DELETE request is sent to `/api/external/comments/123` with the `X-API-Key` header containing the valid key
- **Then** the response status is 403 Forbidden

### Invalid API key is rejected

- **Given** no API key with the provided value exists in the database
- **When** a GET request is sent to `/api/external/comments` with the `X-API-Key` header containing an invalid key
- **Then** the response status is 401 Unauthorized
- **And** the response body contains a JSON error message

### Missing API key header is rejected

- **Given** no `X-API-Key` header is present on the request
- **When** a GET request is sent to `/api/external/comments`
- **Then** the response status is 401 Unauthorized

### JWT bearer token on external endpoint is rejected

- **Given** a valid JWT bearer token exists
- **When** a GET request is sent to `/api/external/comments` with the `Authorization: Bearer` header but no `X-API-Key` header
- **Then** the response status is 401 Unauthorized
- **And** the JWT is not evaluated (no JWT filter on this chain)

### Both API key and JWT on external endpoint uses API key only

- **Given** a valid API key and a valid JWT bearer token exist
- **When** a GET request is sent to `/api/external/comments` with both `X-API-Key` and `Authorization: Bearer` headers
- **Then** the request is authenticated via the API key
- **And** the security context contains an `ApiKeyAuthentication` (not a JWT authentication)

## Default Chain (JWT, all other paths)

### Valid JWT on standard endpoint succeeds

- **Given** a valid JWT bearer token exists
- **When** a GET request is sent to `/api/users` with the `Authorization: Bearer` header
- **Then** the response status is 200
- **And** the security context contains a JWT authentication with `ROLE_*` and `SCOPE_*` authorities

### Valid JWT on POST request succeeds

- **Given** a valid JWT bearer token exists
- **When** a POST request is sent to `/api/users` with the `Authorization: Bearer` header
- **Then** the response status is 200 (or appropriate success code)
- **And** the request is authenticated via JWT

### API key only on standard endpoint is rejected

- **Given** a valid API key exists in the database
- **When** a GET request is sent to `/api/users` with only the `X-API-Key` header (no `Authorization: Bearer`)
- **Then** the response status is 401 Unauthorized
- **And** the API key header is silently ignored

### Both API key and JWT on standard endpoint uses JWT

- **Given** a valid API key and a valid JWT bearer token exist
- **When** a GET request is sent to `/api/users` with both `X-API-Key` and `Authorization: Bearer` headers
- **Then** the request is authenticated via the JWT
- **And** the `X-API-Key` header is silently ignored
- **And** the security context contains a JWT authentication

### Missing credentials on standard endpoint is rejected

- **Given** no authentication headers are present
- **When** a GET request is sent to `/api/users`
- **Then** the response status is 401 Unauthorized

### Health endpoint is accessible without authentication

- **Given** no authentication headers are present
- **When** a GET request is sent to `/api/health/liveness`
- **Then** the response status is 200

### Swagger UI is accessible without authentication

- **Given** no authentication headers are present
- **When** a GET request is sent to `/swagger-ui.html`
- **Then** the response status is 200 (or redirect)

### OpenAPI docs are accessible without authentication

- **Given** no authentication headers are present
- **When** a GET request is sent to `/v3/api-docs`
- **Then** the response status is 200

## ApiKeyAuthenticationFilter (simplified)

### No API key header passes through to next filter

- **Given** a request without an `X-API-Key` header
- **When** the filter processes the request
- **Then** the filter calls `filterChain.doFilter()` without modifying the security context
- **And** `ApiKeyDataService` is not invoked

### Invalid key returns 401

- **Given** a request with an `X-API-Key` header containing an unknown key
- **When** the filter processes the request
- **Then** the response status is 401
- **And** the response body is `{"error":"Invalid API key"}`
- **And** the filter chain is not continued

### Valid key authenticates regardless of HTTP method

- **Given** a request with a valid `X-API-Key` header
- **When** the filter processes a POST request (or any HTTP method)
- **Then** the security context contains an `ApiKeyAuthentication` with `ROLE_API_KEY` authority
- **And** the filter chain is continued
- **And** no 403 is returned by the filter (method enforcement is the chain's responsibility)

## AuthService — UNKNOWN user for non-JWT principals

### JWT principal returns user information from claims

- **Given** the security context contains a JWT authentication with claims `sub=auth0|123`, `name=Hendrik`, `email=hendrik@example.com`, `picture=https://example.com/avatar.jpg`
- **When** `getUserInformation()` is called
- **Then** the returned `UserInformation` has `id=auth0|123`, `name=Hendrik`, `email=hendrik@example.com`, `avatarUrl=https://example.com/avatar.jpg`

### API key principal returns UNKNOWN user

- **Given** the security context contains an `ApiKeyAuthentication` (principal is `ApiKeyEntity`)
- **When** `getUserInformation()` is called
- **Then** the returned `UserInformation` has `id=UNKNOWN`, `name=UNKNOWN`, `email=null`, `avatarUrl=null`

### Non-JWT, non-ApiKeyEntity principal returns UNKNOWN user

- **Given** the security context contains an authentication with a `String` principal
- **When** `getUserInformation()` is called
- **Then** the returned `UserInformation` has `id=UNKNOWN`, `name=UNKNOWN`, `email=null`, `avatarUrl=null`

### JWT with blank subject still throws

- **Given** the security context contains a JWT authentication with a blank `sub` claim
- **When** `getUserInformation()` is called
- **Then** an `IllegalStateException` is thrown with message "No sub found"

## Behavioral Side Effects

### Audit log records UNKNOWN for API-key sessions

- **Given** an API-key authenticated request triggers a data lifecycle event (create/update/delete)
- **When** `AuditLogEventListener` resolves the user via `authService.getUserInformation()`
- **Then** the audit log entry records the user as `"UNKNOWN"` (previously this would have been `"System"` due to the caught `IllegalStateException`)

## Filter Auto-Registration Prevention

### ApiKeyAuthenticationFilter does not run on JWT chain

- **Given** `@Component` is removed from `ApiKeyAuthenticationFilter`
- **And** the filter is only added to the external API chain via `addFilterBefore`
- **When** a request is sent to `/api/users` (matched by the default JWT chain)
- **Then** `ApiKeyAuthenticationFilter.doFilterInternal()` is not invoked
