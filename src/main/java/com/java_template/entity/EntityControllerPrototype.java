package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, ReportSummary> reportSummaries = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> reportPdfs = new ConcurrentHashMap<>();

    @PostMapping("/data-extraction")
    public ResponseEntity<TriggerResponse> triggerDataExtraction(@RequestBody(required = false) @Valid TriggerRequest request) {
        Instant requestedAt = Instant.now();
        log.info("Received data extraction trigger request at {}", requestedAt);

        String triggerDate = (request != null && request.getTriggerDate() != null)
                ? request.getTriggerDate()
                : Instant.now().toString();

        UUID jobId = UUID.randomUUID();
        processDataExtractionAsync(jobId, triggerDate);

        return ResponseEntity.accepted()
                .body(new TriggerResponse("started", "Data extraction and report generation initiated."));
    }

    @Async
    void processDataExtractionAsync(UUID jobId, String triggerDate) {
        try {
            log.info("Starting async data extraction job {} for date {}", jobId, triggerDate);
            String url = "https://petstore.swagger.io/v2/store/inventory";
            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode inventoryNode = objectMapper.readTree(rawJson);
            JsonNode salesData = getMockSalesData();
            ReportSummary summary = analyzeKpis(inventoryNode, salesData, triggerDate);
            byte[] pdfReport = generateMockPdfReport(summary);
            reportSummaries.put(jobId, summary);
            reportPdfs.put(jobId, pdfReport);
            log.info("Pretending to email report to victoria.sagdieva@cyoda.com for job {}", jobId);
            log.info("Data extraction job {} completed successfully", jobId);
        } catch (IOException e) {
            log.error("Failed to process data extraction job {}: {}", jobId, e.getMessage(), e);
        }
    }

    @GetMapping("/reports/latest/summary")
    public ResponseEntity<ReportSummary> getLatestReportSummary() {
        Optional<ReportSummary> latest = reportSummaries.values().stream()
                .max(Comparator.comparing(ReportSummary::getReportDate));
        if (latest.isPresent()) {
            return ResponseEntity.ok(latest.get());
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report summary available");
        }
    }

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
        log.error("Request failed with status {} and reason: {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}