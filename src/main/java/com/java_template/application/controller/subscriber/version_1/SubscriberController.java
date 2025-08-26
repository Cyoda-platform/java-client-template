package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber", description = "Subscriber entity endpoints (version 1) - proxy to EntityService")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register Subscriber", description = "Create a new Subscriber. Returns technicalId of the created subscriber.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<Object> createSubscriber(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Subscriber creation payload",
            content = @Content(schema = @Schema(implementation = SubscriberCreateRequest.class))
    ) @RequestBody SubscriberCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getContactType() == null || request.getContactType().isBlank()) {
                throw new IllegalArgumentException("contactType is required");
            }
            if (request.getContactAddress() == null || request.getContactAddress().isBlank()) {
                throw new IllegalArgumentException("contactAddress is required");
            }

            Subscriber entity = new Subscriber();
            try {
                // attempt to set common fields; entity class is reused as-is
                entity.setId(request.getId());
                entity.setName(request.getName());
                entity.setContactType(request.getContactType());
                entity.setContactAddress(request.getContactAddress());
                entity.setPreferredFormat(request.getPreferredFormat());
            } catch (Exception e) {
                // If entity setters differ, propagate as IllegalArgumentException
                throw new IllegalArgumentException("Failed to populate Subscriber entity: " + e.getMessage(), e);
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createSubscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createSubscriber", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during createSubscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a subscriber by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content =
            @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<Object> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            SubscriberResponse response = objectMapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getSubscriberById", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getSubscriberById", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getSubscriberById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "List Subscribers", description = "Retrieve all subscribers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<Object> listSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<SubscriberResponse> result = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                SubscriberResponse resp = objectMapper.treeToValue(node, SubscriberResponse.class);
                result.add(resp);
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during listSubscribers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during listSubscribers", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during listSubscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "SubscriberCreateRequest", description = "Payload to register a new subscriber")
    public static class SubscriberCreateRequest {
        @Schema(description = "Optional human identifier", example = "nobel-alerts")
        private String id;

        @Schema(description = "Human readable name", required = true, example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type (email, webhook, ...)", required = true, example = "email")
        private String contactType;

        @Schema(description = "Contact address (email or endpoint)", required = true, example = "alerts@example.com")
        private String contactAddress;

        @Schema(description = "Preferred notification format (summary or full_payload)", example = "summary")
        private String preferredFormat;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing created technicalId")
    public static class IdResponse {
        @Schema(description = "Technical id (UUID)", example = "sub-0001-uuid")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber representation returned by the API")
    public static class SubscriberResponse {
        @Schema(description = "Technical id (UUID)", example = "sub-0001-uuid")
        private String technicalId;

        @Schema(description = "Optional human identifier", example = "nobel-alerts")
        private String id;

        @Schema(description = "Human readable name", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type (email, webhook, ...)", example = "email")
        private String contactType;

        @Schema(description = "Contact address (email or endpoint)", example = "alerts@example.com")
        private String contactAddress;

        @Schema(description = "Is subscriber active", example = "true")
        private Boolean active;

        @Schema(description = "Preferred notification format (summary or full_payload)", example = "summary")
        private String preferredFormat;

        @Schema(description = "Timestamp of last notification", example = "2025-01-10T10:02:00Z")
        private String lastNotifiedAt;

        @Schema(description = "Last notification outcome", example = "DELIVERED")
        private String notificationStatus;
    }
}