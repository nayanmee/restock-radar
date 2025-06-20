package com.radar.stock.notifiers;

import com.radar.stock.models.Notification;
import com.radar.stock.models.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Integration test for email notifications with realistic Amul product data.
 * 
 * This test demonstrates exactly how product information appears in email notifications.
 * It creates sample Amul products and sends actual emails to show the notification format.
 * 
 * SETUP INSTRUCTIONS:
 * 1. Set environment variables: SMTP_USERNAME and SMTP_PASSWORD
 * 2. Update recipient email addresses in the test constants
 * 3. Remove @Disabled annotation from the test you want to run
 * 4. Run the specific test method
 * 
 * The test will:
 * - Show email content preview in console logs
 * - Send actual emails to demonstrate formatting
 * - Test both single and multiple recipient scenarios
 * - Test both in-stock and out-of-stock notifications
 */
public class EmailNotificationIntegrationTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationIntegrationTest.class);
    
    // Test configuration - UPDATE THESE TO YOUR EMAIL ADDRESSES
    private static final String PRIMARY_RECIPIENT = "reciepient@gmail.com";
    private static final List<String> MULTIPLE_RECIPIENTS = List.of(
        "reciepient@gmail.com",
        "test.recipient2@example.com",  // Add your second email here
        "test.recipient3@example.com"   // Add your third email here
    );
    
    private EmailNotifier emailNotifier;
    
    @BeforeEach
    void setUp() throws NotificationException {
        // Get credentials from environment variables
        String username = System.getenv("SMTP_USERNAME");
        String password = System.getenv("SMTP_PASSWORD");
        
        if (username == null || password == null) {
            LOGGER.warn("⚠️ SMTP credentials not found in environment variables");
            LOGGER.warn("📧 Set SMTP_USERNAME and SMTP_PASSWORD to run email tests");
            LOGGER.warn("💡 Example: export SMTP_USERNAME='your.email@gmail.com'");
            LOGGER.warn("💡 Example: export SMTP_PASSWORD='your_app_password'");
            return;
        }
        
        // Create Gmail email notifier for testing
        emailNotifier = EmailNotifier.forGmail(username, password);
        LOGGER.info("✅ Email notifier configured for testing with Gmail");
    }
    
    /**
     * Test in-stock notification with realistic Amul product data.
     * This shows exactly how restock alerts appear in your mailbox.
     */
    @Test
    @DisplayName("🎉 Test In-Stock Notification - Shows Restock Alert Format")
    @Disabled("Remove @Disabled to send actual test emails")
    void testInStockNotificationWithRealProductData() throws NotificationException {
        if (emailNotifier == null) {
            LOGGER.warn("⏭️ Skipping test - email notifier not configured (check environment variables)");
            return;
        }
        
        LOGGER.info("🎯 Testing IN-STOCK notification with realistic Amul products");
        
        // Create realistic Amul protein products that just came back in stock
        List<Product> newlyInStockProducts = Arrays.asList(
            new Product(
                "Amul Protein Buttermilk Spiced", 
                "amul-protein-buttermilk-spiced-200ml", 
                true, 
                15  // 15 units available
            ),
            new Product(
                "Amul High Protein Lassi Mango", 
                "amul-high-protein-lassi-mango-250ml", 
                true, 
                8   // 8 units available
            ),
            new Product(
                "Amul Protein Drink Chocolate", 
                "amul-protein-drink-chocolate-200ml", 
                true, 
                22  // 22 units available
            ),
            new Product(
                "Amul Greek Yogurt High Protein", 
                "amul-greek-yogurt-high-protein-130g", 
                true, 
                3   // Low stock - only 3 units
            )
        );
        
        LOGGER.info("📦 Creating notification for {} newly in-stock products", newlyInStockProducts.size());
        
        // Create the notification using the same method as the real application
        Notification notification = Notification.createStockAlert(PRIMARY_RECIPIENT, newlyInStockProducts);
        
        // Show notification details
        LOGGER.info("📧 Notification Details:");
        LOGGER.info("   ✉️  Subject: {}", notification.subject());
        LOGGER.info("   ⚡ Priority: {} (High priority for restocks)", notification.priority());
        LOGGER.info("   📱 Type: {}", notification.type());
        LOGGER.info("   📦 Products: {}", notification.products().size());
        
        // Preview the complete email content
        LOGGER.info("\n" + "=".repeat(60));
        LOGGER.info("📧 EMAIL CONTENT PREVIEW");
        LOGGER.info("=".repeat(60));
        LOGGER.info("📬 TO: {}", PRIMARY_RECIPIENT);
        LOGGER.info("📄 SUBJECT: {}", notification.subject());
        LOGGER.info("⚡ PRIORITY: {}", notification.priority());
        LOGGER.info("\n📝 MESSAGE BODY:");
        LOGGER.info(notification.message());
        LOGGER.info("=".repeat(60) + "\n");
        
        // Send the actual email
        LOGGER.info("🚀 Sending in-stock notification email to: {}", PRIMARY_RECIPIENT);
        emailNotifier.send(notification);
        LOGGER.info("✅ In-stock notification sent successfully!");
        
        // Provide user guidance
        LOGGER.info("📧 ✨ CHECK YOUR MAILBOX at {} ✨", PRIMARY_RECIPIENT);
        LOGGER.info("🔍 This email shows exactly how RESTOCK ALERTS appear to users");
        LOGGER.info("🎯 Notice the high priority and detailed product information");
    }
    
    /**
     * Test out-of-stock notification with realistic Amul product data.
     * This shows how sell-out alerts appear in your mailbox.
     */
    @Test
    @DisplayName("📉 Test Out-of-Stock Notification - Shows Sell-Out Alert Format")
    @Disabled("Remove @Disabled to send actual test emails")
    void testOutOfStockNotificationWithRealProductData() throws NotificationException {
        if (emailNotifier == null) {
            LOGGER.warn("⏭️ Skipping test - email notifier not configured (check environment variables)");
            return;
        }
        
        LOGGER.info("🎯 Testing OUT-OF-STOCK notification with realistic Amul products");
        
        // Create realistic Amul protein products that just went out of stock
        List<Product> newlyOutOfStockProducts = Arrays.asList(
            new Product(
                "Amul Protein Plus Vanilla", 
                "amul-protein-plus-vanilla-200ml", 
                false, 
                0   // Out of stock
            ),
            new Product(
                "Amul ProLife Probiotic Lassi", 
                "amul-prolife-probiotic-lassi-250ml", 
                false, 
                0   // Out of stock
            ),
            new Product(
                "Amul Protein Shake Strawberry", 
                "amul-protein-shake-strawberry-200ml", 
                false, 
                0   // Out of stock
            )
        );
        
        LOGGER.info("📦 Creating notification for {} newly out-of-stock products", newlyOutOfStockProducts.size());
        
        // Create the notification using the same method as the real application
        Notification notification = Notification.createOutOfStockAlert(PRIMARY_RECIPIENT, newlyOutOfStockProducts);
        
        // Show notification details
        LOGGER.info("📧 Notification Details:");
        LOGGER.info("   ✉️  Subject: {}", notification.subject());
        LOGGER.info("   📱 Priority: {} (Normal priority for sell-outs)", notification.priority());
        LOGGER.info("   📱 Type: {}", notification.type());
        LOGGER.info("   📦 Products: {}", notification.products().size());
        
        // Preview the complete email content
        LOGGER.info("\n" + "=".repeat(60));
        LOGGER.info("📧 EMAIL CONTENT PREVIEW");
        LOGGER.info("=".repeat(60));
        LOGGER.info("📬 TO: {}", PRIMARY_RECIPIENT);
        LOGGER.info("📄 SUBJECT: {}", notification.subject());
        LOGGER.info("📱 PRIORITY: {}", notification.priority());
        LOGGER.info("\n📝 MESSAGE BODY:");
        LOGGER.info(notification.message());
        LOGGER.info("=".repeat(60) + "\n");
        
        // Send the actual email
        LOGGER.info("🚀 Sending out-of-stock notification email to: {}", PRIMARY_RECIPIENT);
        emailNotifier.send(notification);
        LOGGER.info("✅ Out-of-stock notification sent successfully!");
        
        // Provide user guidance
        LOGGER.info("📧 ✨ CHECK YOUR MAILBOX at {} ✨", PRIMARY_RECIPIENT);
        LOGGER.info("🔍 This email shows exactly how SELL-OUT ALERTS appear to users");
        LOGGER.info("🎯 Notice the normal priority and tracking information");
    }
    
    /**
     * Test both notification types back-to-back for comparison.
     * This sends both in-stock and out-of-stock notifications to compare formats.
     */
    @Test
    @DisplayName("🔀 Test Both Notification Types - Format Comparison")
    @Disabled("Remove @Disabled to send actual test emails")
    void testBothNotificationTypesForComparison() throws NotificationException {
        if (emailNotifier == null) {
            LOGGER.warn("⏭️ Skipping test - email notifier not configured (check environment variables)");
            return;
        }
        
        LOGGER.info("🎯 Testing BOTH notification types for format comparison");
        LOGGER.info("📧 You'll receive 2 emails to compare the different formats");
        
        // Test in-stock notification first
        LOGGER.info("\n1️⃣ Sending IN-STOCK notification...");
        testInStockNotificationWithRealProductData();
        
        // Wait between emails to ensure they arrive separately
        try {
            LOGGER.info("⏳ Waiting 3 seconds between emails...");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Test out-of-stock notification second
        LOGGER.info("\n2️⃣ Sending OUT-OF-STOCK notification...");
        testOutOfStockNotificationWithRealProductData();
        
        LOGGER.info("\n🎉 Both notification types sent successfully!");
        LOGGER.info("📧 Check your mailbox to compare:");
        LOGGER.info("   🎉 High-priority RESTOCK alert (first email)");
        LOGGER.info("   📉 Normal-priority SELL-OUT alert (second email)");
        LOGGER.info("🔍 Notice the different subjects, priorities, and content structure");
    }
    
    /**
     * Test comprehensive notification with various product scenarios.
     */
    @Test
    @DisplayName("📊 Test Comprehensive Notification - Various Stock Levels")
    @Disabled("Remove @Disabled to send actual test emails")
    void testComprehensiveNotificationScenarios() throws NotificationException {
        if (emailNotifier == null) {
            LOGGER.warn("⏭️ Skipping test - email notifier not configured (check environment variables)");
            return;
        }
        
        LOGGER.info("🎯 Testing COMPREHENSIVE notification with various stock scenarios");
        
        // Create products with different stock levels to show variety
        List<Product> diverseProducts = Arrays.asList(
            new Product(
                "Amul Protein Buttermilk Original", 
                "amul-protein-buttermilk-original-200ml", 
                true, 
                2   // Very low stock - urgency!
            ),
            new Product(
                "Amul High Protein Lassi Strawberry", 
                "amul-high-protein-lassi-strawberry-250ml", 
                true, 
                12  // Medium stock - good availability
            ),
            new Product(
                "Amul Protein Shake Vanilla", 
                "amul-protein-shake-vanilla-200ml", 
                true, 
                35  // High stock - plenty available
            ),
            new Product(
                "Amul Greek Yogurt Berry Protein", 
                "amul-greek-yogurt-berry-protein-130g", 
                true, 
                1   // Critical - only 1 unit left!
            ),
            new Product(
                "Amul Protein Drink Mixed Berry", 
                "amul-protein-drink-mixed-berry-200ml", 
                true, 
                18  // Good stock level
            )
        );
        
        LOGGER.info("📦 Creating comprehensive notification with {} diverse products", diverseProducts.size());
        
        // Show detailed breakdown
        LOGGER.info("\n📊 PRODUCT BREAKDOWN:");
        for (Product product : diverseProducts) {
            String stockLevel = getStockLevelDescription(product.inventoryQuantity());
            LOGGER.info("   📦 {} ({}) - {} units [{}]", 
                       product.name(), 
                       product.alias(), 
                       product.inventoryQuantity(),
                       stockLevel);
        }
        
        // Create the notification
        Notification notification = Notification.createStockAlert(PRIMARY_RECIPIENT, diverseProducts);
        
        // Preview the comprehensive email
        LOGGER.info("\n" + "=".repeat(70));
        LOGGER.info("📧 COMPREHENSIVE EMAIL PREVIEW");
        LOGGER.info("=".repeat(70));
        LOGGER.info("📬 TO: {}", PRIMARY_RECIPIENT);
        LOGGER.info("📄 SUBJECT: {}", notification.subject());
        LOGGER.info("⚡ PRIORITY: {}", notification.priority());
        LOGGER.info("\n📝 MESSAGE BODY:");
        LOGGER.info(notification.message());
        LOGGER.info("=".repeat(70) + "\n");
        
        // Send the email
        LOGGER.info("🚀 Sending comprehensive notification email to: {}", PRIMARY_RECIPIENT);
        emailNotifier.send(notification);
        LOGGER.info("✅ Comprehensive notification sent successfully!");
        
        LOGGER.info("📧 ✨ CHECK YOUR MAILBOX at {} ✨", PRIMARY_RECIPIENT);
        LOGGER.info("🔍 This email shows how MULTIPLE PRODUCTS with different stock levels appear");
        LOGGER.info("📊 Notice how the notification handles various:");
        LOGGER.info("   • Product name lengths");
        LOGGER.info("   • Stock quantities (from 1 to 35 units)");
        LOGGER.info("   • Different product types");
        LOGGER.info("   • URL formatting for shop.amul.com links");
    }
    
    /**
     * Test email connection without sending emails.
     */
    @Test
    @DisplayName("🔗 Test Email Connection - No Emails Sent")
    void testEmailConnectionOnly() throws NotificationException {
        if (emailNotifier == null) {
            LOGGER.warn("⏭️ Skipping test - email notifier not configured (check environment variables)");
            return;
        }
        
        LOGGER.info("🔗 Testing email connection without sending emails");
        
        // Test connection
        boolean connectionSuccessful = emailNotifier.testConnection(PRIMARY_RECIPIENT);
        
        if (connectionSuccessful) {
            LOGGER.info("✅ Email connection test successful!");
            LOGGER.info("📧 Ready to send notifications to: {}", PRIMARY_RECIPIENT);
            LOGGER.info("🎯 To see actual email content, run the other test methods");
        } else {
            LOGGER.error("❌ Email connection test failed!");
            LOGGER.error("🔧 Check your SMTP credentials and configuration");
            LOGGER.error("💡 Ensure SMTP_USERNAME and SMTP_PASSWORD are set correctly");
        }
    }
    
    /**
     * Helper method to describe stock levels.
     */
    private String getStockLevelDescription(int quantity) {
        if (quantity == 1) return "CRITICAL - Last unit!";
        if (quantity <= 3) return "VERY LOW";
        if (quantity <= 10) return "LOW";
        if (quantity <= 20) return "MEDIUM";
        return "HIGH";
    }
} 