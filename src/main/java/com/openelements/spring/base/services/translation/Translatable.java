package com.openelements.spring.base.services.translation;

import org.jspecify.annotations.NonNull;

public interface Translatable {

    @NonNull String type();

    @NonNull String id();

    @NonNull String text();
}
