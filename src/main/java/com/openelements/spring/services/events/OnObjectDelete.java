package com.openelements.spring.services.events;

import com.openelements.spring.services.data.WithId;

public class OnObjectDelete<T extends WithId> extends GenericDataEvent<T> {

    public OnObjectDelete(T source) {
        super(source);
    }
}
