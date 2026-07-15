package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.data.DbSchema;
import com.openelements.spring.base.services.user.UserEntity;
import jakarta.persistence.*;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** JPA entity representing a single comment together with its author. */
@Entity
@Table(name = "comments", schema = DbSchema.NAME)
public class CommentEntity extends AbstractEntity {

  /** The text content of the comment. */
  @Column(name = "text", nullable = false, columnDefinition = "TEXT")
  private String text;

  /** The user who wrote the comment. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "author_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_comments_author"))
  private UserEntity author;

  /** Creates a new, empty comment entity for use by JPA and the owning service. */
  protected CommentEntity() {}

  /**
   * Returns the text of this comment.
   *
   * @return the comment text
   */
  public String getText() {
    return text;
  }

  /**
   * Sets the text of this comment.
   *
   * @param text the comment text
   */
  public void setText(@NonNull final String text) {
    this.text = Objects.requireNonNull(text, "text must not be null");
  }

  /**
   * Returns the author of this comment.
   *
   * @return the user who wrote the comment
   */
  public UserEntity getAuthor() {
    return author;
  }

  /**
   * Sets the author of this comment.
   *
   * @param author the user who wrote the comment
   */
  public void setAuthor(@NonNull final UserEntity author) {
    this.author = Objects.requireNonNull(author, "author must not be null");
  }
}
