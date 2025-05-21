package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, SummaryReport> latestSummary = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    private static final String ENTITY_NAME = "product";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

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
        logger.info("Login attempt for user: {}", request.getUsername());
        List<String> acceptedUsers = Arrays.asList(
                "standard_user","locked_out_user","problem_user",
                "performance_glitch_user","error_user","visual_user"
        );
        if (!acceptedUsers.contains(request.getUsername())
                || !"secret_sauce".equals(request.getPassword())) {
            logger.warn("Login failed for user: {}", request.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        String sessionToken = UUID.randomUUID().toString();
        sessionTokens.put(sessionToken, request.getUsername());
        logger.info("Login successful for user: {}, token: {}", request.getUsername(), sessionToken);
        return ResponseEntity.ok(new LoginResponse("success", sessionToken, "Login successful"));
    }

    @PostMapping("/products/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeProducts(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Analyze request with token: {}", request.getSessionToken());
        if (!sessionTokens.containsKey(request.getSessionToken())) {
            logger.warn("Invalid session token for analyze");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session token");
        }
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Async scraping job: {}", jobId);
                List<Product> products = scrapeInventoryPage(request.getSessionToken());
                SummaryReport summary = analyzeProductsData(products);
                latestSummary.put("latest", summary);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Completed job: {}", jobId);
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Error in job: {}", jobId, e);
            }
        });
        return ResponseEntity.ok(new AnalyzeResponse("success", "Data scraping started", Instant.now()));
    }

    @GetMapping("/products/summary")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        SummaryReport summary = latestSummary.get("latest");
        if (summary == null) {
            logger.warn("No summary available");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Trigger analysis first.");
        }
        logger.info("Returning summary report");
        return ResponseEntity.ok(summary);
    }

    private List<Product> scrapeInventoryPage(String sessionToken) throws Exception {
        logger.info("Scraping inventory page with token: {}", sessionToken);
        // TODO: Implement real HTTP client, session, HTML parsing
        List<Product> products = new ArrayList<>();
        products.add(new Product("Sauce Labs Backpack","carry all the things",29.99,10));
        products.add(new Product("Sauce Labs Bike Light","red light for bike",9.99,15));
        Thread.sleep(500);
        return products;
    }

    private SummaryReport analyzeProductsData(List<Product> products) {
        logger.info("Analyzing {} products", products.size());
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

    @GetMapping("/products")
    public CompletableFuture<List<ProductWithId>> getAllProducts() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<ProductWithId> products = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Product product = parseProductFromNode(node);
                        UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                        products.add(new ProductWithId(technicalId, product));
                    }
                    return products;
                });
    }

    @GetMapping("/products/{id}")
    public CompletableFuture<ProductWithId> getProductById(@PathVariable("id") UUID id) {
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id)
                .thenApply(node -> {
                    if (node == null || node.isNull()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id);
                    }
                    Product product = parseProductFromNode(node);
                    UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                    return new ProductWithId(technicalId, product);
                });
    }

    @PostMapping("/products")
    public CompletableFuture<UUID> addProduct(@RequestBody @Valid Product product) {
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, product);
    }

    @PostMapping("/products/batch")
    public CompletableFuture<List<UUID>> addProductsBatch(@RequestBody List<@Valid Product> products) {
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, products);
    }

    @PutMapping("/products/{id}")
    public CompletableFuture<UUID> updateProduct(@PathVariable("id") UUID id, @RequestBody @Valid Product product) {
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, product);
    }

    @DeleteMapping("/products/{id}")
    public CompletableFuture<UUID> deleteProduct(@PathVariable("id") UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    private Product parseProductFromNode(JsonNode node) {
        // Map fields manually to Product, ignoring technicalId
        Product product = new Product();
        if (node.has("itemName")) product.setItemName(node.get("itemName").asText());
        if (node.has("description")) product.setDescription(node.get("description").asText());
        if (node.has("price")) product.setPrice(node.get("price").asDouble());
        if (node.has("inventory")) product.setInventory(node.get("inventory").asInt());
        return product;
    }

    @Data
    @AllArgsConstructor
    static class ProductWithId {
        private UUID technicalId;
        private Product product;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Exception: {}", ex.getReason());
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }
}