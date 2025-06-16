package com.java_template.entity.ActivityReport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class ActivityReportWorkflow {

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public ActivityReportWorkflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    // Workflow orchestration method - no business logic here, just chaining process steps
    public CompletableFuture<ObjectNode> processActivityReport(ObjectNode entity) {
        return processFetchAndAnalyze(entity)
                .thenCompose(this::processCreateAuditLog)
                .thenApply(e -> e);
    }

    // Fetch external data and analyze, enrich entity
    private CompletableFuture<ObjectNode> processFetchAndAnalyze(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.path("date").asText(null);
                if (dateStr == null || dateStr.isBlank()) {
                    throw new IllegalArgumentException("Entity 'date' field is missing or empty");
                }
                logger.info("Workflow: processing ActivityReport for date {}", dateStr);

                URI fakerestUri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Activities");
                String rawJson = entityService.getRestTemplate().getForObject(fakerestUri, String.class);
                if (rawJson == null) {
                    throw new IllegalStateException("Failed to fetch data from Fakerest API");
                }
                JsonNode activitiesNode = objectMapper.readTree(rawJson);

                int totalActivities = 0;
                Map<String, Integer> activityTypesCount = new HashMap<>();
                activityTypesCount.put("typeA", 0);
                activityTypesCount.put("typeB", 0);
                activityTypesCount.put("typeC", 0);

                if (activitiesNode.isArray()) {
                    totalActivities = activitiesNode.size();
                    for (JsonNode activityNode : activitiesNode) {
                        String activityName = activityNode.path("activityName").asText("");
                        int mod = activityName.length() % 3;
                        switch (mod) {
                            case 0 -> activityTypesCount.merge("typeA", 1, Integer::sum);
                            case 1 -> activityTypesCount.merge("typeB", 1, Integer::sum);
                            default -> activityTypesCount.merge("typeC", 1, Integer::sum);
                        }
                    }
                }

                // Modify entity directly - these changes will be persisted
                entity.put("totalActivities", totalActivities);
                entity.set("activityTypes", objectMapper.valueToTree(activityTypesCount));
                entity.set("trends", objectMapper.valueToTree(Map.of("mostActiveUser", "user123", "peakActivityHour", "15:00")));
                entity.set("anomalies", objectMapper.valueToTree(new String[]{"User456 showed unusually high activity"}));

                logger.info("Workflow: enriched ActivityReport entity with computed values");

                return entity;

            } catch (Exception e) {
                logger.error("Error during processFetchAndAnalyze: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    // Create audit log entity asynchronously
    private CompletableFuture<ObjectNode> processCreateAuditLog(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.path("date").asText(null);
                ObjectNode auditLogEntity = objectMapper.createObjectNode();
                auditLogEntity.put("entityModel", ENTITY_NAME);
                auditLogEntity.put("entityDate", dateStr);
                auditLogEntity.put("event", "ActivityReport processed");
                auditLogEntity.put("timestamp", OffsetDateTime.now().toString());

                // Add audit log entity asynchronously, no workflow on audit log (identity)
                entityService.addItem("AuditLog", ENTITY_VERSION, auditLogEntity, Function.identity());

                logger.info("Workflow: created AuditLog entity");

                return entity;

            } catch (Exception e) {
                logger.error("Error during processCreateAuditLog: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
}