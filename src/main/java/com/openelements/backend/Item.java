package com.openelements.backend;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

public record Item(@NonNull String id, @NonNull String name) {

    public Item {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
