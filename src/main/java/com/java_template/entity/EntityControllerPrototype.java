import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Report> reportStore = new HashMap<>();

    @PostMapping("/data/analyze")
    public ResponseEntity<Map<String, String>> analyzeData(@RequestBody @Valid DataAnalysisRequest request) {
        String csvUrl = request.getCsvUrl();
        logger.info("Starting data analysis for URL: {}", csvUrl);

        // TODO: Implement actual data retrieval and analysis logic
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                reportStore.put("latest", new Report("Report Content", new Date()));
                logger.info("Data analysis completed for URL: {}", csvUrl);
            } catch (InterruptedException e) {
                logger.error("Error during data analysis", e);
            }
        });

        return ResponseEntity.ok(Collections.singletonMap("message", "Data analysis started."));
    }

    @PostMapping("/report/send")
    public ResponseEntity<Map<String, String>> sendReport(@RequestBody @Valid ReportRequest reportRequest) {
        logger.info("Sending report to subscribers: {}", reportRequest.getSubscribers());

        // TODO: Implement actual email sending logic
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                logger.info("Report sent to subscribers: {}", reportRequest.getSubscribers());
            } catch (InterruptedException e) {
                logger.error("Error while sending report", e);
            }
        });

        return ResponseEntity.ok(Collections.singletonMap("message", "Report sending initiated."));
    }

    @GetMapping("/report")
    public ResponseEntity<Report> getReport() {
        Report report = reportStore.get("latest");
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report found");
        }
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getStatusCode().toString());
        errorResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Report {
        private String content;
        private Date generatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReportRequest {
        @NotBlank
        private String reportFormat;
        @Size(min = 1)
        @Email
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DataAnalysisRequest {
        @NotBlank
        private String csvUrl;
    }
}