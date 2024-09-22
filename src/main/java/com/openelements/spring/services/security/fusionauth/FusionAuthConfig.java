package com.openelements.spring.services.security.fusionauth;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class FusionAuthConfig {

    @Bean
    public FusionAuthSupport fusionAuthSupport(final @NonNull OAuth2ResourceServerProperties properties) {
        return new FusionAuthSupport(properties);
    }

    @Bean
    public JwtDecoder jwtDecoder(@NonNull final OAuth2ResourceServerProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(properties.getJwt(), "properties.getJwt() must not be null");
        Objects.requireNonNull(properties.getJwt().getIssuerUri(), "properties.getJwt().getIssuerUri() must not be null");
        return NimbusJwtDecoder.withIssuerLocation(properties.getJwt().getIssuerUri()).build();
    }
}
