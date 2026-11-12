# Implementation Steps: Problem details for error responses

Spec: [`design.md`](design.md) · [`behaviors.md`](behaviors.md) · Issue
[#60](https://github.com/OpenElementsLabs/spring-services/issues/60)

The plan builds bottom-up: trace ID first (every problem carries one), then the problem model, then the
factory, then the five places that write a problem (MVC advice, catch-all resolver, error controller, the
two security handlers), then the ALTCHA module, then the cross-cutting tests and the documentation.

Steps 1–9 are production code with their own unit tests. Steps 10–13 are the behaviour tests that cannot
live in a single class. Step 14 is documentation. Every step ends with a green `./mvnw -B verify`.

---

## Step 1: Trace ID package

Create `com.openelements.spring.base.trace` in `spring-services-core`.

- [x] `TraceIdResolver` — public interface, one method `String resolve(HttpServletRequest request)`,
      contract documented as "32 lowercase hex characters, not all zero".
- [x] `TraceParentTraceIdResolver` — package-private default. Parses a **single** `traceparent` header
      per W3C Trace Context Level 1: version `00`–`fe` (not `ff`), 32 lowercase-hex trace-id that is not
      all zero, 16 lowercase-hex parent-id that is not all zero, 2 hex flags, and for version `00` no
      trailing data. Anything else → generate: 16 bytes from a shared `SecureRandom`, lowercase hex,
      regenerated while all zero.
- [x] `TraceContext` — public static accessor. `traceId(HttpServletRequest)` returns the stored request
      attribute, or resolves **and stores** one when the filter did not run. Attribute name is a
      package-visible constant shared with the filter. Non-instantiable.
- [x] `TraceContextFilter` — package-private `OncePerRequestFilter`. Resolves once per request, stores
      the attribute and `MDC.put("traceId", …)`, removes the MDC key in `finally`. Skips work on an
      `ERROR` dispatch where the attribute already exists, so the ID stays stable across the dispatch.
- [x] `TraceAutoConfiguration` — `@AutoConfiguration`, `@ConditionalOnWebApplication(type = SERVLET)`.
      Contributes `TraceParentTraceIdResolver` under `@ConditionalOnMissingBean(TraceIdResolver.class)`
      and a `FilterRegistrationBean<TraceContextFilter>` with order
      `SecurityProperties.DEFAULT_FILTER_ORDER - 1`. **Not** gated on `problem-details.enabled`.
- [x] `package-info.java`.
- [x] Register `TraceAutoConfiguration` in core's `AutoConfiguration.imports`.
- [x] `TraceParentTraceIdResolverTest` — traceparent parsing matrix, generation shape, non-zero
      guarantee, stability of a resolved value.

**Acceptance criteria:**

- [x] `./mvnw -B -pl spring-services-core verify` passes.
- [x] Unit tests for the resolver exist and pass.
- [x] No dependency on the `problem` package — the trace package stands alone.

**Related behaviors:** Incoming traceparent is honoured · All-zero trace-id is ignored · Malformed
traceparent is ignored · Custom resolver (bean condition only; wiring proven in step 11).

---

## Step 2: Problem model — `ProblemType`, `StandardProblemType`, `ProblemException`

Create `com.openelements.spring.base.problem` in `spring-services-core`.

- [x] `ProblemType` — public interface with `slug()`, `status()`, `title()` and the static factory
      `of(String, HttpStatus, String)`. The factory validates the slug against
      `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, rejects a non-4xx/5xx status and a blank title with
      `IllegalArgumentException`, and `null` with `NullPointerException`. The validation is exposed to
      implementors as a static `validate(ProblemType)` helper so an application enum can self-check.
- [x] `StandardProblemType` — public enum, one constant per RFC 9110 error status plus the registered
      extensions (423, 424, 425, 428, 429, 431, 451, 507, 508, 511) and the four special types
      `validation-failed`, `malformed-request`, `invalid-parameter`. Slugs and titles are **literals**,
      never derived from Spring's `HttpStatus`. `forStatus(int)` returns the status entry, or a
      synthesised `http-<code>` / `HTTP <code>` type for a status with no constant.
- [x] `ProblemException` — public, non-final `RuntimeException` with the three constructors from the
      design. Reserved keys (`type`, `title`, `status`, `detail`, `instance`, `traceId`, `errors`)
      throw `IllegalArgumentException`; `null` keys or values throw `NullPointerException`. `properties()`
      returns an immutable copy; `getMessage()` falls back to the title.
- [x] `ProblemTypeTest`, `StandardProblemTypeTest`, `ProblemExceptionTest`.
- [x] A test that asserts every `StandardProblemType` constant passes `ProblemType.validate` and that
      no two constants share a slug or a status (except the three special 400 types).

**Acceptance criteria:**

- [x] Build passes.
- [x] Unit tests for all three types exist and pass.
- [x] No Spring context is needed by any of these tests.

**Related behaviors:** Valid application type · Invalid slug is rejected · Non-error status is rejected ·
Blank title is rejected · Status types are frozen, not derived from Spring · 500 maps to `internal-error` ·
Reserved extension keys are rejected · Null extension values are rejected · Message of the exception.

---

## Step 3: Configuration properties and the problem factory

- [x] `ProblemDetailsProperties` — `@ConfigurationProperties("openelements.problem-details")` record with
      `enabled` (default `true`), `typeBase` (default
      `https://open-elements.com/problems/spring-services/`) and `includeInstance` (default `true`).
      A `@PostConstruct`-free compact-constructor validation rejects a non-absolute URI and a path that
      does not end in `/`, with a message naming `openelements.problem-details.type-base`.
- [x] `ProblemFactory` — public interface with the two `create(…)` overloads from the design.
- [x] `DefaultProblemFactory` — package-private. Builds `ProblemDetail`: `type` = base + slug, `title`,
      `status`, `detail` (omitted when `null`), `instance` when enabled (the original URI
      `jakarta.servlet.error.request_uri` on an error dispatch, otherwise `request.getRequestURI()`;
      never the query string), `traceId` from `TraceContext`, then the exception's extension members.
- [x] `ProblemResponseWriter` — public. Serialises a `ProblemDetail` onto a raw `HttpServletResponse`
      with status and `application/problem+json`, for the two security handlers that run outside MVC.
- [x] `ProblemDetailsPropertiesTest`, `DefaultProblemFactoryTest` (with `MockHttpServletRequest`),
      `ProblemResponseWriterTest` (with `MockHttpServletResponse`).

**Acceptance criteria:**

- [x] Build passes.
- [x] Property validation is proven by a unit test for each rejected form.
- [x] The factory test proves `instance` omits the query string and honours `include-instance=false`.

**Related behaviors:** Every problem carries the mandatory members · `instance` is the path without query
string · `instance` can be switched off · Default type base · Configured type base applies to all types ·
Relative type base is rejected · Type base without trailing slash is rejected.

---

## Step 4: JSON pointers for body validation

- [x] `JsonPointers` — package-private. Turns a Bean Validation `Path` into an RFC 6901 JSON pointer:
      resolves each property name through Jackson's `BeanDescription` for the owning type (so
      `@JsonProperty` and the configured `PropertyNamingStrategy` apply), appends list indexes and map
      keys as segments, escapes `~` → `~0` and `/` → `~1`, and yields `""` for a class-level constraint
      on the root. Falls back to the Java property name when a type cannot be introspected.
- [x] `JsonPointersTest` — naming strategy, `@JsonProperty`, nested objects, list index, map key
      escaping, class-level root and nested class-level, non-introspectable fallback.

**Acceptance criteria:**

- [x] Build passes.
- [x] Every pointer rule in the design has a test case.
- [x] The test drives a real `jakarta.validation.Validator`, not hand-built paths.

**Related behaviors:** Pointers use JSON names · Pointers follow the naming strategy · Map keys are
escaped · Class-level constraint on the body root.

---

## Step 5: MVC exception handler

- [x] `ProblemDetailsExceptionHandler` — public `@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`,
      extends `ResponseEntityExceptionHandler`. Overrides the standard MVC exceptions per the design's
      mapping table and translates body/parameter validation:
      - body only → `validation-failed` with `errors[]`;
      - parameters only → `invalid-parameter`, no `errors[]`, one sentence per parameter in `detail`;
      - both → `validation-failed` with the body entries **and** the parameter sentences in `detail`.
      The parameter sentence format is
      `Invalid {query|path|header} parameter '<name>': <message>.`, joined by a space in declaration
      order. Rejected values and parser messages are never echoed.
- [x] Two package-private helpers so the rendering lives in one place (DRY): `ParameterMessages` for
      the parameter sentences, shared with the catch-all resolver, and `BodyErrors` for the
      `errors[]` entries. (`ParameterMessages`, not `ParameterErrors` — Spring already has a
      `org.springframework.validation.method.ParameterErrors` that this code uses.)
- [x] `ProblemDetailsExceptionHandlerTest` — direct unit tests of the handler methods where that is
      meaningful; the HTTP-level proof lives in step 10.

**Acceptance criteria:**

- [x] Build passes.
- [x] Every row of the design's MVC mapping table is implemented.
- [x] `Allow` and other `ErrorResponse` headers survive the translation.

**Related behaviors:** Body errors carry pointers · Query parameter only · Path variable and header ·
Body and parameter errors together · Type mismatch does not echo the value · Missing required parameter ·
Malformed body · Wrong method keeps `Allow` · Unsupported media type.

---

## Step 6: Catch-all resolver and error controller

- [x] `ProblemHandlerExceptionResolver` — package-private `HandlerExceptionResolver`, resolving in the
      design's order: `@ResponseStatus` → `ErrorResponse`/`ResponseStatusException` → controller-boundary
      `ConstraintViolationException` → `500 internal-error` with the fixed detail
      `An unexpected error occurred.`. A `ConstraintViolationException` counts as controller-boundary
      only when **all** violations have the current handler's bean as root bean and the handler method as
      executable; anything else is a `500`.
- [x] Logging: status ≥ 500 at `ERROR` with the exception; 4xx at `DEBUG`. The trace ID is in the MDC
      because the filter put it there.
- [x] `ProblemErrorController` — package-private `ErrorController` on `${server.error.path:/error}`.
      Always `application/problem+json` regardless of `Accept`. Status from
      `jakarta.servlet.error.status_code`, type from `StandardProblemType.forStatus`, `instance` from
      `jakarta.servlet.error.request_uri`, no `detail`.
- [x] `ProblemHandlerExceptionResolverTest`, `ProblemErrorControllerTest`.

**Acceptance criteria:**

- [x] Build passes.
- [x] A unit test proves a service-layer `ConstraintViolationException` becomes `500`, and a
      controller-boundary one becomes `400`.
- [x] A unit test proves the 500 detail is the fixed string and contains nothing from the exception.

**Related behaviors:** `@ResponseStatus` wins over internal-error · `ResponseStatusException` reason is the
detail · Library `ResponseStatusException` with 502 · Unexpected exception does not leak internals ·
Status without a standard type · Validated controller (`@Validated`) counts as boundary · Service-layer
violation is a server error · Unknown path.

---

## Step 7: Auto-configuration

- [x] `ProblemDetailsAutoConfiguration` — `@AutoConfiguration(before = {WebMvcAutoConfiguration.class,
      ErrorMvcAutoConfiguration.class})`, `@ConditionalOnWebApplication(type = SERVLET)`,
      `@ConditionalOnProperty(prefix = "openelements.problem-details", name = "enabled",
      havingValue = "true", matchIfMissing = true)`,
      `@EnableConfigurationProperties(ProblemDetailsProperties.class)`.
      Contributes, each under `@ConditionalOnMissingBean`:
      `ProblemFactory`, `ProblemResponseWriter`, `ProblemDetailsExceptionHandler`
      (`@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)`), `ProblemErrorController`
      (`@ConditionalOnMissingBean(ErrorController.class)`), and a `WebMvcConfigurer` that inserts
      `ProblemHandlerExceptionResolver` **directly after** `ExceptionHandlerExceptionResolver` in
      `extendHandlerExceptionResolvers`.
- [x] `package-info.java` for the problem package.
- [x] Register `ProblemDetailsAutoConfiguration` in core's `AutoConfiguration.imports`.
- [x] `ProblemDetailsAutoConfigurationTest` — `WebApplicationContextRunner`: all beans present by
      default; none present with `enabled=false` and `BasicErrorController` active instead; an
      application-supplied `ProblemFactory` / `ResponseEntityExceptionHandler` / `TraceIdResolver` wins;
      an invalid `type-base` fails the context.

**Acceptance criteria:**

- [x] Build passes.
- [x] The runner test proves Boot's `BasicErrorController` and `ProblemDetailsExceptionHandler` back off
      when the library's are active, and come back when `enabled=false`.
- [x] The resolver is positioned after `ExceptionHandlerExceptionResolver`, asserted on the real list.

**Related behaviors:** No library error handling registered · Application extends
`ResponseEntityExceptionHandler` · Application replaces the factory · Custom resolver.

---

## Step 8: Security integration

- [x] `ProblemAuthenticationEntryPoint` — public, constructor
      `(ProblemFactory, ProblemResponseWriter, String wwwAuthenticate)`. Writes a `401` `unauthorized`
      problem with the `AuthenticationException` message as `detail` and the chain-specific
      `WWW-Authenticate` header.
- [x] `ProblemAccessDeniedHandler` — public. Writes a `403` `forbidden` problem with the
      `AccessDeniedException` message as `detail`.
- [x] `SecurityConfig` changes:
      - `jwtAuthenticationEntryPoint` / `apiKeyAuthenticationEntryPoint` return
        `AuthenticationEntryPoint`; two variants per bean name, selected by `@ConditionalOnProperty` on
        `openelements.problem-details.enabled` (problem variant with `matchIfMissing = true`, JSON
        variant with `havingValue = "false"`).
      - `apiKeyAuthenticationFilter(…)` takes `AuthenticationEntryPoint`.
      - `accessDeniedHandler` bean, registered only when problem details are enabled, wired into both
        chains via `exceptionHandling(e -> e.accessDeniedHandler(…))`.
      - The default chain permits `DispatcherType.ERROR` (also only when enabled) so a container or
        filter `sendError` reaches `ProblemErrorController` instead of becoming a `401`.
      - Javadoc updated to the new types.
- [x] `ProblemAuthenticationEntryPointTest`, `ProblemAccessDeniedHandlerTest`.
- [x] Keep `JsonAuthenticationEntryPoint` and its test — it is the `enabled=false` implementation.
- [x] `McpSecurityConfig` needs no change (it already injects `AuthenticationEntryPoint` by qualifier) —
      verified, not assumed.

**Acceptance criteria:**

- [x] Build passes for every module.
- [x] Unit tests prove both handlers write `application/problem+json`, the right status and the right
      `WWW-Authenticate` header.
- [x] `JsonAuthenticationEntryPointTest` still passes unchanged.

**Related behaviors:** Invalid API key · Missing JWT · Deactivated user · Method security · Denied in the
filter chain (handler level; chain-level proof in step 11).

---

## Step 9: ALTCHA module

- [x] `AltchaProblemType` — public enum in `com.openelements.spring.base.services.altcha` implementing
      `ProblemType`: `MISSING`/`INVALID`/`EXPIRED` → `altcha-missing` / `altcha-invalid` /
      `altcha-expired`, all `400`, English titles. A factory `forResult(VerificationResult)` maps the
      verifier's result so the slug derivation lives in one place.
- [x] `AltchaExceptionHandler` takes an `ObjectProvider<ProblemFactory>`: with a factory it answers the
      typed problem with the localised message as `detail`; without one it answers `{code, message}` as
      before. `AltchaVerificationException.code()` stays — it is still the `enabled=false` body and the
      slug source.
- [x] `AltchaAutoConfiguration` passes the provider through.
- [x] `AltchaProblemTypeTest`; extend `AltchaWebTest` with the problem-details-enabled and
      `enabled=false` variants.

**Acceptance criteria:**

- [x] Build passes.
- [x] All three ALTCHA types are proven over HTTP.
- [x] With `problem-details.enabled=false` the old `{code, message}` body is byte-identical to before.

**Related behaviors:** Missing solution · Invalid and expired solutions · ALTCHA with problem details
disabled.

---

## Step 10: MVC behaviour tests (web slice)

A new test package `com.example.problemapp` in `spring-services-core`, driven by
`WebApplicationContextRunner` + `MockMvcBuilders.webAppContextSetup` (no database, no Testcontainers), with
a test controller covering every MVC row.

- [x] `ProblemDetailsWebTest` — the common shape, type URIs, `ProblemException` over HTTP, validation
      (body, parameters, both, malformed), standard MVC errors, application exceptions, and the
      application-advice precedence cases.
- [x] A test controller with endpoints for each scenario, plus the body records carrying the
      `@JsonProperty` / naming-strategy / map-key / class-level constraints.
- [x] `ProblemDetailsDisabledWebTest` — the `enabled=false` counterparts.

**Acceptance criteria:**

- [x] Build passes.
- [x] Every scenario listed below asserts status, `type`, `title`, `status`, `traceId` and
      `Content-Type: application/problem+json`.
- [x] The leak tests assert the **absence** of the exception message, class names and rejected values.

**Related behaviors:** Every problem carries the mandatory members · `instance` is the path without query
string · `instance` can be switched off · Problems are JSON even for browsers · Default type base ·
Configured type base applies to all types · Status without a standard type · Status types are frozen ·
Application types can be enums · Typed problem without a handler · Problem without detail · Extension
members · Subclass of `ProblemException` · 5xx problem exceptions are logged · Body errors carry pointers ·
Pointers use JSON names · Pointers follow the naming strategy · Map keys are escaped · Class-level
constraint on the body root · Query parameter only · Path variable and header · Body and parameter errors
together · Type mismatch does not echo the value · Missing required parameter · Validated controller ·
Service-layer violation is a server error · Malformed body · Unknown path · Wrong method keeps `Allow` ·
Unsupported media type · `@ResponseStatus` wins over internal-error · `ResponseStatusException` reason is
the detail · Library `ResponseStatusException` with 502 · Unexpected exception does not leak internals ·
Application advice wins without `@Order` · Application advice can use the factory · Application handler
for a standard MVC exception with `@Order` · Application replaces the factory.

---

## Step 11: Security-chain behaviour tests

These need the **real** production chains, so they boot `StarterTestApp` with Testcontainers, following
`CallerRoleLookupChainIntegrationTest`.

- [x] `ProblemDetailsChainIntegrationTest` — `401` on the default chain (`WWW-Authenticate: Bearer`),
      `401` on the external chain with an invalid API key (`ApiKey realm="external"`, detail
      `Invalid API key`), `403` from `denyAll` on the external chain, `403` from method security,
      `403` for a deactivated user, `sendError` from a filter, `instance` on an error dispatch, and the
      trace-ID-for-401 and trace-ID-stability scenarios.
- [x] Update `CallerRoleLookupChainIntegrationTest`'s 401 assertion to the problem body (the old
      `{status, error}` assertion is part of the breaking change).
- [x] `ProblemDetailsDisabledChainIntegrationTest` — the `enabled=false` 401 with the exact old member
      set.
- [x] MCP chain: extend the MCP module's security test to assert the problem body on `401`.
- [x] SCIM: assert in the SCIM module's tests that the RFC 7644 envelope and `application/scim+json` are
      unchanged, for both the `401` and a controller exception.

**Acceptance criteria:**

- [x] Build passes.
- [x] Each chain scenario asserts the content type and `type` URI, not only the status.
- [x] The SCIM assertions fail if a problem ever leaks into a `/scim/v2/**` response.

**Related behaviors:** Invalid API key · Missing JWT · MCP chain uses the same entry point · SCIM 401 is
untouched · Deactivated user · Method security · Denied in the filter chain · `sendError` from a filter ·
ALTCHA chain denies a non-GET request · Original path in `instance` on error dispatch · Trace ID is
available for 401 · Trace ID is stable within a request · 401 as before · SCIM errors are untouched.

---

## Step 12: Trace-ID behaviour tests

- [x] `TraceContextFilterWebTest` — over HTTP: incoming `traceparent` honoured, all-zero ignored, each
      malformed form ignored (including two `traceparent` headers), the MDC carries `traceId` during the
      request and is clean afterwards, a custom `TraceIdResolver` bean wins, and the filter still runs
      with `problem-details.enabled=false`.
- [x] MDC assertions use a Logback `ListAppender` on the test controller's logger, as in
      `EmailConfigTest`.

**Acceptance criteria:**

- [x] Build passes.
- [x] Every malformed-traceparent form from `behaviors.md` is a separate parameterised case.
- [x] The "MDC is clean afterwards" assertion runs on the request thread, not a test-only thread.

**Related behaviors:** Incoming traceparent is honoured · All-zero trace-id is ignored · Malformed
traceparent is ignored · Trace ID is in the MDC during the request · Trace filter runs without problem
details · Custom resolver.

---

## Step 13: GovStack contract test

- [x] Vendor `spring-services-core/src/test/resources/govstack/govstack-openapi-common.yaml` (test scope
      only) plus `README.md` with the provenance table from the design and the Apache-2.0 licence notice.
- [x] Add `com.networknt:json-schema-validator` (3.0.9) and `jackson-dataformat-yaml` as **test** scope
      dependencies; pin the validator version as a property in the reactor root `pom.xml`.
- [x] `GovStackContractTest` — verifies the file's SHA-256 before anything else, then runs every error
      scenario with `type-base=https://govstack.global/problems/test/` and validates each response:
      body-validation responses against `ValidationProblem`, everything else against `Problem`. Beyond
      the schema it asserts no problem carries `timestamp`, `error`, `message` or `code`, no `errors[]`
      entry carries `code`, and `traceId` equals the request's `traceparent` trace-id when one was sent.

**Acceptance criteria:**

- [x] Build passes.
- [x] The test fails loudly if the vendored file's hash changes.
- [x] The scenario list is driven from one place so a new error scenario cannot silently skip validation.

**Related behaviors:** Schema provenance is verified · Every error response validates · No forbidden
members.

---

## Step 14: Documentation

- [x] `README.md` — replace the "Uniform Authentication-Failure Responses" bullet with a "Problem
      details" section: properties, the standard types, throwing `ProblemException`, overriding with an
      own advice (and the `@Order` note), replacing the `ProblemFactory`, the `${validatedValue}`
      warning, and the "no HTML error pages" constraint.
- [x] `docs/releases/upgrade-to-1.5.md` — a new section for the content-type change with before/after
      bodies for each affected source, the ALTCHA `code` → `type` migration, and
      `openelements.problem-details.enabled=false` as the escape hatch. Add the row to the summary table.
- [x] `CLAUDE.md` — no Project Context section exists in this repository (the file is a conventions
      file); confirm and leave unchanged rather than inventing one.
- [x] `docs/specs/INDEX.md` — status `done`.

**Acceptance criteria:**

- [x] Build passes with `-Dmaven.javadoc.failOnWarnings=true` (the CI setting).
- [x] Every new public type has Javadoc, and both new packages have `package-info.java`.
- [x] The upgrade guide shows a real before/after body for all six rows of the design's compatibility
      table.

---

## Behavior Coverage

Every scenario in `behaviors.md` is backend-only — this repository is a Java library with no frontend.

| Scenario | Layer | Covered in Step |
|---|---|---|
| Every problem carries the mandatory members | Backend | 3, 10 |
| `instance` is the path without query string | Backend | 3, 10 |
| `instance` can be switched off | Backend | 3, 10 |
| Problems are JSON even for browsers | Backend | 10 |
| Default type base | Backend | 3, 10 |
| Configured type base applies to all types | Backend | 3, 10 |
| Relative type base is rejected | Backend | 3, 7 |
| Type base without trailing slash is rejected | Backend | 3, 7 |
| Status without a standard type | Backend | 2, 6, 10 |
| Status types are frozen, not derived from Spring | Backend | 2, 10 |
| 500 maps to `internal-error` | Backend | 2 |
| Valid application type | Backend | 2 |
| Invalid slug is rejected | Backend | 2 |
| Non-error status is rejected | Backend | 2 |
| Blank title is rejected | Backend | 2 |
| Application types can be enums | Backend | 10 |
| Typed problem without a handler | Backend | 10 |
| Problem without detail | Backend | 10 |
| Extension members | Backend | 10 |
| Subclass of `ProblemException` | Backend | 10 |
| Reserved extension keys are rejected | Backend | 2 |
| Null extension values are rejected | Backend | 2 |
| Message of the exception | Backend | 2 |
| 5xx problem exceptions are logged | Backend | 10 |
| Invalid API key | Backend | 8, 11 |
| Missing JWT | Backend | 8, 11 |
| MCP chain uses the same entry point | Backend | 11 |
| SCIM 401 is untouched | Backend | 11 |
| Deactivated user | Backend | 8, 11 |
| Method security | Backend | 8, 11 |
| Denied in the filter chain | Backend | 8, 11 |
| Body errors carry pointers | Backend | 5, 10 |
| Pointers use JSON names | Backend | 4, 10 |
| Pointers follow the naming strategy | Backend | 4, 10 |
| Map keys are escaped | Backend | 4, 10 |
| Class-level constraint on the body root | Backend | 4, 10 |
| Query parameter only | Backend | 5, 10 |
| Path variable and header | Backend | 5, 10 |
| Body and parameter errors together | Backend | 5, 10 |
| Type mismatch does not echo the value | Backend | 5, 10 |
| Missing required parameter | Backend | 5, 10 |
| Validated controller (`@Validated`) counts as boundary | Backend | 6, 10 |
| Service-layer violation is a server error | Backend | 6, 10 |
| Malformed body | Backend | 5, 10 |
| Unknown path | Backend | 6, 10 |
| Wrong method keeps `Allow` | Backend | 5, 10 |
| Unsupported media type | Backend | 5, 10 |
| `@ResponseStatus` wins over internal-error | Backend | 6, 10 |
| `ResponseStatusException` reason is the detail | Backend | 6, 10 |
| Library `ResponseStatusException` with 502 | Backend | 6, 10 |
| Unexpected exception does not leak internals | Backend | 6, 10 |
| Application advice wins without `@Order` | Backend | 10 |
| Application advice can use the factory | Backend | 10 |
| Application handler for a standard MVC exception with `@Order` | Backend | 10 |
| Application extends `ResponseEntityExceptionHandler` | Backend | 7 |
| Application replaces the factory | Backend | 7, 10 |
| `sendError` from a filter | Backend | 11 |
| ALTCHA chain denies a non-GET request | Backend | 11 |
| Original path in `instance` on error dispatch | Backend | 11 |
| Missing solution (ALTCHA) | Backend | 9 |
| Invalid and expired solutions (ALTCHA) | Backend | 9 |
| ALTCHA with problem details disabled | Backend | 9 |
| Incoming traceparent is honoured | Backend | 1, 12 |
| All-zero trace-id is ignored | Backend | 1, 12 |
| Malformed traceparent is ignored | Backend | 1, 12 |
| Trace ID is in the MDC during the request | Backend | 12 |
| Trace ID is available for 401 | Backend | 11 |
| Trace ID is stable within a request | Backend | 11 |
| Trace filter runs without problem details | Backend | 12 |
| Custom resolver | Backend | 7, 12 |
| 401 as before | Backend | 11 |
| No library error handling registered | Backend | 7 |
| SCIM errors are untouched | Backend | 11 |
| Schema provenance is verified | Backend | 13 |
| Every error response validates | Backend | 13 |
| No forbidden members | Backend | 13 |
