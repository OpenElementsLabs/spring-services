package com.openelements.spring.base.services.audit;

/**
 * Type of data lifecycle action recorded in an {@link AuditLogEntity}.
 *
 * <p>Mirrors the three lifecycle events fired by {@link
 * com.openelements.spring.base.data.AbstractDbBackedDataService}: {@code OnObjectCreate} maps to
 * {@link #INSERT}, {@code OnObjectUpdate} to {@link #UPDATE}, and {@code OnObjectDelete} to {@link
 * #DELETE}.
 */
public enum AuditAction {

  /** A new entity was inserted into the database. */
  INSERT,

  /** An existing entity was updated. */
  UPDATE,

  /** An entity was removed from the database. */
  DELETE
}
