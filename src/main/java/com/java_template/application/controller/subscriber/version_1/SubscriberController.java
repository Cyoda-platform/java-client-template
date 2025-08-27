package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscriber", description = "API for Subscriber entity (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Subscriber", description = "Persist a new Subscriber entity. Returns technicalId only.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create payload", required = true,
                content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest request
    ) {
        try {
            // Basic request validation (format only)
            if (request == null) {
                throw new IllegalArgumentException("request body is required");
            }
            if (request.getId() == null || request.getId().isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getContactType() == null || request.getContactType().isBlank()) {
                throw new IllegalArgumentException("contactType is required");
            }
            if (request.getContactDetails() == null || request.getContactDetails().isBlank()) {
                throw new IllegalArgumentException("contactDetails is required");
            }
            if (request.getActive() == null) {
                throw new IllegalArgumentException("active flag is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactDetails(request.getContactDetails());
            entity.setActive(request.getActive());
            entity.setFilterPreferences(request.getFilterPreferences());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating subscriber", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error while creating subscriber", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve a Subscriber by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberGetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Subscriber not found");
            }
            JsonNode dataNode = dataPayload.getData();
            SubscriberGetResponse response = objectMapper.treeToValue(dataNode, SubscriberGetResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving subscriber", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving subscriber", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving subscriber", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "SubscriberRequest", description = "Payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Business id of subscriber", required = true, example = "sub-1")
        private String id;

        @Schema(description = "Display name", required = true, example = "Nobel Alerts")
        private String name;

        @Schema(description = "contact type (email|webhook|other)", required = true, example = "webhook")
        private String contactType;

        @Schema(description = "contact details (email or webhook URL)", required = true, example = "https://example.com/hook")
        private String contactDetails;

        @Schema(description = "active flag", required = true, example = "true")
        private Boolean active;

        @Schema(description = "filter preferences as JSON string", example = "{\"category\":[\"Chemistry\"],\"years\":[\">=2000\"]}")
        private String filterPreferences;

        @Schema(description = "last notified timestamp (ISO-8601)", example = "2025-08-01T10:00:15Z")
        private String lastNotifiedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id assigned by the system", example = "TID_SUB_987")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberGetResponse", description = "Subscriber response payload")
    public static class SubscriberGetResponse {
        @Schema(description = "Business id of subscriber", example = "sub-1")
        private String id;

        @Schema(description = "Display name", example = "Nobel Alerts")
        private String name;

        @Schema(description = "active flag", example = "true")
        private Boolean active;

        @Schema(description = "last notified timestamp (ISO-8601)", example = "2025-08-01T10:00:15Z")
        private String lastNotifiedAt;
    }
}