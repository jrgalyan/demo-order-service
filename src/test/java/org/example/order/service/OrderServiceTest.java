package org.example.order.service;

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
import org.example.order.dto.CreateOrderRequest;
import org.example.order.dto.OrderItemRequest;
import org.example.order.dto.OrderResponse;
import org.example.order.dto.PaymentRequest;
import org.example.order.dto.PaymentResponse;
import org.example.order.dto.ProductInfo;
import org.example.order.dto.UpdateOrderRequest;
import org.example.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient  productServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @InjectMocks
    private OrderService orderService;
    
    private CreateOrderRequest createOrderRequest;
    private UpdateOrderRequest updateOrderRequest;
    private Order order;
    private OrderItem orderItem;
    private UUID userId;
    private UUID orderId;
    private UUID productId;
    private AddressRequest shippingAddress;
    private AddressRequest billingAddress;
    private OrderItemRequest orderItemRequest;
    private PaymentResponse paymentResponse;
    private ProductInfo productInfo;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        productId = UUID.randomUUID();
        
        shippingAddress = new AddressRequest(
                "123 Main St",
                "Apt 4B",
                "New York",
                "NY",
                "10001",
                "USA"
        );
        
        billingAddress = new AddressRequest(
                "456 Oak Ave",
                null,
                "Los Angeles",
                "CA",
                "90210",
                "USA"
        );
        
        orderItemRequest = new OrderItemRequest(productId, 2);
        
        createOrderRequest = new CreateOrderRequest(
                List.of(orderItemRequest),
                shippingAddress,
                billingAddress
        );
        
        updateOrderRequest = new UpdateOrderRequest(
                OrderStatus.CONFIRMED,
                shippingAddress,
                billingAddress
        );
        
        order = new Order(
                "ORD-123456789-0001",
                userId,
                BigDecimal.valueOf(199.98),
                "{\"addressLine1\": \"123 Main St\",\"addressLine2\": \"Apt 4B\", \"city\": \"New York\",\"state\": \"NY\",\"postalCode\": \"10001\",\"country\": \"USA\"}",
                "{\"addressLine1\": \"456 Oak Ave\",\"city\": \"Los Angeles\",\"state\": \"CA\",\"postalCode\": \"90210\",\"country\": \"USA\"}"
        );
        
        orderItem = new OrderItem(
                order,
                productId,
                "Test Product",
                "TEST-SKU",
                2,
                BigDecimal.valueOf(99.99)
        );
        
        order.addItem(orderItem);

        paymentResponse = new PaymentResponse(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(99.99),
                "USD",
                "VISA",
                "COMPLETED",
                UUID.randomUUID().toString(),
                null,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        productInfo = new ProductInfo(
                productId,
                "Test Product",
                "TEST-SKU",
                BigDecimal.valueOf(99.99),
                true,
                10
        );
    }
    
    @Test
    void createOrder_Success() throws Exception {
        // Given
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        when(productServiceClient.getProducts(any())).thenReturn(List.of(productInfo));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        // When
        OrderResponse result = orderService.createOrder(userId, createOrderRequest);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.totalAmount()).isEqualTo(BigDecimal.valueOf(199.98));
        
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void createOrder_EmptyItems_ThrowsException() {
        // Given
        CreateOrderRequest emptyItemsRequest = new CreateOrderRequest(
                List.of(),
                shippingAddress,
                billingAddress
        );
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(userId, emptyItemsRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item");
        
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void createOrder_InvalidOrderItem_ThrowsException() {
        // Given
        OrderItemRequest invalidItem = new OrderItemRequest(productId, 0);
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                List.of(invalidItem),
                shippingAddress,
                billingAddress
        );
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(userId, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product not found");
        
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void getOrderById_Success() throws Exception {
        // Given
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        
        // When
        OrderResponse result = orderService.getOrderById(orderId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(order.getId());
        assertThat(result.orderNumber()).isEqualTo("ORD-123456789-0001");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        
        verify(orderRepository).findByIdWithItems(orderId);
        verify(objectMapper, times(2)).readValue(anyString(), eq(AddressRequest.class));
    }
    
    @Test
    void getOrderById_NotFound_ThrowsException() {
        // Given
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(ResourceNotFoundException.class);
        
        verify(orderRepository).findByIdWithItems(orderId);
    }
    
    @Test
    void getOrderByNumber_Success() throws Exception {
        // Given
        String orderNumber = "ORD-123456789-0001";
        when(orderRepository.findByOrderNumberWithItems(orderNumber)).thenReturn(Optional.of(order));
        
        // When
        OrderResponse result = orderService.getOrderByNumber(orderNumber);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.orderNumber()).isEqualTo(orderNumber);
        
        verify(orderRepository).findByOrderNumberWithItems(orderNumber);
    }
    
    @Test
    void getOrderByNumber_NotFound_ThrowsException() {
        // Given
        String orderNumber = "INVALID-ORDER";
        when(orderRepository.findByOrderNumberWithItems(orderNumber)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> orderService.getOrderByNumber(orderNumber))
                .isInstanceOf(ResourceNotFoundException.class);
        
        verify(orderRepository).findByOrderNumberWithItems(orderNumber);
    }
    
    @Test
    void getOrdersForUser_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(orderPage);
        
        // When
        Page<OrderResponse> result = orderService.getOrdersForUser(userId, pageable);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    @Test
    void getOrdersByStatus_Success() {
        // Given
        OrderStatus status = OrderStatus.PENDING;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)).thenReturn(orderPage);
        
        // When
        Page<OrderResponse> result = orderService.getOrdersByStatus(status, pageable);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        
        verify(orderRepository).findByStatusOrderByCreatedAtDesc(status, pageable);
    }
    
    @Test
    void updateOrderStatus_Success() {
        // Given
        OrderStatus newStatus = OrderStatus.CONFIRMED;
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(paymentServiceClient.processPayment(any(PaymentRequest.class))).thenReturn(paymentResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        // When
        OrderResponse result = orderService.updateOrderStatus(orderId, newStatus);
        
        // Then
        assertThat(result).isNotNull();
        
        verify(orderRepository).findByIdWithItems(orderId);
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void updateOrderStatus_InvalidTransition_ThrowsException() {
        // Given
        order.setStatus(OrderStatus.DELIVERED); // Final status
        OrderStatus newStatus = OrderStatus.PENDING;
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        
        // When & Then
        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, newStatus))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status transition");
        
        verify(orderRepository).findByIdWithItems(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void updateOrderStatus_OrderNotFound_ThrowsException() {
        // Given
        OrderStatus newStatus = OrderStatus.CONFIRMED;
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, newStatus))
                .isInstanceOf(ResourceNotFoundException.class);
        
        verify(orderRepository).findByIdWithItems(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void updateOrder_Success() throws Exception {
        // Given
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        // When
        OrderResponse result = orderService.updateOrder(orderId, updateOrderRequest);
        
        // Then
        assertThat(result).isNotNull();
        
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void updateOrder_OrderNotModifiable_ThrowsException() {
        // Given
        order.setStatus(OrderStatus.DELIVERED); // Not modifiable
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // When & Then
        assertThatThrownBy(() -> orderService.updateOrder(orderId, updateOrderRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be modified");
        
        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void cancelOrder_Success() {
        // Given
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        // When
        OrderResponse result = orderService.cancelOrder(orderId);
        
        // Then
        assertThat(result).isNotNull();
        
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void cancelOrder_OrderNotCancellable_ThrowsException() {
        // Given
        order.setStatus(OrderStatus.DELIVERED); // Not cancellable
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be cancelled");
        
        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void cancelOrder_OrderNotFound_ThrowsException() {
        // Given
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(ResourceNotFoundException.class);
        
        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void getOrdersForProcessing_Success() {
        // Given
        when(orderRepository.findOrdersNeedingProcessing()).thenReturn(List.of(order));
        
        // When
        List<OrderResponse> result = orderService.getOrdersForProcessing();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        verify(orderRepository).findOrdersNeedingProcessing();
    }
    
    @Test
    void getOverdueOrders_Success() {
        // Given
        int hoursOverdue = 24;
        when(orderRepository.findOverdueOrders(any(Instant.class))).thenReturn(List.of(order));
        
        // When
        List<OrderResponse> result = orderService.getOverdueOrders(hoursOverdue);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        verify(orderRepository).findOverdueOrders(any(Instant.class));
    }
    
    @Test
    void getOrderStatistics_Success() {
        // Given
        List<Object[]> mockStats = List.of(
                new Object[]{OrderStatus.PENDING, 5L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(500.0)},
                new Object[]{OrderStatus.COMPLETED, 10L, BigDecimal.valueOf(150.0), BigDecimal.valueOf(1500.0)}
        );
        when(orderRepository.getOrderStatisticsByStatus()).thenReturn(mockStats);
        
        // When
        OrderService.OrderStatistics result = orderService.getOrderStatistics();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.totalOrders()).isEqualTo(15L);
        assertThat(result.totalRevenue()).isEqualTo(BigDecimal.valueOf(2000.0));
        
        verify(orderRepository).getOrderStatisticsByStatus();
    }
    
    @Test
    void getDailyOrderCount_Success() {
        // Given
        when(orderRepository.countOrdersCreatedToday(any(Instant.class))).thenReturn(5L);
        
        // When
        long result = orderService.getDailyOrderCount();
        
        // Then
        assertThat(result).isEqualTo(5L);
        
        verify(orderRepository).countOrdersCreatedToday(any(Instant.class));
    }
    
    @Test
    void getTotalSalesAmount_Success() {
        // Given
        Instant startDate = Instant.now().minusSeconds(86400);
        Instant endDate = Instant.now();
        BigDecimal expectedAmount = BigDecimal.valueOf(1000.0);
        when(orderRepository.getTotalSalesAmount(startDate, endDate)).thenReturn(expectedAmount);
        
        // When
        BigDecimal result = orderService.getTotalSalesAmount(startDate, endDate);
        
        // Then
        assertThat(result).isEqualTo(expectedAmount);
        
        verify(orderRepository).getTotalSalesAmount(startDate, endDate);
    }
    
    @Test
    void generateOrderNumber_UniqueGeneration() {
        // Given
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);
        
        // When
        String orderNumber1 = invokeGenerateOrderNumber();
        String orderNumber2 = invokeGenerateOrderNumber();
        
        // Then
        assertThat(orderNumber1).isNotNull();
        assertThat(orderNumber2).isNotNull();
        assertThat(orderNumber1).isNotEqualTo(orderNumber2);
        assertThat(orderNumber1).startsWith("ORD-");
        assertThat(orderNumber2).startsWith("ORD-");
    }
    
    @Test
    void generateOrderNumber_HandlesCollision() {
        // Given
        when(orderRepository.existsByOrderNumber(anyString()))
                .thenReturn(true)  // First attempt collides
                .thenReturn(false); // Second attempt succeeds
        
        // When
        String orderNumber = invokeGenerateOrderNumber();
        
        // Then
        assertThat(orderNumber).isNotNull();
        assertThat(orderNumber).startsWith("ORD-");
        
        verify(orderRepository, times(2)).existsByOrderNumber(anyString());
    }
    
    // Helper method to invoke private generateOrderNumber method via reflection
    private String invokeGenerateOrderNumber() {
        try {
            var method = OrderService.class.getDeclaredMethod("generateOrderNumber");
            method.setAccessible(true);
            return (String) method.invoke(orderService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}