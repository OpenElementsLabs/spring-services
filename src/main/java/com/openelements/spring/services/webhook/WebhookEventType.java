package com.openelements.spring.services.webhook;

/**
 * Enum defining all domain event types that trigger webhook notifications.
 */
public enum WebhookEventType {
    COMPANY_CREATED,
    COMPANY_UPDATED,
    COMPANY_DELETED,
    CONTACT_CREATED,
    CONTACT_UPDATED,
    CONTACT_DELETED,
    COMMENT_CREATED,
    COMMENT_UPDATED,
    COMMENT_DELETED,
    TAG_CREATED,
    TAG_UPDATED,
    TAG_DELETED,
    TASK_CREATED,
    TASK_UPDATED,
    TASK_DELETED,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PING
}
