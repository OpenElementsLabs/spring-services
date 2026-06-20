# Behaviors: Security Configuration Hygiene

## Authentication-failure JSON responses

### Invalid API key produces uniform JSON error

- **Given** an external-chain request with `X-API-Key: not-a-valid-key`
- **When** the request reaches `/api/external/...`
- **Then** the response is HTTP `401 Unauthorized` with body
  `{"status":401,"error":"Unauthorized","message":"Invalid API key"}` and `Content-Type:
  application/json`

### Missing JWT on default chain produces same error shape

- **Given** a request to `/api/foo` without an `Authorization` header
- **When** the request reaches the default chain
- **Then** the response is HTTP `401 Unauthorized` with body
  `{"status":401,"error":"Unauthorized","message":"<reason>"}` — same shape as the external
  chain's failure body

### 401 responses include WWW-Authenticate header

- **Given** any unauthenticated request to a protected resource
- **When** the chain rejects it with `401`
- **Then** the response carries a `WWW-Authenticate` header (`Basic`, `Bearer`, or the
  chain-appropriate scheme)

### Successful authentication is unaffected

- **Given** a valid JWT on the default chain or a valid `X-API-Key` on the external chain
- **When** the request is processed
- **Then** the request proceeds normally and the response body is the handler's output, not the
  error shape

## `UserService` concurrency

### Concurrent first logins for the same subject still produce exactly one row

- **Given** no `UserEntity` exists for `sub = "S"`
- **When** ten threads call `getCurrentUserEntity()` simultaneously for the same `sub`
- **Then** exactly one row is inserted and all ten threads return a managed reference to that
  same row — behaviour identical to the pre-refactor code, but without `synchronized` throttling

### Concurrent logins for different subjects do not block each other

- **Given** ten different `sub` values, none with an existing `UserEntity`
- **When** ten threads call `getCurrentUserEntity()` simultaneously, one per `sub`
- **Then** the ten requests are not serialised on a single monitor — measured throughput is
  effectively limited only by the database, not by `UserService` itself

### Drift sync remains transactional

- **Given** an existing `UserEntity` whose `name` differs from the JWT claim
- **When** `getCurrentUserEntity()` is called
- **Then** the drift detection and the `userRepository.save(user)` run inside one transaction —
  same guarantee as today, independent of removing `synchronized`

## `AuthService.getUserInformation()` contract change

### JWT-authenticated request returns a present Optional

- **Given** a JWT-authenticated request
- **When** `authService.getUserInformation()` is called
- **Then** an `Optional<UserInformation>` is returned with `isPresent() == true` and the
  expected fields populated from the JWT claims

### API-key-authenticated request returns an empty Optional

- **Given** a request authenticated via `X-API-Key`
- **When** `authService.getUserInformation()` is called
- **Then** an empty `Optional<UserInformation>` is returned — no `"UNKNOWN"` sentinel
  appears anywhere

### `UserService` still fails fast when called without a JWT

- **Given** a request authenticated via `X-API-Key` (no JWT present)
- **When** application code wrongly calls `userService.getCurrentUserEntity()`
- **Then** the call throws `IllegalStateException` ("no JWT bound to current request"); no
  spurious `UserEntity` with sentinel fields is created

### `getPrincipalJwt()` still throws on non-JWT principals

- **Given** a request authenticated via API key
- **When** `authService.getPrincipalJwt()` is called
- **Then** an `IllegalStateException("Principal is not a JWT")` is thrown — behaviour
  unchanged after removing the dead `null` check

## Configuration wiring

### Library still integrates via `@Import(FullSpringServiceConfig.class)`

- **Given** a consumer Spring Boot application that imports `FullSpringServiceConfig`
- **When** the application starts
- **Then** all previously-published beans are present in the context (`UserService`,
  `AuthService`, `ApiKeyDataService`, `ApiKeyAuthenticationFilter`, both
  `SecurityFilterChain` beans, `JwtAuthenticationConverter`) — no consumer-side change is
  required

### Removing `@EnableAutoConfiguration` does not break startup

- **Given** the library compiled with the new annotation set
- **When** an integration test boots the library inside a Spring Boot test context
- **Then** the context loads, security chains are constructed, and authenticated requests
  succeed end-to-end

### `proxyBeanMethods = false` does not break bean wiring

- **Given** the new `@Configuration(proxyBeanMethods = false)` on `SecurityConfig`,
  `UserConfig`, `ApiKeyConfig`
- **When** the context starts
- **Then** every `@Bean` is created exactly once and injected correctly — no duplicate beans,
  no circular references introduced by the change

## `Roles` constants

### Reflective instantiation is prevented

- **Given** the new `private Roles() {}` constructor
- **When** code attempts `Roles.class.getDeclaredConstructor().newInstance()` with the
  constructor accessible
- **Then** the call still succeeds via `setAccessible(true)` (Java reflection can always bypass)
  but a static analysis tool no longer flags the class — the lock is a hygiene marker, not a
  runtime guarantee

### Constants remain unchanged

- **Given** a downstream caller using `Roles.ROLE_APP_ADMIN`
- **When** the library is upgraded
- **Then** the string value is still `"APP-ADMIN"` — no role rename
