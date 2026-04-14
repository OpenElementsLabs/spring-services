package com.openelements.spring.base.data;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface WithId {

    @Nullable UUID id();
}
