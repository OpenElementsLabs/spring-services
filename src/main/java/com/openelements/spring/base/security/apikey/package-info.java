/**
 * Servlet filter that implements the {@code X-API-Key} authentication mechanism described in the
 * {@linkplain com.openelements.spring.base.security parent package documentation}.
 *
 * <p>The {@link com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter} is a pure
 * authentication adapter: it validates the {@code X-API-Key} header against {@link
 * com.openelements.spring.base.services.apikey.ApiKeyDataService} and populates the security
 * context with an {@code ApiKeyAuthentication} carrying {@code ROLE_API_KEY}. HTTP-method
 * authorization (the read-only restriction for API-key requests) is configured declaratively on
 * the external API filter chain in {@link
 * com.openelements.spring.base.security.SecurityConfig#externalApiFilterChain} and is not the
 * filter's responsibility.
 *
 * <p>The filter is intentionally <b>not</b> annotated with {@code @Component}. It is constructed
 * via a {@code @Bean} method in {@link com.openelements.spring.base.security.SecurityConfig} and
 * registered only on the external API chain via {@code addFilterBefore}. Without this, Spring Boot
 * would auto-register the filter at the servlet level so that it would also run on the JWT chain,
 * breaking the strict isolation between the two chains.
 *
 * <p>The data and persistence side of API keys (entities, DTOs, repositories, key generation and
 * hashing) lives in {@link com.openelements.spring.base.services.apikey} so that the filter can
 * remain a thin authentication adapter.
 */
package com.openelements.spring.base.security.apikey;
