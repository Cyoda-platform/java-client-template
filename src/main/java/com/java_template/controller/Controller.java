package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entity")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private static final String ENTITY_NAME_USER = "User";
    private static final String ENTITY_NAME_USER_BATCH = "UserBatch";
    private static final String ENTITY_NAME_MONTHLY_REPORT = "MonthlyReport";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchProcessRequest {
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "triggerDate must be in YYYY-MM-DD format")
        private String triggerDate;
    }

    @Data
    @AllArgsConstructor
    public static class BatchProcessResponse {
        private String status;
        private String message;
        private String batchId;
    }

    @PostMapping("/processUsers")
    public ResponseEntity<BatchProcessResponse> processUsersBatch(@RequestBody(required = false) @Valid BatchProcessRequest request) {
        String batchId = UUID.randomUUID().toString();
        String triggerDateStr = (request != null) ? request.getTriggerDate() : null;

        LocalDate triggerDate;
        try {
            if (StringUtils.hasText(triggerDateStr)) {
                triggerDate = LocalDate.parse(triggerDateStr);
            } else {
                triggerDate = LocalDate.now();
            }
        } catch (Exception e) {
            logger.error("Invalid triggerDate format: {}", triggerDateStr);
            throw new ResponseStatusException(400, "Invalid triggerDate format, expected YYYY-MM-DD");
        }

        ObjectNode batchEntity = objectMapper.createObjectNode();
        batchEntity.put("batchId", batchId);
        batchEntity.put("triggerDate", triggerDate.toString());
        batchEntity.put("requestedAt", OffsetDateTime.now().toString());
        batchEntity.put("status", "processing");

        CompletableFuture<UUID> addBatchFuture = entityService.addItem(
                ENTITY_NAME_USER_BATCH,
                ENTITY_VERSION,
                batchEntity
        );

        // We do not wait here for completion, return immediately with batchId
        return ResponseEntity.ok(new BatchProcessResponse("processing_started", "Batch processing initiated", batchId));
    }

    @GetMapping("/users")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getUsers(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {

        if (page < 1 || size < 1) {
            throw new ResponseStatusException(400, "Page and size must be positive integers");
        }

        return entityService.getItems(ENTITY_NAME_USER, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    int totalUsers = arrayNode.size();
                    int totalPages = (int) Math.ceil((double) totalUsers / size);

                    if (page > totalPages && totalPages != 0) {
                        throw new ResponseStatusException(404, "Page number out of range");
                    }

                    int fromIndex = Math.min((page - 1) * size, totalUsers);
                    int toIndex = Math.min(fromIndex + size, totalUsers);

                    List<ObjectNode> pageUsers = new ArrayList<>();
                    for (int i = fromIndex; i < toIndex; i++) {
                        ObjectNode userNode = (ObjectNode) arrayNode.get(i);
                        pageUsers.add(userNode);
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("users", pageUsers);
                    response.put("page", page);
                    response.put("size", size);
                    response.put("totalPages", totalPages);

                    return ResponseEntity.ok(response);
                });
    }

    private CompletableFuture<ObjectNode> processUserBatch(ObjectNode batchEntity) {
        String triggerDateStr = batchEntity.path("triggerDate").asText(null);
        LocalDate triggerDate = LocalDate.now();
        try {
            if (triggerDateStr != null) {
                triggerDate = LocalDate.parse(triggerDateStr);
            }
        } catch (Exception ex) {
            logger.warn("Invalid triggerDate in batch entity, defaulting to today: {}", triggerDateStr);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Users";
                String rawJson = entityService.getHttpClient().getForObject(URI.create(apiUrl), String.class);
                if (rawJson == null) {
                    throw new RuntimeException("Empty response from external user API");
                }
                JsonNode rootNode = objectMapper.readTree(rawJson);
                if (!rootNode.isArray()) {
                    throw new RuntimeException("Unexpected JSON format from external user API");
                }

                List<CompletableFuture<UUID>> userAddFutures = new ArrayList<>();
                for (JsonNode userNode : rootNode) {
                    ObjectNode userEntity = objectMapper.createObjectNode();
                    userEntity.put("id", userNode.path("id").asInt());
                    userEntity.put("userName", userNode.path("userName").asText(""));
                    userEntity.put("email", userNode.path("email").asText(""));
                    CompletableFuture<UUID> userAddFuture = entityService.addItem(
                            ENTITY_NAME_USER,
                            ENTITY_VERSION,
                            userEntity
                    );
                    userAddFutures.add(userAddFuture);
                }

                CompletableFuture.allOf(userAddFutures.toArray(new CompletableFuture[0])).join();

                ObjectNode monthlyReportEntity = objectMapper.createObjectNode();
                String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());
                monthlyReportEntity.put("month", monthKey);
                monthlyReportEntity.put("totalUsers", rootNode.size());
                monthlyReportEntity.put("generatedAt", OffsetDateTime.now().toString());

                entityService.addItem(ENTITY_NAME_MONTHLY_REPORT, ENTITY_VERSION, monthlyReportEntity);

                sendReportEmailAsync(monthKey);

                batchEntity.put("status", "completed");
                batchEntity.put("completedAt", OffsetDateTime.now().toString());

                return batchEntity;

            } catch (Exception e) {
                logger.error("Error in processUserBatch workflow", e);
                batchEntity.put("status", "failed");
                batchEntity.put("errorMessage", e.getMessage());
                batchEntity.put("completedAt", OffsetDateTime.now().toString());
                return batchEntity;
            }
        });
    }

    private CompletableFuture<ObjectNode> processUser(ObjectNode userEntity) {
        userEntity.put("processedAt", OffsetDateTime.now().toString());

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("User entity processed for persistence: id={}, userName={}", userEntity.path("id").asText(""), userEntity.path("userName").asText(""));
            } catch (Exception e) {
                logger.warn("Exception in async side effect of processUser: ", e);
            }
        });

        return CompletableFuture.completedFuture(userEntity);
    }

    private void sendReportEmailAsync(String monthKey) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("[MOCK] Sending monthly report email to admin for month: {}", monthKey);
                Thread.sleep(1000);
                logger.info("[MOCK] Monthly report email sent for month: {}", monthKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Email sending thread interrupted");
            } catch (Exception e) {
                logger.error("Error during mock email sending", e);
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body("Internal server error");
    }
}