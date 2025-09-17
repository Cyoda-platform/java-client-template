package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
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
 * EmailNotificationController
 * REST endpoints for managing email notifications and report delivery.
 */
@RestController
@RequestMapping("/api/emailnotification")
@CrossOrigin(origins = "*")
public class EmailNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailNotificationController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new email notification entity
     * POST /api/emailnotification
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailNotification>> createEmailNotification(@RequestBody EmailNotification entity) {
        try {
            // Set creation timestamp
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailNotification> response = entityService.create(entity);
            logger.info("EmailNotification created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email notification by technical UUID
     * GET /api/emailnotification/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailNotification>> getEmailNotificationById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);
            EntityWithMetadata<EmailNotification> response = entityService.getById(id, modelSpec, EmailNotification.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailNotification by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get email notification by business identifier
     * GET /api/emailnotification/business/{notificationId}
     */
    @GetMapping("/business/{notificationId}")
    public ResponseEntity<EntityWithMetadata<EmailNotification>> getEmailNotificationByBusinessId(@PathVariable String notificationId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);
            EntityWithMetadata<EmailNotification> response = entityService.findByBusinessId(
                    modelSpec, notificationId, "notificationId", EmailNotification.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailNotification by business ID: {}", notificationId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update email notification with optional workflow transition
     * PUT /api/emailnotification/{uuid}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailNotification>> updateEmailNotification(
            @PathVariable UUID id,
            @RequestBody EmailNotification entity,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailNotification> response = entityService.update(id, entity, transition);
            logger.info("EmailNotification updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger state transition for email notification
     * PUT /api/emailnotification/{uuid}/transition
     */
    @PutMapping("/{id}/transition")
    public ResponseEntity<EntityWithMetadata<EmailNotification>> triggerTransition(
            @PathVariable UUID id,
            @RequestBody TransitionRequest request) {
        try {
            // Get current entity
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);
            EntityWithMetadata<EmailNotification> current = entityService.getById(id, modelSpec, EmailNotification.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EmailNotification entity = current.entity();
            entity.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailNotification> response = entityService.update(id, entity, request.getTransitionName());
            logger.info("EmailNotification transition triggered: {} for ID: {}", request.getTransitionName(), id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error triggering transition for EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete email notification by technical UUID
     * DELETE /api/emailnotification/{uuid}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailNotification(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("EmailNotification deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all email notifications
     * GET /api/emailnotification
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailNotification>>> getAllEmailNotifications() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);
            List<EntityWithMetadata<EmailNotification>> entities = entityService.findAll(modelSpec, EmailNotification.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all EmailNotifications", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search email notifications by analysis ID
     * GET /api/emailnotification/search?analysisId=text
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<EmailNotification>>> searchEmailNotificationsByAnalysisId(
            @RequestParam String analysisId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(analysisId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<EmailNotification>> entities = entityService.search(modelSpec, condition, EmailNotification.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching EmailNotifications by analysisId: {}", analysisId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email notifications by sent status
     * GET /api/emailnotification/sent/{sent}
     */
    @GetMapping("/sent/{sent}")
    public ResponseEntity<List<EntityWithMetadata<EmailNotification>>> getEmailNotificationsBySentStatus(
            @PathVariable boolean sent) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.sentAt")
                    .withOperation(sent ? Operation.NOT_NULL : Operation.IS_NULL)
                    .withValue(null);

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<EmailNotification>> entities = entityService.search(modelSpec, condition, EmailNotification.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting EmailNotifications by sent status: {}", sent, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email notifications by subscriber email
     * GET /api/emailnotification/subscriber/{email}
     */
    @GetMapping("/subscriber/{email}")
    public ResponseEntity<List<EntityWithMetadata<EmailNotification>>> getEmailNotificationsBySubscriber(
            @PathVariable String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotification.ENTITY_NAME).withVersion(EmailNotification.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.subscriberEmails[*]")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(email));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<EmailNotification>> entities = entityService.search(modelSpec, condition, EmailNotification.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting EmailNotifications by subscriber: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for transition requests
     */
    @Getter
    @Setter
    public static class TransitionRequest {
        private String transitionName;
    }
}
