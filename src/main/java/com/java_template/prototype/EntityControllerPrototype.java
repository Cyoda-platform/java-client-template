```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory store for generated reports metadata keyed by reportId (e.g. "2024W22")
    private final Map<String, ReportMetadata> reports = new ConcurrentHashMap<>();

    // Simulated job status store to track data extraction runs
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    /**
     * POST /prototype/api/data-extraction/run
     * Trigger data extraction from Pet Store API, analyze KPIs, generate report, and send email.
     */
    @PostMapping(path = "/data-extraction/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StartResponse> runDataExtraction(@RequestBody ExtractionRequest request) {
        logger.info("Received data extraction request for date: {}", request.getExtractionDate());

        // Generate a jobId for this extraction run
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));
        logger.info("Job {} status set to processing", jobId);

        // Fire-and-forget async task to fetch data, analyze, generate report, and send email
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Job {} started processing", jobId);

                // 1. Fetch product sales and stock data from Pet Store API
                // TODO: Replace with real API calls and handle pagination if needed
                JsonNode salesData = fetchPetStoreApi("/store/inventory");
                JsonNode productData = fetchPetStoreApi("/pet/findByStatus?status=available");

                // 2. Analyze KPIs (Mocked for prototype)
                ReportSummary summary = analyzePerformanceMetrics(salesData, productData);

                // 3. Generate report metadata and store
                String reportId = generateReportId(request.getExtractionDate());
                ReportMetadata reportMetadata = new ReportMetadata(
                        reportId,
                        Instant.now(),
                        summary,
                        "/prototype/api/reports/" + reportId + "/download"
                );
                reports.put(reportId, reportMetadata);
                logger.info("Report {} generated and stored", reportId);

                // 4. Send email with report (mocked)
                sendReportEmail(reportMetadata);

                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Job {} completed successfully", jobId);

            } catch (Exception e) {
                logger.error("Job {} failed with exception: {}", jobId, e.getMessage(), e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });

        StartResponse response = new StartResponse("started", "Data extraction and analysis workflow initiated");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /prototype/api/reports/latest
     * Retrieve the most recent generated report summary metadata.
     */
    @GetMapping(path = "/reports/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportMetadata> getLatestReport() {
        logger.info("Fetching latest report");

        return reports.values().stream()
                .max((r1, r2) -> r1.getGeneratedAt().compareTo(r2.getGeneratedAt()))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No reports found"));
    }

    /**
     * GET /prototype/api/reports/{reportId}/download
     * Download the detailed report (PDF).
     * For prototype: returns a placeholder PDF content as byte array.
     */
    @GetMapping(path = "/reports/{reportId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReport(@PathVariable("reportId") @NotBlank String reportId) {
        logger.info("Download requested for reportId={}", reportId);

        ReportMetadata metadata = reports.get(reportId);
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }

        // TODO: Replace with real generated PDF report bytes
        byte[] placeholderPdf = generatePlaceholderPdf(reportId, metadata.getSummary());

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"report-" + reportId + ".pdf\"")
                .body(placeholderPdf);
    }

    // --- Helper methods and DTO classes ---

    private JsonNode fetchPetStoreApi(String path) throws Exception {
        String url = "https://petstore.swagger.io/v2" + path;
        logger.info("Fetching Pet Store API data from {}", url);
        String response = restTemplate.getForObject(URI.create(url), String.class);
        if (response == null) {
            throw new Exception("Empty response from Pet Store API");
        }
        return objectMapper.readTree(response);
    }

    /**
     * Mock analysis logic for KPIs (sales volume, revenue, inventory turnover).
     * In this prototype, we generate some dummy summary data.
     */
    private ReportSummary analyzePerformanceMetrics(JsonNode salesData, JsonNode productData) {
        logger.info("Analyzing performance metrics (mock)");

        // TODO: Implement real KPI calculations based on fetched data

        ReportSummary summary = new ReportSummary();

        summary.setTopSellingProducts(new ReportSummary.ProductPerformance[]{
                new ReportSummary.ProductPerformance(101, "Pet Food", 5000),
                new ReportSummary.ProductPerformance(102, "Pet Toys", 3000)
        });

        summary.setRestockItems(new ReportSummary.InventoryStatus[]{
                new ReportSummary.InventoryStatus(103, "Pet Shampoo", 10)
        });

        summary.setPerformanceInsights("Sales increased by 15% compared to last week.");

        return summary;
    }

    /**
     * Generate a report id based on extraction date, e.g. "2024W22"
     */
    private String generateReportId(String extractionDate) {
        // TODO: Improve to calculate week number from date string
        return extractionDate.replaceAll("-", "") + "W22"; // Simplified placeholder
    }

    /**
     * Mock sending email with report.
     */
    private void sendReportEmail(ReportMetadata reportMetadata) {
        // TODO: Implement real email sending via email service
        logger.info("Mock sending email to victoria.sagdieva@cyoda.com with reportId={}", reportMetadata.getReportId());
    }

    /**
     * Generate a simple placeholder PDF binary content.
     */
    private byte[] generatePlaceholderPdf(String reportId, ReportSummary summary) {
        String content = "Product Performance Report: " + reportId + "\n\n" +
                "Top Selling Products:\n" +
                "- Pet Food (Sales Volume: 5000)\n" +
                "- Pet Toys (Sales Volume: 3000)\n\n" +
                "Restock Items:\n" +
                "- Pet Shampoo (Stock Level: 10)\n\n" +
                "Insights:\n" +
                summary.getPerformanceInsights();

        // For prototype just return bytes of text pretending to be PDF content
        return content.getBytes();
    }

    // --- DTO Classes ---

    @Data
    public static class ExtractionRequest {
        @NotBlank
        private String extractionDate;
    }

    @Data
    public static class StartResponse {
        private final String status;
        private final String message;
    }

    @Data
    public static class JobStatus {
        private final String status;
        private final Instant timestamp;
    }

    @Data
    public static class ReportMetadata {
        private final String reportId;
        private final Instant generatedAt;
        private final ReportSummary summary;
        private final String reportDownloadLink;
    }

    @Data
    public static class ReportSummary {
        private ProductPerformance[] topSellingProducts;
        private InventoryStatus[] restockItems;
        private String performanceInsights;

        @Data
        public static class ProductPerformance {
            private final int productId;
            private final String name;
            private final int salesVolume;
        }

        @Data
        public static class InventoryStatus {
            private final int productId;
            private final String name;
            private final int stockLevel;
        }
    }

    // --- Minimal Exception Handler for ResponseStatusException ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}
```