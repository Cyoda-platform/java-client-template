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
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EmailCampaignController - REST API controller for email campaign management
 * 
 * Base Path: /api/campaigns
 * 
 * Provides endpoints for:
 * - Email campaign creation and scheduling
 * - Campaign sending
 * - Campaign cancellation
 * - Campaign retry
 * - Campaign listing and search
 * - Campaign reporting
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
     * Create and schedule new email campaign
     * POST /api/campaigns
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> createCampaign(@RequestBody EmailCampaignCreateRequest request) {
        try {
            EmailCampaign campaign = new EmailCampaign();
            campaign.setCampaignId("campaign_" + UUID.randomUUID().toString().substring(0, 8));
            campaign.setCampaignName(request.getCampaignName());
            campaign.setCatFactId(request.getCatFactId());
            campaign.setScheduledDate(request.getScheduledDate() != null ? request.getScheduledDate() : LocalDateTime.now().plusMinutes(5));
            campaign.setTotalSubscribers(0); // Will be set by processor
            campaign.setSuccessfulDeliveries(0);
            campaign.setFailedDeliveries(0);
            campaign.setBounces(0);
            campaign.setUnsubscribes(0);
            campaign.setOpens(0);
            campaign.setClicks(0);
            campaign.setEmailSubject(request.getEmailSubject() != null ? request.getEmailSubject() : "Your Weekly Cat Fact is Here!");
            campaign.setEmailTemplate(request.getEmailTemplate() != null ? request.getEmailTemplate() : "weekly_template");

            EntityWithMetadata<EmailCampaign> response = entityService.create(campaign);
            logger.info("EmailCampaign created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating email campaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Start sending scheduled campaign
     * PUT /api/campaigns/{uuid}/send
     */
    @PutMapping("/{id}/send")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> sendCampaign(
            @PathVariable UUID id,
            @RequestBody EmailCampaignTransitionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> current = entityService.getById(id, modelSpec, EmailCampaign.class);
            
            EntityWithMetadata<EmailCampaign> response = entityService.update(id, current.entity(), "transition_to_sent");
            logger.info("EmailCampaign sent with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error sending email campaign", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get campaign details by UUID
     * GET /api/campaigns/{uuid}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getCampaignById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> response = entityService.getById(id, modelSpec, EmailCampaign.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting campaign by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List email campaigns with filtering
     * GET /api/campaigns
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailCampaign>>> getAllCampaigns(
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            
            if (state != null && !state.trim().isEmpty()) {
                SimpleCondition stateCondition = new SimpleCondition()
                        .withJsonPath("$.meta.state")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(state));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(stateCondition));

                List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.search(modelSpec, condition, EmailCampaign.class);
                return ResponseEntity.ok(campaigns);
            } else {
                List<EntityWithMetadata<EmailCampaign>> campaigns = entityService.findAll(modelSpec, EmailCampaign.class);
                return ResponseEntity.ok(campaigns);
            }
        } catch (Exception e) {
            logger.error("Error getting all campaigns", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get detailed campaign performance report
     * GET /api/campaigns/{uuid}/report
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<CampaignReportResponse> getCampaignReport(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> campaignWithMetadata = entityService.getById(id, modelSpec, EmailCampaign.class);
            EmailCampaign campaign = campaignWithMetadata.entity();

            CampaignReportResponse report = new CampaignReportResponse();
            report.setCampaignId(campaign.getCampaignId());
            report.setTotalSubscribers(campaign.getTotalSubscribers());
            report.setSuccessfulDeliveries(campaign.getSuccessfulDeliveries());
            report.setFailedDeliveries(campaign.getFailedDeliveries());
            report.setBounces(campaign.getBounces());
            report.setOpens(campaign.getOpens());
            report.setClicks(campaign.getClicks());
            report.setUnsubscribes(campaign.getUnsubscribes());

            // Calculate rates
            if (campaign.getSuccessfulDeliveries() > 0) {
                report.setDeliveryRate((double) campaign.getSuccessfulDeliveries() / campaign.getTotalSubscribers() * 100);
                report.setOpenRate((double) campaign.getOpens() / campaign.getSuccessfulDeliveries() * 100);
                if (campaign.getOpens() > 0) {
                    report.setClickRate((double) campaign.getClicks() / campaign.getOpens() * 100);
                }
            }

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Error getting campaign report for ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    // Request DTOs

    @Getter
    @Setter
    public static class EmailCampaignCreateRequest {
        private String campaignName;
        private String catFactId;
        private LocalDateTime scheduledDate;
        private String emailSubject;
        private String emailTemplate;
    }

    @Getter
    @Setter
    public static class EmailCampaignTransitionRequest {
        private String transitionName;
        private String priority;
        private String reason;
        private LocalDateTime newScheduledDate;
    }

    @Getter
    @Setter
    public static class CampaignReportResponse {
        private String campaignId;
        private Integer totalSubscribers;
        private Integer successfulDeliveries;
        private Integer failedDeliveries;
        private Integer bounces;
        private Integer opens;
        private Integer clicks;
        private Integer unsubscribes;
        private Double deliveryRate;
        private Double openRate;
        private Double clickRate;
    }
}
