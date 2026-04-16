# Project Tech Stack

## Languages

- Java 21 (`maven.compiler.source` / `target` = 21)

## Frameworks

- Spring Boot 3.3.2 (managed via `spring-boot-dependencies` BOM)
- Spring Web — REST controllers, `RestClient` for outbound HTTP
- Spring Security — OAuth2 Resource Server (JWT) + custom API-key filter chain
- Spring Data JPA — repositories
- Spring Validation (`spring-boot-starter-validation`)

## Build Tools

- Maven (with Maven Wrapper `./mvnw`)
- Spotless 3.3.0 with Google Java Format (run via `./mvnw spotless:apply` / `spotless:check`)
- CycloneDX Maven plugin 2.9.1 — SBOM generation in the `publication` profile
- JReleaser Maven plugin 1.23.0 — GPG signing, Maven Central deploy, GitHub Release with
  conventional-commits changelog
- Versions Maven plugin 2.21.0 — dependency version updates

## Databases & Services

- PostgreSQL — production database (driver pulled in for tests; runtime driver supplied by the
  consuming application)
- H2 — in-memory database for unit tests
- Testcontainers PostgreSQL — for integration tests

## Key Libraries

- JSpecify 1.0.0 — `@NonNull` / `@Nullable` nullability annotations on the public API
- Swagger Annotations Jakarta 2.2.29 — OpenAPI schema annotations on DTOs
- Hibernate (via Spring Data JPA) — `@CreationTimestamp` / `@UpdateTimestamp` audit columns
- SLF4J — logging (used directly by `WebhookSender`)
- Byte Buddy 1.17.5 (overridden) — pinned for Java compatibility

## Testing

- JUnit 5 (`spring-boot-starter-test`)
- Spring Security Test — security-aware test slices
- Testcontainers 1.20.4 (`testcontainers`, `junit-jupiter`, `postgresql`)
- WireMock 3.10.0 — outbound HTTP mocking for webhook tests
- Surefire is configured with `--add-opens java.base/{java.lang,java.lang.reflect,java.util}=ALL-UNNAMED`

## CI/CD

- GitHub Actions
  - `build.yml` — `spotless:check` + `mvn clean verify` on PRs and pushes
  - `snapshot.yml` — publishes SNAPSHOT to GitHub Packages on push to `main`
- GitHub Packages — SNAPSHOT artifact hosting
- Maven Central via Central Portal — release artifact hosting (driven by JReleaser)

## Distribution

- `groupId` = `com.open-elements`, `artifactId` = `spring-services`
- License: Apache License 2.0
- SCM / homepage: <https://github.com/OpenElementsLabs/spring-services>
