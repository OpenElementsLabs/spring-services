# Implementation Steps: Comment author as `@ManyToOne UserEntity` association

## Behavior Coverage

| # | Scenario | Layer | Covered in Step |
|---|----------|-------|-----------------|
| 1 | Author is mapped as a lazy ManyToOne to UserEntity | Backend | 5 |
| 2 | Author association is lazily loaded | Backend | 5 |
| 3 | Author is fetched on demand | Backend | 5 |
| 4 | A new comment is associated with the current user | Backend | 5 |
| 5 | A new comment is associated with a freshly provisioned user on first login | Backend | 5 |
| 6 | Comment creation rejects unauthenticated requests | Backend | 5 |
| 7 | Comment retrieval returns the author DTO without an extra service lookup | Backend | 5 |
| 8 | Listing many comments uses a batched author fetch | Backend | 5 |
| 9 | Comment DTO shape is unchanged | Backend | 5 |
| 10 | Inserting a comment with a non-existent author UUID fails | Backend | 5 |
| 11 | Deleting a user that still has comments is rejected | Backend | 5 |
| 12 | Deleting a user with no comments succeeds | Backend | 5 |
| 13 | `getCurrentUserEntity()` is publicly callable and returns the managed entity | Backend | 4 |
| 14 | `getCurrentUserEntity()` provisions a new user on first call | Backend | 4 |
| 15 | `getCurrentUserEntity()` updates drifted JWT claims | Backend | 4 |
| 16 | Comment create still emits an audit event | Backend | 5 |
| 17 | Comment update still emits an audit event | Backend | 5 |
| 18 | Comment delete still emits an audit event | Backend | 5 |

No frontend layer in this project — all scenarios are backend.

---

## Step 1: Refactor `CommentEntity` — replace `String authorId` with `@ManyToOne UserEntity author`

- [x] Remove the `private String authorId` field, its `@Column(name="author")` annotation, and the `getAuthorId()`/`setAuthorId(...)` methods from `CommentEntity`
- [x] Add a `private UserEntity author` field, annotated with:
  - `@ManyToOne(fetch = FetchType.LAZY, optional = false)`
  - `@JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_author"))`
  - `@org.hibernate.annotations.BatchSize(size = 50)`
- [x] Add `getAuthor()` and `setAuthor(@NonNull UserEntity author)` (with `Objects.requireNonNull`)
- [x] Add necessary imports (`UserEntity`, `FetchType`, `JoinColumn`, `ForeignKey`, `ManyToOne`, `BatchSize`)

**Acceptance criteria:**
- [x] `mvn compile` succeeds
- [x] `CommentService.java` no longer compiles (expected — fixed in Step 3)

**Related behaviors:** 1, 2, 3, 10, 11

---

## Step 2: Expose `UserService.getCurrentUserEntity()` as `public`

- [x] In `UserService.java`, change the visibility of `getCurrentUserEntity()` from `private synchronized` to `public synchronized`
- [x] Add a Javadoc block explaining: returns the managed `UserEntity` for the current authenticated subject; lazy-provisions on first call; refreshes JWT-claim drift; intended for callers that need the entity (e.g., to populate a `@ManyToOne` association)
- [ ] Update `getCurrentUser()`'s Javadoc if needed to cross-reference the new public method  *(skipped — existing Javadoc is still accurate)*

**Acceptance criteria:**
- [x] `mvn compile` succeeds (modulo `CommentService.java`, fixed in Step 3)
- [x] No new behavior change — existing tests still pass (UserServiceTest: 10/10 pass)

**Related behaviors:** 13, 14, 15

---

## Step 3: Refactor `CommentService` to use the new association

- [x] Update `createDetachedEntity()`: call `userService.getCurrentUserEntity()`, set the result via `entity.setAuthor(user)`
- [x] Update `toData()`: replace the `userService.findById(...)` lookup with `UserDto.fromEntity(entity.getAuthor())`
- [x] Remove the now-unused `UserDto` import if no longer referenced (or keep for `UserDto.fromEntity`) — `UserDto` import retained for `UserDto.fromEntity`
- [x] Verify `userService` field is still needed (yes — for `getCurrentUserEntity()`)

**Acceptance criteria:**
- [x] `mvn compile` succeeds
- [x] `CommentService` no longer calls `userService.findById(...)`

**Related behaviors:** 4, 5, 6, 7

---

## Step 4: Add `getCurrentUserEntity()` tests in `UserServiceTest`

Add a new `@Nested` class `GetCurrentUserEntityTest` to the existing `UserServiceTest` (which uses mocked repository — no Testcontainers needed):

- [ ] `shouldReturnEntityForExistingUser` — mock `userRepository.findBySub(...)` to return an existing entity, call `getCurrentUserEntity()`, assert the entity is returned (covers behavior 13)
- [ ] `shouldProvisionNewUserOnFirstCall` — mock `findBySub` returns empty, mock `save(...)` returns a saved entity with id, call `getCurrentUserEntity()`, assert `save` was called with the right `sub`/`name`/`email`/`avatarUrl` (covers behavior 14)
- [ ] `shouldUpdateDriftedJwtClaims` — mock `findBySub` returns an entity whose `name` differs from the JWT's `name`, call `getCurrentUserEntity()`, assert `save` was called and the entity's `name` matches the JWT (covers behavior 15)

**Acceptance criteria:**
- [ ] `mvn -Dtest=UserServiceTest test` passes
- [ ] All three new test methods pass
- [ ] No regression in pre-existing tests in this file

**Related behaviors:** 13, 14, 15

---

## Step 5: Add `CommentServiceIntegrationTest`

Create a new `@SpringBootTest` integration test at `src/test/java/com/openelements/spring/base/services/comment/CommentServiceIntegrationTest.java` following the established pattern (see `AuditLogIntegrationTest`):

- [ ] Class annotations: `@SpringBootTest(classes = TestApplication.class)`, `@Import(PostgresTestConfiguration.class)`, `@Testcontainers`, `@ActiveProfiles("testcontainers")`
- [ ] Inject `CommentService`, `CommentRepository`, `UserService`, `UserRepository`, `AuditLogRepository`, `EntityManager`, `JdbcTemplate`
- [ ] `@MockBean AuthService authService` — used to control which user is "logged in"
- [ ] `@BeforeEach`: clear `commentRepository`, `auditLogRepository`, `userRepository`
- [ ] Helper: `setAuthenticatedUser(String sub, String name, String email)` configures the mock
- [ ] Helper: `seedComment(UserEntity author, String text)` writes a comment directly via repository (bypasses service so `setAuthenticatedUser` is not required for setup)

Tests organized as `@Nested` classes:

### `EntityMapping` (covers behaviors 1, 2, 3)
- [ ] `shouldHaveAuthorIdColumnAndForeignKeyConstraint` — query `information_schema` via `JdbcTemplate` to verify `author_id UUID NOT NULL` exists, FK named `fk_comments_author` exists, and the `author` column does not exist (behavior 1)
- [ ] `shouldNotInitializeAuthorProxyOnFindById` — load a comment via `commentRepository.findById(...)`, assert `Hibernate.isInitialized(entity.getAuthor())` is `false` (behavior 2)
- [ ] `shouldLoadAuthorOnGetAuthor` — load a comment, call `getAuthor()` inside the same transaction, assert the returned `UserEntity` has the expected fields (behavior 3)

### `CommentCreation` (covers behaviors 4, 5, 6)
- [ ] `shouldAssociateNewCommentWithCurrentUser` — set authenticated user (existing in DB), call `commentService.save(new CommentDto(null, "hello", null, null, null))`, assert returned `CommentDto.author().id()` equals the user's id and the persisted row's `author_id` matches (behavior 4)
- [ ] `shouldProvisionUserOnFirstLoginAndLinkComment` — set authenticated user with a sub never seen before, call `save`, assert a new `UserEntity` was created and the comment's `author_id` references it (behavior 5)
- [ ] `shouldFailWhenNoAuthentication` — `when(authService.getUserInformation()).thenThrow(...)`, call `save`, assert it throws and no comment row was inserted (behavior 6)

### `CommentRetrieval` (covers behaviors 7, 8, 9)
- [ ] `shouldReturnAuthorDtoWithoutSeparateLookup` — seed a comment + user, configure a Mockito spy on `UserService` to fail the test if `findById(UUID)` is called, call `commentService.findById(commentId)`, assert author matches and `userService.findById(...)` was never invoked (behavior 7)
- [ ] `shouldBatchFetchAuthorsForLargeLists` — seed 100 comments authored by 5 users; reset Hibernate `Statistics`; call `commentService.getAll()` and read every author; assert `statistics.getEntityLoadCount(UserEntity.class.getName())` is at most 2 (behavior 8). Requires `spring.jpa.properties.hibernate.generate_statistics=true` in `application-testcontainers.properties`.
- [ ] `shouldReturnDtoWithUnchangedShape` — verify `CommentDto.class.getRecordComponents()` returns components named `id, text, author, createdAt, updatedAt` in order, with the expected types (behavior 9)

### `ReferentialIntegrity` (covers behaviors 10, 11, 12)
- [ ] `shouldRejectInsertWithUnknownAuthor` — construct a `CommentEntity` whose `author` is a detached `UserEntity` with a random UUID (not in DB), attempt to persist via repository + flush, assert `DataIntegrityViolationException` (behavior 10)
- [ ] `shouldRejectUserDeleteWhenCommentsExist` — seed user + comment, attempt `userRepository.delete(user)` + flush, assert `DataIntegrityViolationException` and verify the comment row remains (behavior 11)
- [ ] `shouldAllowUserDeleteWhenNoComments` — seed user with no comments, call `userRepository.delete(user)` + flush, assert success and verify the user row is gone (behavior 12)

### `AuditIntegration` (covers behaviors 16, 17, 18)
- [ ] `shouldEmitAuditEventOnCreate` — set authenticated user, save a new comment, query `auditLogRepository` for entries of `CommentDto` type with action `INSERT` referencing the new id; assert exactly one (behavior 16)
- [ ] `shouldEmitAuditEventOnUpdate` — save a comment, then save again with the same id and changed text, assert one `UPDATE` audit entry for that comment id (behavior 17)
- [ ] `shouldEmitAuditEventOnDelete` — save a comment, delete it, assert one `DELETE` audit entry for that comment id (behavior 18)

**Acceptance criteria:**
- [ ] `mvn -Dtest=CommentServiceIntegrationTest test` passes
- [ ] All 14 test methods (across 5 nested classes) pass
- [ ] Hibernate Statistics is enabled in the test profile

**Related behaviors:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17, 18

---

## Step 6: Verify the full test suite

- [ ] Run `mvn verify` from the repo root
- [ ] All existing tests must still pass (regression check — especially audit-log tests, user-service tests, and any other code touching the comment package transitively)
- [ ] Address any failures (likely due to schema regeneration, mocked-user fixtures, etc.)

**Acceptance criteria:**
- [ ] Full build green: `mvn verify` exits 0

**Related behaviors:** all (regression)

---

## Step 7: Update project documentation

The repo has no `.claude/conventions/project-specific/` files yet (scan first to confirm). If they exist, update them; if they don't, skip.

- [ ] Check whether `README.md` documents the `CommentService` API or schema. If it does, update the column reference (`author` → `author_id`) and note the `@ManyToOne` association.
- [ ] Check whether `CLAUDE.md` documents JPA conventions. If it does, add a brief note that `@ManyToOne` references to `UserEntity` follow the pattern established here (`fetch = LAZY`, `@BatchSize(50)`, named `@ForeignKey`).
- [ ] No CHANGELOG file exists in the repo — release notes are out of scope for this PR (covered by the migration guide already in `design.md`).

**Acceptance criteria:**
- [ ] All documentation files referencing the comment schema are accurate
- [ ] `mvn verify` still green

**Related behaviors:** none (documentation only)
