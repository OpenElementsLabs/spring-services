package com.openelements.spring.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("AuthService")
class AuthServiceTest {

  private final AuthService authService = new AuthService();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("getAuthentication")
  class GetAuthenticationTest {

    @Test
    void shouldReturnAuthenticationWhenPresent() {
      // GIVEN
      final TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN & THEN
      assertThat(authService.getAuthentication()).isSameAs(auth);
    }

    @Test
    void shouldThrowWhenNoAuthentication() {
      // GIVEN
      SecurityContextHolder.clearContext();

      // WHEN & THEN
      assertThatThrownBy(() -> authService.getAuthentication())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No authentication found");
    }
  }

  @Nested
  @DisplayName("getPrincipalObject")
  class GetPrincipalObjectTest {

    @Test
    void shouldReturnPrincipal() {
      // GIVEN
      final TestingAuthenticationToken auth = new TestingAuthenticationToken("user123", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final Object principal = authService.getPrincipalObject();

      // THEN
      assertThat(principal).isEqualTo("user123");
    }
  }

  @Nested
  @DisplayName("getPrincipalJwt")
  class GetPrincipalJwtTest {

    @Test
    void shouldReturnJwtWhenPrincipalIsJwt() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("auth0|123")
              .claim("name", "Test User")
              .claim("email", "test@example.com")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final Jwt result = authService.getPrincipalJwt();

      // THEN
      assertThat(result).isSameAs(jwt);
    }

    @Test
    void shouldThrowWhenPrincipalIsNotJwt() {
      // GIVEN
      final TestingAuthenticationToken auth =
          new TestingAuthenticationToken("stringPrincipal", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN & THEN
      assertThatThrownBy(() -> authService.getPrincipalJwt())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Principal is not a JWT");
    }
  }

  @Nested
  @DisplayName("getUserInformation")
  class GetUserInformationTest {

    @Test
    void shouldExtractUserInformationFromJwt() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("auth0|456")
              .claim("name", "Hendrik Ebbers")
              .claim("email", "hendrik@example.com")
              .claim("avatar", "https://auth.example.com/avatar.jpg")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final UserInformation info = authService.getUserInformation();

      // THEN
      assertThat(info.id()).isEqualTo("auth0|456");
      assertThat(info.name()).isEqualTo("Hendrik Ebbers");
      assertThat(info.email()).isEqualTo("hendrik@example.com");
      assertThat(info.avatarUrl()).isEqualTo("https://auth.example.com/avatar.jpg");
    }

    @Test
    void shouldReturnNullAvatarUrlWhenAvatarClaimMissing() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("auth0|789")
              .claim("name", "No Avatar User")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final UserInformation info = authService.getUserInformation();

      // THEN
      assertThat(info.avatarUrl()).isNull();
    }

    @Test
    void shouldReturnNullAvatarUrlWhenAvatarClaimIsBlank() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("auth0|blank")
              .claim("avatar", "   ")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final UserInformation info = authService.getUserInformation();

      // THEN
      assertThat(info.avatarUrl()).isNull();
    }

    @Test
    void shouldThrowWhenSubjectIsBlank() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN & THEN
      assertThatThrownBy(() -> authService.getUserInformation())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No sub found");
    }
  }

  @Nested
  @DisplayName("getPrincipal")
  class GetPrincipalTest {

    @Test
    void shouldReturnAuthenticatedPrincipalForJwt() {
      // GIVEN
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("jwt-subject")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final AuthenticatedPrincipal principal = authService.getPrincipal();

      // THEN
      assertThat(principal.getName()).isEqualTo("jwt-subject");
    }

    @Test
    void shouldReturnAuthenticatedPrincipalForStringPrincipal() {
      // GIVEN
      final TestingAuthenticationToken auth = new TestingAuthenticationToken("string-user", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final AuthenticatedPrincipal principal = authService.getPrincipal();

      // THEN
      assertThat(principal.getName()).isEqualTo("string-user");
    }

    @Test
    void shouldReturnAuthenticatedPrincipalDirectly() {
      // GIVEN
      final AuthenticatedPrincipal directPrincipal = () -> "direct-name";
      final TestingAuthenticationToken auth =
          new TestingAuthenticationToken(directPrincipal, "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final AuthenticatedPrincipal result = authService.getPrincipal();

      // THEN
      assertThat(result.getName()).isEqualTo("direct-name");
    }
  }
}
