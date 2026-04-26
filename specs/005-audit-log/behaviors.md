# Behaviors: Audit Log for Data Lifecycle Events

## Event publishing control

### publishEvents defaults to true

- **Given** a `AbstractDbBackedDataService` subclass using the existing single-argument constructor
- **When** a save or delete operation completes
- **Then** events are published as before (no behavioral change)

### publishEvents can be disabled

- **Given** a `AbstractDbBackedDataService` subclass passing `publishEvents = false` to the new constructor
- **When** a save or delete operation completes
- **Then** no `OnObjectCreate`, `OnObjectUpdate`, or `OnObjectDelete` events are published

## Audit entry creation

### Create event produces audit entry

- **Given** an authenticated user "alice"
- **When** a new entity of type `BookDto` is created through a `AbstractDbBackedDataService`
- **Then** an audit log entry is persisted with `entityType = "BookDto"`, `entityId = <the new entity's UUID>`, `action = INSERT`, `user = "alice"`

### Update event produces audit entry

- **Given** an authenticated user "bob"
- **When** an existing entity of type `BookDto` is updated through a `AbstractDbBackedDataService`
- **Then** an audit log entry is persisted with `entityType = "BookDto"`, `entityId = <the entity's UUID>`, `action = UPDATE`, `user = "bob"`

### Delete event produces audit entry

- **Given** an authenticated user "alice"
- **When** an entity of type `BookDto` is deleted through a `AbstractDbBackedDataService`
- **Then** an audit log entry is persisted with `entityType = "BookDto"`, `entityId = <the entity's UUID>`, `action = DELETE`, `user = "alice"`

### Unauthenticated operation uses "System"

- **Given** no user is authenticated (e.g., a system-internal operation)
- **When** an entity is created, updated, or deleted through a `AbstractDbBackedDataService`
- **Then** an audit log entry is persisted with `user = "System"`

## Recursion prevention

### Audit entity does not trigger events

- **Given** the `AuditLogDataService` is configured with `publishEvents = false`
- **When** an audit log entry is persisted
- **Then** no `OnObjectCreate` event is published and no recursive audit entry is created

## Transaction isolation

### Audit write uses separate transaction

- **Given** an authenticated user performs a create operation
- **When** the audit log entry is written
- **Then** it runs in a `REQUIRES_NEW` transaction, independent of the main operation's transaction

### Failed audit write does not affect main operation

- **Given** an authenticated user creates a new entity
- **When** the audit log insert fails (e.g., database constraint violation)
- **Then** the exception is caught and logged as a warning, and the main entity creation succeeds

### Main operation rollback does not affect committed audit entry

- **Given** an event listener (synchronous, `@EventListener`) writes an audit entry in `REQUIRES_NEW`
- **When** the main transaction rolls back after the event
- **Then** the audit entry remains committed (this is an accepted trade-off of `REQUIRES_NEW`)

## Query methods

### Find by entity type

- **Given** audit entries exist for types `BookDto`, `BookDto`, and `UserDto`
- **When** `findByEntityType("BookDto")` is called
- **Then** exactly 2 entries are returned, both with `entityType = "BookDto"`

### Find by user

- **Given** audit entries exist for users "alice", "alice", and "bob"
- **When** `findByUser("alice")` is called
- **Then** exactly 2 entries are returned, both with `user = "alice"`

### Find by type and user

- **Given** audit entries: `(BookDto, alice)`, `(BookDto, bob)`, `(UserDto, alice)`
- **When** `findByEntityTypeAndUser("BookDto", "alice")` is called
- **Then** exactly 1 entry is returned with `entityType = "BookDto"` and `user = "alice"`

### Find by type returns empty list when no matches

- **Given** no audit entries for type `NoteDto`
- **When** `findByEntityType("NoteDto")` is called
- **Then** an empty list is returned

## Retention cleanup

### Expired entries are deleted

- **Given** `audit.retention-days = 90` and audit entries exist from 91 days ago and 89 days ago
- **When** the scheduled cleanup job runs
- **Then** the 91-day-old entry is deleted and the 89-day-old entry is retained

### Default retention is 4 years

- **Given** `audit.retention-days` is not configured
- **When** the scheduled cleanup job runs
- **Then** entries older than 1460 days are deleted

### Cleanup does nothing when no expired entries

- **Given** all audit entries are within the retention window
- **When** the scheduled cleanup job runs
- **Then** no entries are deleted

## Audit log DTO

### AuditLogDto contains all fields

- **Given** an `AuditLogEntity` with `entityType = "BookDto"`, `entityId = <uuid>`, `action = INSERT`, `user = "alice"`, `createdAt = <timestamp>`
- **When** the entity is mapped to `AuditLogDto`
- **Then** all fields are present and match the entity values

### Inherited CRUD methods work

- **Given** the `AuditLogDataService` extends `AbstractDbBackedDataService`
- **When** `getAll()`, `findById(uuid)`, or `findAll(pageable)` is called
- **Then** audit log entries are returned as `AuditLogDto` instances
