```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, SummaryReport> latestSummary = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Session tokens cache (mocked)
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    private static final String SAUCEDEMO_LOGIN_URL = "https://www.saucedemo.com/";
    private static final String SAUCEDEMO_INVENTORY_URL = "https://www.saucedemo.com/inventory.html";

    /**
     * DTOs
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LoginResponse {
        private String status;
        private String sessionToken;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeRequest {
        private String sessionToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeResponse {
        private String status;
        private String message;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SummaryReport {
        private int totalProducts;
        private double averagePrice;
        private ProductPriceInfo highestPricedItem;
        private ProductPriceInfo lowestPricedItem;
        private double totalInventoryValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ProductPriceInfo {
        private String itemName;
        private double price;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    /**
     * 1. User Login - mock implementation that accepts known usernames and password "secret_sauce"
     */
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        List<String> acceptedUsers = Arrays.asList(
                "standard_user",
                "locked_out_user",
                "problem_user",
                "performance_glitch_user",
                "error_user",
                "visual_user"
        );

        if (!acceptedUsers.contains(request.getUsername()) || !"secret_sauce".equals(request.getPassword())) {
            log.warn("Login failed for user: {}", request.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Generate a simple session token (UUID)
        String sessionToken = UUID.randomUUID().toString();
        sessionTokens.put(sessionToken, request.getUsername());

        log.info("Login successful for user: {}, sessionToken: {}", request.getUsername(), sessionToken);
        return ResponseEntity.ok(new LoginResponse("success", sessionToken, "Login successful"));
    }

    /**
     * 2. Trigger scraping and analysis of SauceDemo inventory page.
     * Fire-and-forget async processing simulated.
     */
    @PostMapping("/products/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeProducts(@RequestBody AnalyzeRequest request) {
        log.info("Received analyze request with sessionToken: {}", request.getSessionToken());

        if (!sessionTokens.containsKey(request.getSessionToken())) {
            log.warn("Invalid session token supplied for analyze");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session token");
        }

        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));

        // Fire-and-forget analysis task
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting async scraping and analysis job: {}", jobId);
                List<Product> products = scrapeInventoryPage(request.getSessionToken());

                SummaryReport summary = analyzeProductsData(products);
                latestSummary.put("latest", summary);

                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Completed scraping and analysis job: {}", jobId);
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
                log.error("Error during scraping and analysis job: {}", jobId, e);
            }
        });

        return ResponseEntity.ok(new AnalyzeResponse("success", "Data scraping and analysis started", Instant.now()));
    }

    /**
     * 3. Retrieve latest summary report
     */
    @GetMapping("/products/summary")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        SummaryReport summary = latestSummary.get("latest");
        if (summary == null) {
            log.warn("Summary report requested but no data available yet");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Please trigger analysis first.");
        }
        log.info("Returning latest summary report");
        return ResponseEntity.ok(summary);
    }

    /**
     * Helper: Scrape inventory page with session token
     * 
     * TODO: Replace this mock implementation with real HTTP client + session handling + HTML parsing
     */
    private List<Product> scrapeInventoryPage(String sessionToken) throws Exception {
        log.info("Scraping inventory page with session token: {}", sessionToken);

        // TODO: Implement real login session handling, cookie management, and scraping logic
        // For prototype, mock product list returned

        List<Product> products = new ArrayList<>();
        products.add(new Product("Sauce Labs Backpack", "carry all the things with the sleek...", 29.99, 10));
        products.add(new Product("Sauce Labs Bike Light", "A red light for your bike", 9.99, 15));
        products.add(new Product("Sauce Labs Bolt T-Shirt", "Get your testing shirt", 15.99, 5));
        products.add(new Product("Sauce Labs Fleece Jacket", "Warm and cozy", 49.99, 2));
        products.add(new Product("Sauce Labs Onesie", "Baby wear", 7.99, 20));
        products.add(new Product("Test.allTheThings() T-Shirt (Red)", "Test with style", 29.99, 8));

        // Simulate delay for scraping
        Thread.sleep(1000);

        return products;
    }

    /**
     * Helper: Analyze products data to create summary
     */
    private SummaryReport analyzeProductsData(List<Product> products) {
        log.info("Analyzing {} products", products.size());

        int totalProducts = products.size();
        double totalPriceSum = 0;
        double totalInventoryValue = 0;

        ProductPriceInfo highest = null;
        ProductPriceInfo lowest = null;

        for (Product p : products) {
            totalPriceSum += p.getPrice();
            totalInventoryValue += p.getPrice() * p.getInventory();

            if (highest == null || p.getPrice() > highest.getPrice()) {
                highest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
            if (lowest == null || p.getPrice() < lowest.getPrice()) {
                lowest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
        }

        double averagePrice = totalProducts > 0 ? totalPriceSum / totalProducts : 0;

        return new SummaryReport(
                totalProducts,
                round(averagePrice),
                highest,
                lowest,
                round(totalInventoryValue)
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Product DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Product {
        private String itemName;
        private String description;
        private double price;
        private int inventory;
    }

    /**
     * Minimal error handling for ResponseStatusException
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: {}", ex.getReason());
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

}
```
