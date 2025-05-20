```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-products")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapeRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryReport {
        private int totalProducts;
        private double averagePrice;
        private ProductItem highestPricedItem;
        private ProductItem lowestPricedItem;
        private double totalInventoryValue;
        private Instant generatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        private String name;
        private String description;
        private double price;
        private int inventory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenericResponse {
        private String status;
        private String message;
    }

    // Store current job status and report locally to keep business logic intact
    private volatile SummaryReport latestReport;
    private volatile JobStatus currentJobStatus;

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    /**
     * Workflow function applied to ProductItem entity before persistence.
     * This function takes a ProductItem and returns a ProductItem (possibly modified).
     * You can modify the entity state here or interact with other entities,
     * but must NOT add/update/delete entities of the same entityModel "productitem".
     */
    private Function<ProductItem, ProductItem> processproductitem = productItem -> {
        // Example: you could modify the productItem before saving, e.g., adjust inventory or price.
        // For now, just return the entity as is.
        // For example:
        // productItem.setInventory(productItem.getInventory() + 0); // no change, placeholder
        return productItem;
    };

    @PostMapping("/scrape")
    public ResponseEntity<GenericResponse> scrapeProducts(@RequestBody @Valid ScrapeRequest request) {
        logger.info("Received scrape request for user {}", request.getUsername());
        currentJobStatus = new JobStatus("processing", Instant.now());
        CompletableFuture.runAsync(() -> {
            try {
                SummaryReport report = performScrapingAndAnalysis(request);
                latestReport = report;
                currentJobStatus = new JobStatus("completed", Instant.now());
                logger.info("Scraping and analysis completed successfully");

                // Save products to external entityService
                List<ProductItem> products = List.of(
                        new ProductItem("Sauce Labs Backpack", "carry all the things", 29.99, 1),
                        new ProductItem("Sauce Labs Bike Light", "a light for your bike", 9.99, 1),
                        new ProductItem("Sauce Labs Bolt T-Shirt", "soft and comfortable", 15.99, 1),
                        new ProductItem("Sauce Labs Fleece Jacket", "warm fleece jacket", 49.99, 1)
                );

                // Use new addItems method with workflow function
                entityService.addItems(
                        "productitem",
                        ENTITY_VERSION,
                        products,
                        this::processproductitem
                ).join();

            } catch (Exception e) {
                currentJobStatus = new JobStatus("failed", Instant.now());
                logger.error("Scraping failed: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new GenericResponse("success",
                "Scraping started. Call GET /api/cyoda-products/report for results."));
    }

    @GetMapping("/report")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        if (latestReport == null) {
            logger.warn("No summary report available");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Trigger scraping first.");
        }
        logger.info("Returning summary report generated at {}", latestReport.getGeneratedAt());
        return ResponseEntity.ok(latestReport);
    }

    private SummaryReport performScrapingAndAnalysis(ScrapeRequest request) throws IOException, InterruptedException {
        logger.info("Starting scraping process for user {}", request.getUsername());
        String inventoryUrl = "https://www.saucedemo.com/inventory.html";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(inventoryUrl))
                .GET()
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.error("Failed to fetch inventory page, status code: {}", response.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch inventory page");
        }
        // TODO: Replace this mock parsing with real HTML parsing logic (e.g., Jsoup)
        ProductItem[] products = new ProductItem[]{
                new ProductItem("Sauce Labs Backpack", "carry all the things", 29.99, 1),
                new ProductItem("Sauce Labs Bike Light", "a light for your bike", 9.99, 1),
                new ProductItem("Sauce Labs Bolt T-Shirt", "soft and comfortable", 15.99, 1),
                new ProductItem("Sauce Labs Fleece Jacket", "warm fleece jacket", 49.99, 1)
        };
        int totalProducts = products.length;
        double totalInventoryValue = 0;
        ProductItem highest = products[0];
        ProductItem lowest = products[0];
        for (ProductItem p : products) {
            totalInventoryValue += p.getPrice() * p.getInventory();
            if (p.getPrice() > highest.getPrice()) highest = p;
            if (p.getPrice() < lowest.getPrice()) lowest = p;
        }
        double averagePrice = totalInventoryValue / totalProducts;
        SummaryReport report = new SummaryReport();
        report.setTotalProducts(totalProducts);
        report.setAveragePrice(round(averagePrice));
        report.setHighestPricedItem(highest);
        report.setLowestPricedItem(lowest);
        report.setTotalInventoryValue(round(totalInventoryValue));
        report.setGeneratedAt(Instant.now());
        logger.info("Summary calculated: totalProducts={}, averagePrice={}, totalInventoryValue={}",
                totalProducts, averagePrice, totalInventoryValue);
        return report;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<GenericResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new GenericResponse("failure", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GenericResponse("failure", "Internal server error"));
    }
}
```

---

### Explanation of the changes:

- Added a workflow function `processproductitem` with the required naming convention `process{entity_name}` (entity name is `"productitem"`).
- This function is a `Function<ProductItem, ProductItem>` which takes the entity, optionally modifies it, and returns it.
- Updated the call to `entityService.addItems` to include the workflow function as the last argument.
- The rest of the code remains untouched to preserve existing logic and behavior.

If you want to modify the product items in the workflow, you can do so in `processproductitem`. Currently, it returns the entity as-is.