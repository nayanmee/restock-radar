package com.radar.stock.notifiers;

/**
 * Exception thrown when there's an error sending notifications.
 */
public class NotificationException extends Exception {
    
    /**
     * Constructs a new NotificationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public NotificationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new NotificationException with the specified detail message
     * and cause.
     * 
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new NotificationException with the specified cause.
     * 
     * @param cause the cause of this exception
     */
    public NotificationException(Throwable cause) {
        super(cause);
    }
} 