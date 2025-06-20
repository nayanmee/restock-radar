package com.radar.stock.models;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a notification to be sent.
 * This record captures all the information needed to send any type of notification.
 */
public record Notification(
    List<String> recipients,    // List of email addresses, phone numbers, or other identifiers
    String subject,             // Subject line for email or title for other notification types
    String message,             // Main notification content
    NotificationType type,      // Type of notification (EMAIL, SMS, etc.)
    NotificationPriority priority,  // Priority level
    List<Product> products,     // Products that triggered this notification
    LocalDateTime timestamp     // When the notification was created
) {
    
    /**
     * Creates a stock alert notification for email.
     * 
     * @param recipients List of email addresses to send to
     * @param products List of products that are now in stock
     * @return A new Notification record for stock alerts
     */
    public static Notification createStockAlert(List<String> recipients, List<Product> products) {
        String subject = String.format("🎉 Amul Stock Alert - %d Product%s Now Available!", 
            products.size(), products.size() == 1 ? "" : "s");
        
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Great news! The following Amul protein products are now back in stock:\n\n");
        
        for (Product product : products) {
            messageBuilder.append("✅ ").append(product.name()).append("\n");
            messageBuilder.append("   Stock Available: ").append(product.inventoryQuantity()).append(" units\n");
            messageBuilder.append("   Product Link: https://shop.amul.com/products/").append(product.alias()).append("\n\n");
        }
        
        messageBuilder.append("Don't miss out - these products tend to sell out quickly!\n");
        messageBuilder.append("\n---\n");
        messageBuilder.append("This is an automated notification from Amul Stock Radar.\n");
        messageBuilder.append("Generated at: ").append(LocalDateTime.now());
        
        return new Notification(
            recipients,
            subject,
            messageBuilder.toString(),
            NotificationType.EMAIL,
            NotificationPriority.HIGH,
            products,
            LocalDateTime.now()
        );
    }
    
    /**
     * Creates an out-of-stock alert notification for email.
     * 
     * @param recipients List of email addresses to send to
     * @param products List of products that went out of stock
     * @return A new Notification record for out-of-stock alerts
     */
    public static Notification createOutOfStockAlert(List<String> recipients, List<Product> products) {
        String subject = String.format("📉 Amul Stock Alert - %d Product%s Sold Out", 
            products.size(), products.size() == 1 ? "" : "s");
        
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("The following Amul protein products have gone out of stock:\n\n");
        
        for (Product product : products) {
            messageBuilder.append("❌ ").append(product.name()).append("\n");
            messageBuilder.append("   Status: Out of Stock (").append(product.inventoryQuantity()).append(" units remaining)\n");
            messageBuilder.append("   Product Link: https://shop.amul.com/products/").append(product.alias()).append("\n\n");
        }
        
        messageBuilder.append("These products were previously available but have now sold out.\n");
        messageBuilder.append("You'll receive another notification when they're back in stock.\n");
        messageBuilder.append("\n---\n");
        messageBuilder.append("This is an automated notification from Amul Stock Radar.\n");
        messageBuilder.append("Generated at: ").append(LocalDateTime.now());
        
        return new Notification(
            recipients,
            subject,
            messageBuilder.toString(),
            NotificationType.EMAIL,
            NotificationPriority.NORMAL,  // Lower priority than in-stock alerts
            products,
            LocalDateTime.now()
        );
    }
    
    /**
     * Creates a test notification for testing the notification system.
     * 
     * @param recipients List of recipients to send the test to
     * @return A test notification
     */
    public static Notification createTestNotification(List<String> recipients) {
        return new Notification(
            recipients,
            "🧪 Amul Stock Radar - Test Notification",
            "This is a test notification to verify that the Amul Stock Radar notification system is working correctly.\n\n" +
            "If you receive this message, your notification setup is functioning properly!\n\n" +
            "---\n" +
            "Test sent at: " + LocalDateTime.now(),
            NotificationType.EMAIL,
            NotificationPriority.LOW,
            List.of(),
            LocalDateTime.now()
        );
    }
    
    /**
     * Creates a test notification for a single recipient (convenience method).
     * 
     * @param recipient The recipient to send the test to
     * @return A test notification
     */
    public static Notification createTestNotification(String recipient) {
        return createTestNotification(List.of(recipient));
    }
    
    /**
     * Creates a stock alert notification for a single recipient (convenience method).
     * 
     * @param recipient The email address to send to
     * @param products List of products that are now in stock
     * @return A new Notification record for stock alerts
     */
    public static Notification createStockAlert(String recipient, List<Product> products) {
        return createStockAlert(List.of(recipient), products);
    }
    
    /**
     * Creates an out-of-stock alert notification for a single recipient (convenience method).
     * 
     * @param recipient The email address to send to
     * @param products List of products that went out of stock
     * @return A new Notification record for out-of-stock alerts
     */
    public static Notification createOutOfStockAlert(String recipient, List<Product> products) {
        return createOutOfStockAlert(List.of(recipient), products);
    }
    
    /**
     * Enum representing different types of notifications.
     */
    public enum NotificationType {
        EMAIL,
        SMS,
        SLACK,
        PUSH,
        WEBHOOK
    }
    
    /**
     * Enum representing notification priority levels.
     */
    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
