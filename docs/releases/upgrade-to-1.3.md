# Upgrade prompt: `com.open-elements:spring-services` 1.2.0 → 1.3.0

`spring-services` 1.3.0 is a **coordinate-breaking and DB-breaking (but not Java-API-breaking)
release**. Three coupled changes land together:

1. **Maven multi-module reactor (coordinate change).** The `com.open-elements:spring-services`
   coordinate is now the **reactor parent** (`packaging=pom`) and no longer ships a jar with classes.
   A consumer that keeps depending on the bare coordinate will fail to compile. The library is split
   into `spring-services-core` (all seven entities, single persistence unit) plus optional feature
   modules — `spring-services-slack`, `spring-services-mcp`, `spring-services-email`,
   `spring-services-search`, `spring-services-dbbackup` — an **everything** bundle
   `spring-services-all`, and a BOM `spring-services-bom`. Heavy/rare libraries (`slack-api-client`,
   the MCP SDK, `spring-boot-starter-mail`) are no longer pulled unless the matching module is present.
2. **Dedicated schema.** All seven library tables (`users`, `api_keys`, `audit_log`, `comments`,
   `settings`, `tags`, `webhooks`) now live in a fixed database schema `oe_spring_services` instead
   of the application's default (`public`) schema. They stay in a **single** JPA persistence unit
   (one `EntityManagerFactory`, one `TransactionManager`) — library-internal foreign keys and atomic
   transactions are unchanged.
3. **Real Spring Boot starter.** The library is now auto-configured. `FullSpringServiceConfig` has
   been **removed**; a consuming application no longer uses `@Import(FullSpringServiceConfig.class)`,
   `@EntityScan`, or `@EnableJpaRepositories` to wire the library — adding the dependency is enough,
   and the application's own entities/repositories keep being discovered.

There are **no public Java API changes** (method signatures, DTOs, annotations, records, enums are
unchanged). The breaks are at the **build-coordinate** level (the parent pom carries no classes) and
the **database** level (the new code will not start against the old `public`-schema tables until you
move them). **The library ships no runtime migrations** — you apply the SQL below with your own
migration tool (Flyway/Liquibase), in version order.

> ⚠️ **Read the warnings before you run anything.** Applied incorrectly (e.g. with
> `spring.jpa.hibernate.ddl-auto=update`), this upgrade causes **silent data loss**. It also requires
> **downtime** — it is not safe for rolling/HA deploys against a shared database.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade from `1.2.0` to `1.3.0`. This release turns the library into a Maven reactor (you must
change the dependency coordinate), moves the library's tables into a dedicated `oe_spring_services`
schema, and makes the library an auto-configured starter. Follow the steps and the warnings exactly.
Do **not** improvise the database migration.

### What changed in 1.3.0

#### Dependency coordinate (required change)

The old single coordinate no longer ships classes — it is now a parent pom. Choose one of two
migration paths.

**Path A — everything (drop-in).** Replace `spring-services` with `spring-services-all`:

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
    <artifactId>spring-services-all</artifactId>
    <version>1.3.0</version>
</dependency>
```

`spring-services-all` bundles core plus every feature module, reproducing the pre-split behaviour.

**Path B — à-la-carte (only what you use).** Import the BOM once, then declare `spring-services-core`
plus only the feature modules you need, without versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.open-elements</groupId>
            <artifactId>spring-services-bom</artifactId>
            <version>1.3.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.open-elements</groupId>
        <artifactId>spring-services-core</artifactId>
    </dependency>
    <!-- add only the features you use, e.g.: -->
    <dependency>
        <groupId>com.open-elements</groupId>
        <artifactId>spring-services-slack</artifactId>
    </dependency>
</dependencies>
```

Feature module artifact ids: `spring-services-slack`, `spring-services-mcp`,
`spring-services-email`, `spring-services-search`, `spring-services-dbbackup`.

#### Zero-config starter (required cleanup)

The library now registers itself via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, and
**`FullSpringServiceConfig` has been removed**. If your 1.2.0 application wired the library with
`@Import(FullSpringServiceConfig.class)`, you **must delete that import** — it no longer compiles.
The dependency alone now wires everything, and your own entities/repositories continue to be
discovered, so `@EntityScan("com.openelements.spring.base")` /
`@EnableJpaRepositories("com.openelements.spring.base")` that existed *only* to wire the library
should be removed too.

> **Exception — explicit `@EntityScan`.** If your application declares its own
> `@EntityScan("com.example…")` for its own entities, that *suppresses* the additive fallback and the
> library entities will not be found. In that case add the library root to your scan, e.g.
> `@EntityScan({"com.example…", "com.openelements.spring.base"})` (or a shared common root).

#### Self-activation of feature modules

Each feature module self-activates when it is on the classpath (via its own auto-configuration),
gated by the same enable properties the features already used (e.g. `openelements.mcp.enabled`,
`openelements.meilisearch.enabled`, `openelements.db-backup.enabled`). Slack and email activate when
their module is present. You do not need `@Import` to wire a feature — adding the module is enough.

#### Dedicated schema (`oe_spring_services`)

The seven library tables are now mapped with `@Table(schema = "oe_spring_services")`. Your database
must contain that schema, and the tables must live in it, before the 1.3.0 application starts. This is
what the migration SQL below does.

#### No public API changes

No public method signatures, types, annotations, records, enums, or message strings changed between
1.2.0 and 1.3.0. Nothing to migrate in application code beyond the dependency coordinate and the
removal of the now-deleted `@Import(FullSpringServiceConfig.class)`.

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
2. Replace the coordinate per **Path A** (switch to `spring-services-all`) or **Path B** (BOM +
   `-core` + chosen feature modules). Bump the version to `1.3.0`.
3. Delete `@Import(FullSpringServiceConfig.class)` (the class is gone in 1.3.0) and any
   `@EntityScan` / `@EnableJpaRepositories` that existed only to wire the library — unless you use
   `@EntityScan` for your own entities, in which case add `com.openelements.spring.base` to it.
4. Add the migration SQL above as a new versioned script in your own Flyway/Liquibase timeline.
5. Ensure `spring.jpa.hibernate.ddl-auto` is `validate` or `none` for this deploy (not `update`/`create`).
6. Grant the runtime DB role `USAGE` on `oe_spring_services`.
7. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
8. Deploy with downtime: stop the old instance, run the migration, start 1.3.0.
9. Compile and run the consumer's test suite to verify the build is green.

### Guard rails / Don't do this

- Do **not** keep depending on the bare `com.open-elements:spring-services` coordinate — it is now a
  parent pom and carries no classes.
- Do **not** re-add `slack-api-client`, the MCP SDK, or `spring-boot-starter-mail` by hand — pull the
  corresponding feature module instead, which brings the dependency transitively.
- Do **not** run this upgrade with `ddl-auto=update`/`create` — you will orphan your data.
- Do **not** attempt a rolling/zero-downtime deploy against a shared database.
- Do **not** mix explicit module versions with the BOM import; let the BOM manage them.
- Do **not** rename columns, change the single-`DataSource` setup, or introduce a second
  `EntityManagerFactory` "to isolate the library" — the design is deliberately one persistence unit.
- Do **not** point your **application's own** entities at `oe_spring_services` — that schema is
  library-owned. Your entities stay in your own schema; a cross-schema FK from your table to a
  library table (referencing the immutable `id` PK) is supported, but never the reverse.
- Do **not** bundle unrelated upgrades or feature work into this version bump.
