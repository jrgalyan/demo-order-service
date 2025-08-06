package org.example.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.example.order.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Client for communicating with Notification Service
 */
@Component
public class NotificationServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String notificationServiceUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    public NotificationServiceClient(RestTemplate restTemplate,
                                   @Value("${services.notification-service.url:http://localhost:8085}") String notificationServiceUrl,
                                   @Qualifier("notificationServiceCircuitBreaker") CircuitBreaker circuitBreaker,
                                   @Qualifier("notificationServiceRetry") Retry retry) {
        this.restTemplate = restTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }
    
    /**
     * Send order confirmation notification
     */
    public void sendOrderConfirmation(UUID userId, String userEmail, String orderNumber, 
                                     String customerName, Map<String, Object> orderDetails) {
        logger.info("Sending order confirmation notification for order: {} to: {}", orderNumber, userEmail);
        
        NotificationRequest request = new NotificationRequest(
            "ORDER_CONFIRMATION",
            userEmail,
            "Order Confirmation - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "orderDetails", orderDetails
            )
        );
        
        sendNotificationWithResilience(request, "order confirmation");
    }
    
    /**
     * Send order status update notification
     */
    public void sendOrderStatusUpdate(UUID userId, String userEmail, String orderNumber,
                                     String customerName, String oldStatus, String newStatus) {
        logger.info("Sending order status update notification for order: {} from {} to {}", 
                   orderNumber, oldStatus, newStatus);
        
        NotificationRequest request = new NotificationRequest(
            "ORDER_STATUS_UPDATE",
            userEmail,
            "Order Status Update - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "oldStatus", oldStatus,
                "newStatus", newStatus
            )
        );
        
        sendNotificationWithResilience(request, "order status update");
    }
    
    /**
     * Send order cancellation notification
     */
    public void sendOrderCancellation(UUID userId, String userEmail, String orderNumber,
                                     String customerName, String reason) {
        logger.info("Sending order cancellation notification for order: {} to: {}", orderNumber, userEmail);
        
        NotificationRequest request = new NotificationRequest(
            "ORDER_CANCELLATION",
            userEmail,
            "Order Cancelled - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "reason", reason != null ? reason : "Customer request"
            )
        );
        
        sendNotificationWithResilience(request, "order cancellation");
    }
    
    /**
     * Send payment confirmation notification
     */
    public void sendPaymentConfirmation(UUID userId, String userEmail, String orderNumber,
                                       String customerName, String paymentNumber, String amount) {
        logger.info("Sending payment confirmation notification for order: {} payment: {}", 
                   orderNumber, paymentNumber);
        
        NotificationRequest request = new NotificationRequest(
            "PAYMENT_CONFIRMATION",
            userEmail,
            "Payment Confirmed - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "paymentNumber", paymentNumber,
                "amount", amount
            )
        );
        
        sendNotificationWithResilience(request, "payment confirmation");
    }
    
    /**
     * Send payment failure notification
     */
    public void sendPaymentFailure(UUID userId, String userEmail, String orderNumber,
                                  String customerName, String reason) {
        logger.info("Sending payment failure notification for order: {} to: {}", orderNumber, userEmail);
        
        NotificationRequest request = new NotificationRequest(
            "PAYMENT_FAILURE",
            userEmail,
            "Payment Failed - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "reason", reason != null ? reason : "Payment processing failed"
            )
        );
        
        sendNotificationWithResilience(request, "payment failure");
    }
    
    /**
     * Send shipping notification
     */
    public void sendShippingNotification(UUID userId, String userEmail, String orderNumber,
                                        String customerName, String trackingNumber, String carrier) {
        logger.info("Sending shipping notification for order: {} tracking: {}", orderNumber, trackingNumber);
        
        NotificationRequest request = new NotificationRequest(
            "ORDER_SHIPPED",
            userEmail,
            "Order Shipped - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber,
                "trackingNumber", trackingNumber != null ? trackingNumber : "N/A",
                "carrier", carrier != null ? carrier : "Standard Shipping"
            )
        );
        
        sendNotificationWithResilience(request, "shipping notification");
    }
    
    /**
     * Send delivery confirmation notification
     */
    public void sendDeliveryConfirmation(UUID userId, String userEmail, String orderNumber,
                                        String customerName) {
        logger.info("Sending delivery confirmation notification for order: {}", orderNumber);
        
        NotificationRequest request = new NotificationRequest(
            "ORDER_DELIVERED",
            userEmail,
            "Order Delivered - " + orderNumber,
            Map.of(
                "customerName", customerName,
                "orderNumber", orderNumber
            )
        );
        
        sendNotificationWithResilience(request, "delivery confirmation");
    }
    
    /**
     * Send low inventory alert to admin
     */
    public void sendLowInventoryAlert(String productName, String productSku, Integer currentStock, 
                                     Integer threshold) {
        logger.info("Sending low inventory alert for product: {} current stock: {}", productName, currentStock);
        
        NotificationRequest request = new NotificationRequest(
            "LOW_INVENTORY_ALERT",
            "admin@ecommerce.com", // Admin email
            "Low Inventory Alert - " + productName,
            Map.of(
                "productName", productName,
                "productSku", productSku,
                "currentStock", currentStock,
                "threshold", threshold
            )
        );
        
        sendNotificationWithResilience(request, "low inventory alert");
    }
    
    /**
     * Send notification with resilience patterns applied
     */
    private void sendNotificationWithResilience(NotificationRequest request, String notificationType) {
        Runnable notificationRunnable = () -> {
            try {
                String url = notificationServiceUrl + "/api/v1/notifications";
                ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.debug("Successfully sent {} notification to: {}", notificationType, request.recipient());
                } else {
                    logger.error("Failed to send {} notification: HTTP {}", 
                               notificationType, response.getStatusCode());
                    throw new NotificationServiceException("Failed to send " + notificationType + " notification");
                }
                
            } catch (HttpClientErrorException e) {
                logger.error("HTTP error when sending {} notification: {} - {}", 
                           notificationType, e.getStatusCode(), e.getResponseBodyAsString());
                throw new NotificationServiceException("Failed to send notification: " + e.getMessage());
            } catch (ResourceAccessException e) {
                logger.error("Network error when sending {} notification: {}", notificationType, e.getMessage());
                throw new NotificationServiceException("Notification service unavailable");
            } catch (Exception e) {
                logger.error("Unexpected error when sending {} notification: {}", notificationType, e.getMessage(), e);
                throw new NotificationServiceException("Failed to send notification: " + e.getMessage());
            }
        };
        
        // Apply circuit breaker and retry patterns
        Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(circuitBreaker, notificationRunnable);
        decoratedRunnable = Retry.decorateRunnable(retry, decoratedRunnable);
        
        try {
            decoratedRunnable.run();
        } catch (Exception e) {
            // Log the error but don't fail the main operation for notification failures
            logger.error("Failed to send {} notification after retries: {}", notificationType, e.getMessage());
            // In a production system, you might want to queue the notification for later retry
        }
    }
    
    /**
     * Exception thrown when Notification Service communication fails
     */
    public static class NotificationServiceException extends RuntimeException {
        public NotificationServiceException(String message) {
            super(message);
        }
        
        public NotificationServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}