package com.openelements.spring.base.services.tag;

import java.util.Objects;
import org.springframework.context.ApplicationEvent;

public class PreTagDeleteEvent extends ApplicationEvent {

  public PreTagDeleteEvent(TagDto tag) {
    super(Objects.requireNonNull(tag, "tag must not be null"));
  }

  public TagDto getTag() {
    return (TagDto) getSource();
  }
}
