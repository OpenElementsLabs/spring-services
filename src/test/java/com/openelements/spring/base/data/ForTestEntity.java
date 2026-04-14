package com.openelements.spring.base.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class ForTestEntity extends AbstractEntity {

    @Column
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
