package org.example.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for address information (shipping/billing)
 */
public record AddressRequest(
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
        String addressLine1,
        
        @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
        String addressLine2,
        
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,
        
        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,
        
        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9\\s-]+$", message = "Postal code contains invalid characters")
        String postalCode,
        
        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country
) {
    
    /**
     * Get full address as a single string
     */
    @JsonIgnore
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);
        
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            sb.append(", ").append(addressLine2);
        }
        
        sb.append(", ").append(city);
        sb.append(", ").append(state);
        sb.append(" ").append(postalCode);
        sb.append(", ").append(country);
        
        return sb.toString();
    }
    
    /**
     * Check if this is a domestic address (within the same country)
     */
    @JsonIgnore
    public boolean isDomestic(String domesticCountry) {
        return country.equalsIgnoreCase(domesticCountry);
    }
    
    /**
     * Check if address has secondary line
     */
    @JsonIgnore
    public boolean hasSecondaryLine() {
        return addressLine2 != null && !addressLine2.trim().isEmpty();
    }
    
    /**
     * Validate postal code format for specific countries
     */
    @JsonIgnore
    public boolean isValidPostalCodeFormat() {
        return switch (country.toUpperCase()) {
            case "US", "USA", "UNITED STATES" -> postalCode.matches("^\\d{5}(-\\d{4})?$");
            case "CA", "CANADA" -> postalCode.matches("^[A-Za-z]\\d[A-Za-z]\\s?\\d[A-Za-z]\\d$");
            case "UK", "UNITED KINGDOM", "GB" -> postalCode.matches("^[A-Za-z]{1,2}\\d[A-Za-z\\d]?\\s?\\d[A-Za-z]{2}$");
            default -> true; // Accept any format for other countries
        };
    }
    
    /**
     * Normalize the address for consistent storage
     */
    public AddressRequest normalize() {
        return new AddressRequest(
                addressLine1.trim(),
                addressLine2 != null ? addressLine2.trim() : null,
                city.trim(),
                state.trim(),
                postalCode.trim().toUpperCase(),
                country.trim()
        );
    }
}