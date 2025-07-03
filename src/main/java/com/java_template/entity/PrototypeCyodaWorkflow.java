package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function for entityModel entity to process asynchronously before persistence
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processEntityModel = entityData -> {
        logger.info("Starting async processing of entity before persistence");

        // Add lastModified timestamp
        entityData.put("lastModified", Instant.now().toString());

        // Defensive checks to prevent null pointers or invalid states
        if (!entityData.hasNonNull("relatedId")) {
            logger.warn("Entity missing relatedId, skipping supplementary data fetch");
            return CompletableFuture.completedFuture(entityData);
        }

        UUID relatedId;
        try {
            relatedId = UUID.fromString(entityData.get("relatedId").asText());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid relatedId format: {}, skipping supplementary data fetch", entityData.get("relatedId").asText());
            return CompletableFuture.completedFuture(entityData);
        }

        // Fetch supplementary data asynchronously from different entity model
        CompletableFuture<ObjectNode> supplementaryFuture = entityService.getItem(
                "supplementaryModel", ENTITY_VERSION, relatedId);

        // Compose final entity data with supplementary data if available
        return supplementaryFuture.handle((supplementaryData, ex) -> {
            if (ex != null) {
                logger.error("Failed to fetch supplementary entity: {}", ex.getMessage());
                // Proceed without supplementary data to avoid blocking persistence
                return entityData;
            }
            if (supplementaryData != null) {
                entityData.set("supplementaryData", supplementaryData);
            }
            logger.info("Completed async entity processing before persistence");
            return entityData;
        });
    };

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> postItem(
            @RequestBody @NotNull ObjectNode data
    ) {
        logger.info("Received POST /cyoda/items request");

        // Call entityService.addItem with workflow function to handle async processing
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "entityModel",
                ENTITY_VERSION,
                data,
                processEntityModel
        );

        UUID technicalId;
        try {
            technicalId = idFuture.join();
        } catch (Exception e) {
            logger.error("Error saving entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save entity");
        }

        logger.info("Entity stored successfully with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(technicalId.toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> getItem(
            @PathVariable("id")
            @Pattern(regexp = "[0-9a-fA-F\\-]{36}", message = "Invalid ID format") String id
    ) {
        logger.info("Received GET /cyoda/items/{} request", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for id: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "entityModel",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode item;
        try {
            item = itemFuture.join();
        } catch (Exception e) {
            logger.error("Error retrieving entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve entity");
        }

        if (item == null || !item.has("technicalId")) {
            logger.warn("Entity not found for technicalId {}", technicalId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        logger.info("Entity retrieved successfully for technicalId {}", technicalId);
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class IdResponse {
        private String id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }
}