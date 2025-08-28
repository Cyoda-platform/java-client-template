package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping(path = "/api/v1/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriber", description = "Subscriber entity proxy controller (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Persist a new Subscriber entity (proxy to EntityService). Returns technicalId of created record.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> createSubscriber(
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
                    CreateSubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setActive(request.getActive());
            entity.setContactDetail(request.getContactDetail());
            entity.setContactType(request.getContactType());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setFilters(request.getFilters());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId != null ? entityId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createSubscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createSubscriber", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by technicalId (UUID).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<SubscriberResponse> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            JsonNode node = dataPayload != null ? dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.notFound().build();
            }
            Subscriber entity = objectMapper.treeToValue(node, Subscriber.class);
            SubscriberResponse resp = mapEntityToResponse(entity, technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getSubscriberById", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getSubscriberById", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Subscribers", description = "Retrieve all Subscriber entities (proxy to EntityService.getItems).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<SubscriberResponse>> listSubscribers() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<SubscriberResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null) continue;
                    JsonNode node = payload.getData();
                    if (node == null) continue;
                    Subscriber entity = objectMapper.treeToValue(node, Subscriber.class);
                    // Try to read technicalId from payload data first, fallback to entity.id
                    String techId = null;
                    if (node.has("technicalId") && node.get("technicalId").isTextual()) {
                        techId = node.get("technicalId").asText();
                    }
                    if (techId == null && entity != null) {
                        techId = entity.getId();
                    }
                    responses.add(mapEntityToResponse(entity, techId));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in listSubscribers", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in listSubscribers", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update an existing Subscriber by technicalId (proxy to EntityService.updateItem).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalIdResponse> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
                    CreateSubscriberRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setActive(request.getActive());
            entity.setContactDetail(request.getContactDetail());
            entity.setContactType(request.getContactType());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setFilters(request.getFilters());

            CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id != null ? id.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in updateSubscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in updateSubscriber", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber by technicalId (proxy to EntityService.deleteItem).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id != null ? id.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in deleteSubscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteSubscriber", ex);
            return ResponseEntity.status(500).build();
        }
    }

    private SubscriberResponse mapEntityToResponse(Subscriber entity, String technicalId) {
        SubscriberResponse resp = new SubscriberResponse();
        resp.setTechnicalId(technicalId);
        if (entity != null) {
            resp.setId(entity.getId());
            resp.setActive(entity.getActive());
            resp.setContactDetail(entity.getContactDetail());
            resp.setContactType(entity.getContactType());
            resp.setCreatedAt(entity.getCreatedAt());
            resp.setFilters(entity.getFilters());
        }
        return resp;
    }

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create or update a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Subscriber id (optional)", example = "sub-123")
        private String id;

        @Schema(description = "Contact type (email or webhook)", example = "email")
        private String contactType;

        @Schema(description = "Contact detail (email address or webhook URL)", example = "notify@example.com")
        private String contactDetail;

        @Schema(description = "Active subscription flag", example = "true")
        private Boolean active;

        @Schema(description = "Optional filters (e.g., category, year)")
        private Map<String, String> filters;

        @Schema(description = "Creation timestamp in ISO-8601", example = "2025-08-01T10:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Technical id of the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Subscriber id", example = "sub-123")
        private String id;

        @Schema(description = "Contact type", example = "email")
        private String contactType;

        @Schema(description = "Contact detail", example = "notify@example.com")
        private String contactDetail;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Optional filters")
        private Map<String, String> filters;

        @Schema(description = "Creation timestamp", example = "2025-08-01T10:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}