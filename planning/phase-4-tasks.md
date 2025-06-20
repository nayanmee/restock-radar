# Phase 4: Core Logic and State Management

This final phase brings all the components together to create the fully functional application.

---

### Task 1: Implement State Management Service 🔄 PENDING

*   **Description:** Create a service responsible for persisting the stock status between runs. This prevents us from sending duplicate notifications for a product that is already in stock. We'll use a simple JSON file for this.
*   **Deliverables:**
    *   New file `src/main/java/com/radar/stock/services/StateService.java`.
    *   Methods in `StateService` to `loadState(String path)` from a JSON file and `saveState(String path, Map<String, Product> state)`.
    *   The state will be stored as a map of `alias` to `Product`.

---

### Task 2: Implement Final `CheckStockCommand` Logic 🔄 PENDING

*   **Description:** This is the core orchestration task. We will update the command to use all the components we've built to implement the final business logic.
*   **Deliverables:**
    *   Modified `CheckStockCommand.java` to:
        1.  Load the last known stock state using the `StateService`.
        2.  Fetch the current stock levels using the `StockExtractor`.
        3.  Compare the new state with the old state.

---

### Task 3: Implement Notification Trigger Logic 🔄 PENDING

*   **Description:** Add the precise logic for when to send a notification. A notification should only be sent when a product's stock changes from `0` in the previous run to `> 0` in the current run.
*   **Deliverables:**
    *   An `if` condition inside the `CheckStockCommand`'s product loop that checks `lastStock.get(alias).stock() == 0 && currentStock.get(alias).stock() > 0`.
    *   If the condition is true, construct a `Notification` object with a user-friendly message and pass it to the `Notifier` service.

---

### Task 4: Final Integration and End-to-End Test 🔄 PENDING

*   **Description:** Ensure that the new state is saved at the end of every successful run and perform a full end-to-end test of the application.
*   **Deliverables:**
    *   A call to `stateService.saveState(...)` at the end of the `CheckStockCommand`'s `run` method.
    *   A complete test run where:
        1.  The `last-known-stock.json` starts empty or with a product at 0 stock.
        2.  The application is run, finds the product in stock, and sends an email.
        3.  The `last-known-stock.json` is updated with the new stock level.
        4.  A second run of the application does **not** send an email for the same product. 