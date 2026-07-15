# Upgrade prompt: `com.open-elements:spring-services` 1.2.0 → 1.3.0

`spring-services` 1.3.0 is a **coordinate-breaking and DB-breaking release** with one small public
API break (a single `@Configuration` class was renamed). Three coupled changes land together:

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
3. **Real Spring Boot starter.** The library is now auto-configured via
   `SpringServicesCoreAutoConfiguration` (registered in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`). Adding the
   dependency is enough — a consuming application no longer *needs*
   `@Import(FullSpringServiceConfig.class)`, `@EntityScan`, or `@EnableJpaRepositories` to wire the
   library, and its own entities/repositories keep being discovered. **`FullSpringServiceConfig` is
   retained** (the starter itself `@Import`s it), so an existing `@Import(FullSpringServiceConfig.class)`
   still compiles and works — it simply becomes redundant.

Apart from one renamed `@Configuration` class (`LanguageConfig` → `TranslationConfig`, see below),
there are **no public Java API changes** (method signatures, DTOs, annotations, records, enums are
unchanged). The breaks are at the **build-coordinate** level (the parent pom carries no classes), the
**database** level (the new code will not start against the old `public`-schema tables until you move
them), and — only for à-la-carte consumers who imported it directly — the `LanguageConfig` rename.
**The library ships no runtime migrations** — you apply the SQL below with your own migration tool
(Flyway/Liquibase), in version order.

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

#### Zero-config starter (recommended cleanup — not a compile break)

The library now registers itself via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. The dependency
alone now wires everything, and your own entities/repositories continue to be discovered.

**`FullSpringServiceConfig` is retained, not removed.** The starter auto-configuration itself
`@Import`s it, so if your 1.2.0 application wired the library with `@Import(FullSpringServiceConfig.class)`
it **still compiles and works** — the import just becomes redundant (the config is applied once; Spring
dedups the repeated `@Import` by class). Removing it is optional cleanup, not a required fix.

The `@EntityScan("com.openelements.spring.base")` /
`@EnableJpaRepositories("com.openelements.spring.base")` that existed *only* to wire the library
**should** be removed, though: a lingering manual `@EntityScan` makes `EntityScanPackages` non-empty
and thereby suppresses Boot's additive default scan of your application's own package (see the
exception below). Delete these library-only scan annotations; keep `@Import(FullSpringServiceConfig.class)`
or drop it, as you prefer.

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

#### Renamed configuration class: `LanguageConfig` → `TranslationConfig` (breaking-light)

The public `@Configuration` class `com.openelements.spring.base.services.translation.LanguageConfig`
was renamed to `com.openelements.spring.base.services.translation.TranslationConfig`. There is **no
back-compat alias** — the old name is gone. This only affects consumers who referenced the class
directly (à-la-carte wiring); the vast majority who used `@Import(FullSpringServiceConfig.class)` (or
the new starter) are unaffected, because `FullSpringServiceConfig` now imports `TranslationConfig`
internally.

```java
// before (1.2.0) — only if you imported it directly
import com.openelements.spring.base.services.translation.LanguageConfig;
@Import(LanguageConfig.class)

// after (1.3.0)
import com.openelements.spring.base.services.translation.TranslationConfig;
@Import(TranslationConfig.class)
```

The class's behaviour is unchanged — it is the same component scan over the `translation` package,
only the type name changed.

#### No other public API changes

Aside from the `LanguageConfig` → `TranslationConfig` rename above, no public method signatures,
types, annotations, records, enums, or message strings changed between 1.2.0 and 1.3.0. A full
public-type inventory diff of 1.2.0 → 1.3.0 shows exactly one removed type (`LanguageConfig`); every
other new type is purely additive (the new `*AutoConfiguration` classes, `DbSchema`, and the new
`spring-services-mcp` module). Nothing else to migrate in application code beyond the dependency
coordinate and the schema move.

> **Note for explicit-import consumers.** In 1.2.0, `FullSpringServiceConfig` (shipped in the single
> jar) also aggregated the Slack, email, search, and db-backup configurations. In 1.3.0 those features
> live in separate modules and self-activate via their own auto-configurations, so the *core*
> `FullSpringServiceConfig` no longer imports them. If you rely on `@Import(FullSpringServiceConfig.class)`
> for those features, make sure the corresponding feature module (or `spring-services-all`) is on the
> classpath — the module then wires the feature itself; you do not add its `@Import` back.

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
3. Remove any `@EntityScan` / `@EnableJpaRepositories` that existed only to wire the library — unless
   you use `@EntityScan` for your own entities, in which case add `com.openelements.spring.base` to it.
   `@Import(FullSpringServiceConfig.class)` is now optional (the class is retained and still compiles);
   you may leave it or drop it — do not treat it as a required change.
4. If (and only if) you imported `LanguageConfig` directly, rename the reference to `TranslationConfig`
   (package `com.openelements.spring.base.services.translation`). If you only used
   `FullSpringServiceConfig` or the starter, skip this.
5. Add the migration SQL above as a new versioned script in your own Flyway/Liquibase timeline.
6. Ensure `spring.jpa.hibernate.ddl-auto` is `validate` or `none` for this deploy (not `update`/`create`).
7. Grant the runtime DB role `USAGE` on `oe_spring_services`.
8. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
9. Deploy with downtime: stop the old instance, run the migration, start 1.3.0.
10. Compile and run the consumer's test suite to verify the build is green.

### Guard rails / Don't do this

- Do **not** keep depending on the bare `com.open-elements:spring-services` coordinate — it is now a
  parent pom and carries no classes.
- Do **not** re-add `slack-api-client`, the MCP SDK, or `spring-boot-starter-mail` by hand — pull the
  corresponding feature module instead, which brings the dependency transitively.
- Do **not** run this upgrade with `ddl-auto=update`/`create` — you will orphan your data.
- Do **not** attempt a rolling/zero-downtime deploy against a shared database.
- Do **not** mix explicit module versions with the BOM import; let the BOM manage them.
- Do **not** delete `@Import(FullSpringServiceConfig.class)` believing the class was removed — it was
  **not**. Removing it is optional cleanup; the class still exists and still compiles.
- Do **not** create a `LanguageConfig` shim/alias to avoid the rename — update the reference to
  `TranslationConfig` instead. And do **not** touch `TranslationConfig` unless you actually imported
  the old `LanguageConfig` directly.
- Do **not** rename columns, change the single-`DataSource` setup, or introduce a second
  `EntityManagerFactory` "to isolate the library" — the design is deliberately one persistence unit.
- Do **not** point your **application's own** entities at `oe_spring_services` — that schema is
  library-owned. Your entities stay in your own schema; a cross-schema FK from your table to a
  library table (referencing the immutable `id` PK) is supported, but never the reverse.
- Do **not** bundle unrelated upgrades or feature work into this version bump.
