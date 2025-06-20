package com.radar.stock.extractors;

/**
 * Exception thrown when there's an error extracting stock information
 * from external sources.
 */
public class StockExtractionException extends Exception {
    
    /**
     * Constructs a new StockExtractionException with the specified detail message.
     * 
     * @param message the detail message
     */
    public StockExtractionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new StockExtractionException with the specified detail message
     * and cause.
     * 
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public StockExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new StockExtractionException with the specified cause.
     * 
     * @param cause the cause of this exception
     */
    public StockExtractionException(Throwable cause) {
        super(cause);
    }
} 