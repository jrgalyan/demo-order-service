package org.example.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product information DTO for order processing
 */
public record ProductInfo(
        UUID id,
        String name,
        String sku,
        BigDecimal price,
        Boolean inStock,
        Integer availableQuantity
) {
    
    /**
     * Check if product is available for the requested quantity
     */
    public boolean isAvailable(Integer requestedQuantity) {
        return inStock != null && inStock && 
               availableQuantity != null && 
               availableQuantity >= requestedQuantity;
    }
    
    /**
     * Check if product is in stock
     */
    public boolean isInStock() {
        return inStock != null && inStock;
    }
    
    /**
     * Get available quantity (safe getter)
     */
    public Integer getAvailableQuantity() {
        return availableQuantity != null ? availableQuantity : 0;
    }
    
    /**
     * Calculate total price for given quantity
     */
    public BigDecimal calculateTotalPrice(Integer quantity) {
        if (price == null || quantity == null || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }
    
    /**
     * Get formatted product display name
     */
    public String getDisplayName() {
        if (name == null) {
            return sku != null ? sku : "Unknown Product";
        }
        
        if (sku != null) {
            return name + " (" + sku + ")";
        }
        
        return name;
    }
    
    /**
     * Check if product information is complete
     */
    public boolean isComplete() {
        return id != null && name != null && sku != null && price != null;
    }
}