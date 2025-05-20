package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/products")
public class EntityControllerPrototype {

    private final Map<String, SummaryReport> reports = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
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

    @PostMapping("/scrape")
    public ResponseEntity<GenericResponse> scrapeProducts(@RequestBody @Valid ScrapeRequest request) {
        log.info("Received scrape request for user {}", request.getUsername());
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                SummaryReport report = performScrapingAndAnalysis(request);
                reports.put("latest", report);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Scraping and analysis completed successfully");
            } catch (Exception e) {
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
                log.error("Scraping failed: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new GenericResponse("success",
                "Scraping started. Call GET /api/products/report for results."));
    }

    @GetMapping("/report")
    public ResponseEntity<SummaryReport> getSummaryReport() {
        SummaryReport report = reports.get("latest");
        if (report == null) {
            log.warn("No summary report available");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary report available. Trigger scraping first.");
        }
        log.info("Returning summary report generated at {}", report.getGeneratedAt());
        return ResponseEntity.ok(report);
    }

    private SummaryReport performScrapingAndAnalysis(ScrapeRequest request) throws IOException, InterruptedException {
        log.info("Starting scraping process for user {}", request.getUsername());
        String inventoryUrl = "https://www.saucedemo.com/inventory.html";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(inventoryUrl))
                .GET()
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Failed to fetch inventory page, status code: {}", response.statusCode());
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
        log.info("Summary calculated: totalProducts={}, averagePrice={}, totalInventoryValue={}",
                totalProducts, averagePrice, totalInventoryValue);
        return report;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<GenericResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new GenericResponse("failure", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGenericException(Exception ex) {
        log.error("Unhandled error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GenericResponse("failure", "Internal server error"));
    }
}