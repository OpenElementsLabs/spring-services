package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

public class OnObjectUpdate<T extends WithId> extends GenericDataEvent<T> {

  public OnObjectUpdate(@NonNull T source) {
    super(source);
  }
}
