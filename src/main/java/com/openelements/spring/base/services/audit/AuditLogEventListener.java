package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.data.NameSupplier;
import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.GenericDataEvent;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

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

    private static final String UNKNOWN_PRINCIPAL_ID = "UNKNOWN";

    private final AuditLogDataService auditLogDataService;

    private final AuthService authService;

    private final UserRepository userRepository;

    public AuditLogEventListener(
            final AuditLogDataService auditLogDataService,
            final AuthService authService,
            final UserRepository userRepository) {
        this.auditLogDataService =
                Objects.requireNonNull(auditLogDataService, "auditLogDataService must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    /**
     * Records a {@link AuditAction#INSERT} entry for every {@link OnObjectCreate} event.
     *
     * @param event the event published by the originating data service
     * @param <T>   the audited DTO type
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
     * @param <T>   the audited DTO type
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
     * @param <T>   the audited DTO type
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

        final String name = getNameOfData(event.getData()).orElse("UNKNOWN");

        final UserEntity user = resolveUser();
        try {
            auditLogDataService.createEntry(
                    event.getType().getSimpleName(), event.entityId(), name, action, user);
        } catch (final RuntimeException ex) {
            LOG.warn(
                    "Failed to write audit log entry for {} {} by user id {}",
                    action,
                    event.getType().getSimpleName(),
                    user.id(),
                    ex);
        }
    }

    private <T extends WithId> Optional<String> getNameOfData(T data) {
        Objects.requireNonNull(data, "data must not be null");
        return Arrays.stream(data.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(NameSupplier.class))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> String.class.equals(method.getReturnType()))
                .findFirst()
                .flatMap(method -> {
                    try {
                        return Optional.ofNullable((String) method.invoke(data));
                    } catch (final ReflectiveOperationException ex) {
                        LOG.warn(
                                "Failed to invoke @NameSupplier method {} on {}",
                                method.getName(),
                                data.getClass().getName(),
                                ex);
                        return Optional.empty();
                    }
                });
    }

    private UserEntity resolveUser() {
        final UserInformation userInformation;
        try {
            userInformation = authService.getUserInformation();
        } catch (final IllegalStateException ex) {
            LOG.warn(
                    "Failed to resolve user information from security context, falling back to System User.",
                    ex);
            return systemUserReference();
        }
        if (userInformation == null) {
            LOG.warn("User information is null, falling back to System User.");
            return systemUserReference();
        }
        final String sub = userInformation.id();
        if (sub == null || sub.isBlank() || UNKNOWN_PRINCIPAL_ID.equals(sub)) {
            LOG.warn("Non-JWT principal (sub={}), falling back to System User.", sub);
            return systemUserReference();
        }
        final String name = userInformation.name();
        if (name == null || name.isBlank()) {
            LOG.warn("User name is null or blank, falling back to System User.");
            return systemUserReference();
        }
        return userRepository
                .findBySub(sub)
                .orElseGet(
                        () -> {
                            LOG.warn(
                                    "No local user row found for sub={}, falling back to System User. "
                                            + "UserService should have provisioned the user earlier in the request.",
                                    sub);
                            return systemUserReference();
                        });
    }

    private UserEntity systemUserReference() {
        return userRepository.getReferenceById(SystemUser.ID);
    }
}
