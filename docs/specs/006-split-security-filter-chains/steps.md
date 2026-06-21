# Implementation Steps: Split SecurityFilterChain into Two Isolated Chains

## Step 1: Simplify `ApiKeyAuthenticationFilter`

- [x] Remove `@Component` annotation from `ApiKeyAuthenticationFilter`
- [x] Remove `READ_ONLY_METHODS` constant and the HTTP-method check that returns 403
- [x] Remove the `org.springframework.http.HttpMethod` import
- [x] Update class-level Javadoc to remove method-checking references
- [x] Make the inner `ApiKeyAuthentication` class package-private accessible to test code (already package-private — keep as-is)

**Acceptance criteria:**
- [x] Project compiles: `./mvnw -q -DskipTests compile`
- [x] Filter only authenticates; does not enforce method
- [x] No `@Component` on the filter

**Related behaviors:** Filter Auto-Registration Prevention; ApiKeyAuthenticationFilter (simplified) — all scenarios

---

## Step 2: Split `SecurityConfig` into two filter chains

- [x] Replace single `filterChain` bean with `externalApiFilterChain` (`@Order(1)`) and `defaultFilterChain` (`@Order(2)`)
- [x] Add `@Bean` factory method to construct `ApiKeyAuthenticationFilter` from `ApiKeyDataService`
- [x] External chain: `securityMatcher("/api/external/**")`, allow GET/HEAD/OPTIONS, denyAll() for other methods, `addFilterBefore(filter, BearerTokenAuthenticationFilter.class)`, csrf disabled
- [x] Default chain: keep health/swagger permits, JWT oauth2ResourceServer, csrf disabled
- [x] Update class-level Javadoc to describe two-chain model
- [x] Update injected dependency: change constructor to accept `ApiKeyDataService` instead of `ApiKeyAuthenticationFilter`

**Acceptance criteria:**
- [x] Project compiles
- [x] Two `SecurityFilterChain` beans visible at startup

**Related behaviors:** External API Chain — all scenarios; Default Chain — all scenarios

---

## Step 3: Update `AuthService.getUserInformation()` for non-JWT principals

- [x] Change `getUserInformation()` to use `getPrincipalObject()` and `instanceof Jwt`
- [x] When principal is not a `Jwt`, return `new UserInformation("UNKNOWN", "UNKNOWN", null, null)`
- [x] Keep blank-subject `IllegalStateException` behavior for JWT path
- [x] Update Javadoc to reflect new behavior

**Acceptance criteria:**
- [x] Project compiles
- [x] No regression in existing JWT-path tests

**Related behaviors:** AuthService — UNKNOWN user for non-JWT principals — all scenarios

---

## Step 4: Update `ApiKeyAuthenticationFilterTest`

- [x] Remove the 403-for-POST/PUT/DELETE tests
- [x] Add a "valid key authenticates POST request" test (no 403, security context populated, chain continues)
- [x] Keep no-header passthrough, invalid-key 401, valid-key GET/HEAD/OPTIONS, ROLE_API_KEY tests

**Acceptance criteria:**
- [x] `./mvnw -q test -Dtest=ApiKeyAuthenticationFilterTest` passes
- [x] No tests reference the removed 403 behavior

**Related behaviors:** ApiKeyAuthenticationFilter (simplified) — all scenarios

---

## Step 5: Update `AuthServiceTest`

- [x] Add tests for `ApiKeyEntity` principal returning UNKNOWN user
- [x] Add test for non-JWT, non-ApiKeyEntity principal (e.g., String) returning UNKNOWN user
- [x] Keep existing JWT happy path and blank-subject tests

**Acceptance criteria:**
- [x] `./mvnw -q test -Dtest=AuthServiceTest` passes

**Related behaviors:** AuthService — UNKNOWN user for non-JWT principals — all scenarios

---

## Step 6: Update `SecurityConfigRoleTest` constructor signature

- [x] Update `SecurityConfigRoleTest` to instantiate `SecurityConfig` with `ApiKeyDataService` mock (was `ApiKeyAuthenticationFilter`)

**Acceptance criteria:**
- [x] `./mvnw -q test -Dtest=SecurityConfigRoleTest` passes

**Related behaviors:** N/A (existing role mapping tests preserved)

---

## Step 7: Update package-info documentation

- [x] Update `com/openelements/spring/base/security/package-info.java` to describe the two-chain model
- [x] Update `com/openelements/spring/base/security/apikey/package-info.java` to remove method-checking references

**Acceptance criteria:**
- [x] Javadoc generation does not error: `./mvnw -q -DskipTests javadoc:javadoc` (best-effort)

**Related behaviors:** Documentation only — supports all behaviors

---

## Step 8: Update `TODO.md`

- [x] Add entry: "Associate user information with API keys to replace UNKNOWN user"

**Acceptance criteria:**
- [x] Entry visible in TODO.md

**Related behaviors:** Documents the deferred TODO referenced in Behavioral Side Effects

---

## Step 9: Final build & test pass

- [x] Run `./mvnw clean verify`
- [x] All tests pass; no compilation errors

**Acceptance criteria:**
- [x] Full Maven build is green

**Related behaviors:** All

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| External: Valid API key on GET succeeds | Backend | Step 4 (filter unit) — chain integration covered by existing logic |
| External: Valid API key on HEAD succeeds | Backend | Step 4 |
| External: Valid API key on OPTIONS succeeds | Backend | Step 4 |
| External: Valid API key on POST denied (403) | Backend | Step 2 (chain config) — verified by chain authorize rule |
| External: Valid API key on PUT denied (403) | Backend | Step 2 |
| External: Valid API key on DELETE denied (403) | Backend | Step 2 |
| External: Invalid API key 401 | Backend | Step 4 |
| External: Missing API key header 401 | Backend | Step 2 (chain authorize requires authenticated) + Step 4 (passthrough) |
| External: JWT bearer token rejected | Backend | Step 2 (no JWT filter on external chain) |
| External: Both API key + JWT uses API key | Backend | Step 2 (only API filter present) |
| Default: Valid JWT succeeds | Backend | Step 2 (chain config) |
| Default: Valid JWT POST succeeds | Backend | Step 2 |
| Default: API key only on standard endpoint rejected | Backend | Step 2 (no API filter on default) |
| Default: Both API key + JWT uses JWT | Backend | Step 2 |
| Default: Missing credentials 401 | Backend | Step 2 |
| Default: Health endpoint public | Backend | Step 2 |
| Default: Swagger UI public | Backend | Step 2 |
| Default: OpenAPI docs public | Backend | Step 2 |
| Filter: No header passthrough | Backend | Step 4 |
| Filter: Invalid key 401 | Backend | Step 4 |
| Filter: Valid key auths regardless of method | Backend | Step 4 |
| AuthService: JWT principal user info | Backend | Step 5 |
| AuthService: ApiKeyEntity → UNKNOWN | Backend | Step 5 |
| AuthService: Non-JWT non-ApiKey → UNKNOWN | Backend | Step 5 |
| AuthService: Blank subject throws | Backend | Step 5 |
| Behavioral side effect: Audit logs UNKNOWN | Backend | Step 5 (covered indirectly via AuthService change) |
| Filter auto-registration prevention | Backend | Step 1 (`@Component` removed) + Step 2 (only added to chain 1) |
