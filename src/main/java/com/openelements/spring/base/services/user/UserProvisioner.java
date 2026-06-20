package com.openelements.spring.base.services.user;

import com.openelements.spring.base.security.UserInformation;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper bean that inserts a fresh {@link UserEntity} in its own {@link Propagation#REQUIRES_NEW}
 * transaction, so that a unique-constraint conflict with a concurrent first-login is detected
 * <em>inside</em> the inner transaction and rolled back there — leaving the caller's outer
 * transaction alive for the recovery {@code findBySub} retry in {@link
 * UserService#getCurrentUserEntity()}.
 *
 * <p>For the full design rationale — why the {@code users.sub} unique index is the actual
 * coordinator across threads and instances, and how the three layers (DB lock, inner transaction,
 * outer-tx retry) fit together — see the
 * <a href="package-summary.html#concurrency">"Concurrency and first-login race recovery"</a>
 * section of this package's documentation.
 *
 * <h2>Why this is a separate bean, not a private method on {@code UserService}</h2>
 *
 * <p>Spring applies {@code @Transactional} via AOP proxies. A bean calling its own {@code
 * @Transactional} method bypasses the proxy — the JVM resolves the call against the concrete
 * class, not the proxy wrapper — so Spring never gets a chance to start an inner transaction.
 * If this {@code provision(...)} method lived as a private (or even public) method on {@code
 * UserService} and were invoked through {@code this}, {@code REQUIRES_NEW} would silently degrade
 * into "reuse the caller's transaction", and the entire race-recovery scheme would collapse: the
 * constraint violation would mark the outer transaction as rollback-only, the recovery {@code
 * findBySub} would fail with {@code "current transaction is aborted"}, and concurrent
 * first-logins would surface 500 errors to callers.
 *
 * <p>Keeping the insert in this distinct bean guarantees that the call between {@code UserService}
 * and {@code UserProvisioner} goes through the {@code @Transactional} proxy, which is what makes
 * the inner-vs-outer transaction split real.
 */
@Component
public class UserProvisioner {

  private final UserRepository userRepository;

  public UserProvisioner(final UserRepository userRepository) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
  }

  /**
   * Inserts a new {@link UserEntity} for the given {@link UserInformation} in a fresh transaction.
   *
   * <p>{@code saveAndFlush} forces the {@code INSERT} to be issued before this method returns, so
   * a unique-constraint violation surfaces here as a {@link
   * org.springframework.dao.DataIntegrityViolationException} rather than at the caller's
   * transaction-commit time, where it would be unrecoverable.
   *
   * <p>On a race-loss, the caller (see {@link UserService#getCurrentUserEntity()}) catches the
   * {@code DataIntegrityViolationException} and re-queries {@code findBySub} from its still-alive
   * outer transaction.
   *
   * @param userInformation the JWT-derived user information
   * @return the persisted entity, committed in the inner transaction
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UserEntity provision(final UserInformation userInformation) {
    final UserEntity entity = new UserEntity();
    entity.setSub(userInformation.id());
    entity.setName(userInformation.name());
    entity.setEmail(userInformation.email());
    entity.setAvatarUrl(userInformation.avatarUrl());
    return userRepository.saveAndFlush(entity);
  }
}
