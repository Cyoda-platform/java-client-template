package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, ReportMetadata> reports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

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
                String reportId = generateReportId(request.getExtractionDate());
                ReportMetadata reportMetadata = new ReportMetadata(
                    reportId,
                    Instant.now(),
                    summary,
                    "/prototype/api/reports/" + reportId + "/download"
                );
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
        return reports.values().stream()
            .max((r1, r2) -> r1.getGeneratedAt().compareTo(r2.getGeneratedAt()))
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports found"));
    }

    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable("reportId") @NotBlank String reportId) {
        logger.info("Download requested for reportId={}", reportId);
        ReportMetadata metadata = reports.get(reportId);
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        byte[] placeholderPdf = generatePlaceholderPdf(reportId, metadata.getSummary());
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"report-" + reportId + ".pdf\"")
            .body(placeholderPdf);
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

    public static class ExtractionRequest {
        @NotBlank
        public String extractionDate;
    }

    public static class StartResponse {
        public final String status;
        public final String message;
        public StartResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    public static class JobStatus {
        public final String status;
        public final Instant timestamp;
        public JobStatus(String status, Instant timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
    }

    public static class ReportMetadata {
        public final String reportId;
        public final Instant generatedAt;
        public final ReportSummary summary;
        public final String reportDownloadLink;
        public ReportMetadata(String reportId, Instant generatedAt, ReportSummary summary, String reportDownloadLink) {
            this.reportId = reportId;
            this.generatedAt = generatedAt;
            this.summary = summary;
            this.reportDownloadLink = reportDownloadLink;
        }
    }

    public static class ReportSummary {
        public ProductPerformance[] topSellingProducts;
        public InventoryStatus[] restockItems;
        public String performanceInsights;

        public static class ProductPerformance {
            public final int productId;
            public final String name;
            public final int salesVolume;
            public ProductPerformance(int productId, String name, int salesVolume) {
                this.productId = productId;
                this.name = name;
                this.salesVolume = salesVolume;
            }
        }

        public static class InventoryStatus {
            public final int productId;
            public final String name;
            public final int stockLevel;
            public InventoryStatus(int productId, String name, int stockLevel) {
                this.productId = productId;
                this.name = name;
                this.stockLevel = stockLevel;
            }
        }
    }

    public static class ErrorResponse {
        public final String error;
        public final String message;
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }
}