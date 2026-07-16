package com.openelements.spring.base.tenant;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Convenience meta-annotation that enables the multi-tenancy feature on a Spring configuration
 * class. Equivalent to {@code @Import(TenantConfig.class)} but reads more declaratively.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableTenant
 * public class MyApplication { ... }
 * }</pre>
 */
@Target(TYPE)
@Retention(RUNTIME)
@Import(TenantConfig.class)
@Documented
public @interface EnableTenant {}
