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

  @Column(name = "sub", nullable = false, unique = true, length = 255)
  private String sub;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "avatar_url", length = 2048)
  private String avatarUrl;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UserEntity() {}

  /**
   * Returns the OAuth2 subject identifier ({@code sub} claim) of this user.
   *
   * @return the subject identifier, never {@code null} for persisted entities
   */
  public String getSub() {
    return sub;
  }

  public void setSub(final String sub) {
    Objects.requireNonNull(sub, "sub must not be null");
    this.sub = sub;
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
    return "UserEntity[id=" + id() + ", sub=" + sub + ", name=" + name + "]";
  }
}
