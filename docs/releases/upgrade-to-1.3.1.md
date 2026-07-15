# Upgrade prompt: `com.open-elements:spring-services` 1.3.0 → 1.3.1

`spring-services` 1.3.1 is a **patch release with no consumer-facing changes**. There are no public
API changes, no schema or migration changes, no configuration changes, and no dependency bumps.
Testcontainers stays at `2.0.5` and no other managed dependency version changed. The only substantive
change is a **POM metadata fix**: a `<url>` element was added to every published module POM so the
artifacts satisfy Maven Central's publishing rules. This element lives in the POM metadata only — it
does not add, remove, or change any type, method, class, or runtime behavior that consumers depend on.

This upgrade is therefore a **version bump and nothing else**. If you do only the dependency bump and
your build compiles and tests pass, you are done.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services` (or
one of its modules — `spring-services-core`, `spring-services-all`, `spring-services-slack`,
`spring-services-mcp`, `spring-services-email`, `spring-services-search`, `spring-services-dbbackup`,
`spring-services-bom`). Goal: upgrade from `1.3.0` to `1.3.1`. This is a no-op patch for consumers —
the only required action is bumping the version. Do not change any application code, schema,
configuration, or other dependencies.

### What changed in 1.3.1

#### Dependencies

Bump `com.open-elements:spring-services` (and/or the individual module coordinates you declare) from
`1.3.0` to `1.3.1` in the consumer's build file (`pom.xml` or `build.gradle`). No other version bumps.
Testcontainers stays at `2.0.5` and no other managed dependency version changed — do **not** change
those coordinates as part of this upgrade.

If you manage versions through the BOM, bump only the BOM version; the module versions follow from it:

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-bom</artifactId>
    <version>1.3.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-bom</artifactId>
    <version>1.3.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

If you pin a module version directly, bump that instead:

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-core</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-core</artifactId>
    <version>1.3.1</version>
</dependency>
```

#### No public API changes

No public method signatures, types, annotations, records, enums, or message strings changed between
1.3.0 and 1.3.1. There are **no `src/main` Java changes at all** in this release. Every call site that
compiled and behaved a certain way on 1.3.0 compiles and behaves identically on 1.3.1 — nothing to
migrate.

#### Metadata-only: `<url>` added to published POMs (does not affect consumers)

Every published module POM (`spring-services`, `spring-services-core`, the feature modules,
`spring-services-all`, and `spring-services-bom`) gained a
`<url>https://github.com/OpenElementsLabs/spring-services</url>` element. This was required to satisfy
Maven Central's publishing rules; without it the release could not be deployed. It is pure POM
metadata: it changes nothing on the classpath, adds no transitive dependency, and has no effect on
consumer code, compilation, dependency resolution, or runtime behavior. There is nothing to do here.

#### No schema or configuration changes

There are no database schema or migration changes, and no new or changed configuration keys, defaults,
or validation messages. No data migration or backfill is required. The `oe_spring_services` schema and
all seven library tables are unchanged from 1.3.0.

#### Internal-only: release script update (does not ship)

The library's own `release.sh` was updated to run PomChecker's `check-maven-central` goal across the
reactor before tagging, so POM-metadata problems (like the missing `<url>` that motivated this patch)
are caught locally instead of failing the release workflow after a tag is pushed. This script is part
of the library's repository tooling, is not part of the published artifact, and has no effect on
consumer code, consumer builds, or consumer tests. There is nothing to do here.

### Steps

1. Find the consumer's build file(s) that declare `com.open-elements:spring-services` or any
   `spring-services-*` module or the `spring-services-bom`.
2. Bump the relevant `spring-services` version(s) from `1.3.0` to `1.3.1`. Change nothing else.
3. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
4. Compile and run the consumer's test suite to verify the build is green.
5. Commit the version change.

### Guard rails

- Change only the `com.open-elements:spring-services*` version(s). Touch no other coordinate.
- Do **not** bump Testcontainers or any other dependency in this change — they did not change in 1.3.1.
- Do **not** modify any application code, schema, migration, or configuration — there are no
  consumer-facing changes in this release.
- Do **not** add a `<url>` (or any other metadata) to your own POM in response to this release; the
  `<url>` change happened inside the library's published POMs, not in consumer builds.

### Don't do this

- Do not bundle unrelated upgrades, refactors, or feature work into this version bump.
- Do not edit `spring-services` itself from the consumer side.
- Do not introduce new schema or migration files "to be safe" — none are required.
- Do not "re-wire" the starter (`@Import(FullSpringServiceConfig.class)`, `@EntityScan`,
  `@EnableJpaRepositories`) — auto-configuration is unchanged from 1.3.0.
