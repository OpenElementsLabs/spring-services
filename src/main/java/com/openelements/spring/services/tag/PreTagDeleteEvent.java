package com.openelements.spring.services.tag;

import org.springframework.context.ApplicationEvent;

import java.util.Objects;

public class PreTagDeleteEvent extends ApplicationEvent {

    public PreTagDeleteEvent(TagDto tag) {
        super(Objects.requireNonNull(tag, "tag must not be null"));
    }

    public TagDto getTag() {
        return (TagDto) getSource();
    }
}
