package com.openelements.spring.services.events;

import com.openelements.spring.services.data.WithId;

public class OnObjectCreate<T extends WithId> extends GenericDataEvent<T> {

    public OnObjectCreate(T source) {
        super(source);
    }
}
