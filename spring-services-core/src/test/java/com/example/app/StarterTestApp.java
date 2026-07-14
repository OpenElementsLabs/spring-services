package com.example.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A minimal consumer application used to prove zero-config starter behaviour.
 *
 * <p>Crucially it declares <strong>no</strong> {@code @Import(FullSpringServiceConfig.class)}, no
 * {@code @EntityScan}, and no {@code @EnableJpaRepositories}, and it does not point
 * {@code scanBasePackages} at the library. The default {@code @SpringBootApplication} behaviour
 * registers only this package ({@code com.example.app}) via {@code @AutoConfigurationPackage}. The
 * library's {@code SpringServicesCoreAutoConfiguration} must additively register
 * {@code com.openelements.spring.base} so that both the application's and the library's entities and
 * repositories are discovered — which is exactly what {@link StarterAutoConfigurationIntegrationTest}
 * asserts.
 */
@SpringBootApplication
public class StarterTestApp {}
