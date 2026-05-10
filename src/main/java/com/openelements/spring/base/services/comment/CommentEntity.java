package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.security.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@Entity
@Table(name = "comments")
public class CommentEntity extends AbstractEntity {

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_comments_author"))
    @BatchSize(size = 50)
    private UserEntity author;

    protected CommentEntity() {
    }

    public String getText() {
        return text;
    }

    public void setText(@NonNull final String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public UserEntity getAuthor() {
        return author;
    }

    public void setAuthor(@NonNull final UserEntity author) {
        this.author = Objects.requireNonNull(author, "author must not be null");
    }
}
