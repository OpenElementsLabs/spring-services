package com.openelements.spring.base.tenant;

import com.openelements.spring.base.security.AuthService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

  private final AuthService authService;

  @Autowired
  public TenantService(final AuthService authService) {
    this.authService = Objects.requireNonNull(authService, "authService must not be null");
  }

  public String getCurrentTenant() {
    final String id = authService.getPrincipal().getName();
    if (id == null || id.isBlank()) {
      throw new IllegalStateException("No tenant id found");
    }
    return id;
  }
}
