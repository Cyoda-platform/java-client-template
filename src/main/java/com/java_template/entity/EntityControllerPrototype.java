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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@Validated
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, SummaryReport> latestSummary = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    /**
     * DTOs with validation
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
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
        @NotBlank
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Product {
        private String itemName;
        private String description;
        private double price;
        private int inventory;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        List<String> acceptedUsers = Arrays.asList(
            "standard_user","locked_out_user","problem_user",
            "performance_glitch_user","error_user","visual_user"
        );
        if (!acceptedUsers.contains(request.getUsername()) 
            || !"secret_sauce".equals(request.getPassword())) {
            log.warn("Login failed for user: {}", request.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        String sessionToken = UUID.randomUUID().toString();
        sessionTokens.put(sessionToken, request.getUsername());
        log.info("Login successful for user: {}, token: {}", request.getUsername(), sessionToken);
        return ResponseEntity.ok(new LoginResponse("success", sessionToken, "Login successful"));
    }

    @PostMapping("/products/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeProducts(@RequestBody @Valid AnalyzeRequest request) {
        log.info("Analyze request with token: {}", request.getSessionToken());
        if (!sessionTokens.containsKey(request.getSessionToken())) {
            log.warn("Invalid session token for analyze");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session token");
        }
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Async scraping job: {}", jobId);
                List<Product> products = scrapeInventoryPage(request.getSessionToken());
                SummaryReport summary = analyzeProductsData(products);
                latestSummary.put("latest", summary);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Completed job: {}", jobId);
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
                log.error("Error in job: {}", jobId, e);
            }
        });
        return ResponseEntity.ok(new AnalyzeResponse("success", "Data scraping started", Instant.now()));
    }

    @GetMapping("/products/summary")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        SummaryReport summary = latestSummary.get("latest");
        if (summary == null) {
            log.warn("No summary available");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Trigger analysis first.");
        }
        log.info("Returning summary report");
        return ResponseEntity.ok(summary);
    }

    private List<Product> scrapeInventoryPage(String sessionToken) throws Exception {
        log.info("Scraping inventory page with token: {}", sessionToken);
        // TODO: Implement real HTTP client, session, HTML parsing
        List<Product> products = new ArrayList<>();
        products.add(new Product("Sauce Labs Backpack","carry all the things",29.99,10));
        products.add(new Product("Sauce Labs Bike Light","red light for bike",9.99,15));
        Thread.sleep(500);
        return products;
    }

    private SummaryReport analyzeProductsData(List<Product> products) {
        log.info("Analyzing {} products", products.size());
        int total = products.size();
        double sumPrice = 0, totalInvValue = 0;
        ProductPriceInfo highest = null, lowest = null;
        for (Product p : products) {
            sumPrice += p.getPrice();
            totalInvValue += p.getPrice() * p.getInventory();
            if (highest == null || p.getPrice() > highest.getPrice()) {
                highest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
            if (lowest == null || p.getPrice() < lowest.getPrice()) {
                lowest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
        }
        double avg = total > 0 ? sumPrice / total : 0;
        return new SummaryReport(total, Math.round(avg*100)/100.0, highest, lowest, Math.round(totalInvValue*100)/100.0);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Exception: {}", ex.getReason());
        Map<String,Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }
}