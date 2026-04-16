package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.ImageData;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Application service that bridges the JWT-authenticated subject and the local user-profile mirror.
 *
 * <p>The service deliberately does not expose generic CRUD by id — every operation is implicitly
 * scoped to the <em>current</em> user as identified by the JWT {@code sub} claim. This keeps user
 * data access aligned with the authentication model: a request can only ever read or modify the
 * profile of the user it was authenticated as.
 *
 * <h2>Lazy provisioning</h2>
 *
 * <p>The first call for a previously unknown subject creates the {@link UserEntity}. Subsequent
 * calls detect drift in the {@code name} / {@code email} JWT claims and update the local row, so
 * the mirror always reflects the most recent assertions of the identity provider.
 *
 * <h2>Avatar handling</h2>
 *
 * <p>Avatar uploads accept {@code image/jpeg} and {@code image/png} up to 2 MB. The image bytes are
 * stored on the user row itself and served back through {@link #getAvatarOfCurrentUser()}.
 */
@Service
@Transactional
public class UserService {

  private static final List<String> ALLOWED_AVATAR_TYPES = List.of("image/jpeg", "image/png");
  private static final int MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final AuthService authService;

  public UserService(
      final UserRepository userRepository,
      final ApplicationEventPublisher eventPublisher,
      final AuthService authService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    this.authService = Objects.requireNonNull(authService, "authService must not be null");
  }

  private UserEntity getCurrentUserEntity() {
    final UserInformation userInformation = authService.getUserInformation();
    return userRepository
        .findBySub(userInformation.id())
        .map(
            existing -> {
              boolean changed = false;
              if (!Objects.equals(userInformation.name(), existing.getName())) {
                existing.setName(userInformation.name());
                changed = true;
              }
              if (!Objects.equals(userInformation.email(), existing.getEmail())) {
                existing.setEmail(userInformation.email());
                changed = true;
              }
              return changed ? userRepository.saveAndFlush(existing) : existing;
            })
        .orElseGet(
            () -> {
              final UserEntity entity = new UserEntity();
              entity.setSub(userInformation.id());
              entity.setName(userInformation.name());
              entity.setEmail(userInformation.email());
              final UserEntity saved = userRepository.saveAndFlush(entity);
              return saved;
            });
  }

  /**
   * Returns the profile of the user that owns the current request.
   *
   * <p>Provisions the local mirror on first access and refreshes the cached {@code name} / {@code
   * email} fields from the JWT claims if they have drifted.
   *
   * @return the current user's profile
   * @throws IllegalStateException if no JWT is bound to the current request
   */
  public UserDto getCurrentUser() {
    return UserDto.fromEntity(getCurrentUserEntity());
  }

  /**
   * Replaces the avatar of the current user.
   *
   * @param data the raw image bytes; must be non-empty and at most 2 MB
   * @param contentType the MIME type; must be {@code image/jpeg} or {@code image/png}
   * @return the updated profile (with {@code hasAvatar = true})
   * @throws ResponseStatusException {@code 400 Bad Request} if {@code data} is missing, too large
   *     or has an unsupported {@code contentType}
   */
  public UserDto uploadAvatarForCurrentUser(final byte[] data, final String contentType) {
    if (data == null || data.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image data provided");
    }
    if (data.length > MAX_AVATAR_SIZE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File too large (max 2MB)");
    }
    if (!ALLOWED_AVATAR_TYPES.contains(contentType)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid content type: " + contentType + ". Allowed: " + ALLOWED_AVATAR_TYPES);
    }
    final UserEntity user = getCurrentUserEntity();
    user.setAvatar(data);
    user.setAvatarContentType(contentType);
    return UserDto.fromEntity(userRepository.saveAndFlush(user));
  }

  /**
   * Returns the current user's avatar.
   *
   * @return the avatar image bytes together with their MIME content type
   * @throws ResponseStatusException {@code 404 Not Found} if no avatar has been uploaded
   */
  public ImageData getAvatarOfCurrentUser() {
    final UserEntity user = getCurrentUserEntity();
    if (user.getAvatar() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No avatar set");
    }
    return new ImageData(user.getAvatar(), user.getAvatarContentType());
  }

  /**
   * Removes the avatar of the current user. No-op (but persists a flushed write) if no avatar was
   * set.
   */
  public void deleteAvatarOfCurrentUser() {
    final UserEntity user = getCurrentUserEntity();
    user.setAvatar(null);
    user.setAvatarContentType(null);
    userRepository.saveAndFlush(user);
  }
}
