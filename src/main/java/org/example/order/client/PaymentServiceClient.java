package org.example.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.example.order.dto.PaymentRequest;
import org.example.order.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client for communicating with Payment Service
 */
@Component
public class PaymentServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    public PaymentServiceClient(RestTemplate restTemplate,
                               @Value("${services.payment-service.url:http://localhost:8084}") String paymentServiceUrl,
                               @Qualifier("paymentServiceCircuitBreaker") CircuitBreaker circuitBreaker,
                               @Qualifier("paymentServiceRetry") Retry retry) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }
    
    /**
     * Process payment for an order
     */
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        logger.info("Processing payment for order: {} amount: {}", 
                   paymentRequest.orderId(), paymentRequest.amount());
        
        Supplier<PaymentResponse> paymentSupplier = () -> {
            try {
                String url = paymentServiceUrl + "/api/v1/payments";
                ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                    url, paymentRequest, PaymentResponse.class);
                
                if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                    PaymentResponse payment = response.getBody();
                    logger.info("Payment processed successfully: {} status: {}", 
                               payment.paymentNumber(), payment.status());
                    return payment;
                } else {
                    logger.error("Payment service returned unexpected response: {}", response.getStatusCode());
                    throw new PaymentServiceException("Payment processing failed");
                }
                
            } catch (HttpClientErrorException e) {
                logger.error("HTTP error when processing payment: {} - {}", 
                           e.getStatusCode(), e.getResponseBodyAsString());
                throw new PaymentServiceException("Payment processing failed: " + e.getMessage());
            } catch (ResourceAccessException e) {
                logger.error("Network error when processing payment: {}", e.getMessage());
                throw new PaymentServiceException("Payment service unavailable");
            } catch (Exception e) {
                logger.error("Unexpected error when processing payment: {}", e.getMessage(), e);
                throw new PaymentServiceException("Payment processing failed: " + e.getMessage());
            }
        };
        
        // Apply circuit breaker and retry patterns
        Supplier<PaymentResponse> decoratedSupplier = CircuitBreaker
            .decorateSupplier(circuitBreaker, paymentSupplier);
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        
        return decoratedSupplier.get();
    }
    
    /**
     * Get payment status by payment ID
     */
    public PaymentResponse getPaymentStatus(UUID paymentId) {
        logger.debug("Getting payment status for ID: {}", paymentId);
        
        Supplier<PaymentResponse> statusSupplier = () -> {
            try {
                String url = paymentServiceUrl + "/api/v1/payments/" + paymentId;
                ResponseEntity<PaymentResponse> response = restTemplate.getForEntity(url, PaymentResponse.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    PaymentResponse payment = response.getBody();
                    logger.debug("Payment status retrieved: {} status: {}", 
                                payment.paymentNumber(), payment.status());
                    return payment;
                } else {
                    logger.warn("Payment service returned empty response for payment ID: {}", paymentId);
                    throw new PaymentServiceException("Payment not found: " + paymentId);
                }
                
            } catch (HttpClientErrorException.NotFound e) {
                logger.warn("Payment not found: {}", paymentId);
                throw new PaymentServiceException("Payment not found: " + paymentId);
            } catch (HttpClientErrorException e) {
                logger.error("HTTP error when getting payment status {}: {} - {}", 
                           paymentId, e.getStatusCode(), e.getResponseBodyAsString());
                throw new PaymentServiceException("Failed to get payment status: " + e.getMessage());
            } catch (ResourceAccessException e) {
                logger.error("Network error when getting payment status {}: {}", paymentId, e.getMessage());
                throw new PaymentServiceException("Payment service unavailable");
            } catch (Exception e) {
                logger.error("Unexpected error when getting payment status {}: {}", paymentId, e.getMessage(), e);
                throw new PaymentServiceException("Failed to get payment status: " + e.getMessage());
            }
        };
        
        // Apply circuit breaker and retry patterns
        Supplier<PaymentResponse> decoratedSupplier = CircuitBreaker
            .decorateSupplier(circuitBreaker, statusSupplier);
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        
        return decoratedSupplier.get();
    }
    
    /**
     * Cancel payment
     */
    public void cancelPayment(UUID paymentId) {
        logger.info("Cancelling payment: {}", paymentId);
        
        Runnable cancelRunnable = () -> {
            try {
                String url = paymentServiceUrl + "/api/v1/payments/" + paymentId + "/cancel";
                ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Payment cancelled successfully: {}", paymentId);
                } else {
                    logger.error("Failed to cancel payment {}: HTTP {}", 
                               paymentId, response.getStatusCode());
                    throw new PaymentServiceException("Failed to cancel payment");
                }
                
            } catch (HttpClientErrorException e) {
                logger.error("HTTP error when cancelling payment {}: {} - {}", 
                           paymentId, e.getStatusCode(), e.getResponseBodyAsString());
                throw new PaymentServiceException("Failed to cancel payment: " + e.getMessage());
            } catch (ResourceAccessException e) {
                logger.error("Network error when cancelling payment {}: {}", paymentId, e.getMessage());
                throw new PaymentServiceException("Payment service unavailable");
            } catch (Exception e) {
                logger.error("Unexpected error when cancelling payment {}: {}", paymentId, e.getMessage(), e);
                throw new PaymentServiceException("Failed to cancel payment: " + e.getMessage());
            }
        };
        
        // Apply circuit breaker and retry patterns
        Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(circuitBreaker, cancelRunnable);
        decoratedRunnable = Retry.decorateRunnable(retry, decoratedRunnable);
        
        decoratedRunnable.run();
    }
    
    /**
     * Request refund for a payment
     */
    public void requestRefund(UUID paymentId, BigDecimal amount, String reason) {
        logger.info("Requesting refund for payment: {} amount: {} reason: {}", 
                   paymentId, amount, reason);
        
        Runnable refundRunnable = () -> {
            try {
                RefundRequest refundRequest = new RefundRequest(paymentId, amount, reason);
                String url = paymentServiceUrl + "/api/v1/refunds";
                ResponseEntity<Void> response = restTemplate.postForEntity(url, refundRequest, Void.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Refund requested successfully for payment: {}", paymentId);
                } else {
                    logger.error("Failed to request refund for payment {}: HTTP {}", 
                               paymentId, response.getStatusCode());
                    throw new PaymentServiceException("Failed to request refund");
                }
                
            } catch (HttpClientErrorException e) {
                logger.error("HTTP error when requesting refund for payment {}: {} - {}", 
                           paymentId, e.getStatusCode(), e.getResponseBodyAsString());
                throw new PaymentServiceException("Failed to request refund: " + e.getMessage());
            } catch (ResourceAccessException e) {
                logger.error("Network error when requesting refund for payment {}: {}", paymentId, e.getMessage());
                throw new PaymentServiceException("Payment service unavailable");
            } catch (Exception e) {
                logger.error("Unexpected error when requesting refund for payment {}: {}", paymentId, e.getMessage(), e);
                throw new PaymentServiceException("Failed to request refund: " + e.getMessage());
            }
        };
        
        // Apply circuit breaker and retry patterns
        Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(circuitBreaker, refundRunnable);
        decoratedRunnable = Retry.decorateRunnable(retry, refundRunnable);
        
        decoratedRunnable.run();
    }
    
    /**
     * Refund request DTO for internal use
     */
    private record RefundRequest(
        UUID paymentId,
        BigDecimal amount,
        String reason
    ) {}
    
    /**
     * Exception thrown when Payment Service communication fails
     */
    public static class PaymentServiceException extends RuntimeException {
        public PaymentServiceException(String message) {
            super(message);
        }
        
        public PaymentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}