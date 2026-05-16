package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.services.user.UserEntity;
import jakarta.persistence.*;
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
