package com.openelements.spring.services.tenant;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class TenantService {

    private final UserPrincipalService userPrincipalService;

    @Autowired
    public TenantService(final UserPrincipalService userPrincipalService) {
        this.userPrincipalService = Objects.requireNonNull(userPrincipalService, "userPrincipalService must not be null");
    }

    public String getCurrentTenant() {
        final String id = userPrincipalService.getPrincipal().getName();
        if(id == null || id.isBlank()) {
            throw new IllegalStateException("No tenant id found");
        }
        return id;
    }

}
