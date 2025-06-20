# Phase 2: The Data Fetcher (API Extractor)

This phase focuses on implementing the logic to fetch live stock data from the Amul API.

---

### Task 1: Define Core Data Structures and Interface 🔄 PENDING

*   **Description:** Create the Java `record` classes for our data models (`Product`) and the `StockExtractor` interface. This establishes a clear contract for how the rest of the application will interact with any data-fetching mechanism.
*   **Deliverables:**
    *   New file `src/main/java/com/radar/stock/models/Product.java` defining a `Product` record.
    *   New file `src/main/java/com/radar/stock/extractors/StockExtractor.java` defining the `StockExtractor` interface.

---

### Task 2: Implement the Amul API Extractor 🔄 PENDING

*   **Description:** Create the concrete implementation of the `StockExtractor` that knows how to communicate with the `shop.amul.com` API.
*   **Deliverables:**
    *   New file `src/main/java/com/radar/stock/extractors/AmulApiStockExtractor.java`.
    *   The class will implement the `StockExtractor` interface.
    *   It will contain the logic to build the API URL and required headers.

---

### Task 3: Implement HTTP Client and JSON Parsing 🔄 PENDING

*   **Description:** Use Dropwizard's built-in `JerseyClient` and `Jackson` library to make the actual HTTP request and parse the JSON response into our Java objects.
*   **Deliverables:**
    *   An HTTP GET request implementation inside `AmulApiStockExtractor`.
    *   A new `AmulApiResponse` record/class to map the incoming JSON structure.
    *   Logic to deserialize the JSON string into `AmulApiResponse` and then transform it into a `List<Product>`.

---

### Task 4: Integrate and Test the Extractor 🔄 PENDING

*   **Description:** Update the `CheckStockCommand` to use the new `AmulApiStockExtractor`. We will call it and print the fetched product data to the console to verify that the entire data-fetching pipeline is working correctly.
*   **Deliverables:**
    *   Modified `CheckStockCommand.java` to instantiate and call the `AmulApiStockExtractor`.
    *   Successful run of the command, printing a list of products and their stock levels. 