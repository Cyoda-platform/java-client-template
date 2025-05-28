```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/batch")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory user storage: userId -> User
    private final Map<Integer, User> userStorage = new ConcurrentHashMap<>();

    // In-memory monthly reports storage: month (yyyy-MM) -> MonthlyReport
    private final Map<String, MonthlyReport> monthlyReports = new ConcurrentHashMap<>();

    // In-memory batch job tracking: jobId -> JobStatus
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String USER_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Users";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class BatchProcessRequest {
        // Optional: date to trigger batch, defaults to today if null or empty
        private String triggerDate;
    }

    @Data
    @AllArgsConstructor
    static class BatchProcessResponse {
        private String status;
        private String message;
        private String batchId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class User {
        private Integer id;
        private String userName;
        private String email;
        // Additional fields can be added here as needed
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MonthlyReport {
        private String month; // e.g. "2024-06"
        private String reportUrl; // TODO: Replace with actual storage URL/path when implemented
        private int totalUsers;
        private int newUsers;
        private Map<String, Object> otherStats = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    static class JobStatus {
        private String status; // e.g. "processing", "completed", "failed"
        private OffsetDateTime requestedAt;
    }

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * POST /batch/processUsers
     * Triggers batch processing workflow asynchronously.
     */
    @PostMapping("/processUsers")
    public ResponseEntity<BatchProcessResponse> processUsersBatch(@RequestBody(required = false) BatchProcessRequest request) {
        String batchId = UUID.randomUUID().toString();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        entityJobs.put(batchId, new JobStatus("processing", requestedAt));

        String triggerDateStr = (request != null) ? request.getTriggerDate() : null;
        LocalDate triggerDate;
        try {
            if (StringUtils.hasText(triggerDateStr)) {
                triggerDate = LocalDate.parse(triggerDateStr);
            } else {
                triggerDate = LocalDate.now();
            }
        } catch (Exception e) {
            log.error("Invalid triggerDate format: {}", triggerDateStr);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid triggerDate format, expected YYYY-MM-DD");
        }

        log.info("Batch processing started with batchId={} for date={}", batchId, triggerDate);

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> {
            try {
                fetchTransformStoreUsers();
                generateAndStoreMonthlyReport(triggerDate);
                sendReportEmail(triggerDate);
                entityJobs.put(batchId, new JobStatus("completed", OffsetDateTime.now()));
                log.info("Batch processing completed successfully for batchId={}", batchId);
            } catch (Exception ex) {
                entityJobs.put(batchId, new JobStatus("failed", OffsetDateTime.now()));
                log.error("Batch processing failed for batchId={}", batchId, ex);
            }
        });

        return ResponseEntity.ok(new BatchProcessResponse("processing_started", "Batch processing initiated", batchId));
    }

    /**
     * GET /users
     * Returns stored user data with optional pagination.
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page and size must be positive integers");
        }

        List<User> allUsers = new ArrayList<>(userStorage.values());
        int totalUsers = allUsers.size();
        int totalPages = (int) Math.ceil((double) totalUsers / size);

        if (page > totalPages && totalPages != 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page number out of range");
        }

        int fromIndex = Math.min((page - 1) * size, totalUsers);
        int toIndex = Math.min(fromIndex + size, totalUsers);
        List<User> pageUsers = allUsers.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("users", pageUsers);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", totalPages);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /reports/monthly
     * Returns monthly report metadata and link.
     */
    @GetMapping("/reports/monthly")
    public ResponseEntity<MonthlyReport> getMonthlyReport(@RequestParam String month) {
        MonthlyReport report = monthlyReports.get(month);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report for month " + month + " not found");
        }
        return ResponseEntity.ok(report);
    }

    // -------------------- Internal Helpers --------------------

    /**
     * Fetch users from Fakerest API, transform and store them.
     */
    private void fetchTransformStoreUsers() throws IOException {
        log.info("Fetching users from Fakerest API: {}", USER_API_URL);

        String rawJson = restTemplate.getForObject(URI.create(USER_API_URL), String.class);
        if (rawJson == null) {
            log.error("Failed to fetch users: response was null");
            throw new IOException("Empty response from Fakerest API");
        }

        JsonNode rootNode = objectMapper.readTree(rawJson);
        if (!rootNode.isArray()) {
            log.error("Unexpected JSON format: expected array");
            throw new IOException("Unexpected JSON format from Fakerest API");
        }

        int addedCount = 0;
        for (JsonNode userNode : rootNode) {
            User user = mapJsonNodeToUser(userNode);
            // Save user to in-memory storage (overwrite if exists)
            userStorage.put(user.getId(), user);
            addedCount++;
        }
        log.info("Fetched and stored {} users", addedCount);
    }

    /**
     * Maps raw JSON node from Fakerest API to User entity.
     * Transforms data if needed (currently passes through id, userName, email).
     */
    private User mapJsonNodeToUser(JsonNode userNode) {
        // Fields from Fakerest User sample:
        // id, userName, password, email, createdDate, ...
        Integer id = userNode.path("id").asInt();
        String userName = userNode.path("userName").asText("");
        String email = userNode.path("email").asText("");

        // TODO: Add any transformation logic here if needed

        return new User(id, userName, email);
    }

    /**
     * Generate a simple monthly report and store it.
     * For prototype, generates very basic stats.
     */
    private void generateAndStoreMonthlyReport(LocalDate triggerDate) {
        String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());

        int totalUsers = userStorage.size();
        // TODO: Implement logic to calculate newUsers since last report (mocked here)
        int newUsers = (int) (Math.random() * 20); // placeholder random for prototype

        MonthlyReport report = new MonthlyReport();
        report.setMonth(monthKey);
        report.setTotalUsers(totalUsers);
        report.setNewUsers(newUsers);
        report.setReportUrl("https://example.com/reports/" + monthKey + ".pdf"); // TODO: replace with real report storage path
        report.getOtherStats().put("exampleStat", "prototypeValue");

        monthlyReports.put(monthKey, report);
        log.info("Monthly report generated for {}: totalUsers={}, newUsers={}", monthKey, totalUsers, newUsers);
    }

    /**
     * Send report email to admin.
     * For prototype, just logs the action.
     */
    private void sendReportEmail(LocalDate triggerDate) {
        String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());
        MonthlyReport report = monthlyReports.get(monthKey);

        if (report == null) {
            log.warn("No report found for month {} - skipping email send", monthKey);
            return;
        }

        // TODO: Integrate real email sending logic here
        log.info("[MOCK] Sending monthly report email to admin with report URL: {}", report.getReportUrl());
    }

    // -------------------- Minimal Error Handling --------------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
    }
}
```
