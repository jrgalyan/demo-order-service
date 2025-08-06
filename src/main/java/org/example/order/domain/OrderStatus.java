package org.example.order.domain;

/**
 * Enumeration representing the possible states of an order
 */
public enum OrderStatus {
    /**
     * Order has been created but not yet confirmed
     */
    PENDING,
    
    /**
     * Order has been confirmed and is being processed
     */
    CONFIRMED,
    
    /**
     * Order is being prepared for shipment
     */
    PROCESSING,
    
    /**
     * Order has been shipped
     */
    SHIPPED,
    
    /**
     * Order has been delivered to the customer
     */
    DELIVERED,
    
    /**
     * Order has been cancelled
     */
    CANCELLED,
    
    /**
     * Order has been returned by the customer
     */
    RETURNED,
    
    /**
     * Order has been refunded
     */
    REFUNDED,

    /**
     * Order has been completed
     */
    COMPLETED;

    /**
     * Check if the order status allows cancellation
     */
    public boolean canBeCancelled() {
        return this == PENDING || this == CONFIRMED;
    }
    
    /**
     * Check if the order status allows modification
     */
    public boolean canBeModified() {
        return this == PENDING;
    }
    
    /**
     * Check if the order is in a final state
     */
    public boolean isFinalState() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED || this == REFUNDED;
    }
    
    /**
     * Check if the order is active (not cancelled, returned, or refunded)
     */
    public boolean isActive() {
        return this != CANCELLED && this != RETURNED && this != REFUNDED;
    }
    
    /**
     * Get the next possible statuses from the current status
     */
    public OrderStatus[] getNextPossibleStatuses() {
        return switch (this) {
            case PENDING -> new OrderStatus[]{CONFIRMED, CANCELLED};
            case CONFIRMED -> new OrderStatus[]{PROCESSING, CANCELLED};
            case PROCESSING -> new OrderStatus[]{SHIPPED, CANCELLED};
            case SHIPPED -> new OrderStatus[]{DELIVERED, RETURNED};
            case DELIVERED -> new OrderStatus[]{RETURNED};
            case CANCELLED, RETURNED, REFUNDED, COMPLETED -> new OrderStatus[]{};
        };
    }
}