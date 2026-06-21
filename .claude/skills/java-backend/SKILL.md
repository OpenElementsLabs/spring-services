---
name: java-backend
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Conventions for building Java backend applications at Open Elements — covers framework choice (Spring Boot vs. Helidon SE), feature-based package structure, REST APIs with OpenAPI/Swagger UI, JPA/Flyway/PostgreSQL data access, GDPR compliance, layer-specific testing (DTO/repository/service/controller), and observability (Prometheus, Loki). Should be automatically loaded whenever Java backend code — controllers, services, repositories, entities, DTOs, REST endpoints, or persistence — is planned, generated, or reviewed. For general Java conventions, see the `java-best-practices` skill.
---

# Java Backend Conventions

Conventions specific to backend applications. Our backends are written in Java. For general Java conventions (code style, testing idioms, logging, build setup), see the `java-best-practices` skill — this skill covers only what is backend-specific.

## Frameworks

We use two frameworks for building backend applications:

- **[Spring Boot](https://spring.io/projects/spring-boot)** — The full-featured option. Use Spring Boot when the application needs a broad ecosystem (security, data access, messaging, etc.) and development speed matters more than minimal footprint.
- **[Helidon SE](https://helidon.io/)** — The lightweight option. Use Helidon SE for performant, lean backends where a small footprint and low startup time are important.

Both are valid choices depending on the project requirements. We aim to provide Open Elements base libraries (as dependencies) for both frameworks in the future.

### Libraries for Backend Frameworks

When building libraries that target backend applications, provide support for Spring Boot and Helidon SE as primary targets. Additionally, offer support for [Eclipse MicroProfile](https://microprofile.io/) and [Eclipse Jakarta EE](https://jakarta.ee/) where feasible, to broaden compatibility. For concrete backend applications, we typically do not use MicroProfile or Jakarta EE directly.

## Package Structure

- **IMPORTANT**: Organize backend packages **by feature/domain context**, not by technical layer.
- Each feature or domain concept gets its own package containing all related classes: entities, repositories, services, DTOs, and controllers.
- Do **not** create top-level packages like `entities/`, `repositories/`, `services/`, `controllers/`, `dtos/` that group classes by their technical role across unrelated features.

**Correct** — packages by feature:

```
com.example.app/
├── user/
│   ├── User.java              (entity)
│   ├── UserRepository.java    (repository)
│   ├── UserService.java       (service)
│   ├── UserDto.java           (DTO)
│   └── UserController.java    (controller)
├── order/
│   ├── Order.java
│   ├── OrderItem.java
│   ├── OrderRepository.java
│   ├── OrderService.java
│   ├── OrderDto.java
│   └── OrderController.java
└── common/
    └── ...                    (shared utilities, base classes, cross-cutting concerns)
```

**Wrong** — packages by layer:

```
com.example.app/
├── entities/
│   ├── User.java
│   └── Order.java
├── repositories/
│   ├── UserRepository.java
│   └── OrderRepository.java
├── services/
│   ├── UserService.java
│   └── OrderService.java
└── controllers/
    ├── UserController.java
    └── OrderController.java
```

Feature-based packaging keeps related code together, makes dependencies between features visible, and allows each feature to evolve independently. It also enables better encapsulation — classes that are only used within a feature can be package-private.

## REST APIs and OpenAPI

- Every backend that exposes REST endpoints must include a Swagger UI for interactive API exploration.
- Use [SpringDoc OpenAPI](https://springdoc.org/) (for Spring Boot) or an equivalent library to generate the OpenAPI specification automatically from code.
- Document every endpoint completely with OpenAPI annotations: summary, description, request/response schemas, status codes, and error responses.
- Use meaningful operation IDs and group endpoints with tags.
- Configure authentication information in the OpenAPI specification so that users can authorize directly in the Swagger UI to test protected endpoints. Include the supported security schemes (e.g., Bearer token, OAuth2) and their configuration.
- Ensure the OpenAPI spec stays in sync with the actual implementation — generate it from code rather than maintaining a separate spec file.
- **IMPORTANT**: Never expose JPA entities directly in REST endpoints (neither as request nor as response objects). Always use dedicated **DTOs** (Data Transfer Objects) for the API layer. Map between entities and DTOs explicitly in the service or controller layer. This avoids leaking internal data model details, prevents lazy-loading and serialization issues, and decouples the API contract from the database schema.

## Data Access and Database

- **IMPORTANT**: Use **JPA** (Jakarta Persistence API) for data access. Do not use implementation-specific APIs (e.g., Hibernate session or criteria API directly) — always program against the JPA interfaces.
- Use **[Flyway](https://flywaydb.org/)** for database schema management and migrations in all projects with a database.
- **PostgreSQL** is the preferred database for test environments and production.
- **H2** (in-memory) is the preferred database for fast, automated unit/integration tests. In the future, we plan to replace H2 with [Testcontainers](https://www.testcontainers.org/)-based PostgreSQL to test against the same database in all environments.
- **IMPORTANT**: Database connection URLs, credentials, and other settings must be configurable via environment variables. For fullstack applications, see the `fullstack-architecture-setup` skill for the full configuration pattern.

## Data Privacy and GDPR

- **IMPORTANT**: All backend applications must be designed with GDPR (DSGVO) compliance in mind.
- Collect only personal data that is strictly necessary for the application's purpose (data minimization).
- Every piece of personal data must have a clear, documented legal basis for processing (e.g., consent, contract fulfillment, legitimate interest).
- Provide API endpoints for data subject rights: access (Art. 15), rectification (Art. 16), erasure (Art. 17), and data portability (Art. 20) where applicable.
- Personal data must be deletable — design database schemas so that user data can be fully removed without breaking referential integrity.
- Log access to personal data for audit purposes, but do not log the personal data itself.
- Do not store personal data in log files, error messages, or stack traces.
- Use encryption at rest and in transit for personal data.
- When integrating third-party services, verify that they are GDPR-compliant and document data processing agreements.

## Testing

Every backend layer must have its own tests. Tests at a higher layer do not replace tests at a lower layer — controller tests do not substitute for missing service or repository tests.

### Layer-specific test requirements

**DTO conversion tests (plain unit tests)**
- Every `fromEntity` or mapping method on a DTO must be tested.
- No Spring context needed — use plain JUnit 5 with direct object construction.
- When entity fields are not accessible via public setters (e.g., `id`, `createdAt`), use reflection in test code. This is acceptable for tests and avoids weakening entity encapsulation.

**Repository tests (`@DataJpaTest`)**
- Every repository interface with custom query methods must have a test class.
- Use `@DataJpaTest` — it auto-configures an embedded H2 database and rolls back after each test. No `@ActiveProfiles` annotation needed.
- Use `TestEntityManager` (`persistAndFlush` + `clear()`) to force real database roundtrips instead of hitting the first-level cache.
- Test custom query methods, pagination, and database constraints (NOT NULL, unique).

**Service tests (`@SpringBootTest`)**
- Every service class must have a test class that covers all public methods.
- Use `@SpringBootTest` with `@ActiveProfiles("test")` and a real H2 database — do not mock repositories.
- Clean up test data in `@BeforeEach` by deleting via repositories in foreign-key order. Do not use `@Transactional` on service tests — this hides transaction boundary bugs in service methods that are themselves `@Transactional`.
- Test happy paths, validation errors, cross-entity business logic, and edge cases.

**Controller tests (`@SpringBootTest` + `MockMvc`)**
- Every controller must have a test class that verifies HTTP status codes, request/response serialization, and validation.
- Use `@SpringBootTest` with `@AutoConfigureMockMvc` and `@ActiveProfiles("test")`.

### General test rules

- **IMPORTANT**: Every test must document what it verifies — use `@DisplayName` for the scenario and expected outcome, and Javadoc on the test class for the unit under test. See `../../conventions/software-quality.md` (Testing section).
- **IMPORTANT**: Every new feature must reach at least **80% test coverage** on the added or changed code. Measure with JaCoCo as part of the build and fail below the threshold. See `../../conventions/software-quality.md` (Testing section).
- Do not mock repositories or other internal dependencies when the real implementation is fast and available. Mocks add complexity without proportional value in small codebases and miss integration bugs.
- Use H2 for all automated tests. In the future, Testcontainers with PostgreSQL will replace H2 (see Data Access section).
- Test classes live in the same package structure as the code they test.

## Observability

- Every backend should expose **metrics** in Prometheus format for monitoring and alerting.
- Every backend should stream **logs** to Loki for centralized log aggregation and querying.
- Concrete implementation details for Spring Boot and Helidon SE are still being defined.
