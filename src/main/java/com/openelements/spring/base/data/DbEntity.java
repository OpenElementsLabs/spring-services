package com.openelements.spring.base.data;

import java.io.Serializable;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface DbEntity extends Serializable, WithId {

  @Override
  default @Nullable UUID id() {
    return getId();
  }

  @Nullable UUID getId();

  void setId(@Nullable UUID id);
}
