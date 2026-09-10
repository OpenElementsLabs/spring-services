# Behaviors: Token exchange (RFC 8693) for delegated service-to-service calls

Scenarios marked **[chain]** run through the real Spring Security filter chain (extending the
spec-017 harness in `com.example.rolesapp`); those marked **[wiremock]** run against a WireMock token
endpoint in `spring-services-token-exchange`; the rest are unit-level with the `Authentication` bound
to the `SecurityContextHolder` by hand. Scenarios marked **[idp]** are the pre-merge verification
against real Authentik and Keycloak instances and are not automated.

## Reading the calling client

### The `azp` claim identifies the calling client

- **Given** a `Jwt` with `azp = "tasks-backend"` and no `client_id` claim
- **When** `authService.findCallerClientId()` is called
- **Then** the result is `Optional.of("tasks-backend")`

### The `client_id` claim is the fallback

- **Given** a `Jwt` with `client_id = "tasks-backend"` and no `azp` claim
- **When** `authService.findCallerClientId()` is called
- **Then** the result is `Optional.of("tasks-backend")`

### `azp` wins when both are present

- **Given** a `Jwt` with `azp = "tasks-backend"` and `client_id = "something-else"`
- **When** `authService.findCallerClientId()` is called
- **Then** the result is `Optional.of("tasks-backend")`
- **And** the precedence is documented, not incidental

### A token without either claim yields no client id

- **Given** a `Jwt` with neither `azp` nor `client_id`
- **When** `authService.findCallerClientId()` is called
- **Then** the result is empty
- **And** no exception is thrown

### A non-JWT caller yields no client id

- **Given** an API-key authentication (principal is an `ApiKeyEntity`)
- **When** `authService.findCallerClientId()` is called
- **Then** the result is empty

## Classifying the caller's origin

### The own frontend is a direct caller

- **Given** `openelements.security.own-client-id = "crm-frontend"`
- **And** a `Jwt` whose `azp` is `"crm-frontend"`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.DIRECT`

### Another client is a delegated caller

- **Given** `openelements.security.own-client-id = "crm-frontend"`
- **And** a `Jwt` whose `azp` is `"tasks-backend"`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.DELEGATED`

### A token without a client id is `UNKNOWN`

- **Given** `openelements.security.own-client-id` is configured
- **And** a `Jwt` with neither `azp` nor `client_id`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.UNKNOWN`

### A missing own-client-id configuration is `UNKNOWN`, not `DELEGATED`

- **Given** `openelements.security.own-client-id` is **not** configured
- **And** a `Jwt` whose `azp` is `"crm-frontend"`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.UNKNOWN`
- **And** the library does not guess — an unconfigured deployment gets no verdict

### An empty security context is `UNKNOWN`

- **Given** an empty `SecurityContext`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.UNKNOWN`
- **And** no exception is thrown

### Comparison is exact

- **Given** `openelements.security.own-client-id = "crm-frontend"`
- **And** a `Jwt` whose `azp` is `"CRM-Frontend"`
- **When** `authService.getCallerOrigin()` is called
- **Then** the result is `CallerOrigin.DELEGATED` — matching is case-sensitive, as with role names in
  spec 017

## Reading the actor (delegation)

### An `act` claim surfaces the actor's subject

- **Given** a `Jwt` carrying `act = {"sub": "tasks-service"}`
- **When** `authService.findActorSubject()` is called
- **Then** the result is `Optional.of("tasks-service")`

### A delegated call without an `act` claim is still delegated

- **Given** `openelements.security.own-client-id = "crm-frontend"`
- **And** a `Jwt` whose `azp` is `"tasks-backend"` and which carries **no** `act` claim (the Keycloak
  case)
- **When** both accessors are called
- **Then** `getCallerOrigin()` is `CallerOrigin.DELEGATED`
- **And** `findActorSubject()` is empty — the actor is unknown, the delegation is not

### A malformed `act` claim does not break the read

- **Given** a `Jwt` whose `act` claim is a string instead of an object, or an object without `sub`
- **When** `authService.findActorSubject()` is called
- **Then** the result is empty
- **And** no exception is thrown

## Through the real filter chain

### A frontend token arrives as `DIRECT` **[chain]**

- **Given** the default chain, `own-client-id` set to the test client, and a JWT with that `azp`
- **When** an endpoint reports `authService.getCallerOrigin()`
- **Then** the value is `CallerOrigin.DIRECT`

### A foreign backend's token arrives as `DELEGATED` **[chain]**

- **Given** the same setup and a JWT whose `azp` is a different client
- **When** the endpoint reports `authService.getCallerOrigin()`
- **Then** the value is `CallerOrigin.DELEGATED`

### Delegation is orthogonal to the authentication type **[chain]**

- **Given** a delegated JWT call
- **When** both `authService.getAuthenticationType()` (spec 019) and `getCallerOrigin()` are read
- **Then** the type is `AuthenticationType.JWT_ACCOUNT` **and** the origin is `CallerOrigin.DELEGATED`
- **And** neither value is derived from the other

## Performing the exchange

The outbound API is a `RestClient` customizer per target; no application code ever receives a token
value. Scenarios call a `RestClient` built through `tokenExchange.forTarget("crm")`.

### The exchange request carries the `audience` parameter **[wiremock]**

- **Given** a client registration for target `crm` with grant type
  `urn:ietf:params:oauth:grant-type:token-exchange` and
  `openelements.token-exchange.targets.crm.audience = "crm-backend"`
- **And** an incoming request authenticated with a `Jwt`
- **When** application code performs a `GET` through the `crm` `RestClient`
- **Then** the token endpoint receives a form body containing
  `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token` equal to the incoming
  token value, `subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
  `requested_token_type=urn:ietf:params:oauth:token-type:access_token` and
  **`audience=crm-backend`**

### The outgoing request carries the exchanged token, not the incoming one **[wiremock]**

- **Given** the same setup and a token endpoint answering with a distinguishable `access_token`
- **When** the call is performed through the `crm` `RestClient`
- **Then** the target service receives `Authorization: Bearer <exchanged token>`
- **And** the incoming user token never appears in the outgoing request

### The subject token is the incoming user's token, not a client-credentials token **[wiremock]**

- **Given** an incoming request authenticated with a specific `Jwt`
- **When** the call is performed
- **Then** `subject_token` is exactly that request's token value — proving Spring's default
  `subjectTokenResolver` picks up the current principal

### No application-facing method returns a token value

- **Given** the public API of `spring-services-token-exchange`
- **When** it is inspected
- **Then** no public method returns an access-token value, and the module ships no
  `exchangeFor(...)`-style accessor
- **And** the only entry point is the per-target `RestClient` customizer

### A call outside a request context fails cleanly **[wiremock]**

- **Given** no security context (an `@Async` or `@Scheduled` execution)
- **When** the call is performed through the `crm` `RestClient`
- **Then** it fails with Spring's `ClientAuthorizationException` (no authorized client obtainable)
- **And** no token value appears in the message

### `invalid_target` from the IdP surfaces as Spring's authorization failure **[wiremock]**

- **Given** the token endpoint answers `400` with `{"error":"invalid_target"}`
- **When** the call is performed
- **Then** a `ClientAuthorizationException` carrying the OAuth2 error code `invalid_target` is thrown
- **And** the module defines no exception type of its own
- **And** no token value appears in the message or in any log line

### A rejected token invalidates the stored client **[wiremock]**

- **Given** a previously successful exchange for target `crm`
- **And** the target service now answers `401` with a `WWW-Authenticate: Bearer error="invalid_token"`
  header
- **When** the call is performed
- **Then** the configured `OAuth2AuthorizationFailureHandler` removes the stored authorized client
- **And** the next call performs a fresh exchange instead of reusing the rejected token

### An unknown target id fails fast

- **Given** no client registration named `"nope"`
- **When** `tokenExchange.forTarget("nope")` is used to build a client
- **Then** the failure names the missing registration
- **And** it surfaces at wiring time where possible, not on the first HTTP call

### The exchanged token is reused while valid **[wiremock]**

- **Given** a successful exchange for target `crm` within one request principal's context
- **When** a second call through the `crm` `RestClient` happens before expiry
- **Then** the token endpoint is **not** called again — `OAuth2AuthorizedClientManager` serves the
  stored client
- **And** the same token value is sent

## Module wiring

### The module self-activates and stays inert when absent

- **Given** an application with `spring-services-core` but **without** `spring-services-token-exchange`
- **When** the context starts
- **Then** it starts successfully and no token-exchange bean exists
- **And** `getCallerOrigin()` still works — the inbound half lives in `core`

### Core keeps its dependency footprint

- **Given** the `spring-services-core` dependency tree
- **When** it is resolved
- **Then** `spring-security-oauth2-client` is absent
- **And** the enforcer rule in `spring-services-core/pom.xml` is extended to keep it that way

## Documentation contract

### The audience gap is documented where it is used

- **Given** the README section on token exchange
- **When** it is read
- **Then** it states that `getCallerOrigin()` is a label — not a control — until
  `spring.security.oauth2.resourceserver.jwt.audiences` is configured
- **And** it names the property and points to the `docs/TODO.md` entry

## Pre-merge verification against real instances

### Authentik: what the exchanged token carries **[idp]**

- **Given** an Authentik provider (2026.8+) with the token-exchange grant, bound to an application
- **When** a delegation exchange is performed and the resulting token decoded
- **Then** record `aud`, `azp`/`client_id` and whether `act.sub` is present
- **And** confirm whether the Actor/parent-user constraint permits "service acts for arbitrary users"

### Keycloak: what the audience mapper writes **[idp]**

- **Given** Keycloak 26.2+ with an *Audience* protocol mapper on an assigned client scope
- **When** an internal-internal exchange with `audience` is performed and the token decoded
- **Then** record whether `aud` contains the **client id or the internal UUID**
- **And** confirm `azp`/`client_id` names the requesting backend
