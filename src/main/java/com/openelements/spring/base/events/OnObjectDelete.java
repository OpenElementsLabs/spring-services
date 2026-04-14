package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;

public class OnObjectDelete<T extends WithId> extends GenericDataEvent<T> {

  public OnObjectDelete(T source) {
    super(source);
  }
}
