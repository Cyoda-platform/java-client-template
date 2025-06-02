package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/reports")
@Validated
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Report> reports = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String EXTERNAL_API_BASE = "https://cgiannaros.github.io/Test/1.0.0/developers/searchInventory"; // TODO: replace with actual

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerateReportResponse> generateReport(
        @Valid @RequestBody GenerateReportRequest request) {
        logger.info("Received report generation request with filters: {}", request);
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        reports.put(reportId, new Report(reportId, requestedAt, ReportStatus.IN_PROGRESS, null, null));
        CompletableFuture.runAsync(() -> processReport(reportId, request)); // async fire-and-forget
        return ResponseEntity.ok(new GenerateReportResponse(reportId, ReportStatus.IN_PROGRESS.name(), null));
    }

    @GetMapping(value = "/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Report> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Retrieving report with id {}", reportId);
        Report report = reports.get(reportId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseEntity.ok(report);
    }

    private void processReport(String reportId, GenerateReportRequest request) {
        try {
            logger.info("Processing report {}", reportId);
            URI uri = buildExternalUri(request);
            logger.info("Calling external API: {}", uri);
            String resp = restTemplate.getForObject(uri, String.class);
            if (!StringUtils.hasText(resp)) throw new IllegalStateException("Empty external response");
            JsonNode root = objectMapper.readTree(resp);
            JsonNode itemsNode = root.isArray() ? root : root.path("items");
            if (!itemsNode.isArray()) throw new IllegalStateException("Missing 'items' array");
            List<InventoryItem> items = new ArrayList<>();
            for (JsonNode n : itemsNode) {
                InventoryItem item = parseInventoryItem(n);
                if (item != null) items.add(item);
            }
            ReportMetrics metrics = calculateMetrics(items);
            reports.put(reportId, new Report(reportId, Instant.now(), ReportStatus.COMPLETED, metrics, items));
            logger.info("Report {} COMPLETED", reportId);
        } catch (Exception e) {
            logger.error("Error in report {}: {}", reportId, e.getMessage(), e);
            reports.put(reportId, new Report(reportId, Instant.now(), ReportStatus.FAILED, null, null, e.getMessage()));
        }
    }

    private URI buildExternalUri(GenerateReportRequest f) {
        StringBuilder sb = new StringBuilder(EXTERNAL_API_BASE);
        List<String> params = new ArrayList<>();
        if (StringUtils.hasText(f.getCategory())) params.add("category=" + f.getCategory());
        if (f.getMinPrice() != null) params.add("minPrice=" + f.getMinPrice());
        if (f.getMaxPrice() != null) params.add("maxPrice=" + f.getMaxPrice());
        if (StringUtils.hasText(f.getDateFrom())) params.add("dateFrom=" + f.getDateFrom());
        if (StringUtils.hasText(f.getDateTo())) params.add("dateTo=" + f.getDateTo());
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
            double p = i.getPrice(); int q = i.getQuantity();
            sumPrice += p; totalValue += p * q;
            if (p < min) min = p; if (p > max) max = p;
        }
        double avg = totalItems > 0 ? sumPrice / totalItems : 0;
        if (min == Double.MAX_VALUE) min = 0; if (max == Double.MIN_VALUE) max = 0;
        return new ReportMetrics(totalItems, avg, totalValue, min, max);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, String> err = Collections.singletonMap("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> err = Collections.singletonMap("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

    @Data
    public static class GenerateReportRequest {
        private String category;
        @DecimalMin(value = "0.0", inclusive = true) private Double minPrice;
        @DecimalMin(value = "0.0", inclusive = true) private Double maxPrice;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z$", message = "dateFrom must be ISO8601") private String dateFrom;
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z$", message = "dateTo must be ISO8601") private String dateTo;
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