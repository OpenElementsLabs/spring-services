package com.openelements.spring.base.data;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Contract for JPA entities managed by this platform. Combines {@link Serializable} (required by
 * JPA for detached entity transfer) with {@link WithId} (so entities and their DTO counterparts
 * share a common identity contract).
 *
 * <p>Most domain entities should not implement this interface directly — extend {@link
 * AbstractEntity} instead, which provides a generated {@link UUID} primary key as well as audit
 * timestamps.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "books")
 * public class BookEntity extends AbstractEntity { ... }
 * }</pre>
 */
public interface DbEntity extends Serializable, WithId {

    /**
     * {@inheritDoc}
     *
     * <p>Default implementation delegates to {@link #getId()} so that JPA-style getter access and the
     * record-style accessor on {@link WithId} stay consistent.
     */
    @Override
    default @Nullable UUID id() {
        return getId();
    }

    /**
     * Returns the primary key of this entity.
     *
     * @return the id, or {@code null} if the entity has not been persisted yet
     */
    @Nullable UUID getId();

    /**
     * Sets the primary key of this entity.
     *
     * <p>Application code should normally not call this method — JPA assigns the id automatically on
     * insert. It is exposed because some test fixtures or migration tools require it.
     *
     * @param id the id to assign, or {@code null} to clear it
     */
    void setId(@Nullable UUID id);
}
