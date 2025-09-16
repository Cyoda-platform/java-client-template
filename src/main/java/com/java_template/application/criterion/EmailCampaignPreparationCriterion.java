package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Criterion to check if campaign is ready to be prepared.
 * Handles the prepare transition (scheduled → preparing).
 * 
 * Validation Logic:
 * - Checks if current time >= scheduled date
 * - Verifies cat fact is assigned
 * - Checks if cat fact is in ready state
 * - Verifies there are active subscribers
 */
@Component
public class EmailCampaignPreparationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignPreparationCriterion.class);
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    public EmailCampaignPreparationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        logger.debug("EmailCampaignPreparationCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking email campaign preparation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, ctx -> this.evaluateCampaignPreparation(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "EmailCampaignPreparationCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether a campaign is ready to be prepared.
     * 
     * @param campaign The email campaign to evaluate
     * @return EvaluationOutcome indicating whether preparation criteria are met
     */
    private EvaluationOutcome evaluateCampaignPreparation(EmailCampaign campaign) {
        if (campaign == null) {
            return EvaluationOutcome.fail("Email campaign is null");
        }

        // Check if current time >= scheduled date
        if (campaign.getScheduledDate() == null) {
            return EvaluationOutcome.fail("No scheduled date set");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        if (currentTime.isBefore(campaign.getScheduledDate())) {
            return EvaluationOutcome.fail("Scheduled time not yet reached");
        }

        // Check if cat fact is assigned
        if (campaign.getCatFactId() == null) {
            return EvaluationOutcome.fail("No cat fact assigned to campaign");
        }

        // Check if cat fact is in ready state
        try {
            CatFact catFact = getCatFactById(campaign.getCatFactId());
            if (catFact == null) {
                return EvaluationOutcome.fail("Cat fact not found");
            }

            if (!"ready".equals(catFact.getCategory()) && !"validated".equals(catFact.getCategory())) {
                return EvaluationOutcome.fail("Cat fact is not in ready state");
            }

            if (catFact.getIsUsed() != null && catFact.getIsUsed()) {
                return EvaluationOutcome.fail("Cat fact is already used");
            }

        } catch (Exception e) {
            logger.error("Failed to check cat fact status: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to verify cat fact status");
        }

        // Check if there are active subscribers
        try {
            int activeSubscriberCount = getActiveSubscriberCount();
            if (activeSubscriberCount == 0) {
                return EvaluationOutcome.fail("No active subscribers found");
            }

            logger.debug("Campaign preparation criteria met: {} active subscribers", activeSubscriberCount);

        } catch (Exception e) {
            logger.error("Failed to check active subscribers: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to verify active subscribers");
        }

        // All preparation criteria met
        logger.debug("Email campaign preparation criteria met for: {}", campaign.getCampaignName());
        return EvaluationOutcome.success();
    }

    /**
     * Gets a cat fact by ID.
     */
    private CatFact getCatFactById(Long catFactId) {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("id", catFactId);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> facts = 
                entityService.getItemsByCondition(
                    CatFact.ENTITY_NAME, 
                    CatFact.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            if (facts == null || facts.isEmpty()) {
                return null;
            }
            
            // Convert to CatFact entity (simplified)
            com.fasterxml.jackson.databind.JsonNode factData = facts.get(0).getData();
            CatFact catFact = new CatFact();
            catFact.setId(factData.get("id").asLong());
            catFact.setFactText(factData.get("factText").asText());
            catFact.setCategory(factData.has("category") ? factData.get("category").asText() : null);
            catFact.setIsUsed(factData.has("isUsed") ? factData.get("isUsed").asBoolean() : false);
            
            return catFact;
            
        } catch (Exception e) {
            logger.error("Failed to get cat fact by ID {}: {}", catFactId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the count of active subscribers.
     */
    private int getActiveSubscriberCount() {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("isActive", true);
            
            List<org.cyoda.cloud.api.event.common.DataPayload> activeSubscribers = 
                entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME, 
                    Subscriber.ENTITY_VERSION, 
                    condition, 
                    false
                ).get();
            
            return activeSubscribers != null ? activeSubscribers.size() : 0;
            
        } catch (Exception e) {
            logger.error("Failed to count active subscribers: {}", e.getMessage());
            return 0;
        }
    }
}
