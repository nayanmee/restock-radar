# Project: Amul Product Stock Notifier

This document outlines the plan and findings for creating a service to notify us when a specific Amul product is back in stock on the official Amul Shop website.

## 1. Goal

The primary goal is to build an automated service that:
1.  Periodically checks the stock status of specific Amul protein products.
2.  Sends an email notification when a desired product becomes available.
3.  Is free to run and easy to maintain.

## 2. Core Discovery: The API

We successfully found a "hidden" API used by `shop.amul.com` to load product data. This is more reliable and efficient than web scraping.

- **API Endpoint**: `https://shop.amul.com/api/1/entity/ms.products`
- **Method**: This endpoint returns a JSON object containing a list of all products in a specific category (in our case, "protein").
- **Key Data Points**: For each product, we can get the `name`, `alias` (used in the URL), `available` status, and `inventory_quantity`.

## 3. How to Fetch Product Data

We can get the data using a `curl` command. The server requires specific headers to treat the request as a legitimate browser API call.

### A. Fetch All Protein Products

This is the most reliable command. It fetches all products in the "protein" category.

```bash
curl 'https://shop.amul.com/api/1/entity/ms.products?fields%5Bname%5D=1&fields%5Bbrand%5D=1&fields%5Bcategories%5D=1&fields%5Bcollections%5D=1&fields%5Balias%5D=1&fields%5Bsku%5D=1&fields%5Bprice%5D=1&fields%5Bcompare_price%5D=1&fields%5Boriginal_price%5D=1&fields%5Bimages%5D=1&fields%5Bmetafields%5D=1&fields%5Bdiscounts%5D=1&fields%5Bcatalog_only%5D=1&fields%5Bis_catalog%5D=1&fields%5Bseller%5D=1&fields%5Bavailable%5D=1&fields%5Binventory_quantity%5D=1&fields%5Bnet_quantity%5D=1&fields%5Bnum_reviews%5D=1&fields%5Bavg_rating%5D=1&fields%5Binventory_low_stock_quantity%5D=1&fields%5Binventory_allow_out_of_stock%5D=1&fields%5Bdefault_variant%5D=1&fields%5Bvariants%5D=1&fields%5Blp_seller_ids%5D=1&filters%5B0%5D%5Bfield%5D=categories&filters%5B0%5D%5Bvalue%5D%5B0%5D=protein&filters%5B0%5D%5Boperator%5D=in&filters%5B0%5D%5Boriginal%5D=1&facets=true&facetgroup=default_category_facet&limit=32&total=1&start=0&cdc=1m&substore=66505ff0998183e1b1935c75' \
-H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Safari/605.1.15' \
-H 'Accept: application/json, text/plain, */*' \
-H 'Referer: https://shop.amul.com/' \
--compressed
```

### B. Filter for a Specific Product

To easily find a single product, we can pipe the `curl` command's output into the `jq` command-line tool. This requires finding the product's `alias` from the full JSON output first.

**Command Template:**
```bash
# Replace the alias with the one you want to find
curl '...' --compressed | jq '.data[] | select(.alias == "REPLACE_WITH_PRODUCT_ALIAS")'
```

**Example: Get "Amul High Protein Paneer, 400 g | Pack of 2"**
- Alias: `amul-high-protein-paneer-400-g-or-pack-of-2`
- Command:
  ```bash
  curl 'https://shop.amul.com/api/1/entity/ms.products?...' --compressed | jq '.data[] | select(.alias == "amul-high-protein-paneer-400-g-or-pack-of-2")'
  ```

## 4. Implementation Plan

**UPDATE**: We have decided to build this service using **Java** with the **Dropwizard** framework to create a robust and extensible application. The core logic will be designed with pluggable components.

Here is the step-by-step plan to build the notification script.

-   **Language**: Java (JDK 17 or newer).
-   **Framework**: Dropwizard. While we won't run it as a persistent server, Dropwizard provides excellent structure for configuration, commands, and packaging.
-   **Build Tool**: Maven.
-   **Core Architecture**: The application will be designed around interfaces to allow for future extensions (e.g., adding new websites to scrape or new notification channels).

## 5. Low-Level Design (LLD)

The application will be composed of three main pluggable components:

1.  **`StockExtractor`**: Responsible for fetching stock information.
    *   **Interface**: `StockExtractor` with a method like `List<Product> checkStock(List<String> productAliases)`.
    *   **Initial Implementation**: `AmulApiStockExtractor` which will contain the logic to call the `shop.amul.com` API we discovered.
    *   **Future Implementations**: `AmazonScraperExtractor`, `FlipkartApiExtractor`.

2.  **`Notifier`**: Responsible for sending notifications.
    *   **Interface**: `Notifier` with a method like `void send(Notification notification)`.
    *   **Initial Implementation**: `EmailNotifier` using the `Jakarta Mail` library to send emails via SMTP.
    *   **Future Implementations**: `SmsNotifier`, `SlackNotifier`, `PushbulletNotifier`.

3.  **State Management**: To avoid sending repeated notifications for a product that remains in stock, we need to track the last known status.
    *   **Initial Implementation**: A simple file-based store (e.g., `last-known-stock.json`) that saves the stock status of products from the previous run.

The core application logic will orchestrate these components. It will be a command-line application that is executed by the scheduler.

## 6. Deployment & Automation Plan

We will use GitHub Actions to run the script automatically and for free.

-   **Hosting**: The Python script will be hosted in a GitHub repository.
-   **Scheduling**: A GitHub Actions workflow file (e.g., `.github/workflows/main.yml`) will be created to run the script on a `cron` schedule (e.g., once every hour).
-   **Security**: To avoid exposing credentials, the email address and password will be stored as **Encrypted Secrets** in the GitHub repository settings (`Settings > Secrets and variables > Actions`). The script will access them as environment variables (`EMAIL_USER`, `EMAIL_PASS`).

### Example GitHub Actions Workflow for Java

```yaml
# .github/workflows/main.yml
name: Check Amul Stock (Java Service)
on:
  schedule:
    - cron: '0 * * * *' # Runs every hour
  workflow_dispatch: # Allows manual runs

jobs:
  check-stock:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn -B package --file pom.xml
      - name: Run Stock Checker
        env:
          EMAIL_USER: ${{ secrets.EMAIL_USER }}
          EMAIL_PASS: ${{ secrets.EMAIL_PASS }}
        run: java -jar target/your-project-name-1.0-SNAPSHOT.jar check-stock config.yml
``` 