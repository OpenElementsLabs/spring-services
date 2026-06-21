# Behaviors: Spring Boot 3.5 upgrade for Docker Engine 29+ test compatibility

## Test execution on developer machines

### Tests pass on Docker Engine 29+ without workarounds

- **Given** a developer Mac running Docker Desktop with Engine 29.0 or later
- **And** no `~/.testcontainers.properties` file is present
- **And** no `~/.docker-java.properties` file is present
- **When** `./mvnw clean verify` is run
- **Then** all integration tests start their Postgres Testcontainers successfully
- **And** the build completes with exit code 0

### Tests pass on Docker Engine 28.x (regression check)

- **Given** a developer Mac running Docker Desktop with Engine 28.x
- **When** `./mvnw clean verify` is run
- **Then** all integration tests still pass
- **And** the build completes with exit code 0

### Stale Testcontainers strategy file is ignored

- **Given** a `~/.testcontainers.properties` file exists from a previous workaround attempt with an outdated `docker.client.strategy` or `docker.host` entry
- **When** `./mvnw clean verify` is run
- **Then** the build either succeeds (if the file's settings happen to be compatible) or fails with a clear Testcontainers error pointing at the file
- **And** removing the file and re-running the build succeeds

## CI build

### CI build remains green on `ubuntu-latest`

- **Given** the CI workflow `.github/workflows/build.yml` is unchanged
- **And** the runner uses Linux Docker (not Docker Desktop)
- **When** the workflow runs `./mvnw clean verify` after the upgrade
- **Then** the build completes successfully
- **And** all tests pass

## Dependency state after upgrade

### Spring Boot version is 3.5.14

- **Given** the upgrade is complete
- **When** `./mvnw dependency:list` is run
- **Then** `spring-boot-starter-*` artefacts resolve to version `3.5.14`

### Testcontainers version is 2.0.5

- **Given** the upgrade is complete
- **When** `./mvnw dependency:list` is run
- **Then** `org.testcontainers:testcontainers`, `org.testcontainers:junit-jupiter`, and `org.testcontainers:postgresql` all resolve to version `2.0.5`
- **And** the `pom.xml` contains a `<dependencyManagement>` entry for Testcontainers with a comment explaining the override

### byte-buddy override is removed

- **Given** the upgrade is complete
- **When** `pom.xml` is inspected
- **Then** there is no explicit `<dependency>` entry for `net.bytebuddy:byte-buddy` or `net.bytebuddy:byte-buddy-agent` in `<dependencyManagement>`
- **And** `./mvnw dependency:list` shows byte-buddy resolved to the BOM-provided version (1.17.8 or higher)

## Existing functionality is preserved

### API-key chain still authenticates `/api/external/**`

- **Given** the application is running with the upgraded dependencies
- **And** a valid API key exists for an account
- **When** a `GET /api/external/...` request is sent with the matching `X-API-Key` header
- **Then** the request succeeds with the expected response

### API-key chain still rejects non-read methods

- **Given** the application is running with the upgraded dependencies
- **When** a `POST /api/external/...` request is sent with a valid `X-API-Key`
- **Then** the request is rejected with `403 Forbidden`

### JWT chain still authenticates non-`/api/external/**` requests

- **Given** the application is running with the upgraded dependencies
- **And** a valid JWT is presented as a Bearer token
- **When** a request is sent to a JWT-protected endpoint
- **Then** the request succeeds with the expected response
- **And** authorities derived from both `scope`/`scp` and `roles` claims are exposed (`SCOPE_*` and `ROLE_*`)

### Anonymous endpoints remain anonymously accessible

- **Given** the application is running with the upgraded dependencies
- **When** an unauthenticated request hits `/api/health/**`, `/swagger-ui.html`, `/swagger-ui/**`, or `/v3/api-docs/**`
- **Then** the request succeeds without an authentication challenge

### Spring Data JPA repositories still work

- **Given** the application is running with the upgraded dependencies
- **When** an integration test that exercises a JPA repository (e.g., `ApiKeyDataServiceIntegrationTest`) runs
- **Then** entities can be persisted, queried, and deleted as before
- **And** audit log entries are still recorded for create/update/delete operations (spec 005)

### Slack and email services still wire up correctly

- **Given** the application is running with the upgraded dependencies
- **When** the Spring context loads in a test that uses the Slack or email service beans
- **Then** the beans are present and injectable
- **And** sending operations behave the same as before the upgrade

## Build hygiene

### No new compiler warnings introduced

- **Given** the upgrade is complete
- **When** `./mvnw clean compile` is run
- **Then** no new compiler warnings are emitted relative to the pre-upgrade baseline

### No new deprecation warnings from Spring Security/Framework/Hibernate

- **Given** the upgrade is complete
- **When** `./mvnw clean test-compile` is run
- **Then** no deprecation warnings appear for code that compiled cleanly before the upgrade
