package com.openelements.spring.base.tenant;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Target(TYPE)
@Retention(RUNTIME)
@Import(TenantConfig.class)
@Documented
public @interface EnableTenant {
}
