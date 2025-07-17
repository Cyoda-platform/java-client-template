package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.entity.ReportMetadata;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.Map;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/cyoda/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    private final Map<String, ReportMetadata> reports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/data-extraction/run")
    public ResponseEntity<StartResponse> runDataExtraction(@Valid @RequestBody ExtractionRequest request) {
        logger.info("Received data extraction request for date: {}", request.getExtractionDate());
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                // Fetch data (business logic moved to processors, keep persistence here)
                JsonNode salesData = fetchPetStoreApi("/store/inventory");
                JsonNode productData = fetchPetStoreApi("/pet/findByStatus?status=available");

                // Prepare ReportMetadata entity
                String reportId = generateReportId(request.getExtractionDate());
                ReportSummary summary = analyzePerformanceMetrics(salesData, productData);

                ReportMetadata reportMetadata = new ReportMetadata(
                        reportId,
                        Instant.now(),
                        summary,
                        "/cyoda/entity/reports/" + reportId + "/download"
                );

                // Persist ReportMetadata entity
                CompletableFuture<UUID> addFuture = entityService.addItem(
                        "ReportMetadata",
                        ENTITY_VERSION,
                        reportMetadata
                );
                addFuture.get(); // wait for completion

                // Cache locally
                reports.put(reportId, reportMetadata);

                sendReportEmail(reportMetadata);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Job {} completed", jobId);
            } catch (Exception e) {
                logger.error("Job {} failed: {}", jobId, e.getMessage(), e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });
        return ResponseEntity.ok(new StartResponse("started", "Data extraction and analysis workflow initiated"));
    }

    @GetMapping("/reports/latest")
    public ResponseEntity<ReportMetadata> getLatestReport() {
        logger.info("Fetching latest report");
        try {
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(
                    "ReportMetadata",
                    ENTITY_VERSION
            );
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items == null || items.isEmpty()) {
                return reports.values().stream()
                        .max(Comparator.comparing(ReportMetadata::getGeneratedAt))
                        .map(ResponseEntity::ok)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports found"));
            }
            ReportMetadata latest = null;
            for (JsonNode node : items) {
                ReportMetadata r = objectMapper.treeToValue(node, ReportMetadata.class);
                if (latest == null || r.getGeneratedAt().isAfter(latest.getGeneratedAt())) {
                    latest = r;
                }
            }
            if (latest == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports found");
            }
            return ResponseEntity.ok(latest);
        } catch (InterruptedException | ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable("reportId") @NotBlank String reportId) {
        logger.info("Download requested for reportId={}", reportId);
        try {
            com.java_template.common.util.SearchConditionRequest condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.reportId", "EQUALS", reportId)
            );
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    "ReportMetadata",
                    ENTITY_VERSION,
                    condition
            );
            com.fasterxml.jackson.databind.node.ArrayNode nodes = filteredItemsFuture.get();
            if (nodes == null || nodes.isEmpty()) {
                ReportMetadata metadata = reports.get(reportId);
                if (metadata == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                }
                byte[] placeholderPdf = generatePlaceholderPdf(reportId, metadata.getSummary());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"report-" + reportId + ".pdf\"")
                        .body(placeholderPdf);
            }
            ReportMetadata metadata = objectMapper.treeToValue(nodes.get(0), ReportMetadata.class);
            byte[] placeholderPdf = generatePlaceholderPdf(reportId, metadata.getSummary());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"report-" + reportId + ".pdf\"")
                    .body(placeholderPdf);
        } catch (InterruptedException | ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private JsonNode fetchPetStoreApi(String path) throws Exception {
        String url = "https://petstore.swagger.io/v2" + path;
        logger.info("Fetching Pet Store API data from {}", url);
        String response = restTemplate.getForObject(URI.create(url), String.class);
        if (response == null) throw new Exception("Empty response from Pet Store API");
        return objectMapper.readTree(response);
    }

    // The analyzePerformanceMetrics method and others are simplified placeholders; business logic moved to processors
    private ReportSummary analyzePerformanceMetrics(JsonNode salesData, JsonNode productData) {
        logger.info("Analyzing performance metrics (mock)");
        ReportSummary summary = new ReportSummary();
        summary.topSellingProducts = new ReportSummary.ProductPerformance[]{
                new ReportSummary.ProductPerformance(101, "Pet Food", 5000),
                new ReportSummary.ProductPerformance(102, "Pet Toys", 3000)
        };
        summary.restockItems = new ReportSummary.InventoryStatus[]{
                new ReportSummary.InventoryStatus(103, "Pet Shampoo", 10)
        };
        summary.performanceInsights = "Sales increased by 15% compared to last week.";
        return summary;
    }

    private String generateReportId(String extractionDate) {
        return extractionDate.replaceAll("-", "") + "W22"; // placeholder
    }

    private void sendReportEmail(ReportMetadata reportMetadata) {
        logger.info("Mock sending email to victoria.sagdieva@cyoda.com with reportId={}", reportMetadata.getReportId());
    }

    private byte[] generatePlaceholderPdf(String reportId, ReportSummary summary) {
        String content = "Product Performance Report: " + reportId + "\n\n"
                + "Top Selling Products:\n- " + summary.topSellingProducts[0].name + "\n\n"
                + "Restock Items:\n- " + summary.restockItems[0].name + "\n\n"
                + "Insights:\n" + summary.performanceInsights;
        return content.getBytes();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()), ex.getStatusCode());
    }

    public static class ExtractionRequest {
        @NotBlank
        private String extractionDate;

        public String getExtractionDate() {
            return extractionDate;
        }

        public void setExtractionDate(String extractionDate) {
            this.extractionDate = extractionDate;
        }
    }

    public static class StartResponse {
        private String status;
        private String message;

        public StartResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public void setStatus(String status) { this.status = status; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class JobStatus {
        private String status;
        private Instant timestamp;

        public JobStatus(String status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
        public String getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
        public void setStatus(String status) { this.status = status; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class ReportSummary {
        private ProductPerformance[] topSellingProducts;
        private InventoryStatus[] restockItems;
        private String performanceInsights;

        public ProductPerformance[] getTopSellingProducts() { return topSellingProducts; }
        public InventoryStatus[] getRestockItems() { return restockItems; }
        public String getPerformanceInsights() { return performanceInsights; }

        public void setTopSellingProducts(ProductPerformance[] topSellingProducts) { this.topSellingProducts = topSellingProducts; }
        public void setRestockItems(InventoryStatus[] restockItems) { this.restockItems = restockItems; }
        public void setPerformanceInsights(String performanceInsights) { this.performanceInsights = performanceInsights; }

        public static class ProductPerformance {
            private int productId;
            private String name;
            private int salesVolume;

            public ProductPerformance(int productId, String name, int salesVolume) {
                this.productId = productId;
                this.name = name;
                this.salesVolume = salesVolume;
            }
            public int getProductId() { return productId; }
            public String getName() { return name; }
            public int getSalesVolume() { return salesVolume; }
            public void setProductId(int productId) { this.productId = productId; }
            public void setName(String name) { this.name = name; }
            public void setSalesVolume(int salesVolume) { this.salesVolume = salesVolume; }
        }

        public static class InventoryStatus {
            private int productId;
            private String name;
            private int stockLevel;

            public InventoryStatus(int productId, String name, int stockLevel) {
                this.productId = productId;
                this.name = name;
                this.stockLevel = stockLevel;
            }
            public int getProductId() { return productId; }
            public String getName() { return name; }
            public int getStockLevel() { return stockLevel; }
            public void setProductId(int productId) { this.productId = productId; }
            public void setName(String name) { this.name = name; }
            public void setStockLevel(int stockLevel) { this.stockLevel = stockLevel; }
        }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public void setError(String error) { this.error = error; }
        public void setMessage(String message) { this.message = message; }
    }
}