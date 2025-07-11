package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda/entity/report")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "ReportEntity";

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<GenerateReportResponse>> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        log.info("Received report generation request: dataUrl={}, subscribersCount={}, reportType={}",
                request.getDataUrl(), request.getSubscribers().size(), request.getReportType());

        // Build entity data object
        ObjectNode data = objectMapper.createObjectNode();
        data.put("dataUrl", request.getDataUrl());
        data.put("reportType", request.getReportType());
        ArrayNode subs = objectMapper.createArrayNode();
        request.getSubscribers().forEach(subs::add);
        data.set("subscribers", subs);
        // Initial status will be set and processing started in workflow function

        // Add item to external service without workflow function
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, data)
                .thenApply(id -> {
                    String jobId = id.toString();
                    GenerateReportResponse response = new GenerateReportResponse("processing", "Report generation started", jobId);
                    return ResponseEntity.accepted().body(response);
                });
    }

    @GetMapping("/{reportId}")
    public CompletableFuture<ResponseEntity<ReportStatusResponse>> getReportStatus(@PathVariable @NotBlank String reportId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(reportId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report ID format");
        }
        log.info("Fetching report status for reportId={}", reportId);

        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report ID not found");
                    }
                    ReportStatusResponse response = new ReportStatusResponse();
                    response.setReportId(reportId);
                    response.setGeneratedAt(item.path("requestedAt").asText(null));
                    response.setStatus(item.path("status").asText(null));
                    if (item.has("reportSummary") && !item.path("reportSummary").isNull()) {
                        response.setReportSummary(objectMapper.convertValue(item.get("reportSummary"), Map.class));
                    }
                    return ResponseEntity.ok(response);
                });
    }

    private Map<String, Object> analyzeCsvData(String csvData, String reportType) {
        Map<String, Object> result = new HashMap<>();
        try {
            String[] lines = csvData.split("\\r?\\n");
            result.put("totalRows", Math.max(0, lines.length - 1));
            result.put("reportType", reportType);
            result.put("generatedAt", Instant.now().toString());
            result.put("sampleData", lines.length > 1 ? lines[1] : "No data");
        } catch (Exception e) {
            log.error("Error analyzing CSV data: {}", e.getMessage(), e);
            result.put("error", "CSV analysis failed");
        }
        return result;
    }

    private void sendReportEmail(String jobId, List<String> subscribers, JsonNode reportSummary) {
        if (subscribers == null || subscribers.isEmpty()) {
            log.warn("[sendReportEmail][{}] No subscribers to notify", jobId);
            return;
        }
        log.info("[sendReportEmail][{}] Sending report to {} subscribers: {}", jobId, subscribers.size(), reportSummary);
        try {
            // Simulate sending email with delay
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        log.info("[sendReportEmail][{}] Email sent", jobId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        log.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    public static class GenerateReportRequest {
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "dataUrl must start with http:// or https://")
        private String dataUrl;

        @NotBlank
        private String reportType;

        @NotEmpty
        private List<@Email(message = "Invalid email address") String> subscribers;
    }

    @Data
    public static class GenerateReportResponse {
        private final String status;
        private final String message;
        private final String reportId;
    }

    @Data
    public static class ReportStatusResponse {
        private String reportId;
        private String generatedAt;
        private String status;
        private Map<String, Object> reportSummary;
    }
}
