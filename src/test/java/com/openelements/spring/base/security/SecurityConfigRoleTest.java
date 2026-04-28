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

@DisplayName("SecurityConfig JWT Role Mapping")
class SecurityConfigRoleTest {

  private final SecurityConfig securityConfig =
      new SecurityConfig(mock(ApiKeyDataService.class));
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
  @DisplayName("Single role is mapped to ROLE_ authority")
  void singleRoleMapped() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of("ADMIN")));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).contains("ROLE_ADMIN");
  }

  @Test
  @DisplayName("Multiple roles are mapped to multiple authorities")
  void multipleRolesMapped() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of("ADMIN", "READONLY")));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).contains("ROLE_ADMIN", "ROLE_READONLY");
  }

  @Test
  @DisplayName("Missing roles claim results in no ROLE_ authorities")
  void missingRolesClaimNoAuthorities() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(null));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).noneMatch(a -> a.startsWith("ROLE_"));
  }

  @Test
  @DisplayName("Empty roles array results in no ROLE_ authorities")
  void emptyRolesNoAuthorities() {
    final var auth = jwtAuthenticationConverter.convert(buildJwt(List.of()));
    final Set<String> authorities = authorityStrings(auth.getAuthorities());
    assertThat(authorities).noneMatch(a -> a.startsWith("ROLE_"));
  }

  @Test
  @DisplayName("Scope-based authorities are preserved alongside role authorities")
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
