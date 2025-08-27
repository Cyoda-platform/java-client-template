package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/subscribers/v1")
@Tag(name = "Subscriber", description = "Subscriber entity proxy controller (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register Subscriber", description = "Registers a new Subscriber. Returns technicalId (UUID) of the stored entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createSubscriber(
            @RequestBody SubscriberCreateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setActive(request.getActive());
            entity.setContactType(request.getContactType());
            entity.setPreferredPayload(request.getPreferredPayload());
            entity.setLastNotifiedAt(request.getLastNotifiedAt());

            if (request.getContactDetails() != null) {
                Subscriber.ContactDetails cd = new Subscriber.ContactDetails();
                cd.setUrl(request.getContactDetails().getUrl());
                entity.setContactDetails(cd);
            }

            if (request.getFilters() != null) {
                Subscriber.Filters f = new Subscriber.Filters();
                f.setCategories(request.getFilters().getCategories());
                f.setYears(request.getFilters().getYears());
                entity.setFilters(f);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createSubscriber: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createSubscriber: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createSubscriber", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createSubscriber", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieves a stored Subscriber by its technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    tid
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }

            SubscriberResponse resp = objectMapper.treeToValue(node, SubscriberResponse.class);
            resp.setTechnicalId(technicalId);

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getSubscriberById: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getSubscriberById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getSubscriberById", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "SubscriberCreateRequest", description = "Request payload to register a Subscriber")
    public static class SubscriberCreateRequest {
        @Schema(description = "Business subscriber identifier", example = "sub-abc")
        private String subscriberId;

        @Schema(description = "Contact type (email|webhook|other)", example = "webhook")
        private String contactType;

        @Schema(description = "Contact details (url for webhook or email)")
        private ContactDetailsDto contactDetails;

        @Schema(description = "Whether the subscriber is active", example = "true")
        private Boolean active;

        @Schema(description = "Optional filters for notifications")
        private FiltersDto filters;

        @Schema(description = "Preferred payload (full|summary)", example = "full")
        private String preferredPayload;

        @Schema(description = "Last notified timestamp (ISO-8601)", example = "2025-08-27T09:00:00Z")
        private String lastNotifiedAt;

        @Data
        @Schema(name = "ContactDetailsDto", description = "Contact details")
        public static class ContactDetailsDto {
            @Schema(description = "URL for webhook or email address", example = "https://example.com/webhook")
            private String url;
        }

        @Data
        @Schema(name = "FiltersDto", description = "Filters for subscriber notifications")
        public static class FiltersDto {
            @Schema(description = "List of categories to filter on", example = "[\"Chemistry\"]")
            private java.util.List<String> categories;

            @Schema(description = "List of years to filter on", example = "[\"2010\"]")
            private java.util.List<String> years;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id of a created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID of the persisted entity", example = "9f8d7c3a-1b2c-4d5e-9f0a-123456789abc")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber entity response")
    public static class SubscriberResponse {
        @Schema(description = "Technical UUID of the entity", example = "9f8d7c3a-1b2c-4d5e-9f0a-123456789abc")
        private String technicalId;

        @Schema(description = "Business subscriber identifier", example = "sub-abc")
        private String subscriberId;

        @Schema(description = "Contact type (email|webhook|other)", example = "webhook")
        private String contactType;

        @Schema(description = "Contact details (url for webhook or email)")
        private ContactDetailsDto contactDetails;

        @Schema(description = "Whether the subscriber is active", example = "true")
        private Boolean active;

        @Schema(description = "Optional filters for notifications")
        private FiltersDto filters;

        @Schema(description = "Preferred payload (full|summary)", example = "full")
        private String preferredPayload;

        @Schema(description = "Last notified timestamp (ISO-8601)", example = "2025-08-27T09:00:00Z")
        private String lastNotifiedAt;

        @Data
        @Schema(name = "ContactDetailsDto", description = "Contact details")
        public static class ContactDetailsDto {
            @Schema(description = "URL for webhook or email address", example = "https://example.com/webhook")
            private String url;
        }

        @Data
        @Schema(name = "FiltersDto", description = "Filters for subscriber notifications")
        public static class FiltersDto {
            @Schema(description = "List of categories to filter on", example = "[\"Chemistry\"]")
            private java.util.List<String> categories;

            @Schema(description = "List of years to filter on", example = "[\"2010\"]")
            private java.util.List<String> years;
        }
    }
}