package com.openelements.spring.base.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a {@link WithId} DTO as the source of the human-readable name written into the
 * audit log when an entity of that DTO type is created, updated, or deleted.
 *
 * <h2>Contract</h2>
 *
 * <p>The annotated method must:
 *
 * <ul>
 *   <li>take no arguments,
 *   <li>return a {@link String}, and
 *   <li>be side-effect free — the audit-log event listener invokes it on every persistence event.
 * </ul>
 *
 * <p>If no method on the DTO matches (no {@code @NameSupplier}, wrong arity, or wrong return type),
 * the listener falls back to the literal string {@code "UNKNOWN"} and the audit entry is still
 * written. A {@code null} return value is treated the same as no method.
 *
 * <p>At most one {@code @NameSupplier} method should be declared per DTO. If several are present
 * the listener picks one via {@link java.util.stream.Stream#findFirst()} over reflected methods,
 * and the JVM's iteration order for {@link Class#getMethods()} is not specified — i.e. the choice
 * between duplicates is effectively undefined.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public record BookDto(UUID id, String title, String author) implements WithId {
 *
 *   @NameSupplier
 *   public String displayName() {
 *     return title + " — " + author;
 *   }
 * }
 * }</pre>
 *
 * @see com.openelements.spring.base.services.audit.AuditLogEntity
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NameSupplier {
}
