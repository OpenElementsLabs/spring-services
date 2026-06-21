# Upgrade prompt: `com.open-elements:spring-services` 0.14.x → 0.15.0

`spring-services` 0.15.0 reorganizes the user-related classes into a new package, moves the role constants/annotations
into a dedicated `security.roles` package, and adds several new role annotations, three new audit-log query methods, and
two additional autoconfigurations (`CommentConfig`, `LanguageConfig`, `UserConfig`). There are **no database schema
changes**, **no dependency version bumps**, and **no DTO surface changes** — the upgrade is a Java-only refactor plus a
set of additive features.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo. Paste it verbatim.

---

## Prompt

You are working inside a Spring Boot service that depends on `com.open-elements:spring-services`. Goal: upgrade to
`0.15.0`, fix the import statements for the moved classes, and optionally adopt the new role annotations and audit-log
query methods.

### What changed in 0.15.0

#### Breaking: package move for the user model

All user-related classes moved out of `com.openelements.spring.base.security.user` into the new package
`com.openelements.spring.base.services.user`. The class names, signatures, and behavior are unchanged — only the package
coordinate changed.

| Old fully-qualified name                                           | New fully-qualified name                                           |
|--------------------------------------------------------------------|--------------------------------------------------------------------|
| `com.openelements.spring.base.security.user.UserEntity`            | `com.openelements.spring.base.services.user.UserEntity`            |
| `com.openelements.spring.base.security.user.UserDto`               | `com.openelements.spring.base.services.user.UserDto`               |
| `com.openelements.spring.base.security.user.UserRepository`        | `com.openelements.spring.base.services.user.UserRepository`        |
| `com.openelements.spring.base.security.user.UserService`           | `com.openelements.spring.base.services.user.UserService`           |
| `com.openelements.spring.base.security.user.SystemUser`            | `com.openelements.spring.base.services.user.SystemUser`            |
| `com.openelements.spring.base.security.user.SystemUserInitializer` | `com.openelements.spring.base.services.user.SystemUserInitializer` |

The `com.openelements.spring.base.security.user` package no longer exists.

#### Breaking: package move for `Roles`

The `Roles` constants class moved out of `com.openelements.spring.base.security.user` into the new package
`com.openelements.spring.base.security.roles`. Constants and string values are unchanged.

| Old                                                | New                                                 |
|----------------------------------------------------|-----------------------------------------------------|
| `com.openelements.spring.base.security.user.Roles` | `com.openelements.spring.base.security.roles.Roles` |

`Roles.ROLE_APP_ADMIN`, `Roles.ROLE_APP_USER`, `Roles.ROLE_IT_ADMIN` retain their previous values (`"APP-ADMIN"`,
`"APP-USER"`, `"IT-ADMIN"`). The class gains four new constants — see "Additive" below.

#### Minor: `EmailProperties` registration

`EmailProperties` is no longer annotated `@Component`. It is now registered via
`@EnableConfigurationProperties(EmailProperties.class)` on `EmailService`. The bean is still present at runtime with the
same name and type, so `@Autowired EmailProperties` continues to work for consumers that pull in `EmailConfig` (directly
or via `FullSpringServiceConfig`). Only consumers that relied on component-scanning `EmailProperties` *without*
importing `EmailConfig` need to start importing `EmailConfig`.

#### Additive: role annotations

New meta-annotations in `com.openelements.spring.base.security.roles` provide shortcuts for
`@PreAuthorize("hasRole('<ROLE>')")`:

- `@RequiresAppAdmin` — `APP-ADMIN`
- `@RequiresAppUser` — `APP-USER`
- `@RequiresItAdmin` — `IT-ADMIN`
- `@RequiresEmployee` — `EMPLOYEE`
- `@RequiresExternal` — `EXTERNAL`
- `@RequiresBackoffice` — `BACKOFFICE`
- `@RequiresManagement` — `MANAGEMENT`

`Roles` gains the matching string constants `ROLE_EMPLOYEE`, `ROLE_EXTERNAL`, `ROLE_BACKOFFICE`, `ROLE_MANAGEMENT`
alongside the existing `ROLE_APP_ADMIN`, `ROLE_APP_USER`, `ROLE_IT_ADMIN`.

The annotations target `METHOD` and `TYPE` and have `RUNTIME` retention; apply them on controller / service methods or
classes.

#### Additive: audit-log query methods

`AuditLogDataService` gains three new read-side methods (all return `List<AuditLogDto>` rather than `Page<…>` because
they are bounded by construction):

- `findByDay(LocalDate day)` — all entries created on the given calendar day, ordered oldest-first. Day boundaries are
  computed in the JVM's `ZoneId.systemDefault()`.
- `findLatest(int limit)` — the `limit` most recent entries across all entity types, newest-first. Throws
  `IllegalArgumentException` if `limit <= 0`.
- `findLatestByEntityType(String entityType, int limit)` — the `limit` most recent entries for a given `entityType`,
  newest-first. Throws `IllegalArgumentException` if `limit <= 0`, `NullPointerException` if `entityType` is `null`.

The corresponding `AuditLogRepository` methods are also new:

- `findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(Instant from, Instant to)`
- `findAllByOrderByCreatedAtDesc(Limit limit)`
- `findByEntityTypeOrderByCreatedAtDesc(String entityType, Limit limit)`

#### Additive: new autoconfigurations

Three new `@AutoConfiguration` classes are part of the library:

- `com.openelements.spring.base.services.comment.CommentConfig`
- `com.openelements.spring.base.services.translation.TranslationConfig`
- `com.openelements.spring.base.services.user.UserConfig`

`FullSpringServiceConfig` now imports `CommentConfig` and `LanguageConfig` in addition to the previously imported
configurations. Consumers that import `FullSpringServiceConfig` get all three automatically and need no change.
Consumers that hand-pick configurations should add `UserConfig` (required if they use any user-related bean) and
optionally `CommentConfig` / `LanguageConfig`.

### Steps

1. **Find the consumer's `pom.xml`** at repo root. Confirm `com.open-elements:spring-services` is listed.

2. **Bump `spring-services` to `0.15.0`** in that `pom.xml`. No other dependency edits are required for this upgrade.

3. **Rewrite imports for the moved user package.** Grep for the old package across both production and test sources:

   ```bash
   grep -rln "com\.openelements\.spring\.base\.security\.user\." --include="*.java" src
   ```

   For each hit, replace the import prefix. Either run a per-class sed pass (safer to keep it class-by-class so you
   don't accidentally rewrite an unrelated FQN), or use your IDE's "Move class" refactoring against the local jar. The
   substitutions are:

   ```
   com.openelements.spring.base.security.user.UserEntity            -> com.openelements.spring.base.services.user.UserEntity
   com.openelements.spring.base.security.user.UserDto               -> com.openelements.spring.base.services.user.UserDto
   com.openelements.spring.base.security.user.UserRepository        -> com.openelements.spring.base.services.user.UserRepository
   com.openelements.spring.base.security.user.UserService           -> com.openelements.spring.base.services.user.UserService
   com.openelements.spring.base.security.user.SystemUser            -> com.openelements.spring.base.services.user.SystemUser
   com.openelements.spring.base.security.user.SystemUserInitializer -> com.openelements.spring.base.services.user.SystemUserInitializer
   ```

   No method signatures or behavior changed for any of these classes — only the package coordinate.

4. **Rewrite imports for the moved `Roles` class.** Same drill:

   ```bash
   grep -rln "com\.openelements\.spring\.base\.security\.user\.Roles" --include="*.java" src
   ```

   Replace with `com.openelements.spring.base.security.roles.Roles`. If the consumer also references the old package via
   a wildcard import (e.g., `import com.openelements.spring.base.security.user.*;`), split it into two explicit
   imports — one for `services.user.*` (entities/DTOs/services), one for `security.roles.Roles` — because the contents
   now live in two different packages.

5. **If the consumer hand-picks `@Import` configurations** (instead of relying on `FullSpringServiceConfig`), add
   `UserConfig` to the import list whenever any user-related bean (`UserService`, `UserRepository`,
   `SystemUserInitializer`, …) is consumed:

   ```java
   @Import({
       SecurityConfig.class,
       UserConfig.class,          // NEW in 0.15.0 — required for user-related beans
       ApiKeyConfig.class,
       // …existing imports
   })
   ```

   Add `CommentConfig` / `LanguageConfig` only if the consumer actually uses comment or translation beans.

   Consumers that already use `@Import(FullSpringServiceConfig.class)` need no change here — `FullSpringServiceConfig`
   picks up the new configs transitively.

6. **(Optional) Adopt the new role annotations.** If the consumer hand-writes `@PreAuthorize("hasRole('APP-ADMIN')")`
   and similar, the meta-annotations are equivalent and read more cleanly. Grep for current usages:

   ```bash
   grep -rn "@PreAuthorize.*hasRole.*\(APP-ADMIN\|APP-USER\|IT-ADMIN\|EMPLOYEE\|EXTERNAL\|BACKOFFICE\|MANAGEMENT\)" \
        --include="*.java" src
   ```

   Replace with the matching `@RequiresXxx` annotation from `com.openelements.spring.base.security.roles`. This is *
   *strictly optional** — existing `@PreAuthorize` strings keep working unchanged. Do not bundle this into the same
   commit as the import-rewrite step; ship it as a separate, reviewable change.

7. **(Optional) Adopt the new audit-log query methods.** If the consumer previously emulated "latest N entries" via a
   `PageRequest.of(0, n, Sort.by("createdAt").descending())`, the new `auditLogDataService.findLatest(n)` /
   `findLatestByEntityType(type, n)` / `findByDay(day)` methods are simpler. Do not refactor working code just for
   stylistic alignment; only switch when touching the call site for another reason.

8. **Verify the build.** All three must pass:

   ```bash
   ./mvnw -DskipTests=false test
   ./mvnw verify
   ./mvnw spring-boot:run            # smoke-start; the application context should come up cleanly
   ```

   A compilation error of the form `cannot find symbol: class UserEntity` in package
   `com.openelements.spring.base.security.user` means step 3 missed a file — re-run the grep and finish the rewrite. Do
   **not** add a manual shim class in the consumer.

9. **Commit** with a clear message, e.g.:

   ```
   chore(deps): upgrade spring-services to 0.15.0

   - Rewrite imports for the moved user package (security.user -> services.user).
   - Rewrite imports for the moved Roles class (security.user -> security.roles).
   - (Optionally) adopt new @Requires* annotations / new AuditLog query methods.
   ```

### Guard rails

- **Do not** create a local "shim" subclass or re-export of `UserEntity` / `UserDto` / `UserService` / `Roles` in the
  consumer to avoid the rewrite. The old package is gone; a shim only hides the call sites that still need to move and
  creates a duplicate JPA entity, which Hibernate will reject at startup.
- **Do not** widen the new role annotations onto every controller method "for consistency." Apply them only where the
  equivalent `@PreAuthorize("hasRole(...)")` already exists or where the role check is genuinely required — adding
  authorization to previously-public endpoints is a behavior change, not an upgrade.
- **Do not** bundle this upgrade with feature work in the same PR. Keep the dependency bump, the import rewrites, and
  any optional annotation/audit adoption in separate, focused commits so review and rollback stay tractable.
- **Do not** bump unrelated dependency versions in the same change. 0.15.0 does not change the Spring Boot or
  Testcontainers BOM versions — leave those alone.
- **Do not** add the new `UserConfig` if the consumer relies on `FullSpringServiceConfig` — it is already imported
  there, and a duplicate `@Import` produces a second bean definition for some user-package beans and a noisy startup log
  at best.

### Don't do this

- Do not rewrite imports with a naive `sed -i 's/security\.user/services\.user/g'` — that will also corrupt the `Roles`
  import (it moved to `security.roles`, not `services.user`) and any unrelated identifiers that happen to contain the
  substring. Do the two package moves as two separate, scoped substitutions.
- Do not "consolidate" the new `@Requires*` annotations into a single custom annotation in the consumer to "save
  imports." The point of having one annotation per role is that each one carries its own `@PreAuthorize` SpEL
  expression; merging them defeats Spring Security's parsing.
- Do not edit `spring-services` from the consumer side. If a behaviour you depended on is missing, open an issue against
  the library.
- Do not assume the old `security.user` package still works "transitively." It is fully removed in 0.15.0 — the upgrade
  *will* fail to compile until the imports are rewritten.