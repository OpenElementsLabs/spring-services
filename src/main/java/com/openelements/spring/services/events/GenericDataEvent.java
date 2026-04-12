package com.openelements.spring.services.events;

import com.openelements.spring.services.data.WithId;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

public abstract class GenericDataEvent<T extends WithId> extends ApplicationEvent {

    private Class<T> type;

    public GenericDataEvent(T source) {
        super(Objects.requireNonNull(source, "source must not be null"));
        this.type = (Class<T>) source.getClass();
    }

    public GenericDataEvent(Class<T> type, T source) {
        super(Objects.requireNonNull(source, "source must not be null"));
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public T getSource() {
        return (T) super.getSource();
    }

    public Class<T> getType() {
        return type;
    }
}
