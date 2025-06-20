# Phase 3: The Notification System

This phase covers the implementation of the email notification component.

---

### Task 1: Define Notifier Interface and Data Model 🔄 PENDING

*   **Description:** Create the `Notifier` interface and the `Notification` record class. This provides a standard contract for sending any type of notification.
*   **Deliverables:**
    *   New file `src/main/java/com/radar/stock/notifiers/Notifier.java` defining the `Notifier` interface.
    *   New file `src/main/java/com/radar/stock/models/Notification.java` defining a `Notification` record.

---

### Task 2: Implement the Email Notifier 🔄 PENDING

*   **Description:** Create the `EmailNotifier` class that implements the `Notifier` interface and contains the logic for sending emails using the Jakarta Mail library.
*   **Deliverables:**
    *   New file `src/main/java/com/radar/stock/notifiers/EmailNotifier.java`.
    *   The class will implement the `Notifier` interface.
    *   It will include logic to connect to an SMTP server (e.g., Gmail) using properties and an authenticator.

---

### Task 3: Configure the Notifier 🔄 PENDING

*   **Description:** Integrate the email settings into the Dropwizard configuration files, allowing us to easily change settings like the recipient email without recompiling the code.
*   **Deliverables:**
    *   New `EmailConfiguration` class inside `RestockRadarConfiguration.java` to hold SMTP settings.
    *   Updated `config.yml` to include an `email` section with `host`, `port`, and `recipient`.
    *   The `EmailNotifier` will be updated to read credentials from environment variables.

---

### Task 4: Integrate and Test the Notifier 🔄 PENDING

*   **Description:** Temporarily modify the `CheckStockCommand` to instantiate and call the `EmailNotifier` on every run. This will allow us to test the email functionality independently before adding the final logic.
*   **Deliverables:**
    *   Modified `CheckStockCommand.java` to create and use an `EmailNotifier`.
    *   Successful run of the command that sends a test email to the configured recipient. 