package com.openelements.spring.base.security;

import com.openelements.spring.base.security.user.Roles;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shortcut for {@code @PreAuthorize("hasRole('APP-ADMIN')")}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('" + Roles.ROLE_APP_ADMIN + "')")
public @interface NeedsAppAdminRole {
}
