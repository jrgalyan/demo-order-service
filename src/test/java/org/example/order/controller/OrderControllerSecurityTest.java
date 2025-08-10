package org.example.order.controller;

import jakarta.servlet.http.Cookie;
import org.example.order.domain.OrderStatus;
import org.example.order.dto.OrderResponse;
import org.example.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpringBootTest-based security validation that exercises the real security configuration
 * (JWT via HttpOnly cookie) and verifies endpoint access control without using reflection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        // Exclude DB/Flyway to avoid requiring a datasource in tests
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        // Provide required JWT properties (HS256 requires >= 256-bit secret)
        "security.jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "security.jwt.cookie-name=auth_token"
})
@AutoConfigureMockMvc
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrderService orderService;

    private Jwt jwtWithRoles(String token, String... roles) {
        Instant now = Instant.now();
        return new Jwt(
                token,
                now,
                now.plusSeconds(600),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", "user@example.com",
                        "roles", List.of((Object[]) roles)
                )
        );
    }

    private Cookie authCookie(String token) {
        Cookie c = new Cookie("auth_token", token);
        c.setHttpOnly(true);
        return c;
    }

    @BeforeEach
    void setupStubs() {
        when(orderService.getOrdersForUser(any(UUID.class), any(Pageable.class)))
                .thenAnswer(inv -> Page.empty(PageRequest.of(0, 1)));
        when(orderService.getOrdersByStatus(any(OrderStatus.class), any(Pageable.class)))
                .thenAnswer(inv -> Page.empty(PageRequest.of(0, 1)));
        when(orderService.updateOrderStatus(any(UUID.class), any(OrderStatus.class)))
                .thenReturn(new OrderResponse(null, null, null, OrderStatus.CONFIRMED, BigDecimal.ZERO,
                        null, null, null, 0, 0, null, null));
        when(orderService.getOrdersForProcessing()).thenReturn(List.of());
        when(orderService.getOverdueOrders(anyInt())).thenReturn(List.of());
        when(orderService.getOrderStatistics()).thenReturn(new OrderService.OrderStatistics(0, BigDecimal.ZERO, List.of()));
        when(orderService.getDailyOrderCount()).thenReturn(0L);
        when(orderService.getTotalSalesAmount(any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Unauthenticated requests to admin endpoints return 401")
    void unauthenticatedRequestsReturn401() throws Exception {
        UUID uid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        String start = "2025-01-01T00:00:00Z";
        String end = "2025-01-02T00:00:00Z";

        mockMvc.perform(get("/api/v1/orders/processing-queue"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/user/" + uid).param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/status/CONFIRMED").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/orders/" + oid + "/status").param("status", "CONFIRMED"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/overdue").param("hoursOverdue", "24"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/statistics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/statistics/daily-count"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/statistics/sales").param("startDate", start).param("endDate", end))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/date-range").param("startDate", start).param("endDate", end).param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized());

        String bulkJson = "{\"orderIds\":[\"" + UUID.randomUUID() + "\"],\"newStatus\":\"CONFIRMED\"}";
        mockMvc.perform(patch("/api/v1/orders/bulk-status-update").contentType(MediaType.APPLICATION_JSON).content(bulkJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-admin users receive 403 on admin endpoints")
    void userRoleGetsForbiddenOnAdminEndpoints() throws Exception {
        String token = "user-token";
        when(jwtDecoder.decode(token)).thenReturn(jwtWithRoles(token, "USER"));

        UUID uid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        String start = "2025-01-01T00:00:00Z";
        String end = "2025-01-02T00:00:00Z";

        mockMvc.perform(get("/api/v1/orders/processing-queue").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/user/" + uid).param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/status/CONFIRMED").param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/orders/" + oid + "/status").param("status", "CONFIRMED").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/overdue").param("hoursOverdue", "24").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/statistics").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/statistics/daily-count").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/statistics/sales").param("startDate", start).param("endDate", end).cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/date-range").param("startDate", start).param("endDate", end).param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isForbidden());

        String bulkJson = "{\"orderIds\":[\"" + UUID.randomUUID() + "\"],\"newStatus\":\"CONFIRMED\"}";
        mockMvc.perform(patch("/api/v1/orders/bulk-status-update").contentType(MediaType.APPLICATION_JSON).content(bulkJson).cookie(authCookie(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin users can access all admin endpoints")
    void adminRoleCanAccessAdminEndpoints() throws Exception {
        String token = "admin-token";
        when(jwtDecoder.decode(token)).thenReturn(jwtWithRoles(token, "ADMIN"));

        UUID uid = UUID.randomUUID();
        UUID oid = UUID.randomUUID();
        String start = "2025-01-01T00:00:00Z";
        String end = "2025-01-02T00:00:00Z";

        mockMvc.perform(get("/api/v1/orders/processing-queue").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/user/" + uid).param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/status/CONFIRMED").param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/orders/" + oid + "/status").param("status", "CONFIRMED").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/overdue").param("hoursOverdue", "24").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/statistics").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/statistics/daily-count").cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/statistics/sales").param("startDate", start).param("endDate", end).cookie(authCookie(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/date-range").param("startDate", start).param("endDate", end).param("page", "0").param("size", "1").cookie(authCookie(token)))
                .andExpect(status().isOk());

        String bulkJson = "{\"orderIds\":[\"" + UUID.randomUUID() + "\"],\"newStatus\":\"CONFIRMED\"}";
        mockMvc.perform(patch("/api/v1/orders/bulk-status-update").contentType(MediaType.APPLICATION_JSON).content(bulkJson).cookie(authCookie(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator health endpoint is public")
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
