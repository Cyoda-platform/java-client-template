package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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
 * EmailCampaignController - REST controller for email campaign management
 * 
 * Provides endpoints for managing email campaigns including creation,
 * execution, and reporting.
 */
@RestController
@RequestMapping("/ui/campaign")
@CrossOrigin(origins = "*")
public class EmailCampaignController {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new email campaign
     * POST /ui/campaign
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> createEmailCampaign(@RequestBody EmailCampaign campaign) {
        try {
            // Set creation timestamp
            campaign.setCreatedAt(LocalDateTime.now());
            campaign.setUpdatedAt(LocalDateTime.now());
            
            // Initialize counters if not provided
            if (campaign.getRecipientCount() == null) campaign.setRecipientCount(0);
            if (campaign.getSuccessCount() == null) campaign.setSuccessCount(0);
            if (campaign.getFailureCount() == null) campaign.setFailureCount(0);
            
            // Set default status
            if (campaign.getStatus() == null) {
                campaign.setStatus(EmailCampaign.CampaignStatus.DRAFT);
            }

            EntityWithMetadata<EmailCampaign> response = entityService.create(campaign);
            logger.info("EmailCampaign created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email campaign by technical UUID
     * GET /ui/campaign/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getEmailCampaignById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> response = entityService.getById(id, modelSpec, EmailCampaign.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailCampaign by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get email campaign by business identifier (campaignId)
     * GET /ui/campaign/business/{campaignId}
     */
    @GetMapping("/business/{campaignId}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getEmailCampaignByBusinessId(@PathVariable String campaignId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> response = entityService.findByBusinessId(
                    modelSpec, campaignId, "campaignId", EmailCampaign.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailCampaign by business ID: {}", campaignId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update email campaign with optional workflow transition
     * PUT /ui/campaign/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> updateEmailCampaign(
            @PathVariable UUID id,
            @RequestBody EmailCampaign campaign,
            @RequestParam(required = false) String transition) {
        try {
            // Set update timestamp
            campaign.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, campaign, transition);
            logger.info("EmailCampaign updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete email campaign by technical UUID
     * DELETE /ui/campaign/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailCampaign(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("EmailCampaign deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all email campaigns
     * GET /ui/campaign
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> getAllEmailCampaigns() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.findAll(modelSpec, EmailCampaign.class);
            return ResponseEntity.ok(campaigns);
        } catch (Exception e) {
            logger.error("Error getting all EmailCampaigns", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Send email campaign
     * POST /ui/campaign/{id}/send
     */
    @PostMapping("/{id}/send")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> sendEmailCampaign(@PathVariable UUID id) {
        try {
            // Get current campaign
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update status and trigger sending
            EmailCampaign campaign = current.entity();
            campaign.setStatus(EmailCampaign.CampaignStatus.SENDING);
            campaign.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, campaign, "send_campaign");
            logger.info("EmailCampaign sending initiated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error sending EmailCampaign: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Complete email campaign
     * POST /ui/campaign/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> completeEmailCampaign(@PathVariable UUID id) {
        try {
            // Get current campaign
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update status to completed
            EmailCampaign campaign = current.entity();
            campaign.setStatus(EmailCampaign.CampaignStatus.COMPLETED);
            campaign.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, campaign, "complete_campaign");
            logger.info("EmailCampaign completed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error completing EmailCampaign: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get campaigns by status
     * GET /ui/campaign/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> getCampaignsByStatus(@PathVariable String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);

            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status.toUpperCase()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) statusCondition));

            List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.search(modelSpec, condition, EmailCampaign.class);
            return ResponseEntity.ok(campaigns);
        } catch (Exception e) {
            logger.error("Error getting EmailCampaigns by status: {}", status, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get campaigns by cat fact ID
     * GET /ui/campaign/catfact/{catFactId}
     */
    @GetMapping("/catfact/{catFactId}")
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> getCampaignsByCatFactId(@PathVariable String catFactId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);

            SimpleCondition catFactCondition = new SimpleCondition()
                    .withJsonPath("$.catFactId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(catFactId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of((QueryCondition) catFactCondition));

            List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.search(modelSpec, condition, EmailCampaign.class);
            return ResponseEntity.ok(campaigns);
        } catch (Exception e) {
            logger.error("Error getting EmailCampaigns by cat fact ID: {}", catFactId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced search for email campaigns
     * POST /ui/campaign/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> advancedSearch(
            @RequestBody EmailCampaignSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (searchRequest.getCampaignName() != null && !searchRequest.getCampaignName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.campaignName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCampaignName())));
            }

            if (searchRequest.getStatus() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getStatus().toString())));
            }

            if (searchRequest.getCatFactId() != null && !searchRequest.getCatFactId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.catFactId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getCatFactId())));
            }

            if (searchRequest.getMinRecipients() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.recipientCount")
                        .withOperation(Operation.GREATER_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMinRecipients())));
            }

            if (searchRequest.getMaxRecipients() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.recipientCount")
                        .withOperation(Operation.LESS_OR_EQUAL)
                        .withValue(objectMapper.valueToTree(searchRequest.getMaxRecipients())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.search(modelSpec, condition, EmailCampaign.class);
            return ResponseEntity.ok(campaigns);
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
    public static class EmailCampaignSearchRequest {
        private String campaignName;
        private EmailCampaign.CampaignStatus status;
        private String catFactId;
        private Integer minRecipients;
        private Integer maxRecipients;
    }
}
