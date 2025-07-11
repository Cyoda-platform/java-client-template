package com.java_template.controller;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/entity/report")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "ReportEntity";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<GenerateReportResponse>> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        logger.info("Received report generation request: dataUrl={}, subscribersCount={}, reportType={}",
                request.getDataUrl(), request.getSubscribers().size(), request.getReportType());

        ObjectNode data = objectMapper.createObjectNode();
        data.put("dataUrl", request.getDataUrl());
        data.put("reportType", request.getReportType());
        ArrayNode subs = objectMapper.createArrayNode();
        request.getSubscribers().forEach(subs::add);
        data.set("subscribers", subs);

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
        logger.info("Fetching report status for reportId={}", reportId);

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // Request DTO
    public static class GenerateReportRequest {
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "dataUrl must start with http:// or https://")
        private String dataUrl;

        @NotBlank
        private String reportType;

        @NotEmpty
        private List<@Email(message = "Invalid email address") String> subscribers;

        public String getDataUrl() {
            return dataUrl;
        }

        public void setDataUrl(String dataUrl) {
            this.dataUrl = dataUrl;
        }

        public String getReportType() {
            return reportType;
        }

        public void setReportType(String reportType) {
            this.reportType = reportType;
        }

        public List<String> getSubscribers() {
            return subscribers;
        }

        public void setSubscribers(List<String> subscribers) {
            this.subscribers = subscribers;
        }
    }

    // Response DTOs
    public static class GenerateReportResponse {
        private final String status;
        private final String message;
        private final String reportId;

        public GenerateReportResponse(String status, String message, String reportId) {
            this.status = status;
            this.message = message;
            this.reportId = reportId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getReportId() {
            return reportId;
        }
    }

    public static class ReportStatusResponse {
        private String reportId;
        private String generatedAt;
        private String status;
        private Map<String, Object> reportSummary;

        public String getReportId() {
            return reportId;
        }

        public void setReportId(String reportId) {
            this.reportId = reportId;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(String generatedAt) {
            this.generatedAt = generatedAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getReportSummary() {
            return reportSummary;
        }

        public void setReportSummary(Map<String, Object> reportSummary) {
            this.reportSummary = reportSummary;
        }
    }
}