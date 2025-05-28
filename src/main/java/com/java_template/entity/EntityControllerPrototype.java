package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/batch")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, User> userStorage = new ConcurrentHashMap<>();
    private final Map<String, MonthlyReport> monthlyReports = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private static final String USER_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Users";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class BatchProcessRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "triggerDate must be in YYYY-MM-DD format")
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MonthlyReport {
        private String month;
        private String reportUrl;
        private int totalUsers;
        private int newUsers;
        private Map<String,Object> otherStats = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private OffsetDateTime requestedAt;
    }

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/processUsers")
    public ResponseEntity<BatchProcessResponse> processUsersBatch(@RequestBody(required = false) @Valid BatchProcessRequest request) {
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

    @GetMapping("/users")
    public ResponseEntity<Map<String,Object>> getUsers(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page and size must be positive integers");
        }

        List<User> allUsers = new ArrayList<>(userStorage.values());
        int totalUsers = allUsers.size();
        int totalPages = (int)Math.ceil((double)totalUsers/size);

        if (page > totalPages && totalPages != 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page number out of range");
        }

        int fromIndex = Math.min((page - 1)*size, totalUsers);
        int toIndex = Math.min(fromIndex + size, totalUsers);
        List<User> pageUsers = allUsers.subList(fromIndex, toIndex);

        Map<String,Object> response = new HashMap<>();
        response.put("users", pageUsers);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", totalPages);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/monthly")
    public ResponseEntity<MonthlyReport> getMonthlyReport(
            @RequestParam @Pattern(regexp = "\\d{4}-\\d{2}", message = "month must be in YYYY-MM format") String month) {
        MonthlyReport report = monthlyReports.get(month);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report for month " + month + " not found");
        }
        return ResponseEntity.ok(report);
    }

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
            userStorage.put(user.getId(), user);
            addedCount++;
        }
        log.info("Fetched and stored {} users", addedCount);
    }

    private User mapJsonNodeToUser(JsonNode userNode) {
        Integer id = userNode.path("id").asInt();
        String userName = userNode.path("userName").asText("");
        String email = userNode.path("email").asText("");
        return new User(id, userName, email);
    }

    private void generateAndStoreMonthlyReport(LocalDate triggerDate) {
        String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());
        int totalUsers = userStorage.size();
        int newUsers = (int)(Math.random()*20); // placeholder
        MonthlyReport report = new MonthlyReport();
        report.setMonth(monthKey);
        report.setTotalUsers(totalUsers);
        report.setNewUsers(newUsers);
        report.setReportUrl("https://example.com/reports/"+monthKey+".pdf");
        report.getOtherStats().put("exampleStat","prototypeValue");
        monthlyReports.put(monthKey, report);
        log.info("Monthly report generated for {}: totalUsers={}, newUsers={}", monthKey, totalUsers, newUsers);
    }

    private void sendReportEmail(LocalDate triggerDate) {
        String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());
        MonthlyReport report = monthlyReports.get(monthKey);
        if (report == null) {
            log.warn("No report found for month {} - skipping email send", monthKey);
            return;
        }
        log.info("[MOCK] Sending monthly report email to admin with report URL: {}", report.getReportUrl());
    }

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