package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    private static final String EXTERNAL_API_URL = "https://virtserver.swaggerhub.com/CGIANNAROS/Test/1.0.0/developers/searchInventory";
    private static final String ENTITY_NAME = "InventoryReport";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Workflow function applied asynchronously before persistence.
    // Receives entity ObjectNode, modifies it directly.
    // Calls external API, calculates report, updates entity fields.
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processInventoryReport = entityNode -> {
        logger.info("processInventoryReport workflow started for entity");

        // Extract filters from entityNode
        String category = entityNode.hasNonNull("category") ? entityNode.get("category").asText() : null;
        String dateFrom = entityNode.hasNonNull("dateFrom") ? entityNode.get("dateFrom").asText() : null;
        String dateTo = entityNode.hasNonNull("dateTo") ? entityNode.get("dateTo").asText() : null;

        // Generate and set reportId and generatedAt
        String reportId = UUID.randomUUID().toString();
        Instant generatedAt = Instant.now();
        entityNode.put("reportId", reportId);
        entityNode.put("generatedAt", generatedAt.toString());

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Calling external API from workflow for reportId={}", reportId);

                // In a real scenario, should pass filters to external API if supported
                String response = restTemplate.getForObject(EXTERNAL_API_URL, String.class);
                if (response == null) throw new IllegalStateException("External API returned null response");

                JsonNode inventoryData = objectMapper.readTree(response);
                if (!inventoryData.isArray()) {
                    throw new IllegalStateException("External API returned non-array data");
                }

                int totalItems = 0;
                double totalValue = 0.0;

                for (JsonNode item : inventoryData) {
                    totalItems++;
                    double price = item.has("price") && item.get("price").isNumber() ? item.get("price").asDouble() : 0.0;
                    totalValue += price;
                }

                double averagePrice = totalItems > 0 ? totalValue / totalItems : 0.0;

                // Update entityNode with calculated report fields
                entityNode.put("totalItems", totalItems);
                entityNode.put("totalValue", totalValue);
                entityNode.put("averagePrice", averagePrice);

                // Placeholder for adding supplementary entities if needed:
                // e.g. entityService.addItem("OtherEntityModel", ENTITY_VERSION, supplementaryData, null);

                logger.info("processInventoryReport workflow completed for reportId={}", reportId);

                return entityNode;

            } catch (Exception e) {
                logger.error("Error in processInventoryReport workflow: {}", e.getMessage(), e);
                // Mark entityNode with error info to persist error state if needed
                entityNode.put("error", "Failed to generate report: " + e.getMessage());
                // Could also add an error status or timestamp
                entityNode.put("status", "failed");
                entityNode.put("errorTimestamp", Instant.now().toString());
                // Return entityNode with error info to persist partial state
                return entityNode;
            }
        });
    };

    // Controller endpoint to start report generation
    // Prepares minimal entity with input filters, calls addItem with workflow
    @PostMapping
    public CompletableFuture<ResponseEntity<GenerateReportResponse>> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        logger.info("Received request to generate inventory report with category={} dateFrom={} dateTo={}",
                request.getCategory(), request.getDateFrom(), request.getDateTo());

        ObjectNode entityNode = objectMapper.createObjectNode();
        if (request.getCategory() != null) entityNode.put("category", request.getCategory());
        if (request.getDateFrom() != null) entityNode.put("dateFrom", request.getDateFrom());
        if (request.getDateTo() != null) entityNode.put("dateTo", request.getDateTo());

        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, processInventoryReport)
                .thenApply(uuid -> {
                    String reportId = uuid != null ? uuid.toString() : null;
                    logger.info("Add item call returned UUID={}", reportId);
                    return ResponseEntity.ok(new GenerateReportResponse(reportId, "SUCCESS", "Report generation started"));
                })
                .exceptionally(ex -> {
                    logger.error("Failed to add entity with workflow: {}", ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new GenerateReportResponse(null, "FAILED", "Failed to start report generation"));
                });
    }

    // Get report by reportId, returns persisted ObjectNode entity
    @GetMapping("/{reportId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Retrieving report for reportId={}", reportId);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.reportId", "EQUALS", reportId));

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.isEmpty()) {
                        logger.error("Report not found for reportId={}", reportId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    JsonNode node = arrayNode.get(0);
                    if (!(node instanceof ObjectNode)) {
                        logger.error("Stored report entity is not an ObjectNode for reportId={}", reportId);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid report data");
                    }
                    return ResponseEntity.ok((ObjectNode) node);
                });
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
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}