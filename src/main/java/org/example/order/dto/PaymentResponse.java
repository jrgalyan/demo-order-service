package org.example.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment response DTO from Payment Service
 */
public record PaymentResponse(
        UUID id,
        String paymentNumber,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String status,
        String gatewayTransactionId,
        String failureReason,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt
) {
    
    /**
     * Check if payment is completed successfully
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    /**
     * Check if payment is pending
     */
    public boolean isPending() {
        return "PENDING".equals(status) || "PROCESSING".equals(status);
    }
    
    /**
     * Check if payment failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    /**
     * Check if payment was cancelled
     */
    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }
    
    /**
     * Check if payment was refunded
     */
    public boolean isRefunded() {
        return "REFUNDED".equals(status);
    }
    
    /**
     * Check if payment is in a final state
     */
    public boolean isFinalState() {
        return isCompleted() || isFailed() || isCancelled() || isRefunded();
    }
    
    /**
     * Get formatted amount with currency
     */
    public String getFormattedAmount() {
        if (amount == null || currency == null) {
            return "N/A";
        }
        return String.format("%.2f %s", amount, currency);
    }
    
    /**
     * Get payment status display name
     */
    public String getStatusDisplayName() {
        return switch (status) {
            case "PENDING" -> "Pending";
            case "PROCESSING" -> "Processing";
            case "COMPLETED" -> "Completed";
            case "FAILED" -> "Failed";
            case "CANCELLED" -> "Cancelled";
            case "REFUNDED" -> "Refunded";
            default -> status;
        };
    }
    
    /**
     * Get payment method display name
     */
    public String getPaymentMethodDisplayName() {
        return switch (paymentMethod) {
            case "CREDIT_CARD" -> "Credit Card";
            case "DEBIT_CARD" -> "Debit Card";
            case "PAYPAL" -> "PayPal";
            case "STRIPE" -> "Stripe";
            case "BANK_TRANSFER" -> "Bank Transfer";
            case "DIGITAL_WALLET" -> "Digital Wallet";
            default -> paymentMethod;
        };
    }
    
    /**
     * Check if payment has gateway transaction ID
     */
    public boolean hasGatewayTransactionId() {
        return gatewayTransactionId != null && !gatewayTransactionId.trim().isEmpty();
    }
    
    /**
     * Check if payment has failure reason
     */
    public boolean hasFailureReason() {
        return failureReason != null && !failureReason.trim().isEmpty();
    }
}