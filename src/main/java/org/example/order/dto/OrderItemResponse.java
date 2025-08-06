package org.example.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for order item information
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
    
    /**
     * Check if this item is for the specified product
     */
    public boolean isForProduct(UUID productId) {
        return this.productId.equals(productId);
    }
    
    /**
     * Check if this item has valid quantity
     */
    public boolean hasValidQuantity() {
        return quantity != null && quantity > 0;
    }
    
    /**
     * Check if this item has valid pricing
     */
    public boolean hasValidPricing() {
        return unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0 &&
               totalPrice != null && totalPrice.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if the total price calculation is correct
     */
    public boolean isTotalPriceCorrect() {
        if (quantity == null || unitPrice == null || totalPrice == null) {
            return false;
        }
        
        BigDecimal expectedTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return expectedTotal.compareTo(totalPrice) == 0;
    }
    
    /**
     * Get the discount amount (if unit price was reduced)
     */
    public BigDecimal getDiscountAmount(BigDecimal originalUnitPrice) {
        if (originalUnitPrice == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        
        if (originalUnitPrice.compareTo(unitPrice) > 0) {
            return originalUnitPrice.subtract(unitPrice).multiply(BigDecimal.valueOf(quantity));
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Get the discount percentage (if unit price was reduced)
     */
    public BigDecimal getDiscountPercentage(BigDecimal originalUnitPrice) {
        if (originalUnitPrice == null || unitPrice == null || 
            originalUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if (originalUnitPrice.compareTo(unitPrice) > 0) {
            BigDecimal discount = originalUnitPrice.subtract(unitPrice);
            return discount.divide(originalUnitPrice, 4, java.math.RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100));
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Check if this is a bulk order item (high quantity)
     */
    public boolean isBulkOrder(int threshold) {
        return quantity != null && quantity >= threshold;
    }
    
    /**
     * Check if this is a high-value item
     */
    public boolean isHighValueItem(BigDecimal threshold) {
        return totalPrice != null && totalPrice.compareTo(threshold) >= 0;
    }
    
    /**
     * Get the average price per unit (same as unit price, but useful for consistency)
     */
    public BigDecimal getAveragePricePerUnit() {
        return unitPrice;
    }
    
    /**
     * Calculate the contribution of this item to the total order value
     */
    public BigDecimal getContributionPercentage(BigDecimal orderTotal) {
        if (orderTotal == null || totalPrice == null || 
            orderTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalPrice.divide(orderTotal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Get formatted product display name (name with SKU)
     */
    public String getFormattedProductName() {
        if (productName == null) {
            return productSku != null ? productSku : "Unknown Product";
        }
        
        if (productSku != null) {
            return productName + " (" + productSku + ")";
        }
        
        return productName;
    }
    
    /**
     * Check if product information is complete
     */
    public boolean hasCompleteProductInfo() {
        return productId != null && productName != null && 
               productSku != null && !productName.trim().isEmpty() && 
               !productSku.trim().isEmpty();
    }
}