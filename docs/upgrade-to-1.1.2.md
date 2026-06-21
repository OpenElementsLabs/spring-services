# Upgrade prompt: `com.open-elements:spring-services` 1.1.1 → 1.1.2

`spring-services` 1.1.2 is a **patch release with no consumer-facing changes**. There are no public
API changes, no schema or migration changes, no configuration changes, and no dependency bumps.
Spring Boot stays at `3.5.14` and Testcontainers stays at `2.0.5`. The only changes in this release
are Javadoc clarifications inside one source file and an update to the library's own release script —
neither of which alters the public API, runtime behavior, or any compiled bytecode that consumers
depend on.

This upgrade is therefore a **version bump and nothing else**. If you do only the dependency bump
and your build compiles and tests pass, you are done.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade from `1.1.1` to `1.1.2`. This is a no-op patch for consumers — the only required
action is bumping the dependency version. Do not change any application code, schema, configuration,
or other dependencies.

### What changed in 1.1.2

#### Dependencies

Bump `com.open-elements:spring-services` to `1.1.2` in the consumer's build file (`pom.xml` or
`build.gradle`). No other version bumps. Spring Boot stays at `3.5.14` and Testcontainers stays at
`2.0.5` — do **not** change those coordinates as part of this upgrade.

```xml
<!-- before -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.1.1</version>
</dependency>

<!-- after -->
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services</artifactId>
    <version>1.1.2</version>
</dependency>
```

#### No public API changes

No public method signatures, types, annotations, records, enums, or message strings changed between
1.1.1 and 1.1.2. The only `src/main` change is in
`com.openelements.spring.base.security.SecurityConfig`, and it is **Javadoc-only**: the class- and
method-level documentation was reworded for clarity and its `{@link}` cross-references were corrected
to match the existing method signatures. No method, field, annotation, or runtime behavior changed.
Existing call sites compile and behave exactly as they did on 1.1.1 — nothing to migrate.

#### No schema or configuration changes

There are no database schema or migration changes, and no new or changed configuration keys,
defaults, or validation messages. No data migration or backfill is required.

#### Internal-only: release script update (does not ship)

The library's own `release.sh` was updated to use the publication profile for local builds and
testing. This script is part of the library's repository tooling, is not part of the published
artifact, and has no effect on consumer code, consumer builds, or consumer tests. There is nothing
to do here.

### Steps

1. Find the consumer's build file that declares `com.open-elements:spring-services`.
2. Bump the `spring-services` version from `1.1.1` to `1.1.2`. Change nothing else.
3. Resolve dependencies (`mvn -U dependency:resolve` or `./gradlew --refresh-dependencies`).
4. Compile and run the consumer's test suite to verify the build is green.
5. Commit the single-line version change.

### Guard rails

- Change only the `com.open-elements:spring-services` version. Touch no other coordinate.
- Do **not** bump Spring Boot, Testcontainers, or any other dependency in this change — they did not
  change in 1.1.2.
- Do **not** modify any application code, schema, migration, or configuration — there are no
  consumer-facing changes in this release.
- Do **not** edit your security configuration or any `SecurityConfig`-related call sites in response
  to this release; the only change there was Javadoc wording in the library itself.

### Don't do this

- Do not bundle unrelated upgrades, refactors, or feature work into this version bump.
- Do not edit `spring-services` itself from the consumer side.
- Do not introduce new schema or migration files "to be safe" — none are required.
