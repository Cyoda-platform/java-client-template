package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/prototype/activities")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String EXTERNAL_ACTIVITY_API = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
    private static final String ENTITY_NAME = "Activity";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestActivities() {
        try {
            JsonNode activitiesData = fetchExternalActivities();

            if (!activitiesData.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected an array of activities");
            }

            // Add each activity with the workflow processing
            activitiesData.forEach(activity -> {
                CompletableFuture<Void> future = entityService.addItem(
                        ENTITY_NAME,
                        ENTITY_VERSION,
                        activity.deepCopy(), // Defensive copy to avoid shared mutable state issues
                        this::processActivity
                ).thenAccept(id -> logger.info("Persisted Activity entity with id={}", id))
                 .exceptionally(ex -> {
                    logger.error("Failed to persist Activity entity", ex);
                    return null;
                });
                // Fire and forget; no need to block here
            });

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Data ingestion started and entities are being processed asynchronously"
            ));
        } catch (Exception e) {
            logger.error("Failed to ingest activities", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to ingest activities");
        }
    }

    /**
     * Workflow function that processes Activity entity before persistence.
     * This method is invoked asynchronously by entityService.addItem.
     * You can modify the entity directly (ObjectNode) and add supplementary entities of other models.
     * Do NOT add/update/delete entities of the same model here.
     */
    private CompletableFuture<JsonNode> processActivity(JsonNode activityEntity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!(activityEntity instanceof ObjectNode)) {
                logger.warn("Activity entity is not an ObjectNode, skipping processing");
                return activityEntity;
            }

            ObjectNode entityNode = (ObjectNode) activityEntity;

            // Add processed timestamp safely
            entityNode.put("processedTimestamp", Instant.now().toString());

            // Example: enrich with some derived field
            String title = entityNode.path("title").asText("");
            if (!title.isEmpty()) {
                entityNode.put("titleLength", title.length());
            }

            // Add supplementary entity asynchronously (LogEntry)
            try {
                ObjectNode logEntryNode = objectMapper.createObjectNode();
                logEntryNode.put("message", "Ingested activity with title: " + title);
                logEntryNode.put("ingestedAt", Instant.now().toString());

                entityService.addItem(
                        "LogEntry",  // Different entityModel
                        ENTITY_VERSION,
                        logEntryNode,
                        this::processLogEntry
                ).exceptionally(ex -> {
                    logger.error("Failed to persist LogEntry supplementary entity", ex);
                    return null;
                });
            } catch (Exception e) {
                logger.error("Exception while creating supplementary LogEntry entity", e);
            }

            // Fire and forget: asynchronous notification (simulated)
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Sending notification for activity titled '{}'", title);
                    // Simulate notification sending, e.g., call external service or email
                    // No blocking, fire and forget
                } catch (Exception e) {
                    logger.error("Failed to send notification", e);
                }
            });

            return entityNode;
        });
    }

    /**
     * Workflow function for LogEntry entities.
     * You can enrich or add metadata here.
     */
    private CompletableFuture<JsonNode> processLogEntry(JsonNode logEntryEntity) {
        return CompletableFuture.supplyAsync(() -> {
            if (logEntryEntity instanceof ObjectNode) {
                ((ObjectNode) logEntryEntity).put("processedTimestamp", Instant.now().toString());
            }
            return logEntryEntity;
        });
    }

    /**
     * Fetch external activities from the configured API.
     * Throws ResponseStatusException on failure.
     */
    private JsonNode fetchExternalActivities() {
        try {
            logger.info("Fetching activities from Fakerest API: {}", EXTERNAL_ACTIVITY_API);
            String json = restTemplate.getForObject(EXTERNAL_ACTIVITY_API, String.class);
            if (json == null || json.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external activities API");
            }
            return objectMapper.readTree(json);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            logger.error("Failed to fetch activities from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch external activities");
        }
    }

    // --- Optional endpoints for reports if needed, left as pass-through or minimal ---

    // If you want to generate reports, consider a separate scheduled service that queries persisted entities
    // and generates reports asynchronously, decoupled from ingestion workflow.

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = Map.of(
                "error", ex.getStatus().toString(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        Map<String, String> error = Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}