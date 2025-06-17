import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api/data")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private Map<String, AnalysisResult> analysisResults = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/analyze") // must be first
    public ResponseEntity<AnalysisResponse> analyzeData(@RequestBody @Valid DataRequest dataRequest) {
        String jobId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = LocalDateTime.now();
        analysisResults.put(jobId, new AnalysisResult("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                JsonNode data = objectMapper.readTree(new URL(dataRequest.getDataUrl()));
                // TODO: Implement actual data analysis logic
                logger.info("Data analyzed: {}", data.toString());
                analysisResults.put(jobId, new AnalysisResult("completed", requestedAt, "Sample analysis result"));
            } catch (IOException e) {
                logger.error("Failed to analyze data", e);
                analysisResults.put(jobId, new AnalysisResult("failed", requestedAt));
            }
        });

        return ResponseEntity.ok(new AnalysisResponse("Data analysis initiated", jobId));
    }

    @GetMapping("/report/{analysisId}") // must be first
    public ResponseEntity<ReportResponse> getReport(@PathVariable @NotBlank String analysisId) {
        AnalysisResult result = analysisResults.get(analysisId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found");
        }

        return ResponseEntity.ok(new ReportResponse(analysisId, result.getStatus(), result.getReport()));
    }

    @PostMapping("/send-report") // must be first
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