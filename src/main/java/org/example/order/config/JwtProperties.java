package org.example.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Name of the HttpOnly cookie that carries the JWT access token.
     */
    @NotBlank
    private String cookieName = "auth_token"; // sensible default; override in properties

    /**
     * Secret used to validate HMAC-signed (HS256) JWTs. Prefer Base64-encoded value via env var.
     */
    @NotBlank
    private String secret;

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}