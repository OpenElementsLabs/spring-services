package com.openelements.spring.base.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  @NonNull
  public Authentication getAuthentication() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("No authentication found");
    }
    return authentication;
  }

  @NonNull
  public Object getPrincipalObject() {
    final Object principal = getAuthentication().getPrincipal();
    if (principal == null) {
      throw new IllegalStateException("No principal found");
    }
    return principal;
  }

  @NonNull
  public Jwt getPrincipalJwt() {
    final Object principal = getPrincipalObject();
    if (principal == null) {
      throw new IllegalStateException("No principal found");
    }
    if (principal instanceof Jwt jwt) {
      return jwt;
    }
    throw new IllegalStateException("Principal is not a JWT");
  }

  @NonNull
  public UserInformation getUserInformation() {
    final Jwt jwt = getPrincipalJwt();
    final String sub = jwt.getSubject();
    if (sub == null || sub.isBlank()) {
      throw new IllegalStateException("No sub found");
    }
    return new UserInformation(
        jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
  }

  @NonNull
  public AuthenticatedPrincipal getPrincipal() {
    final Object principal = getPrincipalObject();
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
    if (principal instanceof Jwt jwt) {
      return new AuthenticatedPrincipal() {
        @Override
        public String getName() {
          return jwt.getSubject();
        }
      };
    }
    throw new IllegalStateException("Principal can not be defined");
  }
}
