package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

public class OnObjectCreate<T extends WithId> extends GenericDataEvent<T> {

    public OnObjectCreate(@NonNull T source) {
        super(source);
    }
}
