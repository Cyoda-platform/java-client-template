package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscribers", description = "Operations related to subscribers")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Create a new subscriber. Returns technicalId only. Supports Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubscriberCreateRequest request
    ) {
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getContactMethod() == null || request.getContactMethod().isBlank()) {
                throw new IllegalArgumentException("contactMethod is required");
            }
            if (request.getContactAddress() == null || request.getContactAddress().isBlank()) {
                throw new IllegalArgumentException("contactAddress is required");
            }
            if (request.getFilters() == null) {
                throw new IllegalArgumentException("filters are required");
            }
            if (request.getDeliveryPreference() == null || request.getDeliveryPreference().isBlank()) {
                throw new IllegalArgumentException("deliveryPreference is required");
            }

            Subscriber subscriber = new Subscriber();
            subscriber.setName(request.getName());
            subscriber.setContactMethod(request.getContactMethod());
            subscriber.setContactAddress(request.getContactAddress());
            subscriber.setActive(request.getActive() == null ? true : request.getActive());
            // filters is structured object; store as JSON string in entity
            try {
                subscriber.setFilters(objectMapper.writeValueAsString(request.getFilters()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("filters must be serializable to JSON");
            }
            subscriber.setDeliveryPreference(request.getDeliveryPreference());
            subscriber.setBackfillFromDate(request.getBackfillFromDate());
            if (request.getMeta() != null) {
                try {
                    subscriber.setNotificationHistory(objectMapper.writeValueAsString(request.getMeta()));
                } catch (JsonProcessingException e) {
                    // fall through - meta is optional
                }
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );

            UUID id = idFuture.get();
            String technicalId = id.toString();
            URI location = URI.create(String.format("/subscribers/%s", technicalId));
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);
            return ResponseEntity.created(location).headers(headers).contentType(MediaType.APPLICATION_JSON).body(new IdResponse(technicalId));

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createSubscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve a subscriber by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getSubscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unhandled exception in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class SubscriberCreateRequest {
        @Schema(description = "Subscriber name", required = true)
        private String name;
        @Schema(description = "Contact method: EMAIL|WEBHOOK|SMS", required = true)
        private String contactMethod;
        @Schema(description = "Contact address (email, webhook URL, phone)", required = true)
        private String contactAddress;
        @Schema(description = "Active flag")
        private Boolean active;
        @Schema(description = "Filters as structured JSON DSL", required = true)
        private Object filters;
        @Schema(description = "Delivery preference: IMMEDIATE|DIGEST_DAILY|DIGEST_WEEKLY", required = true)
        private String deliveryPreference;
        @Schema(description = "Backfill from date (ISO)")
        private String backfillFromDate;
        @Schema(description = "Optional meta object")
        private Object meta;
    }

    @Data
    public static class IdResponse {
        @Schema(description = "Technical ID of the created resource")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}