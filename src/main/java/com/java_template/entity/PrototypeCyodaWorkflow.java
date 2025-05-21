package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    // Use volatile to ensure visibility across threads for latest summary and job status
    private volatile SummaryReport latestSummary;
    private volatile JobStatus latestJobStatus;

    private final Map<String, String> sessionTokens = Collections.synchronizedMap(new HashMap<>());

    private static final String ENTITY_NAME = "product";

    public CyodaEntityControllerPrototype(EntityService entityService) {
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
        // Pass workflow function to process asynchronously before persistence
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, product, this::processProduct);
    }

    @PostMapping("/products/batch")
    public CompletableFuture<List<UUID>> addProductsBatch(@RequestBody List<@Valid Product> products) {
        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Product product : products) {
            futures.add(entityService.addItem(ENTITY_NAME, ENTITY_VERSION, product, this::processProduct));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<UUID> ids = new ArrayList<>();
                    for (CompletableFuture<UUID> f : futures) {
                        ids.add(f.join());
                    }
                    return ids;
                });
    }

    @PutMapping("/products/{id}")
    public CompletableFuture<UUID> updateProduct(@PathVariable("id") UUID id, @RequestBody @Valid Product product) {
        // Update without workflow function to avoid recursion and complexity; could add workflow if needed carefully
        return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, product);
    }

    @DeleteMapping("/products/{id}")
    public CompletableFuture<UUID> deleteProduct(@PathVariable("id") UUID id) {
        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
    }

    /**
     * Workflow function to process a Product entity asynchronously before persistence.
     * It modifies entity state directly and triggers async jobs like scraping and analysis.
     * 
     * @param entity the entity data as ObjectNode (JSON tree)
     * @return CompletableFuture with the modified entity ObjectNode to persist
     */
    private CompletableFuture<ObjectNode> processProduct(ObjectNode entity) {
        logger.info("Workflow processProduct triggered for entity: {}", entity);

        // Add processed timestamp to entity
        entity.put("processedAt", Instant.now().toString());

        // Fire and forget async job to scrape inventory and update summary
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting async scraping and analysis job from workflow");
                List<Product> products = scrapeInventoryPage();

                SummaryReport summary = analyzeProductsData(products);

                latestSummary = summary;
                latestJobStatus = new JobStatus("completed", Instant.now());

                logger.info("Async scraping and analysis job completed from workflow");
            } catch (Exception e) {
                latestJobStatus = new JobStatus("failed", Instant.now());
                logger.error("Async job failed in workflow", e);
            }
        });

        return CompletableFuture.completedFuture(entity);
    }

    private List<Product> scrapeInventoryPage() {
        // Simulated fetching or scraping product inventory data
        List<Product> products = new ArrayList<>();
        products.add(new Product("Sauce Labs Backpack","carry all the things",29.99,10));
        products.add(new Product("Sauce Labs Bike Light","red light for bike",9.99,15));
        products.add(new Product("Sauce Labs Bolt T-Shirt","t-shirt with bolt logo",15.99,20));
        // Could extend to real HTTP scraping or DB fetching if needed
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
        return new SummaryReport(
                total,
                Math.round(avg * 100) / 100.0,
                highest,
                lowest,
                Math.round(totalInvValue * 100) / 100.0
        );
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