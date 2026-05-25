# Upgrade prompt: `com.open-elements:spring-services` 0.15.x → 0.16.0

`spring-services` 0.16.0 adds a mandatory `name` field to every audit-log entry (with a new
`@NameSupplier` annotation that lets DTOs supply that name), raises the image upload limit from 2 MB
to 20 MB, and adds WebP/HEIC/HEIF support together with a JPEG transcoder. There are **no Spring
Boot or Testcontainers BOM bumps** in this release; the upgrade is one schema migration plus a small
signature change on the audit-log API plus an opt-in image feature set.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`.
Goal: upgrade to `0.16.0`, migrate the `audit_log` table for the new `entity_name` column, switch
any direct call to `AuditLogDataService#createEntry` to the new five-arg form, and optionally adopt
`@NameSupplier` on DTOs that flow through the audit-log event listener.

### What changed in 0.16.0

#### Dependencies

- No version bumps. Spring Boot stays at `3.5.14`, Testcontainers stays at `2.0.5`. Do **not** change
  those coordinates as part of this upgrade.

#### Breaking: `AuditLogEntity` gains a `name` field

- New field `name: String` (`@Column(name = "entity_name", nullable = false)`).
- DB column `entity_name varchar NOT NULL` must be added to the `audit_log` table.
- Getters/setters: `getName()/setName(String)` on `AuditLogEntity`.

`AuditLogDto` (public API surface):

- New record component `name: String` between `entityId` and `action`.

  ```java
  // before (0.15.x)
  public record AuditLogDto(UUID id, String entityType, UUID entityId,
                            AuditAction action, UserDto user, Instant createdAt) { … }

  // after (0.16.0)
  public record AuditLogDto(UUID id, String entityType, UUID entityId,
                            String name,                                 // NEW
                            AuditAction action, UserDto user, Instant createdAt) { … }
  ```

  Any code constructing `AuditLogDto` by hand (test fixtures, mappers) needs the extra positional
  argument. `AuditLogDto.fromEntity` keeps working without changes.

`AuditLogDataService` (public API surface):

- `createEntry(String entityType, UUID entityId, AuditAction action, UserEntity user)` →
  `createEntry(String entityType, UUID entityId, String name, AuditAction action, UserEntity user)`.
- The new `name` parameter is `@NonNull` (`NullPointerException` on `null`). Use the literal
  `"UNKNOWN"` when you have no meaningful name — that is the same fallback the library uses for
  events with no `@NameSupplier`.

#### Additive: `@NameSupplier` for DTO-derived names

- New annotation `com.openelements.spring.base.data.NameSupplier` with `@Target(METHOD)` and
  `RUNTIME` retention.
- The `AuditLogEventListener` now scans the published event's DTO for a method annotated with
  `@NameSupplier` that takes no arguments and returns `String`. The first matching method's return
  value is written to `AuditLogEntity.name`.
- If the DTO has no such method, the method takes parameters, or returns a non-`String` type, the
  listener falls back to the literal string `"UNKNOWN"` and logs nothing — the audit entry is still
  written.
- Example:

  ```java
  public record BookDto(UUID id, String title, String author) implements WithId {

      @NameSupplier
      public String displayName() {
          return title + " — " + author;
      }
  }
  ```

  Adoption is **strictly optional**. Existing DTOs continue to work; their audit entries simply
  carry `name = "UNKNOWN"`. Adding `@NameSupplier` later is a non-breaking change.

#### Breaking-light: image upload limit raised to 20 MB

- `ImageData.MAX_IMAGE_SIZE` changed from `2 * 1024 * 1024` to `20 * 1024 * 1024`.
- The validation message changed from `"File too large (max 2MB)"` to `"File too large (max 20 MB)"`
  — string-equality assertions on the old message break.
- Consumers that use `ImageData.MAX_IMAGE_SIZE` as a JPA `@Column(length = …)` for image columns:
  the column length on the database side will now be sized for 20 MB. Postgres `bytea` ignores
  `length`, but other vendors (Oracle, MS SQL) may need an `ALTER COLUMN` to widen the BLOB column.
- Spring multipart limits in the consumer's `application.yml` must be raised in lockstep — Spring
  rejects the upload before the bean validation runs:

  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: 20MB
        max-request-size: 20MB
  ```

#### Additive: WebP / HEIC / HEIF image types

- `ImageType` enum gains `WEBP` (`image/webp`), `HEIC` (`image/heic`), `HEIF` (`image/heif`) in
  addition to the existing `PNG`, `JPEG`, `SVG`. `ImageType.fromContentType(...)` recognises the new
  MIME strings; previously they would throw `IllegalArgumentException`.
- `ImageData` gains two instance methods that route through the new transcoder:
    - `asJpeg()` — re-encode to JPEG keeping the original dimensions.
    - `asJpeg(int maxWidth, int maxHeight)` — re-encode to JPEG and downscale so width ≤ `maxWidth`
      and height ≤ `maxHeight` (preserves aspect ratio; never upscales).
      Both throw `ResponseStatusException(BAD_REQUEST)` for `ImageType.SVG` (no raster source) and
      for content types that are not in the `ImageType` enum.
- New helper class `com.openelements.spring.base.data.image.util.ImageUtilities` exposes the same
  operations as static methods (`toJpeg(...)`).
- New helper class `com.openelements.spring.base.data.image.util.HeicSupportCheck` with
  `static boolean verifyHeicSupport()` — checks whether an HEIC/HEIF `ImageIO` reader is registered
  and decodes the bundled probe image. Useful as a startup smoke test in environments where HEIC
  support is required.
- Re-encoding always strips alpha (flattened onto white), strips EXIF/ICC/text chunks, and applies
  EXIF orientation for WebP and HEIC sources. The output is plain JPEG at quality 0.9.

##### Runtime requirements for HEIC / WebP

The library does **not** add transitive dependencies for HEIC or WebP decoding. The transcoder talks
to `javax.imageio` and relies on whatever readers are registered on the consumer's classpath:

- **WebP**: typically supplied by TwelveMonkeys (`com.twelvemonkeys.imageio:imageio-webp`). JDK 21+
  does not ship a WebP reader out of the box.
- **HEIC / HEIF**: supplied by NightMonkeys `imageio-heif` plus native libraries `libheif1` and
  `libheif-plugin-libde265` on the runtime host (e.g., the container image). Without the native
  libraries the reader registers but `ImageIO.read(...)` returns `null`.

If neither HEIC nor WebP uploads are part of the consumer's product surface, **no new dependency is
required** — the rest of the library (PNG transcoding, the new size limit, the audit-log name
field) works on a vanilla Spring Boot 3.5.14 classpath.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is
   listed.

2. **Bump `spring-services` to `0.16.0`** in that `pom.xml`. Do not edit Spring Boot or Testcontainers
   versions in the same change.

3. **Migrate the `audit_log` table.** Add the new `entity_name` column and backfill existing rows.
   The column is `NOT NULL` on the entity, so set a default for the backfill before adding the
   constraint. Use the consumer's existing migration tool (Flyway, Liquibase, …) and apply the
   equivalent of:

   ```sql
   -- audit_log: add entity_name (varchar NOT NULL)
   ALTER TABLE audit_log ADD COLUMN entity_name varchar(255);
   UPDATE audit_log SET entity_name = 'UNKNOWN' WHERE entity_name IS NULL;
   ALTER TABLE audit_log ALTER COLUMN entity_name SET NOT NULL;
   ```

   `"UNKNOWN"` is the same sentinel the library uses at runtime for events whose DTO has no
   `@NameSupplier`, so historical rows remain consistent with new ones from DTOs that have not yet
   adopted the annotation. Do **not** rely on `spring.jpa.hibernate.ddl-auto=update` for this
   migration — Hibernate will add the column but cannot backfill, so the subsequent `NOT NULL`
   validation against a populated table will fail at startup.

4. **Update direct callers of `AuditLogDataService#createEntry`.** Grep for the four-argument form:

   ```bash
   grep -rn "auditLogDataService\.createEntry\|AuditLogDataService.*createEntry" --include="*.java" src
   ```

   For each match, add the `String name` argument in third position. For call sites that have no
   meaningful name available, pass `"UNKNOWN"` (matching the listener's fallback). Where a name is
   available (e.g., the title of the audited entity), pass that instead. Example migration:

   ```java
   // before
   auditLogDataService.createEntry("Book", id, AuditAction.INSERT, user);

   // after
   auditLogDataService.createEntry("Book", id, book.title(), AuditAction.INSERT, user);
   ```

   Most consumers never call `createEntry` directly — the listener wires it from `OnObjectCreate` /
   `OnObjectUpdate` / `OnObjectDelete` events. If grep returns no hits in production sources, this
   step is a no-op there and only the test layer needs an update.

5. **Update tests that asserted against the old `AuditLogDto` shape.** Grep for record-construction
   call sites and string-equality assertions on the old size-limit message:

   ```bash
   grep -rn "new AuditLogDto(" --include="*.java" src/test
   grep -rn "File too large (max 2MB)" --include="*.java" src/test
   ```

    - `new AuditLogDto(id, type, entityId, action, user, createdAt)` →
      `new AuditLogDto(id, type, entityId, name, action, user, createdAt)`.
    - `assertEquals("File too large (max 2MB)", …)` → `assertEquals("File too large (max 20 MB)", …)`
      (or — preferably — assert on the exception type only, not the message string).
    - Assertions like `assertEquals("UNKNOWN", dto.name())` are valid for entries written from DTOs
      that have no `@NameSupplier`.

6. **(Optional) Adopt `@NameSupplier` on DTOs whose audit entries carry a meaningful name.** Find
   DTOs that participate in the data-event lifecycle (`AbstractDbBackedDataService` subclasses) and
   add a no-arg `String`-returning method annotated with `@NameSupplier`. Keep it side-effect-free:
   the listener reflects on it on every event. Do **not** widen this onto DTOs that have nothing
   useful to name — `"UNKNOWN"` is a perfectly valid value and signals exactly that. Do not bundle
   this into the same commit as the migration; ship it separately.

7. **(Optional) Raise the consumer's Spring multipart limits** if the new 20 MB ceiling should
   actually be reachable from HTTP uploads. In `application.yml`:

   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 20MB
         max-request-size: 20MB
   ```

   Skipping this step keeps uploads bounded by the existing `max-file-size`; the `ImageData`
   constructor will still accept up to 20 MB for byte arrays produced inside the JVM.

8. **(Optional, only if HEIC / WebP uploads are a product requirement) Wire up the ImageIO readers.**
   The library does not pull these in transitively. Add to the consumer's `pom.xml`:

   ```xml
   <!-- WebP decoding (TwelveMonkeys) -->
   <dependency>
     <groupId>com.twelvemonkeys.imageio</groupId>
     <artifactId>imageio-webp</artifactId>
     <version>3.12.0</version>
   </dependency>
   <!-- HEIC / HEIF decoding (NightMonkeys) -->
   <dependency>
     <groupId>com.github.gotson.nightmonkeys</groupId>
     <artifactId>imageio-heif</artifactId>
     <version>3.0.1</version>
   </dependency>
   ```

   For HEIC, also install `libheif1` and `libheif-plugin-libde265` into the runtime image (e.g.,
   `apt-get install -y libheif1 libheif-plugin-libde265` in the Dockerfile). Verify with
   `HeicSupportCheck.verifyHeicSupport()` at application startup; the method logs a clear diagnostic
   when the reader is missing or the native libraries are absent.

9. **Verify the build.** All three must pass:

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; application context should come up cleanly
   ```

   A Hibernate validation error mentioning `entity_name` means step 3 did not run — fix the
   migration, do **not** revert the entity change. A test failure asserting on `"File too large
   (max 2MB)"` means step 5 missed a string-equality assertion.

10. **Commit** with a clear message, e.g.:

    ```
    chore(deps): upgrade spring-services to 0.16.0

    - Add audit_log.entity_name column (NOT NULL, backfill 'UNKNOWN').
    - Switch AuditLogDataService#createEntry call sites to the new 5-arg form.
    - (Optionally) adopt @NameSupplier on DTOs / raise multipart limits to 20 MB.
    ```

### Guard rails

- **Do not** add the `entity_name` column with `DEFAULT NULL` and leave the `NOT NULL` constraint
  off. The entity declares the column as `nullable = false`; Hibernate validation runs at every
  startup and will fail if the schema disagrees. Backfill `"UNKNOWN"` for existing rows before
  applying the constraint.
- **Do not** call `createEntry(...)` with `null` for the new `name` argument. The library throws
  `NullPointerException` immediately — pass `"UNKNOWN"` instead, matching the listener's own
  fallback so the audit history stays consistent across direct and event-driven paths.
- **Do not** widen `@NameSupplier` to methods that have side effects, perform I/O, or depend on
  `SecurityContextHolder` / request scope. The listener invokes them on every persistence event;
  a slow or context-bound supplier turns into a per-write latency cost and may throw in background
  threads.
- **Do not** assume HEIC / WebP "just works" because the enum has the constant. The decoder is
  supplied by the consumer's classpath. If the consumer does not need those formats, do not add
  the dependencies just to "complete the set" — they pull in native code (HEIC) and increase the
  artefact size noticeably.
- **Do not** bump Spring Boot, Testcontainers, or any other dependency in the same change. 0.16.0
  ships against the same BOMs as 0.15.0 — leave them alone.
- **Do not** raise the multipart limit above 20 MB without raising `ImageData.MAX_IMAGE_SIZE`
  accordingly (which lives in the library — file an issue against `spring-services` instead). A
  multipart limit larger than `MAX_IMAGE_SIZE` only produces nicer 4xx responses, not larger images.

### Don't do this

- Do not "shim" the old API by adding an `AuditLogDto` overload with the 0.15 signature. The
  record's canonical constructor changed; any helper would shadow the real constructor and silently
  drop the new `name` field on construction.
- Do not pass the entity's class name (`entityType`) as the `name` argument to `createEntry`. They
  are two distinct columns now — `entity_type` is the type, `entity_name` is the human-readable
  label. Conflating them defeats the point of the new field.
- Do not register `@NameSupplier` on more than one method per DTO with the intent of "picking the
  better one." The listener uses `findFirst()` over the reflection result; the JVM's iteration order
  for `getMethods()` is not specified, so the choice between duplicates is effectively undefined.
  Pick one method per DTO.
- Do not store the literal class name `"UNKNOWN"` as a meaningful value. It is the *absence*
  sentinel; the audit log uses it deliberately so reports can filter on it (`WHERE entity_name =
  'UNKNOWN'`) to find DTOs that still need a `@NameSupplier`.
- Do not edit `spring-services` from the consumer side. If a behaviour you depended on is missing,
  open an issue against the library.
- Do not bundle this upgrade with feature work in the same PR. Keep the dependency bump, the
  migration, and the optional `@NameSupplier` / image-feature adoption in separate, focused commits
  so review and rollback stay tractable.
