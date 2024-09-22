package com.openelements.spring.services.security.fusionauth;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@AutoConfiguration
@EnableWebSecurity
@Import(FusionAuthConfig.class)
public class SimpleFusionAuthBasedSecurityConfig {

    private final FusionAuthSupport fusionAuthConfig;

    @Value("${security.permittedUrls:}")
    private List<String> permittedUrls;

    @Autowired
    public SimpleFusionAuthBasedSecurityConfig(@NonNull FusionAuthSupport fusionAuthConfig) {
        this.fusionAuthConfig = Objects.requireNonNull(fusionAuthConfig, "fusionAuthConfig must not be null");
    }

    @Bean
    public SecurityFilterChain filterChain(@NonNull HttpSecurity http) throws Exception {
        Objects.requireNonNull(http, "http must not be null");
        return http
                .authorizeHttpRequests(auth -> fusionAuthConfig.authorizeHttpRequests(auth, permittedUrls))
                .oauth2ResourceServer(oauth2 -> fusionAuthConfig.configureOauth(oauth2))
                .build();
    }
}
