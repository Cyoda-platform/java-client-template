package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private static final String FAKE_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Books";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Report> reportStorage = new ConcurrentHashMap<>();

    @PostMapping("/books/analyze") // must be first
    public ReportStatus analyzeBooks(@RequestBody @Valid AnalysisCriteria criteria) {
        String jobId = UUID.randomUUID().toString();
        reportStorage.put(jobId, new Report("processing", null));

        CompletableFuture.runAsync(() -> {
            try {
                String response = restTemplate.getForObject(FAKE_API_URL, String.class);
                JsonNode books = objectMapper.readTree(response);
                // TODO: Implement actual data analysis logic here
                Report report = new Report("completed", "Book analysis report content...");
                reportStorage.put(jobId, report);
                log.info("Book data analyzed successfully for jobId: {}", jobId);
            } catch (Exception e) {
                log.error("Error analyzing book data for jobId: {}", jobId, e);
                reportStorage.put(jobId, new Report("failed", null));
            }
        });

        return new ReportStatus(jobId, "processing");
    }

    @GetMapping("/reports/{reportId}") // must be first
    public Report getReport(@PathVariable @NotBlank String reportId) {
        Report report = reportStorage.get(reportId);
        if (report == null || "processing".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not available");
        }
        return report;
    }

    @PostMapping("/reports/send") // must be first
    public SendStatus sendReport(@RequestBody @Valid SendRequest request) {
        Report report = reportStorage.get(request.getReportId());
        if (report == null || !"completed".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not available for sending");
        }
        // TODO: Implement actual email sending logic here
        log.info("Report sent successfully to recipients: {}", request.getRecipients());
        return new SendStatus("success", "Report sent successfully");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        return new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalysisCriteria {
        @Min(1)
        private int popularityThreshold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportStatus {
        private String reportId;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Report {
        private String status;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SendRequest {
        @NotBlank
        private String reportId;
        @Size(min = 1)
        private String[] recipients;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SendStatus {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String status;
        private String error;
    }
}