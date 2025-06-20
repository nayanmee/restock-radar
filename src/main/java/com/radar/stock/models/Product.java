package com.radar.stock.models;

/**
 * Represents a product with its stock information.
 * This record captures the essential data from the Amul API response.
 */
public record Product(
    String name,              // Product display name
    String alias,             // Unique identifier used in URLs
    boolean available,        // Current availability status
    int inventoryQuantity     // Current stock level
) {
    
    /**
     * Checks if the product is currently in stock.
     * @return true if the product is available and has inventory > 0
     */
    public boolean isInStock() {
        return available && inventoryQuantity > 0;
    }
    
    /**
     * Creates a summary string for logging and notifications.
     * @return A formatted string with product name and stock status
     */
    public String toSummary() {
        return String.format("%s [%s] - Stock: %d (Available: %s)", 
            name, alias, inventoryQuantity, available ? "Yes" : "No");
    }
} 