package com.radar.stock.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.radar.stock.core.RetryUtility;
import com.radar.stock.models.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing persistent state of product stock levels.
 * This allows the application to track changes between runs and only
 * send notifications when products come back in stock.
 */
public class StateService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StateService.class);
    
    private final ObjectMapper objectMapper;
    
    public StateService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Loads the previous state from a JSON file with retry logic for file system issues.
     * 
     * @param filePath Path to the state file
     * @return Map of product alias to Product, or empty map if file doesn't exist or has errors
     */
    public Map<String, Product> loadState(String filePath) {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            LOGGER.info("State file does not exist: {}. Starting with empty state.", filePath);
            return new HashMap<>();
        }
        
        RetryUtility.RetryConfig retryConfig = RetryUtility.RetryConfig.forStateOperations();
        LOGGER.debug("Loading state from {} with retry config: {}", filePath, 
                    RetryUtility.getConfigSummary(retryConfig));
        
        try {
            return RetryUtility.executeWithRetry(() -> {
                try {
                    return loadStateFromFile(filePath, path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, retryConfig, "Load state from " + filePath);
            
        } catch (Exception e) {
            LOGGER.error("Failed to load state from {} after retries: {}", filePath, e.getMessage());
            LOGGER.warn("Starting with empty state due to persistent load errors");
            return new HashMap<>();
        }
    }
    
    /**
     * Internal method to load state from file without retry logic.
     * 
     * @param filePath The file path for logging
     * @param path The actual Path object
     * @return Map of product state
     * @throws IOException if file operations fail
     */
    private Map<String, Product> loadStateFromFile(String filePath, Path path) throws IOException {
        LOGGER.debug("Attempting to load state from: {}", filePath);
        
        // Check file size for basic validation
        long fileSize = Files.size(path);
        if (fileSize == 0) {
            LOGGER.warn("State file {} is empty, starting with empty state", filePath);
            return new HashMap<>();
        }
        
        if (fileSize > 10 * 1024 * 1024) { // 10MB limit
            throw new IOException("State file is too large: " + fileSize + " bytes");
        }
        
        // Read the JSON file as a list of products first
        TypeReference<List<Product>> typeRef = new TypeReference<List<Product>>() {};
        List<Product> products;
        
        try {
            products = objectMapper.readValue(path.toFile(), typeRef);
        } catch (Exception e) {
            throw new IOException("Failed to parse JSON from state file: " + e.getMessage(), e);
        }
        
        if (products == null) {
            LOGGER.warn("State file contained null data, starting with empty state");
            return new HashMap<>();
        }
        
        // Convert list to map using alias as key
        Map<String, Product> stateMap = new HashMap<>();
        for (Product product : products) {
            if (product != null && product.alias() != null) {
                stateMap.put(product.alias(), product);
            } else {
                LOGGER.warn("Skipping invalid product in state file: {}", product);
            }
        }
        
        LOGGER.info("Successfully loaded state for {} products from: {}", stateMap.size(), filePath);
        return stateMap;
    }
    
    /**
     * Saves the current state to a JSON file with retry logic for file system issues.
     * 
     * @param filePath Path where to save the state file
     * @param state Map of product alias to Product to save
     * @return true if saved successfully, false otherwise
     */
    public boolean saveState(String filePath, Map<String, Product> state) {
        RetryUtility.RetryConfig retryConfig = RetryUtility.RetryConfig.forStateOperations();
        LOGGER.debug("Saving state to {} with retry config: {}", filePath, 
                    RetryUtility.getConfigSummary(retryConfig));
        
        try {
            RetryUtility.executeWithRetry(() -> {
                try {
                    saveStateToFile(filePath, state);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, retryConfig, "Save state to " + filePath);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to save state to {} after retries: {}", filePath, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Internal method to save state to file without retry logic.
     * 
     * @param filePath Path where to save the state file
     * @param state Map of product alias to Product to save
     * @throws IOException if file operations fail
     */
    private void saveStateToFile(String filePath, Map<String, Product> state) throws IOException {
        Path path = Paths.get(filePath);
        
        // Create parent directories if they don't exist
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        
        // Validate state data
        if (state == null) {
            throw new IOException("Cannot save null state");
        }
        
        // Convert map to list for cleaner JSON format
        List<Product> products = state.values().stream()
            .filter(product -> product != null && product.alias() != null)
            .toList();
        
        LOGGER.debug("Saving state for {} valid products to: {}", products.size(), filePath);
        
        // Write to temporary file first for atomic operation
        Path tempPath = Paths.get(filePath + ".tmp");
        
        try {
            // Write to temporary file with pretty printing
            objectMapper.writerWithDefaultPrettyPrinter()
                       .writeValue(tempPath.toFile(), products);
            
            // Verify the file was written correctly
            long fileSize = Files.size(tempPath);
            if (fileSize == 0) {
                throw new IOException("Temporary state file is empty after write");
            }
            
            // Atomic move from temp to final location
            Files.move(tempPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            LOGGER.info("Successfully saved state for {} products to: {} ({} bytes)", 
                       products.size(), filePath, fileSize);
            
        } finally {
            // Clean up temp file if it still exists
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete temporary file {}: {}", tempPath, e.getMessage());
            }
        }
    }
    
    /**
     * Converts a list of products to a state map.
     * 
     * @param products List of products to convert
     * @return Map with product alias as key and Product as value
     */
    public Map<String, Product> productsToStateMap(List<Product> products) {
        Map<String, Product> stateMap = new HashMap<>();
        for (Product product : products) {
            stateMap.put(product.alias(), product);
        }
        return stateMap;
    }
    
    /**
     * Compares two state maps and finds products that have come back in stock.
     * A product is considered "newly in stock" if:
     * - It was out of stock (quantity = 0 or not available) in the previous state
     * - It is now in stock (quantity > 0 and available) in the current state
     * 
     * @param previousState The previous state map
     * @param currentState The current state map
     * @return List of products that are newly back in stock
     */
    public List<Product> findNewlyInStockProducts(Map<String, Product> previousState, 
                                                 Map<String, Product> currentState) {
        return currentState.values().stream()
            .filter(currentProduct -> {
                String alias = currentProduct.alias();
                Product previousProduct = previousState.get(alias);
                
                // If we don't have previous data for this product, don't notify
                if (previousProduct == null) {
                    LOGGER.debug("No previous state for {}, not considered newly in stock", alias);
                    return false;
                }
                
                // Check if it was out of stock before and is in stock now
                boolean wasOutOfStock = !previousProduct.isInStock();
                boolean isNowInStock = currentProduct.isInStock();
                
                if (wasOutOfStock && isNowInStock) {
                    LOGGER.info("Product {} is newly in stock! Was: {} units, Now: {} units", 
                               alias, previousProduct.inventoryQuantity(), currentProduct.inventoryQuantity());
                    return true;
                }
                
                return false;
            })
            .toList();
    }
    
    /**
     * Compares two state maps and finds products that have gone out of stock.
     * A product is considered "newly out of stock" if:
     * - It was in stock (quantity > 0 and available) in the previous state
     * - It is now out of stock (quantity = 0 or not available) in the current state
     * 
     * @param previousState The previous state map
     * @param currentState The current state map
     * @return List of products that are newly out of stock
     */
    public List<Product> findNewlyOutOfStockProducts(Map<String, Product> previousState, 
                                                    Map<String, Product> currentState) {
        return currentState.values().stream()
            .filter(currentProduct -> {
                String alias = currentProduct.alias();
                Product previousProduct = previousState.get(alias);
                
                // If we don't have previous data for this product, don't notify
                if (previousProduct == null) {
                    LOGGER.debug("No previous state for {}, not considered newly out of stock", alias);
                    return false;
                }
                
                // Check if it was in stock before and is out of stock now
                boolean wasInStock = previousProduct.isInStock();
                boolean isNowOutOfStock = !currentProduct.isInStock();
                
                if (wasInStock && isNowOutOfStock) {
                    LOGGER.info("Product {} went out of stock! Was: {} units, Now: {} units", 
                               alias, previousProduct.inventoryQuantity(), currentProduct.inventoryQuantity());
                    return true;
                }
                
                return false;
            })
            .toList();
    }
    
    /**
     * Gets a human-readable summary of the state comparison.
     * 
     * @param previousState Previous state map
     * @param currentState Current state map
     * @return Summary string for logging
     */
    public String getStateComparisonSummary(Map<String, Product> previousState, 
                                           Map<String, Product> currentState) {
        List<Product> newlyInStock = findNewlyInStockProducts(previousState, currentState);
        List<Product> newlyOutOfStock = findNewlyOutOfStockProducts(previousState, currentState);
        
        long previousInStock = previousState.values().stream()
            .mapToLong(p -> p.isInStock() ? 1 : 0)
            .sum();
        
        long currentInStock = currentState.values().stream()
            .mapToLong(p -> p.isInStock() ? 1 : 0)
            .sum();
        
        return String.format("State comparison: Previous (%d in stock) vs Current (%d in stock). " +
                           "Newly in stock: %d products, Newly out of stock: %d products", 
                           previousInStock, currentInStock, newlyInStock.size(), newlyOutOfStock.size());
    }
} 