package com.openelements.spring.services.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authentication filter that checks for the X-API-Key header.
 * If present, validates the key and enforces read-only access (GET/HEAD/OPTIONS only).
 * If absent, passes through to the standard JWT authentication filter.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> READ_ONLY_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name()
    );

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(final ApiKeyService apiKeyService) {
        this.apiKeyService = Objects.requireNonNull(apiKeyService, "apiKeyService must not be null");
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null) {
            // No API key header — pass through to JWT auth
            filterChain.doFilter(request, response);
            return;
        }

        // API key header present — attempt authentication
        final var entity = apiKeyService.authenticate(apiKey);

        if (entity.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid API key\"}");
            return;
        }

        // Key is valid — check if method is read-only
        if (!READ_ONLY_METHODS.contains(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"API keys only grant read-only access\"}");
            return;
        }

        // Authenticate the request
        final ApiKeyAuthentication authentication = new ApiKeyAuthentication(entity.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /**
     * Authentication token representing a valid API key.
     */
    static class ApiKeyAuthentication extends AbstractAuthenticationToken {

        private final ApiKeyEntity apiKey;

        ApiKeyAuthentication(final ApiKeyEntity apiKey) {
            super(List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
            this.apiKey = apiKey;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return apiKey;
        }
    }
}
