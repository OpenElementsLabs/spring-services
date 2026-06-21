# Implementation Steps: Audit Log for Data Lifecycle Events

## Step 1: Add `publishEvents` flag to `AbstractDbBackedDataService`

- [ ] Add `private final boolean publishEvents` field
- [ ] Add second constructor `AbstractDbBackedDataService(ApplicationEventPublisher, boolean publishEvents)`
- [ ] Existing single-arg constructor delegates to the new one with `publishEvents = true`
- [ ] Update `postSave` and `postDelete` to short-circuit when `publishEvents` is false
- [ ] Update Javadoc to document the new constructor

**Acceptance criteria:**
- [ ] Project builds (`./mvnw compile`)
- [ ] Existing tests still pass (`./mvnw test`)
- [ ] All current `AbstractDbBackedDataService` subclasses continue to publish events

**Related behaviors:** `publishEvents defaults to true`, `publishEvents can be disabled`

---

## Step 2: Create `AuditAction` enum and `AuditLogEntity`

- [ ] Create package `com.openelements.spring.base.services.audit`
- [ ] Create `AuditAction` enum with values `INSERT`, `UPDATE`, `DELETE`
- [ ] Create `AuditLogEntity` extending `AbstractEntity`, table name `audit_log`
- [ ] Columns: `entity_type` (varchar(255), not null), `entity_id` (UUID, not null), `action` (varchar(20), not null, `@Enumerated(STRING)`), `user_name` (varchar(255), not null) — note: avoid reserved word `user`
- [ ] Provide getters/setters and a `toString()` (no PII beyond user name)

**Acceptance criteria:**
- [ ] Project builds
- [ ] Entity passes basic JPA validation when persisted in a `@DataJpaTest` smoke test (verified in step 4)

**Related behaviors:** all "Audit entry creation" scenarios

---

## Step 3: Create `AuditLogDto`

- [ ] Record `AuditLogDto(UUID id, String entityType, UUID entityId, AuditAction action, String user, Instant createdAt)` implementing `WithId`
- [ ] Add `fromEntity(AuditLogEntity)` static factory consistent with the `WebhookDto` pattern
- [ ] Add Javadoc on the record and its components

**Acceptance criteria:**
- [ ] Unit test for `fromEntity` mapping covers all fields
- [ ] Project builds

**Related behaviors:** `AuditLogDto contains all fields`

---

## Step 4: Create `AuditLogRepository`

- [ ] Interface `AuditLogRepository extends EntityRepository<AuditLogEntity>`
- [ ] Method `List<AuditLogEntity> findByEntityType(String entityType)`
- [ ] Method `List<AuditLogEntity> findByUserName(String userName)`
- [ ] Method `List<AuditLogEntity> findByEntityTypeAndUserName(String entityType, String userName)`
- [ ] Method `void deleteByCreatedAtBefore(Instant cutoff)` (annotated `@Modifying` + `@Transactional`)

**Acceptance criteria:**
- [ ] `@DataJpaTest` covers each finder and the delete method

**Related behaviors:** `Find by entity type`, `Find by user`, `Find by type and user`, `Find by type returns empty list when no matches`, retention behaviors

---

## Step 5: Create `AuditLogDataService`

- [ ] Class `AuditLogDataService extends AbstractDbBackedDataService<AuditLogEntity, AuditLogDto>`
- [ ] Constructor passes `publishEvents = false` to super
- [ ] Implement `createDetachedEntity`, `updateEntity`, `toData`, `getRepository`
- [ ] Add `findByEntityType`, `findByUser`, `findByEntityTypeAndUser` returning lists of `AuditLogDto`
- [ ] Add `createEntry(String entityType, UUID entityId, AuditAction action, String user)` annotated `@Transactional(propagation = REQUIRES_NEW)`

**Acceptance criteria:**
- [ ] Saving an audit entry does **not** publish `OnObjectCreate`
- [ ] Integration test verifies the finders

**Related behaviors:** `Audit entity does not trigger events`, `Inherited CRUD methods work`, `Find by *`

---

## Step 6: Create `AuditLogEventListener`

- [ ] `@Component` listening to `OnObjectCreate`, `OnObjectUpdate`, `OnObjectDelete` via `@EventListener` (synchronous)
- [ ] Resolve user via `AuthService.getPrincipal().getName()`; on `IllegalStateException` use `"System"`
- [ ] Filter out events whose DTO type is `AuditLogDto` as a defensive guard (recursion prevention is primarily handled by `publishEvents = false`)
- [ ] Call `auditLogDataService.createEntry(...)` in a try/catch — on exception log a `WARN` and swallow
- [ ] Inject `AuditLogDataService` and `AuthService`

**Acceptance criteria:**
- [ ] Unit test (with mocked dependencies) covers: authenticated user → user name; missing auth → `"System"`; failed createEntry → caught and logged

**Related behaviors:** `Create/Update/Delete event produces audit entry`, `Unauthenticated operation uses "System"`, `Failed audit write does not affect main operation`

---

## Step 7: Create `AuditConfig` with retention property and scheduled cleanup

- [ ] `@Configuration @ComponentScan @AutoConfiguration @EnableAutoConfiguration @EnableScheduling` (mirrors `WebhookConfig`)
- [ ] `AuditProperties` (record or class with `@ConfigurationProperties("audit")`) exposing `retentionDays` defaulting to `1460`
- [ ] `AuditCleanupJob` `@Component` with `@Scheduled(cron = "0 0 2 * * *")` and `@Transactional` calling `auditLogRepository.deleteByCreatedAtBefore(now - retentionDays)`
- [ ] Register `AuditConfig` in `FullSpringServiceConfig`'s `@Import`
- [ ] Update its package-level Javadoc to describe the new feature

**Acceptance criteria:**
- [ ] Project builds
- [ ] Cleanup job is invokable directly (unit-callable) and respects the configured retention

**Related behaviors:** `Expired entries are deleted`, `Default retention is 4 years`, `Cleanup does nothing when no expired entries`

---

## Step 8: Integration test — full audit flow

- [ ] `AuditLogDataServiceIntegrationTest` (`@SpringBootTest` + `@Testcontainers` + `@ActiveProfiles("testcontainers")`)
- [ ] Use a representative service that already extends `AbstractDbBackedDataService` (e.g., `WebhookDataService`) and verify a CREATE/UPDATE/DELETE produces audit entries
- [ ] Mock `AuthService` to control the current user; verify "System" path when `AuthService.getPrincipal()` throws
- [ ] Verify the audit service itself does not produce recursive entries
- [ ] Verify finders by type and user
- [ ] Verify cleanup job removes entries older than retention

**Acceptance criteria:**
- [ ] All scenarios in `behaviors.md` are covered by at least one test
- [ ] `./mvnw verify` passes

---

## Step 9: Final validation

- [ ] `./mvnw spotless:apply` (Google Java Format)
- [ ] `./mvnw clean verify`
- [ ] All tests pass
