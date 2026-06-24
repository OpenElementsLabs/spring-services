# Implementation Steps: SCIM Foundation — User Model Refactor

Spec: [`012-scim-foundation-user-model`](design.md) · Behaviors: [`behaviors.md`](behaviors.md)

The work has six steps, ordered by dependency: data-model foundation first (Steps 1–2), then the
JIT-login flow that consumes both (Steps 3–4), then the `PrincipalDirectory` bridge that adapts
the new entity to spec 010's port (Step 5), and finally documentation (Step 6).

**Prerequisites.** Spec 011 (Security Configuration Hygiene) must be merged — this spec extends
`AuthService.getUserInformation()` (returns `Optional<UserInformation>` after 011) and the
`UserInformation` record itself.

**Spec 010 ordering.** Spec 010 declares the `PrincipalDirectory` port; this spec implements it.
If 010 has not landed when this spec is implemented, **Step 5 ships the port declaration too**
(temporary library duplication; 010 deletes the duplicate when it merges). The plan below assumes
this case — adjust Step 5 if 010 lands first.

After each step the project must compile and the full test suite must pass before moving on.

---

## Step 1: `UserEntity` schema additions + repository methods

**Scope-Note:** Step 1 was implemented together with Steps 2 and 3 because they are coupled —
`user_name NOT NULL` requires every code path that inserts a `UserEntity` to provide a value,
which requires `UserInformation.userName` (Step 2) and `UserProvisioner` to set it (Step 3).
Each individual step couldn't compile-and-pass-tests in isolation. The three changes landed
as one logical commit ("Foundation infrastructure").

**Changes:**

- [x] In `src/main/java/com/openelements/spring/base/services/user/UserEntity.java`:
    - Change `@Column(name = "sub", nullable = false, unique = true, length = 255)` to
      `@Column(name = "sub", nullable = true, unique = true, length = 255)` — relax to nullable.
    - Update `setSub(...)` to allow `null` (remove the `Objects.requireNonNull` on `sub`).
    - Add `external_id` column: `@Column(name = "external_id", nullable = true, unique = true,
    length = 255) private String externalId;` plus getter/setter.
    - Add `user_name` column: `@Column(name = "user_name", nullable = false, unique = true,
    length = 255) private String userName;` plus getter/setter.
    - Add `active` column: `@Column(name = "active", nullable = false) private boolean active =
    true;` plus `isActive() / setActive(boolean)`.
    - Update `toString()` to include the new fields (sub may now be null — guard).
- [x] In `src/main/java/com/openelements/spring/base/services/user/UserRepository.java`:
    - Add `Optional<UserEntity> findByExternalId(String externalId);`
    - Add `Optional<UserEntity> findByUserName(String userName);`
- [x] In `src/main/java/com/openelements/spring/base/services/user/SystemUserInitializer.java`:
    - Extend the JDBC `INSERT_SQL` to set the new columns: `user_name = SystemUser.SUB ("system")`,
      `active = TRUE`, `external_id = NULL`.

**Acceptance criteria:**

- [x] Project builds.
- [x] Library integration tests still bootstrap the System User correctly with the new schema.
- [x] New `UserRepositorySchemaTest` (12 tests) covering all DB-integrity behaviours.
- [x] `AuditLogIntegrationTest.persistSystemUser()` inline SQL updated to set the new columns.

**Related behaviors:**

- *Database integrity — Inserting a UserEntity without userName is rejected*
- *Database integrity — externalId uniqueness is enforced*
- *Database integrity — Two users with NULL externalId can coexist*
- *Database integrity — sub uniqueness is unchanged*

---

## Step 2: `UserInformation` record + `AuthService` claim mapping

**Changes:**

- [x] `UserInformation` record extended to 6 fields.
- [x] `AuthService.getUserInformation()` builds `userName` via the fallback chain and mirrors
  `sub` into `externalId`. New private helper `resolveUserName` encodes the chain.
- [x] All existing direct constructions of `UserInformation` in test files updated (9 call
  sites across `UserServiceTest`, `UserServiceConcurrencyTest`, `AuditLogIntegrationTest`,
  `AuditLogEventListenerTest`, `CommentServiceIntegrationTest`).

**Acceptance criteria:**

- [x] Project compiles after the record signature change.
- [x] `AuthServiceTest.GetUserInformationTest` extended with 4 new fallback-chain tests
  plus an `externalIdMirrorsSubject` test, alongside the updated
  `shouldExtractUserInformationFromJwt` test (asserts `userName` + `externalId`).

**Related behaviors:**

- *UserEntity schema — userName falls back to email when preferred_username is missing*
- *UserEntity schema — userName falls back to sub when both preferred_username and email are
  missing*
- *UserEntity schema — userName falls back to a generated value when all canonical claims are
  missing*

---

## Step 3: `UserProvisioner` populates the new fields

**Changes:**

- [x] `UserProvisioner.provision(UserInformation)` now sets `userName`, `externalId`, and
  `active = true` on the new `UserEntity` in addition to the existing four fields.

**Acceptance criteria:**

- [x] Project builds; existing `UserServiceConcurrencyTest` (race-recovery integration test
  from spec 011) still passes — the new columns do not break the `REQUIRES_NEW` +
  `saveAndFlush` recovery path.

**Related behaviors:**

- *JIT-login correlation — Concurrent first logins for the same subject create exactly one
  row* (regression — was added by spec 011)

---

## Step 4: `UserService.getCurrentUserEntity()` — tiered lookup + active gate

**Discovered during implementation:** the tiered lookup as designed in the spec has a
fallback-mis-correlation hazard. When two distinct users share the same email-as-userName
fallback (because the IdP omitted `preferred_username`), the second user's `findByUserName`
lookup returns the first user's row and the second user effectively logs in as the first user
— a silent identity merger, not the spec's intended `IllegalStateException`. Fix added in this
step: a `requireUnambiguousMatch(...)` guard that throws `IllegalStateException` when the
matched row's existing `sub` differs from the JWT's `sub` (indicating the row belongs to a
different human). The guard fires for externalId / userName mismatches and is a no-op for
the `findBySub` happy path.

**Changes:**

- [x] In `src/main/java/com/openelements/spring/base/services/user/UserService.java`:
    - Replace the single `userRepository.findBySub(...)` with a tiered lookup:
        1. `findBySub(jwt.sub)` — if found, drop into the drift-sync path.
        2. else `findByExternalId(jwt.sub)` — if found, write `sub` onto the row, drop into
           drift-sync.
        3. else `findByUserName(jwt.preferredUsername)` — if found, write both `sub` *and*
           `externalId` onto the row, drop into drift-sync.
        4. else fall through to `userProvisioner.provision(userInformation)`.
    - Extend the drift-sync block to also diff `externalId` and `userName`, writing them through
      when missing (backfill).
    - **Active gate**: immediately before returning the resolved/created entity, check
      `entity.isActive()`. If `false`, throw `AccessDeniedException("User account is disabled")`.
      Note for the create branch: a freshly-provisioned row has `active = true` by definition
      (the provisioner sets it). The gate only fires for existing rows that were toggled off.
    - Update class-level Javadoc and `package-info.java` to describe the tiered lookup and the
      active gate.

**Acceptance criteria:**

- [x] Project builds; full test suite passes.
- [x] `UserServiceTest` (Mockito unit tests) is extended with:
    - First-login scenario: new user → `userProvisioner.provision(...)` called → entity has all
      four new fields populated.
    - Pre-foundation backfill: existing row with `sub` set, `externalId/userName` null →
      `findBySub` matches → drift-sync populates `externalId` and `userName`.
    - SCIM-pre-provisioned correlation: `findBySub` returns empty, `findByExternalId` returns the
      row → `sub` is written onto the row.
    - `userName`-fallback correlation: `findBySub` and `findByExternalId` both miss,
      `findByUserName` matches → both `sub` and `externalId` are written.
    - `findBySub` precedence: when both `findBySub` and `findByExternalId` would return rows,
      only `findBySub` runs.
    - Active-true: passes through normally.
    - Active-false (existing row): throws `AccessDeniedException` with message containing
      "disabled".
    - Inactive user on first JIT after externalId/userName lookup: row found, active=false →
      AccessDeniedException; `sub` is **not** written onto the row.
    - Drift on multiple fields runs as a single `save(user)` call.
    - Brand-new user only inserted after all three lookups miss.
- [x] `UserServiceConcurrencyTest` still passes — the concurrency-test from spec 011 keeps
  the race-recovery regression covered.
- [x] A new integration test in `UserServiceTest` or a new file
  `UserServiceActiveGateIntegrationTest` (Testcontainers): persists an inactive
  `UserEntity` directly and verifies that the next `getCurrentUserEntity()` for that subject
  throws `AccessDeniedException`.
- [x] Reactivation regression test: toggle a user `active=false → active=true` between two
  calls; the second call succeeds.
- [x] Two-users-same-email-no-preferred_username test: create user A with
  `userName = email`, then attempt to provision user B with the same email and no
  `preferred_username` → expect `DataIntegrityViolationException`/`IllegalStateException`
  (whichever Spring surfaces) → request fails as `500 Internal Server Error`. This test goes in
  `UserServiceConcurrencyTest` (uses real DB).

**Related behaviors:**

- *UserEntity schema — A new UserEntity has all identifying fields populated on creation*
- *UserEntity schema — Two users sharing the same email both get unique userNames*
- *JIT-login correlation — Pre-foundation user is backfilled on next login*
- *JIT-login correlation — Drift sync still updates name, email, avatarUrl*
- *JIT-login correlation — Drift sync runs in a single save even with multiple field changes*
- *JIT-login correlation — Lookup by sub takes precedence over lookup by externalId*
- *JIT-login correlation — Lookup by externalId is used only when lookup by sub misses*
- *JIT-login correlation — Lookup by userName is the last resort*
- *JIT-login correlation — Creating a brand-new user only happens after all three lookups miss*
- *Activation gate — Active user passes through every auth path*
- *Activation gate — Inactive user is refused on JWT-authenticated request*
- *Activation gate — Inactive user cannot be JIT-provisioned by their first JWT*
- *Activation gate — Reactivation restores access without token reissue*

---

## Step 5: `PrincipalDirectory` port + `UserEntityPrincipalDirectory` bean

**Status:** spec 010 has not landed — Step 5 shipped the port too. Spec 010 will take ownership
of `services/apitoken/PrincipalDirectory.java` when it merges; the interface shape will not
change.

**Changes:**

- [x] **Spec 010 has not landed:** shipped the port declaration in this spec. Created
  `src/main/java/com/openelements/spring/base/services/apitoken/PrincipalDirectory.java`
  (new package): interface with `Optional<ResolvedPrincipal> resolveUser(String subjectRef)`,
  nested record `ResolvedPrincipal(String subjectRef, boolean active, Set<String> roles,
  Set<String> groups, String displayName)`. Add Javadoc that describes the contract; mark the
  whole package as a temporary 012-shipped scaffolding that spec 010 supersedes.
  **If spec 010 has landed:** skip the port creation; just consume it from its real location.
- [x] Create `src/main/java/com/openelements/spring/base/services/user/UserEntityPrincipalDirectory.java`:
    - `@Component` implementing `PrincipalDirectory`.
    - Constructor takes a `UserRepository` (`@Autowired` constructor injection,
      `Objects.requireNonNull`).
    - `resolveUser(subjectRef)` queries `userRepository.findByExternalId(subjectRef)` and maps
      a present `UserEntity` to `ResolvedPrincipal(user.getExternalId(), user.isActive(),
    Set.of(), Set.of(), user.getName())`. Empty input → empty Optional.
    - Class-level Javadoc explains: `subjectRef` is the `externalId`, not `sub`; roles/groups
      return empty sets until SCIM Groups land; do not filter inactive users out at this layer
      — the introspector decides.

**Acceptance criteria:**

- [x] Project builds.
- [x] New `UserEntityPrincipalDirectoryTest` (Testcontainers integration test):
    - Active user with `externalId = "alice-ext"` → `Optional.of(ResolvedPrincipal(active=true,
    roles=empty, groups=empty, displayName=user.getName()))`.
    - Inactive user with `externalId = "bob-ext"` → `Optional.of(ResolvedPrincipal(active=false,
    ...))`. The directory does **not** filter; it returns what it sees.
    - Unknown `externalId` → `Optional.empty()`.
    - User with `sub = "S1"`, `externalId = "X1"`: `resolveUser("S1")` returns empty
      (`findByExternalId` is the query, not `findBySub`).

**Related behaviors:**

- *UserEntityPrincipalDirectory — Resolves an active user by externalId*
- *UserEntityPrincipalDirectory — Resolves an inactive user as inactive*
- *UserEntityPrincipalDirectory — Returns empty Optional for an unknown externalId*
- *UserEntityPrincipalDirectory — Lookup is by externalId, not by sub*

---

## Step 6: Documentation

**Changes:**

- [x] `README.md`:
    - Extend the "User Service" feature bullet to mention `externalId`, `userName`, and `active`.
    - Add a new "User Account Deactivation" bullet describing the `active=false` gate on the
      JWT chain (`AccessDeniedException → 403`) and, when spec 010 is present, on the
      opaque-token chain (`PrincipalDirectory.active() → BadOpaqueTokenException → 401`).
    - Extend the existing "Upgrade Notes" section with a 1.1.x → 1.2.x sub-section covering:
        - The new `UserInformation` record fields (source-level breaking change).
        - The Flyway migration template from `design.md` for consuming apps.
        - The fact that `active` defaults to `true` for all existing rows.
- [x] `.claude/conventions/project-specific/project-structure.md`:
    - Add `UserEntityPrincipalDirectory` to the `services/user/` listing.
    - Add the `services/apitoken/` package (if Step 5 shipped the port here as a temporary
      measure).
- [x] `TODO.md`:
    - If applicable, mark the "SCIM 2.0 Foundation" entry as superseded (spec 012 is the
      implementation of that entry).
- [x] Optional but recommended: a one-paragraph section in
  `services/user/package-info.java` describing how `PrincipalDirectory` consumers (the SCIM
  spec's eventual handlers, opaque-token validation in spec 010, etc.) read `UserEntity`.

**Acceptance criteria:**

- [x] README compiles (markdown-lint clean if linter is configured).
- [x] Upgrade-notes paragraph mentions all three observable changes: record fields, schema
  migration template, active default.
- [x] No stale references to the old single-`sub` lookup or "JIT-sync of roles" survive in
  documentation.

**Related behaviors:**

- (Documentation step — no behavior scenarios.)

---

## Behavior Coverage

All scenarios from `behaviors.md` are backend scenarios — the library has no UI. Two scenarios
are explicitly **deferred** to spec 010's implementation phase because they exercise the
opaque-token chain end-to-end, which requires `ApiTokenIntrospector` to exist.

| Scenario                                                                                           | Layer   | Covered in Step                                                                     |
|----------------------------------------------------------------------------------------------------|---------|-------------------------------------------------------------------------------------|
| UserEntity schema — A new UserEntity has all identifying fields populated on creation              | Backend | Step 4                                                                              |
| UserEntity schema — userName falls back to email when preferred_username is missing                | Backend | Step 2                                                                              |
| UserEntity schema — userName falls back to sub when both preferred_username and email are missing  | Backend | Step 2                                                                              |
| UserEntity schema — userName falls back to a generated value when all canonical claims are missing | Backend | Step 2                                                                              |
| UserEntity schema — Two users sharing the same email both get unique userNames                     | Backend | Step 4                                                                              |
| JIT-login correlation — Pre-foundation user is backfilled on next login                            | Backend | Step 4                                                                              |
| JIT-login correlation — Drift sync still updates name, email, avatarUrl                            | Backend | Step 4 (regression)                                                                 |
| JIT-login correlation — Drift sync runs in a single save even with multiple field changes          | Backend | Step 4                                                                              |
| JIT-login correlation — Lookup by sub takes precedence over lookup by externalId                   | Backend | Step 4                                                                              |
| JIT-login correlation — Lookup by externalId is used only when lookup by sub misses                | Backend | Step 4                                                                              |
| JIT-login correlation — Lookup by userName is the last resort                                      | Backend | Step 4                                                                              |
| JIT-login correlation — Creating a brand-new user only happens after all three lookups miss        | Backend | Step 4                                                                              |
| JIT-login correlation — Concurrent first logins for the same subject create exactly one row        | Backend | Step 3 (regression)                                                                 |
| Activation gate — Active user passes through every auth path                                       | Backend | Step 4                                                                              |
| Activation gate — Inactive user is refused on JWT-authenticated request                            | Backend | Step 4                                                                              |
| Activation gate — Inactive user is refused on opaque-token-authenticated request                   | Backend | Step 5 (via PrincipalDirectory unit test) + **deferred to spec 010 for end-to-end** |
| Activation gate — Inactive user cannot be JIT-provisioned by their first JWT                       | Backend | Step 4                                                                              |
| Activation gate — Reactivation restores access without token reissue                               | Backend | Step 4                                                                              |
| Activation gate — Reactivation does not un-expire opaque tokens                                    | Backend | **Deferred to spec 010** — depends on opaque-token expiry logic owned by 010        |
| Activation gate — Reactivation reinstates a previously-issued, still-valid opaque token            | Backend | **Deferred to spec 010** — depends on `ApiTokenIntrospector` existing               |
| UserEntityPrincipalDirectory — Resolves an active user by externalId                               | Backend | Step 5                                                                              |
| UserEntityPrincipalDirectory — Resolves an inactive user as inactive                               | Backend | Step 5                                                                              |
| UserEntityPrincipalDirectory — Returns empty Optional for an unknown externalId                    | Backend | Step 5                                                                              |
| UserEntityPrincipalDirectory — Lookup is by externalId, not by sub                                 | Backend | Step 5                                                                              |
| Database integrity — Inserting a UserEntity without userName is rejected                           | Backend | Step 1                                                                              |
| Database integrity — externalId uniqueness is enforced                                             | Backend | Step 1                                                                              |
| Database integrity — Two users with NULL externalId can coexist                                    | Backend | Step 1                                                                              |
| Database integrity — sub uniqueness is unchanged                                                   | Backend | Step 1 (regression)                                                                 |

Every scenario has a step. Two scenarios are flagged as deferred to spec 010 because they
require `ApiTokenIntrospector` end-to-end — the foundational pieces they need (active flag,
`PrincipalDirectory` bridge) are covered here in Step 5; the integration test for the full
flow will land with 010.

---

## Execution Options

- **Manual** — work through the steps as a developer; each step is small enough to land as a
  single commit. Steps 1–3 are mechanical schema/record work; Step 4 is the substantive one.
- **Guided** — implement step by step and ask for help on Step 4's tiered lookup logic and
  Step 5 if 010 has not yet landed (the temporary port-shipping path).
- **Automated** — Claude Code executes each step in order, ticks off the boxes here, and
  pauses for review before the next step. Recommended once the spec's design is locked.
