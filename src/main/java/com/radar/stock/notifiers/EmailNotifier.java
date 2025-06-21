package com.radar.stock.notifiers;

import com.radar.stock.core.RetryUtility;
import com.radar.stock.models.Notification;
import com.radar.stock.RestockRadarConfiguration.EmailConfiguration;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Email notifier implementation using Jakarta Mail.
 * Supports SMTP authentication and configuration for various email providers.
 */
public class EmailNotifier implements Notifier {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotifier.class);
    
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final boolean enableTLS;
    private final boolean enableSSL;
    
    /**
     * Creates a new EmailNotifier with SMTP configuration.
     * 
     * @param smtpHost SMTP server hostname (e.g., "smtp.gmail.com")
     * @param smtpPort SMTP server port (e.g., 587 for TLS, 465 for SSL)
     * @param username SMTP username (usually the sender's email address)
     * @param password SMTP password or app-specific password
     * @param enableTLS Whether to enable STARTTLS
     * @param enableSSL Whether to enable SSL/TLS
     */
    public EmailNotifier(String smtpHost, int smtpPort, String username, String password, 
                        boolean enableTLS, boolean enableSSL) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.enableTLS = enableTLS;
        this.enableSSL = enableSSL;
        
        LOGGER.info("EmailNotifier configured for {}:{} with TLS={}, SSL={}", 
                   smtpHost, smtpPort, enableTLS, enableSSL);
    }
    
    /**
     * Creates a new EmailNotifier from Dropwizard EmailConfiguration.
     * Reads credentials from environment variables for security.
     * 
     * @param config The email configuration from Dropwizard config
     * @throws NotificationException if configuration is invalid or credentials are missing
     */
    public EmailNotifier(EmailConfiguration config) throws NotificationException {
        if (!config.isEnabled()) {
            throw new NotificationException("Email notifications are disabled in configuration");
        }
        
        if (!config.isValid()) {
            String missingCredentials = "";
            if (config.getUsername() == null) {
                missingCredentials += " " + EmailConfiguration.USERNAME_ENV;
            }
            if (config.getPassword() == null) {
                missingCredentials += " " + EmailConfiguration.PASSWORD_ENV;
            }
            
            throw new NotificationException(
                "Email configuration is invalid. Missing environment variables:" + missingCredentials +
                ". Please set these environment variables with your SMTP credentials.");
        }
        
        this.smtpHost = config.getHost();
        this.smtpPort = config.getPort();
        this.username = config.getUsername();
        this.password = config.getPassword();
        this.enableTLS = config.isEnableTLS();
        this.enableSSL = config.isEnableSSL();
        
        LOGGER.info("EmailNotifier configured from Dropwizard config: {}", config.getSummary());
    }
    
    /**
     * Creates a Gmail-optimized EmailNotifier with standard settings.
     * 
     * @param username Gmail address
     * @param password App-specific password or OAuth token
     * @return EmailNotifier configured for Gmail
     */
    public static EmailNotifier forGmail(String username, String password) {
        return new EmailNotifier("smtp.gmail.com", 587, username, password, true, false);
    }
    
    /**
     * Creates an Outlook/Hotmail-optimized EmailNotifier with standard settings.
     * 
     * @param username Outlook email address
     * @param password Account password or app-specific password
     * @return EmailNotifier configured for Outlook
     */
    public static EmailNotifier forOutlook(String username, String password) {
        return new EmailNotifier("smtp-mail.outlook.com", 587, username, password, true, false);
    }
    
    /**
     * Creates an EmailNotifier from Dropwizard configuration.
     * This is the recommended way to create an EmailNotifier in production.
     * 
     * @param config The email configuration
     * @return Configured EmailNotifier
     * @throws NotificationException if configuration is invalid
     */
    public static EmailNotifier fromConfiguration(EmailConfiguration config) throws NotificationException {
        return new EmailNotifier(config);
    }
    
    @Override
    public void send(Notification notification) throws NotificationException {
        if (!notification.type().equals(Notification.NotificationType.EMAIL)) {
            throw new NotificationException(
                "EmailNotifier can only send EMAIL notifications, received: " + notification.type());
        }
        
        RetryUtility.RetryConfig retryConfig = RetryUtility.RetryConfig.forEmailDelivery();
        LOGGER.info("Sending email to {} recipient(s) {} with retry config: {}", 
                   notification.recipients().size(), notification.recipients(), RetryUtility.getConfigSummary(retryConfig));
        
        try {
            RetryUtility.executeWithRetry(() -> {
                try {
                    sendEmailWithoutRetry(notification);
                } catch (NotificationException e) {
                    throw new RuntimeException(e);
                }
            }, retryConfig, "Email delivery to " + notification.recipients().size() + " recipient(s)");
            
            LOGGER.info("✅ Email notification sent successfully to {} recipient(s): {}", 
                       notification.recipients().size(), notification.recipients());
            
        } catch (Exception e) {
            // If the original cause was a NotificationException, preserve it
            Throwable cause = e.getCause();
            if (cause instanceof NotificationException) {
                throw (NotificationException) cause;
            }
            
            String errorMsg = String.format("Failed to send email to %d recipient(s) %s after retries: %s", 
                                          notification.recipients().size(), notification.recipients(), e.getMessage());
            LOGGER.error(errorMsg, e);
            throw new NotificationException(errorMsg, e);
        }
    }
    
    /**
     * Internal method to send an email without retry logic.
     * This is separated to allow clean retry logic without exception wrapping.
     * 
     * @param notification The notification to send
     * @throws NotificationException if email sending fails
     */
    private void sendEmailWithoutRetry(Notification notification) throws NotificationException {
        try {
            Session session = createSession();
            Message message = createMessage(session, notification);
            
            LOGGER.debug("Attempting to send email - Subject: {}", notification.subject());
            
            // Test connection before attempting to send
            Transport transport = session.getTransport("smtp");
            try {
                transport.connect(smtpHost, smtpPort, username, password);
                LOGGER.debug("SMTP connection established successfully");
                
                // Send the message
                transport.sendMessage(message, message.getAllRecipients());
                
            } finally {
                if (transport.isConnected()) {
                    transport.close();
                }
            }
            
        } catch (MessagingException e) {
            String errorMsg = String.format("SMTP error when sending to %d recipient(s) %s: %s", 
                                          notification.recipients().size(), notification.recipients(), e.getMessage());
            LOGGER.debug("MessagingException details: ", e);
            
            // Enhance error messages for common issues
            if (e.getMessage().contains("authentication") || e.getMessage().contains("username")) {
                errorMsg = "Authentication failed. Please check your email credentials.";
            } else if (e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                errorMsg = "Connection to email server failed. This may be temporary.";
            } else if (e.getMessage().contains("invalid") && e.getMessage().contains("address")) {
                errorMsg = String.format("Invalid email address(es) in recipient list: %s", notification.recipients());
            }
            
            throw new NotificationException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error sending email to %d recipient(s) %s: %s", 
                                          notification.recipients().size(), notification.recipients(), e.getMessage());
            throw new NotificationException(errorMsg, e);
        }
    }
    
    @Override
    public boolean testConnection(String testRecipient) throws NotificationException {
        try {
            Notification testNotification = Notification.createTestNotification(testRecipient);
            send(testNotification);
            LOGGER.info("Email connection test successful for recipient: {}", testRecipient);
            return true;
        } catch (NotificationException e) {
            LOGGER.warn("Email connection test failed for recipient: {}: {}", testRecipient, e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getNotifierName() {
        return String.format("EmailNotifier[%s:%d]", smtpHost, smtpPort);
    }
    
    @Override
    public Notification.NotificationType getSupportedType() {
        return Notification.NotificationType.EMAIL;
    }
    
    @Override
    public boolean isConfigured() {
        return smtpHost != null && !smtpHost.trim().isEmpty() &&
               smtpPort > 0 &&
               username != null && !username.trim().isEmpty() &&
               password != null && !password.trim().isEmpty();
    }
    
    /**
     * Creates a configured Jakarta Mail session.
     * 
     * @return Configured mail session
     */
    private Session createSession() {
        Properties props = new Properties();
        
        // Basic SMTP configuration
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        
        // TLS/SSL configuration
        if (enableTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        
        if (enableSSL) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        
        // Trust configuration for common providers
        props.put("mail.smtp.ssl.trust", smtpHost);
        
        // Connection timeouts
        props.put("mail.smtp.connectiontimeout", "10000"); // 10 seconds
        props.put("mail.smtp.timeout", "10000"); // 10 seconds
        
        // Create authenticator
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };
        
        return Session.getInstance(props, authenticator);
    }
    
    /**
     * Creates a Jakarta Mail message from a Notification.
     * 
     * @param session The mail session
     * @param notification The notification to convert
     * @return Configured MimeMessage
     * @throws MessagingException if message creation fails
     */
    private Message createMessage(Session session, Notification notification) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        
        // Set sender (from address)
        message.setFrom(new InternetAddress());
        
        // Set recipients (to addresses)
        if (notification.recipients().isEmpty()) {
            throw new MessagingException("No recipients specified for email notification");
        }
        
        // Create array of InternetAddress objects for all recipients
        InternetAddress[] recipientAddresses = new InternetAddress[notification.recipients().size()];
        for (int i = 0; i < notification.recipients().size(); i++) {
            recipientAddresses[i] = new InternetAddress(notification.recipients().get(i));
        }
        message.setRecipients(Message.RecipientType.TO, recipientAddresses);
        
        // Set subject
        message.setSubject(notification.subject());
        
        // Set message content (plain text for now)
        message.setText(notification.message());
        
        // Set sent timestamp
        message.setSentDate(java.sql.Timestamp.valueOf(notification.timestamp()));
        
        // Add headers for better email client compatibility
        message.setHeader("X-Mailer", "Amul Stock Radar v1.0");
        message.setHeader("X-Priority", getPriorityHeader(notification.priority()));
        
        return message;
    }
    
    /**
     * Converts notification priority to email priority header value.
     * 
     * @param priority The notification priority
     * @return Email priority header value (1=High, 3=Normal, 5=Low)
     */
    private String getPriorityHeader(Notification.NotificationPriority priority) {
        return switch (priority) {
            case URGENT, HIGH -> "1"; // High priority
            case NORMAL -> "3";       // Normal priority
            case LOW -> "5";          // Low priority
        };
    }
    
    /**
     * Gets the configuration summary for logging and debugging.
     * 
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        return String.format("EmailNotifier[host=%s, port=%d, user=%s, TLS=%s, SSL=%s, configured=%s]",
                           smtpHost, smtpPort, username, enableTLS, enableSSL, isConfigured());
    }
} 