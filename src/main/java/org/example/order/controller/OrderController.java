package org.example.order.controller;

import jakarta.validation.Valid;
import org.example.order.domain.OrderStatus;
import org.example.order.dto.CreateOrderRequest;
import org.example.order.dto.OrderResponse;
import org.example.order.dto.UpdateOrderRequest;
import org.example.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for order management
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    
    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Create a new order
     */
    @PostMapping
    ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                             Authentication authentication) {
        String userEmail = authentication.getName();
        logger.info("Received request to create order for user: {}", userEmail);
        
        // In real implementation, get user ID from user service using email
        UUID userId = UUID.randomUUID(); // Mock user ID
        
        OrderResponse order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    
    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        logger.info("Received request to get order by id: {}", id);
        
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Get order by order number
     */
    @GetMapping("/number/{orderNumber}")
    ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber) {
        logger.info("Received request to get order by number: {}", orderNumber);
        
        OrderResponse order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Get current user's orders
     */
    @GetMapping("/my-orders")
    ResponseEntity<Page<OrderResponse>> getCurrentUserOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String userEmail = authentication.getName();
        logger.info("Received request to get orders for current user: {}", userEmail);
        
        // In real implementation, get user ID from user service using email
        UUID userId = UUID.randomUUID(); // Mock user ID
        
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getOrdersForUser(userId, pageable);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders for a specific user (admin endpoint)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    ResponseEntity<Page<OrderResponse>> getOrdersForUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Received request to get orders for user: {}", userId);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getOrdersForUser(userId, pageable);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders by status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status/{status}")
    ResponseEntity<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Received request to get orders by status: {}", status);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Update order
     */
    @PutMapping("/{id}")
    ResponseEntity<OrderResponse> updateOrder(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateOrderRequest request) {
        logger.info("Received request to update order: {}", id);
        
        OrderResponse order = orderService.updateOrder(id, request);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Update order status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable UUID id,
                                                   @RequestParam OrderStatus status) {
        logger.info("Received request to update order {} status to: {}", id, status);
        
        OrderResponse order = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Cancel order
     */
    @PostMapping("/{id}/cancel")
    ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        logger.info("Received request to cancel order: {}", id);
        
        OrderResponse order = orderService.cancelOrder(id);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Get orders that need processing
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/processing-queue")
    ResponseEntity<List<OrderResponse>> getOrdersForProcessing() {
        logger.info("Received request to get orders for processing");
        
        List<OrderResponse> orders = orderService.getOrdersForProcessing();
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get overdue orders
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overdue")
    ResponseEntity<List<OrderResponse>> getOverdueOrders(
            @RequestParam(defaultValue = "24") int hoursOverdue) {
        logger.info("Received request to get orders overdue by {} hours", hoursOverdue);
        
        List<OrderResponse> orders = orderService.getOverdueOrders(hoursOverdue);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get order statistics
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics")
    ResponseEntity<OrderService.OrderStatistics> getOrderStatistics() {
        logger.info("Received request to get order statistics");
        
        OrderService.OrderStatistics statistics = orderService.getOrderStatistics();
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Get daily order count
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics/daily-count")
    ResponseEntity<Long> getDailyOrderCount() {
        logger.info("Received request to get daily order count");
        
        long count = orderService.getDailyOrderCount();
        return ResponseEntity.ok(count);
    }
    
    /**
     * Get total sales amount for date range
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics/sales")
    ResponseEntity<BigDecimal> getTotalSalesAmount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        logger.info("Received request to get total sales amount from {} to {}", startDate, endDate);
        
        BigDecimal totalSales = orderService.getTotalSalesAmount(startDate, endDate);
        return ResponseEntity.ok(totalSales);
    }
    
    /**
     * Get orders by date range
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/date-range")
    ResponseEntity<Page<OrderResponse>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Received request to get orders from {} to {}", startDate, endDate);
        
        // This would require adding a method to OrderService
        // For now, return empty page as placeholder
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = Page.empty(pageable);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get order summary for current user
     */
    @GetMapping("/my-orders/summary")
    ResponseEntity<OrderSummary> getCurrentUserOrderSummary(Authentication authentication) {
        String userEmail = authentication.getName();
        logger.info("Received request to get order summary for current user: {}", userEmail);
        
        // Mock implementation - in real scenario, this would call OrderService
        OrderSummary summary = new OrderSummary(
                5L,  // totalOrders
                2L,  // pendingOrders
                3L,  // completedOrders
                BigDecimal.valueOf(1299.95) // totalSpent
        );
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Search orders (placeholder for future implementation)
     */
    @GetMapping("/search")
    ResponseEntity<Page<OrderResponse>> searchOrders(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("Received request to search orders with query: {}", query);
        
        // Placeholder implementation
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> orders = Page.empty(pageable);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get order status history (placeholder for future implementation)
     */
    @GetMapping("/{id}/status-history")
    ResponseEntity<List<OrderStatusHistory>> getOrderStatusHistory(@PathVariable UUID id) {
        logger.info("Received request to get status history for order: {}", id);
        
        // Placeholder implementation
        List<OrderStatusHistory> history = List.of();
        return ResponseEntity.ok(history);
    }
    
    /**
     * Bulk update order status (admin endpoint)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/bulk-status-update")
    ResponseEntity<BulkUpdateResult> bulkUpdateOrderStatus(
            @RequestBody BulkStatusUpdateRequest request) {
        logger.info("Received request to bulk update {} orders to status: {}", 
                   request.orderIds().size(), request.newStatus());
        
        // Placeholder implementation
        BulkUpdateResult result = new BulkUpdateResult(
                request.orderIds().size(),
                request.orderIds().size(),
                0
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Order summary record
     */
    public record OrderSummary(
            long totalOrders,
            long pendingOrders,
            long completedOrders,
            BigDecimal totalSpent
    ) {}
    
    /**
     * Order status history record
     */
    public record OrderStatusHistory(
            OrderStatus status,
            Instant timestamp,
            String notes
    ) {}
    
    /**
     * Bulk status update request
     */
    public record BulkStatusUpdateRequest(
            List<UUID> orderIds,
            OrderStatus newStatus
    ) {}
    
    /**
     * Bulk update result
     */
    public record BulkUpdateResult(
            int totalRequested,
            int successful,
            int failed
    ) {}
}