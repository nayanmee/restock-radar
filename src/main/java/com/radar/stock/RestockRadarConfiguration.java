package com.radar.stock;

import io.dropwizard.core.Configuration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import java.util.List;
import java.util.ArrayList;

public class RestockRadarConfiguration extends Configuration {
    
    @Valid
    @NotNull
    @JsonProperty("email")
    private EmailConfiguration emailConfiguration = new EmailConfiguration();
    
    @JsonProperty("watchedProducts")
    private List<String> watchedProducts = new ArrayList<>();
    
    public EmailConfiguration getEmailConfiguration() {
        return emailConfiguration;
    }
    
    public void setEmailConfiguration(EmailConfiguration emailConfiguration) {
        this.emailConfiguration = emailConfiguration;
    }
    
    /**
     * Gets the list of specific product aliases to monitor.
     * If empty, all products will be monitored.
     * 
     * @return List of product aliases to watch, or empty list for all products
     */
    public List<String> getWatchedProducts() {
        return watchedProducts;
    }
    
    /**
     * Sets the list of specific product aliases to monitor.
     * 
     * @param watchedProducts List of product aliases to watch
     */
    public void setWatchedProducts(List<String> watchedProducts) {
        this.watchedProducts = watchedProducts != null ? watchedProducts : new ArrayList<>();
    }
    
    /**
     * Checks if specific products are being watched (selective monitoring).
     * 
     * @return true if watching specific products, false if monitoring all products
     */
    @JsonIgnore
    public boolean isSelectiveMonitoring() {
        return watchedProducts != null && !watchedProducts.isEmpty();
    }
    
    /**
     * Email configuration for SMTP settings and recipients.
     * Credentials should be provided via environment variables for security.
     */
    public static class EmailConfiguration {
        
        @NotEmpty
        @JsonProperty
        private String host = "smtp.gmail.com";
        
        @Min(1)
        @Max(65535)
        @JsonProperty
        private int port = 587;
        
        @JsonProperty
        private boolean enableTLS = true;
        
        @JsonProperty
        private boolean enableSSL = false;
        
        @JsonProperty
        private List<@NotEmpty @Email String> recipients = new ArrayList<>();
        
        @JsonProperty
        private String senderName = "Amul Stock Radar";
        
        @JsonProperty
        private boolean enabled = true;
        
        // Environment variable names for credentials
        public static final String USERNAME_ENV = "SMTP_USERNAME";
        public static final String PASSWORD_ENV = "SMTP_PASSWORD";
        
        // Getters and setters
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public boolean isEnableTLS() {
            return enableTLS;
        }
        
        public void setEnableTLS(boolean enableTLS) {
            this.enableTLS = enableTLS;
        }
        
        public boolean isEnableSSL() {
            return enableSSL;
        }
        
        public void setEnableSSL(boolean enableSSL) {
            this.enableSSL = enableSSL;
        }
        
        public List<String> getRecipients() {
            return recipients;
        }
        
        public void setRecipients(List<String> recipients) {
            this.recipients = recipients != null ? recipients : new ArrayList<>();
        }
        
        /**
         * Gets the first recipient (for backward compatibility).
         * 
         * @return First recipient or null if no recipients configured
         */
        @JsonIgnore
        public String getRecipient() {
            return recipients.isEmpty() ? null : recipients.get(0);
        }
        
        /**
         * Sets a single recipient (for backward compatibility).
         * 
         * @param recipient The recipient email address
         */
        public void setRecipient(String recipient) {
            this.recipients = recipient != null ? List.of(recipient) : new ArrayList<>();
        }
        
        public String getSenderName() {
            return senderName;
        }
        
        public void setSenderName(String senderName) {
            this.senderName = senderName;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /**
         * Gets the SMTP username from environment variable.
         * 
         * @return SMTP username or null if not set
         */
        @JsonIgnore
        public String getUsername() {
            return System.getenv(USERNAME_ENV);
        }
        
        /**
         * Gets the SMTP password from environment variable.
         * 
         * @return SMTP password or null if not set
         */
        @JsonIgnore
        public String getPassword() {
            return System.getenv(PASSWORD_ENV);
        }
        
        /**
         * Checks if email configuration is complete and valid.
         * 
         * @return true if all required settings and credentials are available
         */
        @JsonIgnore
        public boolean isValid() {
            return enabled && 
                   host != null && !host.trim().isEmpty() &&
                   port > 0 &&
                   recipients != null && !recipients.isEmpty() &&
                   recipients.stream().allMatch(email -> email != null && !email.trim().isEmpty()) &&
                   getUsername() != null && !getUsername().trim().isEmpty() &&
                   getPassword() != null && !getPassword().trim().isEmpty();
        }
        
        /**
         * Gets a configuration summary for logging (without credentials).
         * 
         * @return Configuration summary string
         */
        @JsonIgnore
        public String getSummary() {
            return String.format("EmailConfiguration[host=%s, port=%d, TLS=%s, SSL=%s, recipients=%d, enabled=%s, credentialsSet=%s]",
                               host, port, enableTLS, enableSSL, recipients.size(), enabled, 
                               (getUsername() != null && getPassword() != null));
        }
    }
}
