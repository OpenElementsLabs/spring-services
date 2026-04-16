package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Local mirror of an OAuth2-authenticated user.
 *
 * <p>The platform does not own credentials — the {@code sub} column stores the OAuth2 subject
 * identifier supplied by the identity provider's JWT and is the only stable link back to the
 * external identity. The remaining columns cache profile information so that domain rows can refer
 * to a local primary key instead of an opaque external identifier.
 *
 * <p>The avatar bytes are loaded lazily ({@link FetchType#LAZY}) to avoid the cost of streaming an
 * image with every {@code SELECT}.
 */
@Entity
@Table(name = "users")
public class UserEntity extends AbstractEntity {

  @Column(name = "sub", nullable = false, unique = true, length = 255)
  private String sub;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "email", length = 255)
  private String email;

  @Basic(fetch = FetchType.LAZY)
  @Column(name = "avatar")
  private byte[] avatar;

  @Column(name = "avatar_content_type", length = 100)
  private String avatarContentType;

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
   * Returns the avatar image bytes, or {@code null} if no avatar has been uploaded.
   *
   * <p>Loaded lazily — accessing this getter outside of an active persistence context throws a
   * {@code LazyInitializationException}.
   */
  public byte[] getAvatar() {
    return avatar;
  }

  public void setAvatar(final byte[] avatar) {
    this.avatar = avatar;
  }

  /**
   * Returns the MIME content type of the stored avatar (e.g. {@code image/png}), or {@code null} if
   * no avatar is set.
   */
  public String getAvatarContentType() {
    return avatarContentType;
  }

  public void setAvatarContentType(final String avatarContentType) {
    this.avatarContentType = avatarContentType;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "UserEntity[id=" + id() + ", sub=" + sub + ", name=" + name + "]";
  }
}
