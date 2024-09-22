package com.openelements.spring.services.security.fusionauth;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(TYPE)
@Retention(RUNTIME)
@Import(FusionAuthConfig.class)
@Documented
public @interface EnableFusionAuth {
}
