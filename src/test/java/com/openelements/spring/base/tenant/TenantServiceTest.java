package com.openelements.spring.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Mockito-style unit tests for {@link TenantService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The contract of {@link TenantService#getCurrentTenant()}: extract the tenant id from the
 * authenticated principal's {@code getName()} value, throw {@link IllegalStateException} when
 * the value is missing (null or blank), and reject a null {@link AuthService} at construction.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 + Mockito. The single collaborator — {@link AuthService} — is mocked. The
 * test injects different {@link AuthenticatedPrincipal} values (returning "tenant-abc", {@code
 * null}, "   ") via {@code when(authService.getPrincipal()).thenReturn(...)} and asserts the
 * service's reaction.
 *
 * <p><b>Mock-Audit.</b> One mock, justified: {@link AuthService} reads from a Spring Security
 * context that would otherwise require populating {@link
 * org.springframework.security.core.context.SecurityContextHolder} per test. The mock isolates
 * {@code TenantService}'s decision logic — that's the unit under test.
 */
@DisplayName("TenantService")
class TenantServiceTest {

  private final AuthService authService = mock(AuthService.class);
  private final TenantService tenantService = new TenantService(authService);

  @Test
  @DisplayName("getCurrentTenant() returns the authenticated principal's name as the tenant id.")
  void shouldReturnTenantIdFromPrincipal() {
    // GIVEN
    final AuthenticatedPrincipal principal = () -> "tenant-abc";
    when(authService.getPrincipal()).thenReturn(principal);

    // WHEN
    final String tenant = tenantService.getCurrentTenant();

    // THEN
    assertThat(tenant).isEqualTo("tenant-abc");
  }

  @Test
  @DisplayName("A null principal name produces IllegalStateException — no silent default tenant.")
  void shouldThrowWhenTenantIdIsNull() {
    // GIVEN
    final AuthenticatedPrincipal principal = () -> null;
    when(authService.getPrincipal()).thenReturn(principal);

    // WHEN & THEN
    assertThatThrownBy(() -> tenantService.getCurrentTenant())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No tenant id found");
  }

  @Test
  @DisplayName("A whitespace-only principal name is treated as missing — IllegalStateException.")
  void shouldThrowWhenTenantIdIsBlank() {
    // GIVEN
    final AuthenticatedPrincipal principal = () -> "   ";
    when(authService.getPrincipal()).thenReturn(principal);

    // WHEN & THEN
    assertThatThrownBy(() -> tenantService.getCurrentTenant())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No tenant id found");
  }

  @Test
  @DisplayName("TenantService constructor rejects a null AuthService with NullPointerException.")
  void shouldRejectNullAuthService() {
    assertThatThrownBy(() -> new TenantService(null)).isInstanceOf(NullPointerException.class);
  }
}
