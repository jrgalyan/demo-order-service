package org.example.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.exception.BusinessException;
import org.example.common.exception.ResourceNotFoundException;
import org.example.order.client.NotificationServiceClient;
import org.example.order.client.PaymentServiceClient;
import org.example.order.client.ProductServiceClient;
import org.example.order.domain.Order;
import org.example.order.domain.OrderItem;
import org.example.order.domain.OrderStatus;
import org.example.order.dto.AddressRequest;
import org.example.order.dto.AddressResponse;
import org.example.order.dto.CreateOrderRequest;
import org.example.order.dto.OrderItemRequest;
import org.example.order.dto.OrderItemResponse;
import org.example.order.dto.OrderResponse;
import org.example.order.dto.PaymentRequest;
import org.example.order.dto.PaymentResponse;
import org.example.order.dto.ProductInfo;
import org.example.order.dto.UpdateOrderRequest;
import org.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Service for order management operations
 */
@Service
@Transactional
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    
    public OrderService(OrderRepository orderRepository, 
                       ObjectMapper objectMapper,
                       ProductServiceClient productServiceClient,
                       PaymentServiceClient paymentServiceClient,
                       NotificationServiceClient notificationServiceClient) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.productServiceClient = productServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.notificationServiceClient = notificationServiceClient;
    }
    
    /**
     * Create a new order
     */
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        logger.info("Creating new order for user: {}", userId);
        
        try {
            // Validate and get product information from Product Service
            List<ProductInfo> products = validateAndGetProducts(request.items());
            
            // Check inventory availability for all products
            validateInventoryAvailability(request.items(), products);
            
            // Calculate total amount using real product prices
            BigDecimal totalAmount = calculateTotalAmountFromProducts(request.items(), products);
            
            // Generate unique order number
            String orderNumber = generateOrderNumber();
            
            // Convert addresses to JSON
            String shippingAddressJson = convertAddressToJson(request.shippingAddress());
            String billingAddressJson = convertAddressToJson(request.billingAddress());
            
            // Create order
            Order order = new Order(orderNumber, userId, totalAmount, shippingAddressJson, billingAddressJson);
            
            // Add order items with real product details
            for (OrderItemRequest itemRequest : request.items()) {
                ProductInfo product = findProductById(products, itemRequest.productId());
                
                OrderItem orderItem = new OrderItem(order, itemRequest.productId(), 
                                                   product.name(), product.sku(), 
                                                   itemRequest.quantity(), product.price());
                order.addItem(orderItem);
            }
            
            // Save order first
            Order savedOrder = orderRepository.save(order);
            
            // Reserve inventory for all products
            reserveInventoryForOrder(request.items());
            
            // Send order confirmation notification
            sendOrderConfirmationNotification(userId, savedOrder);
            
            logger.info("Successfully created order with number: {}", orderNumber);
            return toOrderResponse(savedOrder);
            
        } catch (ProductServiceClient.ProductServiceException e) {
            logger.error("Product service error while creating order: {}", e.getMessage());
            throw new BusinessException("Failed to validate products: " + e.getMessage(), "PRODUCT_VALIDATION_FAILED");
        } catch (Exception e) {
            logger.error("Unexpected error while creating order: {}", e.getMessage(), e);
            throw new BusinessException("Failed to create order: " + e.getMessage(), "ORDER_CREATION_FAILED");
        }
    }
    
    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        
        return toOrderResponse(order);
    }
    
    /**
     * Get order by order number
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order with number '" + orderNumber + "' not found"));
        
        return toOrderResponse(order);
    }
    
    /**
     * Get orders for a user
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersForUser(UUID userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(this::toOrderResponse);
    }
    
    /**
     * Get orders by status
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return orders.map(this::toOrderResponse);
    }
    
    /**
     * Update order status
     */
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        logger.info("Updating order {} to status: {}", orderId, newStatus);
        
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        
        OrderStatus oldStatus = order.getStatus();
        
        // Validate status transition
        if (!isValidStatusTransition(oldStatus, newStatus)) {
            throw new BusinessException("Invalid status transition from " + oldStatus + " to " + newStatus, 
                                      "INVALID_STATUS_TRANSITION");
        }
        
        // Handle status-specific logic
        handleStatusTransition(order, oldStatus, newStatus);
        
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        
        // Send status update notification
        sendOrderStatusUpdateNotification(savedOrder, oldStatus, newStatus);
        
        logger.info("Successfully updated order {} to status: {}", orderId, newStatus);
        return toOrderResponse(savedOrder);
    }
    
    /**
     * Handle status-specific business logic
     */
    private void handleStatusTransition(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        switch (newStatus) {
            case CONFIRMED -> handleOrderConfirmation(order);
            case SHIPPED -> handleOrderShipped(order);
            case DELIVERED -> handleOrderDelivered(order);
            case CANCELLED -> handleOrderCancellation(order, oldStatus);
            default -> logger.debug("No special handling required for status transition to: {}", newStatus);
        }
    }
    
    /**
     * Handle order confirmation - initiate payment processing
     */
    private void handleOrderConfirmation(Order order) {
        try {
            logger.info("Processing payment for confirmed order: {}", order.getOrderNumber());
            
            // Create payment request
            PaymentRequest paymentRequest = PaymentRequest.create(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                "CREDIT_CARD", // Default payment method - in real implementation, get from order
                PaymentRequest.PaymentDetails.creditCard("****-****-****-1234", "John Doe", "12", "2025", "123")
            );
            
            // Process payment through Payment Service
            PaymentResponse paymentResponse = paymentServiceClient.processPayment(paymentRequest);
            
            if (paymentResponse.isCompleted()) {
                logger.info("Payment completed successfully for order: {} payment: {}", 
                           order.getOrderNumber(), paymentResponse.paymentNumber());
                
                // Send payment confirmation notification
                sendPaymentConfirmationNotification(order, paymentResponse);
            } else if (paymentResponse.isFailed()) {
                logger.error("Payment failed for order: {} reason: {}", 
                           order.getOrderNumber(), paymentResponse.failureReason());
                
                // Send payment failure notification
                sendPaymentFailureNotification(order, paymentResponse.failureReason());
                
                // Revert order status to PENDING
                order.setStatus(OrderStatus.PENDING);
            }
            
        } catch (PaymentServiceClient.PaymentServiceException e) {
            logger.error("Payment service error for order {}: {}", order.getOrderNumber(), e.getMessage());
            
            // Send payment failure notification
            sendPaymentFailureNotification(order, e.getMessage());
            
            // Revert order status to PENDING
            order.setStatus(OrderStatus.PENDING);
        }
    }
    
    /**
     * Handle order shipped
     */
    private void handleOrderShipped(Order order) {
        try {
            // Confirm inventory sale
            for (OrderItem item : order.getItems()) {
                productServiceClient.confirmSale(item.getProductId(), item.getQuantity());
            }
            
            // Send shipping notification
            sendShippingNotification(order);
            
            logger.info("Order shipped successfully: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Error handling order shipment for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Handle order delivered
     */
    private void handleOrderDelivered(Order order) {
        try {
            // Send delivery confirmation notification
            sendDeliveryConfirmationNotification(order);
            
            logger.info("Order delivered successfully: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Error handling order delivery for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Handle order cancellation
     */
    private void handleOrderCancellation(Order order, OrderStatus oldStatus) {
        try {
            // Release reserved inventory if order was not yet shipped
            if (oldStatus == OrderStatus.PENDING || oldStatus == OrderStatus.CONFIRMED || oldStatus == OrderStatus.PROCESSING) {
                for (OrderItem item : order.getItems()) {
                    productServiceClient.releaseInventory(item.getProductId(), item.getQuantity());
                }
                logger.info("Released inventory for cancelled order: {}", order.getOrderNumber());
            }
            
            // Send cancellation notification
            sendOrderCancellationNotification(order);
            
        } catch (Exception e) {
            logger.error("Error handling order cancellation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Update order
     */
    public OrderResponse updateOrder(UUID orderId, UpdateOrderRequest request) {
        logger.info("Updating order: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        
        // Check if order can be modified
        if (!order.getStatus().canBeModified()) {
            throw new BusinessException("Order cannot be modified in current status: " + order.getStatus(), 
                                      "ORDER_NOT_MODIFIABLE");
        }
        
        // Validate status transition
        if (!isValidStatusTransition(order.getStatus(), request.status())) {
            throw new BusinessException("Invalid status transition from " + order.getStatus() + " to " + request.status(), 
                                      "INVALID_STATUS_TRANSITION");
        }
        
        // Update status
        order.setStatus(request.status());
        
        // Update addresses if provided
        if (request.shippingAddress() != null) {
            order.setShippingAddress(convertAddressToJson(request.shippingAddress()));
        }
        
        if (request.billingAddress() != null) {
            order.setBillingAddress(convertAddressToJson(request.billingAddress()));
        }
        
        Order savedOrder = orderRepository.save(order);
        logger.info("Successfully updated order: {}", orderId);
        
        return toOrderResponse(savedOrder);
    }
    
    /**
     * Cancel order
     */
    public OrderResponse cancelOrder(UUID orderId) {
        logger.info("Cancelling order: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        
        if (!order.getStatus().canBeCancelled()) {
            throw new BusinessException("Order cannot be cancelled in current status: " + order.getStatus(), 
                                      "ORDER_NOT_CANCELLABLE");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        
        logger.info("Successfully cancelled order: {}", orderId);
        return toOrderResponse(savedOrder);
    }
    
    /**
     * Get orders for processing
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForProcessing() {
        List<Order> orders = orderRepository.findOrdersNeedingProcessing();
        return orders.stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get overdue orders
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOverdueOrders(int hoursOverdue) {
        Instant cutoffTime = Instant.now().minusSeconds(hoursOverdue * 3600L);
        List<Order> orders = orderRepository.findOverdueOrders(cutoffTime);
        return orders.stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get order statistics
     */
    @Transactional(readOnly = true)
    public OrderStatistics getOrderStatistics() {
        List<Object[]> stats = orderRepository.getOrderStatisticsByStatus();
        
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        for (Object[] stat : stats) {
            totalOrders += (Long) stat[1];
            totalRevenue = totalRevenue.add((BigDecimal) stat[3]);
        }
        
        return new OrderStatistics(totalOrders, totalRevenue, stats);
    }
    
    /**
     * Get daily order count
     */
    @Transactional(readOnly = true)
    public long getDailyOrderCount() {
        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        return orderRepository.countOrdersCreatedToday(startOfDay);
    }
    
    /**
     * Get total sales amount for date range
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalSalesAmount(Instant startDate, Instant endDate) {
        return orderRepository.getTotalSalesAmount(startDate, endDate);
    }
    
    /**
     * Validate and get product information from Product Service
     */
    private List<ProductInfo> validateAndGetProducts(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Order must contain at least one item", "EMPTY_ORDER");
        }
        
        List<UUID> productIds = items.stream()
                .map(OrderItemRequest::productId)
                .toList();
        
        logger.debug("Validating {} products", productIds.size());
        return productServiceClient.getProducts(productIds);
    }
    
    /**
     * Validate inventory availability for all order items
     */
    private void validateInventoryAvailability(List<OrderItemRequest> items, List<ProductInfo> products) {
        for (OrderItemRequest item : items) {
            ProductInfo product = findProductById(products, item.productId());
            
            if (!product.isInStock()) {
                throw new BusinessException("Product is out of stock: " + product.name(), "PRODUCT_OUT_OF_STOCK");
            }
            
            if (!product.isAvailable(item.quantity())) {
                throw new BusinessException(
                    String.format("Insufficient inventory for product %s. Requested: %d, Available: %d", 
                                 product.name(), item.quantity(), product.getAvailableQuantity()),
                    "INSUFFICIENT_INVENTORY"
                );
            }
        }
    }
    
    /**
     * Calculate total amount using real product prices
     */
    private BigDecimal calculateTotalAmountFromProducts(List<OrderItemRequest> items, List<ProductInfo> products) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (OrderItemRequest item : items) {
            ProductInfo product = findProductById(products, item.productId());
            BigDecimal itemTotal = product.price().multiply(BigDecimal.valueOf(item.quantity()));
            total = total.add(itemTotal);
        }
        
        return total;
    }
    
    /**
     * Find product by ID in the products list
     */
    private ProductInfo findProductById(List<ProductInfo> products, UUID productId) {
        return products.stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Product not found: " + productId, "PRODUCT_NOT_FOUND"));
    }
    
    /**
     * Reserve inventory for all order items
     */
    private void reserveInventoryForOrder(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            try {
                productServiceClient.reserveInventory(item.productId(), item.quantity());
                logger.debug("Reserved {} units of product {}", item.quantity(), item.productId());
            } catch (Exception e) {
                logger.error("Failed to reserve inventory for product {}: {}", item.productId(), e.getMessage());
                // In a production system, you might want to implement compensation logic here
                throw new BusinessException("Failed to reserve inventory for product: " + item.productId(), 
                                          "INVENTORY_RESERVATION_FAILED");
            }
        }
    }
    
    /**
     * Send order confirmation notification
     */
    private void sendOrderConfirmationNotification(UUID userId, Order order) {
        try {
            // In a real implementation, you would get user details from User Service
            String userEmail = "user-" + userId + "@example.com"; // Mock email
            String customerName = "Customer " + userId; // Mock name
            
            // Prepare order details for notification
            Map<String, Object> orderDetails = Map.of(
                "totalAmount", order.getTotalAmount().toString(),
                "itemCount", order.getItems().size(),
                "shippingAddress", order.getShippingAddress(),
                "billingAddress", order.getBillingAddress()
            );
            
            notificationServiceClient.sendOrderConfirmation(
                userId, userEmail, order.getOrderNumber(), customerName, orderDetails);
            
            logger.info("Order confirmation notification sent for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Failed to send order confirmation notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
            // Don't fail the order creation if notification fails
        }
    }
    
    /**
     * Send order status update notification
     */
    private void sendOrderStatusUpdateNotification(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            
            notificationServiceClient.sendOrderStatusUpdate(
                order.getUserId(), userEmail, order.getOrderNumber(), 
                customerName, oldStatus.toString(), newStatus.toString());
            
            logger.info("Order status update notification sent for order: {} ({} -> {})", 
                       order.getOrderNumber(), oldStatus, newStatus);
        } catch (Exception e) {
            logger.error("Failed to send order status update notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Send payment confirmation notification
     */
    private void sendPaymentConfirmationNotification(Order order, PaymentResponse paymentResponse) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            
            notificationServiceClient.sendPaymentConfirmation(
                order.getUserId(), userEmail, order.getOrderNumber(), 
                customerName, paymentResponse.paymentNumber(), paymentResponse.getFormattedAmount());
            
            logger.info("Payment confirmation notification sent for order: {} payment: {}", 
                       order.getOrderNumber(), paymentResponse.paymentNumber());
        } catch (Exception e) {
            logger.error("Failed to send payment confirmation notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Send payment failure notification
     */
    private void sendPaymentFailureNotification(Order order, String reason) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            
            notificationServiceClient.sendPaymentFailure(
                order.getUserId(), userEmail, order.getOrderNumber(), customerName, reason);
            
            logger.info("Payment failure notification sent for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Failed to send payment failure notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Send shipping notification
     */
    private void sendShippingNotification(Order order) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            String trackingNumber = "TRK-" + order.getOrderNumber(); // Mock tracking number
            String carrier = "Standard Shipping"; // Mock carrier
            
            notificationServiceClient.sendShippingNotification(
                order.getUserId(), userEmail, order.getOrderNumber(), 
                customerName, trackingNumber, carrier);
            
            logger.info("Shipping notification sent for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Failed to send shipping notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Send delivery confirmation notification
     */
    private void sendDeliveryConfirmationNotification(Order order) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            
            notificationServiceClient.sendDeliveryConfirmation(
                order.getUserId(), userEmail, order.getOrderNumber(), customerName);
            
            logger.info("Delivery confirmation notification sent for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Failed to send delivery confirmation notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Send order cancellation notification
     */
    private void sendOrderCancellationNotification(Order order) {
        try {
            String userEmail = "user-" + order.getUserId() + "@example.com"; // Mock email
            String customerName = "Customer " + order.getUserId(); // Mock name
            String reason = "Order cancelled by customer request"; // Default reason
            
            notificationServiceClient.sendOrderCancellation(
                order.getUserId(), userEmail, order.getOrderNumber(), customerName, reason);
            
            logger.info("Order cancellation notification sent for order: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("Failed to send order cancellation notification for order {}: {}", 
                        order.getOrderNumber(), e.getMessage());
        }
    }
    
    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        String prefix = "ORD";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        
        String orderNumber = prefix + "-" + timestamp + "-" + random;
        
        // Ensure uniqueness
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            orderNumber = prefix + "-" + timestamp + "-" + random;
        }
        
        return orderNumber;
    }
    
    /**
     * Validate status transition
     */
    private boolean isValidStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }
        
        OrderStatus[] allowedStatuses = currentStatus.getNextPossibleStatuses();
        for (OrderStatus allowedStatus : allowedStatuses) {
            if (allowedStatus == newStatus) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Convert address to JSON string
     */
    private String convertAddressToJson(AddressRequest address) {
        try {
            return objectMapper.writeValueAsString(address);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Failed to serialize address", "ADDRESS_SERIALIZATION_ERROR");
        }
    }
    
    /**
     * Convert JSON string to address response
     */
    private AddressResponse convertJsonToAddress(String addressJson) {
        try {
            AddressRequest addressRequest = objectMapper.readValue(addressJson, AddressRequest.class);
            return new AddressResponse(
                    addressRequest.addressLine1(),
                    addressRequest.addressLine2(),
                    addressRequest.city(),
                    addressRequest.state(),
                    addressRequest.postalCode(),
                    addressRequest.country()
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException("Failed to deserialize address", "ADDRESS_DESERIALIZATION_ERROR");
        }
    }
    
    /**
     * Convert Order entity to OrderResponse DTO
     */
    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());
        
        AddressResponse shippingAddress = convertJsonToAddress(order.getShippingAddress());
        AddressResponse billingAddress = convertJsonToAddress(order.getBillingAddress());
        
        int totalItems = order.getItems().size();
        int totalQuantity = order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
        
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                shippingAddress,
                billingAddress,
                itemResponses,
                totalItems,
                totalQuantity,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
    
    /**
     * Convert OrderItem entity to OrderItemResponse DTO
     */
    private OrderItemResponse toOrderItemResponse(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getProductSku(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice(),
                orderItem.getTotalPrice()
        );
    }
    
    /**
     * Order statistics record
     */
    public record OrderStatistics(
            long totalOrders,
            BigDecimal totalRevenue,
            List<Object[]> statusBreakdown
    ) {}
}