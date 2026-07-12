package com.openelements.spring.base.data;

/**
 * Single source of truth for the database schema that owns every spring-services table.
 *
 * <p>All library {@code @Entity} classes map their {@code @Table(schema = DbSchema.NAME)} to this
 * schema, and every schema-qualified native SQL statement references it through {@link #NAME}. The
 * value is a compile-time constant ({@code static final String}), so it is usable both in
 * annotation attributes and in native SQL string concatenation — changing it here changes every
 * reference consistently, with no literal drift.
 *
 * <p>The schema name is deliberately fixed and not configurable: externalising it would require a
 * Hibernate {@code PhysicalNamingStrategy} that distinguishes library entities from application
 * entities, which is out of scope.
 */
public final class DbSchema {

  /** The dedicated database schema that owns every spring-services table. */
  public static final String NAME = "oe_spring_services";

  private DbSchema() {}
}
