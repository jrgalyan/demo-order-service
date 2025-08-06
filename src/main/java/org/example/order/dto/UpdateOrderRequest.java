package org.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.order.domain.OrderStatus;

/**
 * Request DTO for updating an existing order
 */
public record UpdateOrderRequest(
        @NotNull(message = "Order status is required")
        OrderStatus status,
        
        @Valid
        AddressRequest shippingAddress,
        
        @Valid
        AddressRequest billingAddress
) {
    
    /**
     * Check if only status is being updated
     */
    public boolean isStatusOnlyUpdate() {
        return shippingAddress == null && billingAddress == null;
    }
    
    /**
     * Check if addresses are being updated
     */
    public boolean isAddressUpdate() {
        return shippingAddress != null || billingAddress != null;
    }
    
    /**
     * Check if shipping address is being updated
     */
    public boolean isShippingAddressUpdate() {
        return shippingAddress != null;
    }
    
    /**
     * Check if billing address is being updated
     */
    public boolean isBillingAddressUpdate() {
        return billingAddress != null;
    }
    
    /**
     * Check if the status transition is valid from current status
     */
    public boolean isValidStatusTransition(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return false;
        }
        
        OrderStatus[] allowedNextStatuses = currentStatus.getNextPossibleStatuses();
        for (OrderStatus allowedStatus : allowedNextStatuses) {
            if (allowedStatus == status) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if the update is attempting to modify a final status
     */
    public boolean isModifyingFinalStatus(OrderStatus currentStatus) {
        return currentStatus != null && currentStatus.isFinalState();
    }
}