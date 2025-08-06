package org.example.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for order items
 */
public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,
        
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
    
    /**
     * Check if this item request is valid
     */
    public boolean isValid() {
        return productId != null && quantity != null && quantity > 0;
    }
    
    /**
     * Check if this item is for the specified product
     */
    public boolean isForProduct(UUID productId) {
        return this.productId.equals(productId);
    }
    
    /**
     * Check if this is a bulk order (high quantity)
     */
    public boolean isBulkOrder(int threshold) {
        return quantity >= threshold;
    }
}