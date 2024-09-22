package com.openelements.spring.services.security.fusionauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

public class FusionAuthBearerTokenResolver implements BearerTokenResolver {

    public static final String APP_AT = "app.at";

    @Override
    public String resolve(final @NonNull HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final Cookie[] cookies = request.getCookies();
        if(cookies == null || cookies.length == 0) {
            return null;
        }
        return Arrays.stream(cookies).
                filter(cookie -> cookie.getName().equals(APP_AT))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

}
