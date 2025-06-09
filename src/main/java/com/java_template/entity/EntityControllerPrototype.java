import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/data/retrieve") // must be first
    public Map<String, Object> retrieveData(@RequestBody @Valid RetrieveDataRequest request) {
        log.info("Retrieving data for date: {}", request.getDate());

        // TODO: Implement actual API call to Pet Store API
        CompletableFuture.runAsync(() -> {
            String jobId = "job-" + System.currentTimeMillis();
            entityJobs.put(jobId, new JobStatus("processing", request.getDate()));
            // Mock processing logic
            try {
                Thread.sleep(2000); // Simulate processing time
                entityJobs.put(jobId, new JobStatus("completed", request.getDate()));
                log.info("Data retrieval job completed: {}", jobId);
            } catch (InterruptedException e) {
                log.error("Error processing job: {}", jobId, e);
            }
        });

        return Map.of("status", "success", "message", "Data retrieval initiated");
    }

    @PostMapping("/analysis/perform") // must be first
    public Map<String, Object> performAnalysis(@RequestBody @Valid AnalysisRequest request) {
        log.info("Performing analysis on data: {}", request.getData());

        // TODO: Implement actual analysis logic
        JsonNode analysisResults = objectMapper.createObjectNode()
                .put("highPerformingProducts", "product1,product2")
                .put("lowStockProducts", "product3")
                .put("trends", "upward");

        return Map.of(
                "status", "success",
                "message", "Analysis complete",
                "analysis", analysisResults
        );
    }

    @PostMapping("/report/generate") // must be first
    public Map<String, Object> generateReport(@RequestBody @Valid ReportRequest request) {
        log.info("Generating report based on analysis: {}", request.getAnalysis());

        // TODO: Implement actual report generation logic
        String reportUrl = "http://example.com/report/123";

        return Map.of(
                "status", "success",
                "message", "Report generated successfully",
                "reportUrl", reportUrl
        );
    }

    @GetMapping("/report/latest") // must be first
    public Map<String, Object> getLatestReport() {
        log.info("Fetching latest report");

        // TODO: Implement logic to fetch latest report
        String reportUrl = "http://example.com/report/latest";

        return Map.of(
                "status", "success",
                "reportUrl", reportUrl
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Error occurred: {}", ex.getStatusCode().toString(), ex);
        return Map.of("error", ex.getStatusCode().toString());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class RetrieveDataRequest {
        @NotBlank
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalysisRequest {
        @NotNull
        private JsonNode data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportRequest {
        @NotNull
        private JsonNode analysis;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class JobStatus {
        @NotBlank
        private String status;
        @NotBlank
        private String requestedAt;
    }
}