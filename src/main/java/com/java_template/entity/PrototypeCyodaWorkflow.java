To refactor the code by moving logic from the controller methods to the `process{entity_name}` workflow functions, we need to extract any asynchronous logic related to entity processing and place it in these functions. This will help in making the controllers cleaner and more focused on managing HTTP requests and responses.

Here's how you can refactor the code:

1. **Move Asynchronous Logic to Workflow Functions:** Identify any asynchronous logic, especially those that modify the entity state or involve supplementary data retrieval or processing, and move them to the workflow functions.

2. **Use `ObjectNode` for Entity Modification:** Since the entity is an `ObjectNode`, use methods like `put()` to modify its state.

Here's the updated code reflecting these changes:

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
                // Perform asynchronous book data analysis
                entityService.addItem(
                    "Books",
                    ENTITY_VERSION,
                    createAnalysisData(criteria),
                    processBooks // Use the workflow function
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
                .thenApply(item -> {
                    Report report = objectMapper.convertValue(item, Report.class);
                    if (!"completed".equals(report.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not available for sending");
                    }
                    // Send logic moved to workflow
                    entityService.addItem(
                        "SendReport",
                        ENTITY_VERSION,
                        createSendData(report, request),
                        processSendReport // Use the workflow function
                    ).join();
                    return new SendStatus("success", "Report sent successfully");
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        return new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
    }

    // Workflow function for processing Books entity
    private Function<ObjectNode, ObjectNode> processBooks = books -> {
        // Asynchronously analyze books data
        CompletableFuture.runAsync(() -> {
            // Simulate analysis logic
            books.put("status", "completed");
            books.put("content", "Book analysis completed.");
            log.info("Books analysis completed");
        }).join();
        return books;
    };

    // Workflow function for processing SendReport entity
    private Function<ObjectNode, ObjectNode> processSendReport = sendData -> {
        // Asynchronously send report
        CompletableFuture.runAsync(() -> {
            // Simulate sending logic
            sendData.put("status", "sent");
            log.info("Report sent successfully to recipients.");
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

### Key Changes:

- **`processBooks` and `processSendReport` Workflow Functions:** These functions now handle the asynchronous logic for processing books and sending reports respectively. They modify the `ObjectNode` entity state directly using methods like `put()`.

- **Entity Data Preparation:** Helper methods `createAnalysisData` and `createSendData` are used to prepare `ObjectNode` instances for processing, keeping the controller methods clean.

This refactoring moves asynchronous logic from the controller to workflow functions, making the controller more focused and the codebase more maintainable.