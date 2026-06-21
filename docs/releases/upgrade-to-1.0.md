# Upgrade prompt: `com.open-elements:spring-services` 0.17.x → 1.0.0

`spring-services` 1.0.0 is the first stable release. Relative to 0.17.0 it adds a **client for the
db-backup-service sidecar** as a new, self-contained `dbbackup` package, and ships **one bug fix**
in the settings store (lookups now resolve by the human-readable `key` instead of misusing it as the
entity primary key). There are **no Spring Boot or Testcontainers BOM bumps** and **no schema
migrations**. The new package is purely additive and inert unless configured; the settings fix is
behaviour-preserving for anyone using the public `SettingsDataService` API.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade to `1.0.0`. The mandatory part is a single dependency bump. The only thing that can
require a code change is direct use of `SettingsRepository` (most consumers use
`SettingsDataService` and need no change). Adopting the db-backup client is optional.

### What changed in 1.0.0

#### Dependencies

- No version bumps. Spring Boot stays at `3.5.14`, Testcontainers stays at `2.0.5`. Do **not** change
  those coordinates as part of this upgrade.
- The db-backup client is built on Spring's `RestClient` (already on the classpath). 1.0.0 adds
  **no new transitive runtime dependency**.

#### Bug fix: settings are looked up by business key, not by entity id

`SettingsEntity` inherits its primary key (a `UUID`) from `AbstractEntity`, while the
human-readable setting name lives in a separate, unique `key` column. Before 1.0.0,
`SettingsDataService` and `SettingsRepository` resolved settings with
`findById(key)` / `deleteById(key)` — passing the `String` key where the repository's id type was
declared, so lookups by key did not reliably match the `UUID` primary key. 1.0.0 fixes this:

- `SettingsRepository` id type changed from `JpaRepository<SettingsEntity, String>` to
  `JpaRepository<SettingsEntity, UUID>` (matching the entity's actual `UUID` primary key), and gains
  two derived-query methods:

  ```java
  Optional<SettingsEntity> findByKey(String key);
  void deleteByKey(String key);
  ```

- `SettingsDataService` now calls `findByKey(...)` / `deleteByKey(...)` internally instead of
  `findById(...)` / `deleteById(...)`. **Its public API is unchanged** —
  `get(String key)`, `set(String key, String value)`, and `delete(String key)` keep the same
  signatures and now behave correctly.

- **No schema migration.** The `settings` table is unchanged: it already had a `UUID` primary key
  (from `AbstractEntity`) and a unique `key` column. Only the query path changed.

Impact on consumers:

- **If you use `SettingsDataService`** (the supported path): no code change. The fix simply makes
  key-based lookups work as documented.
- **If you injected `SettingsRepository` directly** and called `findById(key)` / `deleteById(key)`
  with a `String`: that no longer compiles — the id type is now `UUID`. Switch those calls to
  `findByKey(key)` / `deleteByKey(key)`, or prefer `SettingsDataService`.

#### Additive: new `dbbackup` package (db-backup-service client)

A new package `com.openelements.spring.base.services.dbbackup` provides a thin Java client for the
[db-backup-service sidecar](https://github.com/OpenElementsLabs/db-backup-service), which produces
scheduled PostgreSQL dumps. The client lets a host application trigger backups, poll job status,
list artefacts, and stream downloads without re-implementing the HTTP wiring.

Core types:

- `DbBackupClient` — thin `RestClient`-based wrapper. Public methods: `isHealthy()`, `getInfo()`,
  `triggerBackup()`, `getJob(String jobId)`, `listBackups()`, `getLatestBackup()`,
  `downloadBackup(String id)`, `downloadLatestBackup()`.
- DTOs mirroring the sidecar's JSON: `BackupJob`, `BackupJobStatus` (enum), `BackupMetadata`,
  `BackupServiceInfo` (with nested `Retention` / `BackupInterval`), `BackupTrigger` (enum).
- `BackupTriggerResult(BackupJob job, boolean alreadyRunning)` — wraps the triggered job and an
  `alreadyRunning` flag that distinguishes the sidecar's `202` (new job) from `409` (a backup is
  already in flight).
- `BackupDownload` — `AutoCloseable` wrapper around the streamed gzip body; use it in
  try-with-resources.
- `DbBackupException` — runtime exception for client failures.
- `DbBackupProperties` — `@ConfigurationProperties("openelements.db-backup")` with `baseUrl`
  (default `http://localhost:8081`), `apiToken` (may be `null` if only the unauthenticated
  `/health` and `/info` probes are used; the client throws at the point of use when an authenticated
  call is made without a token), and `requestTimeout` (default 30s — downloads stream gzip blobs).

`DbBackupConfig` (a plain `@Configuration` that component-scans the package and binds
`DbBackupProperties`) is now imported by `FullSpringServiceConfig`. The feature is **inert until**
`openelements.db-backup.base-url` and `openelements.db-backup.api-token` are configured. The bearer
token must be supplied through environment variables or secret management — never committed to
source control.

#### Note: Personal Access Tokens (specs only)

1.0.0 includes design and behaviour specs for Personal Access Tokens under
`specs/010-personal-access-tokens/`, but **no PAT code ships in this release**. There is nothing to
adopt and no API to migrate — ignore PAT for this upgrade.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is
   listed.

2. **Bump `spring-services` to `1.0.0`** in that `pom.xml`. Do not edit Spring Boot or Testcontainers
   versions in the same change.

3. **Check for direct `SettingsRepository` usage.** Grep for it:

   ```bash
   grep -rn "SettingsRepository" --include="*.java" src
   ```

   If any call site uses `findById(...)` / `deleteById(...)` with a `String` argument, switch it to
   `findByKey(...)` / `deleteByKey(...)` (or route through `SettingsDataService`). If there are no
   direct uses — the common case — this step is a no-op.

4. **Verify the build:**

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; application context should come up cleanly
   ```

   If your service imports `FullSpringServiceConfig`, the context comes up cleanly with no
   db-backup sidecar configured — the client is inert until `openelements.db-backup.*` is set.

5. **Commit** the version bump (plus any `SettingsRepository` call-site fixes) on its own:

   ```
   chore(deps): upgrade spring-services to 1.0.0
   ```

#### Optional: adopt the db-backup client

Only do the following if you want the application to drive the db-backup-service sidecar.

6. **Run the db-backup-service sidecar** and configure the connection via `application.yml`. Supply
   the token via environment variable / secret, never in source:

   ```yaml
   openelements:
     db-backup:
       base-url: ${DB_BACKUP_URL:http://localhost:8081}
       api-token: ${DB_BACKUP_API_TOKEN}
       request-timeout: 30s
   ```

7. **Inject `DbBackupClient`** where you need it (e.g. an admin controller) and use it. Always wrap
   downloads in try-with-resources because `BackupDownload` is `AutoCloseable`:

   ```java
   BackupTriggerResult result = dbBackupClient.triggerBackup();
   if (result.alreadyRunning()) { /* a backup was already in flight (409) */ }

   try (BackupDownload download = dbBackupClient.downloadLatestBackup()) {
       // stream download.inputStream() to the response
   }
   ```

### Guard rails

- **Do not** bump Spring Boot, Testcontainers, or any other dependency in the same change. 1.0.0
  ships against the same BOMs as 0.17.0 — leave them alone.
- **Do not** write a schema migration for the settings fix. The `settings` table is unchanged; the
  fix is purely in the query path. Adding or altering columns will not help and may break the
  `unique` constraint on `key`.
- **Do not** commit the db-backup `api-token` to source control. Supply it through environment
  variables or secret management; the client reads it from `openelements.db-backup.api-token`.
- **Do not** call authenticated `DbBackupClient` methods (`triggerBackup`, `getJob`, `listBackups`,
  downloads) without configuring `api-token` — the client throws at the point of use. Only
  `isHealthy()` and `getInfo()` work against the unauthenticated probes.
- **Do not** leak `BackupDownload` instances. They wrap a live streamed gzip body — always close
  them (try-with-resources) or you leak the underlying connection.

### Don't do this

- Do not "shim" the old settings repository by declaring `JpaRepository<SettingsEntity, String>`
  again. The entity's primary key is a `UUID`; the `String` id type was the bug. Look settings up by
  the `key` column via `findByKey` / `deleteByKey` (or `SettingsDataService`).
- Do not treat the PAT specs as shippable behaviour. No PAT code exists in 1.0.0 — there is nothing
  to wire up or migrate.
- Do not add a Meilisearch or db-backup dependency "to complete the set". Both clients use the
  already-present `RestClient`; the only external requirement is the running sidecar, and only if
  you actually use the feature.
- Do not edit `spring-services` from the consumer side. If a behaviour you depended on is missing,
  open an issue against the library.
- Do not bundle the db-backup adoption with the version bump in the same PR. Ship the bump (and any
  `SettingsRepository` fix) first, then adopt the backup client in a separate, focused change.
