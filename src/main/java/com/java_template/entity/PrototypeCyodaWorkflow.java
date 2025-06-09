package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@RequestMapping("/cyoda/api/photos")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String FAKEREST_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Books";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<String> ingestData() {
        CompletableFuture.runAsync(() -> {
            logger.info("Starting data ingestion from {}", FAKEREST_API_URL);
            try {
                String response = new RestTemplate().getForObject(FAKEREST_API_URL, String.class);
                JsonNode photos = objectMapper.readTree(response);

                if (photos.isArray()) {
                    List<ObjectNode> photoList = objectMapper.convertValue(photos, objectMapper.getTypeFactory().constructCollectionType(List.class, ObjectNode.class));
                    for (ObjectNode photo : photoList) {
                        try {
                            entityService.addItem("Photo", ENTITY_VERSION, photo, this::processPhoto).get();
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Error processing photo", e);
                            Thread.currentThread().interrupt(); // Restore interrupted status
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error during data ingestion", e);
                throw new ResponseStatusException(500, "Data ingestion failed");
            }
        });
        return ResponseEntity.ok("Data ingestion completed");
    }

    @GetMapping("/gallery")
    public ResponseEntity<Iterable<JsonNode>> getGallery() {
        ArrayNode photos = entityService.getItems("Photo", ENTITY_VERSION).join();
        return ResponseEntity.ok(photos);
    }

    @PostMapping("/{photoId}/view")
    public ResponseEntity<String> viewAndComment(@PathVariable UUID photoId, @RequestBody @Valid CommentRequest commentRequest) {
        CompletableFuture.runAsync(() -> {
            logger.info("Retrieving photo with ID: {}", photoId);
            try {
                ObjectNode photoNode = entityService.getItem("Photo", ENTITY_VERSION, photoId).get();
                int views = photoNode.get("views").asInt() + 1;
                photoNode.put("views", views);
                entityService.updateItem("Photo", ENTITY_VERSION, photoId, photoNode).get();
                logger.info("Comment added: {}", commentRequest.getComment());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error viewing and commenting on photo", e);
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        });
        return ResponseEntity.ok("View and comment recorded");
    }

    @PostMapping("/reports/monthly")
    public ResponseEntity<String> generateMonthlyReport() {
        CompletableFuture.runAsync(() -> {
            logger.info("Report generation started");
            // Add report generation logic here
        });
        return ResponseEntity.ok("Report generation initiated");
    }

    @PostMapping("/notify")
    public ResponseEntity<String> notifyUsers(@RequestBody @Valid NotificationRequest notificationRequest) {
        CompletableFuture.runAsync(() -> {
            logger.info("Notifying users via {}", notificationRequest.getNotificationType());
            // Add notification logic here
        });
        return ResponseEntity.ok("Notifications sent");
    }

    private ObjectNode processPhoto(ObjectNode photo) {
        // Example workflow logic: add a default title if missing
        if (!photo.has("title") || photo.get("title").asText().isEmpty()) {
            photo.put("title", "Untitled");
        }

        // Increment views
        int views = photo.has("views") ? photo.get("views").asInt() : 0;
        photo.put("views", views + 1);

        // Any additional asynchronous tasks related to the entity can be added here

        return photo;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CommentRequest {
        @NotNull
        @Size(min = 1, max = 255)
        private String comment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class NotificationRequest {
        @NotNull
        @Pattern(regexp = "email|push", message = "Notification type must be either 'email' or 'push'")
        private String notificationType;
    }
}