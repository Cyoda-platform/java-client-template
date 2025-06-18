```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/cyoda/api/reports/inventory")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String EXTERNAL_API_URL = "https://virtserver.swaggerhub.com/CGIANNAROS/Test/1.0.0/developers/searchInventory";
    private static final String ENTITY_NAME = "InventoryReport";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    /**
     * The workflow function to be applied to InventoryReport entity before persistence.
     * This function can modify the entity asynchronously.
     * It must return the entity (possibly modified).
     */
    private final Function<InventoryReport, CompletableFuture<InventoryReport>> processInventoryReport = entity -> {
        // Example: add/update some fields asynchronously if needed.
        // For demo, we just return the entity as is.
        return CompletableFuture.completedFuture(entity);
    };

    @PostMapping
    public CompletableFuture<ResponseEntity<GenerateReportResponse>> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        logger.info("Received request to generate inventory report with category={} dateFrom={} dateTo={}",
                request.getCategory(), request.getDateFrom(), request.getDateTo());

        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(reportId, new JobStatus("processing", requestedAt));

        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting report generation for reportId={}", reportId);
                JsonNode inventoryData = callExternalInventoryApi(request);
                InventoryReport report = calculateReport(inventoryData);
                report.setReportId(reportId);
                report.setGeneratedAt(Instant.now());

                // Add report via entityService with workflow function
                entityService.addItem(ENTITY_NAME, ENTITY_VERSION, report, processInventoryReport).join();

                entityJobs.put(reportId, new JobStatus("completed", Instant.now()));
                logger.info("Report generation completed for reportId={}", reportId);
            } catch (Exception e) {
                logger.error("Error generating report for reportId={}: {}", reportId, e.getMessage(), e);
                entityJobs.put(reportId, new JobStatus("failed", Instant.now()));
            }
        }).thenApply(v -> ResponseEntity.ok(new GenerateReportResponse(reportId, "SUCCESS", "Report generation started")));
    }

    @GetMapping("/{reportId}")
    public CompletableFuture<ResponseEntity<InventoryReport>> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Retrieving report for reportId={}", reportId);

        // Build condition to find by reportId field
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.reportId", "EQUALS", reportId));

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.isEmpty()) {
                        logger.error("Report not found for reportId={}", reportId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    JsonNode foundNode = arrayNode.get(0);
                    InventoryReport report;
                    try {
                        report = objectMapper.treeToValue(foundNode, InventoryReport.class);
                    } catch (Exception e) {
                        logger.error("Error deserializing InventoryReport for reportId={}: {}", reportId, e.getMessage(), e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize report");
                    }
                    return ResponseEntity.ok(report);
                });
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
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId; // ignored for serialization/deserialization except internal use
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
```
---

### Explanation of changes:
- Added a workflow function `processInventoryReport` with the required naming convention (`process` + `InventoryReport` entity name).
- This function takes an `InventoryReport` entity and returns a `CompletableFuture<InventoryReport>`.
- Updated the call to `entityService.addItem` to pass this workflow function as the new fourth argument.
- The workflow function currently just returns the entity as-is, but you can modify it asynchronously if needed before persistence.
- All other parts of the code remain unchanged.