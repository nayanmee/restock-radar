package com.radar.stock.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Utility class for implementing retry logic with exponential backoff.
 * Provides standardized retry mechanisms for API calls and email delivery.
 */
public class RetryUtility {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtility.class);
    
    /**
     * Configuration for retry operations.
     */
    public static class RetryConfig {
        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double backoffMultiplier;
        
        public RetryConfig(int maxAttempts, long initialDelayMs, long maxDelayMs, double backoffMultiplier) {
            this.maxAttempts = maxAttempts;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.backoffMultiplier = backoffMultiplier;
        }
        
        // Getters
        public int getMaxAttempts() { return maxAttempts; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        
        // Common configurations
        public static RetryConfig forApiCalls() {
            return new RetryConfig(3, 1000, 10000, 2.0); // 3 attempts, 1s, 2s, 4s delays
        }
        
        public static RetryConfig forEmailDelivery() {
            return new RetryConfig(3, 2000, 15000, 1.5); // 3 attempts, 2s, 3s, 4.5s delays
        }
        
        public static RetryConfig forStateOperations() {
            return new RetryConfig(2, 500, 2000, 2.0); // 2 attempts, 0.5s, 1s delays
        }
    }
    
    /**
     * Exception types that should trigger a retry attempt.
     */
    public enum RetryableErrorType {
        NETWORK_ERROR,
        TIMEOUT,
        SERVER_ERROR,
        AUTHENTICATION_ERROR,
        RATE_LIMIT,
        TEMPORARY_FAILURE
    }
    
    /**
     * Executes an operation with retry logic and exponential backoff.
     * 
     * @param operation The operation to execute
     * @param config The retry configuration
     * @param operationName A human-readable name for logging
     * @param <T> The return type of the operation
     * @return The result of the successful operation
     * @throws Exception if all retry attempts fail
     */
    public static <T> T executeWithRetry(Supplier<T> operation, 
                                        RetryConfig config, 
                                        String operationName) throws Exception {
        Exception lastException = null;
        long currentDelay = config.getInitialDelayMs();
        
        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                LOGGER.debug("Executing {} (attempt {}/{})", operationName, attempt, config.getMaxAttempts());
                T result = operation.get();
                
                if (attempt > 1) {
                    LOGGER.info("✅ {} succeeded on attempt {}/{}", operationName, attempt, config.getMaxAttempts());
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt == config.getMaxAttempts()) {
                    LOGGER.error("❌ {} failed after {} attempts. Final error: {}", 
                               operationName, config.getMaxAttempts(), e.getMessage());
                    break;
                }
                
                if (isRetryableException(e)) {
                    LOGGER.warn("⚠️ {} failed on attempt {}/{}. Error: {}. Retrying in {}ms...", 
                              operationName, attempt, config.getMaxAttempts(), 
                              e.getMessage(), currentDelay);
                    
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Retry operation was interrupted", ie);
                    }
                    
                    // Calculate next delay with exponential backoff
                    currentDelay = Math.min(
                        (long) (currentDelay * config.getBackoffMultiplier()), 
                        config.getMaxDelayMs()
                    );
                } else {
                    LOGGER.error("❌ {} failed with non-retryable error: {}", operationName, e.getMessage());
                    break;
                }
            }
        }
        
        throw new Exception("Operation '" + operationName + "' failed after " + 
                          config.getMaxAttempts() + " attempts", lastException);
    }
    
    /**
     * Executes a void operation with retry logic.
     * 
     * @param operation The operation to execute
     * @param config The retry configuration
     * @param operationName A human-readable name for logging
     * @throws Exception if all retry attempts fail
     */
    public static void executeWithRetry(Runnable operation, 
                                       RetryConfig config, 
                                       String operationName) throws Exception {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, config, operationName);
    }
    
    /**
     * Determines if an exception should trigger a retry attempt.
     * 
     * @param exception The exception to check
     * @return true if the operation should be retried, false otherwise
     */
    private static boolean isRetryableException(Exception exception) {
        String message = exception.getMessage().toLowerCase();
        String className = exception.getClass().getSimpleName().toLowerCase();
        
        // Network-related errors
        if (message.contains("connection") || message.contains("network") || 
            message.contains("timeout") || message.contains("unreachable") ||
            className.contains("connection") || className.contains("socket")) {
            return true;
        }
        
        // HTTP server errors (5xx)
        if (message.contains("internal server error") || message.contains("bad gateway") ||
            message.contains("service unavailable") || message.contains("gateway timeout") ||
            message.contains("status 5")) {
            return true;
        }
        
        // Rate limiting
        if (message.contains("rate limit") || message.contains("too many requests") ||
            message.contains("status 429")) {
            return true;
        }
        
        // Temporary failures
        if (message.contains("temporary") || message.contains("retry") ||
            message.contains("try again")) {
            return true;
        }
        
        // Email-specific retryable errors
        if (message.contains("smtp") || message.contains("mail server") ||
            message.contains("could not connect")) {
            return true;
        }
        
        // Non-retryable errors
        if (message.contains("authentication") || message.contains("unauthorized") ||
            message.contains("forbidden") || message.contains("not found") ||
            message.contains("bad request") || message.contains("invalid")) {
            return false;
        }
        
        // Default to retryable for unknown errors
        return true;
    }
    
    /**
     * Creates a summary of the retry configuration for logging.
     * 
     * @param config The retry configuration
     * @return A human-readable summary
     */
    public static String getConfigSummary(RetryConfig config) {
        return String.format("RetryConfig[attempts=%d, initialDelay=%dms, maxDelay=%dms, backoff=%.1fx]",
                           config.getMaxAttempts(), config.getInitialDelayMs(), 
                           config.getMaxDelayMs(), config.getBackoffMultiplier());
    }
} 