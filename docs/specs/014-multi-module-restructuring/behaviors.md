# Behaviors: Multi-module restructuring

These scenarios are largely **build- and wiring-level** (Maven reactor, classpath composition, Spring
auto-configuration) rather than HTTP request/response behaviors. They are written so each can be
turned into a reactor build assertion, a Maven dependency/classpath check, or a Spring integration
test (`ApplicationContextRunner` / `@SpringBootTest`).

## Reactor build

### Reactor builds all modules

- **Given** the multi-module reactor (`spring-services` parent + `core`, `slack`, `mcp`, `email`,
  `search`, `dbbackup`, `all`, `bom`)
- **When** `mvn verify` runs at the reactor root
- **Then** every module builds and its tests pass, and the build produces a jar for each non-pom
  module plus pom artifacts for the parent and the BOM

### Parent coordinate is no longer a consumable jar

- **Given** the repurposed `com.open-elements:spring-services` coordinate
- **When** its packaging is inspected
- **Then** it is `pom` (reactor parent) and produces no class-bearing jar

### Lockstep version across all modules

- **Given** the reactor after a global version change via `versions:set`
- **When** the version of each module and the BOM is read
- **Then** all modules and the BOM carry the identical version, and each module references the parent
  version explicitly

## Dependency isolation

### Core does not pull optional heavy dependencies

- **Given** `spring-services-core` only
- **When** its resolved transitive runtime classpath is computed
- **Then** it contains none of `slack-api-client`, the MCP SDK artifacts, or `spring-boot-starter-mail`

### À-la-carte consumer pulls only chosen features

- **Given** a consumer depending on `spring-services-core` + `spring-services-slack` only
- **When** the consumer's transitive classpath is computed
- **Then** `slack-api-client` is present, but the MCP SDK, mail, and the search/dbbackup modules are
  absent

### BOM resolves all module versions from one import

- **Given** a consumer that imports `spring-services-bom` (scope `import`) and declares
  `spring-services-core` and `spring-services-mcp` **without** versions
- **When** dependency resolution runs
- **Then** both modules resolve at the BOM's lockstep version

### all-module brings the complete set

- **Given** a consumer depending only on `spring-services-all`
- **When** its transitive classpath is computed
- **Then** every spring-services module and every feature dependency (slack, mcp, mail, …) is present

## Auto-configuration activation

### Full classpath activates every feature

- **Given** an application context with `spring-services-all` on the classpath and all feature
  properties enabled
- **When** the context starts
- **Then** the core beans and every optional feature's beans (Slack, MCP, email, search, db-backup)
  are present — equivalent to today's `@Import(FullSpringServiceConfig)` behavior

### Absent optional module does not break startup

- **Given** an application with `spring-services-core` only (no optional modules)
- **When** the context starts
- **Then** it starts successfully, exposes the core beans, and no `NoClassDefFoundError` or missing-bean
  failure occurs for any absent optional feature

### Optional module self-activates by classpath presence

- **Given** an application with `spring-services-core` + `spring-services-mcp` on the classpath and
  `openelements.mcp.enabled=true`
- **When** the context starts
- **Then** the MCP auto-configuration activates and the MCP server beans are present, without the
  application declaring any `@Import`

### Optional module stays inert when its class is missing

- **Given** the `spring-services-mcp` auto-configuration class on the classpath but the MCP SDK type
  it guards on absent
- **When** the context starts (simulated via `ApplicationContextRunner` without the SDK)
- **Then** the `@ConditionalOnClass` guard prevents activation and no MCP bean is created

### Per-feature property toggle still disables a present module

- **Given** an application with `spring-services-mcp` present but `openelements.mcp.enabled=false`
- **When** the context starts
- **Then** the MCP endpoint and beans are not registered (existing `@ConditionalOnProperty` behavior is
  preserved)

### all-module ships no aggregate configuration

- **Given** `spring-services-all`
- **When** its artifact contents are inspected
- **Then** it contains no `@Configuration`/`@AutoConfiguration` class and no
  `AutoConfiguration.imports` of its own — it is a pure dependency bundle

## Persistence unit (carried over from spec 013)

### Single persistence unit resolves all entities

- **Given** an application with `spring-services-core` (entities in `oe_spring_services`) and its own
  `@Entity` in a separate package, with no `@Import`/`@EntityScan`/`@EnableJpaRepositories`
- **When** the context starts
- **Then** both the application's and the library's entities and Spring Data repositories resolve under
  one `EntityManagerFactory` / one `TransactionManager`

### Library-internal foreign keys and transactions still work

- **Given** the core module against a Testcontainers PostgreSQL
- **When** an operation writes across library-internal associations (e.g. `audit_log.user_id → users.id`)
  within one transaction
- **Then** the FK holds and the transaction commits atomically

### Entity schema-guard test lives in core and covers all entities

- **Given** the schema-convention guard test in `spring-services-core`
- **When** it scans the classpath for `@Entity` under `com.openelements.spring.base`
- **Then** it finds all seven entities and asserts each carries `@Table(schema = DbSchema.NAME)`; a new
  entity without the schema fails the build

### No optional module contributes an entity

- **Given** any optional feature module on the classpath
- **When** the classpath is scanned for `@Entity` types contributed by that module
- **Then** none are found (all entities live in core)

## Aggregate / starter integration (carried over from spec 013, relocated)

### Zero-config starter test runs against the full classpath

- **Given** the aggregate test in `spring-services-all` with a test app declaring its own `@Entity` +
  repository in a separate package, no `@Import`/`@EntityScan`/`@EnableJpaRepositories`
- **When** the context starts with the full reactor on the classpath
- **Then** the app's own and the library's entities/repositories resolve, and representative
  optional-feature beans (e.g. a Slack and an MCP bean) are present

## Consumer migration (breaking change)

### Old coordinate no longer compiles

- **Given** an existing consumer depending on `com.open-elements:spring-services` (the old jar)
- **When** it builds against the new release
- **Then** resolution yields the reactor parent pom (no classes) and the consumer's compilation fails —
  signalling the required migration

### Migration to all-module restores behavior

- **Given** that same consumer switched to `com.open-elements:spring-services-all`
- **When** it builds and starts
- **Then** all previously available features are present and behavior matches the pre-split release

### Explicit-wiring consumer wires only core features

- **Given** a consumer on `spring-services-core` using `@Import(FullSpringServiceConfig.class)`
- **When** the context starts
- **Then** only the core feature beans are wired; optional features require adding their module or
  importing their feature config explicitly
