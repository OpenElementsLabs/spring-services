package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;

public class OnObjectUpdate<T extends WithId> extends GenericDataEvent<T> {

  public OnObjectUpdate(T source) {
    super(source);
  }
}
