package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.jspecify.annotations.NonNull;

public class OnObjectDelete<T extends WithId> extends GenericDataEvent<T> {

    public OnObjectDelete(@NonNull T source) {
        super(source);
    }
}
