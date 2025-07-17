package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    // Keep minor/utility entities in local cache
    private final Map<String, ReportMetadata> reports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/data-extraction/run")
    public ResponseEntity<StartResponse> runDataExtraction(@Valid @RequestBody ExtractionRequest request) {
        logger.info("Received data extraction request for date: {}", request.getExtractionDate());
        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode salesData = fetchPetStoreApi("/store/inventory");
                JsonNode productData = fetchPetStoreApi("/pet/findByStatus?status=available");
                ReportSummary summary = analyzePerformanceMetrics(salesData, productData);

                // Build ReportMetadata entity object to store via EntityService
                String reportId = generateReportId(request.getExtractionDate());
                ReportMetadata reportMetadata = new ReportMetadata(
                        reportId,
                        Instant.now(),
                        summary,
                        "/cyoda/entity/reports/" + reportId + "/download"
                );

                // Add reportMetadata to EntityService
                CompletableFuture<UUID> addFuture = entityService.addItem(
                        "ReportMetadata",
                        ENTITY_VERSION,
                        reportMetadata
                );
                addFuture.get(); // wait for completion

                // Update local cache as well
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
        // Try to get latest report from EntityService first
        try {
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItems(
                    "ReportMetadata",
                    ENTITY_VERSION
            );
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items == null || items.isEmpty()) {
                // fallback to local cache
                return reports.values().stream()
                        .max(Comparator.comparing(ReportMetadata::getGeneratedAt))
                        .map(ResponseEntity::ok)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports found"));
            }
            // Find the report with max generatedAt
            ReportMetadata latest = null;
            for (JsonNode node : items) {
                ReportMetadata r = objectMapper.treeToValue(node, ReportMetadata.class);
                if (latest == null || r.generatedAt.isAfter(latest.generatedAt)) {
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

        // Try to get report from EntityService by condition
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
                // fallback to local cache
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
        logger.info("Mock sending email to victoria.sagdieva@cyoda.com with reportId={}", reportMetadata.reportId);
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

    @Data
    public static class ExtractionRequest {
        @NotBlank
        private String extractionDate;
    }

    @Data
    @AllArgsConstructor
    public static class StartResponse {
        private String status;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportMetadata {
        private String reportId;
        private Instant generatedAt;
        private ReportSummary summary;
        private String reportDownloadLink;
    }

    @Data
    @NoArgsConstructor
    public static class ReportSummary {
        private ProductPerformance[] topSellingProducts;
        private InventoryStatus[] restockItems;
        private String performanceInsights;

        @Data
        @AllArgsConstructor
        public static class ProductPerformance {
            private int productId;
            private String name;
            private int salesVolume;
        }

        @Data
        @AllArgsConstructor
        public static class InventoryStatus {
            private int productId;
            private String name;
            private int stockLevel;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}