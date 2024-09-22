package com.openelements.spring.services.tenant;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserPrincipalService {

    public AuthenticatedPrincipal getPrincipal() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null) {
            throw new IllegalStateException("No authentication found");
        }
        final Object principal = authentication.getPrincipal();
        if (principal == null) {
            throw new IllegalStateException("No principal found");
        }
        if (principal instanceof AuthenticatedPrincipal userPrincipal) {
            return userPrincipal;
        }
        if (principal instanceof String id) {
            return new AuthenticatedPrincipal() {
                @Override
                public String getName() {
                    return id;
                }
            };
        }
        throw new IllegalStateException("Principal can not be defined");
    }
}
