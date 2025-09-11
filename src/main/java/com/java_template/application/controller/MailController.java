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
 * MailController - REST API endpoints for managing Mail entities
 * 
 * This controller provides CRUD operations and workflow transitions for mail processing.
 * It handles mail entities throughout their workflow lifecycle from creation to delivery.
 * 
 * Base path: /api/mail
 */
@RestController
@RequestMapping("/api/mail")
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
     * POST /api/mail
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Mail>> createMail(@RequestBody Mail mail) {
        try {
            // Validate input
            if (!mail.isValid()) {
                logger.warn("Invalid mail entity provided for creation");
                return ResponseEntity.badRequest().build();
            }

            // Validate mail list size (max 100 recipients as per requirements)
            if (mail.getMailList().size() > 100) {
                logger.warn("Mail list exceeds maximum of 100 recipients: {}", mail.getMailList().size());
                return ResponseEntity.badRequest().build();
            }

            // Create entity - workflow will start automatically
            EntityWithMetadata<Mail> response = entityService.create(mail);
            logger.info("Mail entity created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating mail entity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get mail entity by technical UUID (FASTEST method)
     * GET /api/mail/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> getMailById(@PathVariable UUID id) {
        try {
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
     * GET /api/mail?state=SENT&isHappy=true
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Mail>>> getAllMails(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isHappy) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            
            if (state == null && isHappy == null) {
                // No filters - get all entities
                List<EntityWithMetadata<Mail>> entities = entityService.findAll(modelSpec, Mail.class);
                return ResponseEntity.ok(entities);
            } else {
                // Apply filters using search
                List<SimpleCondition> conditions = new ArrayList<>();
                
                if (state != null && !state.trim().isEmpty()) {
                    // Note: state filtering would need to be done on metadata, which is more complex
                    // For now, we'll filter by entity fields only
                    logger.debug("State filtering requested but not implemented in this version: {}", state);
                }
                
                if (isHappy != null) {
                    conditions.add(new SimpleCondition()
                            .withJsonPath("$.isHappy")
                            .withOperation(Operation.EQUALS)
                            .withValue(objectMapper.valueToTree(isHappy)));
                }
                
                if (conditions.isEmpty()) {
                    List<EntityWithMetadata<Mail>> entities = entityService.findAll(modelSpec, Mail.class);
                    return ResponseEntity.ok(entities);
                } else {
                    GroupCondition condition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(new ArrayList<>(conditions));
                    
                    List<EntityWithMetadata<Mail>> entities = entityService.search(modelSpec, condition, Mail.class);
                    return ResponseEntity.ok(entities);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting all mail entities", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update mail entity with optional workflow transition
     * PUT /api/mail/{id}?transition=retry
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Mail>> updateMail(
            @PathVariable UUID id,
            @RequestBody Mail mail,
            @RequestParam(required = false) String transition) {
        try {
            // Validate input
            if (!mail.isValid()) {
                logger.warn("Invalid mail entity provided for update");
                return ResponseEntity.badRequest().build();
            }

            // Validate mail list size (max 100 recipients as per requirements)
            if (mail.getMailList().size() > 100) {
                logger.warn("Mail list exceeds maximum of 100 recipients: {}", mail.getMailList().size());
                return ResponseEntity.badRequest().build();
            }

            // Validate transition if provided
            if (transition != null && !transition.trim().isEmpty()) {
                // Only "retry" transition is supported (from FAILED to PENDING)
                if (!"retry".equalsIgnoreCase(transition.trim())) {
                    logger.warn("Invalid transition requested: {}", transition);
                    return ResponseEntity.badRequest().build();
                }
            }

            // Update entity with optional transition
            EntityWithMetadata<Mail> response = entityService.update(id, mail, transition);
            logger.info("Mail entity updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating mail entity with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete mail entity by technical UUID
     * DELETE /api/mail/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMail(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Mail entity deleted with ID: {}", id);
            
            Map<String, Object> response = Map.of(
                "message", "Mail entity deleted successfully",
                "uuid", id.toString()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting mail entity with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search mail entities by advanced criteria
     * POST /api/mail/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Mail>>> searchMails(@RequestBody SearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Mail.ENTITY_NAME).withVersion(Mail.ENTITY_VERSION);
            
            List<SimpleCondition> conditions = new ArrayList<>();
            
            if (searchRequest.getIsHappy() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isHappy")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsHappy())));
            }
            
            if (searchRequest.getEmailContains() != null && !searchRequest.getEmailContains().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.mailList[*]")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getEmailContains())));
            }
            
            if (conditions.isEmpty()) {
                List<EntityWithMetadata<Mail>> entities = entityService.findAll(modelSpec, Mail.class);
                return ResponseEntity.ok(entities);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(new ArrayList<>(conditions));
                
                List<EntityWithMetadata<Mail>> entities = entityService.search(modelSpec, condition, Mail.class);
                return ResponseEntity.ok(entities);
            }
        } catch (Exception e) {
            logger.error("Error performing advanced search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for advanced search requests
     */
    @Getter
    @Setter
    public static class SearchRequest {
        private Boolean isHappy;
        private String emailContains;
    }
}
