```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory storage for demo purposes
    private final Map<UUID, ReportSummary> reportSummaries = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> reportPdfs = new ConcurrentHashMap<>();

    /**
     * POST /api/data-extraction
     * Trigger data extraction from Pet Store API, analyze KPIs, generate report, and email it.
     */
    @PostMapping("/data-extraction")
    public ResponseEntity<TriggerResponse> triggerDataExtraction(@RequestBody(required = false) TriggerRequest request) {
        Instant requestedAt = Instant.now();
        log.info("Received data extraction trigger request at {}", requestedAt);

        // Use provided date or default to current date
        String triggerDate = (request != null && request.getTriggerDate() != null)
                ? request.getTriggerDate()
                : Instant.now().toString();

        UUID jobId = UUID.randomUUID();
        // Fire-and-forget async processing
        processDataExtractionAsync(jobId, triggerDate);

        return ResponseEntity.accepted()
                .body(new TriggerResponse("started", "Data extraction and report generation initiated."));
    }

    @Async
    void processDataExtractionAsync(UUID jobId, String triggerDate) {
        // TODO: Improve error handling, retries, and resiliency for production
        try {
            log.info("Starting async data extraction job {} for date {}", jobId, triggerDate);

            // Fetch product data from Pet Store API (JSON format)
            String url = "https://petstore.swagger.io/v2/store/inventory";
            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode inventoryNode = objectMapper.readTree(rawJson);
            log.info("Fetched inventory data: {}", inventoryNode.toString());

            // TODO: Mock sales data fetch - Pet Store API does not provide sales, so we simulate it here
            JsonNode salesData = getMockSalesData();
            log.info("Using mocked sales data: {}", salesData.toString());

            // Analyze KPIs
            ReportSummary summary = analyzeKpis(inventoryNode, salesData, triggerDate);

            // Generate PDF report - placeholder byte array
            byte[] pdfReport = generateMockPdfReport(summary);

            // Store results in memory (mock persistence)
            reportSummaries.put(jobId, summary);
            reportPdfs.put(jobId, pdfReport);

            // Email report - TODO: implement actual email sending
            log.info("Pretending to email report to victoria.sagdieva@cyoda.com for job {}", jobId);

            log.info("Data extraction job {} completed successfully", jobId);
        } catch (IOException e) {
            log.error("Failed to process data extraction job {}: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * GET /api/reports/latest/summary
     * Returns summary data of the latest generated report.
     */
    @GetMapping("/reports/latest/summary")
    public ResponseEntity<ReportSummary> getLatestReportSummary() {
        // Return the most recent report by insertion order (mock)
        Optional<ReportSummary> latest = reportSummaries.values().stream()
                .max(Comparator.comparing(ReportSummary::getReportDate));
        if (latest.isPresent()) {
            return ResponseEntity.ok(latest.get());
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report summary available");
        }
    }

    /**
     * GET /api/reports/latest/download
     * Download the latest weekly report PDF.
     */
    @GetMapping(value = "/reports/latest/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadLatestReportPdf() {
        Optional<Map.Entry<UUID, byte[]>> latest = reportPdfs.entrySet().stream()
                .max(Comparator.comparing(entry -> reportSummaries.get(entry.getKey()).getReportDate()));

        if (latest.isPresent()) {
            byte[] pdf = latest.get().getValue();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"weekly_report.pdf\"")
                    .contentLength(pdf.length)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }
    }

    // --- Helper methods & mock data below ---

    /**
     * Mock sales data as Pet Store API does not provide sales info.
     */
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

    /**
     * Analyze KPIs from inventory and sales data.
     */
    private ReportSummary analyzeKpis(JsonNode inventoryNode, JsonNode salesData, String triggerDate) {
        // TODO: Implement real aggregation logic based on actual data structure
        // For prototype, we build a simple mock summary

        List<UnderperformingProduct> underperforming = new ArrayList<>();
        salesData.get("products").forEach(p -> {
            int salesVolume = p.get("salesVolume").asInt();
            if (salesVolume < 10) {
                underperforming.add(new UnderperformingProduct(p.get("productId").asText(), "Low sales volume"));
            }
        });

        return new ReportSummary(
                triggerDate,
                155, // mock total sales volume
                17200.0, // mock revenue
                3.2, // mock inventory turnover
                underperforming
        );
    }

    /**
     * Generates a mock PDF report as byte array.
     */
    private byte[] generateMockPdfReport(ReportSummary summary) {
        // TODO: Replace with real PDF generation logic
        String mockContent = "Weekly Report PDF\n" +
                "Date: " + summary.getReportDate() + "\n" +
                "Sales Volume: " + summary.getSalesVolume() + "\n" +
                "Revenue: " + summary.getRevenue() + "\n" +
                "Inventory Turnover: " + summary.getInventoryTurnover() + "\n" +
                "Underperforming Products: " + summary.getUnderperformingProducts().size();
        return mockContent.getBytes();
    }

    // --- Request and Response DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerRequest {
        private String triggerDate; // ISO-8601 string, optional
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
        private String reportDate;      // ISO-8601 date string
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

    // --- Minimal global exception handler for ResponseStatusException ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed with status {} and reason: {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}
```
