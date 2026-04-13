# Project Structure

## Repository Layout

```
spring-services/
├── src/main/java/com/openelements/spring/base/
│   ├── data/                — Base entity, repository, and service abstractions
│   ├── events/              — Spring event system (create, update, delete events)
│   ├── security/
│   │   ├── apikey/          — API key authentication filter and config
│   │   └── user/            — User entity, service, DTO, and repository
│   ├── services/
│   │   ├── apikey/          — API key data service, entity, DTOs
│   │   ├── settings/        — Key-value settings service
│   │   ├── tag/             — Tag CRUD service
│   │   └── webhook/         — Webhook management and async event delivery
│   └── tenant/              — Multi-tenancy support (entity, service, repository)
├── src/test/java/           — Unit and integration tests
├── src/test/resources/      — Test configuration (application-testcontainers.properties)
├── .github/workflows/
│   ├── build.yml            — CI: spotless check + build + test on PRs and pushes
│   └── snapshot.yml         — SNAPSHOT publish to GitHub Packages on push to main
├── specs/                   — Specification-driven development documents
├── pom.xml                  — Maven build configuration
├── jreleaser.toml           — JReleaser config (signing, Maven Central, GitHub Releases)
├── release.sh               — Manual release script
├── .env.example             — Template for local release credentials
└── .editorconfig            — Editor formatting rules
```

## Key Directories

- `src/main/java/com/openelements/spring/base/data/` — Generic CRUD abstractions (`AbstractEntity`, `AbstractDbBackedDataService`)
- `src/main/java/com/openelements/spring/base/security/` — Spring Security config, JWT, API key filter
- `src/main/java/com/openelements/spring/base/services/` — Domain services (API keys, tags, webhooks, settings)
- `src/main/java/com/openelements/spring/base/tenant/` — Multi-tenant entity and service base classes
