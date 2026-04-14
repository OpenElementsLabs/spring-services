package com.openelements.spring.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticatedPrincipal;

@DisplayName("TenantService")
class TenantServiceTest {

  private final AuthService authService = mock(AuthService.class);
  private final TenantService tenantService = new TenantService(authService);

  @Test
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
  void shouldRejectNullAuthService() {
    assertThatThrownBy(() -> new TenantService(null)).isInstanceOf(NullPointerException.class);
  }
}
