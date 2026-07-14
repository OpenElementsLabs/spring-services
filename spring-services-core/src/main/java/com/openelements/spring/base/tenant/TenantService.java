package com.openelements.spring.base.tenant;

import com.openelements.spring.base.security.AuthService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Resolves the tenant id of the current request.
 *
 * <p>The tenant id is taken verbatim from the {@code name} of the authenticated principal as
 * exposed by {@link AuthService#getPrincipal()}. For JWT-authenticated requests this is the {@code
 * sub} claim; for API-key requests it is the API key entity's name.
 */
@Service
public class TenantService {

  private final AuthService authService;

  /**
   * Creates a new tenant service.
   *
   * @param authService the service used to resolve the authenticated principal
   */
  @Autowired
  public TenantService(final AuthService authService) {
    this.authService = Objects.requireNonNull(authService, "authService must not be null");
  }

  /**
   * Returns the tenant id that owns the current request.
   *
   * @return the current tenant id, never {@code null} or blank
   * @throws IllegalStateException if no authentication is bound to the current request, or if the
   *     authenticated principal does not expose a usable name
   */
  public String getCurrentTenant() {
    final String id = authService.getPrincipal().getName();
    if (id == null || id.isBlank()) {
      throw new IllegalStateException("No tenant id found");
    }
    return id;
  }
}
