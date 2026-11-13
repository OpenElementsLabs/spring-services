# Implementation Steps: Upgrade to Spring Boot 4

## Step 0: Capture the "before" payloads and test inventory

- [x] Temporary capture tests (not committed) serialise webhook payloads, SCIM `User` / `ListResponse` / `Error`,
  problem details (401, 400 body/param/type mismatch, 404, 409 with extension, 500) and the library DTOs
  (users, API keys, webhooks, audit log, application info) with Boot's mapper on Boot 3.5
- [x] Output stored outside the repository (session scratchpad), together with the list of test methods per file
  (784 tests, 4 pre-existing `@Disabled`)

**Acceptance criteria:**
- [x] Payload files exist for every payload category named in design.md "JSON defaults"

**Related behaviors:** JSON differences are recorded (input), No test removed or weakened (baseline)

---

## Step 1: Build baseline — parent, version, Java 25

- [x] Root `pom.xml`: parent `java-parent` 1.3.0 → 2.0.0-SNAPSHOT; Central snapshot repository for the
  parent and its imports until java-parent 2.0.0 is released
- [x] Reactor version `1.5.0-SNAPSHOT` → `2.0.0-SNAPSHOT` in all module POMs, `-all` and `-bom`
- [x] `.sdkmanrc` → `java=25`; `java-version: 25` in `build.yml`, `snapshot.yml`, `release.yml`
- [x] Root `pom.xml`: `mcp-sdk.version` → 2.1.0, new `spring-ai.version` 2.0.2, `json-schema-validator.version`
  → 3.x (comment updated)

**Acceptance criteria:**
- [x] `./mvnw validate` succeeds on Java 25 against java-parent 2.0.0-SNAPSHOT
- [x] No POM defines `spring-boot.version`, `maven.compiler.*` or `enforcer.requiredJavaVersion`

**Related behaviors:** Boot version comes from the parent only, Reactor version, Build runs on Java 25

---

## Step 2: OpenRewrite `UpgradeSpringBoot_4_0` (one-off, not added to the build)

- [x] Run `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (incl. Jackson 3 recipe) once via the
  rewrite-maven-plugin command line
- [x] Review the diff like hand-written code; revert anything outside the spec's scope

**Acceptance criteria:**
- [x] No rewrite plugin configuration in any POM

**Related behaviors:** —

---

## Step 3: Core module on Boot 4 / Jackson 3 / Security 7 / Hibernate 7

- [x] Starters renamed (`spring-boot-starter-web` → `spring-boot-starter-webmvc`), Boot 4 test starters for
  slices / MockMvc / Jackson / security
- [x] Jackson imports → `tools.jackson.*`; `JsonProcessingException` → `JacksonException`; immutable mapper via
  builder/`rebuild()`
- [x] Moved auto-configuration classes (`HibernateJpaAutoConfiguration`, `JpaRepositoriesAutoConfiguration`,
  `ProjectInfoAutoConfiguration`, `WebMvcAutoConfiguration`, `ErrorMvcAutoConfiguration`, `SecurityProperties`)
- [x] `JsonPointers` on Jackson 3 `BeanDescription` / naming strategy
- [x] Security 7 and Hibernate 7 API changes, if any
- [x] Test YAML dependency → `tools.jackson.dataformat:jackson-dataformat-yaml`; networknt validator 3.x API
- [x] Tests adapted only where the platform forces it

**Acceptance criteria:**
- [x] `spring-services-core` builds and all tests pass (incl. Testcontainers)

**Related behaviors:** Core starts after the migration, Problem details still replace Boot's error handling,
Trace filter still runs before security, Configuration properties bind unchanged, All chains behave as before,
JWT roles still map to authorities, API keys stay read-only, Error dispatch still permitted, Entities and
timestamps unchanged, No schema change, JSON pointers follow the naming strategy under Jackson 3,
`@JsonProperty` still honoured, Serialization failures are unchecked

---

## Step 4: Feature modules (SCIM, search, DB backup, email, slack, storage, tenant, ALTCHA, all)

- [x] Same treatment as step 3 per module; `MeilisearchClient` and `ScimAuthenticationEntryPoint` on Jackson 3
- [x] `MailSenderAutoConfiguration` moved package

**Acceptance criteria:**
- [x] Every feature module builds and all tests pass

**Related behaviors:** Meilisearch client on Jackson 3, Tenant guard still enforced, Feature modules stay inert
by default

---

## Step 5: MCP module on MCP SDK 2.x + Spring AI WebMVC transport

- [x] Dependencies: `mcp-core`, `mcp-json-jackson3` (`${mcp-sdk.version}`), `org.springframework.ai:mcp-spring-webmvc`
  (`${spring-ai.version}`); remove `mcp-json-jackson2` and the SDK's `mcp-spring-webmvc`
- [x] Adapt `McpServerConfig`, `McpTools`, `McpToolSupport`, `McpToolLogic`, `McpConfiguration`
- [x] Core's `ban-optional-feature-dependencies` gains `org.springframework.ai:*`

**Acceptance criteria:**
- [x] MCP module builds; `McpToolsTest`, `McpSecurityChainWebTest`, `McpAutoConfigurationTest` pass unchanged in
  their expectations
- [x] A streamable-HTTP test sends `initialize` + `tools/list` to `/mcp` and checks tool names and input schemas

**Related behaviors:** MCP server answers over streamable HTTP, Tool call returns the same result, MCP still
requires an API key, MCP stays off by default, BOM manages the new MCP and Spring AI coordinates, Core still bans
optional feature libraries

---

## Step 6: Enforcer rule — no Jackson 2 at runtime

- [x] `bannedDependencies` rule in the root `pom.xml` `<build><plugins>` (compile + runtime scope)
- [x] Verified once by temporarily adding `jackson-databind` (direct and transitive) to a module, and a
  test-scoped Jackson 2 dependency (not committed)

**Acceptance criteria:**
- [x] Full build green with the rule active
- [x] Temporary violation fails with the spec 025 message

**Related behaviors:** No Jackson 2 in any runtime tree, Enforcer fails on a Jackson 2 dependency, Transitive
Jackson 2 is caught, Jackson annotations stay allowed, Test-scoped Jackson 2 is not banned, No Jackson 2 imports in
main code

---

## Step 7: Behaviour gaps

- [x] Test: library beans (`ProblemResponseWriter`, `ScimAuthenticationEntryPoint`, `McpToolSupport`,
  `MeilisearchClient`) use Boot's customised `JsonMapper`
- [x] Any scenario from behaviors.md not yet covered by an existing test gets one

**Acceptance criteria:**
- [x] Every scenario in the coverage table below maps to a passing test or a build check

**Related behaviors:** Library beans receive Boot's mapper (+ any gaps found)

---

## Step 8: After-capture, upgrade guide, README

- [x] Re-run the capture on Boot 4, diff against step 0, record differences
- [x] `docs/releases/upgrade-to-2.0.md` (Java 25, Boot 4.1, java-parent 2.0.0, starter renames, Jackson 3, four
  signatures, `JacksonException`, MCP SDK / Spring AI, JSON differences with webhook section, springdoc Jackson 2
  note, changed test expectations)
- [x] README: Boot version and supported range per major; BOM section
- [x] Compare test inventory with step 0; remove the capture tests

**Acceptance criteria:**
- [x] Full `./mvnw verify` green
- [x] No test method removed or newly `@Disabled`

**Related behaviors:** Upgrade guide exists and is complete, JSON differences are recorded, Supported Boot range
documented, No test removed or weakened, Full build is green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|---|---|---|
| Full build is green | Build | 8 |
| Boot version comes from the parent only | Build | 1 |
| Reactor version | Build | 1 |
| Build runs on Java 25 | Build | 1 |
| BOM manages the new MCP and Spring AI coordinates | Build | 5 |
| No Jackson 2 in any runtime tree | Build | 6 |
| Enforcer fails on a Jackson 2 dependency | Build | 6 |
| Transitive Jackson 2 is caught | Build | 6 |
| Jackson annotations stay allowed | Build | 6 |
| Test-scoped Jackson 2 is not banned | Build | 6 |
| No Jackson 2 imports in main code | Build | 6 |
| Core still bans optional feature libraries | Build | 5 |
| Meilisearch client on Jackson 3 | Backend | 4 |
| Library beans receive Boot's mapper | Backend | 7 |
| JSON pointers follow the naming strategy under Jackson 3 | Backend | 3 |
| `@JsonProperty` still honoured | Backend | 3 |
| Serialization failures are unchecked | Backend | 3 |
| Core starts after the migration | Backend | 3 |
| Problem details still replace Boot's error handling | Backend | 3 |
| Trace filter still runs before security | Backend | 3 |
| Feature modules stay inert by default | Backend | 4 |
| Configuration properties bind unchanged | Backend | 3 |
| All chains behave as before | Backend | 3 |
| JWT roles still map to authorities | Backend | 3 |
| API keys stay read-only | Backend | 3 |
| Error dispatch still permitted | Backend | 3 |
| Entities and timestamps unchanged | Backend | 3 |
| Tenant guard still enforced | Backend | 4 |
| No schema change | Build | 3 |
| MCP server answers over streamable HTTP | Backend | 5 |
| Tool call returns the same result | Backend | 5 |
| MCP still requires an API key | Backend | 5 |
| MCP stays off by default | Backend | 5 |
| No test removed or weakened | Build | 8 |
| Upgrade guide exists and is complete | Docs | 8 |
| JSON differences are recorded | Docs | 8 |
| Supported Boot range documented | Docs | 8 |
