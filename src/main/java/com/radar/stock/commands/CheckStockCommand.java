package com.radar.stock.commands;

import com.radar.stock.RestockRadarConfiguration;
import com.radar.stock.extractors.AmulApiStockExtractor;
import com.radar.stock.extractors.StockExtractionException;
import com.radar.stock.extractors.StockExtractor;
import com.radar.stock.models.Notification;
import com.radar.stock.models.Product;
import com.radar.stock.notifiers.EmailNotifier;
import com.radar.stock.notifiers.NotificationException;
import com.radar.stock.notifiers.Notifier;
import com.radar.stock.services.StateService;
import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Command to check stock levels for monitored products.
 * This serves as the main entry point for the stock-checking functionality.
 */
public class CheckStockCommand extends ConfiguredCommand<RestockRadarConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckStockCommand.class);
    
    // Configuration
    private static final String STATE_FILE_PATH = "last-known-stock.json";

    public CheckStockCommand() {
        super("check-stock", "Checks for Amul product stock changes and sends notifications.");
    }

    @Override
    protected void run(Bootstrap<RestockRadarConfiguration> bootstrap,
                       Namespace namespace,
                       RestockRadarConfiguration configuration) throws Exception {
        LOGGER.info("=== Amul Stock Radar - Smart Notification System ===");
        long startTime = System.currentTimeMillis();
        
        try {
            executeStockCheckWorkflow(configuration);
        } catch (Exception e) {
            LOGGER.error("❌ Fatal error during stock check execution: {}", e.getMessage(), e);
            LOGGER.error("Application run failed - please check logs and configuration");
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Stock check completed in {} ms", duration);
        }
    }
    
    /**
     * Executes the main stock checking workflow.
     * 
     * @param configuration The application configuration
     */
    private void executeStockCheckWorkflow(RestockRadarConfiguration configuration) {

        // Initialize services
        StockExtractor stockExtractor = new AmulApiStockExtractor();
        StateService stateService = new StateService();
        
        // Step 1: Load previous state
        LOGGER.info("Loading previous stock state from: {}", STATE_FILE_PATH);
        Map<String, Product> previousState = stateService.loadState(STATE_FILE_PATH);
        LOGGER.info("Loaded previous state for {} products", previousState.size());

        // Step 2: Fetch current stock data with enhanced error handling
        List<String> productsToWatch = configuration.getWatchedProducts();
        List<Product> currentProducts;
        try {
            if (configuration.isSelectiveMonitoring()) {
                LOGGER.info("📋 Selective monitoring enabled - watching {} specific products", productsToWatch.size());
                LOGGER.debug("Watched product aliases: {}", productsToWatch);
            } else {
                LOGGER.info("🔍 Monitoring all protein products (selective monitoring disabled)");
            }
            
            LOGGER.info("Fetching current stock data from {}...", stockExtractor.getExtractorName());
            currentProducts = stockExtractor.checkStock(productsToWatch);
            
            if (configuration.isSelectiveMonitoring()) {
                LOGGER.info("Successfully fetched data for {} watched products", currentProducts.size());
            } else {
                LOGGER.info("Successfully fetched {} protein products", currentProducts.size());
            }
            
            // Validate that we got meaningful data
            if (currentProducts.isEmpty()) {
                LOGGER.warn("⚠️ API returned empty product list - this may indicate an API issue");
                LOGGER.warn("Continuing with empty data, but no notifications will be sent");
            }
            
        } catch (StockExtractionException e) {
            LOGGER.error("❌ Failed to fetch stock data: {}", e.getMessage(), e);
            LOGGER.error("Application will exit as stock data is required for operation");
            
            // Log helpful debugging information
            LOGGER.info("Troubleshooting tips:");
            LOGGER.info("1. Check your internet connection");
            LOGGER.info("2. Verify the Amul API is accessible");
            LOGGER.info("3. Check if there are any firewall restrictions");
            
            return; // Exit if we can't get current data
        } catch (Exception e) {
            LOGGER.error("❌ Unexpected error during stock data fetch: {}", e.getMessage(), e);
            return;
        }

        // Step 3: Convert current products to state map for comparison
        Map<String, Product> currentState = stateService.productsToStateMap(currentProducts);
        LOGGER.info("Converted current data to state map with {} products", currentState.size());

        // Step 4: Compare states and find changes
        List<Product> newlyInStockProducts = stateService.findNewlyInStockProducts(previousState, currentState);
        List<Product> newlyOutOfStockProducts = stateService.findNewlyOutOfStockProducts(previousState, currentState);
        
        // Log state comparison summary
        String summary = stateService.getStateComparisonSummary(previousState, currentState);
        LOGGER.info(summary);

        // Step 5: Display current stock status
        displayStockStatus(currentProducts, configuration);

        // Step 6: Send notifications if enabled and configured
        sendNotificationsIfNeeded(configuration, newlyInStockProducts, newlyOutOfStockProducts);

        // Step 7: Save current state for next run with enhanced error handling
        LOGGER.info("Saving current state to: {}", STATE_FILE_PATH);
        try {
            boolean stateSaved = stateService.saveState(STATE_FILE_PATH, currentState);
            if (stateSaved) {
                LOGGER.info("✅ State saved successfully");
            } else {
                LOGGER.error("❌ Failed to save state - notifications may be duplicated on next run");
                LOGGER.warn("Troubleshooting tips for state save failure:");
                LOGGER.warn("1. Check file system permissions in current directory");
                LOGGER.warn("2. Ensure sufficient disk space is available");
                LOGGER.warn("3. Verify no other process is locking the file: {}", STATE_FILE_PATH);
            }
        } catch (Exception e) {
            LOGGER.error("❌ Unexpected error while saving state: {}", e.getMessage(), e);
            LOGGER.warn("State save failed - notifications may be duplicated on next run");
        }

        // Step 8: Summary
        logRunSummary(currentProducts, newlyInStockProducts, newlyOutOfStockProducts);
    }

    /**
     * Sends notifications for stock changes if email is properly configured.
     */
    private void sendNotificationsIfNeeded(RestockRadarConfiguration configuration,
                                          List<Product> newlyInStockProducts,
                                          List<Product> newlyOutOfStockProducts) {
        var emailConfig = configuration.getEmailConfiguration();

        if (!emailConfig.isEnabled()) {
            LOGGER.info("Email notifications are disabled. Skipping notifications.");
            return;
        }

        if (!emailConfig.isValid()) {
            LOGGER.warn("Email configuration is invalid. Skipping notifications.");
            LOGGER.warn("Please check your config.yml and ensure SMTP_USERNAME and SMTP_PASSWORD environment variables are set.");
            return;
        }

        try {
            Notifier emailNotifier = EmailNotifier.fromConfiguration(emailConfig);
            List<String> recipients = emailConfig.getRecipients();

            // Validate recipients
            if (recipients.isEmpty()) {
                LOGGER.warn("No recipients configured - skipping notifications");
                return;
            }

            // Send in-stock notifications (high priority)
            if (!newlyInStockProducts.isEmpty()) {
                LOGGER.info("🎉 Sending in-stock notification for {} products to {} recipient(s): {}", 
                           newlyInStockProducts.size(), recipients.size(), recipients);
                
                Notification inStockAlert = Notification.createStockAlert(recipients, newlyInStockProducts);
                emailNotifier.send(inStockAlert);
                
                LOGGER.info("✅ In-stock notification sent successfully to all recipients!");
            }

            // Send out-of-stock notifications (normal priority)
            if (!newlyOutOfStockProducts.isEmpty()) {
                LOGGER.info("📉 Sending out-of-stock notification for {} products to {} recipient(s): {}", 
                           newlyOutOfStockProducts.size(), recipients.size(), recipients);
                
                Notification outOfStockAlert = Notification.createOutOfStockAlert(recipients, newlyOutOfStockProducts);
                emailNotifier.send(outOfStockAlert);
                
                LOGGER.info("✅ Out-of-stock notification sent successfully to all recipients!");
            }

            // Log if no notifications were needed
            if (newlyInStockProducts.isEmpty() && newlyOutOfStockProducts.isEmpty()) {
                LOGGER.info("ℹ️ No stock changes detected - no notifications sent");
            }

        } catch (NotificationException e) {
            LOGGER.error("❌ Failed to send notifications: {}", e.getMessage(), e);
            LOGGER.warn("Stock changes were detected but notifications could not be sent");
            LOGGER.warn("Consider checking your email configuration and network connectivity");
            
            // Log which notifications failed to send
            if (!newlyInStockProducts.isEmpty()) {
                LOGGER.warn("Failed to send in-stock alerts for: {}", 
                           newlyInStockProducts.stream().map(Product::name).toList());
            }
            if (!newlyOutOfStockProducts.isEmpty()) {
                LOGGER.warn("Failed to send out-of-stock alerts for: {}", 
                           newlyOutOfStockProducts.stream().map(Product::name).toList());
            }
        } catch (Exception e) {
            LOGGER.error("❌ Unexpected error during notification handling: {}", e.getMessage(), e);
        }
    }

    /**
     * Displays the current stock status in a user-friendly format.
     */
    private void displayStockStatus(List<Product> products) {
        LOGGER.info("--- Current Stock Status ---");
        
        long inStockCount = products.stream().filter(Product::isInStock).count();
        long outOfStockCount = products.size() - inStockCount;

        // Display each product
        for (Product product : products) {
            System.out.println(product.toSummary());
        }

        // Summary statistics
        System.out.println("\n=== Current Stock Summary ===");
        System.out.printf("Total products monitored: %d\n", products.size());
        System.out.printf("Currently in stock: %d\n", inStockCount);
        System.out.printf("Currently out of stock: %d\n", outOfStockCount);
    }
    
    /**
     * Enhanced version that takes configuration to show monitoring mode.
     */
    private void displayStockStatus(List<Product> products, RestockRadarConfiguration configuration) {
        LOGGER.info("--- Current Stock Status ---");
        
        if (configuration.isSelectiveMonitoring()) {
            System.out.println("🎯 Selective Monitoring Mode - Watching specific products");
            System.out.printf("📝 Watched products: %s\n", String.join(", ", configuration.getWatchedProducts()));
        } else {
            System.out.println("🔍 Full Monitoring Mode - Watching all protein products");
        }
        
        long inStockCount = products.stream().filter(Product::isInStock).count();
        long outOfStockCount = products.size() - inStockCount;

        // Display each product
        for (Product product : products) {
            System.out.println(product.toSummary());
        }

        // Summary statistics
        System.out.println("\n=== Current Stock Summary ===");
        System.out.printf("Total products monitored: %d\n", products.size());
        System.out.printf("Currently in stock: %d\n", inStockCount);
        System.out.printf("Currently out of stock: %d\n", outOfStockCount);
    }

    /**
     * Logs a comprehensive summary of the application run.
     */
    private void logRunSummary(List<Product> allProducts, 
                              List<Product> newlyInStockProducts, 
                              List<Product> newlyOutOfStockProducts) {
        LOGGER.info("=== Run Summary ===");
        LOGGER.info("Total products monitored: {}", allProducts.size());
        LOGGER.info("Products newly in stock: {}", newlyInStockProducts.size());
        LOGGER.info("Products newly out of stock: {}", newlyOutOfStockProducts.size());
        
        long currentlyInStock = allProducts.stream().filter(Product::isInStock).count();
        LOGGER.info("Currently in stock: {}", currentlyInStock);
        
        // Log specific products that changed
        if (!newlyInStockProducts.isEmpty()) {
            LOGGER.info("🎉 Newly available: {}", 
                       newlyInStockProducts.stream().map(Product::name).toList());
        }
        
        if (!newlyOutOfStockProducts.isEmpty()) {
            LOGGER.info("📉 Recently sold out: {}", 
                       newlyOutOfStockProducts.stream().map(Product::name).toList());
        }
        
        LOGGER.info("=== Amul Stock Radar Run Complete ===");
    }
} 