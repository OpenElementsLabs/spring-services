# Upgrade prompt: `com.open-elements:spring-services` 0.16.x → 0.17.0

`spring-services` 0.17.0 adds a **Meilisearch-backed full-text search integration** as a new,
self-contained `search` package. There are **no Spring Boot or Testcontainers BOM bumps**, **no
schema migrations**, and **no breaking API changes** in this release. The feature is purely
additive and completely inert unless the consuming application configures a Meilisearch sidecar and
supplies the per-index beans. If you do not want search, upgrading is a one-line version bump.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade to `0.17.0`. The mandatory part is a single dependency bump — everything else is
optional and only relevant if you want to adopt the new Meilisearch search feature.

### What changed in 0.17.0

#### Dependencies

- No version bumps. Spring Boot stays at `3.5.14`, Testcontainers stays at `2.0.5`. Do **not** change
  those coordinates as part of this upgrade.
- The search client is built on Spring's `RestClient` (already on the classpath via Spring Boot
  web). 0.17.0 adds **no new transitive runtime dependency** for search — it talks to the
  Meilisearch REST API directly.

#### Additive: new `search` package (Meilisearch integration)

A new package `com.openelements.spring.base.services.search` provides building blocks for indexing
application data into a [Meilisearch](https://www.meilisearch.com/) sidecar and querying it. The
library is deliberately app-agnostic: it never sees domain types and takes no stance on which
indexes exist. The application supplies the documents and index definitions; the library drives the
Meilisearch REST API.

Core types (all in the `search` package):

- `MeilisearchClient` — thin HTTP wrapper around the Meilisearch REST API. Public methods include
  `isHealthy()`, `createScopedKey(List<String> indexPatterns, List<String> actions)`,
  `addDocuments(String indexUid, List<Map<String,Object>> docs)`,
  `deleteDocument(String indexUid, String id)`,
  `updateSettings(String indexUid, Map<String,Object> settings)`,
  `ensureIndex(String indexUid, String primaryKey)`,
  `waitForTask(long taskUid, Duration timeout)`, `waitForLatestTask(Duration timeout)`, and
  `multiSearch(Map<String,Object> queries)`.
- `SearchIndexBootstrapStep` — **SPI the application implements (one bean per index)**. It exposes
  `String indexUid()` and `Stream<Map<String,Object>> documents()`. The library batches the stream
  and pushes it during the startup reindex; it closes the stream via try-with-resources. The
  library never sees domain types — steps deliver already-mapped `Map<String,Object>` documents.
  Execution order is the Spring bean `@Order`.
- `IndexSettings` — declarative per-index settings bean
  (`record IndexSettings(String indexUid, String primaryKey, List<String> searchableAttributes,
  List<String> filterableAttributes, List<String> sortableAttributes)`). The application registers
  one bean per index; the library applies them idempotently at startup.
- `ScopedKeySpec` — `record ScopedKeySpec(List<String> indexes, List<String> actions)`; optional
  bean describing the scoped runtime key minted at startup.
- `Highlighter` — turns Meilisearch's `_formatted` output into HTML-safe highlighted fragments
  (`static String safeHighlight(String raw)`).
- `SearchReadinessState` — shared flag that stays "not ready" while the initial reindex is in
  flight (`isBootstrapping()`, `markBootstrappingStarted()`, `markBootstrappingFinished()`).
- `MeilisearchException` — runtime exception for client failures; `TaskOutcome` — enum for task
  results.
- `MeilisearchProperties` — `@ConfigurationProperties("openelements.meilisearch")` carrying
  `host` (default `http://localhost:7700`), `masterKey`, `indexPrefix` (default empty), and
  `requestTimeout` (default 5s), plus `resolveIndex(String suffix)`.

Startup sequence (three `ApplicationRunner`s, `@Order`):
`MeilisearchScopedKeyInitializer` (10) exchanges the master key for a scoped runtime key,
`MeilisearchIndexSettingsInitializer` (20) applies per-index settings, and
`MeilisearchBootstrapRunner` (30) performs the full reindex. Each step is resilient: a failure
leaves a partial-but-usable index, and an **unreachable sidecar is skipped with a warning** —
readiness flips to ready without indexing.

Configuration binds from the `openelements.meilisearch` prefix. The master key must be supplied
through environment variables or secret management — never committed to source control.

#### Wiring: `SearchConfig` joins `FullSpringServiceConfig`

`com.openelements.spring.base.services.search.SearchConfig` (a plain `@Configuration` that
component-scans the package and binds `MeilisearchProperties`) is now imported by
`FullSpringServiceConfig`. Consumers that import `FullSpringServiceConfig` therefore pick up the
search beans automatically — **but they stay inert** until a Meilisearch sidecar is reachable and
the application registers `IndexSettings` / `SearchIndexBootstrapStep` beans. No action is required
to keep the previous behaviour; doing nothing leaves search switched off.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is
   listed.

2. **Bump `spring-services` to `0.17.0`** in that `pom.xml`. Do not edit Spring Boot or
   Testcontainers versions in the same change. **This is the only mandatory step.**

3. **Verify the build** — nothing else is required if you are not adopting search:

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; application context should come up cleanly
   ```

   If your service imports `FullSpringServiceConfig`, the context still comes up cleanly with no
   Meilisearch sidecar configured — the startup runners log a warning and flip readiness to ready
   without indexing.

4. **Commit** the version bump on its own:

   ```
   chore(deps): upgrade spring-services to 0.17.0
   ```

#### Optional: adopt Meilisearch search

Only do the following if full-text search backed by Meilisearch is a product requirement.

5. **Run a Meilisearch sidecar** alongside the application (e.g. a `getmeili/meilisearch` container)
   and provide its connection settings via `application.yml`. Supply the master key via environment
   variable / secret, never in source:

   ```yaml
   openelements:
     meilisearch:
       host: ${MEILISEARCH_HOST:http://localhost:7700}
       master-key: ${MEILISEARCH_MASTER_KEY}
       index-prefix: ""            # optional: namespace indexes if one instance hosts several datasets
       request-timeout: 5s
   ```

6. **Register one `IndexSettings` bean per index** describing the primary key and searchable /
   filterable / sortable attributes. Use `MeilisearchProperties#resolveIndex(...)` to apply the
   configured prefix to the index UID.

7. **Register one `SearchIndexBootstrapStep` bean per index** that streams already-mapped
   `Map<String,Object>` documents from your data layer. Keep `documents()` lazy — the library
   batches and closes the stream itself.

8. **(Optional) Register a `ScopedKeySpec` bean** if you want the startup runner to mint a scoped
   runtime key (instead of exposing the master key to query paths).

9. **(Optional) For asynchronous bootstrap**, add `@EnableAsync` and a `searchIndexExecutor` bean so
   the reindex does not block startup; gate request handling on `SearchReadinessState` while the
   reindex is in flight.

10. **Use `MeilisearchClient#multiSearch(...)`** for queries and `Highlighter#safeHighlight(...)` to
    render Meilisearch's `_formatted` snippets as HTML-safe highlighted fragments.

### Guard rails

- **Do not** bump Spring Boot, Testcontainers, or any other dependency in the same change. 0.17.0
  ships against the same BOMs as 0.16.0 — leave them alone.
- **Do not** commit the Meilisearch master key (or any scoped key) to source control. Supply it
  through environment variables or secret management; the library reads it from
  `openelements.meilisearch.master-key`.
- **Do not** assume search "just works" after the version bump. Without a configured, reachable
  sidecar and at least one `IndexSettings` + `SearchIndexBootstrapStep` bean, the feature is inert
  by design — the startup runners log a warning and continue.
- **Do not** register more than one `@Order` collision expecting a stable tie-break — step
  execution order is the Spring bean `@Order`, so give each index step a distinct order if order
  matters.

### Don't do this

- Do not pass domain objects to the search SPI. `SearchIndexBootstrapStep#documents()` returns
  `Stream<Map<String,Object>>` on purpose — the library never sees your domain types. Map to plain
  `Map<String,Object>` documents in your step implementation.
- Do not eagerly materialise the document stream (e.g. `.toList()` inside `documents()`). The
  library batches and closes the stream; returning a lazy stream keeps memory bounded for large
  reindexes.
- Do not edit `spring-services` from the consumer side to "turn search on". If a behaviour you need
  is missing, open an issue against the library.
- Do not bundle the search adoption with the version bump in the same PR. Ship the dependency bump
  first (it is behaviour-preserving), then adopt search in a separate, focused change.
