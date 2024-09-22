package com.openelements.spring.services.security.fusionauth;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class FusionAuthJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLES_CLAIM = "roles";

    private static final String EMAIL_CLAIM = "email";

    private static final String AUD_CLAIM = "aud";
    public static final String N_A = "n/a";

    private final List<String> audiences;

    public FusionAuthJwtConverter(@NonNull final List<String> audiences) {
        this.audiences = Objects.requireNonNull(audiences, "audiences must not be null");
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull final Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        final String email = jwt.getClaimAsString(EMAIL_CLAIM);
        if (!hasAudience(jwt)) {
            return new UsernamePasswordAuthenticationToken(email, N_A);
        } else {
            final Collection<GrantedAuthority> authorities = extractRoles(jwt);
            return new UsernamePasswordAuthenticationToken(email, N_A, authorities);
        }
    }

    private boolean hasAudience(@NonNull final Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        return jwt.hasClaim(AUD_CLAIM)
                && jwt.getClaimAsStringList(AUD_CLAIM)
                .stream()
                .anyMatch(audiences::contains);
    }

    private List<GrantedAuthority> extractRoles(@NonNull final Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        return jwt.hasClaim(ROLES_CLAIM)
                ? jwt.getClaimAsStringList(ROLES_CLAIM)
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList())
                : List.of();
    }
}
