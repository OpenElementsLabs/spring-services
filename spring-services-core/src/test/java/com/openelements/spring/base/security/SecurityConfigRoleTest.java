package com.openelements.spring.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Unit tests for the {@link JwtAuthenticationConverter} produced by {@link SecurityConfig}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The mapping from a JWT's {@code roles} claim onto Spring Security {@code GrantedAuthority}
 * objects: each entry in the claim becomes a {@code ROLE_<value>} authority, the claim's absence
 * or emptiness yields no role authorities, and the default scope-to-authority conversion
 * ({@code SCOPE_<scope>}) is preserved alongside the role mapping. These properties are what
 * {@code @PreAuthorize("hasRole('ADMIN')")} and {@code hasAuthority('SCOPE_openid')} depend on,
 * so any drift here silently widens or breaks authorization.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests, no Spring context. The {@link JwtAuthenticationConverter} is
 * pulled directly off a real {@link SecurityConfig} instance and exercised against
 * hand-constructed {@link Jwt} instances built via {@code Jwt.withTokenValue(...)} — the
 * production converter is invoked verbatim.
 *
 * <p><b>Mock-Audit.</b> One mock: {@link ApiKeyDataService} is mocked because the
 * {@link SecurityConfig} constructor requires it but the role-mapping path under test never
 * touches it. A real {@code ApiKeyDataService} would pull in a {@code JpaRepository} and a
 * datasource for no added coverage. The mock has no stubbed methods — it serves only as a
 * non-null constructor argument.
 */
@DisplayName("SecurityConfig JWT Role Mapping")
class SecurityConfigRoleTest {

  private final SecurityConfig securityConfig = new SecurityConfig(mock(ApiKeyDataService.class));
  private final JwtAuthenticationConverter jwtAuthenticationConverter =
      securityConfig.jwtAuthenticationConverter();

  private Jwt buildJwt(final List<String> roles) {
    final var builder =
        Jwt.withTokenValue("mock-token").header("alg", "RS256").claim("sub", "test-subject");
    if (roles != null) {
      builder.claim("roles", roles);
    }
    return builder.build();
  }

  private Set<String> authorityStrings(final Collection<GrantedAuthority> authorities) {
    return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
  }

  @Test
  @DisplayName("A single entry in the JWT roles claim becomes a ROLE_<name> GrantedAuthority.")
  void singleRoleMapped() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of("ADMIN")));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).contains("ROLE_ADMIN");
  }

  @Test
  @DisplayName("Multiple entries in the roles claim all become distinct ROLE_ authorities.")
  void multipleRolesMapped() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of("ADMIN", "READONLY")));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).contains("ROLE_ADMIN", "ROLE_READONLY");
  }

  @Test
  @DisplayName("A JWT without a roles claim produces zero ROLE_ authorities — no default role is granted.")
  void missingRolesClaimNoAuthorities() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(null));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).noneMatch(a -> a.startsWith("ROLE_"));
  }

  @Test
  @DisplayName("An empty roles array produces zero ROLE_ authorities — empty array is treated like a missing claim.")
  void emptyRolesNoAuthorities() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of()));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).noneMatch(a -> a.startsWith("ROLE_"));
  }

  /**
   * The custom role-mapping does not replace the default {@code scope}-based authority
   * extraction — both run, so a JWT with {@code scope="openid profile"} and {@code roles=[ADMIN]}
   * yields {@code SCOPE_openid}, {@code SCOPE_profile}, and {@code ROLE_ADMIN} side-by-side.
   * Pins this so that switching to a role-only converter would break {@code hasAuthority('SCOPE_*')}
   * checks elsewhere.
   */
  @Test
  @DisplayName("Scope-based authorities (SCOPE_*) and role-based authorities (ROLE_*) coexist on the same JWT.")
  void scopeAuthoritiesPreserved() {
    final Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", "test-subject")
            .claim("scope", "openid profile")
            .claim("roles", List.of("ADMIN"))
            .build();
    final var auth = jwtAuthenticationConverter.convert(jwt);
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).contains("SCOPE_openid", "SCOPE_profile", "ROLE_ADMIN");
  }
}
