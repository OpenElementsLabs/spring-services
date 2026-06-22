# Behaviors: Library-owned Flyway migrations

## Library-owned migration run

### Library migrates its own schema on startup

- **Given** a consumer with the library on the classpath and an empty database
- **When** the application starts
- **Then** the library's Flyway instance creates `oe_spring_services` and its 7 tables and records
  the run in a `flyway_schema_history` table **inside** `oe_spring_services`

### Library and application Flyway histories are isolated

- **Given** a consumer that also runs its own application Flyway (default location + history table)
- **When** the application starts
- **Then** there are two independent history tables (one in `oe_spring_services`, one in the app
  schema), neither validates the other's scripts, and no checksum conflict occurs

### Library schema change is applied automatically on upgrade

- **Given** a deployed consumer at library version N, and a new library version N+1 that ships an
  additional migration for `oe_spring_services`
- **When** the consumer bumps the dependency and restarts
- **Then** the library Flyway applies the new migration automatically; the consumer wrote no SQL

## Ordering

### Library Flyway runs before application Flyway

- **Given** an application migration that adds a foreign key into `oe_spring_services.users`
- **When** startup runs both migration sets
- **Then** the library migration that creates `oe_spring_services.users` has run first, so the
  application FK creation succeeds (proven by an integration test, not assumed)

### Both Flyway runs complete before the EntityManagerFactory initializes

- **Given** `ddl-auto=validate`
- **When** the context starts
- **Then** Hibernate validation runs against the fully migrated schema and passes (no "table not
  found" during EMF init)

## Cross-entity references

### Application entity references a library entity

- **Given** an application entity with `@ManyToOne UserEntity` (library entity)
- **When** the schema is built
- **Then** a cross-schema foreign key `app_schema.<table>.user_id → oe_spring_services.users.id`
  exists and the association is navigable in one persistence context

### Reference targets a stable library primary key

- **Given** an application FK into a library table
- **When** it is defined
- **Then** it references the library `id` primary key (stable `UUID`), not a volatile column such as
  `sub`, `user_name`, or `email`

### Referential action is owned by the application

- **Given** an application FK `app.orders.user_id → oe_spring_services.users.id` defined with
  `ON DELETE RESTRICT`
- **When** a library operation attempts to delete a referenced `UserEntity`
- **Then** the delete is blocked by the application-defined FK, and the library code surfaces the
  constraint violation rather than assuming the delete always succeeds

### Library never references an application table (forbidden)

- **Given** the library codebase
- **When** the convention guard test runs
- **Then** no library entity declares an association/foreign key to a non-library type — the
  app→lib direction is the only permitted one

### Destructive library change follows the deprecation cycle

- **Given** a library table/column that applications may reference and that is slated for removal
- **When** the library plans the removal
- **Then** it is announced as deprecated in release N (changelog/upgrade doc) and removed no earlier
  than release N+1, so a consumer can drop its FK in a separate earlier deploy (while on version N)
  before upgrading — the library never hard-drops a referenced table/column unannounced or via
  `DROP … CASCADE`

### Concurrent instance startup is serialized by the history lock

- **Given** multiple instances of a consumer starting concurrently (stop-the-world deploy of N
  replicas) against one database
- **When** they run the library migration simultaneously
- **Then** Flyway's history-table lock serializes the apply (one migrates, others skip
  already-applied); mixed library versions are out of scope because rolling deploys are unsupported

## Flyway requirement (fail-fast) and opt-out

### Missing Flyway without opt-out fails fast

- **Given** a consumer with `spring-services` active (JPA present), **no** Flyway on the classpath,
  and the opt-out **not** set
- **When** the application starts
- **Then** context startup **fails** with a clear message instructing the consumer to add
  `org.flywaydb:flyway-core` or set the opt-out — it does **not** silently fall back to `ddl-auto`
  (which would risk data loss)

### Consumer opts out to self-manage the schema

- **Given** a consumer that sets the opt-out property
- **When** the application starts
- **Then** the library runs no migration, performs no schema management, and the fail-fast Flyway
  check is skipped; the consumer owns all migrations

## Coexistence with the application's Flyway

### The application's own Flyway still runs

- **Given** a consumer with its **own** application Flyway (default location + history table) and the
  library on the classpath
- **When** the application starts
- **Then** both migration runs execute — the library does not register a `Flyway`-typed bean, so
  Boot's application Flyway auto-configuration is **not** suppressed; both history tables exist and
  advance independently

## V1 — conditional move-or-create

### Existing 1.x consumer: tables are moved, not recreated

- **Given** a consumer upgrading from the latest 1.x with the 7 tables and data in `public`
- **When** the combined 013+014 release starts and library V1 runs
- **Then** V1 detects the existing `public` tables and `ALTER … SET SCHEMA`-moves them (with data,
  indexes, FKs) into `oe_spring_services` — it does **not** create empty tables or orphan data

### Greenfield consumer: tables are created in the schema

- **Given** a consumer with no existing `spring-services` tables
- **When** library V1 runs
- **Then** V1 creates `oe_spring_services` and the 7 tables matching the entity mappings, and
  `ddl-auto=validate` passes

### Upgrade precondition is enforced by documentation

- **Given** a consumer on an **older** 1.x (missing columns, e.g. pre-SCIM)
- **When** they attempt to jump straight to the schema release
- **Then** the documented precondition ("upgrade to the latest 1.x first") applies; V1 only moves by
  location and does not reconcile column drift, so skipping the precondition surfaces as a
  `ddl-auto=validate` failure rather than silent corruption

## Edge / error cases

### Library migration failure aborts startup cleanly

- **Given** a library migration that fails (e.g. due to insufficient privileges)
- **When** the application starts
- **Then** startup fails fast with a clear error attributing the failure to the library Flyway
  instance (not the application's), and the application Flyway / EMF do not run against a
  half-migrated schema
