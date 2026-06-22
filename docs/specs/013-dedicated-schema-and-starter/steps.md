# Implementation Steps: Dedicated DB schema + Spring Boot starter

Ordered, atomic steps. Each builds on the previous. Check items off as completed.

---

## Step 1: Schema name constant + entity schema mapping

- [ ] Create `src/main/java/com/openelements/spring/base/data/DbSchema.java` with
  `public static final String NAME = "oe_spring_services";` and a private constructor.
- [ ] Add `schema = DbSchema.NAME` to the existing `@Table` on the 7 concrete entities:
    - [ ] `services/user/UserEntity` (`users`)
    - [ ] `services/apikey/ApiKeyEntity` (`api_keys`)
    - [ ] `services/audit/AuditLogEntity` (`audit_log`)
    - [ ] `services/comment/CommentEntity` (`comments`)
    - [ ] `services/settings/SettingsEntity` (`settings`)
    - [ ] `services/tag/TagEntity` (`tags`)
    - [ ] `services/webhook/data/WebhookEntity` (`webhooks`)
- [ ] Confirm `AbstractEntity` (`@MappedSuperclass`) and `DbEntity` (doc-only) are left unchanged.

**Acceptance criteria:**

- [ ] Project compiles (`./mvnw -o compile`).
- [ ] `@Table(schema = DbSchema.NAME)` present on all 7 entities; `DbSchema.NAME` is a compile-time
  constant usable in the annotation.

**Related behaviors:** Schema name has a single source of truth; Entities are created in the
dedicated schema (enabled here, asserted later).

---

## Step 2: Schema-qualify native SQL

- [ ] `services/user/SystemUserInitializer`: change `INSERT_SQL` to
  `"INSERT INTO " + DbSchema.NAME + ".users (...) VALUES (...)"`.
- [ ] Schema-qualify the native SQL in the test cleanups/seeds (use `DbSchema.NAME` or the literal
  `oe_spring_services.`):
    - [ ] `UserServiceConcurrencyTest` — `delete from oe_spring_services.audit_log` / `... .users where id <> ?`
    - [ ] `UserServiceActiveGateIntegrationTest` — same two deletes
    - [ ] `UserRepositorySchemaTest` — same two deletes
    - [ ] `UserEntityPrincipalDirectoryTest` — same two deletes
    - [ ] `AuditLogIntegrationTest` — `INSERT INTO oe_spring_services.users (...)`
- [ ] Confirm JPA-based cleanups (`commentRepository.deleteAll()`, `auditLogRepository.deleteAll()`)
  are left unchanged (schema-transparent).

**Acceptance criteria:**

- [ ] Project compiles.
- [ ] No remaining unqualified native reference to a library table (`grep` for `INTO users`,
  `from users`, `from audit_log` in `src` returns only schema-qualified hits).

**Related behaviors:** System user is inserted into the schema-qualified table; Test cleanups target
the schema-qualified tables.

---

## Step 3: Enable schema creation in the test profile

- [ ] Add `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true` to
  `src/test/resources/application-testcontainers.properties`.
- [ ] Verify no other test profile relies on `ddl-auto` (open question in design — currently only
  `application-testcontainers.properties` exists; if an H2 profile is added later it needs the
  same property).

**Acceptance criteria:**

- [ ] A single existing Postgres integration test (e.g. `UserRepositorySchemaTest`) runs green —
  proving `create-drop` provisions the `oe_spring_services` schema and the schema-qualified
  tables are created.

**Related behaviors:** Entities are created in the dedicated schema; Test schema creation is
required for create-drop; New consumer provisions the schema first.

---

## Step 4: Spring Boot starter (auto-configuration)

- [ ] Create `src/main/java/com/openelements/spring/base/SpringServicesAutoConfiguration.java`:
    - `@AutoConfiguration`
    - `@ConditionalOnClass(jakarta.persistence.EntityManagerFactory.class)`
    - `@AutoConfigureBefore({HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})`
    - `@Import({FullSpringServiceConfig.class, PackageRegistrar.class})`
    - static nested `PackageRegistrar implements ImportBeanDefinitionRegistrar` calling
      `AutoConfigurationPackages.register(registry, "com.openelements.spring.base")`.
- [ ] Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  containing the single line `com.openelements.spring.base.SpringServicesAutoConfiguration`.

**Acceptance criteria:**

- [ ] Project compiles; `./mvnw -Ppublication clean verify` still builds (no Javadoc/SBOM break).
- [ ] Existing tests still pass (the test apps that use `@Import(...)`/manual config must not break —
  the auto-config is additive and idempotent; `allow-bean-definition-overriding=true` already set
  for the testcontainers profile).

**Related behaviors:** Dependency alone wires the library; Auto-config backs off without JPA;
Explicit import still works.

---

## Step 5: Schema-convention guard test (dependency-free)

- [ ] Add a test (e.g. `data/EntitySchemaConventionTest`) that scans the classpath for every
  `@jakarta.persistence.Entity` type under `com.openelements.spring.base` (reflection /
  `ClassPathScanningCandidateComponentProvider`, **no new dependency**) and asserts each carries
  `@Table` with `schema().equals(DbSchema.NAME)`.
- [ ] Test must enumerate all 7 current entities (guard against an empty/again-passing scan) and fail
  if any entity lacks the schema.

**Acceptance criteria:**

- [ ] Test passes for the current 7 entities.
- [ ] Manually verified to fail if `schema` is removed from one entity (negative check during dev).

**Related behaviors:** Every library entity is in the dedicated schema (guard test).

---

## Step 6: Starter integration test — both app and library persistence resolve

- [ ] Add a test-only application + entity/repository in a **separate** package (e.g.
  `src/test/java/com/example/app/Order.java` + `OrderRepository` + a minimal
  `@SpringBootApplication`/`@SpringBootConfiguration` test config) with **no** `@Import`,
  `@EntityScan`, or `@EnableJpaRepositories`.
- [ ] Boot it via `@SpringBootTest` with only the library on the classpath (auto-config active),
  Postgres Testcontainers, `create_namespaces=true`.
- [ ] Assert both `OrderRepository` (app) **and** `UserRepository` (library) are injectable and
  usable; the test fails if either is missing.

**Acceptance criteria:**

- [ ] Test passes — proving `@AutoConfigureBefore` + `AutoConfigurationPackages.register` win the
  ordering and the additive scan covers both packages.
- [ ] Removing `@AutoConfigureBefore` (dev-time check) is observed to break the test (ordering
  actually matters), then restored.

**Related behaviors:** Application's own entities are still discovered (binding acceptance gate);
Application with its own `@EntityScan` must include the library root (documented limitation —
optionally a negative assertion).

---

## Step 7: Regression — schema-qualified existing tests green

- [ ] Run the full suite. The existing integration tests now exercise the schema end-to-end:
    - FK `audit_log.user_id → users.id` within the schema (`AuditLogIntegrationTest`,
      `CommentServiceIntegrationTest`).
    - Single-transaction atomicity (`UserServiceConcurrencyTest#driftSyncIsTransactional`).
    - System user insert via schema-qualified SQL (`SystemUserInitializer` exercised on startup).

**Acceptance criteria:**

- [ ] `./mvnw -Ppublication clean verify` is green (full suite, Javadoc, sources, SBOM).

**Related behaviors:** Library-internal foreign keys stay valid within the schema; A single
transaction across library entities is atomic; System user is inserted into the schema-qualified
table; Test cleanups target the schema-qualified tables.

---

## Step 8: Migration-SQL integration test (existing tables move correctly)

> **Moved to spec 014 (combined release).** Under Pfad 2 the move SQL becomes library Flyway **V1**
> (conditional move-or-create), so this test belongs to 014's implementation. Re-derive the combined
> step list via `/spec-implement` on 014. Kept here for traceability of the move correctness.

- [ ] Add an integration test (Postgres Testcontainers) that: creates the library tables in `public`
  with seed rows + an FK row (`audit_log → users`), executes the documented migration SQL
  (`CREATE SCHEMA oe_spring_services; ALTER TABLE ... SET SCHEMA ...` for the 7 tables), then
  asserts the tables, rows, indexes, and the FK relationship now exist under
  `oe_spring_services` with data intact.
- [ ] This proves the migration statements in the upgrade doc are correct and FK-/data-preserving.

**Acceptance criteria:**

- [ ] Test passes; row counts and the FK constraint survive the move.

**Related behaviors:** Existing tables are moved into the new schema.

---

## Step 9: Upgrade documentation (migration guide + mandatory warnings)

> **Combined-release doc (013+014).** The move is automatic (library Flyway V1, spec 014), so the doc
> no longer lists a *manual* `ALTER … SET SCHEMA` step. It documents: the automatic move, the
> **"upgrade to the latest 1.x first"** precondition, the Flyway fail-fast requirement + opt-out,
> downtime/stop-the-world, and required privileges.

- [ ] Author `docs/upgrade-to-X.Y.md` (version filled at release time; see open question) following
  the project's upgrade-doc convention, containing:
    - [ ] The dependency bump and a statement that this is a DB-breaking (not API-breaking) change.
    - [ ] Note that the schema move runs automatically via library Flyway V1 (no manual SQL).
    - [ ] **Hard precondition:** upgrade to the latest 1.x (all columns) before the schema release.
    - [ ] **Flyway required (fail-fast):** add `org.flywaydb:flyway-core` or set the opt-out.
    - [ ] **Warning:** stop-the-world / not rolling-safe (downtime required).
    - [ ] **Required privileges:** DDL/ownership for the migration role; `USAGE` + `search_path` for
      the runtime role.
    - [ ] Guard rails / "Don't do this" (no column renames, no second DataSource, app entities stay
      out of `oe_spring_services`).
- [ ] Confirm the release flow treats this doc as a hard gate for the releasing version
  (`release.sh` already generates/commits docs before the tag).

**Acceptance criteria:**

- [ ] Doc exists and contains all mandatory sections above (review checklist).

**Related behaviors:** Upgrade doc warns about ddl-auto data loss; Upgrade doc states the upgrade
requires downtime; Upgrade doc lists required privileges; Release is gated on the upgrade doc; New
consumer provisions the schema first (documented).

---

## Step 10: Project documentation updates (mandatory)

- [ ] `README.md` — Quick Start / Installation: state that adding the dependency is enough (no
  `@Import`/`@EntityScan`/`@EnableJpaRepositories` needed); note the dedicated `oe_spring_services`
  schema and link the upgrade doc; mention `@Import(FullSpringServiceConfig.class)` remains
  optional for explicit wiring.
- [ ] `CLAUDE.md` Project Context — update **Architecture** (auto-config starter + dedicated schema),
  **Structure** (new `SpringServicesAutoConfiguration`, `DbSchema`, `.imports` file), and
  **Tech Stack** if needed.

**Acceptance criteria:**

- [ ] README no longer implies manual `@Import`/scanning is required.
- [ ] CLAUDE.md reflects the starter + schema architecture.

**Related behaviors:** — (documentation; supports Dependency alone wires the library and the
documented-limitation scenario).

---

## Behavior Coverage

| Scenario                                                                  | Layer        | Covered in Step |
|---------------------------------------------------------------------------|--------------|-----------------|
| Entities are created in the dedicated schema                              | Backend      | 1, 3            |
| Schema name has a single source of truth                                  | Backend      | 1               |
| Library-internal foreign keys stay valid within the schema                | Backend      | 7               |
| A single transaction across library entities is atomic                    | Backend      | 7               |
| Dependency alone wires the library                                        | Backend      | 4, 6            |
| Application's own entities are still discovered (binding acceptance gate) | Backend      | 6               |
| Auto-config backs off without JPA                                         | Backend      | 4               |
| Explicit import still works                                               | Backend      | 4, 7            |
| Every library entity is in the dedicated schema (guard test)              | Backend      | 5               |
| System user is inserted into the schema-qualified table                   | Backend      | 2, 7            |
| Test cleanups target the schema-qualified tables                          | Backend      | 2, 7            |
| Existing tables are moved into the new schema                             | Backend      | 8               |
| New consumer provisions the schema first                                  | Backend      | 3, 9            |
| Upgrade doc warns about ddl-auto data loss                                | Docs         | 9               |
| Upgrade doc states the upgrade requires downtime                          | Docs         | 9               |
| Upgrade doc lists required privileges                                     | Docs         | 9               |
| Release is gated on the upgrade doc                                       | Process      | 9               |
| Application with its own `@EntityScan` must include the library root      | Backend/Docs | 6, 10           |
| Test schema creation is required for create-drop                          | Backend      | 3               |

All 19 scenarios are assigned. The four Docs/Process scenarios (ddl-auto warning, downtime,
privileges, release gate) are verified by upgrade-doc review, not automated tests — they describe
documentation/process behavior, not runtime behavior.
