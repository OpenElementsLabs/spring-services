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

/**
 * Unit tests for {@link AuthService} — the thin wrapper around {@link SecurityContextHolder} that
 * extracts authentication details and JWT claims for the rest of the security stack.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three concerns are verified:
 *
 * <ol>
 *   <li><b>Accessors against the SecurityContext.</b> {@code getAuthentication()} returns the
 *       bound {@link org.springframework.security.core.Authentication} or fails fast with
 *       {@link IllegalStateException} when none is present; {@code getPrincipalObject()} and
 *       {@code getPrincipalJwt()} narrow to the expected principal type or reject mismatches.
 *   <li><b>JWT claim extraction into {@link UserInformation}.</b> The {@code preferred_username
 *       → email → sub} fallback chain for {@code userName}, the {@code picture}-to-{@code
 *       avatarUrl} mapping with blank-claim normalisation to {@code null}, and the
 *       {@code externalId == sub} identity that downstream code relies on.
 *   <li><b>Non-JWT principals.</b> {@code getUserInformation()} returns
 *       {@link java.util.Optional#empty()} for {@link ApiKeyEntity} and {@link String} principals
 *       rather than throwing — this lets {@code UserService} treat API-key requests as
 *       "no JWT bound" without special-casing.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests. A real {@link AuthService} is constructed; the
 * {@link SecurityContextHolder} is populated per-test with a {@link TestingAuthenticationToken}
 * carrying the principal under test, then cleared in {@code @AfterEach} so context state does
 * not leak between tests.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. Real {@link Jwt} instances are built via {@code
 * Jwt.withTokenValue(...)} and a real {@link TestingAuthenticationToken} carries them through
 * the real {@link SecurityContextHolder} — the production code path is exercised verbatim.
 */
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
    @DisplayName("getAuthentication() returns the Authentication bound to the SecurityContextHolder.")
    void shouldReturnAuthenticationWhenPresent() {
      // GIVEN
      final TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");
      SecurityContextHolder.getContext().setAuthentication(auth);

      // WHEN & THEN
      assertThat(authService.getAuthentication()).isSameAs(auth);
    }

    @Test
    @DisplayName("getAuthentication() fails fast with IllegalStateException when no Authentication is bound.")
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
    @DisplayName("getPrincipalObject() returns the raw principal stored on the Authentication.")
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
    @DisplayName("getPrincipalJwt() narrows to the Jwt instance when the principal is a JWT.")
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
    @DisplayName("getPrincipalJwt() throws IllegalStateException when the principal is not a JWT — caller must not assume JWT semantics.")
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
    @DisplayName("All standard OIDC claims (sub, name, email, picture, preferred_username) flow into UserInformation fields.")
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

    /**
     * Documents tier two of the {@code userName} fallback chain: when an IdP omits {@code
     * preferred_username} (Auth0's default for some social providers does this), {@code email}
     * takes its place so downstream code always has a non-blank handle.
     */
    @Test
    @DisplayName("Missing preferred_username falls back to email — tier two of the userName chain.")
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

    /**
     * Documents tier three of the {@code userName} fallback chain: service-account tokens
     * frequently carry neither {@code preferred_username} nor {@code email}, so the final
     * fallback is the JWT subject itself — guaranteeing a non-blank handle for every principal.
     */
    @Test
    @DisplayName("Missing preferred_username and email fall back to sub — tier three, the last resort.")
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

    /**
     * A whitespace-only {@code preferred_username} is treated as missing — otherwise a single
     * space would be persisted as the canonical handle. Normalising blanks to "absent" forces
     * the fallback to the next tier.
     */
    @Test
    @DisplayName("A blank (whitespace-only) preferred_username is treated as missing and falls back to email.")
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

    /**
     * Pins the {@code externalId == sub == id} invariant. {@code UserService} relies on this to
     * use the JWT subject as the SCIM-side external identifier when issuing the second-tier
     * lookup; a divergence would break SCIM-pre-provisioned row adoption.
     */
    @Test
    @DisplayName("externalId mirrors the JWT subject — same value flows into both UserInformation.id and externalId.")
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
    @DisplayName("A missing picture claim normalises to a null avatarUrl on UserInformation.")
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

    /**
     * Symmetric to the blank-{@code preferred_username} normalisation: a whitespace-only
     * {@code picture} claim is treated as absent, so {@code avatarUrl} is {@code null} rather
     * than a string of spaces — saves a JPA drift-sync flap on next login.
     */
    @Test
    @DisplayName("A blank (whitespace-only) picture claim normalises to a null avatarUrl, not a string of spaces.")
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
    @DisplayName("A blank JWT subject throws IllegalStateException — sub is the one claim that must never be empty.")
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

    /**
     * Key contract: API-key principals carry no JWT claims, so {@code getUserInformation()}
     * returns {@code Optional.empty()} rather than throwing. This lets {@code UserService}
     * treat API-key requests as "no JWT bound" without special-casing the principal type.
     */
    @Test
    @DisplayName("getUserInformation() returns Optional.empty() when the principal is an ApiKeyEntity — no JWT claims to extract.")
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
    @DisplayName("getUserInformation() returns Optional.empty() when the principal is a bare String (e.g. anonymous, test stubs).")
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
    @DisplayName("getPrincipal() returns an AuthenticatedPrincipal whose name is the JWT subject when the principal is a Jwt.")
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
    @DisplayName("getPrincipal() wraps a String principal into an AuthenticatedPrincipal whose name is the string itself.")
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
    @DisplayName("getPrincipal() passes through an existing AuthenticatedPrincipal without re-wrapping.")
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
