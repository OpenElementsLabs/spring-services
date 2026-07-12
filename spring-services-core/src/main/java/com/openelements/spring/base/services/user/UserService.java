package com.openelements.spring.base.services.user;

import com.openelements.spring.base.data.AbstractDbBackedDataService;
import com.openelements.spring.base.data.EntityRepository;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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
 * the local row, so the mirror always reflects the most recent assertions of the identity provider.
 */
@Service
@Transactional
public class UserService extends AbstractDbBackedDataService<UserEntity, UserDto> {

    private final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final AuthService authService;

    private final UserProvisioner userProvisioner;

    public UserService(
            final UserRepository userRepository,
            final ApplicationEventPublisher eventPublisher,
            final AuthService authService,
            final UserProvisioner userProvisioner) {
        super(eventPublisher);
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.userProvisioner =
                Objects.requireNonNull(userProvisioner, "userProvisioner must not be null");
    }

    /**
     * Returns the managed {@link UserEntity} for the user that owns the current request.
     *
     * <p>Resolves the local mirror through a three-tier lookup so that SCIM-pre-provisioned rows
     * (with {@code externalId} or {@code userName} set but {@code sub} still {@code null}) are
     * found and adopted on the first interactive login:
     *
     * <ol>
     *   <li>{@code findBySub(jwt.sub)} — the historical fast path for already-known users.
     *   <li>{@code findByExternalId(jwt.sub)} — picks up SCIM-pre-provisioned rows that share the
     *       IdP-side identifier with the JWT {@code sub}.
     *   <li>{@code findByUserName(jwt.preferredUsername)} — last-resort correlation for IdPs that
     *       use different identifiers for OIDC and SCIM.
     * </ol>
     *
     * <p>If all three lookups miss, the row is created via {@link
     * UserProvisioner#provision(UserInformation)} in its own {@code REQUIRES_NEW} transaction so
     * that a concurrent first-login race can be caught and recovered without poisoning the outer
     * transaction (see the package documentation's
     * <a href="package-summary.html#concurrency">"Concurrency and first-login race recovery"</a>
     * section).
     *
     * <p>After resolution the entity is checked for {@link UserEntity#isActive()}. A deactivated
     * user causes an {@link AccessDeniedException} → HTTP {@code 403}. The check fires
     * <em>before</em> any drift-sync writes, so a deactivated SCIM-pre-provisioned row cannot be
     * accidentally bound to a {@code sub} via the JIT flow.
     *
     * <p>Drift sync overwrites {@code name}, {@code email} and {@code avatarUrl} whenever the JWT
     * claim disagrees with the persisted value. {@code sub}, {@code externalId} and {@code
     * userName} are opportunistically <em>backfilled</em> only when currently {@code null} — they
     * are stable identifiers and are not overwritten by later JWTs.
     *
     * @return the managed entity for the current user
     * @throws IllegalStateException if no JWT is bound to the current request
     * @throws AccessDeniedException if the resolved {@link UserEntity} has {@code active = false}
     */
    public UserEntity getCurrentUserEntity() {
        final UserInformation userInformation =
                authService
                        .getUserInformation()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No JWT bound to current request — getCurrentUserEntity"
                                                    + " may only be called from JWT-authenticated request threads."));

        final Optional<UserEntity> existing =
                userRepository
                        .findBySub(userInformation.id())
                        .or(() -> userRepository.findByExternalId(userInformation.externalId()))
                        .or(() -> userRepository.findByUserName(userInformation.userName()));

        if (existing.isPresent()) {
            final UserEntity user = existing.get();
            requireUnambiguousMatch(user, userInformation);
            requireActive(user);
            return syncDriftAndBackfill(user, userInformation);
        }

        try {
            LOG.debug("First login for user {}", userInformation.id());
            final UserEntity provisioned = userProvisioner.provision(userInformation);
            requireActive(provisioned);
            return provisioned;
        } catch (final DataIntegrityViolationException e) {
            LOG.warn(
                    "Concurrent first-login for user {} — re-fetching the row another thread"
                            + " inserted.",
                    userInformation.id());
            final UserEntity recovered =
                    userRepository
                            .findBySub(userInformation.id())
                            .orElseThrow(
                                    () -> new IllegalStateException("Error in storing user entity", e));
            requireActive(recovered);
            return recovered;
        }
    }

    /**
     * Drift-syncs the volatile claims ({@code name}, {@code email}, {@code avatarUrl}) and
     * back-fills the stable identifiers ({@code sub}, {@code externalId}, {@code userName}) only
     * when they are currently {@code null} on the persisted row.
     *
     * <p>Saves the entity in one call when any field changed; otherwise the existing instance is
     * returned untouched.
     */
    private UserEntity syncDriftAndBackfill(
            final UserEntity user, final UserInformation info) {
        boolean changed = false;

        if (!Objects.equals(info.name(), user.getName())) {
            user.setName(info.name());
            changed = true;
        }
        if (!Objects.equals(info.email(), user.getEmail())) {
            user.setEmail(info.email());
            changed = true;
        }
        if (!Objects.equals(info.avatarUrl(), user.getAvatarUrl())) {
            user.setAvatarUrl(info.avatarUrl());
            changed = true;
        }

        // Stable identifiers — backfill only, never overwrite.
        if (user.getSub() == null && info.id() != null) {
            user.setSub(info.id());
            changed = true;
        }
        if (user.getExternalId() == null && info.externalId() != null) {
            user.setExternalId(info.externalId());
            changed = true;
        }
        if (user.getUserName() == null && info.userName() != null) {
            user.setUserName(info.userName());
            changed = true;
        }

        if (changed) {
            LOG.debug("Updating user {}: {}", user.id(), user);
            return userRepository.save(user);
        }
        return user;
    }

    private static void requireActive(final UserEntity user) {
        if (!user.isActive()) {
            throw new AccessDeniedException("User account is disabled");
        }
    }

    /**
     * Guards against fallback-lookup mis-correlation. A row whose {@code sub} is already set to
     * a value different from the JWT's {@code sub} cannot be the same human — even if the
     * {@code externalId} or {@code userName} lookup matched. The most common trigger is two
     * users sharing the same email, both falling back to {@code email} as their {@code userName}
     * because the IdP omitted {@code preferred_username}.
     */
    private static void requireUnambiguousMatch(
            final UserEntity user, final UserInformation info) {
        if (user.getSub() != null && !user.getSub().equals(info.id())) {
            throw new IllegalStateException(
                    "Matched UserEntity "
                            + user.id()
                            + " has sub="
                            + user.getSub()
                            + " but the request carries sub="
                            + info.id()
                            + ". This indicates two distinct identities collided on externalId or"
                            + " userName — typically because the IdP omitted preferred_username and two"
                            + " users share the same email fallback. The IdP must assert"
                            + " preferred_username to disambiguate.");
        }
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
