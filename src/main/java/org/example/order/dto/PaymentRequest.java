package org.example.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment request DTO for communicating with Payment Service
 */
public record PaymentRequest(
        @NotNull
        UUID orderId,
        
        @NotNull
        UUID userId,
        
        @NotNull
        @Positive
        BigDecimal amount,
        
        @NotNull
        String currency,
        
        @NotNull
        String paymentMethod,
        
        PaymentDetails paymentDetails
) {
    
    /**
     * Create payment request with default currency
     */
    public static PaymentRequest create(UUID orderId, UUID userId, BigDecimal amount, 
                                       String paymentMethod, PaymentDetails paymentDetails) {
        return new PaymentRequest(orderId, userId, amount, "USD", paymentMethod, paymentDetails);
    }
    
    /**
     * Payment details for different payment methods
     */
    public record PaymentDetails(
        String cardNumber,
        String cardHolderName,
        String expiryMonth,
        String expiryYear,
        String cvv,
        String paypalEmail,
        String stripeTokenId,
        String billingAddress
    ) {
        
        /**
         * Create payment details for credit card
         */
        public static PaymentDetails creditCard(String cardNumber, String cardHolderName,
                                               String expiryMonth, String expiryYear, String cvv) {
            return new PaymentDetails(cardNumber, cardHolderName, expiryMonth, expiryYear, cvv,
                                    null, null, null);
        }
        
        /**
         * Create payment details for PayPal
         */
        public static PaymentDetails paypal(String paypalEmail) {
            return new PaymentDetails(null, null, null, null, null,
                                    paypalEmail, null, null);
        }
        
        /**
         * Create payment details for Stripe token
         */
        public static PaymentDetails stripe(String stripeTokenId) {
            return new PaymentDetails(null, null, null, null, null,
                                    null, stripeTokenId, null);
        }
    }
}