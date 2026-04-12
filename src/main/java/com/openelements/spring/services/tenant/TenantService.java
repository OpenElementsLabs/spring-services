package com.openelements.spring.services.tenant;

import com.openelements.spring.services.security.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;


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
