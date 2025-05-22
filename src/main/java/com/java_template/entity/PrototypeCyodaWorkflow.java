package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Cache for storing PDF reports keyed by technicalId after persistence.
    // Use concurrent map to prevent concurrency issues.
    private final Map<UUID, byte[]> reportPdfs = Collections.synchronizedMap(new HashMap<>());

    /**
     * Trigger endpoint only creates minimal entity with reportDate.
     * Heavy async processing moved into workflow function.
     */
    @PostMapping("/data-extraction")
    public ResponseEntity<TriggerResponse> triggerDataExtraction(@RequestBody(required = false) @Valid TriggerRequest request) {
        Instant requestedAt = Instant.now();
        logger.info("Received data extraction trigger request at {}", requestedAt);

        String triggerDate = (request != null && request.getTriggerDate() != null)
                ? request.getTriggerDate()
                : Instant.now().toString();

        ReportSummary initialSummary = new ReportSummary();
        initialSummary.setReportDate(triggerDate);

        // Add entity with workflow function that does async processing and enriches entity before persistence
        CompletableFuture<UUID> futureId = entityService.addItem(
                "ReportSummary",
                ENTITY_VERSION,
                initialSummary,
                this::processReportSummary
        );

        // After entity is persisted we can store the PDF in cache keyed by technicalId
        futureId.thenAccept(technicalId -> {
            // Defensive null check
            if (technicalId == null) {
                logger.warn("Received null technicalId after persisting ReportSummary entity");
                return;
            }
            // Generate PDF again for caching since workflow can't store technicalId
            try {
                // Retrieve the persisted entity to generate PDF
                // This is a workaround since we can't pass technicalId to workflow
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
                ArrayNode items = itemsFuture.join();
                if (items != null && !items.isEmpty()) {
                    for (JsonNode node : items) {
                        if (node.has("technicalId") && technicalId.toString().equals(node.get("technicalId").asText())) {
                            ReportSummary rs = objectMapper.convertValue(node, ReportSummary.class);
                            byte[] pdf = generateMockPdfReport(rs);
                            reportPdfs.put(technicalId, pdf);
                            logger.info("Cached PDF report for technicalId {}", technicalId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to generate/cache PDF after persistence for technicalId " + technicalId, e);
            }
        });

        return ResponseEntity.accepted()
                .body(new TriggerResponse("started", "Data extraction and report generation initiated."));
    }

    /**
     * Workflow function called before persistence.
     * Receives entity as ObjectNode, modifies it in-place, can add/get different entityModels but cannot modify same entityModel.
     * All async tasks moved here.
     */
    private CompletableFuture<ObjectNode> processReportSummary(ObjectNode entity) {
        logger.info("Workflow processReportSummary started for reportDate={}", entity.get("reportDate").asText());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Fetch external inventory data
                String url = "https://petstore.swagger.io/v2/store/inventory";
                String rawJson = restTemplate.getForObject(url, String.class);
                JsonNode inventoryNode = objectMapper.readTree(rawJson);

                // Get mock sales data
                JsonNode salesData = getMockSalesData();

                // Analyze KPIs
                ReportSummary summary = analyzeKpis(inventoryNode, salesData, entity.get("reportDate").asText());

                // Modify entity ObjectNode fields directly
                entity.put("salesVolume", summary.getSalesVolume());
                entity.put("revenue", summary.getRevenue());
                entity.put("inventoryTurnover", summary.getInventoryTurnover());

                ArrayNode underperformingArray = objectMapper.createArrayNode();
                for (UnderperformingProduct p : summary.getUnderperformingProducts()) {
                    ObjectNode productNode = objectMapper.createObjectNode();
                    productNode.put("productId", p.getProductId());
                    productNode.put("reason", p.getReason());
                    underperformingArray.add(productNode);
                }
                entity.set("underperformingProducts", underperformingArray);

                // Additional entities could be added here if needed, e.g.
                // entityService.addItem("OtherEntityModel", ENTITY_VERSION, someOtherData, someOtherWorkflow);

                // We cannot add/update/delete entity of same model here (would cause recursion)
            } catch (IOException e) {
                logger.error("Error in processReportSummary workflow function", e);
                // Consider adding error info to entity for transparency
                entity.put("workflowError", "Failed to process report summary: " + e.getMessage());
            }
            logger.info("Workflow processReportSummary completed for reportDate={}", entity.get("reportDate").asText());
            return entity;
        });
    }

    @GetMapping("/reports/latest/summary")
    public ResponseEntity<ReportSummary> getLatestReportSummary() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report summary available");
        }

        ReportSummary latestSummary = null;
        String latestDate = null;
        for (JsonNode node : items) {
            ReportSummary rs = objectMapper.convertValue(node, ReportSummary.class);
            if (latestDate == null || rs.getReportDate().compareTo(latestDate) > 0) {
                latestDate = rs.getReportDate();
                latestSummary = rs;
            }
        }

        if (latestSummary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report summary available");
        }

        return ResponseEntity.ok(latestSummary);
    }

    @GetMapping(value = "/reports/latest/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadLatestReportPdf() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        String latestDate = null;
        UUID latestId = null;
        for (JsonNode node : items) {
            String reportDate = node.get("reportDate").asText();
            if (!node.has("technicalId")) {
                continue;
            }
            UUID technicalId;
            try {
                technicalId = UUID.fromString(node.get("technicalId").asText());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid technicalId UUID format: {}", node.get("technicalId").asText());
                continue;
            }
            if (latestDate == null || reportDate.compareTo(latestDate) > 0) {
                latestDate = reportDate;
                latestId = technicalId;
            }
        }

        if (latestId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        byte[] pdf = reportPdfs.get(latestId);

        if (pdf == null) {
            // PDF not cached yet, try regenerating from entity data as fallback
            try {
                for (JsonNode node : items) {
                    if (node.has("technicalId") && latestId.toString().equals(node.get("technicalId").asText())) {
                        ReportSummary rs = objectMapper.convertValue(node, ReportSummary.class);
                        pdf = generateMockPdfReport(rs);
                        reportPdfs.put(latestId, pdf);
                        logger.info("Generated and cached PDF report on-demand for technicalId {}", latestId);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to generate PDF report on-demand for technicalId " + latestId, e);
            }
        }

        if (pdf == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"weekly_report.pdf\"")
                .contentLength(pdf.length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private JsonNode getMockSalesData() throws IOException {
        String mockJson = """
                {
                  "products": [
                    {"productId": "p123", "salesVolume": 50, "revenue": 5000},
                    {"productId": "p456", "salesVolume": 5, "revenue": 200},
                    {"productId": "p789", "salesVolume": 100, "revenue": 15000}
                  ]
                }
                """;
        return objectMapper.readTree(mockJson);
    }

    private ReportSummary analyzeKpis(JsonNode inventoryNode, JsonNode salesData, String triggerDate) {
        List<UnderperformingProduct> underperforming = new ArrayList<>();
        salesData.get("products").forEach(p -> {
            int salesVolume = p.get("salesVolume").asInt();
            if (salesVolume < 10) {
                underperforming.add(new UnderperformingProduct(p.get("productId").asText(), "Low sales volume"));
            }
        });

        // Example static metrics - in real scenario compute from inventoryNode and salesData
        return new ReportSummary(
                triggerDate,
                155,
                17200.0,
                3.2,
                underperforming
        );
    }

    private byte[] generateMockPdfReport(ReportSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weekly Report PDF\n");
        sb.append("Date: ").append(summary.getReportDate()).append("\n");
        sb.append("Sales Volume: ").append(summary.getSalesVolume()).append("\n");
        sb.append("Revenue: ").append(summary.getRevenue()).append("\n");
        sb.append("Inventory Turnover: ").append(summary.getInventoryTurnover()).append("\n");
        sb.append("Underperforming Products: ").append(summary.getUnderperformingProducts().size()).append("\n");
        for (UnderperformingProduct p : summary.getUnderperformingProducts()) {
            sb.append(" - ProductId: ").append(p.getProductId()).append(", Reason: ").append(p.getReason()).append("\n");
        }
        return sb.toString().getBytes();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerRequest {
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}.*$", message = "triggerDate must be ISO-8601 format")
        private String triggerDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSummary {
        private String reportDate;
        private int salesVolume;
        private double revenue;
        private double inventoryTurnover;
        private List<UnderperformingProduct> underperformingProducts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnderperformingProduct {
        private String productId;
        private String reason;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Request failed with status {} and reason: {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}