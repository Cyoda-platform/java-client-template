package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/subscribers", produces = APPLICATION_JSON)
@Tag(name = "Subscriber", description = "Subscriber entity operations (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Create a Subscriber entity and trigger its workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = APPLICATION_JSON)
    public ResponseEntity<CreateSubscriberResponse> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(request.getId());
            entity.setName(request.getName());
            entity.setContactType(request.getContactType());
            entity.setContactAddress(request.getContactAddress());
            entity.setActive(request.getActive());
            entity.setPreferredPayload(request.getPreferredPayload());

            if (request.getFilters() != null) {
                Subscriber.Filters f = new Subscriber.Filters();
                f.setCategories(request.getFilters().getCategories());
                entity.setFilters(f);
            }

            if (request.getRetryPolicy() != null) {
                Subscriber.RetryPolicy rp = new Subscriber.RetryPolicy();
                rp.setBackoffSeconds(request.getRetryPolicy().getBackoffSeconds());
                rp.setMaxAttempts(request.getRetryPolicy().getMaxAttempts());
                entity.setRetryPolicy(rp);
            }

            // Proxy call to entity service
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();

            CreateSubscriberResponse resp = new CreateSubscriberResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}")
    public ResponseEntity<SubscriberResponse> getSubscriberByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID techId = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    techId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }

            SubscriberResponse resp = objectMapper.convertValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get subscriber: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving subscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving subscriber", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving subscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Request and Response DTOs

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-42")
        private String id;

        @Schema(description = "Name of the subscriber", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type (email or webhook)", example = "webhook")
        private String contactType;

        @Schema(description = "Contact address (email or webhook URL)", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Whether subscriber is active", example = "true")
        private Boolean active;

        @Schema(description = "Filters object (optional)")
        private FiltersDto filters;

        @Schema(description = "Preferred payload (summary / full)", example = "summary")
        private String preferredPayload;

        @Schema(description = "Retry policy overrides (optional)")
        private RetryPolicyDto retryPolicy;

        @Data
        @Schema(name = "Filters", description = "Subscriber filters")
        public static class FiltersDto {
            @Schema(description = "Categories filter", example = "[\"Chemistry\",\"Physics\"]")
            private java.util.List<String> categories;
        }

        @Data
        @Schema(name = "RetryPolicy", description = "Retry policy overrides")
        public static class RetryPolicyDto {
            @Schema(description = "Backoff seconds between retries", example = "60")
            private Integer backoffSeconds;

            @Schema(description = "Maximum retry attempts", example = "3")
            private Integer maxAttempts;
        }
    }

    @Data
    @Schema(name = "CreateSubscriberResponse", description = "Response after creating a Subscriber")
    public static class CreateSubscriberResponse {
        @Schema(description = "Technical id assigned to the subscriber", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber representation returned by GET")
    public static class SubscriberResponse {
        @Schema(description = "Technical id", example = "8a7f3a6e-1c2b-4d5e-9f00-123456789abc")
        private String technicalId;

        @Schema(description = "Business id", example = "sub-42")
        private String id;

        @Schema(description = "Name", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Contact type", example = "webhook")
        private String contactType;

        @Schema(description = "Contact address", example = "https://example.com/webhook")
        private String contactAddress;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Filters")
        private FiltersDto filters;

        @Schema(description = "Preferred payload", example = "summary")
        private String preferredPayload;

        @Schema(description = "Retry policy")
        private RetryPolicyDto retryPolicy;

        @Data
        @Schema(name = "Filters", description = "Subscriber filters")
        public static class FiltersDto {
            @Schema(description = "Categories filter", example = "[\"Chemistry\",\"Physics\"]")
            private java.util.List<String> categories;
        }

        @Data
        @Schema(name = "RetryPolicy", description = "Retry policy")
        public static class RetryPolicyDto {
            @Schema(description = "Backoff seconds between retries", example = "60")
            private Integer backoffSeconds;

            @Schema(description = "Maximum retry attempts", example = "3")
            private Integer maxAttempts;
        }
    }
}