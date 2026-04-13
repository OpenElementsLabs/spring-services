package com.openelements.spring.base.security.user;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

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

    public UserEntity() {
    }

    public String getSub() {
        return sub;
    }

    public void setSub(final String sub) {
        Objects.requireNonNull(sub, "sub must not be null");
        this.sub = sub;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(final byte[] avatar) {
        this.avatar = avatar;
    }

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
