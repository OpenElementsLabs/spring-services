# Behaviors: Upgrade to Spring Boot 4

Unless stated otherwise: the reactor on `java-parent` 2.0.0-SNAPSHOT (Spring Boot 4.1.2, Java 25), version
`2.0.0-SNAPSHOT`.

## Build

### Full build is green

- **Given** a clean checkout after the migration
- **When** `./mvnw verify` runs in CI, including the Testcontainers tests
- **Then** every module, `spring-services-all` and `spring-services-bom` build and all tests pass

### Boot version comes from the parent only

- **Given** the root `pom.xml` and all module POMs
- **When** they are inspected
- **Then** no POM defines `spring-boot.version` or otherwise overrides a version that `java-parent` manages

### Reactor version

- **Given** the migrated reactor
- **When** the effective version of every module is read
- **Then** it is `2.0.0-SNAPSHOT`

### Build runs on Java 25

- **Given** the CI workflows (`build.yml`, `snapshot.yml`, `release.yml`) and `.sdkmanrc`
- **When** they are inspected
- **Then** every one of them selects Java 25, and no POM overrides `maven.compiler.*` or
  `enforcer.requiredJavaVersion` from `java-parent`

### BOM manages the new MCP and Spring AI coordinates

- **Given** a consumer that imports `spring-services-bom` and depends on `spring-services-mcp` without versions
- **When** the dependency tree is resolved
- **Then** it contains `io.modelcontextprotocol.sdk:mcp-core` 2.1.0 and `org.springframework.ai:mcp-spring-webmvc`
  2.0.x, and no `mcp-json-jackson2`

## Jackson 2 ban

### No Jackson 2 in any runtime tree

- **Given** the migrated reactor
- **When** `mvn dependency:tree -Dscope=runtime` runs for each module
- **Then** neither `com.fasterxml.jackson.core:jackson-databind` nor `com.fasterxml.jackson.core:jackson-core`
  appears

### Enforcer fails on a Jackson 2 dependency

- **Given** a module with a `compile` dependency on `com.fasterxml.jackson.core:jackson-databind`
- **When** the build runs
- **Then** it fails with the message naming spec 025 and the banned artifact

### Transitive Jackson 2 is caught

- **Given** a module whose dependency pulls `com.fasterxml.jackson.core:jackson-databind` transitively
- **When** the build runs
- **Then** it fails the same way

### Jackson annotations stay allowed

- **Given** `com.fasterxml.jackson.core:jackson-annotations` on the runtime classpath (via Jackson 3 or MCP SDK)
- **When** the build runs
- **Then** the enforcer rule does not fail

### Test-scoped Jackson 2 is not banned

- **Given** a `test`-scoped dependency that brings `jackson-databind` 2
- **When** the build runs
- **Then** the enforcer rule does not fail

### No Jackson 2 imports in main code

- **Given** all `src/main/java` sources
- **When** they are searched for `import com.fasterxml.jackson.databind`, `com.fasterxml.jackson.core` and
  `com.fasterxml.jackson.dataformat`
- **Then** there is no match

### Core still bans optional feature libraries

- **Given** `spring-services-core` with a dependency on `org.springframework.ai:mcp-spring-webmvc` or
  `io.modelcontextprotocol.sdk:mcp-core`
- **When** the build runs
- **Then** the core enforcer rule `ban-optional-feature-dependencies` fails

## Jackson 3 public API

### Meilisearch client on Jackson 3

- **Given** a `MeilisearchClient` built with the application's Jackson 3 `ObjectMapper`
- **When** `multiSearch` is called against the WireMock-stubbed Meilisearch
- **Then** it returns a `tools.jackson.databind.JsonNode` with the same content as before the migration

### Library beans receive Boot's mapper

- **Given** an application that customises Boot's JSON mapper (e.g. a naming strategy)
- **When** `ProblemResponseWriter`, `ScimAuthenticationEntryPoint`, `McpToolSupport` and `MeilisearchClient` are
  created by auto-configuration
- **Then** each uses that customised mapper

### JSON pointers follow the naming strategy under Jackson 3

- **Given** `spring.jackson.property-naming-strategy=SNAKE_CASE` (or the Boot 4 equivalent property) and a body
  field `postalCode` violating a constraint
- **When** the request is validated
- **Then** the problem's `errors[0].pointer` is `/postal_code`, as before the migration

### `@JsonProperty` still honoured

- **Given** a body record with `@JsonProperty("postal_code")` from `com.fasterxml.jackson.annotation`
- **When** it fails validation
- **Then** the pointer is `/address/postal_code`

### Serialization failures are unchecked

- **Given** library code that serialized with Jackson 2 and handled `JsonProcessingException`
- **When** serialization fails under Jackson 3
- **Then** the code handles `JacksonException` with the same outcome as before (same log, same response)

## Auto-configuration

### Core starts after the migration

- **Given** an application with `spring-services-core` and a PostgreSQL Testcontainer
- **When** it starts
- **Then** the context starts, Flyway applies the existing migrations, and core runs before JPA auto-configuration

### Problem details still replace Boot's error handling

- **Given** a servlet application with default configuration
- **When** it starts
- **Then** the library's `ProblemDetailsExceptionHandler` and `ProblemErrorController` are registered, and Boot's
  `ProblemDetailsExceptionHandler` and `BasicErrorController` are not

### Trace filter still runs before security

- **Given** a request with a valid `traceparent` and no credentials
- **When** the default chain rejects it
- **Then** the 401 problem's `traceId` equals the incoming trace-id

### Feature modules stay inert by default

- **Given** `spring-services-all` on the classpath and none of the `openelements.*.enabled` properties set
- **When** the application starts
- **Then** the same beans are registered as before the migration (Search, DB backup, MCP, ALTCHA, SCIM inactive)

### Configuration properties bind unchanged

- **Given** the existing property files of the module tests (`openelements.*`)
- **When** the contexts start
- **Then** every `@ConfigurationProperties` record binds the same values as before

## Security

### All chains behave as before

- **Given** the existing security tests for the SCIM, MCP, external API-key, default JWT and ALTCHA chains
- **When** they run on Spring Security 7
- **Then** they pass without changed expectations: same status codes, same `WWW-Authenticate` headers, same
  problem bodies

### JWT roles still map to authorities

- **Given** a JWT with `scope: "read"` and `roles: ["ADMIN"]`
- **When** it is converted
- **Then** the authorities are `SCOPE_read` and `ROLE_ADMIN`

### API keys stay read-only

- **Given** a valid API key
- **When** it is used for `GET` and for `POST` on `/api/external/contacts`
- **Then** `GET` is authorized and `POST` is `403 forbidden`

### Error dispatch still permitted

- **Given** an application filter that calls `response.sendError(429)` for an anonymous request
- **When** the request is processed
- **Then** the response is `429` with a problem body, not `401`

## Persistence

### Entities and timestamps unchanged

- **Given** an entity saved and then updated
- **When** it is read back on Hibernate 7
- **Then** `createdAt` is set once and `updatedAt` changes on the update, as before

### Tenant guard still enforced

- **Given** the tenant module and an entity persisted without a tenant
- **When** it is persisted
- **Then** the pre-persist guard rejects it, as before

### No schema change

- **Given** the Flyway migrations of the reactor
- **When** they are compared to before the migration
- **Then** no migration was added, changed or removed

## MCP

### MCP server answers over streamable HTTP

- **Given** `openelements.mcp.enabled=true` and a valid API key
- **When** a client sends `initialize` and `tools/list` to `/mcp`
- **Then** the server answers with the same tool names and input schemas as before the migration

### Tool call returns the same result

- **Given** a registered tool from the existing `McpToolsTest`
- **When** it is called with the same arguments as before
- **Then** the result content is the same

### MCP still requires an API key

- **Given** `openelements.mcp.enabled=true`
- **When** a request to `/mcp` has no API key
- **Then** the response is `401` with a problem of type `unauthorized`

### MCP stays off by default

- **Given** `spring-services-mcp` on the classpath and `openelements.mcp.enabled` unset
- **When** the application starts
- **Then** no MCP server, transport or MCP security chain is registered

## Tests

### No test removed or weakened

- **Given** the test sources before and after the migration
- **When** they are compared
- **Then** no test method was deleted, newly `@Disabled`, or had an assertion removed
- **And** every changed expectation is listed in `upgrade-to-2.0.md`

## Documentation

### Upgrade guide exists and is complete

- **Given** `docs/releases/upgrade-to-2.0.md`
- **When** it is read
- **Then** it names the required Java, Boot and `java-parent` versions, the starter renames, the Jackson 3 package
  change, the four changed public signatures, `JacksonException`, the MCP SDK and Spring AI dependencies, the
  JSON differences found, and that springdoc still brings Jackson 2 into consumer applications

### JSON differences are recorded

- **Given** the before/after payload capture of webhook, SCIM, problem details and library REST DTOs
- **When** a semantic difference was found
- **Then** it is listed in the upgrade guide, with webhook payload changes in their own section

### Supported Boot range documented

- **Given** the README
- **When** it is read
- **Then** it states that spring-services 1.x supports Spring Boot 3.5 and 2.x supports Spring Boot 4.1
