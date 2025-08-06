package org.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Notification request DTO for communicating with Notification Service
 */
public record NotificationRequest(
        @NotBlank
        String type,
        
        @NotBlank
        String recipient,
        
        @NotBlank
        String subject,
        
        @NotNull
        Map<String, Object> variables
) {
    
    /**
     * Create order confirmation notification request
     */
    public static NotificationRequest orderConfirmation(String recipient, String orderNumber, 
                                                       String customerName, Map<String, Object> orderDetails) {
        return new NotificationRequest(
            "ORDER_CONFIRMATION",
            recipient,
            "Order Confirmation - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "orderDetails", orderDetails
            )
        );
    }
    
    /**
     * Create order status update notification request
     */
    public static NotificationRequest orderStatusUpdate(String recipient, String orderNumber,
                                                       String customerName, String oldStatus, String newStatus) {
        return new NotificationRequest(
            "ORDER_STATUS_UPDATE",
            recipient,
            "Order Status Update - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "oldStatus", oldStatus,
                "newStatus", newStatus
            )
        );
    }
    
    /**
     * Create order cancellation notification request
     */
    public static NotificationRequest orderCancellation(String recipient, String orderNumber,
                                                       String customerName, String reason) {
        return new NotificationRequest(
            "ORDER_CANCELLATION",
            recipient,
            "Order Cancelled - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "reason", reason != null ? reason : "Customer request"
            )
        );
    }
    
    /**
     * Create payment confirmation notification request
     */
    public static NotificationRequest paymentConfirmation(String recipient, String orderNumber,
                                                         String customerName, String paymentNumber, String amount) {
        return new NotificationRequest(
            "PAYMENT_CONFIRMATION",
            recipient,
            "Payment Confirmed - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "paymentNumber", paymentNumber,
                "amount", amount
            )
        );
    }
    
    /**
     * Create payment failure notification request
     */
    public static NotificationRequest paymentFailure(String recipient, String orderNumber,
                                                    String customerName, String reason) {
        return new NotificationRequest(
            "PAYMENT_FAILURE",
            recipient,
            "Payment Failed - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "reason", reason != null ? reason : "Payment processing failed"
            )
        );
    }
    
    /**
     * Create shipping notification request
     */
    public static NotificationRequest orderShipped(String recipient, String orderNumber,
                                                  String customerName, String trackingNumber, String carrier) {
        return new NotificationRequest(
            "ORDER_SHIPPED",
            recipient,
            "Order Shipped - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "trackingNumber", trackingNumber != null ? trackingNumber : "N/A",
                "carrier", carrier != null ? carrier : "Standard Shipping"
            )
        );
    }
    
    /**
     * Create delivery confirmation notification request
     */
    public static NotificationRequest orderDelivered(String recipient, String orderNumber, String customerName) {
        return new NotificationRequest(
            "ORDER_DELIVERED",
            recipient,
            "Order Delivered - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber
            )
        );
    }
    
    /**
     * Create low inventory alert notification request
     */
    public static NotificationRequest lowInventoryAlert(String recipient, String productName, 
                                                       String productSku, Integer currentStock, Integer threshold) {
        return new NotificationRequest(
            "LOW_INVENTORY_ALERT",
            recipient,
            "Low Inventory Alert - " + productName,
            Map.of(
                "productName", productName,
                "productSku", productSku,
                "currentStock", currentStock,
                "threshold", threshold
            )
        );
    }
    
    /**
     * Create custom notification request
     */
    public static NotificationRequest custom(String type, String recipient, String subject, 
                                           Map<String, Object> variables) {
        return new NotificationRequest(type, recipient, subject, variables);
    }
    
    /**
     * Check if notification is for email
     */
    public boolean isEmailNotification() {
        return recipient != null && recipient.contains("@");
    }
    
    /**
     * Check if notification is for SMS
     */
    public boolean isSmsNotification() {
        return recipient != null && recipient.matches("^\\+?[1-9]\\d{1,14}$");
    }
    
    /**
     * Get notification priority based on type
     */
    public String getPriority() {
        return switch (type) {
            case "PAYMENT_FAILURE", "ORDER_CANCELLATION" -> "HIGH";
            case "ORDER_CONFIRMATION", "PAYMENT_CONFIRMATION" -> "MEDIUM";
            case "ORDER_STATUS_UPDATE", "ORDER_SHIPPED", "ORDER_DELIVERED" -> "NORMAL";
            case "LOW_INVENTORY_ALERT" -> "LOW";
            default -> "NORMAL";
        };
    }
    
    /**
     * Check if notification type requires immediate delivery
     */
    public boolean requiresImmediateDelivery() {
        return "HIGH".equals(getPriority()) || "MEDIUM".equals(getPriority());
    }
}