```java
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

    // We keep this cache here because workflow functions cannot return a file or binary.
    // We assign reportPDF to the persisted entity in workflow (by technicalId key).
    private final Map<UUID, byte[]> reportPdfs = new HashMap<>();

    /**
     * Endpoint only triggers creation of ReportSummary entity with minimal data (reportDate).
     * All heavy lifting, external calls, PDF generation, and secondary entities are done inside
     * the workflow function processReportSummary().
     */
    @PostMapping("/data-extraction")
    public ResponseEntity<TriggerResponse> triggerDataExtraction(@RequestBody(required = false) @Valid TriggerRequest request) {
        Instant requestedAt = Instant.now();
        logger.info("Received data extraction trigger request at {}", requestedAt);

        String triggerDate = (request != null && request.getTriggerDate() != null)
                ? request.getTriggerDate()
                : Instant.now().toString();

        // Create minimal ReportSummary entity with just reportDate.
        ReportSummary initialSummary = new ReportSummary();
        initialSummary.setReportDate(triggerDate);

        // Add item with workflow function to do all async tasks and enrich entity before persistence
        CompletableFuture<UUID> futureId = entityService.addItem(
                "ReportSummary",
                ENTITY_VERSION,
                initialSummary,
                this::processReportSummary // workflow function that will do async tasks and enrich entity
        );

        // We do not wait here, just return accepted
        return ResponseEntity.accepted()
                .body(new TriggerResponse("started", "Data extraction and report generation initiated."));
    }

    /**
     * Workflow function with prefix 'process' + entity name 'ReportSummary'.
     * This function is called asynchronously before persisting the entity.
     * It receives the entity as ObjectNode, can modify it directly (e.g. put fields),
     * can get/add entities of different models, but cannot modify the same entityModel (to avoid recursion).
     */
    private CompletableFuture<ObjectNode> processReportSummary(ObjectNode entity) {
        logger.info("Workflow processReportSummary started for entity reportDate={}", entity.get("reportDate").asText());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Fetch inventory data from external API
                String url = "https://petstore.swagger.io/v2/store/inventory";
                String rawJson = restTemplate.getForObject(url, String.class);
                JsonNode inventoryNode = objectMapper.readTree(rawJson);

                // Step 2: Get mock sales data
                JsonNode salesData = getMockSalesData();

                // Step 3: Analyze KPIs
                ReportSummary summary = analyzeKpis(inventoryNode, salesData, entity.get("reportDate").asText());

                // Step 4: Modify the entity ObjectNode directly with all summary fields
                entity.put("salesVolume", summary.getSalesVolume());
                entity.put("revenue", summary.getRevenue());
                entity.put("inventoryTurnover", summary.getInventoryTurnover());

                // Convert underperforming products list to ArrayNode and set in entity
                ArrayNode underperformingArray = objectMapper.createArrayNode();
                for (UnderperformingProduct p : summary.getUnderperformingProducts()) {
                    ObjectNode productNode = objectMapper.createObjectNode();
                    productNode.put("productId", p.getProductId());
                    productNode.put("reason", p.getReason());
                    underperformingArray.add(productNode);
                }
                entity.set("underperformingProducts", underperformingArray);

                // Step 5: Since we can't update or add the same entityModel inside workflow (to avoid recursion),
                // we can add secondary/supplementary entities if needed here (not required in current logic).

                // Step 6: Generate mock PDF report bytes
                byte[] pdfReport = generateMockPdfReport(summary);

                // Step 7: Store the PDF in a local cache keyed by technicalId (which is still unknown here!)
                // The technicalId is assigned AFTER persistence, so we cannot get it now.
                // Instead, we will store PDF after persistence in a separate listener or on-demand.
                // For demo, store PDF keyed by reportDate (as a workaround)
                synchronized (reportPdfs) {
                    reportPdfs.put(UUID.nameUUIDFromBytes(entity.get("reportDate").asText().getBytes()), pdfReport);
                }

                logger.info("Workflow processReportSummary completed successfully for reportDate={}", entity.get("reportDate").asText());
            } catch (IOException e) {
                logger.error("Error in workflow processReportSummary: {}", e.getMessage(), e);
            }
            return entity;
        });
    }

    @GetMapping("/reports/latest/summary")
    public ResponseEntity<ReportSummary> getLatestReportSummary() {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        if (items.isEmpty()) {
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

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        // Find latest reportDate and technicalId
        String latestDate = null;
        UUID latestId = null;
        for (JsonNode node : items) {
            String reportDate = node.get("reportDate").asText();
            UUID technicalId = UUID.fromString(node.get("technicalId").asText());
            if (latestDate == null || reportDate.compareTo(latestDate) > 0) {
                latestDate = reportDate;
                latestId = technicalId;
            }
        }

        if (latestId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        // Try get PDF from cache by technicalId
        byte[] pdf = reportPdfs.get(latestId);

        // If not found by technicalId, fallback to reportDate key
        if (pdf == null && latestDate != null) {
            pdf = reportPdfs.get(UUID.nameUUIDFromBytes(latestDate.getBytes()));
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
        return new ReportSummary(
                triggerDate,
                155,
                17200.0,
                3.2,
                underperforming
        );
    }

    private byte[] generateMockPdfReport(ReportSummary summary) {
        String mockContent = "Weekly Report PDF\n" +
                "Date: " + summary.getReportDate() + "\n" +
                "Sales Volume: " + summary.getSalesVolume() + "\n" +
                "Revenue: " + summary.getRevenue() + "\n" +
                "Inventory Turnover: " + summary.getInventoryTurnover() + "\n" +
                "Underperforming Products: " + summary.getUnderperformingProducts().size();
        return mockContent.getBytes();
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
```

---

### What was done:

- **Moved all async tasks and heavy logic from controller endpoint into the `processReportSummary` workflow function**:
  - Fetching external inventory data
  - Getting mock sales data
  - KPI analysis
  - Modifying the entity ObjectNode directly with all calculated fields (including nested arrays)
  - Generating mock PDF bytes and saving it in local cache
- The controller method `triggerDataExtraction` now only creates a minimal entity (with just `reportDate`) and calls `entityService.addItem` with the workflow function.
- Removed all async code from controller, making it clean and focused on HTTP handling.
- Workflow function returns the modified entity which will be persisted.
- Added comments explaining constraints and design choices regarding PDF cache keyed by `reportDate` due to lack of technicalId before persistence.
- Read endpoints (`getLatestReportSummary`, `downloadLatestReportPdf`) remain unchanged, except fallback for PDF cache key.

This approach fully complies with your requirements:
- All async and side-effect logic moved to workflow function.
- Workflow function modifies the entity state directly.
- No add/update/delete on the same entityModel inside workflow.
- Controller is free from business logic, only manages requests and responses.