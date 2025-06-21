package com.radar.stock.extractors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radar.stock.core.RetryUtility;
import com.radar.stock.models.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * StockExtractor implementation for the Amul Shop API.
 * 
 * This extractor communicates with the hidden API used by shop.amul.com
 * to fetch real-time product stock information for protein products.
 * 
 * Includes retry logic with exponential backoff for handling network issues
 * and temporary API failures.
 */
public class AmulApiStockExtractor implements StockExtractor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AmulApiStockExtractor.class);
    
    // Base URL for the API, the rest will be constructed dynamically.
    private static final String API_BASE_URL = "https://shop.amul.com/api/1/entity/ms.products";
    // URL of the page we scrape for dynamic tokens.
    private static final String BROWSE_PAGE_URL = "https://shop.amul.com/en/browse/protein";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmulApiStockExtractor() {
        this.httpClient = createTrustAllHttpClient();
    }
    
    /**
     * Creates the HTTP headers required by the Amul API.
     * These headers make the request appear as a legitimate browser call,
     * which is crucial for avoiding bot detection on platforms like GitHub Actions.
     * 
     * @param sessionToken A dynamic session token, or null if not yet fetched.
     * @return Array of header names to values
     */
    private String[] createHeaders(String sessionToken) {
        // This list is based on a real browser request to improve success rate.
        String[] baseHeaders = new String[]{
            "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept", "application/json, text/plain, */*",
            "Accept-Language", "en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer", "https://shop.amul.com/en/browse/protein",
            "base_url", "https://shop.amul.com/en/browse/protein",
            "frontend", "1",
            "sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile", "?0",
            "sec-ch-ua-platform", "\"macOS\"",
            "Sec-Fetch-Dest", "empty",
            "Sec-Fetch-Mode", "cors",
            "Sec-Fetch-Site", "same-origin",
            "tid", "1750505532027:941:b5b2cd66cd8d0d0155fa012df1d4998ca69612508d99f7959ec3b472b7a7e475"
        };

        if (sessionToken != null) {
            // Add the dynamic token header if it exists
            String[] withToken = new String[baseHeaders.length + 2];
            System.arraycopy(baseHeaders, 0, withToken, 0, baseHeaders.length);
            withToken[baseHeaders.length] = "token";
            withToken[baseHeaders.length + 1] = sessionToken;
            return withToken;
        }

        return baseHeaders;
    }
    
    @Override
    public List<Product> checkStock(List<String> productAliases) throws StockExtractionException {
        RetryUtility.RetryConfig retryConfig = RetryUtility.RetryConfig.forApiCalls();
        LOGGER.info("Fetching stock data from Amul API with retry config: {}", 
                   RetryUtility.getConfigSummary(retryConfig));
        
        try {
            return RetryUtility.executeWithRetry(() -> {
                try {
                    return fetchStockDataFromApi(productAliases);
                } catch (Exception e) {
                    // Convert all exceptions to RuntimeException for the retry mechanism
                    throw new RuntimeException(e);
                }
            }, retryConfig, "Amul API stock fetch");
            
        } catch (Exception e) {
            // If the original cause was a StockExtractionException, preserve it
            Throwable cause = e.getCause();
            if (cause instanceof StockExtractionException) {
                throw (StockExtractionException) cause;
            }
            throw new StockExtractionException("Failed to fetch stock data after retries", e);
        }
    }
    
    /**
     * Internal method to fetch stock data from the API using Java's built-in HttpClient.
     * This is separated to allow clean retry logic without exception wrapping.
     * 
     * @param productAliases List of product aliases to filter by (or null for all)
     * @return List of products from the API
     * @throws StockExtractionException if the API call fails
     */
    private List<Product> fetchStockDataFromApi(List<String> productAliases) throws StockExtractionException {
        try {
            // --- DYNAMIC PARAMETER LOGIC ---
            String sessionToken = fetchDynamicSessionToken().orElseThrow(() -> new StockExtractionException("Could not find dynamic session token"));
            String substoreId = "66505ff0998183e1b1935c75"; // This seems to be static, but we fetch a session token.

            String apiUrl = buildApiUrl(substoreId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .headers(createHeaders(sessionToken)) // Pass the dynamic token here
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            LOGGER.debug("Making API request to: {}", apiUrl);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            if (statusCode >= 500) {
                throw new StockExtractionException(String.format("Server error (status %d). This may be temporary.", statusCode));
            } else if (statusCode == 429) {
                throw new StockExtractionException("Rate limit exceeded (status 429). Please try again later.");
            } else if (statusCode >= 400) {
                throw new StockExtractionException(String.format("Client error (status %d)", statusCode));
            } else if (statusCode != 200) {
                throw new StockExtractionException(String.format("Unexpected response status %d", statusCode));
            }

            String jsonResponse = response.body();
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                throw new StockExtractionException("Received empty response from API");
            }
            
            LOGGER.debug("Received JSON response: {} characters", jsonResponse.length());
            
            AmulApiResponse apiResponse;
            try {
                apiResponse = objectMapper.readValue(jsonResponse, AmulApiResponse.class);
            } catch (Exception e) {
                String errorDetails = String.format(
                    "Failed to parse JSON response from API. Response was %d chars. Body: %s",
                    jsonResponse.length(), jsonResponse);
                throw new StockExtractionException(errorDetails, e);
            }
            
            if (apiResponse == null || apiResponse.data() == null) {
                throw new StockExtractionException("API returned null or invalid data structure");
            }
            
            List<Product> allProducts = apiResponse.data().stream()
                .map(this::transformToProduct)
                .collect(Collectors.toList());
            
            LOGGER.debug("Successfully parsed {} products from API response", allProducts.size());
            
            if (productAliases != null && !productAliases.isEmpty()) {
                return allProducts.stream()
                    .filter(product -> productAliases.contains(product.alias()))
                    .collect(Collectors.toList());
            }
            
            return allProducts;
            
        } catch (Exception e) {
            if (e instanceof StockExtractionException) {
                throw (StockExtractionException) e;
            }
            throw new StockExtractionException("Unexpected error during API call", e);
        }
    }
    
    /**
     * Fetches the Amul browse page and parses it to find the dynamic 'token' for the session.
     * This is crucial for making the API requests appear authentic.
     *
     * @return An Optional containing the session token if found, otherwise empty.
     * @throws StockExtractionException if the page fetch fails.
     */
    private Optional<String> fetchDynamicSessionToken() throws StockExtractionException {
        try {
            LOGGER.info("Fetching dynamic session token from browse page...");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BROWSE_PAGE_URL))
                .headers(createHeaders(null)) // Don't need a token to get the token
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String htmlBody = response.body();

            // The token is in a script tag: <script>token = "some_value"</script>
            Pattern pattern = Pattern.compile("token\\s*=\\s*\"([a-f0-9]{24})\"");
            Matcher matcher = pattern.matcher(htmlBody);

            if (matcher.find()) {
                String sessionToken = matcher.group(1);
                LOGGER.info("Successfully extracted dynamic session token: {}", sessionToken);
                return Optional.of(sessionToken);
            } else {
                LOGGER.warn("Could not find session token in the page body. The API call will likely fail.");
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt(); // Preserve interrupted status
            throw new StockExtractionException("Failed to fetch or parse the browse page for dynamic tokens", e);
        }
    }

    /**
     * Constructs the full API URL with the dynamically fetched substore ID.
     *
     * @param substoreId The dynamic substore ID.
     * @return The complete URL for fetching protein products.
     */
    private String buildApiUrl(String substoreId) {
        return API_BASE_URL + "?" +
            "fields%5Bname%5D=1&" +
            "fields%5Bbrand%5D=1&" +
            "fields%5Bcategories%5D=1&" +
            "fields%5Bcollections%5D=1&" +
            "fields%5Balias%5D=1&" +
            "fields%5Bsku%5D=1&" +
            "fields%5Bprice%5D=1&" +
            "fields%5Bcompare_price%5D=1&" +
            "fields%5Boriginal_price%5D=1&" +
            "fields%5Bimages%5D=1&" +
            "fields%5Bmetafields%5D=1&" +
            "fields%5Bdiscounts%5D=1&" +
            "fields%5Bcatalog_only%5D=1&" +
            "fields%5Bis_catalog%5D=1&" +
            "fields%5Bseller%5D=1&" +
            "fields%5Bavailable%5D=1&" +
            "fields%5Binventory_quantity%5D=1&" +
            "fields%5Bnet_quantity%5D=1&" +
            "fields%5Bnum_reviews%5D=1&" +
            "fields%5Bavg_rating%5D=1&" +
            "fields%5Binventory_low_stock_quantity%5D=1&" +
            "fields%5Binventory_allow_out_of_stock%5D=1&" +
            "fields%5Bdefault_variant%5D=1&" +
            "fields%5Bvariants%5D=1&" +
            "fields%5Blp_seller_ids%5D=1&" +
            "filters%5B0%5D%5Bfield%5D=categories&" +
            "filters%5B0%5D%5Bvalue%5D%5B0%5D=protein&" +
            "filters%5B0%5D%5Boperator%5D=in&" +
            "filters%5B0%5D%5Boriginal%5D=1&" +
            "facets=true&" +
            "facetgroup=default_category_facet&" +
            "limit=32&" +
            "total=1&" +
            "start=0&" +
            "cdc=1m&" +
            "substore=" + substoreId;
    }
    
    /**
     * Transforms an AmulProduct from the API response to our domain Product record.
     * 
     * @param amulProduct The product from the API response
     * @return A Product record with the relevant data
     */
    private Product transformToProduct(AmulApiResponse.AmulProduct amulProduct) {
        return new Product(
            amulProduct.name(),
            amulProduct.alias(),
            amulProduct.available() == 1,  // Convert 0/1 to boolean
            amulProduct.inventoryQuantity()
        );
    }
    
    @Override
    public String getExtractorName() {
        return "Amul Shop API Extractor";
    }
    
    public String getApiUrl() {
        // This is now less relevant as the URL is built dynamically.
        return "See buildApiUrl()";
    }
    
    /**
     * Gets the required HTTP headers for API requests.
     * 
     * @return Map of HTTP headers
     */
    public Map<String, String> getHeaders() {
        // This method is no longer used by the core logic but can be kept for debugging/info
        return Map.of();
    }
    
    private HttpClient createTrustAllHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            return HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create trust-all HTTP client, falling back to default", e);
            return HttpClient.newHttpClient();
        }
    }
} 