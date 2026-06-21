<!--
Reference example for the `release-doc` skill — a LIBRARY upgrade guide with
breaking changes. Modeled on the canonical doc:
https://github.com/OpenElementsLabs/spring-services/blob/main/docs/upgrade-to-0.16.md
Illustrative and trimmed; consult the linked doc for the authoritative version.
File would live at: docs/releases/upgrade-to-0.16.md
-->

# Upgrade prompt: `com.open-elements:spring-services` 0.15.x → 0.16.0

## Prompt

You are upgrading a Spring Boot app that depends on `com.open-elements:spring-services` from 0.15.x to 0.16.0. This upgrade has **one mandatory breaking change** (audit-log entries now require a `name`), one **breaking-light** change (a validation message string changed), and one **optional** addition (`@NameSupplier`). Apply the mandatory changes, then decide on the optional one. Do not change anything outside this scope.

### What changed in 0.16.0

#### Dependencies

Bump only `com.open-elements:spring-services` to `0.16.0`. No other version bumps: Spring Boot stays at `3.5.14`, Testcontainers stays at `2.0.5`. Do **not** change those coordinates as part of this upgrade.

#### Breaking: `AuditLogEntity` gains a `name` field

Every audit-log entry now carries a non-null `name`. Three call surfaces change:

```java
// AuditLogEntity — new column, NOT NULL
@Column(name = "entity_name", nullable = false)
private String name;

// AuditLogDataService#createEntry — 4 args → 5 args.
// The new `name` parameter is @NonNull (NullPointerException on null).
// Use the literal "UNKNOWN" when you have no meaningful name.

// 0.15.x
auditLogDataService.createEntry(entityType, entityId, AuditAction.UPDATE, changeSet);

// 0.16.0
auditLogDataService.createEntry(entityType, entityId, "UNKNOWN", AuditAction.UPDATE, changeSet);
```

A database migration must add the column, backfill existing rows, and only then apply the `NOT NULL` constraint:

```sql
ALTER TABLE audit_log ADD COLUMN entity_name VARCHAR(255);
UPDATE audit_log SET entity_name = 'UNKNOWN' WHERE entity_name IS NULL;
ALTER TABLE audit_log ALTER COLUMN entity_name SET NOT NULL;
```

#### Additive: `@NameSupplier` for DTO-derived names

Adoption is **strictly optional**. Annotate exactly one method per DTO to supply a meaningful name for its audit entries:

```java
public record ContactDto(UUID id, String firstName, String lastName) {
    @NameSupplier
    public String auditName() {
        return firstName + " " + lastName;
    }
}
```

Existing DTOs continue to work unchanged; their audit entries simply carry `name = "UNKNOWN"`.

#### Breaking-light: image upload limit raised to 20 MB

The multipart limit changed from 2 MB to 20 MB. The validation message changed from `"File too large (max 2MB)"` to `"File too large (max 20 MB)"` — any string-equality assertion on the old message will break.

##### Runtime requirements for HEIC / WebP

0.16.0 can transcode WebP/HEIC/HEIF uploads. If neither HEIC nor WebP uploads are part of the consumer's product surface, **no new dependency is required** and no action is needed here.

### Steps

1. Bump `com.open-elements:spring-services` to `0.16.0`; leave all other coordinates untouched.
2. Add the database migration above (add column → backfill `'UNKNOWN'` → set `NOT NULL`), in that order.
3. Add the `name` field to `AuditLogEntity` and the `name` parameter to any local `AuditLogDto` usage.
4. Update every `createEntry(...)` call site to pass a `name`; use `"UNKNOWN"` where none is available.
5. (Optional) Add a single `@NameSupplier` method to DTOs that should record meaningful names.
6. Fix any test asserting on the old `"File too large (max 2MB)"` message.
7. Build and run the test suite; confirm green before committing.

### Guard rails

- Do **not** add the `entity_name` column with `DEFAULT NULL` and leave the `NOT NULL` constraint off.
- Do **not** call `createEntry(...)` with `null` for the new `name` argument.
- Do **not** assume HEIC / WebP "just works" because the enum has the constant.
- Do **not** bump Spring Boot, Testcontainers, or any other dependency in the same change.

### Don't do this

- Do not "shim" the old API by adding an `AuditLogDto` overload with the 0.15 signature.
- Do not register `@NameSupplier` on more than one method per DTO with the intent of "picking the better one."
- Do not bundle this upgrade with unrelated feature work in the same PR.
