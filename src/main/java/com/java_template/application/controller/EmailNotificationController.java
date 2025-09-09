package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.email_notification_entity.version_1.EmailNotificationEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EmailNotificationController - REST controller for EmailNotification entity operations
 * Base Path: /api/notifications
 */
@RestController
@RequestMapping("/api/notifications")
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
     * POST /api/notifications
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailNotificationEntity>> createNotification(@RequestBody EmailNotificationEntity entity) {
        try {
            // Generate notification ID if not provided
            if (entity.getNotificationId() == null || entity.getNotificationId().trim().isEmpty()) {
                entity.setNotificationId("notif-" + System.currentTimeMillis());
            }

            // Set default scheduled time if not provided (5 minutes from now)
            if (entity.getScheduledTime() == null) {
                entity.setScheduledTime(LocalDateTime.now().plusMinutes(5));
            }

            // Initialize delivery tracking
            if (entity.getDeliveryAttempts() == null) {
                entity.setDeliveryAttempts(0);
            }

            EntityWithMetadata<EmailNotificationEntity> response = entityService.create(entity);
            logger.info("EmailNotification created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email notification by technical UUID
     * GET /api/notifications/{uuid}
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<EmailNotificationEntity>> getNotificationById(@PathVariable UUID uuid) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotificationEntity.ENTITY_NAME).withVersion(EmailNotificationEntity.ENTITY_VERSION);
            EntityWithMetadata<EmailNotificationEntity> response = entityService.getById(uuid, modelSpec, EmailNotificationEntity.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailNotification by ID: {}", uuid, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update email notification entity with optional state transition
     * PUT /api/notifications/{uuid}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<EmailNotificationEntity>> updateNotification(
            @PathVariable UUID uuid,
            @RequestBody EmailNotificationEntity entity,
            @RequestParam(required = false) String transitionName) {
        try {
            EntityWithMetadata<EmailNotificationEntity> response = entityService.update(uuid, entity, transitionName);
            logger.info("EmailNotification updated with ID: {}", uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete email notification entity
     * DELETE /api/notifications/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID uuid) {
        try {
            entityService.deleteById(uuid);
            logger.info("EmailNotification deleted with ID: {}", uuid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting EmailNotification", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all email notifications with optional filtering
     * GET /api/notifications?reportId=report-123&recipientEmail=test@example.com&status=sent
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailNotificationEntity>>> getAllNotifications(
            @RequestParam(required = false) String reportId,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotificationEntity.ENTITY_NAME).withVersion(EmailNotificationEntity.ENTITY_VERSION);
            
            // Build search conditions
            List<QueryCondition> conditions = new ArrayList<>();
            
            if (reportId != null && !reportId.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.reportId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(reportId)));
            }

            if (recipientEmail != null && !recipientEmail.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.recipientEmail")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(recipientEmail)));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<EmailNotificationEntity>> entities = entityService.search(modelSpec, condition, EmailNotificationEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all EmailNotifications", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search email notifications by criteria
     * POST /api/notifications/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<EmailNotificationEntity>>> searchNotifications(@RequestBody NotificationSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailNotificationEntity.ENTITY_NAME).withVersion(EmailNotificationEntity.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getReportId() != null && !searchRequest.getReportId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.reportId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getReportId())));
            }

            if (searchRequest.getRecipientEmail() != null && !searchRequest.getRecipientEmail().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.recipientEmail")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getRecipientEmail())));
            }

            if (searchRequest.getMaxDeliveryAttempts() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.deliveryAttempts")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxDeliveryAttempts())));
            }

            GroupCondition condition = null;
            if (!conditions.isEmpty()) {
                condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
            }

            List<EntityWithMetadata<EmailNotificationEntity>> entities = entityService.search(modelSpec, condition, EmailNotificationEntity.class);
            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error searching EmailNotifications", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for notification search requests
     */
    @Getter
    @Setter
    public static class NotificationSearchRequest {
        private String reportId;
        private String recipientEmail;
        private Integer maxDeliveryAttempts;
        private LocalDateTime scheduledAfter;
        private LocalDateTime scheduledBefore;
    }
}
