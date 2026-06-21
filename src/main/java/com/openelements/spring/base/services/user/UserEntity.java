package com.openelements.spring.base.services.user;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.NameSupplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Local mirror of an OAuth2-authenticated user.
 *
 * <p>The platform does not own credentials — the {@code sub} column stores the OAuth2 subject
 * identifier supplied by the identity provider's JWT and is the only stable link back to the
 * external identity. The remaining columns cache profile information so that domain rows can refer
 * to a local primary key instead of an opaque external identifier.
 *
 * <p>The {@code name}, {@code email} and {@code avatarUrl} columns are kept in sync with the
 * matching JWT claims by {@link UserService} on every request — the local row therefore always
 * reflects the most recent assertions of the identity provider.
 */
@Entity
@Table(name = "users")
@BatchSize(size = 50)
public class UserEntity extends AbstractEntity {

  @Column(name = "sub", nullable = true, unique = true, length = 255)
  private String sub;

  @Column(name = "external_id", nullable = true, unique = true, length = 255)
  private String externalId;

  @Column(name = "user_name", nullable = false, unique = true, length = 255)
  private String userName;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "avatar_url", length = 2048)
  private String avatarUrl;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UserEntity() {}

  /**
   * Returns the OAuth2 subject identifier ({@code sub} claim) of this user.
   *
   * <p>May be {@code null} for rows that were provisioned by SCIM before the user ever logged in
   * interactively — once the user authenticates the first time, the JIT flow backfills this field
   * from the JWT {@code sub} claim. For interactively-provisioned users (the historical default)
   * this is always populated.
   *
   * @return the subject identifier, or {@code null} for SCIM-pre-provisioned rows that have not
   *     yet seen an interactive login
   */
  public String getSub() {
    return sub;
  }

  public void setSub(final String sub) {
    this.sub = sub;
  }

  /**
   * Returns the stable IdP-side identifier of this user.
   *
   * <p>For Authentik (and most IdPs that use the same value for OIDC {@code sub} and SCIM
   * {@code externalId}), this mirrors {@link #getSub()} once the user has logged in interactively.
   * Populated independently of {@code sub} when the row is created via SCIM provisioning, which
   * is what makes correlation between SCIM-pre-provisioned and JIT-logged-in users possible.
   *
   * @return the IdP-side identifier, or {@code null} for users provisioned before the
   *     {@code externalId} column existed and not yet seen for an interactive login since
   */
  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(final String externalId) {
    this.externalId = externalId;
  }

  /**
   * Returns the unique business-key handle of this user — typically the IdP's
   * {@code preferred_username} claim, with a fallback chain ({@code email → sub → "user-<UUID>"})
   * applied at JIT-login time to guarantee a non-null value.
   *
   * @return the user-name handle, never {@code null} for persisted entities
   */
  public String getUserName() {
    return userName;
  }

  public void setUserName(final String userName) {
    this.userName = userName;
  }

  /**
   * Returns whether this user is active. An inactive user is rejected by every authentication
   * path (JWT chain throws {@code AccessDeniedException}; opaque-token chain throws
   * {@code BadOpaqueTokenException} via the {@code PrincipalDirectory} live check).
   *
   * @return {@code true} if the user is active, {@code false} otherwise
   */
  public boolean isActive() {
    return active;
  }

  public void setActive(final boolean active) {
    this.active = active;
  }

  /**
   * Returns the cached display name of the user, kept in sync with the JWT {@code name} claim by
   * {@link UserService}.
   */
  @NameSupplier
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    Objects.requireNonNull(name, "name must not be null");
    this.name = name;
  }

  /**
   * Returns the cached email address of the user, kept in sync with the JWT {@code email} claim by
   * {@link UserService}. May be {@code null} if the identity provider does not assert it.
   */
  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  /**
   * Returns the avatar URL of the user, kept in sync with the JWT {@code avatar} claim by {@link
   * UserService}. May be {@code null} if the identity provider does not assert it.
   */
  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(final String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "UserEntity[id="
        + id()
        + ", sub="
        + sub
        + ", externalId="
        + externalId
        + ", userName="
        + userName
        + ", active="
        + active
        + ", name="
        + name
        + "]";
  }
}
