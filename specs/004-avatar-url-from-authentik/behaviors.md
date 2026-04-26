# Behaviors: Avatar URL from authentik

## Avatar URL synchronization

### Avatar URL is stored on first login

- **Given** a user authenticates for the first time with a JWT containing `avatar: "https://auth.example.com/media/avatars/user1.jpg"`
- **When** `getCurrentUser()` is called
- **Then** a new `UserEntity` is created with `avatarUrl` set to `"https://auth.example.com/media/avatars/user1.jpg"`

### Avatar URL is updated when it changes in authentik

- **Given** a user exists in the database with `avatarUrl: "https://auth.example.com/media/avatars/old.jpg"`
- **When** the user authenticates with a JWT containing `avatar: "https://auth.example.com/media/avatars/new.jpg"`
- **Then** the `UserEntity.avatarUrl` is updated to `"https://auth.example.com/media/avatars/new.jpg"` and persisted

### Avatar URL is not updated when unchanged

- **Given** a user exists in the database with `avatarUrl: "https://auth.example.com/media/avatars/same.jpg"`
- **When** the user authenticates with a JWT containing `avatar: "https://auth.example.com/media/avatars/same.jpg"`
- **Then** no database write occurs

### Avatar URL is set to null when JWT claim is absent

- **Given** a user exists in the database with `avatarUrl: "https://auth.example.com/media/avatars/old.jpg"`
- **When** the user authenticates with a JWT that does not contain an `avatar` claim
- **Then** the `UserEntity.avatarUrl` is set to `null` and persisted

### Avatar URL is set to null when JWT claim is empty string

- **Given** a user exists in the database with `avatarUrl: "https://auth.example.com/media/avatars/old.jpg"`
- **When** the user authenticates with a JWT containing `avatar: ""`
- **Then** the `UserEntity.avatarUrl` is set to `null` and persisted

### First login without avatar

- **Given** a user authenticates for the first time with a JWT that does not contain an `avatar` claim
- **When** `getCurrentUser()` is called
- **Then** a new `UserEntity` is created with `avatarUrl` set to `null`

## UserInformation

### UserInformation includes avatar URL

- **Given** a JWT with claims `sub: "abc123"`, `name: "Alice"`, `email: "alice@example.com"`, `avatar: "https://auth.example.com/avatar.jpg"`
- **When** `AuthService.getUserInformation()` is called
- **Then** the returned `UserInformation` has `avatarUrl` equal to `"https://auth.example.com/avatar.jpg"`

### UserInformation with missing avatar claim

- **Given** a JWT with claims `sub: "abc123"`, `name: "Alice"`, `email: "alice@example.com"` and no `avatar` claim
- **When** `AuthService.getUserInformation()` is called
- **Then** the returned `UserInformation` has `avatarUrl` equal to `null`

### UserInformation normalizes blank avatar to null

- **Given** a JWT with claims `sub: "abc123"`, `name: "Alice"`, `email: "alice@example.com"`, `avatar: "  "`
- **When** `AuthService.getUserInformation()` is called
- **Then** the returned `UserInformation` has `avatarUrl` equal to `null`

## UserDto

### UserDto exposes avatar URL

- **Given** a `UserEntity` with `avatarUrl: "https://auth.example.com/avatar.jpg"`
- **When** `UserDto.fromEntity()` is called
- **Then** the returned `UserDto` has `avatarUrl` equal to `"https://auth.example.com/avatar.jpg"`

### UserDto exposes null when no avatar URL

- **Given** a `UserEntity` with `avatarUrl: null`
- **When** `UserDto.fromEntity()` is called
- **Then** the returned `UserDto` has `avatarUrl` equal to `null`

## Removal of manual avatar upload

### Upload method is removed

- **Given** the library is updated to this version
- **When** a consuming application references `UserService.updateAvatarForCurrentUser(ImageData)`
- **Then** a compile error occurs — the method no longer exists

### Download method is removed

- **Given** the library is updated to this version
- **When** a consuming application references `UserService.getAvatarOfCurrentUser()`
- **Then** a compile error occurs — the method no longer exists

### Delete method is removed

- **Given** the library is updated to this version
- **When** a consuming application references `UserService.deleteAvatarOfCurrentUser()`
- **Then** a compile error occurs — the method no longer exists

## EntityWithImage decoupling

### UserEntity no longer implements EntityWithImage

- **Given** the library is updated to this version
- **When** code attempts to cast or assign a `UserEntity` to `EntityWithImage`
- **Then** a compile error occurs — `UserEntity` no longer implements that interface

### EntityWithImage interface remains available

- **Given** the library is updated to this version
- **When** another entity class implements `EntityWithImage`
- **Then** it compiles and works as before — the interface is unchanged
