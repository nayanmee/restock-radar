package com.radar.stock.notifiers;

import com.radar.stock.models.Notification;

/**
 * Interface for sending notifications through various channels.
 * This provides a pluggable architecture for different notification methods
 * (e.g., Email, SMS, Slack, Push notifications, etc.).
 */
public interface Notifier {
    
    /**
     * Sends a notification through the implementing channel.
     * 
     * @param notification The notification to be sent
     * @throws NotificationException if there's an error sending the notification
     */
    void send(Notification notification) throws NotificationException;
    
    /**
     * Tests the notification channel to verify it's properly configured.
     * This is useful for setup validation and troubleshooting.
     * 
     * @param testRecipient The recipient to send a test notification to
     * @return true if the test notification was sent successfully, false otherwise
     * @throws NotificationException if there's an error during testing
     */
    boolean testConnection(String testRecipient) throws NotificationException;
    
    /**
     * Returns a human-readable name for this notifier implementation.
     * Used for logging and debugging purposes.
     * 
     * @return The name of this notifier
     */
    String getNotifierName();
    
    /**
     * Returns the type of notifications this notifier handles.
     * 
     * @return The notification type
     */
    Notification.NotificationType getSupportedType();
    
    /**
     * Checks if this notifier is properly configured and ready to send notifications.
     * 
     * @return true if the notifier is configured and ready
     */
    boolean isConfigured();
    
    /**
     * Sends multiple notifications in batch (if supported by the implementation).
     * Default implementation sends notifications one by one.
     * 
     * @param notifications Array of notifications to send
     * @throws NotificationException if there's an error sending any notification
     */
    default void sendBatch(Notification... notifications) throws NotificationException {
        for (Notification notification : notifications) {
            send(notification);
        }
    }
} 