package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEvent;

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

  public T getData() {
    return getSource();
  }

  public Class<T> getType() {
    return type;
  }

  public UUID entityId() {
    return getData().id();
  }
}
