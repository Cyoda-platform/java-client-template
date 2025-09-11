package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.mail.version_1.Mail;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MailController - REST controller for managing Mail entities
 * 
 * Provides endpoints for creating, reading, updating, and managing mail entities
 * through their workflow lifecycle. All operations are thin proxies to EntityService.
 */
@RestController
@RequestMapping("/api/mails")
@CrossOrigin(origins = "*")
public class MailController {

    private static final Logger logger = LoggerFactory.getLogger(MailController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MailController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new mail entity
     * POST /api/mails
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Mail>> createMail(@RequestBody Mail mail) {
        try {
            logger.info("Creating new mail entity with isHappy: {}, recipients: {}", 
                       mail.getIsHappy(), mail.getMailList() != null ? mail.getMailList().size() : 0);

            // Validate the mail entity
            if (!mail.isValid()) {
                logger.error("Invalid mail entity provided");
                return ResponseEntity.badRequest().build();
            }

            // Create the mail entity
            EntityWithMetadata<Mail> response = entityService.create(mail);
            logger.info("Mail entity created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating mail entity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get mail entity by technical UUID
     * GET /api/mails/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> getMailById(@PathVariable UUID id) {
        try {
            logger.debug("Getting mail entity by ID: {}", id);
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            EntityWithMetadata<Mail> response = entityService.getById(id, modelSpec, Mail.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting mail entity by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all mail entities with optional filtering
     * GET /api/mails?state=PENDING&isHappy=true
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Mail>>> getAllMails(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isHappy) {
        try {
            logger.debug("Getting all mail entities with filters - state: {}, isHappy: {}", state, isHappy);
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);

            List<EntityWithMetadata<Mail>> entities;

            // If no filters, return all entities
            if (state == null && isHappy == null) {
                entities = entityService.findAll(modelSpec, Mail.class);
            } else {
                // Build search conditions based on filters
                List<QueryCondition> conditions = new ArrayList<>();

                if (isHappy != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.isHappy")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(isHappy)));
                }

                // Note: state filtering would require metadata search which is more complex
                // For now, we'll filter by entity fields only
                if (!conditions.isEmpty()) {
                    GroupCondition condition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    entities = entityService.search(modelSpec, condition, Mail.class);
                } else {
                    entities = entityService.findAll(modelSpec, Mail.class);
                }

                // Post-filter by state if needed (since metadata filtering is complex)
                if (state != null) {
                    entities = entities.stream()
                            .filter(entity -> state.equalsIgnoreCase(entity.metadata().getState()))
                            .toList();
                }
            }

            return ResponseEntity.ok(entities);
        } catch (Exception e) {
            logger.error("Error getting all mail entities", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update mail entity with optional workflow transition
     * PUT /api/mails/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> updateMail(
            @PathVariable UUID id,
            @RequestBody Mail mail,
            @RequestParam(required = false) String transitionName) {
        try {
            logger.info("Updating mail entity with ID: {}, transition: {}", id, transitionName);

            // Validate the mail entity
            if (!mail.isValid()) {
                logger.error("Invalid mail entity provided for update");
                return ResponseEntity.badRequest().build();
            }

            // Update the mail entity with optional transition
            EntityWithMetadata<Mail> response = entityService.update(id, mail, transitionName);
            logger.info("Mail entity updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating mail entity with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry failed mail by transitioning from FAILED to PENDING state
     * POST /api/mails/{id}/retry
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<EntityWithMetadata<Mail>> retryFailedMail(@PathVariable UUID id) {
        try {
            logger.info("Retrying failed mail with ID: {}", id);

            // Get the current mail entity
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            EntityWithMetadata<Mail> currentMail = entityService.getById(id, modelSpec, Mail.class);

            // Check if the mail is in FAILED state
            if (!"failed".equalsIgnoreCase(currentMail.metadata().getState())) {
                logger.error("Mail with ID {} is not in FAILED state, current state: {}",
                           id, currentMail.metadata().getState());
                return ResponseEntity.badRequest().build();
            }

            // Trigger manual transition from FAILED to PENDING
            EntityWithMetadata<Mail> response = entityService.update(id, currentMail.entity(), "transition_to_pending");
            logger.info("Mail entity retry initiated for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrying failed mail with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete mail entity by technical UUID
     * DELETE /api/mails/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMail(@PathVariable UUID id) {
        try {
            logger.info("Deleting mail entity with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Mail entity deleted with ID: {}", id);

            Map<String, String> response = Map.of("message", "Mail entity deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting mail entity with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
