package com.openelements.spring.base.services.scim;

import java.util.UUID;

/**
 * Constants identifying the reserved <em>SCIM service principal</em> — a synthetic, permanently
 * inactive {@link com.openelements.spring.base.services.user.UserEntity} row used as the audit actor
 * for every write performed over the SCIM API.
 *
 * <p>It mirrors the System user of spec 008 but is distinct from it, so that SCIM-driven audit
 * entries are unambiguously attributable to "SCIM" and told apart from internal System actions and
 * from human users. Its fixed UUID is chosen not to collide with the System user's reserved nil
 * UUID.
 */
public final class ScimServicePrincipal {

  /** Reserved fixed UUID of the SCIM service principal (distinct from the System user's nil UUID). */
  public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000cf");

  /** Reserved {@code userName} of the SCIM service principal. */
  public static final String USER_NAME = "scim";

  /** Display name of the SCIM service principal. */
  public static final String NAME = "SCIM Provisioning";

  private ScimServicePrincipal() {}
}
