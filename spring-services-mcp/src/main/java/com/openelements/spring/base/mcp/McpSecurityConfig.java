package com.openelements.spring.base.mcp;

import com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the MCP endpoint (Phase 1).
 *
 * <p>Registers a dedicated {@link SecurityFilterChain} scoped to {@code /mcp/**}
 * with {@code @Order(0)} — ahead of the spring-services
 * {@code externalApiFilterChain} ({@code @Order(1)}, locked to GET on
 * {@code /api/external/**}) and {@code defaultFilterChain} ({@code @Order(2)},
 * the JWT catch-all). Because the chain's {@code securityMatcher} only matches
 * {@code /mcp}, ordering it first never interferes with {@code /api/**}.
 *
 * <p>Authentication reuses the spring-services {@link ApiKeyAuthenticationFilter}
 * (which is HTTP-method-agnostic — it only inspects the {@code X-API-Key}
 * header), so the MCP {@code POST} endpoint authenticates with the existing
 * CRM API keys even though spring-services' own external chain permits only GET.
 * A fresh filter instance is constructed here (rather than reusing the library
 * bean) to keep this chain self-contained and avoid global servlet-filter
 * registration side effects.
 *
 * <p>The whole configuration is gated on {@code openelements.mcp.enabled=true}
 * and the API-key profile being enabled. CSRF is disabled and the session
 * policy is stateless — this is a machine-to-machine JSON-RPC endpoint.
 */
@Configuration
@ConditionalOnProperty(prefix = "openelements.mcp", name = "enabled", havingValue = "true")
public class McpSecurityConfig {

    /** Path patterns covered by the MCP security chain: the endpoint itself and any sub-path. */
    static final String[] MCP_PATHS = {"/mcp", "/mcp/**"};

    /**
     * The {@code /mcp} filter chain for the API-key auth profile (Phase 1).
     *
     * @param http              the {@link HttpSecurity} to configure
     * @param apiKeyDataService the spring-services key store used for validation
     * @param apiKeyEntryPoint  the JSON 401 entry point used by the external API chain
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "openelements.mcp.auth.api-key", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain mcpApiKeyFilterChain(
        final HttpSecurity http,
        final ApiKeyDataService apiKeyDataService,
        @Qualifier("apiKeyAuthenticationEntryPoint") final AuthenticationEntryPoint apiKeyEntryPoint
    ) throws Exception {
        final ApiKeyAuthenticationFilter mcpApiKeyFilter =
            new ApiKeyAuthenticationFilter(apiKeyDataService, apiKeyEntryPoint);
        http
            .securityMatcher(MCP_PATHS)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(eh -> eh.authenticationEntryPoint(apiKeyEntryPoint))
            .addFilterBefore(mcpApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
