# Project Tech Stack

## Languages

- Java 21

## Frameworks

- Spring Boot 3.3.2
- Spring Security (OAuth2 Resource Server, JWT)
- Spring Data JPA

## Build Tools

- Maven (with Maven Wrapper `./mvnw`)
- Spotless (Google Java Format)
- CycloneDX (SBOM generation)
- JReleaser 1.23.0 (release automation, GPG signing, Maven Central upload)

## Databases & Services

- PostgreSQL (production)
- H2 (unit tests)

## Key Libraries

- JSpecify (nullability annotations)
- Swagger / OpenAPI 3 annotations
- Byte Buddy 1.17.5 (overridden for Java compatibility)

## Testing

- JUnit 5
- Spring Boot Test / Spring Security Test
- Testcontainers (PostgreSQL)
- WireMock (HTTP mocking)

## CI/CD

- GitHub Actions (build validation, SNAPSHOT publishing)
- GitHub Packages (SNAPSHOT artifact hosting)
- Maven Central via Central Portal (release artifact hosting)
