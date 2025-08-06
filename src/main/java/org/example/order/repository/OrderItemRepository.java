package org.example.order.repository;

import org.example.order.domain.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for OrderItem entity
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    
    /**
     * Find all items for a specific order
     */
    List<OrderItem> findByOrderIdOrderByProductName(UUID orderId);
    
    /**
     * Find all items for a specific product
     */
    List<OrderItem> findByProductIdOrderByOrderCreatedAtDesc(UUID productId);
    
    /**
     * Find items by product with pagination
     */
    Page<OrderItem> findByProductIdOrderByOrderCreatedAtDesc(UUID productId, Pageable pageable);
    
    /**
     * Find items by product SKU
     */
    List<OrderItem> findByProductSkuOrderByOrderCreatedAtDesc(String productSku);
    
    /**
     * Find items by order and product
     */
    List<OrderItem> findByOrderIdAndProductId(UUID orderId, UUID productId);
    
    /**
     * Find items by multiple orders
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.id IN :orderIds ORDER BY oi.order.createdAt DESC, oi.productName")
    List<OrderItem> findByOrderIds(@Param("orderIds") List<UUID> orderIds);
    
    /**
     * Get total quantity sold for a product
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi JOIN oi.order o WHERE oi.productId = :productId AND o.status IN ('DELIVERED', 'SHIPPED')")
    Long getTotalQuantitySoldForProduct(@Param("productId") UUID productId);
    
    /**
     * Get total revenue for a product
     */
    @Query("SELECT COALESCE(SUM(oi.totalPrice), 0) FROM OrderItem oi JOIN oi.order o WHERE oi.productId = :productId AND o.status IN ('DELIVERED', 'SHIPPED')")
    BigDecimal getTotalRevenueForProduct(@Param("productId") UUID productId);
    
    /**
     * Get total quantity sold for a product in date range
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi JOIN oi.order o WHERE oi.productId = :productId AND o.status IN ('DELIVERED', 'SHIPPED') AND o.createdAt BETWEEN :startDate AND :endDate")
    Long getTotalQuantitySoldForProductInDateRange(@Param("productId") UUID productId, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * Find top selling products by quantity
     */
    @Query("SELECT oi.productId, oi.productName, oi.productSku, SUM(oi.quantity) as totalQuantity FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED') GROUP BY oi.productId, oi.productName, oi.productSku ORDER BY totalQuantity DESC")
    List<Object[]> findTopSellingProductsByQuantity(Pageable pageable);
    
    /**
     * Find top selling products by revenue
     */
    @Query("SELECT oi.productId, oi.productName, oi.productSku, SUM(oi.totalPrice) as totalRevenue FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED') GROUP BY oi.productId, oi.productName, oi.productSku ORDER BY totalRevenue DESC")
    List<Object[]> findTopSellingProductsByRevenue(Pageable pageable);
    
    /**
     * Find top selling products in date range
     */
    @Query("SELECT oi.productId, oi.productName, oi.productSku, SUM(oi.quantity) as totalQuantity, SUM(oi.totalPrice) as totalRevenue FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED') AND o.createdAt BETWEEN :startDate AND :endDate GROUP BY oi.productId, oi.productName, oi.productSku ORDER BY totalQuantity DESC")
    List<Object[]> findTopSellingProductsInDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, Pageable pageable);
    
    /**
     * Get product sales summary
     */
    @Query("SELECT oi.productId, oi.productName, oi.productSku, COUNT(DISTINCT oi.order.id) as orderCount, SUM(oi.quantity) as totalQuantity, AVG(oi.unitPrice) as avgPrice, SUM(oi.totalPrice) as totalRevenue FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED') GROUP BY oi.productId, oi.productName, oi.productSku")
    List<Object[]> getProductSalesSummary();
    
    /**
     * Find items with high quantity (bulk orders)
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.quantity >= :threshold ORDER BY oi.quantity DESC")
    List<OrderItem> findHighQuantityItems(@Param("threshold") Integer threshold);
    
    /**
     * Find items with high value
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.totalPrice >= :threshold ORDER BY oi.totalPrice DESC")
    List<OrderItem> findHighValueItems(@Param("threshold") BigDecimal threshold);
    
    /**
     * Get average order item value
     */
    @Query("SELECT AVG(oi.totalPrice) FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED')")
    BigDecimal getAverageOrderItemValue();
    
    /**
     * Get average quantity per order item
     */
    @Query("SELECT AVG(oi.quantity) FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED')")
    Double getAverageQuantityPerOrderItem();
    
    /**
     * Count distinct products sold
     */
    @Query("SELECT COUNT(DISTINCT oi.productId) FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED')")
    Long countDistinctProductsSold();
    
    /**
     * Find products frequently bought together
     */
    @Query(value = """
        SELECT 
            oi1.product_id as product1_id,
            oi1.product_name as product1_name,
            oi2.product_id as product2_id,
            oi2.product_name as product2_name,
            COUNT(*) as frequency
        FROM order_items oi1
        JOIN order_items oi2 ON oi1.order_id = oi2.order_id AND oi1.product_id < oi2.product_id
        JOIN orders o ON oi1.order_id = o.id
        WHERE o.status IN ('DELIVERED', 'SHIPPED')
        GROUP BY oi1.product_id, oi1.product_name, oi2.product_id, oi2.product_name
        HAVING COUNT(*) >= :minFrequency
        ORDER BY frequency DESC
        """, nativeQuery = true)
    List<Object[]> findProductsBoughtTogether(@Param("minFrequency") Integer minFrequency, Pageable pageable);
    
    /**
     * Get monthly product sales
     */
    @Query(value = """
        SELECT 
            oi.product_id,
            oi.product_name,
            DATE_TRUNC('month', o.created_at) as month,
            SUM(oi.quantity) as total_quantity,
            SUM(oi.total_price) as total_revenue
        FROM order_items oi
        JOIN orders o ON oi.order_id = o.id
        WHERE o.status IN ('DELIVERED', 'SHIPPED') AND o.created_at >= :startDate
        GROUP BY oi.product_id, oi.product_name, DATE_TRUNC('month', o.created_at)
        ORDER BY month DESC, total_revenue DESC
        """, nativeQuery = true)
    List<Object[]> getMonthlyProductSales(@Param("startDate") Instant startDate);
    
    /**
     * Find items by price range
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.unitPrice BETWEEN :minPrice AND :maxPrice ORDER BY oi.unitPrice DESC")
    List<OrderItem> findItemsByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);
    
    /**
     * Get customer purchase history for a product
     */
    @Query("SELECT oi FROM OrderItem oi JOIN oi.order o WHERE oi.productId = :productId AND o.userId = :userId ORDER BY o.createdAt DESC")
    List<OrderItem> getCustomerPurchaseHistoryForProduct(@Param("productId") UUID productId, @Param("userId") UUID userId);
    
    /**
     * Find items that need inventory replenishment (high sales, low current stock)
     */
    @Query("SELECT oi.productId, oi.productName, oi.productSku, SUM(oi.quantity) as totalSold FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED') AND o.createdAt >= :since GROUP BY oi.productId, oi.productName, oi.productSku ORDER BY totalSold DESC")
    List<Object[]> findProductsNeedingReplenishment(@Param("since") Instant since, Pageable pageable);
    
    /**
     * Get order item statistics
     */
    @Query("SELECT COUNT(oi), SUM(oi.quantity), AVG(oi.quantity), SUM(oi.totalPrice), AVG(oi.totalPrice) FROM OrderItem oi JOIN oi.order o WHERE o.status IN ('DELIVERED', 'SHIPPED')")
    List<Object[]> getOrderItemStatistics();
    
    /**
     * Find items by order status
     */
    @Query("SELECT oi FROM OrderItem oi JOIN oi.order o WHERE o.status = :status ORDER BY o.createdAt DESC")
    Page<OrderItem> findItemsByOrderStatus(@Param("status") String status, Pageable pageable);
    
    /**
     * Count items by product
     */
    long countByProductId(UUID productId);
    
    /**
     * Delete items by order ID (used when deleting orders)
     */
    void deleteByOrderId(UUID orderId);
}