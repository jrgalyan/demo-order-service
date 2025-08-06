package org.example.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Configuration for RestTemplate with resilience patterns
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * RestTemplate with timeout and interceptors
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(requestIdInterceptor(), loggingInterceptor())
                .build();
    }
    
    /**
     * Circuit breaker for Product Service
     */
    @Bean
    public CircuitBreaker productServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s in open state
                .slidingWindowSize(10) // Consider last 10 calls
                .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
                .slowCallRateThreshold(50) // 50% slow call rate threshold
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // Calls slower than 2s are slow
                .build();
        
        return CircuitBreaker.of("productService", config);
    }
    
    /**
     * Circuit breaker for Payment Service
     */
    @Bean
    public CircuitBreaker paymentServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60) // Higher threshold for payment service
                .waitDurationInOpenState(Duration.ofSeconds(60)) // Longer wait for payment service
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // Payment calls can be slower
                .build();
        
        return CircuitBreaker.of("paymentService", config);
    }
    
    /**
     * Circuit breaker for Notification Service
     */
    @Bean
    public CircuitBreaker notificationServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(70) // More lenient for notifications
                .waitDurationInOpenState(Duration.ofSeconds(15)) // Shorter wait for notifications
                .slidingWindowSize(15)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(2)
                .slowCallRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .build();
        
        return CircuitBreaker.of("notificationService", config);
    }
    
    /**
     * Retry configuration for Product Service
     */
    @Bean
    public Retry productServiceRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .intervalFunction(exponentialBackoffInterval())
                .retryOnException(throwable -> 
                    throwable instanceof org.springframework.web.client.ResourceAccessException ||
                    throwable instanceof org.springframework.web.client.HttpServerErrorException)
                .build();
        
        return Retry.of("productService", config);
    }
    
    /**
     * Retry configuration for Payment Service
     */
    @Bean
    public Retry paymentServiceRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2) // Fewer retries for payment operations
                .waitDuration(Duration.ofSeconds(2))
                .intervalFunction(exponentialBackoffInterval())
                .retryOnException(throwable -> 
                    throwable instanceof org.springframework.web.client.ResourceAccessException)
                .build();
        
        return Retry.of("paymentService", config);
    }
    
    /**
     * Retry configuration for Notification Service
     */
    @Bean
    public Retry notificationServiceRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(4) // More retries for notifications
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(exponentialBackoffInterval())
                .retryOnException(throwable -> 
                    throwable instanceof org.springframework.web.client.ResourceAccessException ||
                    throwable instanceof org.springframework.web.client.HttpServerErrorException)
                .build();
        
        return Retry.of("notificationService", config);
    }
    
    /**
     * Request ID interceptor for distributed tracing
     */
    private ClientHttpRequestInterceptor requestIdInterceptor() {
        return (request, body, execution) -> {
            String requestId = UUID.randomUUID().toString();
            request.getHeaders().add("X-Request-ID", requestId);
            request.getHeaders().add("X-Service-Name", "order-service");
            return execution.execute(request, body);
        };
    }
    
    /**
     * Logging interceptor for request/response logging
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            long startTime = System.currentTimeMillis();
            
            try {
                var response = execution.execute(request, body);
                long duration = System.currentTimeMillis() - startTime;
                
                org.slf4j.LoggerFactory.getLogger(RestTemplateConfig.class)
                    .debug("HTTP {} {} -> {} ({}ms)", 
                           request.getMethod(), 
                           request.getURI(), 
                           response.getStatusCode(),
                           duration);
                
                return response;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                
                org.slf4j.LoggerFactory.getLogger(RestTemplateConfig.class)
                    .error("HTTP {} {} -> ERROR: {} ({}ms)", 
                           request.getMethod(), 
                           request.getURI(), 
                           e.getMessage(),
                           duration);
                
                throw e;
            }
        };
    }

    /**
     * Interval function for retry backoff
     */
    private IntervalFunction exponentialBackoffInterval() {
        return  IntervalFunction.ofExponentialBackoff(IntervalFunction.DEFAULT_INITIAL_INTERVAL, 2d);
    }
}