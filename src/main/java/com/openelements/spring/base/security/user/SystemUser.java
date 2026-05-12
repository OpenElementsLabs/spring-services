package com.openelements.spring.base.security.user;

import java.util.UUID;

/**
 * Constants identifying the dedicated <em>System User</em> — a synthetic {@link UserEntity} row
 * used as the actor reference for operations that have no authenticated principal (scheduled
 * tasks, startup data loads, API-key requests, etc.).
 *
 * <p>The System User row is created on application startup by {@link SystemUserInitializer} and
 * is guaranteed to exist for the lifetime of the application.
 */
public final class SystemUser {

  /**
   * Reserved nil UUID per RFC 9562. Hibernate's {@link jakarta.persistence.GenerationType#UUID}
   * produces v4 (random) UUIDs, so a real user can never collide with this value in practice.
   */
  public static final UUID ID = new UUID(0L, 0L);

  /**
   * Reserved OAuth2 {@code sub} sentinel value. No real identity provider should issue this
   * subject; if a consumer's IdP does, the System User row prevents the conflict from being
   * silently provisioned as a real user since the row already occupies the unique {@code sub}.
   */
  public static final String SUB = "system";

  /** Display name of the System User. */
  public static final String NAME = "System";

  private SystemUser() {}
}
