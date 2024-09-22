package com.openelements.spring.services.data;

import java.io.Serializable;
import java.util.UUID;

public interface DbEntity extends Serializable {

    UUID getId();

    void setId(UUID id);
}
