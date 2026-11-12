# Behaviors: Problem details for error responses

Unless stated otherwise: a servlet web application with `spring-services-core`, default configuration
(`openelements.problem-details.enabled` unset, `type-base` unset), and a test controller under `/api/test/**`
reachable with a valid JWT. `<base>` stands for `https://open-elements.com/problems/spring-services/`.

## Common problem shape

### Every problem carries the mandatory members

- **Given** any request that ends in an error handled by this spec
- **When** the response is written
- **Then** `Content-Type` is `application/problem+json`
- **And** the body contains `type`, `title`, `status` and `traceId`
- **And** `status` equals the HTTP status code of the response
- **And** `traceId` is 32 lowercase hex characters, not all zero

### `instance` is the path without query string

- **Given** a request to `/api/test/fail?email=jane@example.com` that fails with `404`
- **When** the problem is written
- **Then** `instance` is `/api/test/fail`

### `instance` can be switched off

- **Given** `openelements.problem-details.include-instance=false`
- **When** any request fails
- **Then** the problem has no `instance` member

### Problems are JSON even for browsers

- **Given** a request with `Accept: text/html` that fails with `404`
- **When** the response is written
- **Then** `Content-Type` is `application/problem+json` and the body is a problem, not an HTML page

## Type URIs

### Default type base

- **Given** no `type-base` is configured
- **When** a request fails with `401`
- **Then** `type` is `<base>unauthorized`

### Configured type base applies to all types

- **Given** `openelements.problem-details.type-base=https://govstack.global/problems/registries/`
- **When** a request fails with a library type and another with an application type `duplicate-contact`
- **Then** the types are `https://govstack.global/problems/registries/unauthorized` and
  `https://govstack.global/problems/registries/duplicate-contact`

### Relative type base is rejected

- **Given** `type-base=/problems/`
- **When** the application starts
- **Then** start-up fails with a message naming `openelements.problem-details.type-base`

### Type base without trailing slash is rejected

- **Given** `type-base=https://example.org/problems`
- **When** the application starts
- **Then** start-up fails with a message naming `openelements.problem-details.type-base`

### Status without a standard type

- **Given** a controller throws `new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT)`
- **When** it is called
- **Then** the response is `418` with `type` `<base>http-418` and `title` `HTTP 418`

### Status types are frozen, not derived from Spring

- **Given** a controller throws `new ResponseStatusException(HttpStatus.valueOf(413))`
- **When** it is called
- **Then** `type` is `<base>content-too-large` and `title` is `Content Too Large`

### 500 maps to `internal-error`

- **Given** `StandardProblemType.forStatus(500)`
- **When** it is evaluated
- **Then** it returns the `internal-error` type

## `ProblemType`

### Valid application type

- **Given** `ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, "Duplicate contact")`
- **When** it is created
- **Then** slug, status and title are returned unchanged

### Invalid slug is rejected

- **Given** slugs `Duplicate`, `duplicate_contact`, `-duplicate`, `duplicate-`, `duplicate--contact` and `""`
- **When** `ProblemType.of` is called with each
- **Then** each call throws `IllegalArgumentException`

### Non-error status is rejected

- **Given** `ProblemType.of("ok", HttpStatus.OK, "OK")`
- **When** it is created
- **Then** it throws `IllegalArgumentException`

### Blank title is rejected

- **Given** `ProblemType.of("duplicate-contact", HttpStatus.CONFLICT, " ")`
- **When** it is created
- **Then** it throws `IllegalArgumentException`

### Application types can be enums

- **Given** an application enum `CrmProblemType implements ProblemType` with `DUPLICATE_CONTACT`
- **When** a controller throws `new ProblemException(CrmProblemType.DUPLICATE_CONTACT)`
- **Then** the response is `409` with `type` `<base>duplicate-contact`

## `ProblemException`

### Typed problem without a handler

- **Given** `ProblemType.of("duplicate-record", HttpStatus.CONFLICT, "Duplicate record")`
- **When** a controller throws `new ProblemException(type, "Record already exists")`
- **Then** the response is `409` with `type` `<base>duplicate-record`, `title` `Duplicate record` and `detail`
  `Record already exists`

### Problem without detail

- **Given** a controller throws `new ProblemException(type)`
- **When** it is called
- **Then** the problem has no `detail` member

### Extension members

- **Given** a controller throws `new ProblemException(type, "Record already exists", Map.of("existingId", "42"))`
- **When** it is called
- **Then** the problem contains `"existingId": "42"` next to the standard members

### Subclass of `ProblemException`

- **Given** an application class `DuplicateContactException extends ProblemException`
- **When** a controller throws it
- **Then** the response is the problem of its type, without any application handler

### Reserved extension keys are rejected

- **Given** each of the keys `type`, `title`, `status`, `detail`, `instance`, `traceId`, `errors`
- **When** a `ProblemException` is constructed with that key in its properties
- **Then** the constructor throws `IllegalArgumentException`

### Null extension values are rejected

- **Given** a properties map containing a `null` value
- **When** a `ProblemException` is constructed with it
- **Then** the constructor throws `NullPointerException`

### Message of the exception

- **Given** `new ProblemException(type, "Record already exists")` and `new ProblemException(type)`
- **When** `getMessage()` is called
- **Then** it returns `Record already exists` and the title of the type, respectively

### 5xx problem exceptions are logged

- **Given** an application type with status `503`
- **When** a controller throws a `ProblemException` of that type
- **Then** the response is `503` with that type
- **And** the exception is logged at `ERROR` with the problem's `traceId` in the MDC

## Authentication (401)

### Invalid API key

- **Given** a request to `/api/external/contacts` with `X-API-Key: not-a-valid-key`
- **When** the external chain rejects it
- **Then** the response is `401`, `Content-Type: application/problem+json`,
  `WWW-Authenticate: ApiKey realm="external"`
- **And** `type` is `<base>unauthorized`, `detail` is `Invalid API key`, and `traceId` is present

### Missing JWT

- **Given** a request to `/api/test/ok` without `Authorization`
- **When** the default chain rejects it
- **Then** the response is `401` with `WWW-Authenticate: Bearer` and a problem of type `<base>unauthorized`

### MCP chain uses the same entry point

- **Given** `openelements.mcp.enabled=true` and a request to `/mcp` without an API key
- **When** the MCP chain rejects it
- **Then** the response is `401` with a problem of type `<base>unauthorized`

### SCIM 401 is untouched

- **Given** the SCIM module is enabled and a request to `/scim/v2/Users` without a valid token
- **When** the SCIM chain rejects it
- **Then** the response is the RFC 7644 `Error` envelope with `Content-Type: application/scim+json`, as before

## Access denied (403)

### Deactivated user

- **Given** a JWT for a user with `active = false`
- **When** the user calls an endpoint that resolves the current user
- **Then** the response is `403` with `type` `<base>forbidden` and `detail` `User account is disabled`

### Method security

- **Given** an endpoint annotated `@PreAuthorize("hasRole('ADMIN')")` and a JWT without that role
- **When** it is called
- **Then** the response is `403` with `type` `<base>forbidden`

### Denied in the filter chain

- **Given** a valid API key
- **When** it sends `POST /api/external/contacts` (denied by `denyAll` on the external chain)
- **Then** the response is `403` with `type` `<base>forbidden`, written by `ProblemAccessDeniedHandler`

## Validation

### Body errors carry pointers

- **Given** a request body violating `@NotBlank` on `address.street` and `@Size` on `items[0].name`
- **When** it reaches an `@Valid @RequestBody` parameter
- **Then** the response is `400` with `type` `<base>validation-failed`
- **And** `errors` contains entries with `pointer` `/address/street` and `/items/0/name`, each with a `message`
- **And** no rejected value appears anywhere in the body

### Pointers use JSON names

- **Given** a body record with `@JsonProperty("postal_code") @Pattern(regexp = "[0-9]{5}") String postalCode`
  nested under `address`
- **When** the value `ABC` is sent
- **Then** the error's `pointer` is `/address/postal_code`

### Pointers follow the naming strategy

- **Given** `spring.jackson.property-naming-strategy=SNAKE_CASE` and a body field `postalCode` violating a constraint
- **When** the request is validated
- **Then** the error's `pointer` is `/postal_code`

### Map keys are escaped

- **Given** a body `Map<String, @NotBlank String> labels` with an invalid value under the key `a/b~c`
- **When** the request is validated
- **Then** the error's `pointer` is `/labels/a~1b~0c`

### Class-level constraint on the body root

- **Given** a body record with a class-level constraint that fails
- **When** the request is validated
- **Then** `errors` contains an entry with `pointer` `""`

### Query parameter only

- **Given** `@RequestParam @Max(100) int size` and the request `?size=5000`
- **When** it is called
- **Then** the response is `400` with `type` `<base>invalid-parameter`
- **And** the problem has no `errors` member
- **And** `detail` is `Invalid query parameter 'size': must be less than or equal to 100.`

### Path variable and header

- **Given** `@PathVariable @Min(1) long id` and `@RequestHeader("X-Page") @Min(1) int page`, called with id `0`
  and header `X-Page: 0`
- **When** it is called
- **Then** the response is `invalid-parameter` and `detail` names `path parameter 'id'` and
  `header parameter 'X-Page'`, in declaration order

### Body and parameter errors together

- **Given** an endpoint with an invalid `@RequestBody` field `name` and an invalid `@RequestParam` `size`
- **When** it is called
- **Then** the response is `400` with `type` `<base>validation-failed`
- **And** `errors` contains only the entry for `/name`
- **And** `detail` describes the query parameter `size`

### Type mismatch does not echo the value

- **Given** `@RequestParam int size` and the request `?size=jane@example.com`
- **When** it is called
- **Then** the response is `400` with `type` `<base>invalid-parameter`
- **And** `detail` names the query parameter `size` and does not contain `jane@example.com`

### Missing required parameter

- **Given** `@RequestParam int size` and a request without `size`
- **When** it is called
- **Then** the response is `400` with `type` `<base>invalid-parameter` and `detail` names the missing query parameter

### Validated controller (`@Validated`) counts as boundary

- **Given** a controller class annotated `@Validated` with a parameter `@RequestParam @Max(100) int size`
- **When** it is called with `?size=5000` and a `ConstraintViolationException` is thrown
- **Then** the response is `400` with `type` `<base>invalid-parameter`

### Service-layer violation is a server error

- **Given** a controller that calls a `@Validated` service method with an invalid argument it built itself
- **When** the service throws `ConstraintViolationException`
- **Then** the response is `500` with `type` `<base>internal-error`

### Malformed body

- **Given** the body `{"name": ` (truncated JSON)
- **When** it reaches an `@RequestBody` parameter
- **Then** the response is `400` with `type` `<base>malformed-request`
- **And** `detail` contains no Jackson parser message, no class name and no part of the body

## Standard MVC errors

### Unknown path

- **Given** an authenticated request to `/api/does-not-exist`
- **When** it is handled
- **Then** the response is `404` with `type` `<base>not-found`

### Wrong method keeps `Allow`

- **Given** an endpoint that supports only `GET`
- **When** it is called with `DELETE`
- **Then** the response is `405` with `type` `<base>method-not-allowed` and an `Allow: GET` header

### Unsupported media type

- **Given** an endpoint consuming `application/json`
- **When** it is called with `Content-Type: text/plain`
- **Then** the response is `415` with `type` `<base>unsupported-media-type`

## Application exceptions

### `@ResponseStatus` wins over internal-error

- **Given** an application exception annotated `@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Already taken")`
- **When** a controller throws it
- **Then** the response is `409` with `type` `<base>conflict` and `detail` `Already taken`

### `ResponseStatusException` reason is the detail

- **Given** a controller throws `new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found")`
- **When** it is called
- **Then** the response is `404` with `type` `<base>not-found` and `detail` `Contact not found`

### Library `ResponseStatusException` with 502

- **Given** the translation service is called and the upstream fails (`ResponseStatusException(BAD_GATEWAY, "Translation API call failed")`)
- **When** the endpoint is called
- **Then** the response is `502` with `type` `<base>bad-gateway` and `detail` `Translation API call failed`

### Unexpected exception does not leak internals

- **Given** a controller throws `new IllegalStateException("SELECT * FROM users WHERE email='jane@example.com'")`
- **When** it is called
- **Then** the response is `500` with `type` `<base>internal-error` and `detail` `An unexpected error occurred.`
- **And** the body contains no part of the exception message and no class name
- **And** the log contains the exception with its stack trace at `ERROR`, with the same `traceId` in the MDC

### Application advice wins without `@Order`

- **Given** an application `@RestControllerAdvice` without `@Order` handling `IllegalStateException` with its own response
- **When** a controller throws `IllegalStateException`
- **Then** the application's response is returned, not `internal-error`

### Application advice can use the factory

- **Given** an application advice that maps `SdkTimeoutException` via
  `problemFactory.create(StandardProblemType.SERVICE_UNAVAILABLE, null, request)`
- **When** a controller throws `SdkTimeoutException`
- **Then** the response is `503` with `type` `<base>service-unavailable` and a `traceId`

### Application handler for a standard MVC exception with `@Order`

- **Given** an application advice with `@Order(0)` handling `MethodArgumentNotValidException`
- **When** a body fails validation
- **Then** the application's response is returned

### Application extends `ResponseEntityExceptionHandler`

- **Given** the application declares its own `ResponseEntityExceptionHandler` subclass as an advice
- **When** the application starts
- **Then** the library's `ProblemDetailsExceptionHandler` is not registered
- **And** an unexpected exception still becomes `500 internal-error`

### Application replaces the factory

- **Given** the application declares its own `ProblemFactory` bean
- **When** any request fails
- **Then** the application's factory builds the problem, including for 401 and 403

## Errors outside Spring MVC

### `sendError` from a filter

- **Given** an application filter that calls `response.sendError(429)` for an anonymous request
- **When** the request is processed
- **Then** the response is `429` with `type` `<base>too-many-requests` and no `detail`
- **And** it is not turned into a `401` by the default chain

### ALTCHA chain denies a non-GET request

- **Given** the ALTCHA module is enabled
- **When** a client sends `POST` to the challenge path
- **Then** the response is a problem with status `403` and `type` `<base>forbidden`

### Original path in `instance` on error dispatch

- **Given** a filter calls `response.sendError(429)` on `/api/test/limited`
- **When** the error is rendered
- **Then** `instance` is `/api/test/limited`, not `/error`

## ALTCHA

### Missing solution

- **Given** the ALTCHA module is enabled and an endpoint calls `AltchaService.requireVerified(null)`
- **When** it is called with `Accept-Language: de`
- **Then** the response is `400` with `type` `<base>altcha-missing`, an English `title`, and the German message as `detail`
- **And** the body has no `code` member

### Invalid and expired solutions

- **Given** an invalid and an expired payload
- **When** each is verified
- **Then** the types are `<base>altcha-invalid` and `<base>altcha-expired`

### ALTCHA with problem details disabled

- **Given** `openelements.problem-details.enabled=false`
- **When** an ALTCHA verification fails
- **Then** the response is `400` `application/json` with `{code, message}`, as before

## Trace ID

### Incoming traceparent is honoured

- **Given** a request with `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`
- **When** it fails with any error
- **Then** `traceId` is `4bf92f3577b34da6a3ce929d0e0e4736`

### All-zero trace-id is ignored

- **Given** `traceparent: 00-00000000000000000000000000000000-00f067aa0ba902b7-01`
- **When** the request fails
- **Then** `traceId` is a freshly generated, non-zero 32-hex value

### Malformed traceparent is ignored

- **Given** each of: uppercase hex, a 31-character trace-id, an all-zero parent-id, version `ff`, version `00`
  with trailing data, two `traceparent` headers
- **When** the request fails
- **Then** `traceId` is freshly generated in each case

### Trace ID is in the MDC during the request

- **Given** a controller that logs a message
- **When** it is called with a valid `traceparent`
- **Then** the log line carries the trace-id in the MDC key `traceId`
- **And** after the request completes, the MDC no longer contains `traceId` on that thread

### Trace ID is available for 401

- **Given** a request without JWT and with a valid `traceparent`
- **When** the security chain rejects it
- **Then** the 401 problem's `traceId` equals the incoming trace-id

### Trace ID is stable within a request

- **Given** a request without `traceparent` that fails
- **When** the 5xx is logged and the problem is written
- **Then** the logged `traceId` and the problem's `traceId` are identical

### Trace filter runs without problem details

- **Given** `openelements.problem-details.enabled=false`
- **When** a controller logs a message
- **Then** the MDC contains `traceId`

### Custom resolver

- **Given** the application declares its own `TraceIdResolver` bean returning a fixed ID
- **When** a request fails
- **Then** the problem's `traceId` is that ID

## Feature disabled

### 401 as before

- **Given** `openelements.problem-details.enabled=false`
- **When** a request to `/api/external/contacts` carries an invalid API key
- **Then** the response is `401`, `Content-Type: application/json`, with exactly the members `status`, `error`,
  `message`, and `WWW-Authenticate: ApiKey realm="external"`

### No library error handling registered

- **Given** `openelements.problem-details.enabled=false`
- **When** the application starts
- **Then** no `ProblemFactory`, `ProblemDetailsExceptionHandler`, `ProblemHandlerExceptionResolver`,
  `ProblemErrorController` or `ProblemAccessDeniedHandler` is registered
- **And** Spring Boot's `BasicErrorController` is active

## SCIM

### SCIM errors are untouched

- **Given** a SCIM request for an unknown user
- **When** the SCIM controller throws `ScimNotFoundException`
- **Then** the response is the RFC 7644 `Error` envelope with `Content-Type: application/scim+json`, as before

## GovStack contract

### Schema provenance is verified

- **Given** the vendored `govstack-openapi-common.yaml`
- **When** the contract test starts
- **Then** it fails unless the file's SHA-256 is `05d1bfc89c86a8d64005e343268326b3fb43cd044fe30f662fec0f9f573b0c73`

### Every error response validates

- **Given** `type-base=https://govstack.global/problems/test/`
- **When** each error scenario of this document is executed
- **Then** body-validation responses validate against `ValidationProblem` and all others against `Problem`

### No forbidden members

- **Given** the same configuration
- **When** each error scenario is executed
- **Then** no problem contains `timestamp`, `error`, `message` or `code`, and no `errors[]` entry contains `code`
