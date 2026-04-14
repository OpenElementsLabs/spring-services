package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;

public class OnObjectCreate<T extends WithId> extends GenericDataEvent<T> {

  public OnObjectCreate(T source) {
    super(source);
  }
}
