package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/api/reports/inventory")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    private static final String ENTITY_NAME = "InventoryReport";

    @PostMapping
    public ResponseEntity<ReportGenerationResponse> generateReport(@RequestBody @Valid ReportRequest request) {
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        logger.info("Received report generation request: {}. reportId={}", request, reportId);

        InventoryReport stub = new InventoryReport(reportId, requestedAt, "processing", null, null);

        // Save stub report via entityService
        // Using addItem returns technicalId, but reportId is UUID string, so preserve reportId in object
        entityService.addItem(ENTITY_NAME, ENTITY_VERSION, stub);

        CompletableFuture.runAsync(() -> processReport(reportId, request))
            .exceptionally(ex -> {
                logger.error("Error processing report {}: {}", reportId, ex.getMessage(), ex);
                // Update report status to failed
                try {
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.reportId", "EQUALS", reportId));
                    entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond)
                        .thenAccept(arr -> {
                            if (arr.size() > 0) {
                                JsonNode node = arr.get(0);
                                InventoryReport rep;
                                try {
                                    rep = objectMapper.treeToValue(node, InventoryReport.class);
                                    rep.setStatus("failed");
                                    entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(node.get("technicalId").asText()), rep);
                                } catch (Exception e) {
                                    logger.error("Failed to update report status to failed: {}", e.getMessage(), e);
                                }
                            }
                        }).join();
                } catch (Exception e) {
                    logger.error("Failed to set report status failed on exception: {}", e.getMessage(), e);
                }
                return null;
            });

        return ResponseEntity.accepted().body(new ReportGenerationResponse(reportId, "processing"));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<InventoryReport> getReport(@PathVariable String reportId) {
        logger.info("Fetching report {}", reportId);

        SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.reportId", "EQUALS", reportId));

        ArrayList<InventoryReport> reports = new ArrayList<>();
        try {
            ArrayList<JsonNode> arr = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond).get();
            if (arr.isEmpty()) {
                logger.error("Report {} not found", reportId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
            }
            JsonNode node = arr.get(0);
            InventoryReport report = objectMapper.treeToValue(node, InventoryReport.class);

            if ("processing".equalsIgnoreCase(report.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(report);
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    private void processReport(String reportId, ReportRequest request) {
        logger.info("Processing report {}", reportId);
        String url = buildExternalApiUrl(request);
        logger.info("Calling external API: {}", url);
        JsonNode root;
        try {
            String resp = restTemplate.getForObject(new URI(url), String.class);
            if (resp == null) throw new IllegalStateException("Empty response");
            root = objectMapper.readTree(resp);
        } catch (Exception e) {
            logger.error("Fetch/parse error for {}: {}", reportId, e.getMessage(), e);
            // Update report status to failed
            try {
                SearchConditionRequest cond = SearchConditionRequest.group("AND",
                        Condition.of("$.reportId", "EQUALS", reportId));
                entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond)
                        .thenAccept(arr -> {
                            if (arr.size() > 0) {
                                JsonNode node = arr.get(0);
                                InventoryReport rep;
                                try {
                                    rep = objectMapper.treeToValue(node, InventoryReport.class);
                                    rep.setStatus("failed");
                                    entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(node.get("technicalId").asText()), rep);
                                } catch (Exception ex) {
                                    logger.error("Failed to update report status to failed: {}", ex.getMessage(), ex);
                                }
                            }
                        }).join();
            } catch (Exception ex) {
                logger.error("Failed to set report status failed on exception: {}", ex.getMessage(), ex);
            }
            return;
        }
        List<InventoryItem> items = parseInventoryItems(root);
        InventoryMetrics metrics = calculateMetrics(items);
        InventoryReport complete = new InventoryReport(reportId, Instant.now(), "completed", metrics, items);

        try {
            SearchConditionRequest cond = SearchConditionRequest.group("AND",
                    Condition.of("$.reportId", "EQUALS", reportId));
            entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond)
                    .thenAccept(arr -> {
                        if (arr.size() > 0) {
                            JsonNode node = arr.get(0);
                            UUID techId = UUID.fromString(node.get("technicalId").asText());
                            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, techId, complete);
                        }
                    }).join();
        } catch (Exception e) {
            logger.error("Failed to update completed report: {}", e.getMessage(), e);
        }
        logger.info("Completed report {} with {} items", reportId, items.size());
    }

    private String buildExternalApiUrl(ReportRequest req) {
        StringBuilder sb = new StringBuilder("https://cgiannaros.github.io/Test-API/inventory");
        boolean first = true;
        if (req.getCategory() != null) { sb.append(first?"?":"&").append("category=").append(req.getCategory()); first=false; }
        if (req.getMinPrice() != null) { sb.append(first?"?":"&").append("minPrice=").append(req.getMinPrice()); first=false; }
        if (req.getMaxPrice() != null) { sb.append(first?"?":"&").append("maxPrice=").append(req.getMaxPrice()); first=false; }
        if (req.getDateFrom() != null) { sb.append(first?"?":"&").append("dateFrom=").append(req.getDateFrom()); first=false; }
        if (req.getDateTo() != null) sb.append(first?"?":"&").append("dateTo=").append(req.getDateTo());
        return sb.toString();
    }

    private List<InventoryItem> parseInventoryItems(JsonNode root) {
        List<InventoryItem> list = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(node -> { InventoryItem it = parseItem(node); if (it!=null) list.add(it); });
        } else if (root.isObject()) {
            JsonNode arr = root.has("items") ? root.get("items") : root.has("inventory") ? root.get("inventory") : null;
            if (arr!=null && arr.isArray()) {
                arr.forEach(node -> { InventoryItem it = parseItem(node); if (it!=null) list.add(it); });
            } else {
                InventoryItem it = parseItem(root);
                if (it!=null) list.add(it);
            }
        }
        return list;
    }

    private InventoryItem parseItem(JsonNode n) {
        try {
            String itemId = n.hasNonNull("itemId") ? n.get("itemId").asText() : UUID.randomUUID().toString();
            String name = n.hasNonNull("name") ? n.get("name").asText() : "Unknown";
            String category = n.hasNonNull("category") ? n.get("category").asText() : "Uncategorized";
            double price = n.hasNonNull("price") ? n.get("price").asDouble() : 0;
            int qty = n.hasNonNull("quantity") ? n.get("quantity").asInt() : 0;
            return new InventoryItem(itemId, name, category, price, qty);
        } catch (Exception e) {
            logger.warn("Item parse failed: {}", e.getMessage());
            return null;
        }
    }

    private InventoryMetrics calculateMetrics(List<InventoryItem> items) {
        int total = items.size();
        double sumVal = 0, sumPrice=0; int count=0;
        for (InventoryItem i: items) {
            sumVal += i.getPrice()*i.getQuantity();
            if (i.getPrice()>0) { sumPrice+=i.getPrice(); count++; }
        }
        double avg = count>0?sumPrice/count:0;
        return new InventoryMetrics(total, avg, sumVal);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handle(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getMessage());
        Map<String,String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @Data
    public static class ReportRequest {
        @Size(max=50) // optional category
        private String category;
        @PositiveOrZero // optional minimum price
        private Double minPrice;
        @PositiveOrZero // optional maximum price
        private Double maxPrice;
        @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}.*", message="Must be ISO8601") // optional
        private String dateFrom;
        @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}.*", message="Must be ISO8601")
        private String dateTo;
    }

    @Data
    public static class ReportGenerationResponse {
        private final String reportId;
        private final String status;
    }

    @Data
    public static class InventoryReport {
        private final String reportId;
        private final Instant generatedAt;
        private String status;
        private InventoryMetrics metrics;
        private List<InventoryItem> data;
    }

    @Data
    public static class InventoryMetrics {
        private final int totalItems;
        private final double averagePrice;
        private final double totalValue;
    }

    @Data
    public static class InventoryItem {
        private final String itemId;
        private final String name;
        private final String category;
        private final double price;
        private final int quantity;
    }
}