To adhere to the requirement of moving asynchronous logic into the `process{entity_name}` function and reducing the logic in the controller, we need to refactor the code such that the asynchronous operations are handled within the workflow function. This will make the controller more streamlined and delegate the processing to the workflow function.

Here's how you can refactor the code to achieve this:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api/data")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeData(@RequestBody @Valid DataRequest dataRequest) {
        String jobId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = LocalDateTime.now();
        ObjectNode initialResult = objectMapper.createObjectNode();
        initialResult.put("status", "processing");
        initialResult.put("requestedAt", requestedAt.toString());

        CompletableFuture<UUID> uuidFuture = entityService.addItem(
                "AnalysisResult",
                ENTITY_VERSION,
                initialResult,
                (entity) -> processAnalysisResult(entity, dataRequest.getDataUrl()) // Pass the workflow function with additional data
        );

        return ResponseEntity.ok(new AnalysisResponse("Data analysis initiated", jobId));
    }

    private CompletableFuture<ObjectNode> processAnalysisResult(ObjectNode entity, String dataUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode data = objectMapper.readTree(new URL(dataUrl));
                // TODO: Implement actual data analysis logic
                logger.info("Data analyzed: {}", data.toString());
                entity.put("status", "completed");
                entity.put("report", "Sample analysis result");
            } catch (IOException e) {
                logger.error("Failed to analyze data", e);
                entity.put("status", "failed");
            }
            return entity;
        });
    }

    @GetMapping("/report/{analysisId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable @NotBlank String analysisId) {
        CompletableFuture<JsonNode> itemFuture = entityService.getItem(
                "AnalysisResult",
                ENTITY_VERSION,
                UUID.fromString(analysisId)
        );

        JsonNode resultNode = itemFuture.join();
        if (resultNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found");
        }

        AnalysisResult result = objectMapper.convertValue(resultNode, AnalysisResult.class);
        return ResponseEntity.ok(new ReportResponse(analysisId, result.getStatus(), result.getReport()));
    }

    @PostMapping("/send-report")
    public ResponseEntity<MessageResponse> sendReport(@RequestBody @Valid EmailRequest emailRequest) {
        // TODO: Implement email sending logic
        logger.info("Sending report to email: {}", emailRequest.getEmail());
        return ResponseEntity.ok(new MessageResponse("Report sent successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), "An error occurred"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DataRequest {
        @NotNull
        @NotBlank
        @Size(max = 255)
        private String dataUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AnalysisResponse {
        private String message;
        private String analysisId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReportResponse {
        private String analysisId;
        private String status;
        private String report;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EmailRequest {
        @NotNull
        @NotBlank
        private String analysisId;

        @NotNull
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MessageResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AnalysisResult {
        private String status;
        private LocalDateTime requestedAt;
        private String report;
    }
}
```

### Key Changes:
1. **Workflow Function Implementation**: We moved the asynchronous data analysis logic into the `processAnalysisResult` function. This function takes `ObjectNode` as a parameter and modifies it directly.

2. **Controller Simplification**: The controller now primarily handles the request initiation and response, while the detailed processing logic is handled by the workflow function. 

3. **Asynchronous Processing**: The `processAnalysisResult` function utilizes `CompletableFuture.supplyAsync` to perform asynchronous processing of the data analysis.

This refactoring delegates the responsibility of processing and modifying the entity to a dedicated function, making the controller less cluttered and more focused on handling HTTP requests and responses.