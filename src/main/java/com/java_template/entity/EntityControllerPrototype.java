import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, UserData> userDataStore = new ConcurrentHashMap<>();
    private final Map<String, ReportData> reportDataStore = new ConcurrentHashMap<>();

    @PostMapping("/users/fetch")
    public ResponseEntity<String> fetchAndStoreUserData(@RequestBody @Valid FetchRequest request) {
        try {
            String response = restTemplate.getForObject(request.getApiUrl(), String.class);
            JsonNode users = objectMapper.readTree(response);
            users.forEach(user -> {
                String userId = user.get("id").asText();
                userDataStore.put(userId, new UserData(userId, user.toString()));
            });

            logger.info("Data fetched and stored successfully");
            return ResponseEntity.ok("Data fetched and stored successfully");
        } catch (Exception e) {
            logger.error("Error fetching data", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error fetching data", e);
        }
    }

    @PostMapping("/reports/generate")
    public ResponseEntity<ReportResponse> generateMonthlyReport(@RequestBody @Valid ReportRequest request) {
        String reportId = "report-" + request.getMonth();

        CompletableFuture.runAsync(() -> {
            // TODO: Implement actual report generation logic
            reportDataStore.put(reportId, new ReportData(reportId, "Report content for " + request.getMonth()));
            logger.info("Report generated for month: {}", request.getMonth());
        });

        return ResponseEntity.ok(new ReportResponse("success", reportId));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportData> retrieveReport(@PathVariable String reportId) {
        ReportData reportData = reportDataStore.get(reportId);
        if (reportData != null) {
            return ResponseEntity.ok(reportData);
        } else {
            logger.error("Report not found: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
    }

    @PostMapping("/reports/send")
    public ResponseEntity<String> sendReportToAdmin(@RequestBody @Valid SendReportRequest request) {
        // TODO: Implement email sending logic
        logger.info("Report {} sent to admin email: {}", request.getReportId(), request.getAdminEmail());
        return ResponseEntity.ok("Report sent to admin email successfully");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class UserData {
        private String userId;
        private String data;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportData {
        private String reportId;
        private String content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResponse {
        private String status;
        private String reportId;
    }

    @Data
    @NoArgsConstructor
    static class FetchRequest {
        @NotBlank
        private String apiUrl;
    }

    @Data
    @NoArgsConstructor
    static class ReportRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Month should be in the format yyyy-MM")
        private String month;
    }

    @Data
    @NoArgsConstructor
    static class SendReportRequest {
        @NotBlank
        private String reportId;
        @NotBlank
        @Pattern(regexp = ".+@.+\\..+", message = "Email should be valid")
        private String adminEmail;
    }
}