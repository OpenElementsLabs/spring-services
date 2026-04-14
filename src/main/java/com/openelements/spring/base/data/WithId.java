package com.openelements.spring.base.data;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface WithId {

  @Nullable UUID id();
}
