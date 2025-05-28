package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ENTITY_NAME_USER = "User";
    private static final String ENTITY_NAME_MONTHLY_REPORT = "MonthlyReport";
    private static final String ENTITY_VERSION = "1.0";

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Orchestration only: no business logic here
    public CompletableFuture<ObjectNode> processUserBatch(ObjectNode batchEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                batchEntity.put("status", "processing");
                CompletableFuture<ObjectNode> fetchUsersFut = processFetchUsers(batchEntity);
                ObjectNode usersArray = fetchUsersFut.join();

                CompletableFuture<ObjectNode> storeUsersFut = processStoreUsers(usersArray);
                storeUsersFut.join();

                CompletableFuture<ObjectNode> reportFut = processGenerateMonthlyReport(batchEntity, usersArray);
                reportFut.join();

                CompletableFuture<ObjectNode> emailFut = processSendReportEmail(batchEntity);
                emailFut.join();

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

    // Fetch users JSON array from external API, return as ObjectNode with field "users" holding array
    private CompletableFuture<ObjectNode> processFetchUsers(ObjectNode batchEntity) {
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
                ObjectNode result = objectMapper.createObjectNode();
                result.set("users", rootNode);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch users", e);
            }
        });
    }

    // Store each user from users JSON array to entityService with workflow processUser
    private CompletableFuture<ObjectNode> processStoreUsers(ObjectNode usersEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode users = usersEntity.get("users");
                if (users == null || !users.isArray()) {
                    throw new RuntimeException("Invalid users data for storing");
                }
                List<CompletableFuture<UUID>> futures = new ArrayList<>();
                for (JsonNode userNode : users) {
                    ObjectNode userEntity = objectMapper.createObjectNode();
                    userEntity.put("id", userNode.path("id").asInt());
                    userEntity.put("userName", userNode.path("userName").asText(""));
                    userEntity.put("email", userNode.path("email").asText(""));
                    CompletableFuture<UUID> fut = entityService.addItem(
                            ENTITY_NAME_USER,
                            ENTITY_VERSION,
                            userEntity,
                            this::processUser // workflow for users
                    );
                    futures.add(fut);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                return usersEntity;
            } catch (Exception e) {
                throw new RuntimeException("Failed to store users", e);
            }
        });
    }

    // Create monthly report entity and add it directly (no workflow)
    private CompletableFuture<ObjectNode> processGenerateMonthlyReport(ObjectNode batchEntity, ObjectNode usersEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String triggerDateStr = batchEntity.path("triggerDate").asText(null);
                LocalDate triggerDate = LocalDate.now();
                try {
                    if (triggerDateStr != null) {
                        triggerDate = LocalDate.parse(triggerDateStr);
                    }
                } catch (Exception ex) {
                    logger.warn("Invalid triggerDate in batch entity, defaulting to today: {}", triggerDateStr);
                }
                String monthKey = String.format("%04d-%02d", triggerDate.getYear(), triggerDate.getMonthValue());

                ObjectNode monthlyReportEntity = objectMapper.createObjectNode();
                monthlyReportEntity.put("month", monthKey);
                monthlyReportEntity.put("totalUsers", usersEntity.path("users").size());
                monthlyReportEntity.put("generatedAt", OffsetDateTime.now().toString());

                // Add monthly report entity, no workflow needed
                entityService.addItem(ENTITY_NAME_MONTHLY_REPORT, ENTITY_VERSION, monthlyReportEntity, entity -> CompletableFuture.completedFuture(entity));

                batchEntity.put("monthKey", monthKey); // store monthKey for next step

                return batchEntity;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate monthly report", e);
            }
        });
    }

    // Send report email asynchronously (mocked)
    private CompletableFuture<ObjectNode> processSendReportEmail(ObjectNode batchEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String monthKey = batchEntity.path("monthKey").asText(null);
                if (monthKey == null) {
                    throw new RuntimeException("Missing monthKey for sending report email");
                }
                sendReportEmailAsync(monthKey);
                return batchEntity;
            } catch (Exception e) {
                throw new RuntimeException("Failed to send report email", e);
            }
        });
    }

    // User workflow - example placeholder, modify user entity state if needed
    private CompletableFuture<ObjectNode> processUser(ObjectNode userEntity) {
        // TODO: Add user-specific processing logic here if needed
        return CompletableFuture.completedFuture(userEntity);
    }

    // Mock email sending method
    private void sendReportEmailAsync(String monthKey) {
        logger.info("[MOCK] Sending monthly report email to admin with report month: {}", monthKey);
    }
}