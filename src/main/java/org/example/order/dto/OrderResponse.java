package org.example.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for order information
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID userId,
        OrderStatus status,
        BigDecimal totalAmount,
        AddressResponse shippingAddress,
        AddressResponse billingAddress,
        List<OrderItemResponse> items,
        Integer totalItems,
        Integer totalQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    
    /**
     * Check if the order is in a final state
     */
    public boolean isFinalState() {
        return status != null && status.isFinalState();
    }
    
    /**
     * Check if the order can be cancelled
     */
    public boolean canBeCancelled() {
        return status != null && status.canBeCancelled();
    }
    
    /**
     * Check if the order can be modified
     */
    public boolean canBeModified() {
        return status != null && status.canBeModified();
    }
    
    /**
     * Check if the order is active
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }
    
    /**
     * Get the next possible statuses for this order
     */
    public OrderStatus[] getNextPossibleStatuses() {
        return status != null ? status.getNextPossibleStatuses() : new OrderStatus[]{};
    }
    
    /**
     * Check if shipping and billing addresses are the same
     */
    public boolean hasSameShippingAndBillingAddress() {
        return shippingAddress != null && billingAddress != null && 
               shippingAddress.equals(billingAddress);
    }
    
    /**
     * Get total number of items (sum of quantities)
     */
    public int getTotalItemQuantity() {
        return totalQuantity != null ? totalQuantity : 0;
    }
    
    /**
     * Get number of distinct items
     */
    public int getDistinctItemCount() {
        return totalItems != null ? totalItems : 0;
    }
    
    /**
     * Check if order contains a specific product
     */
    public boolean containsProduct(UUID productId) {
        return items != null && items.stream()
                .anyMatch(item -> item.productId().equals(productId));
    }
    
    /**
     * Get average item value
     */
    public BigDecimal getAverageItemValue() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalItemValue = items.stream()
                .map(OrderItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return totalItemValue.divide(BigDecimal.valueOf(items.size()), 2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Check if this is a high-value order
     */
    public boolean isHighValueOrder(BigDecimal threshold) {
        return totalAmount != null && totalAmount.compareTo(threshold) >= 0;
    }
    
    /**
     * Get order age in days
     */
    public long getOrderAgeInDays() {
        if (createdAt == null) {
            return 0;
        }
        
        return java.time.Duration.between(createdAt, Instant.now()).toDays();
    }
    
    /**
     * Check if order is overdue for processing
     */
    public boolean isOverdueForProcessing(int maxDaysInStatus) {
        if (updatedAt == null) {
            return false;
        }
        
        long daysInCurrentStatus = java.time.Duration.between(updatedAt, Instant.now()).toDays();
        return daysInCurrentStatus > maxDaysInStatus;
    }
}