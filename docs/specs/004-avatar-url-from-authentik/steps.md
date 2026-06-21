# Implementation Steps: Avatar URL from authentik

## Step 1: Extend `UserInformation` and `AuthService` for the avatar claim

- [x] Add `String avatarUrl` (nullable) as the fourth component of the `UserInformation` record in `src/main/java/com/openelements/spring/base/security/UserInformation.java`. Update Javadoc to describe the new field.
- [x] In `AuthService.getUserInformation()` (`src/main/java/com/openelements/spring/base/security/AuthService.java`), read `jwt.getClaimAsString("avatar")`, normalize blank/null to `null`, and pass the result to the `UserInformation` constructor. Update Javadoc to mention the `avatar` claim.
- [x] Update `AuthServiceTest`:
    - Extend `shouldExtractUserInformationFromJwt` to assert `info.avatarUrl()` equals the JWT `avatar` claim.
    - Add `shouldReturnNullAvatarUrlWhenAvatarClaimMissing`.
    - Add `shouldReturnNullAvatarUrlWhenAvatarClaimIsBlank` (whitespace-only string).

**Acceptance criteria:**
- [x] `./mvnw compile` succeeds.
- [x] `./mvnw test -Dtest=AuthServiceTest` passes (all three new/extended scenarios).

**Related behaviors:** UserInformation includes avatar URL; UserInformation with missing avatar claim; UserInformation normalizes blank avatar to null.

---

## Step 2: Replace binary avatar fields on `UserEntity`

- [x] In `src/main/java/com/openelements/spring/base/security/user/UserEntity.java`:
    - Remove `implements EntityWithImage` and the `EntityWithImage` import.
    - Remove the `avatar` (`byte[]`) and `avatarContentType` fields plus their getters/setters.
    - Remove the `getRawImageData` / `setRawImageData` / `getContentType` / `setContentType` overrides.
    - Add `@Column(name = "avatar_url", length = 2048) private String avatarUrl;` (nullable) plus standard getter/setter.
    - Update the class-level Javadoc: drop the lazy-avatar-bytes paragraph and instead mention that `avatarUrl` is kept in sync with the JWT `avatar` claim.

**Acceptance criteria:**
- [x] `./mvnw compile` succeeds (verified together with step 3).

**Related behaviors:** UserEntity no longer implements EntityWithImage; EntityWithImage interface remains available (verified by no changes to the interface file).

---

## Step 3: Sync `avatarUrl` and remove the manual avatar API on `UserService`

- [x] In `src/main/java/com/openelements/spring/base/security/user/UserService.java`:
    - Remove `updateAvatarForCurrentUser(ImageData)`, `getAvatarOfCurrentUser()`, and `deleteAvatarOfCurrentUser()`.
    - Drop the now-unused `import com.openelements.spring.base.data.image.ImageData;` and the `Optional` import.
    - In `getCurrentUserEntity()`:
        - When the user already exists, also compare `userInformation.avatarUrl()` against `user.getAvatarUrl()` and update + flag `changed = true` on mismatch.
        - When provisioning a new user, set `entity.setAvatarUrl(userInformation.avatarUrl())`.
    - In `toData()`, replace `entity.getAvatar() != null` with `entity.getAvatarUrl()`.
    - Update class-level Javadoc: remove the "Avatar handling" section that describes the upload pipeline; mention `avatarUrl` alongside `name`/`email` in the lazy-provisioning paragraph.

**Acceptance criteria:**
- [x] `./mvnw compile` succeeds.

**Related behaviors:** Avatar URL is stored on first login; Avatar URL is updated when it changes in authentik; Avatar URL is not updated when unchanged; Avatar URL is set to null when JWT claim is absent; Avatar URL is set to null when JWT claim is empty string; First login without avatar; Upload/download/delete methods are removed.

---

## Step 4: Update `UserDto`

- [x] In `src/main/java/com/openelements/spring/base/security/user/UserDto.java`:
    - Replace the `boolean hasAvatar` record component with `String avatarUrl`.
    - Update `fromEntity()` to read `entity.getAvatarUrl()`.
    - Update the class-level Javadoc to describe direct rendering by clients without proxying.

**Acceptance criteria:**
- [x] `./mvnw compile` succeeds.

**Related behaviors:** UserDto exposes avatar URL; UserDto exposes null when no avatar URL.

---

## Step 5: Rewrite the affected unit tests

- [x] In `src/test/java/com/openelements/spring/base/security/user/UserDtoTest.java`:
    - Replace `shouldConvertFromEntityWithAvatar` and `shouldConvertFromEntityWithoutAvatar` to set/assert `avatarUrl` directly.
- [x] In `src/test/java/com/openelements/spring/base/security/user/UserServiceTest.java`:
    - Update `setupAuth` and `createExistingUser` to take an additional `avatarUrl` parameter.
    - Replace the broken `userRepository.saveAndFlush(...)` mocks with `userRepository.save(...)` (matches the production code).
    - Add explicit avatar-sync scenarios in `GetCurrentUserTest` (`shouldStoreAvatarUrlOnFirstLogin`, `shouldUpdateAvatarUrlWhenChanged`, `shouldNotSaveWhenAvatarUrlUnchanged`, `shouldClearAvatarUrlWhenJwtClaimMissing`, `shouldCreateUserWithoutAvatarUrlWhenClaimMissing`).
    - Delete the `UploadAvatarTest`, `GetAvatarTest`, and `DeleteAvatarTest` nested classes.
- [x] Fix `ApiKeyDataServiceIntegrationTest` constructor call to use the new `UserDto` signature (`null` instead of `false`).

**Acceptance criteria:**
- [x] `./mvnw test -Dtest='UserServiceTest,UserDtoTest,AuthServiceTest'` passes (24 / 24, zero failures, zero errors).

**Related behaviors:** All scenarios listed in steps 1, 3, and 4 now have explicit tests.

---

## Step 6: Update project documentation

- [x] In `.claude/conventions/project-specific/project-features.md`, rewrite the "User Management" bullet to reflect avatar URL sync and removal of binary upload.
- [x] In `README.md`, rewrite the "User Service" bullet for avatar URL sync.
- [x] No changes needed in `project-architecture.md`, `project-structure.md`, `project-tech.md`, or `CLAUDE.md`.

**Acceptance criteria:**
- [x] No references to manual avatar upload, content-type validation, or 2 MB limits remain in `README.md` or the project-specific docs.

---

## Behavior Coverage

| Scenario                                              | Layer   | Covered in Step  |
|-------------------------------------------------------|---------|------------------|
| Avatar URL is stored on first login                   | Backend | 3, 5             |
| Avatar URL is updated when it changes in authentik    | Backend | 3, 5             |
| Avatar URL is not updated when unchanged              | Backend | 3, 5             |
| Avatar URL is set to null when JWT claim is absent    | Backend | 3, 5             |
| Avatar URL is set to null when JWT claim is empty     | Backend | 1, 3, 5          |
| First login without avatar                            | Backend | 3, 5             |
| UserInformation includes avatar URL                   | Backend | 1                |
| UserInformation with missing avatar claim             | Backend | 1                |
| UserInformation normalizes blank avatar to null       | Backend | 1                |
| UserDto exposes avatar URL                            | Backend | 4, 5             |
| UserDto exposes null when no avatar URL               | Backend | 4, 5             |
| Upload method is removed                              | Backend | 3 (compile-time) |
| Download method is removed                            | Backend | 3 (compile-time) |
| Delete method is removed                              | Backend | 3 (compile-time) |
| UserEntity no longer implements EntityWithImage       | Backend | 2 (compile-time) |
| EntityWithImage interface remains available           | Backend | 2 (untouched)    |
