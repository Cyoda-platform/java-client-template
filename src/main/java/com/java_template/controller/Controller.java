package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch-activities")
    public CompletableFuture<ApiResponse> fetchActivities(@RequestBody @Valid FetchRequest fetchRequest) {
        String apiUrl = fetchRequest.getApiUrl();
        try {
            String response = ""; // Simulate fetching data
            JsonNode activities = new ObjectMapper().readTree(response);
            return entityService.addItem("Activity", ENTITY_VERSION, activities)
                .thenApply(id -> {
                    logger.info("Activities fetched and stored successfully.");
                    return new ApiResponse("success", "Activities fetched and stored successfully.");
                });
        } catch (Exception e) {
            logger.error("Error fetching activities: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private JsonNode fetchSupplementaryData() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode supplementaryData = objectMapper.createObjectNode();
        supplementaryData.put("exampleKey", "exampleValue");
        return supplementaryData;
    }

    @PostMapping("/analyze-activities")
    public CompletableFuture<ApiResponse> analyzeActivities(@RequestBody @Valid AnalyzeRequest analyzeRequest) {
        String analysisType = analyzeRequest.getAnalysisType();
        return entityService.getItems("Activity", ENTITY_VERSION)
            .thenApply(activities -> {
                logger.info("Activities analyzed successfully.");
                return new ApiResponse("success", "Activities analyzed successfully.", "example-pattern", "additional details");
            });
    }

    @GetMapping("/get-report")
    public CompletableFuture<ReportResponse> getReport() {
        return entityService.getItems("Report", ENTITY_VERSION)
            .thenApply(reports -> {
                JsonNode report = reports.get(0); // Simplified logic
                logger.info("Daily report retrieved successfully.");
                return new ReportResponse("2023-10-10", "Daily report summary", "Highlighted trends", "Notable anomalies");
            });
    }

    @PostMapping("/send-report")
    public ApiResponse sendReport(@RequestBody @Valid SendReportRequest sendReportRequest) {
        String adminEmail = sendReportRequest.getAdminEmail();
        CompletableFuture.runAsync(() -> {
            logger.info("Report sent to {}", adminEmail);
        });
        logger.info("Report sent successfully to admin.");
        return new ApiResponse("success", "Report sent successfully to admin.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getReason());
        return new ApiResponse("error", ex.getStatusCode().toString());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchRequest {
        @NotBlank
        private String apiUrl;
        @NotBlank
        private String startDate;
        @NotBlank
        private String endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeRequest {
        @NotBlank
        private String analysisType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SendReportRequest {
        @NotBlank
        private String adminEmail;
        @NotBlank
        private String reportDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ApiResponse {
        private String status;
        private String message;
        private String pattern;
        private String details;

        public ApiResponse(String status, String message) {
            this.status = status;
            this.message = message;
            this.pattern = null;
            this.details = null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportResponse {
        private String reportDate;
        private String summary;
        private String trends;
        private String anomalies;
    }
}
