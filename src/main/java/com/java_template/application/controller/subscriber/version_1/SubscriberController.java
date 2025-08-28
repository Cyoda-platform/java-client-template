package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/subscribers/v1")
@Tag(name = "Subscriber", description = "Subscriber entity proxy controller")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Subscriber", description = "Creates a Subscriber entity and returns the technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create request", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic request format validation
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getContactEmail() == null || request.getContactEmail().isBlank()) {
                throw new IllegalArgumentException("contact_email is required");
            }
            if (request.getDeliveryPreference() == null || request.getDeliveryPreference().isBlank()) {
                throw new IllegalArgumentException("delivery_preference is required");
            }

            Subscriber entity = new Subscriber();
            // Map request fields to entity. No business logic is applied here.
            entity.setName(request.getName());
            entity.setContactEmail(request.getContactEmail());
            entity.setDeliveryPreference(request.getDeliveryPreference());
            entity.setWebhookUrl(request.getWebhookUrl());
            entity.setActive(request.getActive());
            // subscriberId (business id) is intentionally not set here; workflows may set or validate it.

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
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createSubscriber: {}", cause.getMessage());
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createSubscriber: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createSubscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during createSubscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieves a Subscriber entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<SubscriberResponse> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(404).build();
            }

            JsonNode dataNode = dataPayload.getData();
            SubscriberResponse response = objectMapper.treeToValue(dataNode, SubscriberResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found: {}", cause.getMessage());
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getSubscriber: {}", cause.getMessage());
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getSubscriber", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during getSubscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Schema(name = "SubscriberRequest", description = "Payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Subscriber name", example = "Research Team")
        private String name;

        @Schema(description = "Contact email", example = "team@example.com", name = "contact_email")
        private String contactEmail;

        @Schema(description = "Webhook URL", example = "https://example.com/webhook", name = "webhook_url")
        private String webhookUrl;

        @Schema(description = "Delivery preference (email or webhook)", example = "webhook", name = "delivery_preference")
        private String deliveryPreference;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        public SubscriberRequest() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getDeliveryPreference() {
            return deliveryPreference;
        }

        public void setDeliveryPreference(String deliveryPreference) {
            this.deliveryPreference = deliveryPreference;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    @Schema(name = "TechnicalIdResponse", description = "Response containing generated technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of the persisted entity", example = "550e8400-e29b-41d4-a716-446655440000", name = "technicalId")
        private String technicalId;

        public TechnicalIdResponse() {
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Schema(name = "SubscriberResponse", description = "Subscriber entity representation returned by GET")
    public static class SubscriberResponse {
        @Schema(description = "Business subscriber id", example = "s-123", name = "subscriberId")
        private String subscriberId;

        @Schema(description = "Subscriber name", example = "Research Team", name = "name")
        private String name;

        @Schema(description = "Contact email", example = "team@example.com", name = "contact_email")
        private String contactEmail;

        @Schema(description = "Webhook URL", example = "https://example.com/webhook", name = "webhook_url")
        private String webhookUrl;

        @Schema(description = "Active flag", example = "true", name = "active")
        private Boolean active;

        @Schema(description = "Delivery preference (email or webhook)", example = "webhook", name = "delivery_preference")
        private String deliveryPreference;

        public SubscriberResponse() {
        }

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public String getDeliveryPreference() {
            return deliveryPreference;
        }

        public void setDeliveryPreference(String deliveryPreference) {
            this.deliveryPreference = deliveryPreference;
        }
    }
}