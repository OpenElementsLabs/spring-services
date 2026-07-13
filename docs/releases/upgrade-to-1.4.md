# Upgrade prompt: `com.open-elements:spring-services` 1.3.0 → 1.4.0

`spring-services` 1.4.0 restructures the library into a **Maven multi-module reactor**. This is a
**breaking coordinate change** — but **not** a Java-API or database change:

- The `com.open-elements:spring-services` coordinate is now the **reactor parent** (`packaging=pom`).
  It no longer resolves to a jar with classes, so a consumer that declares it as a dependency will
  fail to compile.
- The library is split into `spring-services-core` (all seven entities, single persistence unit) plus
  optional feature modules — `spring-services-slack`, `spring-services-mcp`, `spring-services-email`,
  `spring-services-search`, `spring-services-dbbackup` — an **everything** bundle
  `spring-services-all`, and a BOM `spring-services-bom`.
- Consumers now depend only on the features they use; a consumer that used everything switches to
  `spring-services-all` (drop-in). Heavy/rare libraries (`slack-api-client`, the MCP SDK,
  `spring-boot-starter-mail`) are no longer pulled unless the matching module is present.

There are **no public Java API changes** (method signatures, DTOs) and **no schema change** — the
`oe_spring_services` schema introduced in 1.3.0 is unchanged. The one internal rename: the starter
auto-configuration `com.openelements.spring.base.SpringServicesAutoConfiguration` is now
`SpringServicesCoreAutoConfiguration` (only relevant if you referenced it in an
`spring.autoconfigure.exclude` list).

> If you are upgrading from a version **older than 1.3.0**, apply the 1.3.0 database migration first
> (see `upgrade-to-1.3.md`: move the tables into `oe_spring_services`), then this coordinate swap.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade from `1.3.0` to `1.4.0`. The library became a Maven reactor; you must change the
dependency coordinate. No application code, schema, or configuration changes are required.

### What changed in 1.4.0

#### Dependency coordinate (required change)

The old single coordinate no longer ships classes. Choose one of two migration paths.

**Path A — everything (drop-in).** Replace `spring-services` with `spring-services-all`:

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-all</artifactId>
    <version>1.4.0</version>
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
            <version>1.4.0</version>
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

#### Self-activation

Each feature module self-activates when it is on the classpath (via its own auto-configuration),
gated by the same enable properties as before (e.g. `openelements.mcp.enabled`,
`openelements.meilisearch.enabled`, `openelements.db-backup.enabled`). Slack and email activate when
their module is present. You do not need `@Import` to wire a feature — adding the module is enough.

#### Auto-configuration rename (only if you excluded it)

If your application excluded the starter auto-configuration by name, rename it:

```
com.openelements.spring.base.SpringServicesAutoConfiguration
  ->
com.openelements.spring.base.SpringServicesCoreAutoConfiguration
```

Most applications never reference this class and have nothing to do here.

#### No API, schema, or configuration changes

No public Java types or signatures changed. The database schema (`oe_spring_services`, from 1.3.0) is
unchanged — no migration. Property names are unchanged.

### Steps

1. Find the consumer's build file that declares `com.open-elements:spring-services`.
2. Replace it per **Path A** (switch to `spring-services-all`) or **Path B** (BOM + `-core` + chosen
   feature modules). Bump the version to `1.4.0`.
3. If you kept a hand-written `spring.autoconfigure.exclude` for the starter, rename it to
   `SpringServicesCoreAutoConfiguration`.
4. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
5. Compile and run the consumer's test suite to verify the build is green.
6. Commit the coordinate change.

### Guard rails / Don't do this

- Do **not** keep depending on the bare `com.open-elements:spring-services` coordinate — it is now a
  parent pom and carries no classes.
- Do **not** re-add `slack-api-client`, the MCP SDK, or `spring-boot-starter-mail` by hand — pull the
  corresponding feature module instead, which brings the dependency transitively.
- Do **not** change the database or any `@Table(schema = ...)` — there is no schema change in 1.4.0.
- Do **not** mix explicit module versions with the BOM import; let the BOM manage them.
- Do **not** bundle unrelated upgrades into this coordinate change.
