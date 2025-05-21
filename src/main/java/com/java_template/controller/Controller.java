package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@Validated
@RequestMapping("/api/cyoda-entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Use volatile to ensure visibility across threads for latest summary and job status
    private volatile SummaryReport latestSummary;
    private volatile JobStatus latestJobStatus;

    private final Map<String, String> sessionTokens = Collections.synchronizedMap(new HashMap<>());

    private static final String ENTITY_NAME = "product";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class LoginResponse {
        private String status;
        private String sessionToken;
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class SummaryReport {
        private int totalProducts;
        private double averagePrice;
        private ProductPriceInfo highestPricedItem;
        private ProductPriceInfo lowestPricedItem;
        private double totalInventoryValue;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class ProductPriceInfo {
        private String itemName;
        private double price;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
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

    @GetMapping("/products/summary")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        if (latestSummary == null) {
            logger.warn("No summary available");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Add products first.");
        }
        logger.info("Returning latest summary report");
        return ResponseEntity.ok(latestSummary);
    }

    @GetMapping("/products/job-status")
    public ResponseEntity<JobStatus> getJobStatus() {
        if (latestJobStatus == null) {
            return ResponseEntity.ok(new JobStatus("no job", null));
        }
        return ResponseEntity.ok(latestJobStatus);
    }

    @GetMapping("/products")
    public CompletableFuture<List<ProductWithId>> getAllProducts() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<ProductWithId> products = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Product product = parseProductFromNode(node);
                        UUID technicalId = null;
                        try {
                            technicalId = UUID.fromString(node.get("technicalId").asText());
                        } catch (Exception e) {
                            logger.warn("Invalid technicalId format in stored entity: {}", node.get("technicalId"));
                        }
                        if (technicalId != null) {
                            products.add(new ProductWithId(technicalId, product));
                        }
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
        // Pass to addItem without workflow argument
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, product);
    }

    @PostMapping("/products/batch")
    public CompletableFuture<List<UUID>> addProductsBatch(@RequestBody List<@Valid Product> products) {
        // Batch add without workflow argument
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

    private List<Product> scrapeInventoryPage() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("Sauce Labs Backpack","carry all the things",29.99,10));
        products.add(new Product("Sauce Labs Bike Light","red light for bike",9.99,15));
        products.add(new Product("Sauce Labs Bolt T-Shirt","t-shirt with bolt logo",15.99,20));
        return products;
    }

    private SummaryReport analyzeProductsData(List<Product> products) {
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
        return new SummaryReport(total, Math.round(avg * 100) / 100.0, highest, lowest, Math.round(totalInvValue * 100) / 100.0);
    }

    private Product parseProductFromNode(JsonNode node) {
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal server error");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}