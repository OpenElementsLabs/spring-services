package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@Entity
@Table(name = "comments")
public class CommentEntity extends AbstractEntity {

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "author", nullable = false, length = 255)
    private String authorId;

    protected CommentEntity() {
    }

    public String getText() {
        return text;
    }

    public void setText(@NonNull final String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(@NonNull final String authorId) {
        this.authorId = Objects.requireNonNull(authorId, "authorId must not be null");
    }
}
