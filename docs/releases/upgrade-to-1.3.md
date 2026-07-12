# Upgrade prompt: `com.open-elements:spring-services` 1.2.0 → 1.3.0

`spring-services` 1.3.0 is a **DB-breaking (not API-breaking) release**. Two coupled changes:

1. **Dedicated schema.** All seven library tables (`users`, `api_keys`, `audit_log`, `comments`,
   `settings`, `tags`, `webhooks`) now live in a fixed database schema `oe_spring_services` instead
   of the application's default (`public`) schema. They stay in a **single** JPA persistence unit
   (one `EntityManagerFactory`, one `TransactionManager`) — library-internal foreign keys and atomic
   transactions are unchanged.
2. **Real Spring Boot starter.** The library is now an auto-configured starter. A consuming
   application no longer needs `@Import(FullSpringServiceConfig.class)`, `@EntityScan`, or
   `@EnableJpaRepositories` — adding the dependency is enough, and the application's own
   entities/repositories keep being discovered.

There are **no public Java API changes** (method signatures, DTOs, annotations are unchanged). The
break is purely at the database level: the new code will not start against the old `public`-schema
tables until you move them. **The library ships no runtime migrations** — you apply the SQL below with
your own migration tool (Flyway/Liquibase), in version order.

> ⚠️ **Read the warnings before you run anything.** Applied incorrectly (e.g. with
> `spring.jpa.hibernate.ddl-auto=update`), this upgrade causes **silent data loss**. It also requires
> **downtime** — it is not safe for rolling/HA deploys against a shared database.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade from `1.2.0` to `1.3.0`. This release moves the library's tables into a dedicated
`oe_spring_services` schema and turns the library into an auto-configured starter. Follow the steps
and the warnings exactly. Do **not** improvise the database migration.

### What changed in 1.3.0

#### Dependencies

Bump `com.open-elements:spring-services` to `1.3.0`. No other version bumps. Spring Boot stays at
`3.5.14` and Testcontainers stays at `2.0.5`.

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.3.0</version>
</dependency>
```

#### Dedicated schema (`oe_spring_services`)

The seven library tables are now mapped with `@Table(schema = "oe_spring_services")`. Your database
must contain that schema, and the tables must live in it, before the 1.3.0 application starts. This is
what the migration SQL below does.

#### Zero-config starter (optional cleanup)

The library now registers itself via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. If your
application only used `@Import(FullSpringServiceConfig.class)` / `@EntityScan("com.openelements.spring.base")`
/ `@EnableJpaRepositories("com.openelements.spring.base")` **to wire the library**, you may now remove
those — the dependency alone wires everything, and your own entities/repositories continue to be
discovered. This is optional: `@Import(FullSpringServiceConfig.class)` still works.

> **Exception — explicit `@EntityScan`.** If your application declares its own
> `@EntityScan("com.example…")` for its own entities, that *suppresses* the additive fallback and the
> library entities will not be found. In that case add the library root to your scan, e.g.
> `@EntityScan({"com.example…", "com.openelements.spring.base"})` (or a shared common root).

#### No public API changes

No public method signatures, types, annotations, records, enums, or message strings changed between
1.2.0 and 1.3.0. Nothing to migrate in application code.

### Database migration (you run this with your own migration tool)

Add the following as a new versioned migration in your **own** Flyway/Liquibase timeline (e.g.
`V<next>__move_spring_services_to_dedicated_schema.sql`). Apply migrations in version order.

**Existing application — tables currently in `public` (the normal case):**

```sql
CREATE SCHEMA IF NOT EXISTS oe_spring_services;

-- PostgreSQL moves each table together with its constraints and indexes; foreign keys between these
-- tables keep pointing at the moved tables, so order does not matter for referential integrity.
ALTER TABLE users      SET SCHEMA oe_spring_services;
ALTER TABLE api_keys   SET SCHEMA oe_spring_services;
ALTER TABLE audit_log  SET SCHEMA oe_spring_services;
ALTER TABLE comments   SET SCHEMA oe_spring_services;
ALTER TABLE settings   SET SCHEMA oe_spring_services;
ALTER TABLE tags       SET SCHEMA oe_spring_services;
ALTER TABLE webhooks   SET SCHEMA oe_spring_services;
```

**New application — no existing `spring-services` tables:**

```sql
CREATE SCHEMA IF NOT EXISTS oe_spring_services;
-- Then create the seven tables inside oe_spring_services (DDL generated from the entities or
-- hand-written). In dev you may instead let Hibernate create them with
-- spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true — never in production.
```

### ⚠️ Mandatory warnings

- **`ddl-auto=update`/`create` causes silent data loss.** If you run
  `spring.jpa.hibernate.ddl-auto` in `update` or `create` mode, Hibernate sees the schema-qualified
  tables missing and **creates fresh empty tables** in `oe_spring_services`, orphaning your existing
  data in `public`. Apply the migration scripts above with a real migration tool and set `ddl-auto`
  to `validate` or `none` for this upgrade.
- **Downtime required — not rolling-safe.** `ALTER TABLE … SET SCHEMA` is a hard cut: the instant it
  runs, `public.<table>` is gone, and any still-running old (1.2.0) instance — which expects `public`
  — breaks. The upgrade is **stop-the-world**: stop the app, run the migration, start 1.3.0. It is
  not safe for rolling/HA deploys against a shared database.
- **Required database privileges.** The migration role needs DDL rights and table ownership
  (`CREATE SCHEMA`, `ALTER TABLE … SET SCHEMA`). The application's **runtime** role then needs
  `USAGE` on `oe_spring_services` (and the schema on its `search_path` if you run any consumer-side
  native SQL against library tables). Do not assume the application role is a full-privilege DB owner;
  grant the migration and runtime privileges separately.

### Steps

1. Find the consumer's build file that declares `com.open-elements:spring-services`.
2. Bump the `spring-services` version from `1.2.0` to `1.3.0`.
3. Add the migration SQL above as a new versioned script in your own Flyway/Liquibase timeline.
4. Ensure `spring.jpa.hibernate.ddl-auto` is `validate` or `none` for this deploy (not `update`/`create`).
5. Grant the runtime DB role `USAGE` on `oe_spring_services`.
6. (Optional) Remove now-redundant `@Import(FullSpringServiceConfig.class)` / `@EntityScan` /
   `@EnableJpaRepositories` that existed only to wire the library — unless you use `@EntityScan` for
   your own entities, in which case add `com.openelements.spring.base` to it.
7. Deploy with downtime: stop the old instance, run the migration, start 1.3.0.
8. Compile and run the consumer's test suite to verify the build is green.

### Guard rails / Don't do this

- Do **not** run this upgrade with `ddl-auto=update`/`create` — you will orphan your data.
- Do **not** attempt a rolling/zero-downtime deploy against a shared database.
- Do **not** rename columns, change the single-`DataSource` setup, or introduce a second
  `EntityManagerFactory` "to isolate the library" — the design is deliberately one persistence unit.
- Do **not** point your **application's own** entities at `oe_spring_services` — that schema is
  library-owned. Your entities stay in your own schema; a cross-schema FK from your table to a
  library table (referencing the immutable `id` PK) is supported, but never the reverse.
- Do **not** bundle unrelated upgrades or feature work into this version bump.
