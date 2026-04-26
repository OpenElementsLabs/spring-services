package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.GenericDataEvent;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.security.AuthService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to data lifecycle events and writes a corresponding {@link AuditLogEntity} for each one.
 *
 * <p>The handlers run synchronously, inside the originating thread, so that {@link AuthService} can
 * still see the active {@code SecurityContext}. The actual database write is delegated to {@link
 * AuditLogDataService#createEntry} which is annotated {@code @Transactional(REQUIRES_NEW)} —
 * isolating the audit row from the surrounding business transaction in both directions.
 */
@Component
public class AuditLogEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogEventListener.class);

  private static final String SYSTEM_USER = "System";

  private final AuditLogDataService auditLogDataService;

  private final AuthService authService;

  public AuditLogEventListener(
      final AuditLogDataService auditLogDataService, final AuthService authService) {
    this.auditLogDataService =
        Objects.requireNonNull(auditLogDataService, "auditLogDataService must not be null");
    this.authService = Objects.requireNonNull(authService, "authService must not be null");
  }

  /**
   * Records a {@link AuditAction#INSERT} entry for every {@link OnObjectCreate} event.
   *
   * @param event the event published by the originating data service
   * @param <T> the audited DTO type
   */
  @EventListener
  public <T extends WithId> void handleOnObjectCreate(final OnObjectCreate<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    record(event, AuditAction.INSERT);
  }

  /**
   * Records a {@link AuditAction#UPDATE} entry for every {@link OnObjectUpdate} event.
   *
   * @param event the event published by the originating data service
   * @param <T> the audited DTO type
   */
  @EventListener
  public <T extends WithId> void handleOnObjectUpdate(final OnObjectUpdate<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    record(event, AuditAction.UPDATE);
  }

  /**
   * Records a {@link AuditAction#DELETE} entry for every {@link OnObjectDelete} event.
   *
   * @param event the event published by the originating data service
   * @param <T> the audited DTO type
   */
  @EventListener
  public <T extends WithId> void handleOnObjectDelete(final OnObjectDelete<T> event) {
    Objects.requireNonNull(event, "event must not be null");
    record(event, AuditAction.DELETE);
  }

  private <T extends WithId> void record(
      final GenericDataEvent<T> event, final AuditAction action) {
    if (AuditLogDto.class.equals(event.getType())) {
      return;
    }
    final String user = resolveUser();
    try {
      auditLogDataService.createEntry(
          event.getType().getSimpleName(), event.entityId(), action, user);
    } catch (final RuntimeException ex) {
      LOG.warn(
          "Failed to write audit log entry for {} {} by {}",
          action,
          event.getType().getSimpleName(),
          user,
          ex);
    }
  }

  private String resolveUser() {
    try {
      return authService.getPrincipal().getName();
    } catch (final IllegalStateException ex) {
      return SYSTEM_USER;
    }
  }
}
