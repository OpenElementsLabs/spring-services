# Upgrade prompt: `com.open-elements:spring-services` 0.13.x → 0.14.0

`spring-services` 0.14.0 turns two free-form `String` user references into real `@ManyToOne UserEntity` associations and bumps Spring Boot plus the Testcontainers BOM. The audit log and the comment service now point at the dedicated `users` table; unauthenticated actors are recorded against a bootstrapped *System User* row instead of the literal string `"System"`.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo. Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`. Goal: upgrade to `0.14.0`, migrate the database schema for the renamed columns, and adjust any code that reads audit log entries or comments by the old `String` user reference.

### What changed in 0.14.0

#### Dependencies

- Spring Boot bumped from `3.5.13` to `3.5.14`.
- Testcontainers pinned to `2.0.5` via an explicit BOM import that must be declared **before** `spring-boot-dependencies`. Spring Boot 3.5.14's BOM still pins Testcontainers `1.21.4`, which fails against Docker Engine 29+ on macOS. The override is intentional; remove it once Spring Boot picks up Testcontainers 2.x natively.
- In the Testcontainers 2.x line all module artefacts were renamed to a `testcontainers-*` prefix. Consumers that pull Testcontainers modules transitively from `spring-services` are not affected; consumers that declare them directly are.

#### Breaking: `AuditLogEntity` user field

- Field `userName: String` → `user: UserEntity` (`@ManyToOne(optional = false, fetch = LAZY)`).
- DB column `user_name varchar(255) NOT NULL` → `user_id uuid NOT NULL` with a foreign key to `users(id)`.
- Getters/setters: `getUserName()/setUserName(String)` → `getUser()/setUser(UserEntity)`.

`AuditLogDto` (public API surface):

- Field `user: String` → `user: UserDto`. `AuditLogDto.fromEntity` now requires an open persistence context because the association is lazy.

`AuditLogDataService` (public API surface):

- `findByUser(String, Pageable)` → `findByUser(UUID, Pageable)`.
- `findByEntityTypeAndUser(String, String, Pageable)` → `findByEntityTypeAndUser(String, UUID, Pageable)`.
- `createEntry(String, UUID, AuditAction, String)` → `createEntry(String, UUID, AuditAction, UserEntity)`.
- The constructor gained a `UserRepository` parameter — consumers that wire `AuditLogDataService` by hand (instead of via component scan) need to pass it.

`AuditLogRepository` (public API surface):

- `findByUserNameOrderByCreatedAtDesc(String, Pageable)` → `findByUserIdOrderByCreatedAtDesc(UUID, Pageable)`.
- `findByEntityTypeAndUserNameOrderByCreatedAtDesc(String, String, Pageable)` → `findByEntityTypeAndUserIdOrderByCreatedAtDesc(String, UUID, Pageable)`.

#### Breaking: `CommentEntity` author field

- Field `authorId: String` → `author: UserEntity` (`@ManyToOne(optional = false, fetch = LAZY)`).
- DB column `author varchar(255) NOT NULL` → `author_id uuid NOT NULL` with a foreign key `fk_comments_author` to `users(id)`.
- Getters/setters: `getAuthorId()/setAuthorId(String)` → `getAuthor()/setAuthor(UserEntity)`.
- `CommentDto` is unchanged on the surface — it still exposes `author: UserDto`. The DTO mapping no longer makes a second `UserService.findById(...)` round-trip; the association is traversed directly.

#### New: System User

- New class `com.openelements.spring.base.security.user.SystemUser` with the constants `ID = new UUID(0L, 0L)` (RFC 9562 nil UUID), `SUB = "system"`, `NAME = "System"`.
- New `SystemUserInitializer` (`ApplicationRunner`) inserts that row on startup if missing. It uses `JdbcTemplate` because Hibernate would reject the pre-assigned id. The check is idempotent; concurrent inserts from clustered instances are swallowed as `DataIntegrityViolationException`.
- The audit log event listener now resolves unauthenticated, blank, or non-JWT principals to `SystemUser.ID` and writes that row as the actor — replacing the old `"System"` string fallback.

#### Additive

- `UserService.getCurrentUserEntity()` is now `public` (was `private synchronized`). Callers that need to populate a `@ManyToOne UserEntity` association on their own entities can use it instead of going through `UserDto`.
- `UserEntity` is annotated with `@BatchSize(50)` to avoid N+1 fetches when paging audit log or comment lists.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is listed.

2. **Bump `spring-services` to `0.14.0`** in that `pom.xml`.

3. **Re-check the Testcontainers manifest** if the consumer declares Testcontainers modules directly (i.e., not just relying on `spring-services` transitives):

   - The `org.testcontainers:testcontainers-bom` import must appear **before** `spring-boot-dependencies` in `dependencyManagement` so the 2.0.5 versions win. Move it if necessary.
   - Rename module coordinates in the consumer's `<dependencies>`:
     - `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`
     - `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`
     - The plain `org.testcontainers:testcontainers` artefact keeps its name.
   - Remove explicit `<version>` declarations on those modules — the BOM owns the version now.

4. **Migrate the database schema.** The two column renames are not safe for `spring.jpa.hibernate.ddl-auto=update` on a populated database — the old `user_name` / `author` columns contain free-form text that does not match a `users.id`. Use whichever migration tool the consumer already has (Flyway, Liquibase, …) and apply the equivalent of:

   ```sql
   -- audit_log: user_name (varchar) -> user_id (uuid FK)
   ALTER TABLE audit_log ADD COLUMN user_id uuid;
   -- Backfill: map known display names to user ids, everything else to the System User.
   UPDATE audit_log al
      SET user_id = COALESCE(
           (SELECT u.id FROM users u WHERE u.name = al.user_name),
           '00000000-0000-0000-0000-000000000000');
   ALTER TABLE audit_log ALTER COLUMN user_id SET NOT NULL;
   ALTER TABLE audit_log
       ADD CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users(id);
   ALTER TABLE audit_log DROP COLUMN user_name;

   -- comments: author (varchar) -> author_id (uuid FK)
   ALTER TABLE comments ADD COLUMN author_id uuid;
   UPDATE comments c SET author_id = c.author::uuid;  -- previous value was already user.id().toString()
   ALTER TABLE comments ALTER COLUMN author_id SET NOT NULL;
   ALTER TABLE comments
       ADD CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id);
   ALTER TABLE comments DROP COLUMN author;
   ```

   Note: the System User row (`id = 00000000-0000-0000-0000-000000000000`) is inserted by `SystemUserInitializer` on the first start with 0.14.0. If your migration runs *before* application code (typical Flyway setup), insert the row in the migration itself so the FK targets exist during backfill:

   ```sql
   INSERT INTO users (id, sub, name, created_at, updated_at)
   VALUES ('00000000-0000-0000-0000-000000000000', 'system', 'System',
           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
   ON CONFLICT (id) DO NOTHING;
   ```

5. **Update call sites that touched the renamed audit-log API.** Grep for the old names:

   ```bash
   grep -rn "findByUserNameOrderByCreatedAtDesc\|findByEntityTypeAndUserNameOrderByCreatedAtDesc\|getUserName\|setUserName" \
        --include="*.java" src
   ```

   For each match:
   - `findByUserNameOrderByCreatedAtDesc(String, …)` → `findByUserIdOrderByCreatedAtDesc(UUID, …)` and pass the user's `UUID` (use `SystemUser.ID` for the synthetic actor).
   - `findByEntityTypeAndUserNameOrderByCreatedAtDesc(String, String, …)` → `findByEntityTypeAndUserIdOrderByCreatedAtDesc(String, UUID, …)`.
   - `AuditLogEntity#getUserName/setUserName` → `getUser/setUser` and switch the string to a managed `UserEntity` (resolve via `UserRepository#findById` / `UserService#getCurrentUserEntity`).
   - `AuditLogDataService#createEntry(..., String)` → `createEntry(..., UserEntity)`. Use `userRepository.getReferenceById(SystemUser.ID)` for unauthenticated callers.

6. **Update call sites that touched the renamed comment API.** Grep for:

   ```bash
   grep -rn "getAuthorId\|setAuthorId" --include="*.java" src
   ```

   Each match needs to switch to `getAuthor()/setAuthor(UserEntity)`. Inside service classes, the typical pattern is `entity.setAuthor(userService.getCurrentUserEntity())`. The public `CommentDto.author: UserDto` did **not** change — REST clients, OpenAPI consumers, and code reading `CommentDto.author()` need no changes.

7. **Update tests that asserted against the old shape.** Grep for:

   ```bash
   grep -rn "\"System\"\|userName\|authorId" --include="*.java" src/test
   ```

   In audit-related assertions:
   - `AuditLogDto.user()` now returns a `UserDto`, not a `String`. Assertions like `assertEquals("System", dto.user())` become `assertEquals(SystemUser.ID, dto.user().id())` (or compare on `name()` if that is what the test really cares about).
   - Setting up test fixtures: persist a `UserEntity` (or look up the System User via `userRepository.getReferenceById(SystemUser.ID)`) and pass that to `createEntry`, instead of a literal name string.

8. **Verify the build.** All three must pass:

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; check the log for "Created System User row"
   ```

   If a Hibernate validation error mentions `user_name` or `author`, the migration (step 4) did not run — fix the migration order, do **not** revert the entity change.

9. **Commit** with a clear message, e.g.:

   ```
   chore(deps): upgrade spring-services to 0.14.0

   - Migrate audit_log.user_name -> audit_log.user_id (FK to users).
   - Migrate comments.author -> comments.author_id (FK to users).
   - Switch call sites from String to UserEntity / UUID.
   ```

### Guard rails

- **Do not** delete the old `user_name` / `author` column before the backfill `UPDATE` populates the new column, and do not set `NOT NULL` before backfill completes. Any row that cannot be mapped to a real user must fall back to the System User id (`00000000-0000-0000-0000-000000000000`), never to `NULL`.
- **Do not** insert the System User row through `UserRepository#save` or `EntityManager#persist`. The id is pre-assigned to the nil UUID; both paths reject pre-assigned ids. Use a direct SQL INSERT (Flyway migration or `JdbcTemplate`) — that is exactly what the library's own `SystemUserInitializer` does.
- **Do not** widen `UserService#getCurrentUserEntity` callers to non-request threads. It still relies on the JWT bound to the current request; from a scheduled task or `ApplicationRunner` use `userRepository.getReferenceById(SystemUser.ID)` instead.
- **Do not** keep `spring.jpa.hibernate.ddl-auto=update` as the migration strategy for this upgrade. Even when Hibernate adds the new column it will not backfill data and will not drop the old one — you end up with both columns and a failed `NOT NULL` validation.
- **Do not** bump unrelated dependency versions in the same change.

### Don't do this

- Do not "shim" the old API by adding a `getUserName()` that returns `user.name()`. The DTO type changed (`String` → `UserDto`), the repository signatures changed, and the migration is needed regardless — a shim only hides the call sites that still need to move.
- Do not store the literal string `"System"` anywhere as an actor reference. The System User row is the new source of truth; reach for `SystemUser.ID` / `SystemUser.NAME` constants instead of re-hardcoding the string.
- Do not edit `spring-services` from the consumer side. If a behaviour you depended on is missing, open an issue against the library.
- Do not bundle this upgrade with feature work in the same PR. Keep the dependency bump, the migration, and the call-site rewrites focused so review and rollback stay tractable.
