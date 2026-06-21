# Project Structure

## Repository Layout

```
spring-services/
├── src/main/java/com/openelements/spring/base/
│   ├── FullSpringServiceConfig.java   — Aggregate @Import of every feature config
│   ├── data/                          — Generic CRUD abstractions (DTO/entity/service templates)
│   ├── events/                        — OnObjectCreate / OnObjectUpdate / OnObjectDelete
│   ├── security/
│   │   ├── SecurityConfig.java        — Spring Security filter chain, JWT converter
│   │   ├── AuthService.java           — Facade over SecurityContextHolder (Optional-returning)
│   │   ├── UserInformation.java       — Typed view of the JWT subject
│   │   ├── JsonAuthenticationEntryPoint — Uniform JSON 401 + WWW-Authenticate for both chains
│   │   ├── apikey/                    — ApiKeyAuthenticationFilter (X-API-Key header)
│   │   ├── roles/                     — @Requires* meta-annotations + Roles constants
│   │   └── user/                      — Local user mirror (entity, DTO, repo, service, ImageData)
│   ├── services/
│   │   ├── apikey/                    — API key data layer (entity, DTOs, repo, data service)
│   │   ├── user/                      — UserEntity, UserService, UserProvisioner (REQUIRES_NEW)
│   │   ├── settings/                  — Key/value SettingsDataService + entity/repo
│   │   ├── tag/                       — Tag CRUD + PreTagDeleteEvent
│   │   └── webhook/
│   │       ├── WebhookConfig.java     — RestClient bean (10s timeouts)
│   │       ├── WebhookEventListener   — @TransactionalEventListener(AFTER_COMMIT)
│   │       ├── WebhookSender          — @Async HTTP POST + status persistence
│   │       ├── data/                  — Webhook entity, DTO, repository, data service
│   │       └── payload/               — Outbound JSON payload records
│   └── tenant/                        — Multi-tenancy (entity/repo/service base + EnableTenant)
├── src/test/java/                     — Unit and integration tests (mirrors main package layout)
├── src/test/resources/
│   └── application-testcontainers.properties  — Testcontainers profile config
├── .github/workflows/
│   ├── build.yml                      — CI: spotless:check + mvn clean verify
│   └── snapshot.yml                   — SNAPSHOT publish to GitHub Packages on push to main
├── .claude/                           — Claude Code configuration, skills and conventions
├── specs/
│   ├── INDEX.md                       — Index of all specifications
│   └── 001-release-pipeline/          — Spec for the release pipeline
├── pom.xml                            — Maven build config; JReleaser config inlined in
│                                        the `publication` profile
├── release.sh                         — Manual release script (sets version, builds, runs JReleaser)
├── mvnw, mvnw.cmd, .mvn/              — Maven Wrapper
├── README.md                          — Release process documentation
├── CLAUDE.md                          — Project-level Claude Code rules
└── LICENSE                            — Apache License 2.0
```

## Key Directories

- `src/main/java/com/openelements/spring/base/data/` — `WithId`, `DbEntity`,
  `AbstractEntity` (UUID PK + audit timestamps), `EntityRepository`, `DataService` /
  `DbBackedDataService` and the `AbstractDbBackedDataService` template that wires CRUD,
  transactions and lifecycle events.
- `src/main/java/com/openelements/spring/base/security/` — Security entry point. `SecurityConfig`
  registers the API-key filter ahead of the bearer-token filter and configures the JWT-to-roles
  mapping.
- `src/main/java/com/openelements/spring/base/services/` — Domain features (API keys, settings,
  tags, webhooks). Each subpackage is self-contained: entity + repository + DTO + data service
  + Spring `@Configuration`.
- `src/main/java/com/openelements/spring/base/tenant/` — Tenant-aware analogues of the data
  abstractions: `AbstractMultitenantEntity` (with pre-persist guard),
  `RepositoryWithTenantSupport`, `AbstractMultitenantDbBackedDataService`, plus the
  `@EnableTenant` meta-annotation.
- `src/test/` — Unit and integration tests, including Testcontainers-based integration tests for
  PostgreSQL and WireMock-based tests for webhook delivery.

## Where to Find What

- **Application entry point (for consumers):** `FullSpringServiceConfig` (or any individual
  `*Config` class for à-la-carte usage).
- **Configuration:** `pom.xml` (build, dependencies, JReleaser); `application-testcontainers.properties`
  (test profile).
- **Tests:** `src/test/java/com/openelements/spring/base/...` — package layout mirrors `main`.
- **Documentation:** `README.md` (release process), `specs/` (specification-driven design docs),
  per-package `package-info.java` files for in-source overviews.
- **CI / release scripts:** `.github/workflows/`, `release.sh`, `pom.xml` `publication` profile.
