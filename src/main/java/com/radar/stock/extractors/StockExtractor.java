package com.radar.stock.extractors;

import com.radar.stock.models.Product;
import java.util.List;

/**
 * Interface for extracting stock information from external sources.
 * This provides a pluggable architecture for different data sources
 * (e.g., Amul API, Amazon scraper, Flipkart API, etc.).
 */
public interface StockExtractor {
    
    /**
     * Fetches current stock information for the specified product aliases.
     * 
     * @param productAliases List of product aliases to check stock for.
     *                      If empty or null, should return all available products.
     * @return List of Product objects with current stock information
     * @throws StockExtractionException if there's an error fetching data
     */
    List<Product> checkStock(List<String> productAliases) throws StockExtractionException;
    
    /**
     * Fetches stock information for all available products.
     * This is a convenience method equivalent to calling checkStock(null).
     * 
     * @return List of all available Product objects with current stock information
     * @throws StockExtractionException if there's an error fetching data
     */
    default List<Product> checkAllStock() throws StockExtractionException {
        return checkStock(null);
    }
    
    /**
     * Returns a human-readable name for this extractor implementation.
     * Used for logging and debugging purposes.
     * 
     * @return The name of this stock extractor
     */
    String getExtractorName();
} 