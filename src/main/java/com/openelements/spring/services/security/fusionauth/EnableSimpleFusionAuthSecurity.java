package com.openelements.spring.services.security.fusionauth;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(TYPE)
@Retention(RUNTIME)
@Import(SimpleFusionAuthBasedSecurityConfig.class)
@Documented
public @interface EnableSimpleFusionAuthSecurity {
}
