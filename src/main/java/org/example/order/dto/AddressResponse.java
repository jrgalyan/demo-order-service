package org.example.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for address information (shipping/billing)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressResponse(
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country
) {
    
    /**
     * Get full address as a single string
     */
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
     * Get formatted address for display
     */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);
        
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            sb.append("\n").append(addressLine2);
        }
        
        sb.append("\n").append(city).append(", ").append(state).append(" ").append(postalCode);
        sb.append("\n").append(country);
        
        return sb.toString();
    }
    
    /**
     * Check if this is a domestic address (within the same country)
     */
    public boolean isDomestic(String domesticCountry) {
        return country != null && country.equalsIgnoreCase(domesticCountry);
    }
    
    /**
     * Check if address has secondary line
     */
    public boolean hasSecondaryLine() {
        return addressLine2 != null && !addressLine2.trim().isEmpty();
    }
    
    /**
     * Get city and state combination
     */
    public String getCityState() {
        if (city == null || state == null) {
            return city != null ? city : (state != null ? state : "");
        }
        return city + ", " + state;
    }
    
    /**
     * Get state and postal code combination
     */
    public String getStatePostalCode() {
        if (state == null || postalCode == null) {
            return state != null ? state : (postalCode != null ? postalCode : "");
        }
        return state + " " + postalCode;
    }
    
    /**
     * Check if address is complete (has all required fields)
     */
    public boolean isComplete() {
        return addressLine1 != null && !addressLine1.trim().isEmpty() &&
               city != null && !city.trim().isEmpty() &&
               state != null && !state.trim().isEmpty() &&
               postalCode != null && !postalCode.trim().isEmpty() &&
               country != null && !country.trim().isEmpty();
    }
    
    /**
     * Validate postal code format for specific countries
     */
    public boolean isValidPostalCodeFormat() {
        if (country == null || postalCode == null) {
            return false;
        }
        
        return switch (country.toUpperCase()) {
            case "US", "USA", "UNITED STATES" -> postalCode.matches("^\\d{5}(-\\d{4})?$");
            case "CA", "CANADA" -> postalCode.matches("^[A-Za-z]\\d[A-Za-z]\\s?\\d[A-Za-z]\\d$");
            case "UK", "UNITED KINGDOM", "GB" -> postalCode.matches("^[A-Za-z]{1,2}\\d[A-Za-z\\d]?\\s?\\d[A-Za-z]{2}$");
            default -> true; // Accept any format for other countries
        };
    }
    
    /**
     * Get address summary (first line + city, state)
     */
    public String getAddressSummary() {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null) {
            sb.append(addressLine1);
        }
        
        if (city != null || state != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(getCityState());
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this address matches another address
     */
    public boolean matches(AddressResponse other) {
        if (other == null) {
            return false;
        }
        
        return java.util.Objects.equals(this.addressLine1, other.addressLine1) &&
               java.util.Objects.equals(this.addressLine2, other.addressLine2) &&
               java.util.Objects.equals(this.city, other.city) &&
               java.util.Objects.equals(this.state, other.state) &&
               java.util.Objects.equals(this.postalCode, other.postalCode) &&
               java.util.Objects.equals(this.country, other.country);
    }
    
    /**
     * Get normalized postal code (uppercase, no spaces)
     */
    public String getNormalizedPostalCode() {
        return postalCode != null ? postalCode.toUpperCase().replaceAll("\\s", "") : null;
    }
    
    /**
     * Check if this is an international address (outside domestic country)
     */
    public boolean isInternational(String domesticCountry) {
        return !isDomestic(domesticCountry);
    }
}