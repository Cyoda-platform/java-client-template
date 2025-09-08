package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * EmailDeliveryController - REST API controller for email delivery management
 * Base Path: /api/deliveries
 */
@RestController
@RequestMapping("/api/deliveries")
@CrossOrigin(origins = "*")
public class EmailDeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailDeliveryController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get delivery details
     * GET /api/deliveries/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailDelivery>> getDelivery(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            EntityWithMetadata<EmailDelivery> response = entityService.getById(id, modelSpec, EmailDelivery.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailDelivery by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List deliveries with filtering
     * GET /api/deliveries
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailDelivery>>> getAllDeliveries(
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String subscriberId,
            @RequestParam(required = false) String deliveryStatus) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            
            if (campaignId != null) {
                SimpleCondition campaignCondition = new SimpleCondition()
                        .withJsonPath("$.campaignId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(campaignId));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(campaignCondition));

                List<EntityWithMetadata<EmailDelivery>> entities = entityService.search(modelSpec, condition, EmailDelivery.class);
                return ResponseEntity.ok(entities);
            } else if (deliveryStatus != null) {
                SimpleCondition statusCondition = new SimpleCondition()
                        .withJsonPath("$.deliveryStatus")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(deliveryStatus));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(statusCondition));

                List<EntityWithMetadata<EmailDelivery>> entities = entityService.search(modelSpec, condition, EmailDelivery.class);
                return ResponseEntity.ok(entities);
            } else {
                List<EntityWithMetadata<EmailDelivery>> entities = entityService.findAll(modelSpec, EmailDelivery.class);
                return ResponseEntity.ok(entities);
            }
        } catch (Exception e) {
            logger.error("Error getting all EmailDeliveries", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Track email open event
     * PUT /api/deliveries/{id}/track-open
     * Transition: DELIVERED → OPENED (EmailDeliveryOpenProcessor)
     */
    @PutMapping("/{id}/track-open")
    public ResponseEntity<EntityWithMetadata<EmailDelivery>> trackOpen(
            @PathVariable UUID id,
            @RequestBody TrackOpenRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            EntityWithMetadata<EmailDelivery> current = entityService.getById(id, modelSpec, EmailDelivery.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EmailDelivery delivery = current.entity();
            delivery.setOpenedDate(request.getOpenedDate() != null ? request.getOpenedDate() : LocalDateTime.now());

            EntityWithMetadata<EmailDelivery> response = entityService.update(id, delivery, "transition_to_opened");
            logger.info("EmailDelivery {} opened at {}", id, delivery.getOpenedDate());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error tracking open for EmailDelivery", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Track email click event
     * PUT /api/deliveries/{id}/track-click
     * Transition: OPENED → CLICKED (EmailDeliveryClickProcessor)
     */
    @PutMapping("/{id}/track-click")
    public ResponseEntity<EntityWithMetadata<EmailDelivery>> trackClick(
            @PathVariable UUID id,
            @RequestBody TrackClickRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            EntityWithMetadata<EmailDelivery> current = entityService.getById(id, modelSpec, EmailDelivery.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EmailDelivery delivery = current.entity();
            delivery.setClickedDate(request.getClickedDate() != null ? request.getClickedDate() : LocalDateTime.now());

            EntityWithMetadata<EmailDelivery> response = entityService.update(id, delivery, "transition_to_clicked");
            logger.info("EmailDelivery {} clicked at {} on URL: {}", id, delivery.getClickedDate(), request.getClickedUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error tracking click for EmailDelivery", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark delivery as bounced
     * PUT /api/deliveries/{id}/mark-bounced
     * Transition: SENT → BOUNCED (EmailDeliveryBounceProcessor)
     */
    @PutMapping("/{id}/mark-bounced")
    public ResponseEntity<EntityWithMetadata<EmailDelivery>> markBounced(
            @PathVariable UUID id,
            @RequestBody MarkBouncedRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            EntityWithMetadata<EmailDelivery> current = entityService.getById(id, modelSpec, EmailDelivery.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EmailDelivery delivery = current.entity();
            delivery.setErrorMessage(request.getBounceReason());

            EntityWithMetadata<EmailDelivery> response = entityService.update(id, delivery, "transition_to_bounced");
            logger.info("EmailDelivery {} marked as bounced: {}", id, request.getBounceReason());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error marking EmailDelivery as bounced", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class TrackOpenRequest {
        private LocalDateTime openedDate;
        private String userAgent;
        private String transitionName = "track-open";
    }

    @Getter
    @Setter
    public static class TrackClickRequest {
        private LocalDateTime clickedDate;
        private String clickedUrl;
        private String transitionName = "track-click";
    }

    @Getter
    @Setter
    public static class MarkBouncedRequest {
        private String bounceReason;
        private String bounceType;
        private String transitionName = "mark-bounced";
    }
}
