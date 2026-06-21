# Design: Security Configuration Hygiene

## GitHub Issue

—

## Summary

Tighten the Spring Boot configuration and Spring Security wiring of the existing security stack to
match Spring Boot best practices. The current implementation works correctly but mixes
auto-configuration patterns in ways that limit composability for consumers, serialises a critical
hot path with `synchronized`, and produces inconsistent error responses on authentication failures.

The cleanup is structural — no new feature is added, no public DTO changes, no schema migrations.
It is a deliberate prerequisite for the SCIM Foundation spec (`012-scim-foundation-user-model`),
because the foundation extends exactly the surfaces touched here: the JIT-login hot path, the
authentication-failure response shape, and the `UserInformation` record.

## Goals

- Make every `@Configuration` / auto-configuration class in the library follow one consistent
  Spring Boot library pattern.
- Remove `synchronized` from `UserService.getCurrentUserEntity()` without weakening the existing
  race protection.
- Normalise authentication-failure responses across all chains to one JSON shape produced by a
  single `AuthenticationEntryPoint`.
- Replace direct `SecurityContextHolder.getContext()` access with `SecurityContextHolderStrategy`.
- Replace `UserInformation` sentinel values (`"UNKNOWN"`) with an `Optional` return type, so
  callers cannot accidentally persist sentinel strings.
- Add the missing `private` constructor on `Roles` to lock it as a constants holder.

## Non-goals

- Splitting the library into separate auto-configuration imports per feature. The aggregated
  `FullSpringServiceConfig` entry point stays the documented integration path.
- Introducing `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  for true auto-discovery. That is a larger product decision and would change the integration
  contract (`@Import(FullSpringServiceConfig.class)` would no longer be required). Tracked as a
  follow-up.
- Adding `@ConditionalOnProperty` toggles for individual features. Tracked separately — needs
  a property naming convention first.
- Replacing the `/api/health/**` permitAll path with `/actuator/health`. App-specific decision,
  not a library refactor.
- Touching the bearer-token / JWT validation logic itself (it is correct today).
- Spec 010 (Personal Access Tokens) changes. This spec does not depend on 010 being implemented;
  it touches only code that exists in `main` today.

## Technical Approach

Six coordinated changes, all confined to the `security` and `services/user` and
`services/apikey` packages.

### Change 1 — Auto-configuration annotations normalised

Three classes carry an inconsistent and partly contradictory set of annotations today:

```java
// SecurityConfig.java
@EnableWebSecurity
@AutoConfiguration
@EnableAutoConfiguration
@Import({ApiKeyConfig.class, UserConfig.class})
@ComponentScan
@EnableMethodSecurity

// UserConfig.java, ApiKeyConfig.java
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
```

Issues:
- `@AutoConfiguration` is meaningful only when the class is registered via
  `META-INF/spring/...AutoConfiguration.imports`. No such file exists in the library, so the
  annotation is inert today.
- `@EnableAutoConfiguration` belongs on application entry points, not on library configs. Placing
  it on a library `@Configuration` causes recursive auto-config resolution to start from the
  library's package, which can introduce subtle ordering issues for consumers.
- `@AutoConfiguration` already implies `@Configuration`. The combination is redundant.
- `@ComponentScan` without `basePackageClasses` is fragile — it scans the package the
  configuration class lives in. For a library this is acceptable when the package layout is
  controlled, but the lack of explicit anchors makes refactors riskier than necessary.

Replacement, applied uniformly:

```java
// SecurityConfig.java
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@Import({ApiKeyConfig.class, UserConfig.class})

// UserConfig.java, ApiKeyConfig.java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UserConfig.class) // anchored to its own class
```

Rationale:
- The library is `@Import`-driven (documented in the README via
  `@Import(FullSpringServiceConfig.class)`), so neither `@AutoConfiguration` nor
  `@EnableAutoConfiguration` is needed.
- `proxyBeanMethods = false` skips the CGLIB subclass that Spring otherwise generates for every
  `@Configuration`. None of the current `@Bean` methods in this library call each other, so the
  proxy adds cost without benefit.
- `@ComponentScan(basePackageClasses = ...)` pins the scan to the package containing the named
  class, surviving package renames without silent breakage.

### Change 2 — Remove `synchronized` from `UserService.getCurrentUserEntity()`

```java
public synchronized UserEntity getCurrentUserEntity() { ... }
```

Becomes:

```java
public UserEntity getCurrentUserEntity() { ... }
```

`UserService` is a singleton bean, so `synchronized` serialises every JIT-login lookup across all
threads in the application, not just two concurrent logins for the same user. The intended race
— two parallel first-time logins for the same `sub` — is already correctly handled by:

1. The `UNIQUE` constraint on `users.sub` at the DB level.
2. The `try { save() } catch (DataIntegrityViolationException) { refetch }` block in the method.

Removing `synchronized` eliminates the throughput bottleneck without weakening that protection.
Existing concurrency behaviors continue to hold; the recovery block is the load-bearing piece.

### Change 3 — Centralised JSON `AuthenticationEntryPoint`

`ApiKeyAuthenticationFilter` writes the failure body by hand:

```java
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType(MediaType.APPLICATION_JSON_VALUE);
response.getWriter().write("{\"error\":\"Invalid API key\"}");
```

Two problems: the shape disagrees with what Spring Security's default
`BearerTokenAuthenticationEntryPoint` produces on the JWT chain, and the hand-rolled write skips
Jackson, content negotiation, `Content-Length`, and the `WWW-Authenticate` header that
RFC 7235 §3.1 requires on `401` responses.

Replacement:

1. Introduce a single `JsonAuthenticationEntryPoint` bean in `SecurityConfig` that uses the
   application's `ObjectMapper` to produce a uniform JSON error body:

   ```json
   { "status": 401, "error": "Unauthorized", "message": "<reason>" }
   ```

   And sets `WWW-Authenticate` appropriately for the chain it is wired into.

2. Wire the entry point into both chains:
   - Default chain: `oauth2ResourceServer.authenticationEntryPoint(jsonAuthenticationEntryPoint)`
   - External chain: `http.exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthenticationEntryPoint))`

3. `ApiKeyAuthenticationFilter` no longer writes a body. On an invalid key it calls
   `entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"))` and
   returns — the entry point produces the response.

Rationale: callers of the library get one consistent error shape across chains, and the manual
JSON write disappears. Consumer apps can override the bean via `@ConditionalOnMissingBean` once
that pattern is introduced (separate follow-up).

### Change 4 — `SecurityContextHolderStrategy` instead of direct holder access

`ApiKeyAuthenticationFilter` and `AuthService` both touch the context holder directly:

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
SecurityContextHolder.getContext().getAuthentication();
```

Replacement:

```java
// In the filter
private final SecurityContextHolderStrategy strategy =
    SecurityContextHolder.getContextHolderStrategy();

SecurityContext context = strategy.createEmptyContext();
context.setAuthentication(authentication);
strategy.setContext(context);
```

```java
// In AuthService
private final SecurityContextHolderStrategy strategy =
    SecurityContextHolder.getContextHolderStrategy();

Authentication authentication = strategy.getContext().getAuthentication();
```

Rationale: Spring Security 6 documents `SecurityContextHolderStrategy` as the supported indirection
for test isolation and reactive composition. The runtime behaviour is identical today, but the
code becomes resilient to strategy changes (e.g. `MODE_INHERITABLETHREADLOCAL`,
`@WithMockUser`-style test contexts).

### Change 5 — `UserInformation` returns `Optional`, drops sentinel values

`AuthService.getUserInformation()` currently has two return paths:

```java
if (principal instanceof Jwt jwt) {
  return new UserInformation(sub, name, email, avatarUrl);
}
return new UserInformation("UNKNOWN", "UNKNOWN", null, null);
```

The sentinel path is reached for non-JWT principals (API-key auth, primitive auth). A caller that
persists `userInformation.id()` into a `created_by` column or an audit log would silently store
the literal string `"UNKNOWN"`.

Replacement:

```java
public Optional<UserInformation> getUserInformation() {
  Object principal = getPrincipalObject();
  if (principal instanceof Jwt jwt) {
    return Optional.of(buildFromJwt(jwt));
  }
  return Optional.empty();
}
```

Callers must explicitly handle the empty case. The existing call site
(`UserService.getCurrentUserEntity()`) is updated to `orElseThrow(...)` — if a request reached
`UserService` without being JWT-authenticated, that is a programming error and should fail loudly.

Also remove the dead `null` check in `getPrincipalJwt()`:

```java
final Object principal = getPrincipalObject();
if (principal == null) {                          // already throws above
  throw new IllegalStateException("...");         // unreachable
}
```

Rationale: type-system-enforced handling of the "no JWT" case eliminates a class of silent bugs.
This is a *minor breaking change* for direct callers of `AuthService.getUserInformation()` —
acceptable because the project is `1.1.0-SNAPSHOT` and the previous spec (010) already noted
record-level breaking changes are tolerated when they serve a clear purpose.

### Change 6 — `Roles` private constructor

```java
public final class Roles {
  public static final String ROLE_APP_ADMIN = "APP-ADMIN";
  // ...
}
```

Becomes:

```java
public final class Roles {
  private Roles() {}                              // utility class lock

  public static final String ROLE_APP_ADMIN = "APP-ADMIN";
  // ...
}
```

Trivial nit, but flagged by SpotBugs / PMD and a common Java utility-class idiom. Avoids accidental
or reflective instantiation.

## Dependencies

- No new external libraries. No transitive dependency changes.
- Affected packages: `security/`, `security/apikey/`, `security/roles/`, `services/user/`,
  `services/apikey/`.

## Security Considerations

- **JSON entry point body.** The new uniform body deliberately does not include stack traces,
  exception class names, or the raw `Authentication` object. The `message` field carries a
  short, fixed reason string per failure type — no user-controlled content is reflected.
- **No change to authentication semantics.** A request that was rejected with `401` before is
  still rejected with `401`. A request that was accepted is still accepted. The refactor is
  pure plumbing.
- **`Optional<UserInformation>` cannot leak data.** Callers that previously accepted the
  sentinel `"UNKNOWN"` and might have logged it now have to opt in to handling the empty case.
  The only in-tree consumer (`UserService.getCurrentUserEntity()`) cannot reach the empty case
  because the request was already JWT-authenticated to get past the chain.

## Open Questions

- **`META-INF/spring/.../AutoConfiguration.imports`.** Should the library switch to true
  Spring Boot auto-discovery in a follow-up? Trade-off: zero-config integration for consumers,
  at the cost of less explicit control. Deferred.
- **Per-feature `@ConditionalOnMissingBean` / `@ConditionalOnProperty` annotations.** Would let
  consumers selectively replace beans (e.g. their own `JwtAuthenticationConverter`) or disable
  features (`openelements.security.api-key.enabled=false`). Needs a property-name convention
  first. Deferred.
- **`/api/health/**` vs `/actuator/health`.** App-specific, not a library decision. Left as is.

## Migration / Upgrade Notes for Consumers

- Direct callers of `AuthService.getUserInformation()` will see a signature change from
  `UserInformation` to `Optional<UserInformation>`. Single-line adaptation:
  `getUserInformation().orElseThrow()` for the existing semantics, or `.orElse(...)` for an
  intentional fallback.
- Authentication-failure JSON bodies on the external chain change shape (from
  `{"error":"..."}` to `{"status":401,"error":"Unauthorized","message":"..."}`). Consumers that
  parse the failure body must adapt; consumers that only check status codes are unaffected.
- No DB changes, no property changes, no API path changes.
