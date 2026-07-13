# Implementation Steps: Multi-module restructuring

Ordered, atomic steps. The strategy keeps the build green after **every** step: first convert the
single artifact into a reactor whose only module is `spring-services-core` (a pure move, no feature
split), then extract one feature module at a time, then add the `-all` aggregator and `-bom`, then
wire release/CI and docs. Each feature extraction moves code + tests + dependencies and replaces its
slice of `FullSpringServiceConfig` with a per-module `@AutoConfiguration`.

Reference: `design.md` (module layout, dependency analysis, auto-configuration distribution).

---

## Step 1: Reactor parent + `spring-services-core` (pure move, no feature split)

- [ ] Create the reactor parent `pom.xml` at repo root: keep `groupId=com.open-elements`,
  `artifactId=spring-services`, `parent=com.open-elements:java-parent`, change `<packaging>` to `pom`,
  add `<modules><module>spring-services-core</module></modules>`.
- [ ] Move the current dependency/plugin management and third-party version properties
  (`slack-api-client.version`, `mcp-sdk.version`, …) into the reactor parent's
  `<dependencyManagement>`/`<pluginManagement>`/`<properties>`.
- [ ] Create `spring-services-core/pom.xml`: `parent=spring-services` (explicit version, not
  `${revision}`), `artifactId=spring-services-core`, packaging `jar`. Declare **all** current
  dependencies here for now (they get redistributed in later steps).
- [ ] `git mv src/ spring-services-core/src/` — move all main + test sources and resources unchanged
  (packages stay `com.openelements.spring.base.*`, including the test apps in `com.example.*`).
- [ ] Move the `.imports` file with the sources (still one `SpringServicesAutoConfiguration`).

**Acceptance criteria:**

- [ ] `./mvnw -o clean test` (reactor root) builds `spring-services-core` and all 400 tests pass.
- [ ] `./mvnw -o -Ppublication clean verify` is green (Javadoc/sources/SBOM per module).

**Related behaviors:** Reactor builds all modules; Parent coordinate is no longer a consumable jar.

---

## Step 2: Extract `spring-services-slack`

- [ ] Create `spring-services-slack` module (`parent=spring-services`, depends on
  `spring-services-core`); move `services/slack/**` main+test there; move the `slack-api-client`
  dependency from core to this module.
- [ ] Remove `SlackConfig` from `FullSpringServiceConfig`; add `SlackAutoConfiguration`
  (`@ConditionalOnClass(<slack marker>)`, retains `@ConditionalOnProperty`, `@Import(SlackConfig)`)
  with its own `META-INF/spring/....AutoConfiguration.imports`.

**Acceptance criteria:**

- [ ] Reactor builds; slack unit tests run in the slack module; core no longer references slack.
- [ ] `./mvnw -o dependency:tree -pl spring-services-core` shows no `slack-api-client`.

**Related behaviors:** Optional module self-activates; Core does not pull optional heavy dependencies.

---

## Step 3: Extract `spring-services-search`

- [ ] Create `spring-services-search` (depends on core); move `services/search/**`; no new
  third-party dep (RestClient via core). Move WireMock test dep usage as needed.
- [ ] Remove `SearchConfig` from `FullSpringServiceConfig`; add `SearchAutoConfiguration`
  (guard on a search marker class + existing enable property) + its `.imports`.

**Acceptance criteria:** Reactor builds; search tests run in-module.

**Related behaviors:** Optional module self-activates; property toggle still disables a present module.

---

## Step 4: Extract `spring-services-dbbackup`

- [ ] Create `spring-services-dbbackup` (depends on core); move `services/dbbackup/**`; no new dep.
- [ ] Remove `DbBackupConfig` from `FullSpringServiceConfig`; add `DbBackupAutoConfiguration` +
  `.imports`.

**Acceptance criteria:** Reactor builds; db-backup tests run in-module.

**Related behaviors:** Optional module self-activates; property toggle still disables a present module.

---

## Step 5: Extract `spring-services-email`

- [ ] Create `spring-services-email` (depends on core); move `services/email/**`; move
  `spring-boot-starter-mail` from core to this module. (`email` → `user` internal dep is satisfied by
  the core dependency.)
- [ ] Remove `EmailConfig` from `FullSpringServiceConfig`; add `EmailAutoConfiguration`
  (`@ConditionalOnClass(JavaMailSender or jakarta.mail marker)`) + `.imports`.

**Acceptance criteria:** Reactor builds; email tests run in-module; core no longer pulls
`spring-boot-starter-mail`.

**Related behaviors:** Core does not pull optional heavy dependencies; Optional module self-activates.

---

## Step 6: Extract `spring-services-mcp`

- [ ] Create `spring-services-mcp` (depends on core); move `mcp/**`; move
  `mcp-spring-webmvc` + `mcp-json-jackson2` from core to this module. (`mcp` → `security`, `apikey`
  satisfied by the core dependency.)
- [ ] Remove `McpServerConfig` from `FullSpringServiceConfig`; add `McpAutoConfiguration`
  (`@ConditionalOnClass(<mcp SDK marker>)`, retains `openelements.mcp.enabled`) + `.imports`.

**Acceptance criteria:** Reactor builds; MCP tests run in-module; core no longer pulls the MCP SDK.

**Related behaviors:** Optional module self-activates; Optional module stays inert when its class is
missing; Per-feature property toggle still disables a present module.

---

## Step 7: Finalise core auto-configuration

- [ ] Rename the core auto-config to `SpringServicesCoreAutoConfiguration` (keeps
  `AutoConfigurationPackages.register`, `@AutoConfigureBefore(...)`,
  `@ConditionalOnClass(EntityManagerFactory)`), `@Import`ing the now core-only
  `FullSpringServiceConfig`. Update core's `.imports`.
- [ ] Confirm `FullSpringServiceConfig` now references only core feature configs (security, tenant,
  apikey, audit, comment, settings, tag, webhook, translation, apitoken, system).

**Acceptance criteria:** Reactor builds; core context test (app + lib persistence) still passes;
backs-off test still passes.

**Related behaviors:** Single persistence unit resolves all entities; Absent optional module does not
break startup; Entity schema-guard test lives in core.

---

## Step 8: `spring-services-all` aggregator + aggregate tests

- [ ] Create `spring-services-all` (jar) depending on core + all five feature modules. **No**
  `@Configuration`/`@AutoConfiguration`, no `.imports`.
- [ ] Move the zero-config starter integration test (`com.example.app.*`) and the explicit-import
  test into `spring-services-all/src/test` (full classpath present there). Add assertions that a
  representative Slack and MCP bean are present.

**Acceptance criteria:** `spring-services-all` builds; aggregate test proves full-feature wiring;
`jar tf` / resource scan shows no config/`.imports` in the all artifact.

**Related behaviors:** Full classpath activates every feature; all-module ships no aggregate
configuration; Zero-config starter test runs against the full classpath.

---

## Step 9: `spring-services-bom`

- [ ] Create `spring-services-bom` (`packaging=pom`) with `<dependencyManagement>` entries for every
  spring-services module at `${project.version}`.

**Acceptance criteria:** A consumer importing the BOM resolves `core` + a feature module without
explicit versions (verified by a small resolution assertion or `dependency:tree` in a probe).

**Related behaviors:** BOM resolves all module versions from one import.

---

## Step 10: Module-isolation / dependency assertions

- [ ] Add a test (in `spring-services-all` or a dedicated `it` module) asserting the resolved runtime
  classpath of `core` alone contains none of `slack-api-client`, the MCP SDK, or
  `spring-boot-starter-mail`; and that `core` + one feature pulls only that feature's deps.
- [ ] Add an `ApplicationContextRunner` test proving an optional auto-config stays inert when its
  guard class is absent (`FilteredClassLoader`).

**Acceptance criteria:** Isolation assertions pass in the reactor build.

**Related behaviors:** Core does not pull optional heavy dependencies; À-la-carte consumer pulls only
chosen features; Optional module stays inert when its class is missing.

---

## Step 11: Release / CI wiring for multiple artifacts

- [ ] Update `release.sh` + `.github/workflows/release.yml` + `snapshot.yml` to stage/deploy all
  module jars (+ `-sources`/`-javadoc`/signatures) and the BOM instead of one artifact.
- [ ] Update `.github/workflows/build.yml` if needed so CI builds the whole reactor.
- [ ] Confirm lockstep version bump works via `mvn versions:set -DnewVersion=… -DprocessAllModules`.

**Acceptance criteria:** CI builds the reactor green; release config references every module + BOM.

**Related behaviors:** Lockstep version across all modules.

---

## Step 12: Upgrade doc + README + CLAUDE.md

- [ ] Author `docs/releases/upgrade-to-1.4.md` (minor cadence, per 013 precedent; coordinate break): `spring-services` →
  `spring-services-all` migration; à-la-carte option via `-core` + feature modules + BOM; ordering
  note (spec 013 schema migration first, then the coordinate swap).
- [ ] Update `README.md` install/quick-start for the new coordinates and the à-la-carte model.

**Acceptance criteria:** Docs describe the new coordinates; upgrade doc is a release gate.

**Related behaviors:** Old coordinate no longer compiles; Migration to all-module restores behavior;
Explicit-wiring consumer wires only core features.

---

## Behavior Coverage

| Scenario                                              | Covered in Step |
|-------------------------------------------------------|-----------------|
| Reactor builds all modules                            | 1–9             |
| Parent coordinate is no longer a consumable jar       | 1               |
| Lockstep version across all modules                   | 11              |
| Core does not pull optional heavy dependencies        | 2, 5, 6, 10     |
| À-la-carte consumer pulls only chosen features        | 9, 10           |
| BOM resolves all module versions from one import      | 9               |
| all-module brings the complete set                    | 8               |
| Full classpath activates every feature                | 8               |
| Absent optional module does not break startup         | 7, 10           |
| Optional module self-activates by classpath presence  | 2–6             |
| Optional module stays inert when its class is missing | 6, 10           |
| Per-feature property toggle still disables a module   | 3, 4, 6         |
| all-module ships no aggregate configuration           | 8               |
| Single persistence unit resolves all entities         | 7, 8            |
| Library-internal FKs and transactions still work      | 1, 7            |
| Entity schema-guard test lives in core                | 1, 7            |
| No optional module contributes an entity              | 2–6             |
| Zero-config starter test runs against full classpath  | 8               |
| Old coordinate no longer compiles                     | 12              |
| Migration to all-module restores behavior             | 8, 12           |
| Explicit-wiring consumer wires only core features     | 7, 12           |
