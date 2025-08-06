package org.example.order.client;

import org.example.order.dto.ProductInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Client for communicating with Product Service
 */
@Component
public class ProductServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String productServiceUrl;
    
    public ProductServiceClient(RestTemplate restTemplate,
                               @Value("${services.product-service.url:http://localhost:8082}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }
    
    /**
     * Get product information by ID
     */
    public ProductInfo getProduct(UUID productId) {
        try {
            logger.debug("Fetching product information for ID: {}", productId);
            
            String url = productServiceUrl + "/api/v1/products/" + productId;
            ResponseEntity<ProductResponse> response = restTemplate.getForEntity(url, ProductResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ProductResponse product = response.getBody();
                logger.debug("Successfully retrieved product: {}", product.name());
                
                return new ProductInfo(
                    product.id(),
                    product.name(),
                    product.sku(),
                    product.price(),
                    product.inStock(),
                    product.availableQuantity()
                );
            } else {
                logger.warn("Product service returned empty response for product ID: {}", productId);
                throw new ProductServiceException("Product not found: " + productId);
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("Product not found: {}", productId);
            throw new ProductServiceException("Product not found: " + productId);
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when fetching product {}: {} - {}", 
                        productId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ProductServiceException("Failed to fetch product: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Network error when fetching product {}: {}", productId, e.getMessage());
            throw new ProductServiceException("Product service unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error when fetching product {}: {}", productId, e.getMessage(), e);
            throw new ProductServiceException("Failed to fetch product: " + e.getMessage());
        }
    }
    
    /**
     * Get multiple products by IDs
     */
    public List<ProductInfo> getProducts(List<UUID> productIds) {
        logger.debug("Fetching {} products", productIds.size());
        
        return productIds.stream()
                .map(this::getProduct)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if product has sufficient inventory
     */
    public boolean checkInventory(UUID productId, Integer requestedQuantity) {
        try {
            logger.debug("Checking inventory for product {} with quantity {}", productId, requestedQuantity);
            
            String url = productServiceUrl + "/api/v1/inventory/product/" + productId + "/sufficient-stock" +
                        "?quantity=" + requestedQuantity;
            
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                boolean hasSufficientStock = response.getBody();
                logger.debug("Inventory check for product {}: sufficient={}", productId, hasSufficientStock);
                return hasSufficientStock;
            } else {
                logger.warn("Inventory service returned empty response for product ID: {}", productId);
                return false;
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("Product not found for inventory check: {}", productId);
            return false;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when checking inventory for product {}: {} - {}", 
                        productId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ProductServiceException("Failed to check inventory: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Network error when checking inventory for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException("Product service unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error when checking inventory for product {}: {}", productId, e.getMessage(), e);
            throw new ProductServiceException("Failed to check inventory: " + e.getMessage());
        }
    }
    
    /**
     * Reserve inventory for products
     */
    public void reserveInventory(UUID productId, Integer quantity) {
        try {
            logger.debug("Reserving {} units of product {}", quantity, productId);
            
            String url = productServiceUrl + "/api/v1/inventory/product/" + productId + "/reserve" +
                        "?quantity=" + quantity;
            
            ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully reserved {} units of product {}", quantity, productId);
            } else {
                logger.error("Failed to reserve inventory for product {}: HTTP {}", 
                           productId, response.getStatusCode());
                throw new ProductServiceException("Failed to reserve inventory");
            }
            
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when reserving inventory for product {}: {} - {}", 
                        productId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ProductServiceException("Failed to reserve inventory: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Network error when reserving inventory for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException("Product service unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error when reserving inventory for product {}: {}", productId, e.getMessage(), e);
            throw new ProductServiceException("Failed to reserve inventory: " + e.getMessage());
        }
    }
    
    /**
     * Release reserved inventory for products
     */
    public void releaseInventory(UUID productId, Integer quantity) {
        try {
            logger.debug("Releasing {} units of product {}", quantity, productId);
            
            String url = productServiceUrl + "/api/v1/inventory/product/" + productId + "/release" +
                        "?quantity=" + quantity;
            
            ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully released {} units of product {}", quantity, productId);
            } else {
                logger.error("Failed to release inventory for product {}: HTTP {}", 
                           productId, response.getStatusCode());
                throw new ProductServiceException("Failed to release inventory");
            }
            
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when releasing inventory for product {}: {} - {}", 
                        productId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ProductServiceException("Failed to release inventory: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Network error when releasing inventory for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException("Product service unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error when releasing inventory for product {}: {}", productId, e.getMessage(), e);
            throw new ProductServiceException("Failed to release inventory: " + e.getMessage());
        }
    }
    
    /**
     * Confirm sale and reduce inventory
     */
    public void confirmSale(UUID productId, Integer quantity) {
        try {
            logger.debug("Confirming sale of {} units of product {}", quantity, productId);
            
            String url = productServiceUrl + "/api/v1/inventory/product/" + productId + "/confirm-sale" +
                        "?quantity=" + quantity;
            
            ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully confirmed sale of {} units of product {}", quantity, productId);
            } else {
                logger.error("Failed to confirm sale for product {}: HTTP {}", 
                           productId, response.getStatusCode());
                throw new ProductServiceException("Failed to confirm sale");
            }
            
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when confirming sale for product {}: {} - {}", 
                        productId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ProductServiceException("Failed to confirm sale: " + e.getMessage());
        } catch (ResourceAccessException e) {
            logger.error("Network error when confirming sale for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException("Product service unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error when confirming sale for product {}: {}", productId, e.getMessage(), e);
            throw new ProductServiceException("Failed to confirm sale: " + e.getMessage());
        }
    }
    
    /**
     * Product response DTO for internal use
     */
    private record ProductResponse(
        UUID id,
        String name,
        String sku,
        BigDecimal price,
        Boolean inStock,
        Integer availableQuantity
    ) {}
    
    /**
     * Exception thrown when Product Service communication fails
     */
    public static class ProductServiceException extends RuntimeException {
        public ProductServiceException(String message) {
            super(message);
        }
        
        public ProductServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}