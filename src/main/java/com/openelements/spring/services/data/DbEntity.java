package com.openelements.spring.services.data;

import java.io.Serializable;
import java.util.UUID;

public interface DbEntity extends Serializable, WithId {

    @Override
    default UUID id() {
        return getId();
    }

    UUID getId();

    void setId(UUID id);
}
