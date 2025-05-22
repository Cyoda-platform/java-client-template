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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<UUID, byte[]> reportPdfs = new ConcurrentHashMap<>();

    @PostMapping("/data-extraction")
    public ResponseEntity<TriggerResponse> triggerDataExtraction(@RequestBody(required = false) @Valid TriggerRequest request) {
        Instant requestedAt = Instant.now();
        logger.info("Received data extraction trigger request at {}", requestedAt);

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
            logger.info("Starting async data extraction job {} for date {}", jobId, triggerDate);
            String url = "https://petstore.swagger.io/v2/store/inventory";
            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode inventoryNode = objectMapper.readTree(rawJson);
            JsonNode salesData = getMockSalesData();
            ReportSummary summary = analyzeKpis(inventoryNode, salesData, triggerDate);

            // Persist summary entity via entityService
            CompletableFuture<UUID> futureId = entityService.addItem(
                    "ReportSummary",
                    ENTITY_VERSION,
                    summary
            );
            UUID technicalId = futureId.join();

            // Generate PDF report and store in local cache keyed by technicalId
            byte[] pdfReport = generateMockPdfReport(summary);
            reportPdfs.put(technicalId, pdfReport);

            logger.info("Pretending to email report to victoria.sagdieva@cyoda.com for job {}", jobId);
            logger.info("Data extraction job {} completed successfully", jobId);
        } catch (IOException e) {
            logger.error("Failed to process data extraction job {}: {}", jobId, e.getMessage(), e);
        }
    }

    @GetMapping("/reports/latest/summary")
    public ResponseEntity<ReportSummary> getLatestReportSummary() {
        // Retrieve all ReportSummary entities
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report summary available");
        }

        // Find the ReportSummary with the latest reportDate
        ReportSummary latestSummary = null;
        String latestDate = null;
        for (JsonNode node : items) {
            // Map node to ReportSummary
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
        // Retrieve all ReportSummary entities to find latest technicalId
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("ReportSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report PDF available");
        }

        UUID latestId = null;
        String latestDate = null;

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

        // Fetch PDF from local cache
        byte[] pdf = reportPdfs.get(latestId);
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