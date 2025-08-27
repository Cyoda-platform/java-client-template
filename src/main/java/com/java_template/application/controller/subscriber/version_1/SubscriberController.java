package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscribers")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Persist a new Subscriber entity. Returns technicalId as string.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createSubscriber(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber creation payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class),
                examples = @ExampleObject(value = "{\"subscriberId\":\"chemistry_team\",\"contactType\":\"WEBHOOK\",\"contactAddress\":\"https://example.com/webhook\",\"filters\":\"category=Chemistry\",\"active\":true}")))
        @RequestBody CreateSubscriberRequest request
    ) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            // Basic validation
            if (request.getSubscriberId() == null || request.getSubscriberId().isBlank())
                throw new IllegalArgumentException("subscriberId is required");
            if (request.getContactType() == null || request.getContactType().isBlank())
                throw new IllegalArgumentException("contactType is required");
            if (request.getContactAddress() == null || request.getContactAddress().isBlank())
                throw new IllegalArgumentException("contactAddress is required");
            if (request.getActive() == null) throw new IllegalArgumentException("active is required");

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setContactType(request.getContactType());
            entity.setContactAddress(request.getContactAddress());
            entity.setFilters(request.getFilters());
            entity.setActive(request.getActive());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            java.util.concurrent.CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                entity
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by technical UUID.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriberById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID uuid = UUID.fromString(technicalId);
            java.util.concurrent.CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscriber entities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllSubscribers() {
        try {
            java.util.concurrent.CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all subscribers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving all subscribers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing Subscriber by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateSubscriber(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update payload", required = true,
            content = @Content(schema = @Schema(implementation = UpdateSubscriberRequest.class)))
        @RequestBody UpdateSubscriberRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID uuid = UUID.fromString(technicalId);

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setContactType(request.getContactType());
            entity.setContactAddress(request.getContactAddress());
            entity.setFilters(request.getFilters());
            entity.setActive(request.getActive());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            java.util.concurrent.CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid,
                entity
            );
            UUID updatedId = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while updating subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteSubscriber(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID uuid = UUID.fromString(technicalId);
            java.util.concurrent.CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                uuid
            );
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete subscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting subscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Payload to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "External subscriber identifier", example = "chemistry_team", required = true)
        private String subscriberId;

        @Schema(description = "Contact type (EMAIL or WEBHOOK or OTHER)", example = "WEBHOOK", required = true)
        private String contactType;

        @Schema(description = "Contact address (email or webhook URL)", example = "https://example.com/webhook", required = true)
        private String contactAddress;

        @Schema(description = "Optional filter expression for notifications", example = "category=Chemistry")
        private String filters;

        @Schema(description = "Is subscriber active", example = "true", required = true)
        private Boolean active;

        @Schema(description = "Timestamp of last notification as ISO string", example = "2025-08-20T02:00:00Z")
        private String lastNotifiedAt;
    }

    @Data
    @Schema(name = "UpdateSubscriberRequest", description = "Payload to update a Subscriber")
    public static class UpdateSubscriberRequest {
        @Schema(description = "External subscriber identifier", example = "chemistry_team")
        private String subscriberId;

        @Schema(description = "Contact type (EMAIL or WEBHOOK or OTHER)", example = "WEBHOOK")
        private String contactType;

        @Schema(description = "Contact address (email or webhook URL)", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Optional filter expression for notifications", example = "category=Chemistry")
        private String filters;

        @Schema(description = "Is subscriber active", example = "true")
        private Boolean active;

        @Schema(description = "Timestamp of last notification as ISO string", example = "2025-08-20T02:00:00Z")
        private String lastNotifiedAt;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber representation returned by GET operations")
    public static class SubscriberResponse {
        @Schema(description = "External subscriber identifier", example = "chemistry_team")
        private String subscriberId;

        @Schema(description = "Contact type (EMAIL or WEBHOOK or OTHER)", example = "WEBHOOK")
        private String contactType;

        @Schema(description = "Contact address (email or webhook URL)", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Optional filter expression for notifications", example = "category=Chemistry")
        private String filters;

        @Schema(description = "Is subscriber active", example = "true")
        private Boolean active;

        @Schema(description = "Timestamp of last notification as ISO string", example = "2025-08-20T02:00:00Z")
        private String lastNotifiedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical UUID of the affected entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID as string", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}