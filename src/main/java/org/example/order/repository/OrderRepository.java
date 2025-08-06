package org.example.order.repository;

import org.example.order.domain.Order;
import org.example.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Order entity
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {
    
    /**
     * Find order by order number
     */
    Optional<Order> findByOrderNumber(String orderNumber);
    
    /**
     * Find order by order number with items
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);
    
    /**
     * Find order by ID with items
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);
    
    /**
     * Find all orders by user ID
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find orders by user ID and status
     */
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatus status, Pageable pageable);
    
    /**
     * Find orders by user ID with items
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithItems(@Param("userId") UUID userId);
    
    /**
     * Find orders by status
     */
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    
    /**
     * Find orders by multiple statuses
     */
    Page<Order> findByStatusInOrderByCreatedAtDesc(List<OrderStatus> statuses, Pageable pageable);
    
    /**
     * Find orders created between dates
     */
    Page<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant startDate, Instant endDate, Pageable pageable);
    
    /**
     * Find orders by user and date range
     */
    Page<Order> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, Instant startDate, Instant endDate, Pageable pageable);
    
    /**
     * Find orders by total amount range
     */
    Page<Order> findByTotalAmountBetweenOrderByCreatedAtDesc(
            BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);
    
    /**
     * Find orders by user and status with date range
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status = :status AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    Page<Order> findByUserIdAndStatusAndDateRange(
            @Param("userId") UUID userId, 
            @Param("status") OrderStatus status,
            @Param("startDate") Instant startDate, 
            @Param("endDate") Instant endDate, 
            Pageable pageable);
    
    /**
     * Find recent orders for a user
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByUserId(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Find orders containing a specific product
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items oi WHERE oi.productId = :productId ORDER BY o.createdAt DESC")
    Page<Order> findOrdersContainingProduct(@Param("productId") UUID productId, Pageable pageable);
    
    /**
     * Find orders that need processing (CONFIRMED status)
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'CONFIRMED' ORDER BY o.createdAt ASC")
    List<Order> findOrdersNeedingProcessing();
    
    /**
     * Find orders that are overdue for shipping
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'PROCESSING' AND o.updatedAt < :cutoffTime ORDER BY o.updatedAt ASC")
    List<Order> findOverdueOrders(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Count orders by status
     */
    long countByStatus(OrderStatus status);
    
    /**
     * Count orders by user and status
     */
    long countByUserIdAndStatus(UUID userId, OrderStatus status);
    
    /**
     * Count orders created today
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startOfDay")
    long countOrdersCreatedToday(@Param("startOfDay") Instant startOfDay);
    
    /**
     * Get total sales amount by date range
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN ('DELIVERED', 'SHIPPED') AND o.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesAmount(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * Get total sales amount for a user
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.userId = :userId AND o.status IN ('DELIVERED', 'SHIPPED')")
    BigDecimal getTotalSalesAmountForUser(@Param("userId") UUID userId);
    
    /**
     * Find top customers by order count
     */
    @Query("SELECT o.userId, COUNT(o) as orderCount FROM Order o WHERE o.status IN ('DELIVERED', 'SHIPPED') GROUP BY o.userId ORDER BY orderCount DESC")
    List<Object[]> findTopCustomersByOrderCount(Pageable pageable);
    
    /**
     * Find top customers by total amount
     */
    @Query("SELECT o.userId, SUM(o.totalAmount) as totalAmount FROM Order o WHERE o.status IN ('DELIVERED', 'SHIPPED') GROUP BY o.userId ORDER BY totalAmount DESC")
    List<Object[]> findTopCustomersByTotalAmount(Pageable pageable);
    
    /**
     * Get order statistics by status
     */
    @Query("SELECT o.status, COUNT(o), AVG(o.totalAmount), SUM(o.totalAmount) FROM Order o GROUP BY o.status")
    List<Object[]> getOrderStatisticsByStatus();
    
    /**
     * Find orders with high value (above threshold)
     */
    @Query("SELECT o FROM Order o WHERE o.totalAmount >= :threshold ORDER BY o.totalAmount DESC")
    List<Order> findHighValueOrders(@Param("threshold") BigDecimal threshold, Pageable pageable);
    
    /**
     * Check if order number exists
     */
    boolean existsByOrderNumber(String orderNumber);
    
    /**
     * Find orders that can be cancelled
     */
    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'CONFIRMED') ORDER BY o.createdAt DESC")
    List<Order> findCancellableOrders();
    
    /**
     * Find orders by user that can be cancelled
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status IN ('PENDING', 'CONFIRMED') ORDER BY o.createdAt DESC")
    List<Order> findCancellableOrdersByUser(@Param("userId") UUID userId);
    
    /**
     * Get monthly order summary
     */
    @Query(value = """
        SELECT 
            DATE_TRUNC('month', created_at) as month,
            COUNT(*) as order_count,
            SUM(total_amount) as total_amount,
            AVG(total_amount) as avg_amount
        FROM orders 
        WHERE created_at >= :startDate 
        GROUP BY DATE_TRUNC('month', created_at) 
        ORDER BY month DESC
        """, nativeQuery = true)
    List<Object[]> getMonthlySummary(@Param("startDate") Instant startDate);
}