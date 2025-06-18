```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api/reports/inventory")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage for reports: reportId -> ReportData
    private final Map<String, InventoryReport> reports = new ConcurrentHashMap<>();

    // Simulated job status map if needed later (optional)
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    // Base URL of external SwaggerHub API for inventory search
    // TODO: Replace with actual external API endpoint if different or include authentication if needed
    private static final String EXTERNAL_API_URL = "https://virtserver.swaggerhub.com/CGIANNAROS/Test/1.0.0/developers/searchInventory";

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    /**
     * POST: Generate report by fetching inventory data from external API,
     * perform calculations and store report for retrieval.
     */
    @PostMapping
    public ResponseEntity<GenerateReportResponse> generateReport(@RequestBody GenerateReportRequest request) {
        logger.info("Received request to generate inventory report with filters: {}", request.getFilters());

        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Store job status as processing (optional utility for async tracking)
        entityJobs.put(reportId, new JobStatus("processing", requestedAt));

        // Fire and forget report generation async task
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting report generation for reportId={}", reportId);
                JsonNode inventoryData = callExternalInventoryApi(request.getFilters());
                InventoryReport report = calculateReport(inventoryData);
                report.setReportId(reportId);
                report.setGeneratedAt(Instant.now());
                reports.put(reportId, report);
                entityJobs.put(reportId, new JobStatus("completed", Instant.now()));
                logger.info("Report generation completed for reportId={}", reportId);
            } catch (Exception e) {
                logger.error("Error generating report for reportId={}: {}", reportId, e.getMessage(), e);
                entityJobs.put(reportId, new JobStatus("failed", Instant.now()));
            }
        });

        return ResponseEntity.ok(new GenerateReportResponse(reportId, "SUCCESS", "Report generation started"));
    }

    /**
     * GET: Retrieve generated report by reportId.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable String reportId) {
        logger.info("Retrieving report for reportId={}", reportId);
        InventoryReport report = reports.get(reportId);
        if (report == null) {
            logger.error("Report not found for reportId={}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    /**
     * Calls the external SwaggerHub API to retrieve inventory data.
     * Uses filters if provided.
     * Returns raw JsonNode from response body.
     */
    private JsonNode callExternalInventoryApi(Filters filters) {
        try {
            String url = EXTERNAL_API_URL;
            // TODO: If the external API supports filters via query params or POST body, implement here.
            // For prototype, assuming GET without filters.

            logger.info("Calling external API: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error calling external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch inventory data from external API");
        }
    }

    /**
     * Calculates report metrics from the raw inventory JSON data.
     * This prototype assumes inventory data is a JSON array of items,
     * each item containing fields like "price" (number).
     *
     * TODO: Adjust parsing according to real external API response structure.
     */
    private InventoryReport calculateReport(JsonNode inventoryData) {
        if (!inventoryData.isArray()) {
            logger.error("Expected inventory data to be an array");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid inventory data format");
        }

        int totalItems = 0;
        double totalValue = 0.0;

        for (JsonNode item : inventoryData) {
            totalItems++;
            double price = 0.0;
            if (item.has("price") && item.get("price").isNumber()) {
                price = item.get("price").asDouble();
            }
            totalValue += price;
        }

        double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;

        InventoryReport report = new InventoryReport();
        report.setTotalItems(totalItems);
        report.setTotalValue(totalValue);
        report.setAveragePrice(averagePrice);
        // otherStatistics can be extended here if needed

        logger.info("Calculated report: totalItems={}, averagePrice={}, totalValue={}",
                totalItems, averagePrice, totalValue);

        return report;
    }


    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }


    // --- DTOs and helper classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateReportRequest {
        private Filters filters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filters {
        private String category;
        private DateRange dateRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private String from; // YYYY-MM-DD
        private String to;   // YYYY-MM-DD
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateReportResponse {
        private String reportId;
        private String status; // SUCCESS | FAILURE
        private String message;
    }

    @Data
    @NoArgsConstructor
    public static class InventoryReport {
        private String reportId;
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private Map<String, Object> otherStatistics; // extensible for category stats etc.
        private Instant generatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status; // e.g. processing, completed, failed
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```