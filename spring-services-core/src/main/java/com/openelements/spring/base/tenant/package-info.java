/**
 * Multi-tenancy primitives for the {@code spring-services} platform.
 *
 * <p>Multi-tenancy is implemented as <b>row-level isolation in a shared schema</b>: every
 * tenant-scoped entity carries a {@code tenant_id} column, and every read/write goes through a
 * repository method that filters on the current tenant. There is no schema-per-tenant or
 * database-per-tenant separation.
 *
 * <h2>Tenant identity</h2>
 *
 * <p>The current tenant is derived from the authenticated principal by {@link
 * com.openelements.spring.base.tenant.TenantService#getCurrentTenant()}, which delegates to {@link
 * com.openelements.spring.base.security.AuthService#getPrincipal()}. The principal's name is used
 * verbatim as the tenant id — for JWT-authenticated requests this is the {@code sub} claim, for
 * API-key requests it is the API key entity's name. There is intentionally no way to switch the
 * tenant context manually inside a request.
 *
 * <h2>Building a tenant-scoped feature</h2>
 *
 * The tenant-aware analogues of the abstractions in {@link com.openelements.spring.base.data} are:
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.tenant.AbstractMultitenantEntity} — base class for JPA
 *       entities. Adds a non-null {@code tenantId} column and a {@code @PrePersist} /
 *       {@code @PreUpdate} guard that fails fast if the tenant id was not set.
 *   <li>{@link com.openelements.spring.base.tenant.RepositoryWithTenantSupport} — Spring Data
 *       repository contract that exposes tenant-filtered finders ({@code findAllByTenantId}, {@code
 *       findByIdAndTenantId}).
 *   <li>{@link com.openelements.spring.base.tenant.AbstractMultitenantDbBackedDataService} —
 *       template implementation of {@link com.openelements.spring.base.data.DbBackedDataService}
 *       that injects the current tenant id on insert and uses tenant-filtered lookups for read,
 *       update and delete.
 * </ul>
 *
 * <h2>Enabling the feature</h2>
 *
 * The configuration class {@link com.openelements.spring.base.tenant.TenantConfig} can be imported
 * directly, or — more declaratively — via the {@link
 * com.openelements.spring.base.tenant.EnableTenant} meta-annotation on a Spring configuration
 * class. {@link com.openelements.spring.base.FullSpringServiceConfig} already imports it.
 */
package com.openelements.spring.base.tenant;
