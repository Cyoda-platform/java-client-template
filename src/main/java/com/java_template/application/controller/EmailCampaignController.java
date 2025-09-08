package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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
 * EmailCampaignController - REST API controller for email campaign management
 * Base Path: /api/campaigns
 */
@RestController
@RequestMapping("/api/campaigns")
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
     * POST /api/campaigns
     * Transition: INITIAL → CREATED (EmailCampaignCreationProcessor)
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> createCampaign(@RequestBody EmailCampaign campaign) {
        try {
            // Set defaults
            campaign.setId(UUID.randomUUID().toString());
            if (campaign.getCampaignType() == null) {
                campaign.setCampaignType("WEEKLY");
            }
            campaign.setTotalSubscribers(0);
            campaign.setSuccessfulDeliveries(0);
            campaign.setFailedDeliveries(0);

            EntityWithMetadata<EmailCampaign> response = entityService.create(campaign);
            logger.info("EmailCampaign created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Schedule campaign with cat fact
     * PUT /api/campaigns/{id}/schedule
     * Transition: CREATED → SCHEDULED (EmailCampaignSchedulingProcessor)
     */
    @PutMapping("/{id}/schedule")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> scheduleCampaign(
            @PathVariable UUID id,
            @RequestBody ScheduleRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EmailCampaign campaign = current.entity();
            campaign.setCatFactId(request.getCatFactId());

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, campaign, "transition_to_scheduled");
            logger.info("EmailCampaign {} scheduled with cat fact {}", id, request.getCatFactId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error scheduling EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Manually execute a scheduled campaign
     * PUT /api/campaigns/{id}/execute
     * Transition: SCHEDULED → EXECUTING (EmailCampaignExecutionProcessor)
     */
    @PutMapping("/{id}/execute")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> executeCampaign(
            @PathVariable UUID id,
            @RequestBody ExecuteRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, current.entity(), "transition_to_executing");
            logger.info("EmailCampaign {} execution started", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error executing EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel a campaign
     * PUT /api/campaigns/{id}/cancel
     * Transition: CREATED/SCHEDULED → CANCELLED (EmailCampaignCancellationProcessor)
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> cancelCampaign(
            @PathVariable UUID id,
            @RequestBody CancelRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<EmailCampaign> response = entityService.update(id, current.entity(), "transition_to_cancelled");
            logger.info("EmailCampaign {} cancelled: {}", id, request.getReason());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error cancelling EmailCampaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get campaign details with statistics
     * GET /api/campaigns/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getCampaign(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> response = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailCampaign by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List campaigns with filtering
     * GET /api/campaigns
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> getAllCampaigns(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String campaignType) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            
            if (campaignType != null) {
                SimpleCondition typeCondition = new SimpleCondition()
                        .withJsonPath("$.campaignType")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(campaignType));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(typeCondition));

                List<EntityWithMetadata<EmailCampaign>> entities = entityService.search(modelSpec, condition, EmailCampaign.class);
                return ResponseEntity.ok(entities);
            } else {
                List<EntityWithMetadata<EmailCampaign>> entities = entityService.findAll(modelSpec, EmailCampaign.class);
                return ResponseEntity.ok(entities);
            }
        } catch (Exception e) {
            logger.error("Error getting all EmailCampaigns", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get delivery details for a campaign
     * GET /api/campaigns/{id}/deliveries
     */
    @GetMapping("/{id}/deliveries")
    public ResponseEntity<List<EntityWithMetadata<EmailDelivery>>> getCampaignDeliveries(@PathVariable UUID id) {
        try {
            // First get the campaign to get its business ID
            ModelSpec campaignModelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> campaign = entityService.getById(id, campaignModelSpec, EmailCampaign.class);
            
            if (campaign == null) {
                return ResponseEntity.notFound().build();
            }

            ModelSpec deliveryModelSpec = new ModelSpec().withName(EmailDelivery.ENTITY_NAME).withVersion(EmailDelivery.ENTITY_VERSION);
            
            SimpleCondition campaignCondition = new SimpleCondition()
                    .withJsonPath("$.campaignId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(campaign.entity().getId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(campaignCondition));

            List<EntityWithMetadata<EmailDelivery>> deliveries = entityService.search(deliveryModelSpec, condition, EmailDelivery.class);
            return ResponseEntity.ok(deliveries);
        } catch (Exception e) {
            logger.error("Error getting deliveries for campaign {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs
    @Getter
    @Setter
    public static class ScheduleRequest {
        private String catFactId;
        private String transitionName = "schedule";
    }

    @Getter
    @Setter
    public static class ExecuteRequest {
        private String transitionName = "execute";
    }

    @Getter
    @Setter
    public static class CancelRequest {
        private String reason;
        private String transitionName = "cancel";
    }
}
