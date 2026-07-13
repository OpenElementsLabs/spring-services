package com.openelements.spring.base.data;

import com.openelements.spring.base.events.GenericDataEvent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;

@Component
@ApplicationScope
public class TestApplicationListener {

  private final Lock lock = new ReentrantLock();

  private final Condition condition = lock.newCondition();

  private GenericDataEvent<ForTestDto> lastEvent;

  @EventListener
  public void onApplicationEvent(GenericDataEvent<ForTestDto> event) {
    lock.lock();
    try {
      lastEvent = event;
      condition.signal();
    } finally {
      lock.unlock();
    }
  }

  public void clearEvent() {
    lock.lock();
    try {
      lastEvent = null;
    } finally {
      lock.unlock();
    }
  }

  public GenericDataEvent<ForTestDto> waitForNextEvent() {
    lock.lock();
    try {
      if (lastEvent != null) {
        return lastEvent;
      } else {
        try {
          condition.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return lastEvent;
      }
    } finally {
      lock.unlock();
    }
  }
}
