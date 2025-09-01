package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscriber", description = "Subscriber entity endpoints (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Subscriber", description = "Create a new Subscriber entity. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is required");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("email is required");
            }

            Subscriber entity = new Subscriber();
            entity.setEmail(request.getEmail());
            // entity.isValid requires name; use email as fallback if name not provided
            String name = (request.getName() != null && !request.getName().isBlank()) ? request.getName() : request.getEmail();
            entity.setName(name);
            // Set minimal required fields to satisfy entity validation
            entity.setInteractionsCount(0);
            entity.setStatus("PENDING_CONFIRMATION");
            entity.setSubscribedAt(OffsetDateTime.now());

            UUID id = entityService.addItem(Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, entity).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when creating subscriber", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscriber", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error when creating subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);
            DataPayload payload = entityService.getItem(id).get();

            if (payload == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }

            ObjectNode dataNode = payload.getData() != null ? (ObjectNode) payload.getData() : null;
            Subscriber entity = null;
            if (dataNode != null) {
                entity = objectMapper.treeToValue(dataNode, Subscriber.class);
            }

            SubscriberResponse resp = new SubscriberResponse();
            resp.setTechnicalId(technicalId);
            if (entity != null) {
                resp.setEmail(entity.getEmail());
                resp.setName(entity.getName());
                resp.setStatus(entity.getStatus());
                resp.setInteractionsCount(entity.getInteractionsCount());
                if (entity.getSubscribedAt() != null) {
                    resp.setSubscribedAt(entity.getSubscribedAt().toString());
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument when fetching subscriber", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when fetching subscriber", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when fetching subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error when fetching subscriber", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Subscriber email", example = "alice@example.com", required = true)
        private String email;

        @Schema(description = "Display name", example = "Alice", required = false)
        private String name;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of the created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "generated-uuid-1234")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Technical ID of the entity", example = "generated-uuid-1234")
        private String technicalId;

        @Schema(description = "Subscriber email", example = "alice@example.com")
        private String email;

        @Schema(description = "Display name", example = "Alice")
        private String name;

        @Schema(description = "ISO timestamp when signed up", example = "2025-09-01T10:00:00Z")
        private String subscribedAt;

        @Schema(description = "Subscription status", example = "PENDING_CONFIRMATION")
        private String status;

        @Schema(description = "Total recorded interactions", example = "0")
        private Integer interactionsCount;
    }
}