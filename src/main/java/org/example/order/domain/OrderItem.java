package org.example.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItem entity representing individual items within an order
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    
    @Column(name = "product_name", nullable = false)
    private String productName;
    
    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;
    
    // Default constructor for JPA
    protected OrderItem() {}
    
    public OrderItem(Order order, UUID productId, String productName, String productSku,
                    Integer quantity, BigDecimal unitPrice) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = calculateTotalPrice();
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public UUID getProductId() {
        return productId;
    }
    
    public void setProductId(UUID productId) {
        this.productId = productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public String getProductSku() {
        return productSku;
    }
    
    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        this.totalPrice = calculateTotalPrice();
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.totalPrice = calculateTotalPrice();
    }
    
    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
    
    /**
     * Calculate total price based on quantity and unit price
     */
    private BigDecimal calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Update total price (called when quantity or unit price changes)
     */
    public void updateTotalPrice() {
        this.totalPrice = calculateTotalPrice();
    }
    
    /**
     * Check if this order item is for the specified product
     */
    public boolean isForProduct(UUID productId) {
        return this.productId.equals(productId);
    }
    
    /**
     * Check if this order item has valid quantity
     */
    public boolean hasValidQuantity() {
        return quantity != null && quantity > 0;
    }
    
    /**
     * Check if this order item has valid pricing
     */
    public boolean hasValidPricing() {
        return unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0;
    }
}