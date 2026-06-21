package com.openelements.spring.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.services.apikey.ApiKeyEntity;
import java.time.Instant;
import java.util.UUID;
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
              .claim("picture", "https://auth.example.com/avatar.jpg")
              .claim("preferred_username", "hendrik")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final UserInformation info = authService.getUserInformation().orElseThrow();

      // THEN
      assertThat(info.id()).isEqualTo("auth0|456");
      assertThat(info.name()).isEqualTo("Hendrik Ebbers");
      assertThat(info.email()).isEqualTo("hendrik@example.com");
      assertThat(info.avatarUrl()).isEqualTo("https://auth.example.com/avatar.jpg");
      assertThat(info.userName()).isEqualTo("hendrik");
      assertThat(info.externalId()).isEqualTo("auth0|456");
    }

    @Test
    void userNameFallsBackToEmailWhenPreferredUsernameMissing() {
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("xyz789")
              .claim("email", "bob@example.com")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(jwt, null));

      final UserInformation info = authService.getUserInformation().orElseThrow();

      assertThat(info.userName()).isEqualTo("bob@example.com");
    }

    @Test
    void userNameFallsBackToSubWhenPreferredUsernameAndEmailMissing() {
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("svc-123")
              .claim("name", "Service Account")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(jwt, null));

      final UserInformation info = authService.getUserInformation().orElseThrow();

      assertThat(info.userName()).isEqualTo("svc-123");
    }

    @Test
    void userNameFallsBackToEmailWhenPreferredUsernameIsBlank() {
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("blank-pref-sub")
              .claim("preferred_username", "   ")
              .claim("email", "blank@example.com")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(jwt, null));

      final UserInformation info = authService.getUserInformation().orElseThrow();

      assertThat(info.userName()).isEqualTo("blank@example.com");
    }

    @Test
    void externalIdMirrorsSubject() {
      final Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "RS256")
              .subject("authentik|abc123")
              .claim("preferred_username", "alice")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(jwt, null));

      final UserInformation info = authService.getUserInformation().orElseThrow();

      assertThat(info.externalId()).isEqualTo("authentik|abc123");
      assertThat(info.id()).isEqualTo("authentik|abc123");
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
      final UserInformation info = authService.getUserInformation().orElseThrow();

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
              .claim("picture", "   ")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final UserInformation info = authService.getUserInformation().orElseThrow();

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

    @Test
    @DisplayName("returns empty Optional when principal is an ApiKeyEntity")
    void shouldReturnEmptyForApiKeyPrincipal() {
      // GIVEN
      final ApiKeyEntity entity = new ApiKeyEntity();
      entity.setId(UUID.randomUUID());
      entity.setName("CRM key");
      entity.setKeyHash("hash");
      entity.setKeyPrefix("crm_test...1234");
      entity.setCreatedBy("admin");
      final TestingAuthenticationToken auth = new TestingAuthenticationToken(entity, null);
      auth.setAuthenticated(true);
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final java.util.Optional<UserInformation> info = authService.getUserInformation();

      // THEN
      assertThat(info).isEmpty();
    }

    @Test
    @DisplayName("returns empty Optional when principal is a String")
    void shouldReturnEmptyForStringPrincipal() {
      // GIVEN
      final TestingAuthenticationToken auth =
          new TestingAuthenticationToken("string-principal", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN
      final java.util.Optional<UserInformation> info = authService.getUserInformation();

      // THEN
      assertThat(info).isEmpty();
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
