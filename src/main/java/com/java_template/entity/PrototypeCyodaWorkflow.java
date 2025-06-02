package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-reports")
@Validated
@Slf4j
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String EXTERNAL_API_BASE = "https://cgiannaros.github.io/Test/1.0.0/developers/searchInventory"; // TODO: replace with actual

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    /**
     * Workflow function: processReportRequest
     * This is called asynchronously by entityService.addItem right before persisting.
     * It receives the entity as ObjectNode, modifies it directly and returns it.
     */
    private CompletableFuture<ObjectNode> processReportRequest(ObjectNode entity) {
        logger.info("Workflow processReportRequest started for entity");

        // Run asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract filters from entity
                String category = getStringOrNull(entity, "category");
                Double minPrice = getDoubleOrNull(entity, "minPrice");
                Double maxPrice = getDoubleOrNull(entity, "maxPrice");
                String dateFrom = getStringOrNull(entity, "dateFrom");
                String dateTo = getStringOrNull(entity, "dateTo");

                URI uri = buildExternalUri(category, minPrice, maxPrice, dateFrom, dateTo);
                logger.info("Workflow calling external API: {}", uri);

                String resp = restTemplate.getForObject(uri, String.class);
                if (!StringUtils.hasText(resp)) {
                    throw new IllegalStateException("Empty external response");
                }

                JsonNode root = objectMapper.readTree(resp);
                JsonNode itemsNode = root.isArray() ? root : root.path("items");

                if (!itemsNode.isArray()) {
                    throw new IllegalStateException("Missing or invalid 'items' array");
                }

                List<InventoryItem> items = new ArrayList<>();
                for (JsonNode n : itemsNode) {
                    InventoryItem item = parseInventoryItem(n);
                    if (item != null) items.add(item);
                }

                ReportMetrics metrics = calculateMetrics(items);

                // Update entity fields directly
                entity.put("generatedAt", Instant.now().toString());
                entity.put("status", ReportStatus.COMPLETED.name());

                // Set metrics as nested object
                ObjectNode metricsNode = objectMapper.createObjectNode();
                metricsNode.put("totalItems", metrics.getTotalItems());
                metricsNode.put("averagePrice", metrics.getAveragePrice());
                metricsNode.put("totalValue", metrics.getTotalValue());
                metricsNode.put("minPrice", metrics.getMinPrice());
                metricsNode.put("maxPrice", metrics.getMaxPrice());
                entity.set("metrics", metricsNode);

                // Set data array of inventory items
                entity.set("data", objectMapper.valueToTree(items));

                // Clear any previous error message
                entity.remove("errorMessage");

                logger.info("Workflow processReportRequest completed successfully");

            } catch (Exception e) {
                logger.error("Workflow processReportRequest failed: {}", e.getMessage(), e);

                entity.put("generatedAt", Instant.now().toString());
                entity.put("status", ReportStatus.FAILED.name());
                entity.put("errorMessage", e.getMessage());

                // Remove metrics and data in case of failure
                entity.remove("metrics");
                entity.remove("data");
            }

            return entity;
        });
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerateReportResponse> generateReport(
            @Valid @RequestBody GenerateReportRequest request) {

        logger.info("Received report generation request with filters: {}", request);

        // Compose initial entity data as ObjectNode
        ObjectNode ingestData = objectMapper.createObjectNode();
        if (request.getCategory() != null) ingestData.put("category", request.getCategory());
        if (request.getMinPrice() != null) ingestData.put("minPrice", request.getMinPrice());
        if (request.getMaxPrice() != null) ingestData.put("maxPrice", request.getMaxPrice());
        if (request.getDateFrom() != null) ingestData.put("dateFrom", request.getDateFrom());
        if (request.getDateTo() != null) ingestData.put("dateTo", request.getDateTo());
        ingestData.put("requestedAt", Instant.now().toString());
        ingestData.put("status", ReportStatus.IN_PROGRESS.name());

        // Pass workflow function processReportRequest to addItem
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "ReportRequest",
                ENTITY_VERSION,
                ingestData,
                this::processReportRequest
        );

        // Return technicalId as reportId string
        return ResponseEntity.ok(new GenerateReportResponse(idFuture.join().toString(), ReportStatus.IN_PROGRESS.name(), null));
    }

    @GetMapping(value = "/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Report>> getReport(@PathVariable @NotBlank String reportId) {

        UUID technicalId;
        try {
            technicalId = UUID.fromString(reportId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid reportId format");
        }

        return entityService.getItem("ReportRequest", ENTITY_VERSION, technicalId)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isNull()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found");
                    }
                    Report report = mapObjectNodeToReport((ObjectNode) itemNode);
                    return ResponseEntity.ok(report);
                });
    }

    // Helper to get a String value or null from ObjectNode
    private static String getStringOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    // Helper to get Double or null from ObjectNode
    private static Double getDoubleOrNull(ObjectNode node, String field) {
        JsonNode n = node.get(field);
        if (n != null && n.isNumber()) {
            return n.asDouble();
        }
        return null;
    }

    private URI buildExternalUri(String category, Double minPrice, Double maxPrice, String dateFrom, String dateTo) {
        StringBuilder sb = new StringBuilder(EXTERNAL_API_BASE);
        List<String> params = new ArrayList<>();
        if (StringUtils.hasText(category)) params.add("category=" + category);
        if (minPrice != null) params.add("minPrice=" + minPrice);
        if (maxPrice != null) params.add("maxPrice=" + maxPrice);
        if (StringUtils.hasText(dateFrom)) params.add("dateFrom=" + dateFrom);
        if (StringUtils.hasText(dateTo)) params.add("dateTo=" + dateTo);
        if (!params.isEmpty()) sb.append("?").append(String.join("&", params));
        return URI.create(sb.toString());
    }

    private InventoryItem parseInventoryItem(JsonNode n) {
        try {
            String id = n.path("itemId").asText(null);
            String name = n.path("name").asText(null);
            String cat = n.path("category").asText(null);
            Double price = n.path("price").isNumber() ? n.path("price").asDouble() : null;
            Integer qty = n.path("quantity").isInt() ? n.path("quantity").asInt() : null;
            if (id == null || name == null || price == null || qty == null) {
                logger.warn("Skipping item with missing fields: {}", n);
                return null;
            }
            return new InventoryItem(id, name, cat, price, qty);
        } catch (Exception e) {
            logger.warn("Failed to parse item: {}", e.getMessage());
            return null;
        }
    }

    private ReportMetrics calculateMetrics(List<InventoryItem> items) {
        int totalItems = items.size();
        double sumPrice = 0, totalValue = 0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (InventoryItem i : items) {
            double p = i.getPrice();
            int q = i.getQuantity();
            sumPrice += p;
            totalValue += p * q;
            if (p < min) min = p;
            if (p > max) max = p;
        }
        double avg = totalItems > 0 ? sumPrice / totalItems : 0;
        if (min == Double.MAX_VALUE) min = 0;
        if (max == Double.MIN_VALUE) max = 0;
        return new ReportMetrics(totalItems, avg, totalValue, min, max);
    }

    private Report mapObjectNodeToReport(ObjectNode node) {
        Report r = new Report();
        r.setReportId(node.path("technicalId").asText(null));
        r.setGeneratedAt(node.hasNonNull("generatedAt") ? Instant.parse(node.get("generatedAt").asText()) : null);
        r.setStatus(node.hasNonNull("status") ? ReportStatus.valueOf(node.get("status").asText()) : null);
        if (node.hasNonNull("metrics")) {
            JsonNode metricsNode = node.get("metrics");
            ReportMetrics metrics = new ReportMetrics(
                    metricsNode.path("totalItems").asInt(),
                    metricsNode.path("averagePrice").asDouble(),
                    metricsNode.path("totalValue").asDouble(),
                    metricsNode.path("minPrice").asDouble(),
                    metricsNode.path("maxPrice").asDouble()
            );
            r.setMetrics(metrics);
        }
        if (node.hasNonNull("data")) {
            List<InventoryItem> items = new ArrayList<>();
            for (JsonNode itemNode : node.withArray("data")) {
                InventoryItem item = new InventoryItem(
                        itemNode.path("itemId").asText(),
                        itemNode.path("name").asText(),
                        itemNode.path("category").asText(null),
                        itemNode.path("price").asDouble(),
                        itemNode.path("quantity").asInt()
                );
                items.add(item);
            }
            r.setData(items);
        }
        if (node.hasNonNull("errorMessage")) {
            r.setErrorMessage(node.get("errorMessage").asText());
        }
        return r;
    }

    @Data
    public static class GenerateReportRequest {
        private String category;
        @DecimalMin(value = "0.0", inclusive = true)
        private Double minPrice;
        @DecimalMin(value = "0.0", inclusive = true)
        private Double maxPrice;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z$", message = "dateFrom must be ISO8601")
        private String dateFrom;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z$", message = "dateTo must be ISO8601")
        private String dateTo;
    }

    @Data
    @AllArgsConstructor
    public static class GenerateReportResponse {
        private String reportId;
        private String status;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Report {
        private String reportId;
        private Instant generatedAt;
        private ReportStatus status;
        private ReportMetrics metrics;
        private List<InventoryItem> data;
        private String errorMessage;
    }

    @Data
    @AllArgsConstructor
    public static class ReportMetrics {
        private int totalItems;
        private double averagePrice;
        private double totalValue;
        private double minPrice;
        private double maxPrice;
    }

    @Data
    @AllArgsConstructor
    public static class InventoryItem {
        private String itemId;
        private String name;
        private String category;
        private double price;
        private int quantity;
    }

    public enum ReportStatus {
        IN_PROGRESS, COMPLETED, FAILED
    }
}