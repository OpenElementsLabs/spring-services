package com.openelements.spring.base.altcha;

import com.openelements.spring.base.SpringServicesCoreAutoConfiguration;
import com.openelements.spring.base.services.altcha.AltchaChallengeController;
import com.openelements.spring.base.services.altcha.AltchaExceptionHandler;
import com.openelements.spring.base.services.altcha.AltchaProperties;
import com.openelements.spring.base.services.altcha.AltchaService;
import com.openelements.spring.base.services.altcha.AltchaVerifier;
import com.openelements.spring.base.services.altcha.InMemoryReplayStore;
import com.openelements.spring.base.services.altcha.ReplayStore;
import org.altcha.altcha.v2.Altcha;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auto-configuration for the optional ALTCHA bot protection.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, but gated on
 * {@code openelements.altcha.enabled=true} rather than on classpath presence: the module cannot work
 * without a signing secret, and self-activation would fail the start-up of every application that
 * consumes {@code spring-services-all} without configuring one.
 *
 * <p>Every bean is guarded by {@link ConditionalOnMissingBean}, so an application replaces a part — most
 * likely the {@link ReplayStore} when it runs several instances — by declaring its own bean. The beans
 * live on the auto-configuration itself, as in {@code StorageAutoConfiguration}, because
 * {@code @ConditionalOnMissingBean} is only reliable while auto-configurations are being processed.
 *
 * <p>Runs after the auto-configurations that enable Spring Security, so that the module's filter
 * chain can see whether there is an {@link HttpSecurity} to build it with. Without Spring Security
 * there is nothing to open, and the chain is not registered.
 */
@AutoConfiguration(after = {SpringServicesCoreAutoConfiguration.class, SecurityAutoConfiguration.class})
@ConditionalOnClass(Altcha.class)
@ConditionalOnProperty(prefix = "openelements.altcha", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AltchaProperties.class)
public class AltchaAutoConfiguration {

    /** Creates the auto-configuration. */
    public AltchaAutoConfiguration() {
    }

    /**
     * Remembers used challenges on the heap; correct for a single instance only.
     *
     * @return the store
     */
    @Bean
    @ConditionalOnMissingBean
    public ReplayStore altchaReplayStore() {
        return new InMemoryReplayStore();
    }

    /**
     * Creates and verifies challenges.
     *
     * @param properties  the configuration
     * @param replayStore the store that remembers used challenges
     * @return the verifier
     */
    @Bean
    @ConditionalOnMissingBean
    public AltchaVerifier altchaVerifier(final AltchaProperties properties, final ReplayStore replayStore) {
        return new AltchaVerifier(properties, replayStore);
    }

    /**
     * The API for the application's endpoints.
     *
     * @param verifier the verifier
     * @return the service
     */
    @Bean
    @ConditionalOnMissingBean
    public AltchaService altchaService(final AltchaVerifier verifier) {
        return new AltchaService(verifier);
    }

    /** The web part: the challenge endpoint, its security filter chain and the error response. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Web {

        @Bean
        @ConditionalOnMissingBean
        AltchaChallengeController altchaChallengeController(final AltchaService service) {
            return new AltchaChallengeController(service);
        }

        @Bean
        @ConditionalOnMissingBean
        AltchaExceptionHandler altchaExceptionHandler() {
            return new AltchaExceptionHandler();
        }

        /**
         * Permits anonymous {@code GET} on the challenge path and nothing else on it. Ordered ahead of
         * the default JWT chain, which has no matcher and would otherwise demand a token from the
         * visitor of a public form. The chain matches only this one path, so it cannot open anything
         * else.
         */
        @Bean
        @Order(0)
        @ConditionalOnClass(HttpSecurity.class)
        @ConditionalOnBean(HttpSecurity.class)
        @ConditionalOnMissingBean(name = "altchaSecurityFilterChain")
        SecurityFilterChain altchaSecurityFilterChain(final HttpSecurity http, final AltchaProperties properties)
                throws Exception {
            http.securityMatcher(properties.challengePath())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.GET, properties.challengePath()).permitAll()
                            .anyRequest().denyAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }
    }
}
