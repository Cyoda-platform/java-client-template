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
import java.util.UUID;

/**
 * MailController - REST controller for managing Mail entities
 * 
 * Provides endpoints for creating, retrieving, updating, and sending emails.
 * All endpoints follow the EntityService pattern and work with EntityWithMetadata.
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

            EntityWithMetadata<Mail> response = entityService.create(mail);
            logger.info("Mail created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating mail", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get mail by technical UUID
     * GET /api/mails/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> getMailById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            EntityWithMetadata<Mail> response = entityService.getById(id, modelSpec, Mail.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting mail by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all mails with optional filtering
     * GET /api/mails?state=PENDING&isHappy=true
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Mail>>> getAllMails(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isHappy) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            
            if (state != null || isHappy != null) {
                // Build search condition for filtering
                List<QueryCondition> conditions = new ArrayList<>();

                if (state != null) {
                    // Note: state filtering would need to be done on metadata, which is more complex
                    // For now, we'll get all and filter in memory (not optimal for large datasets)
                    logger.warn("State filtering not implemented in search - getting all mails");
                }

                if (isHappy != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.isHappy")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(isHappy)));
                }

                if (!conditions.isEmpty()) {
                    GroupCondition condition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    
                    List<EntityWithMetadata<Mail>> mails = entityService.search(modelSpec, condition, Mail.class);
                    
                    // Filter by state if specified (in-memory filtering)
                    if (state != null) {
                        mails = mails.stream()
                                .filter(mail -> state.equals(mail.metadata().getState()))
                                .toList();
                    }
                    
                    return ResponseEntity.ok(mails);
                }
            }
            
            // No filtering - get all mails
            List<EntityWithMetadata<Mail>> mails = entityService.findAll(modelSpec, Mail.class);
            
            // Filter by state if specified (in-memory filtering)
            if (state != null) {
                mails = mails.stream()
                        .filter(mail -> state.equals(mail.metadata().getState()))
                        .toList();
            }
            
            return ResponseEntity.ok(mails);
        } catch (Exception e) {
            logger.error("Error getting all mails", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update mail with optional workflow transition
     * PUT /api/mails/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> updateMail(
            @PathVariable UUID id,
            @RequestBody Mail mail,
            @RequestParam(required = false) String transitionName) {
        try {
            logger.info("Updating mail {} with transition: {}", id, transitionName);

            EntityWithMetadata<Mail> response = entityService.update(id, mail, transitionName);
            logger.info("Mail updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating mail", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Send happy mail
     * POST /api/mails/{id}/send-happy?transitionName=send_happy
     */
    @PostMapping("/{id}/send-happy")
    public ResponseEntity<EntityWithMetadata<Mail>> sendHappyMail(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "send_happy") String transitionName) {
        try {
            logger.info("Sending happy mail for ID: {} with transition: {}", id, transitionName);

            // Get current mail to validate preconditions
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            EntityWithMetadata<Mail> currentMail = entityService.getById(id, modelSpec, Mail.class);
            
            // Validate preconditions
            if (!"HAPPY_READY".equals(currentMail.metadata().getState())) {
                logger.warn("Mail {} is not in HAPPY_READY state, current state: {}", 
                           id, currentMail.metadata().getState());
                return ResponseEntity.badRequest().build();
            }
            
            if (currentMail.entity().getIsHappy() == null || !currentMail.entity().getIsHappy()) {
                logger.warn("Mail {} is not happy: {}", id, currentMail.entity().getIsHappy());
                return ResponseEntity.badRequest().build();
            }

            // Trigger the transition (this will invoke the processor)
            EntityWithMetadata<Mail> response = entityService.update(id, currentMail.entity(), transitionName);
            logger.info("Happy mail sent for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error sending happy mail", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Send gloomy mail
     * POST /api/mails/{id}/send-gloomy?transitionName=send_gloomy
     */
    @PostMapping("/{id}/send-gloomy")
    public ResponseEntity<EntityWithMetadata<Mail>> sendGloomyMail(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "send_gloomy") String transitionName) {
        try {
            logger.info("Sending gloomy mail for ID: {} with transition: {}", id, transitionName);

            // Get current mail to validate preconditions
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            EntityWithMetadata<Mail> currentMail = entityService.getById(id, modelSpec, Mail.class);
            
            // Validate preconditions
            if (!"GLOOMY_READY".equals(currentMail.metadata().getState())) {
                logger.warn("Mail {} is not in GLOOMY_READY state, current state: {}", 
                           id, currentMail.metadata().getState());
                return ResponseEntity.badRequest().build();
            }
            
            if (currentMail.entity().getIsHappy() == null || currentMail.entity().getIsHappy()) {
                logger.warn("Mail {} is not gloomy: {}", id, currentMail.entity().getIsHappy());
                return ResponseEntity.badRequest().build();
            }

            // Trigger the transition (this will invoke the processor)
            EntityWithMetadata<Mail> response = entityService.update(id, currentMail.entity(), transitionName);
            logger.info("Gloomy mail sent for ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error sending gloomy mail", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete mail by technical UUID
     * DELETE /api/mails/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMail(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Mail deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting mail", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
