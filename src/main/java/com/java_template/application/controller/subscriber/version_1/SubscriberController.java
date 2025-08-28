package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber", description = "Operations for Subscriber entity (proxy controller)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Persist a new Subscriber entity. Returns only the technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map request DTO to entity (no business logic)
            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setName(request.getName());
            entity.setEmail(request.getEmail());
            entity.setWebhookUrl(request.getWebhookUrl());
            entity.setFilters(request.getFilters());
            entity.setActive(request.getActive());
            entity.setCreatedAt(request.getCreatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId != null ? technicalId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error creating subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by its technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            SubscriberResponse resp = objectMapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while fetching subscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error fetching subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Subscribers", description = "Retrieve all Subscribers (no filtering/pagination applied by controller)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listSubscribers() {
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
                    JsonNode data = payload.getData();
                    if (data != null) {
                        SubscriberResponse resp = objectMapper.treeToValue(data, SubscriberResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while listing subscribers", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error listing subscribers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Resource not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in async execution: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("ExecutionException with unexpected cause", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        }
    }

    // Static DTOs

    @Data
    @Schema(name = "SubscriberRequest", description = "Request payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Business id of subscriber", example = "sub-01")
        private String id;

        @Schema(description = "Subscriber name", example = "Chemistry Feed")
        private String name;

        @Schema(description = "Subscriber email", example = "alerts@example.com", nullable = true)
        private String email;

        @Schema(description = "Webhook URL", example = "https://example.com/webhook", nullable = true)
        private String webhookUrl;

        @Schema(description = "Filters expression", example = "category=Chemistry", nullable = true)
        private String filters;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Created at timestamp (ISO-8601)", example = "2025-08-01T12:00:00Z", nullable = true)
        private String createdAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id after creation")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id of the persisted entity", example = "T_SUB_0001")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber representation returned by GET endpoints")
    public static class SubscriberResponse {
        @Schema(description = "Business id of subscriber", example = "sub-01")
        private String id;

        @Schema(description = "Subscriber name", example = "Chemistry Feed")
        private String name;

        @Schema(description = "Subscriber email", example = "alerts@example.com", nullable = true)
        private String email;

        @Schema(description = "Webhook URL", example = "https://example.com/webhook", nullable = true)
        private String webhookUrl;

        @Schema(description = "Filters expression", example = "category=Chemistry", nullable = true)
        private String filters;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Created at timestamp (ISO-8601)", example = "2025-08-01T12:00:00Z", nullable = true)
        private String createdAt;
    }
}