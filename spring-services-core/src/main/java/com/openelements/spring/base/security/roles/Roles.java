package com.openelements.spring.base.security.roles;

/**
 * Predefined role names and the Spring Security authority prefix.
 *
 * <p>The constants fall into three categories:
 *
 * <ul>
 *   <li><b>Business roles</b> ({@link #ROLE_APP_ADMIN} and the six that follow) — issued by the
 *       identity provider through the JWT {@code roles} claim.
 *   <li><b>Mechanism markers</b> ({@link #ROLE_API_KEY}, {@link #ROLE_ANONYMOUS}) — issued by a
 *       filter rather than the identity provider, to describe how the caller reached the
 *       application.
 *   <li><b>The authority prefix</b> ({@link #AUTHORITY_PREFIX}) — the {@code "ROLE_"} literal that
 *       Spring Security prepends to a role name when it is exposed as a {@code GrantedAuthority}.
 * </ul>
 *
 * <p>Every role constant holds the role name <em>without</em> the {@link #AUTHORITY_PREFIX}, so it
 * can be compared directly against the values returned by {@link
 * com.openelements.spring.base.security.AuthService#getRoles()}.
 */
public final class Roles {

  private Roles() {}

  /**
   * Prefix that Spring Security prepends to a role name when the role is exposed as a {@code
   * org.springframework.security.core.GrantedAuthority}. The role constants in this class hold the
   * name <em>without</em> this prefix, so they can be compared directly against the values returned
   * by {@link com.openelements.spring.base.security.AuthService#getRoles()}. It is also the single
   * home of the {@code "ROLE_"} literal for the library's own authority producers.
   */
  public static final String AUTHORITY_PREFIX = "ROLE_";

  /**
   * Role of a caller authenticated with an API key. Issued by {@code ApiKeyAuthenticationFilter},
   * never by the identity provider — it is the token's only authority. Because the JWT {@code roles}
   * claim is mapped unfiltered, an identity-provider role literally named {@code API_KEY} would also
   * surface under this name; the value therefore says <em>which</em> role the caller carries, not
   * <em>how</em> the caller authenticated.
   */
  public static final String ROLE_API_KEY = "API_KEY";

  /**
   * Role of an unauthenticated caller on a {@code permitAll} path. Issued by Spring Security's
   * {@code AnonymousAuthenticationFilter}, which runs on the default chain.
   *
   * <p>Unlike {@link #ROLE_API_KEY}, this value cannot be wired to its producer: {@code
   * "ANONYMOUS"} is Spring Security's default anonymous authority, overridable by any consumer via
   * {@code http.anonymous(a -> a.authorities(...))}. If a consumer reconfigures it, this constant no
   * longer describes that application.
   */
  public static final String ROLE_ANONYMOUS = "ANONYMOUS";

  /** Predefined role for administrators. */
  public static final String ROLE_APP_ADMIN = "APP-ADMIN";

  /** Predefined role for regular users. */
  public static final String ROLE_APP_USER = "APP-USER";

  /** Predefined role for IT administrators. */
  public static final String ROLE_IT_ADMIN = "IT-ADMIN";

  /** Predefined role for internal employees. */
  public static final String ROLE_EMPLOYEE = "EMPLOYEE";

  /** Predefined role for external, non-employee users. */
  public static final String ROLE_EXTERNAL = "EXTERNAL";

  /** Predefined role for back-office staff. */
  public static final String ROLE_BACKOFFICE = "BACKOFFICE";

  /** Predefined role for management users. */
  public static final String ROLE_MANAGEMENT = "MANAGEMENT";
}
