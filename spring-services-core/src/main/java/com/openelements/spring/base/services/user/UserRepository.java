package com.openelements.spring.base.services.user;

import com.openelements.spring.base.data.EntityRepository;
import java.util.Optional;

/** Spring Data repository for {@link UserEntity}. */
public interface UserRepository extends EntityRepository<UserEntity> {

  /**
   * Looks up a user by the OAuth2 subject identifier ({@code sub} JWT claim).
   *
   * @param sub the OAuth2 subject identifier
   * @return the matching user, or an empty {@link Optional} if no local mirror has been provisioned
   *     yet
   */
  Optional<UserEntity> findBySub(String sub);

  /**
   * Looks up a user by the stable IdP-side identifier ({@code externalId}).
   *
   * <p>The opaque-token introspector and the JIT-login correlation path both consult this lookup
   * when a {@code sub}-based match misses — most commonly when a SCIM-pre-provisioned row exists
   * but the user has not yet logged in interactively (so {@code sub} is still {@code null}).
   *
   * @param externalId the IdP-side identifier
   * @return the matching user, or empty if no row carries this {@code externalId}
   */
  Optional<UserEntity> findByExternalId(String externalId);

  /**
   * Looks up a user by the unique business-key handle ({@code userName}).
   *
   * <p>Used as the last-resort correlation step in the JIT-login flow, for the edge case where an
   * IdP uses different identifiers for OIDC and SCIM (so neither {@code sub} nor {@code
   * externalId} matches the pre-provisioned row).
   *
   * @param userName the user-name handle
   * @return the matching user, or empty if no row carries this {@code userName}
   */
  Optional<UserEntity> findByUserName(String userName);
}
