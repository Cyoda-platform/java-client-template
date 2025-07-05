package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/prototype/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function that performs asynchronous processing and entity state changes before persisting.
     * This is the recommended place for any async enrichment, side effects or secondary entity operations.
     *
     * @param entity the entity data to process and modify if needed
     * @return a CompletableFuture of the processed entity to persist
     */
    private CompletableFuture<ObjectNode> processPrototype(ObjectNode entity) {
        // Defensive null check (should not happen)
        if (entity == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity is null"));
        }

        try {
            // Add or modify fields directly on the entity before persistence
            entity.put("processedTimestamp", System.currentTimeMillis());

            // Example: If you want to add a supplementary entity of a different model,
            // you can do it here asynchronously.

            // WARNING: Do NOT call addItem/updateItem/deleteItem on "prototype" entityModel here,
            // to prevent infinite recursion.

            UUID supplementaryId = UUID.randomUUID(); // fake id for example

            // Fetch supplementary data asynchronously, for example from another entityModel "supplementaryModel"
            CompletableFuture<ObjectNode> supplementaryDataFuture = entityService.getItem(
                    "supplementaryModel",
                    ENTITY_VERSION,
                    supplementaryId
            );

            // When supplementary data arrives, merge or add to the main entity
            return supplementaryDataFuture.handle((supplementaryData, ex) -> {
                if (ex != null) {
                    logger.warn("Failed to fetch supplementary data for entity {}, continuing without it", supplementaryId, ex);
                } else if (supplementaryData != null) {
                    // Add supplementary data as nested property or merge fields
                    entity.set("supplementaryData", supplementaryData);
                }
                // Return the modified entity regardless of supplementary data success or failure
                return entity;
            });

        } catch (Exception e) {
            logger.error("Exception in processPrototype workflow", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> saveItem(
            @RequestBody @Valid SaveItemRequest request) {
        logger.info("POST /cyoda/prototype/items - received save request");

        ObjectNode data;
        try {
            data = (ObjectNode) entityService.getObjectMapper().readTree(request.getItemJson());
        } catch (Exception e) {
            logger.error("Invalid JSON format", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid JSON");
        }

        // Validate that data is not empty or null (basic sanity check)
        if (data.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Empty entity data");
        }

        // Pass the workflow function to addItem
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "prototype",
                ENTITY_VERSION,
                data,
                this::processPrototype
        );

        UUID technicalId;
        try {
            technicalId = idFuture.join();
        } catch (Exception e) {
            logger.error("Failed to persist entity", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist entity");
        }

        logger.info("Item stored with technicalId {}", technicalId);
        return ResponseEntity.ok(new IdResponse(technicalId.toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> getItem(
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$") String id) {
        logger.info("GET /cyoda/prototype/items/{} - retrieving item", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "prototype",
                ENTITY_VERSION,
                UUID.fromString(id)
        );

        ObjectNode item;
        try {
            item = itemFuture.join();
        } catch (Exception e) {
            logger.error("Failed to retrieve entity with id {}", id, e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve entity");
        }

        if (item == null) {
            logger.warn("Item with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Item not found");
        }
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SaveItemRequest {
        @NotBlank
        @Size(max = 10000)
        private String itemJson;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IdResponse {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }
}