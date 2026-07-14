package com.openelements.spring.base.services.webhook;

/** Enum defining all domain event types that trigger webhook notifications. */
public enum WebhookDataEventType {
  /** A new entity was created. */
  CREATED,
  /** An existing entity was updated. */
  UPDATED,
  /** An existing entity was deleted. */
  DELETED
}
