# Design: Spring Boot 3.5 upgrade for Docker Engine 29+ test compatibility

**GitHub Issue:** [#13](https://github.com/OpenElementsLabs/spring-services/issues/13)

## Summary

Integration tests using Testcontainers fail on developer machines running Docker Desktop with Engine 29+. The fix is to upgrade Spring Boot from 3.3.2 to 3.5.14 (latest stable in the 3.5.x line) and explicitly pin Testcontainers to 2.0.5, since Boot's BOM still ships Testcontainers 1.21.4, which has the bug. This is treated as a bug-fix spec — no functional changes to application code.

## Reproduction

1. macOS with Docker Desktop ≥ 4.52 (Engine ≥ 29.0).
2. Run `./mvnw test -Dtest=ApiKeyDataServiceIntegrationTest` (or any other `*IntegrationTest`).
3. Expected: tests pass.
4. Actual: container startup fails with `BadRequestException` — either an empty info-stub payload with a `com.docker.desktop.address` label, or `client version 1.32 is too old. Minimum supported API version is 1.44`.

Both failure modes are confirmed as upstream issues
[#11210](https://github.com/testcontainers/testcontainers-java/issues/11210),
[#11212](https://github.com/testcontainers/testcontainers-java/issues/11212),
[#11235](https://github.com/testcontainers/testcontainers-java/issues/11235), and
[#11360](https://github.com/testcontainers/testcontainers-java/issues/11360)
in the testcontainers-java repository.

CI on `ubuntu-latest` is **not** affected — the bug is specific to Docker Desktop's socket forwarder on macOS.

## Root cause analysis

Two distinct issues, both tied to the docker-java client bundled with Testcontainers ≤ 1.21.x:

1. **Socket redirect-stub.** Engine 29's default socket replies to older clients with HTTP 400 plus a `com.docker.desktop.address` label, signalling that the client should reconnect to a different socket. docker-java in Testcontainers 1.21.x doesn't follow this redirect and treats the response as a hard failure.
2. **API version too old.** Engine 29 enforces a minimum API version of 1.44. Testcontainers 1.21.x sends API version 1.32 hardcoded when starting the Ryuk container, which the engine rejects.

Both fixes landed in **Testcontainers 2.0.2**. The latest release is **2.0.5** (April 2026).

Spring Boot 3.5.14's BOM still pins Testcontainers 1.21.4. Boot has not yet adopted the 2.x line, and there is no public timeline for that change. Therefore, upgrading Spring Boot alone is not sufficient — a targeted Testcontainers override is required.

## Fix approach

Three coordinated changes in `pom.xml`:

1. **Bump Spring Boot:** `3.3.2 → 3.5.14`. Pulls in Spring Security 6.5.10, Spring Framework 6.2.18, Hibernate 6.6.49, byte-buddy 1.17.8 via the BOM.
2. **Pin Testcontainers to 2.0.5** in `<dependencyManagement>`, with a comment explaining the rationale (BOM ships 1.21.4, which has the Engine-29 bug). The pin must cover all four Testcontainers artefacts in use: `testcontainers`, `junit-jupiter`, `postgresql`, and the transitive dependency surface picked up by `spring-boot-testcontainers`.
3. **Remove the explicit byte-buddy override** (currently `1.17.5` for Java 25 support). Boot 3.5.14 ships byte-buddy 1.17.8, which is newer — the override is now redundant and only adds noise.

No application source-code changes are expected. The Spring Security configuration in
`src/main/java/com/openelements/spring/base/security/SecurityConfig.java` (introduced by spec
[006](../006-split-security-filter-chains/design.md)) already uses the lambda DSL that Spring Security 6.5 mandates.

### Rationale: why not wait for Spring Boot 3.6?

Spring Boot 3.6 has no public release timeline. The Boot BOM does not auto-jump to a Testcontainers major (2.x), since that would be a breaking change for downstream consumers. Adoption is likely months out. Pinning Testcontainers explicitly is a small, well-contained change with a clear comment — cleaner than waiting indefinitely on an external schedule.

### Rationale: why upgrade Spring Boot at all if a Testcontainers pin would suffice?

Spring Boot 3.3.2 is from July 2024 and out of mainstream support. The upgrade brings approximately nine months of security and stability fixes alongside the core fix. Bundling both into one focused change avoids a future second upgrade with overlapping breaking-change review.

## Regression risk

| Component | Change | Risk | Reasoning |
|---|---|---|---|
| Spring Security | 6.3 → 6.5.10 | Low | Existing code uses the lambda DSL throughout `SecurityConfig.java` |
| Spring Framework | 6.1 → 6.2.18 | Low | Only minor deprecations; no removed APIs in use |
| Hibernate | 6.5 → 6.6.49 | Low | Standard JPA annotations only |
| Spring Data JPA | minor bump | Low | Repository interfaces unchanged |
| Testcontainers | 1.20.4 → **2.0.5** | **Medium** | Major version bump; the application uses `PostgreSQLContainer` and the `@Testcontainers` JUnit extension, both of which keep stable APIs across the 2.0 release |
| docker-java | 3.4.0 → ≥ 3.5 (transitive via TC 2.0.5) | Low | Used only internally by Testcontainers |
| Mockito (via byte-buddy) | effective version unchanged | None | BOM byte-buddy 1.17.8 ≥ removed override 1.17.5 |

The Testcontainers 2.0 jump is the only meaningful regression risk. Verification (see below) covers it explicitly by running the existing integration test suite on both Engine 28 and Engine 29.

## Verification plan

1. `./mvnw clean verify` passes on this Mac (Docker Engine 29.3.1) without any `~/.testcontainers.properties` workaround file.
2. `./mvnw clean verify` passes on the second Mac (Docker Engine 28.5.1).
3. CI build green on `ubuntu-latest` (`.github/workflows/build.yml` is unchanged — the upgrade must not break Linux Docker either).
4. Smoke-check: at least one test from each major test class (security, JPA, Slack, mail) runs successfully, confirming Spring Boot auto-configuration is unaffected.

## Out of scope

- Java compile target — stays at 21.
- Spring Boot 3.6.x — no GA release available as of April 2026.
- Functional changes to security, data, or service code.
