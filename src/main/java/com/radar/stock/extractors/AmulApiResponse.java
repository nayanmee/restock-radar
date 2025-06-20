package com.radar.stock.extractors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Record to map the JSON response from the Amul API.
 * Uses Jackson annotations for JSON deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmulApiResponse(
    List<Message> messages,
    @JsonProperty("fileBaseUrl") String fileBaseUrl,
    List<AmulProduct> data,
    Paging paging
) {
    
    /**
     * Represents a message in the API response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String name,
        String level
    ) {}
    
    /**
     * Represents a product in the Amul API response.
     * Maps the JSON fields to our domain model.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmulProduct(
        @JsonProperty("_id") String id,
        String alias,
        String name,
        String brand,
        int available,           // 0 or 1
        @JsonProperty("inventory_quantity") int inventoryQuantity,
        int price,
        String sku,
        @JsonProperty("avg_rating") Double avgRating,
        @JsonProperty("num_reviews") Integer numReviews,
        List<String> categories,
        List<String> collections
    ) {}
    
    /**
     * Represents pagination information in the API response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(
        int limit,
        int start,
        int count,
        int total
    ) {}
} 