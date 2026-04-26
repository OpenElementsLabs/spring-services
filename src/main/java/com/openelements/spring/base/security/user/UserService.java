package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * calls detect drift in the {@code name}, {@code email} and {@code avatarUrl} JWT claims and update
 * the local row, so the mirror always reflects the most recent assertions of the identity
 * provider.
 */
@Service
@Transactional
public class UserService extends AbstractDbBackedDataService<UserEntity, UserDto> {

    private final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final AuthService authService;

    public UserService(
            final UserRepository userRepository,
            final ApplicationEventPublisher eventPublisher,
            final AuthService authService) {
        super(eventPublisher);
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    private synchronized UserEntity getCurrentUserEntity() {
        final UserInformation userInformation = authService.getUserInformation();
        return userRepository
                .findBySub(userInformation.id())
                .map(user -> {
                    boolean changed = false;
                    if (!Objects.equals(userInformation.name(), user.getName())) {
                        user.setName(userInformation.name());
                        changed = true;
                    }
                    if (!Objects.equals(userInformation.email(), user.getEmail())) {
                        user.setEmail(userInformation.email());
                        changed = true;
                    }
                    if (!Objects.equals(userInformation.avatarUrl(), user.getAvatarUrl())) {
                        user.setAvatarUrl(userInformation.avatarUrl());
                        changed = true;
                    }
                    if (changed) {
                        LOG.debug("Updating user {}: {}", user.id(), user);
                        return userRepository.save(user);
                    } else {
                        return user;
                    }
                })
                .orElseGet(() -> {
                    try {
                        LOG.debug("First login for user {}", userInformation.id());
                        final UserEntity entity = new UserEntity();
                        entity.setSub(userInformation.id());
                        entity.setName(userInformation.name());
                        entity.setEmail(userInformation.email());
                        entity.setAvatarUrl(userInformation.avatarUrl());
                        return userRepository.save(entity);
                    } catch (final DataIntegrityViolationException e) {
                        LOG.warn("Error in storing user entity. Will try to find it instead.");
                        return userRepository.findBySub(userInformation.id()).orElseThrow(() -> new IllegalStateException("Error in storing user entity", e));
                    }
                });
    }

    /**
     * Returns the profile of the user that owns the current request.
     *
     * <p>Provisions the local mirror on first access and refreshes the cached {@code name}, {@code
     * email} and {@code avatarUrl} fields from the JWT claims if they have drifted.
     *
     * @return the current user's profile
     * @throws IllegalStateException if no JWT is bound to the current request
     */
    public UserDto getCurrentUser() {
        return UserDto.fromEntity(getCurrentUserEntity());
    }

    @Override
    protected @NonNull UserEntity createDetachedEntity() {
        return new UserEntity();
    }

    @Override
    protected void preSave(@NonNull UserDto data) {
        throw new IllegalStateException(
                "User data should never be changed since it is based on external system");
    }

    @Override
    protected void updateEntity(@NonNull UserEntity entity, @NonNull UserDto data) {
        entity.setName(data.name());
        entity.setEmail(data.email());
    }

    @Override
    protected @NonNull UserDto toData(@NonNull UserEntity entity) {
        return new UserDto(
                entity.id(),
                entity.getName(),
                entity.getEmail(),
                entity.getAvatarUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    @Override
    protected @NonNull EntityRepository<UserEntity> getRepository() {
        return userRepository;
    }
}
