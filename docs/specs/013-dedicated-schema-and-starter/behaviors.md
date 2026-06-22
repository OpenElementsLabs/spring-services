# Behaviors: Dedicated DB schema + Spring Boot starter

## Schema mapping

### Entities are created in the dedicated schema

- **Given** a fresh database and the library with `ddl-auto=create-drop` plus
  `hibernate.hbm2ddl.create_namespaces=true`
- **When** the application context starts
- **Then** the schema `oe_spring_services` exists and the seven tables (`users`, `api_keys`,
  `audit_log`, `comments`, `settings`, `tags`, `webhooks`) are created inside it, not in `public`

### Schema name has a single source of truth

- **Given** `DbSchema.NAME = "oe_spring_services"`
- **When** an entity's `@Table(schema = DbSchema.NAME)` and native SQL both reference the schema
- **Then** changing the constant would change every reference consistently (no literal drift), and
  the constant is a compile-time constant usable in the annotation

### Library-internal foreign keys stay valid within the schema

- **Given** all library tables live in `oe_spring_services`
- **When** an `AuditLogEntity` is persisted referencing a `UserEntity`
- **Then** the `oe_spring_services.audit_log.user_id → oe_spring_services.users.id` foreign key is
  enforced, and `@ManyToOne` navigation from audit log to user resolves the user

### A single transaction across library entities is atomic

- **Given** one persistence unit / one `TransactionManager`
- **When** a transactional operation writes a user and an audit log entry and then fails
- **Then** both writes roll back together (no partial commit), proving the single-context atomicity
  is preserved

## Auto-configuration (starter)

### Dependency alone wires the library

- **Given** an application that only has `spring-services` on the classpath (no
  `@Import(FullSpringServiceConfig)`, no `@EntityScan`, no `@EnableJpaRepositories`)
- **When** the context starts
- **Then** the library's feature beans are present and a library repository (e.g. `UserRepository`)
  can be injected and used

### Application's own entities are still discovered (binding acceptance gate)

- **Given** the auto-configured library and a test application that declares its own `@Entity` +
  Spring Data repository in a sibling package (e.g. `com.example.app.Order` / `OrderRepository`) and
  **no** `@Import`, `@EntityScan`, or `@EnableJpaRepositories`
- **When** the context starts with only the library on the classpath
- **Then** both the application's entity/repository **and** the library's entities/repositories
  (e.g. `UserRepository`) are registered and injectable
- **And** the test fails if **either** side is missing — this is the proof that
  `AutoConfigurationPackages.register` plus `@AutoConfigureBefore(HibernateJpaAutoConfiguration,
  JpaRepositoriesAutoConfiguration)` win the ordering, not an assumption

## Schema convention enforcement

### Every library entity is in the dedicated schema (guard test)

- **Given** a dependency-free test that scans the classpath for all `@Entity` types under
  `com.openelements.spring.base`
- **When** the test inspects each entity's `@Table`
- **Then** every entity has `@Table(schema = DbSchema.NAME)` and the test fails the build if any
  entity (including future ones from specs 010 / 012) omits or changes the schema

### Auto-config backs off without JPA

- **Given** an application without JPA (`EntityManagerFactory` not on the classpath)
- **When** the context starts
- **Then** `SpringServicesAutoConfiguration` does not activate (guarded by
  `@ConditionalOnClass(EntityManagerFactory.class)`) and the context does not fail trying to
  configure persistence

### Explicit import still works

- **Given** an application that keeps `@Import(FullSpringServiceConfig.class)` (explicit wiring)
- **When** the context starts
- **Then** the library works exactly as before with no duplicate-bean errors

## Native SQL

### System user is inserted into the schema-qualified table

- **Given** a started context where no system user row exists
- **When** `SystemUserInitializer` runs
- **Then** the row is inserted via `INSERT INTO oe_spring_services.users (...)` and the system user
  is present in `oe_spring_services.users`

### Test cleanups target the schema-qualified tables

- **Given** an integration test with a `@BeforeEach` cleanup
- **When** the cleanup deletes audit log and user rows
- **Then** the statements address `oe_spring_services.audit_log` and `oe_spring_services.users`
  (child rows before parent rows, preserving the FK order from spec 011's fix)

## Consumer migration

### Existing tables are moved into the new schema

- **Given** a consumer whose `spring-services` tables currently live in `public` with data
- **When** the documented Flyway migration runs (`CREATE SCHEMA ...; ALTER TABLE ... SET SCHEMA ...`
  for all seven tables)
- **Then** the tables and all their data, indexes, and foreign keys exist under
  `oe_spring_services`, and the upgraded application starts and reads/writes them without error

### New consumer provisions the schema first

- **Given** a consumer with no existing `spring-services` tables
- **When** the consumer's Flyway creates `oe_spring_services` and then the tables in it
- **Then** the application starts and Hibernate schema validation (if enabled) passes against the
  schema-qualified entities

### ddl-auto data loss is enforced against (combined release with 014)

- **Given** the combined 013+014 release, where Flyway is a fail-fast requirement (spec 014)
- **When** a consumer runs with the library active but without Flyway and without the opt-out
- **Then** startup fails fast (no silent `ddl-auto` fallback that would orphan `public` data); the
  upgrade doc additionally advises `ddl-auto=validate`/`none`

### Upgrade doc states the upgrade requires downtime

- **Given** the upgrade doc
- **When** a reader plans the rollout
- **Then** it states the migration is stop-the-world and **not** rolling-/HA-safe (an old instance
  expecting `public` breaks the instant `ALTER … SET SCHEMA` runs)

### Upgrade doc lists required privileges

- **Given** the upgrade doc
- **When** a reader prepares database roles
- **Then** it lists DDL/ownership privileges for the migration role and `USAGE` (plus `search_path`)
  for the application's runtime role, without assuming the app role is a full-privilege DB owner

### Release is gated on the upgrade doc

- **Given** the release flow for the version that introduces the dedicated schema
- **When** the release is cut
- **Then** the `docs/upgrade-to-X.Y.md` for that version exists and is committed — the version
  cannot be released without it

## Edge / error cases

### Application with its own `@EntityScan` must include the library root

- **Given** an application that declares an explicit `@EntityScan("com.example.app")` (which
  suppresses the `AutoConfigurationPackages` fallback for entities)
- **When** the context starts without including `com.openelements.spring.base` in that `@EntityScan`
- **Then** the library entities are not found — this is the documented limitation; the consumer must
  add the library root (or a shared common root) to their `@EntityScan`

### Test schema creation is required for create-drop

- **Given** a test profile with `ddl-auto=create-drop` but **without**
  `hibernate.hbm2ddl.create_namespaces=true`
- **When** the context starts
- **Then** schema creation fails because `oe_spring_services` does not exist — the property is
  mandatory for `ddl-auto`-driven test/dev schemas (documented; the project's test profile sets it)
