package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;
import java.util.UUID;

public abstract class GenericDataEvent<T extends WithId> extends ApplicationEvent {

    private final Class<T> type;

    public GenericDataEvent(@NonNull final T source) {
        super(Objects.requireNonNull(source, "source must not be null"));
        this.type = (Class<T>) source.getClass();
    }

    public GenericDataEvent(@NonNull final Class<T> type, @Nullable final T source) {
        super(Objects.requireNonNull(source, "source must not be null"));
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    @Nullable
    public T getSource() {
        return (T) super.getSource();
    }

    @Nullable
    public T getData() {
        return getSource();
    }

    @NonNull
    public Class<T> getType() {
        return type;
    }

    @NonNull
    public UUID entityId() {
        return getData().id();
    }
}
