package com.openelements.spring.services.events;

import com.openelements.spring.services.data.WithId;

public class OnObjectUpdate<T extends WithId> extends GenericDataEvent<T> {

    public OnObjectUpdate(T source) {
        super(source);
    }
}
