package com.openelements.spring.base.security.roles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Shortcut for {@code @PreAuthorize("hasRole('APP-ADMIN')")}. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('" + Roles.ROLE_APP_ADMIN + "')")
public @interface RequiresAppAdmin {}
