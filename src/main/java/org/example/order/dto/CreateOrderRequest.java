package org.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for creating a new order
 */
public record CreateOrderRequest(
        @NotEmpty(message = "Order items are required")
        List<@Valid OrderItemRequest> items,
        
        @NotNull(message = "Shipping address is required")
        @Valid
        AddressRequest shippingAddress,
        
        @NotNull(message = "Billing address is required")
        @Valid
        AddressRequest billingAddress
) {
    
    /**
     * Check if shipping and billing addresses are the same
     */
    public boolean hasSameShippingAndBillingAddress() {
        return shippingAddress.equals(billingAddress);
    }
    
    /**
     * Get total number of items in the order
     */
    public int getTotalItemCount() {
        return items.stream()
                .mapToInt(OrderItemRequest::quantity)
                .sum();
    }
    
    /**
     * Check if order contains a specific product
     */
    public boolean containsProduct(java.util.UUID productId) {
        return items.stream()
                .anyMatch(item -> item.productId().equals(productId));
    }
}