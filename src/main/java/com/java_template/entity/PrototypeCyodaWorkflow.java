```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Slf4j
@Validated
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/books/analyze")
    public CompletableFuture<ReportStatus> analyzeBooks(@RequestBody @Valid AnalysisCriteria criteria) {
        String jobId = UUID.randomUUID().toString();

        return CompletableFuture.runAsync(() -> {
            try {
                ObjectNode analysisData = createAnalysisData(criteria);
                entityService.addItem(
                    "Books",
                    ENTITY_VERSION,
                    analysisData,
                    processBooks
                ).join();
                log.info("Book data analysis initiated for jobId: {}", jobId);
            } catch (Exception e) {
                log.error("Error initiating book data analysis for jobId: {}", jobId, e);
            }
        }).thenApply(v -> new ReportStatus(jobId, "processing"));
    }

    @GetMapping("/reports/{reportId}")
    public CompletableFuture<Report> getReport(@PathVariable @NotBlank String reportId) {
        return entityService.getItem("Report", ENTITY_VERSION, UUID.fromString(reportId))
                .thenApply(item -> {
                    Report report = objectMapper.convertValue(item, Report.class);
                    if ("processing".equals(report.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not available");
                    }
                    return report;
                });
    }

    @PostMapping("/reports/send")
    public CompletableFuture<SendStatus> sendReport(@RequestBody @Valid SendRequest request) {
        return entityService.getItem("Report", ENTITY_VERSION, UUID.fromString(request.getReportId()))
                .thenCompose(item -> {
                    Report report = objectMapper.convertValue(item, Report.class);
                    if (!"completed".equals(report.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not available for sending");
                    }
                    ObjectNode sendData = createSendData(report, request);
                    return entityService.addItem(
                        "SendReport",
                        ENTITY_VERSION,
                        sendData,
                        processSendReport
                    ).thenApply(v -> new SendStatus("success", "Report sent successfully"));
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        return new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
    }

    // Workflow function for processing Books entity
    private Function<ObjectNode, ObjectNode> processBooks = books -> {
        CompletableFuture.runAsync(() -> {
            try {
                // Example of asynchronous processing
                books.put("status", "completed");
                books.put("content", "Book analysis completed.");
                log.info("Books analysis completed");
            } catch (Exception e) {
                log.error("Error processing books", e);
            }
        }).join();
        return books;
    };

    // Workflow function for processing SendReport entity
    private Function<ObjectNode, ObjectNode> processSendReport = sendData -> {
        CompletableFuture.runAsync(() -> {
            try {
                // Example of asynchronous sending logic
                sendData.put("status", "sent");
                log.info("Report sent successfully to recipients.");
            } catch (Exception e) {
                log.error("Error sending report", e);
            }
        }).join();
        return sendData;
    };

    private ObjectNode createAnalysisData(AnalysisCriteria criteria) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("popularityThreshold", criteria.getPopularityThreshold());
        return data;
    }

    private ObjectNode createSendData(Report report, SendRequest request) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("reportContent", report.getContent());
        data.put("recipients", String.join(",", request.getRecipients()));
        return data;
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
```
