package org.example.order.config;

import org.example.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpringBootTest-based validation of security beans and token resolution behavior.
 */
@SpringBootTest(properties = {
        // Exclude DB/JPA/Flyway to avoid requiring a datasource
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        // Provide required JWT properties (HS256 requires >= 256-bit secret)
        "security.jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "security.jwt.cookie-name=auth_token"
})
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private BearerTokenResolver bearerTokenResolver;

    @MockitoBean
    private OrderService orderService; // needed for the Spring context

    @Test
    @DisplayName("SecurityFilterChain bean is created and BearerTokenResolver is our cookie-first resolver")
    void contextLoadsAndBeansPresent() {
        assertThat(securityFilterChain).isNotNull();
        assertThat(bearerTokenResolver).isInstanceOf(CookieOrHeaderBearerTokenResolver.class);
    }

    @Test
    @DisplayName("BearerTokenResolver resolves from cookie first, else falls back to Authorization header")
    void cookieFirstThenHeader() {
        // Cookie preferred
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setCookies(new jakarta.servlet.http.Cookie("auth_token", "Bearer cookie-token"));
        assertThat(bearerTokenResolver.resolve(req1)).isEqualTo("cookie-token");

        // Header fallback
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.addHeader("Authorization", "Bearer header-token");
        assertThat(bearerTokenResolver.resolve(req2)).isEqualTo("header-token");

        // Empty cookie -> header
        MockHttpServletRequest req3 = new MockHttpServletRequest();
        req3.setCookies(new jakarta.servlet.http.Cookie("auth_token", " "));
        req3.addHeader("Authorization", "Bearer h2");
        assertThat(bearerTokenResolver.resolve(req3)).isEqualTo("h2");
    }
}
