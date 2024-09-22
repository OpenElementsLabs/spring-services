package com.openelements.spring.services.security.fusionauth;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.stereotype.Component;

@Component
public class FusionAuthSupport {

    private final OAuth2ResourceServerProperties properties;

    @Autowired
    public FusionAuthSupport(@NonNull final OAuth2ResourceServerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public void authorizeHttpRequests(@NonNull final AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,@NonNull final List<String> permittedUrls) {
        Objects.requireNonNull(auth, "auth must not be null");
        Objects.requireNonNull(permittedUrls, "permittedUrls must not be null");
        auth.requestMatchers("/auth/check").permitAll();
        auth.requestMatchers("/auth/logout").permitAll();
        permittedUrls.forEach(url -> auth.requestMatchers(url).permitAll());
        auth.anyRequest().authenticated();
    }

    public void authorizeHttpRequests(@NonNull final AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        authorizeHttpRequests(auth, List.of());
    }

    public void configureOauth(@NonNull final OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) {
        Objects.requireNonNull(oauth2, "oauth2 must not be null");
        final List<String> audiences = properties.getJwt().getAudiences();
        final FusionAuthJwtConverter converter = new FusionAuthJwtConverter(audiences);
        oauth2.jwt(jwt -> {
            jwt.jwtAuthenticationConverter(converter);
        });
        oauth2.bearerTokenResolver(new FusionAuthBearerTokenResolver());
    }
}
