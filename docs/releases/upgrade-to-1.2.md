# Upgrade prompt: `com.open-elements:spring-services` 1.1.2 → 1.2.0

`spring-services` 1.2.0 is a **release with no consumer-facing changes**. There are no public API
changes, no schema or migration changes, no configuration changes, and no dependency version
changes. Spring Boot stays at `3.5.14` and Testcontainers stays at `2.0.5`. The only changes in this
release are an internal type-safety cleanup inside two source files (no signature or behavior
change), a build refactor that moves the library's own Maven configuration into a shared parent POM,
and updates to the library's release/CI tooling and documentation layout — none of which alter the
public API, runtime behavior, or any compiled bytecode that consumers depend on.

This upgrade is therefore a **version bump and nothing else**. If you do only the dependency bump and
your build compiles and tests pass, you are done.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade from `1.1.2` to `1.2.0`. This is a no-op release for consumers — the only required
action is bumping the dependency version. Do not change any application code, schema, configuration,
or other dependencies.

### What changed in 1.2.0

#### Dependencies

Bump `com.open-elements:spring-services` to `1.2.0` in the consumer's build file (`pom.xml` or
`build.gradle`). No other version bumps. Spring Boot stays at `3.5.14` and Testcontainers stays at
`2.0.5` — do **not** change those coordinates as part of this upgrade.

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.1.2</version>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.2.0</version>
</dependency>
```

#### No public API changes

No public method signatures, types, annotations, records, enums, or message strings changed between
1.1.2 and 1.2.0. The only `src/main` changes are in
`com.openelements.spring.base.events.GenericDataEvent` and
`com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload`, and they are an
**internal type-safety cleanup only**: the inline unchecked casts (`(Class<T>) source.getClass()`,
`(T) super.getSource()`, `(Class<T>) data.getClass()`) were moved into local variables annotated
with `@SuppressWarnings("unchecked")` and given explanatory comments. The constructor signature,
the `getSource()` signature, and the static `WebhookDataEventPayload.of(...)` factory signatures are
all unchanged, and they produce exactly the same values at runtime. Existing call sites compile and
behave exactly as they did on 1.1.2 — nothing to migrate.

#### Internal-only: parent POM, release tooling, and docs layout (do not ship / nothing to do)

These changes are part of the library's own repository and build pipeline. They do not affect the
public API, the published bytecode, or the resolved dependency tree of consumers:

- **Parent POM.** The library's `pom.xml` now inherits from `com.open-elements:java-parent:1.0.0`
  instead of declaring its plugin management, distribution management, and Spring Boot / Testcontainers
  BOM imports inline. This is transparent to consumers: when Maven resolves
  `com.open-elements:spring-services:1.2.0` it also resolves `java-parent:1.0.0` from Maven Central
  automatically, and the **effective managed dependency versions are unchanged** (Spring Boot still
  `3.5.14`, Testcontainers still `2.0.5`). There is nothing for the consumer to add or configure.
- **Release / CI tooling.** `release.sh` and the GitHub Actions workflows were updated (snapshot
  repository configuration, artifact deployment, and a new `check-dependencies.sh` helper). These are
  repository tooling, are not part of the published artifact, and have no effect on consumer code,
  builds, or tests.
- **Documentation layout.** Upgrade guides moved under `docs/releases/` and specs under `docs/specs/`.
  This is internal repository housekeeping with no consumer impact.

#### No schema or configuration changes

There are no database schema or migration changes, and no new or changed configuration keys,
defaults, or validation messages. No data migration or backfill is required.

### Steps

1. Find the consumer's build file that declares `com.open-elements:spring-services`.
2. Bump the `spring-services` version from `1.1.2` to `1.2.0`. Change nothing else.
3. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
4. Compile and run the consumer's test suite to verify the build is green.
5. Commit the single-line version change.

### Guard rails

- Change only the `com.open-elements:spring-services` version. Touch no other coordinate.
- Do **not** bump Spring Boot, Testcontainers, or any other dependency in this change — they did not
  change in 1.2.0.
- Do **not** add `com.open-elements:java-parent` as a dependency or import in the consumer build —
  the parent POM is the library's own concern and is resolved transitively by Maven.
- Do **not** modify any application code, schema, migration, or configuration — there are no
  consumer-facing changes in this release.
- Do **not** change any call site of `GenericDataEvent` or `WebhookDataEventPayload.of(...)` in
  response to this release; the only change there was internal casting hygiene inside the library.

### Don't do this

- Do not bundle unrelated upgrades, refactors, or feature work into this version bump.
- Do not edit `spring-services` itself from the consumer side.
- Do not introduce new schema or migration files "to be safe" — none are required.
