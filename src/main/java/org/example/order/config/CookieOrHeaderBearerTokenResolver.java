package org.example.order.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * Resolves a Bearer token from an HttpOnly cookie first, then falls back to the Authorization header.
 */
class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;
    private final DefaultBearerTokenResolver headerResolver;

    CookieOrHeaderBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
        this.headerResolver = new DefaultBearerTokenResolver();
        this.headerResolver.setAllowUriQueryParameter(false);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String fromCookie = resolveFromCookie(request, cookieName);
        if (StringUtils.hasText(fromCookie)) {
            String value = fromCookie.trim();
            if (StringUtils.startsWithIgnoreCase(value, "Bearer ")) {
                value = value.substring(7).trim();
            }
            return value;
        }
        return headerResolver.resolve(request);
    }

    @Nullable
    private static String resolveFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                String value = c.getValue();
                return StringUtils.hasText(value) ? value : null;
            }
        }
        return null;
    }
}
