# Implementation Steps: Application build and SBOM info

All work lands in a new package `com.openelements.spring.base.info` inside `spring-services-core`.
No new dependency is added. The feature is backend-only — there are no frontend scenarios.

---

## Step 1: Immutable record model

- [x] Create `base/info/SbomComponent.java` — record `(group?, name, version?, type?, purl?, licenses)`; compact constructor normalises `licenses` (`null` → empty, then `List.copyOf`).
- [x] Create `base/info/SbomSummary.java` — record `(bomFormat?, specVersion?, serialNumber?, application?, componentCount, licenses)`; compact constructor normalises `licenses`. No timestamp field.
- [x] Create `base/info/SbomDocument.java` — record `(summary, components)`; compact constructor normalises `components`.
- [x] Create `base/info/GitInfo.java` — record `(commitId, shortCommitId, branch?, tag?, dirty?, commitTime?)`. `dirty` is a `@Nullable Boolean`.
- [x] Create `base/info/ApplicationInfo.java` — record `(group?, artifact?, version?, name?, git?, sbom?)` plus a static `empty()` factory returning an all-null instance.
- [x] Create `base/info/package-info.java` — `@NullMarked` package doc describing the feature.

**Acceptance criteria:**
- [x] Module compiles.
- [x] No collection accessor returns `null`; all returned collections are unmodifiable.

**Related behaviors:** The model exposes no build time; The summary carries no SBOM timestamp; Returned collections are immutable (model half).

---

## Step 2: `CycloneDxReader` — package-private Jackson tree parser

- [x] Create `base/info/CycloneDxReader.java`. Given a Spring `Resource`, parse it as a Jackson tree and produce an `Optional<SbomDocument>`.
- [x] Reject a document whose `bomFormat` is present and not `CycloneDX` → empty + WARN.
- [x] Parse `bomFormat`, `specVersion`, `serialNumber`, `metadata.component` (→ application `SbomComponent`), and the flat `components[]` array.
- [x] Per component: `group`, `name`, `version`, `type`, `purl`, and licenses collapsed via `license.id` → `license.name` → `expression`.
- [x] `SbomSummary.licenses` = distinct, sorted union over all component licenses.
- [x] Malformed JSON / IO error → empty + WARN naming the resource. Never throw.

**Acceptance criteria:**
- [x] Module compiles.
- [x] Reader never throws on bad input.

**Related behaviors:** A valid SBOM is summarised; The summary carries no SBOM timestamp; An SBOM with no components is still a valid SBOM; An SBOM with no metadata section parses; Unknown fields from a future spec version are ignored; A JSON document that is not CycloneDX degrades to no SBOM; all six License scenarios; The summary aggregates distinct licenses in sorted order.

---

## Step 3: `ApplicationInfoProperties`

- [x] Create `base/info/ApplicationInfoProperties.java` — `@ConfigurationProperties("openelements.info")` with nested `sbom.enabled` (default `true`) and `sbom.location` (default empty).

**Acceptance criteria:**
- [x] Module compiles; binding works for `openelements.info.sbom.enabled` / `openelements.info.sbom.location`.

**Related behaviors:** SBOM reading can be switched off; An explicit location overrides autodetection.

---

## Step 4: `ApplicationInfoService`

- [x] Create `base/info/ApplicationInfoService.java` (`@Service`).
- [x] Constructor takes `ObjectProvider<BuildProperties>`, `ObjectProvider<GitProperties>`, `ResourceLoader`, `ApplicationInfoProperties`.
- [x] Resolve coordinates from `BuildProperties` (null when absent).
- [x] Resolve `GitInfo`: prefer `GitProperties`; fall back to `BuildProperties.get("commit")`; `null` when neither. `shortCommitId` = abbrev, else first 7 chars, else whole hash. Empty `tag` → `null`. `dirty` nullable.
- [x] Resolve SBOM: if `sbom.enabled` is false, skip entirely (never open a file). Else resolve location (explicit, else autodetect order: `bom.json`, `application.cdx.json`, `native-image/sbom.json`) via `ResourceLoader` and parse with `CycloneDxReader`.
- [x] Compute `ApplicationInfo` and `Optional<SbomDocument>` once in constructor, cache in final fields.
- [x] Public `getApplicationInfo()` (never null, carries only summary) and `findSbom()` (full list).

**Acceptance criteria:**
- [x] Module compiles.
- [x] `getApplicationInfo()` never returns null; resource read at most once.

**Related behaviors:** Coordinates read from build-info; Missing build-info yields empty coordinates; all Git-metadata scenarios; getApplicationInfo does not carry the component list; The full component list is available separately; No SBOM on the classpath yields no SBOM; Autodetection prefers bom.json; An explicit location overrides autodetection; SBOM reading can be switched off; The service never returns null; Repeated calls return the same cached result.

---

## Step 5: Auto-configuration

- [x] Create `base/info/ApplicationInfoAutoConfiguration.java` — `@AutoConfiguration`, `@AutoConfigureAfter(ProjectInfoAutoConfiguration.class)`, `@EnableConfigurationProperties(ApplicationInfoProperties.class)`, with an `@ConditionalOnMissingBean` `@Bean` for `ApplicationInfoService`. **Not** guarded by `@ConditionalOnClass(EntityManagerFactory.class)`; **not** imported by `FullSpringServiceConfig`.
- [x] Append `com.openelements.spring.base.info.ApplicationInfoAutoConfiguration` as a second line in core's `AutoConfiguration.imports`.

**Acceptance criteria:**
- [x] Module compiles; the bean is contributed by auto-config, not component scan.

**Related behaviors:** The service is available without JPA; An application can substitute its own service.

---

## Step 6: Unit tests — reader and model

- [x] `CycloneDxReaderTest` against hand-written fixtures under `src/test/resources/sbom/`: valid 1.6 doc with metadata + 3 components, empty `components`, no `metadata`, non-CycloneDX (`bomFormat=SPDX`), malformed/truncated JSON, `specVersion=1.7` with unknown keys, and each license form (id, name, expression, id+name precedence, no licenses).
- [x] Model tests for collection immutability and `ApplicationInfo.empty()`.

**Acceptance criteria:**
- [x] All tests pass; `./mvnw -pl spring-services-core test` green.

**Related behaviors:** A valid SBOM is summarised; An SBOM with no components is still a valid SBOM; An SBOM with no metadata section parses; A JSON document that is not CycloneDX degrades to no SBOM; Unknown fields from a future spec version are ignored; all License scenarios; The summary aggregates distinct licenses in sorted order; Returned collections are immutable.

---

## Step 7: Unit tests — service

- [x] `ApplicationInfoServiceTest` with hand-built `BuildProperties`/`GitProperties` covering: coordinates read; missing build-info → null coordinates, no exception; git from git.properties (all fields); git fallback to build.commit; git.properties wins on conflict; no git → null GitInfo; unknown dirty → null; dirty true; short hash < 7 not truncated; empty tag → null; summary vs full-list split; explicit location override; autodetect order; disabled → no file opened; no sbom → null + no WARN; caching (same instance, read once).

**Acceptance criteria:**
- [x] All tests pass.

**Related behaviors:** Coordinates read from build-info; Missing build-info yields empty coordinates; all Git-metadata scenarios; getApplicationInfo does not carry the component list; The full component list is available separately; An explicit location overrides autodetection; Autodetection prefers bom.json; SBOM reading can be switched off; No SBOM on the classpath yields no SBOM; The service never returns null; Repeated calls return the same cached result.

---

## Step 8: Context/integration tests

- [x] `ApplicationInfoAutoConfigurationTest` (`ApplicationContextRunner`): bean exists with `EntityManagerFactory` hidden via `FilteredClassLoader`, and `SpringServicesCoreAutoConfiguration` did not apply; a user-declared `ApplicationInfoService` bean replaces the library one; a malformed SBOM on the classpath does not fail context startup and logs a warning.

**Acceptance criteria:**
- [x] All context tests pass.

**Related behaviors:** The service is available without JPA; An application can substitute its own service; Malformed JSON degrades to no SBOM.

---

## Step 9: Real reactor SBOM fixture

- [x] Generate a real `cyclonedx-maven-plugin` SBOM and check it in under `spring-services-core/src/test/resources/sbom/reactor.cdx.json`.
- [x] Add a fixture test: parses, `componentCount > 0`, a component's `purl` identifies a Spring Boot artifact, every component has non-null `name`.

**Acceptance criteria:**
- [x] Fixture test passes.

**Related behaviors:** The real reactor SBOM parses [fixture].

---

## Step 10: Documentation

- [x] Update `spring-services-core` README / module docs with the new service, the `openelements.info.*` properties, and the security note that an application-defined SBOM endpoint must sit behind an admin guard (D7).
- [x] Update root `CLAUDE.md` Project Context (Features) if it tracks per-feature descriptions.

**Acceptance criteria:**
- [x] Docs mention the service, properties, and the admin-guard security note.

**Related behaviors:** — (documentation)

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Coordinates are read from build-info.properties | Backend | 4, 7 |
| Missing build-info yields empty coordinates, not an exception | Backend | 4, 7 |
| The model exposes no build time | Backend | 1, 7 |
| Git info is read from git.properties | Backend | 4, 7 |
| Git info falls back to build.commit when git.properties is absent | Backend | 4, 7 |
| git.properties wins when both sources disagree | Backend | 4, 7 |
| No git information at all yields a null GitInfo | Backend | 4, 7 |
| An unknown dirty state is null, not false | Backend | 4, 7 |
| A dirty build is reported as dirty | Backend | 4, 7 |
| A commit hash shorter than seven characters is not truncated | Backend | 4, 7 |
| An empty tag value is treated as absent | Backend | 4, 7 |
| A valid SBOM is summarised | Backend | 2, 6 |
| The summary carries no SBOM timestamp | Backend | 1, 2, 6 |
| The full component list is available separately | Backend | 4, 7 |
| getApplicationInfo does not carry the component list | Backend | 4, 7 |
| An explicit location overrides autodetection | Backend | 4, 7 |
| Autodetection prefers bom.json over application.cdx.json | Backend | 4, 7 |
| SBOM reading can be switched off | Backend | 3, 4, 7 |
| No SBOM on the classpath yields no SBOM | Backend | 4, 7 |
| Malformed JSON degrades to no SBOM [context] | Backend | 2, 8 |
| A JSON document that is not CycloneDX degrades to no SBOM | Backend | 2, 6 |
| An SBOM with no components is still a valid SBOM | Backend | 2, 6 |
| An SBOM with no metadata section parses | Backend | 2, 6 |
| Unknown fields from a future spec version are ignored | Backend | 2, 6 |
| The real reactor SBOM parses [fixture] | Backend | 9 |
| An SPDX license id is surfaced as the id | Backend | 2, 6 |
| A free-text license name is surfaced when no id is declared | Backend | 2, 6 |
| A license expression is surfaced as the expression | Backend | 2, 6 |
| An id takes precedence over a name on the same entry | Backend | 2, 6 |
| A component without licenses yields an empty list | Backend | 2, 6 |
| The summary aggregates distinct licenses in sorted order | Backend | 2, 6 |
| The service is available without JPA [context] | Backend | 5, 8 |
| An application can substitute its own service [context] | Backend | 5, 8 |
| The service never returns null | Backend | 4, 7 |
| Returned collections are immutable | Backend | 1, 6 |
| Repeated calls return the same cached result | Backend | 4, 7 |

All 36 scenarios are assigned. No frontend scenarios exist.
