package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("/prototype/api/reports/inventory")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, InventoryReport> reports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String EXTERNAL_API_URL = "https://virtserver.swaggerhub.com/CGIANNAROS/Test/1.0.0/developers/searchInventory";

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    @PostMapping
    public ResponseEntity<GenerateReportResponse> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        logger.info("Received request to generate inventory report with category={} dateFrom={} dateTo={}",
                request.getCategory(), request.getDateFrom(), request.getDateTo());

        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(reportId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting report generation for reportId={}", reportId);
                JsonNode inventoryData = callExternalInventoryApi(request);
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

    @GetMapping("/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Retrieving report for reportId={}", reportId);
        InventoryReport report = reports.get(reportId);
        if (report == null) {
            logger.error("Report not found for reportId={}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    private JsonNode callExternalInventoryApi(GenerateReportRequest request) {
        try {
            // TODO: Enhance to send filters via query params or request body if supported
            logger.info("Calling external API: {}", EXTERNAL_API_URL);
            String response = restTemplate.getForObject(EXTERNAL_API_URL, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error calling external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch inventory data from external API");
        }
    }

    private InventoryReport calculateReport(JsonNode inventoryData) {
        if (!inventoryData.isArray()) {
            logger.error("Expected inventory data to be an array");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid inventory data format");
        }

        int totalItems = 0;
        double totalValue = 0.0;

        for (JsonNode item : inventoryData) {
            totalItems++;
            double price = item.has("price") && item.get("price").isNumber() ? item.get("price").asDouble() : 0.0;
            totalValue += price;
        }

        double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;
        InventoryReport report = new InventoryReport();
        report.setTotalItems(totalItems);
        report.setTotalValue(totalValue);
        report.setAveragePrice(averagePrice);
        logger.info("Calculated report: totalItems={}, averagePrice={}, totalValue={}", totalItems, averagePrice, totalValue);
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateReportRequest {
        @Size(max = 100)
        private String category;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String dateFrom;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String dateTo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateReportResponse {
        private String reportId;
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    public static class InventoryReport {
        private String reportId;
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private Map<String, Object> otherStatistics;
        private Instant generatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}