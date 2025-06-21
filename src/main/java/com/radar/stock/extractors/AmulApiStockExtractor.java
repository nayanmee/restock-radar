package com.radar.stock.extractors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radar.stock.core.RetryUtility;
import com.radar.stock.models.Product;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

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
    
    // API endpoint discovered through browser analysis
    private static final String API_BASE_URL = "https://shop.amul.com/api/1/entity/ms.products";
    
    // Complete URL with all required query parameters for protein products
    // Using the exact URL structure that works in curl
    private static final String PROTEIN_PRODUCTS_URL = 
        API_BASE_URL + "?" +
        "fields[name]=1&" +
        "fields[brand]=1&" +
        "fields[categories]=1&" +
        "fields[collections]=1&" +
        "fields[alias]=1&" +
        "fields[sku]=1&" +
        "fields[price]=1&" +
        "fields[compare_price]=1&" +
        "fields[original_price]=1&" +
        "fields[images]=1&" +
        "fields[metafields]=1&" +
        "fields[discounts]=1&" +
        "fields[catalog_only]=1&" +
        "fields[is_catalog]=1&" +
        "fields[seller]=1&" +
        "fields[available]=1&" +
        "fields[inventory_quantity]=1&" +
        "fields[net_quantity]=1&" +
        "fields[num_reviews]=1&" +
        "fields[avg_rating]=1&" +
        "fields[inventory_low_stock_quantity]=1&" +
        "fields[inventory_allow_out_of_stock]=1&" +
        "fields[default_variant]=1&" +
        "fields[variants]=1&" +
        "fields[lp_seller_ids]=1&" +
        "filters[0][field]=categories&" +
        "filters[0][value][0]=protein&" +
        "filters[0][operator]=in&" +
        "filters[0][original]=1&" +
        "facets=true&" +
        "facetgroup=default_category_facet&" +
        "limit=24&" +
        "total=1&" +
        "start=0&" +
        "cdc=1m&" +
        "substore=66505ff0998183e1b1935c75";
    
    /**
     * Creates the HTTP headers required by the Amul API.
     * These headers make the request appear as a legitimate browser call.
     * 
     * @return Map of header names to values
     */
    private Map<String, String> createHeaders() {
        return Map.of(
            "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Safari/605.1.15",
            "Accept", "application/json, text/plain, */*",
            "Referer", "https://shop.amul.com/"
        );
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
     * Internal method to fetch stock data from the API.
     * This is separated to allow clean retry logic without exception wrapping.
     * 
     * @param productAliases List of product aliases to filter by (or null for all)
     * @return List of products from the API
     * @throws StockExtractionException if the API call fails
     */
    private List<Product> fetchStockDataFromApi(List<String> productAliases) throws StockExtractionException {
        try {
            // Create HTTP client with SSL context that trusts all certificates
            // Note: In production, proper certificate validation should be used
            Client client = createTrustAllClient();
            
            LOGGER.debug("Making API request to: {}", PROTEIN_PRODUCTS_URL);
            
            // Make HTTP GET request with required headers
            // Build the URL manually to avoid double encoding issues
            String requestUrl = PROTEIN_PRODUCTS_URL;
            
            Response response = client.target(requestUrl)
                .request(MediaType.APPLICATION_JSON)
                .header("User-Agent", createHeaders().get("User-Agent"))
                .header("Accept", createHeaders().get("Accept"))
                .header("Referer", createHeaders().get("Referer"))
                .header("Accept-Language", "en-IN,en-GB;q=0.9,en;q=0.8")
                .get();
            
            // Enhanced error handling with specific HTTP status checks
            if (response.getStatus() >= 500) {
                // Server errors - retryable
                throw new StockExtractionException(
                    String.format("Server error (status %d): %s. This may be temporary.", 
                        response.getStatus(), response.getStatusInfo().getReasonPhrase())
                );
            } else if (response.getStatus() == 429) {
                // Rate limiting - retryable
                throw new StockExtractionException(
                    "Rate limit exceeded (status 429). Please try again later."
                );
            } else if (response.getStatus() >= 400) {
                // Client errors - usually not retryable
                throw new StockExtractionException(
                    String.format("Client error (status %d): %s", 
                        response.getStatus(), response.getStatusInfo().getReasonPhrase())
                );
            } else if (response.getStatus() != 200) {
                // Other non-success statuses
                throw new StockExtractionException(
                    String.format("Unexpected response status %d: %s", 
                        response.getStatus(), response.getStatusInfo().getReasonPhrase())
                );
            }
            
            // Parse JSON response
            String jsonResponse = response.readEntity(String.class);
            
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                throw new StockExtractionException("Received empty response from API");
            }
            
            LOGGER.debug("Received JSON response: {} characters", jsonResponse.length());
            LOGGER.debug("Raw JSON response: {}", jsonResponse);
            
            ObjectMapper objectMapper = new ObjectMapper();
            AmulApiResponse apiResponse;
            
            try {
                apiResponse = objectMapper.readValue(jsonResponse, AmulApiResponse.class);
            } catch (Exception e) {
                LOGGER.error("Failed to parse JSON response. Raw JSON: {}", jsonResponse);
                throw new StockExtractionException("Failed to parse JSON response from API", e);
            }
            
            if (apiResponse == null || apiResponse.data() == null) {
                LOGGER.error("API returned null or invalid data structure. Raw JSON: {}", jsonResponse);
                throw new StockExtractionException("API returned null or invalid data structure");
            }
            
            LOGGER.debug("Parsed API response: data={}, messages={}, paging={}", 
                apiResponse.data() != null ? apiResponse.data().size() : "null",
                apiResponse.messages() != null ? apiResponse.messages().size() : "null",
                apiResponse.paging() != null ? apiResponse.paging().toString() : "null");
            
            // Transform AmulProduct objects to our Product domain objects
            List<Product> allProducts = apiResponse.data().stream()
                .map(this::transformToProduct)
                .collect(Collectors.toList());
            
            LOGGER.debug("Successfully parsed {} products from API response", allProducts.size());
            
            // Filter by aliases if specified
            if (productAliases != null && !productAliases.isEmpty()) {
                List<Product> filteredProducts = allProducts.stream()
                    .filter(product -> productAliases.contains(product.alias()))
                    .collect(Collectors.toList());
                
                LOGGER.debug("Filtered to {} products matching specified aliases", filteredProducts.size());
                return filteredProducts;
            }
            
            return allProducts;
            
        } catch (StockExtractionException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions
            throw new StockExtractionException("Unexpected error during API call", e);
        }
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
    
    /**
     * Gets the complete URL for fetching protein products.
     * 
     * @return The full API URL with query parameters
     */
    public String getApiUrl() {
        return PROTEIN_PRODUCTS_URL;
    }
    
    /**
     * Gets the required HTTP headers for API requests.
     * 
     * @return Map of HTTP headers
     */
    public Map<String, String> getHeaders() {
        return createHeaders();
    }
    
    /**
     * Creates an HTTP client configured to trust all SSL certificates.
     * This is appropriate for development/testing but should use proper
     * certificate validation in production environments.
     * 
     * @return Configured Jersey Client
     * @throws Exception if SSL context creation fails
     */
    private Client createTrustAllClient() throws Exception {
        // Create a trust manager that accepts all certificates
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all client certificates
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all server certificates
                }
            }
        };
        
        // Create SSL context with the trust-all manager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // Create client with custom SSL context
        return ClientBuilder.newBuilder()
            .sslContext(sslContext)
            .hostnameVerifier((hostname, session) -> true) // Trust all hostnames
            .build();
    }
} 