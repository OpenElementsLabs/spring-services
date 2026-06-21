# Behaviors: Audit Log User FK

## System User Bootstrap

### Bootstrap on first startup

- **Given** an empty `users` table
- **When** the application context starts and `SystemUserInitializer.run()` executes
- **Then** a `UserEntity` row exists with `id = 00000000-0000-0000-0000-000000000000`, `sub = "system"`, and `name = "System"`

### Idempotent bootstrap on subsequent startups

- **Given** a `users` table that already contains the System User row
- **When** the application context starts again and `SystemUserInitializer.run()` executes
- **Then** no insert is attempted and no exception is thrown
- **And** the existing System User row is unchanged

### Concurrent multi-instance bootstrap

- **Given** two application instances starting simultaneously against the same empty database
- **When** both instances reach `SystemUserInitializer.run()` at the same time and both pass the `existsById` check
- **Then** the first instance to insert succeeds
- **And** the second instance catches a `DataIntegrityViolationException` and continues without re-throwing
- **And** exactly one System User row exists after both instances have started

### Bootstrap with pre-existing schema only

- **Given** the `users` table exists but is empty (Hibernate `validate` mode or pre-migrated schema)
- **When** the application context starts
- **Then** the System User is created without requiring any DDL change

## Audit Write — User Resolution

### Authenticated JWT user with matching local row

- **Given** a JWT-authenticated request whose `sub` claim corresponds to a `UserEntity` already present in the `users` table
- **When** the user performs a create/update/delete via an `AbstractDbBackedDataService`
- **Then** an `AuditLogEntity` is persisted with `user_id` equal to the local `UserEntity.id` of the authenticated user

### No security context — fallback to System User

- **Given** a code path executing without a `SecurityContext` (e.g., scheduled task, startup data load)
- **When** an `AbstractDbBackedDataService` performs a save and `AuthService.getUserInformation()` throws `IllegalStateException`
- **Then** an `AuditLogEntity` is persisted with `user_id = 00000000-0000-0000-0000-000000000000`

### Null `UserInformation` — fallback to System User

- **Given** `AuthService.getUserInformation()` returns `null`
- **When** an `AbstractDbBackedDataService` performs a save
- **Then** an `AuditLogEntity` is persisted with `user_id = SystemUser.ID`

### Blank user name — fallback to System User

- **Given** `AuthService.getUserInformation()` returns a `UserInformation` whose `name` is `null` or blank
- **When** an `AbstractDbBackedDataService` performs a save
- **Then** an `AuditLogEntity` is persisted with `user_id = SystemUser.ID`

### API-key authenticated request — fallback to System User

- **Given** a request authenticated via an API key (no JWT principal; `AuthService.getUserInformation()` returns `UserInformation("UNKNOWN", "UNKNOWN", null, null)`)
- **When** an `AbstractDbBackedDataService` performs a save
- **Then** an `AuditLogEntity` is persisted with `user_id = SystemUser.ID`

### Audit-write failure does not break the originating transaction

- **Given** any of the above scenarios
- **When** the audit insert fails (e.g., DB transient error)
- **Then** the originating create/update/delete transaction still commits successfully
- **And** the failure is logged at WARN level

## Schema and FK Constraint

### Foreign key prevents user deletion when audit entries reference them

- **Given** a `UserEntity` referenced by at least one `AuditLogEntity` row
- **When** something attempts to delete the `UserEntity` row directly via SQL
- **Then** the database raises a foreign key constraint violation (`RESTRICT` / `NO ACTION`) and the delete is rejected

### `user_id` column is non-nullable

- **Given** a fresh schema generated from `AuditLogEntity`
- **When** an `INSERT INTO audit_log (...)` omits `user_id`
- **Then** the database rejects the statement with a `NOT NULL` constraint violation

## Repository Queries

### Find audit entries by user id

- **Given** several audit entries exist for users `alice` and `bob`
- **When** `auditLogRepository.findByUserIdOrderByCreatedAtDesc(aliceId, pageable)` is called
- **Then** only entries with `user_id = aliceId` are returned, sorted by `createdAt` descending

### Find audit entries by entity type and user id

- **Given** several audit entries exist across multiple entity types and users
- **When** `auditLogRepository.findByEntityTypeAndUserIdOrderByCreatedAtDesc("BookEntity", aliceId, pageable)` is called
- **Then** only entries matching both filters are returned

### Find audit entries written by the System User

- **Given** at least one audit entry was written without a security context
- **When** `auditLogRepository.findByUserIdOrderByCreatedAtDesc(SystemUser.ID, pageable)` is called
- **Then** all System-attributed entries are returned

## DTO Shape

### `AuditLogDto.user` is the full `UserDto`

- **Given** an `AuditLogEntity` whose `user` is `alice`
- **When** `AuditLogDto.fromEntity(entry)` is called within an open persistence context
- **Then** the returned DTO has `user.id = alice.id`, `user.name = "alice"`, and the other `UserDto` fields populated from the entity

### `AuditLogDto.user` for a System-attributed entry

- **Given** an `AuditLogEntity` whose `user_id = SystemUser.ID`
- **When** `AuditLogDto.fromEntity(entry)` is called
- **Then** the returned DTO has `user.id = SystemUser.ID` and `user.name = "System"`

## REST API

### Filter audit log endpoint by user id

- **Given** the audit log REST endpoint is exposed
- **When** a client sends `GET /audit-log?userId=<uuid>`
- **Then** the response contains only entries with that `user_id`

### Audit log JSON response includes nested user object

- **Given** an audit entry exists for `alice`
- **When** a client retrieves it via the REST endpoint
- **Then** the JSON contains `"user": { "id": "...", "name": "alice", "email": "...", "avatarUrl": "...", "createdAt": "...", "updatedAt": "..." }`
- **And** the legacy `"user": "alice"` String form is no longer present

## Edge Cases

### JWT user whose local mirror has not yet been created

- **Given** a request authenticated by JWT for a user whose `sub` does not yet have a `UserEntity` row
- **When** an `AbstractDbBackedDataService` performs a save before `UserService` has lazy-provisioned the user
- **Then** the audit listener provisions or looks up the user before writing the audit row, **or** falls back to the System User if provisioning is not possible from the audit path

  *(Resolution: The audit listener relies on `UserService` having already provisioned the user earlier in the request — `UserService` runs on every authenticated request. If the `findBySub` lookup nevertheless returns empty, fall back to System User and log a WARN.)*

### Test profile with `create-drop` schema

- **Given** a test using `spring.jpa.hibernate.ddl-auto=create-drop`
- **When** the Spring context is reloaded between test classes
- **Then** the schema is recreated empty
- **And** `SystemUserInitializer` runs and re-creates the System User as part of context startup

### Existing tests that mocked `userName` as a String

- **Given** a test that previously asserted `auditLogDto.user().equals("alice")`
- **When** the test is updated to the new DTO shape
- **Then** the equivalent assertion is `auditLogDto.user().name().equals("alice")` and `auditLogDto.user().id().equals(aliceId)`
