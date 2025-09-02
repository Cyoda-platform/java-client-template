package com.java_template.application.criterion;

import com.java_template.application.entity.interaction.version_1.Interaction;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class InteractionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    @Autowired
    private EntityService entityService;
    
    private static final List<String> VALID_INTERACTION_TYPES = Arrays.asList(
        "EMAIL_OPENED", "EMAIL_CLICKED", "UNSUBSCRIBED"
    );

    public InteractionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Interaction.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Interaction> context) {
        Interaction entity = context.entity();
        
        // Validate required fields
        if (entity.getSubscriberId() == null || entity.getSubscriberId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Subscriber ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (entity.getCatFactId() == null || entity.getCatFactId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Cat fact ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (entity.getCampaignId() == null || entity.getCampaignId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Campaign ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate interaction type
        if (!isValidInteractionType(entity.getInteractionType())) {
            return EvaluationOutcome.fail("Invalid interaction type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate interaction date is not in future
        if (entity.getInteractionDate() != null && entity.getInteractionDate().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Interaction date cannot be in the future", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate referenced entities exist
        if (!doesSubscriberExist(entity.getSubscriberId())) {
            return EvaluationOutcome.fail("Referenced subscriber does not exist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        if (!doesCatFactExist(entity.getCatFactId())) {
            return EvaluationOutcome.fail("Referenced cat fact does not exist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        if (!doesCampaignExist(entity.getCampaignId())) {
            return EvaluationOutcome.fail("Referenced campaign does not exist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        // Check if interaction is eligible for processing
        if (!isEligibleForProcessing(entity)) {
            return EvaluationOutcome.fail("Interaction not eligible for processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
    
    private boolean isValidInteractionType(String interactionType) {
        return interactionType != null && VALID_INTERACTION_TYPES.contains(interactionType.toUpperCase());
    }
    
    private boolean doesSubscriberExist(String subscriberId) {
        try {
            EntityResponse<Subscriber> subscriber = entityService.findByBusinessId(
                Subscriber.class, 
                Subscriber.ENTITY_NAME, 
                Subscriber.ENTITY_VERSION, 
                subscriberId, 
                "email"
            );
            return subscriber != null && subscriber.getData() != null;
        } catch (Exception e) {
            logger.error("Error checking subscriber existence: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private boolean doesCatFactExist(String catFactId) {
        try {
            // Try to parse as UUID first, then fall back to business ID search
            try {
                UUID factUuid = UUID.fromString(catFactId);
                EntityResponse<CatFact> catFact = entityService.getItem(factUuid, CatFact.class);
                return catFact != null && catFact.getData() != null;
            } catch (IllegalArgumentException e) {
                // Not a UUID, try business ID search
                EntityResponse<CatFact> catFact = entityService.findByBusinessId(
                    CatFact.class, 
                    CatFact.ENTITY_NAME, 
                    CatFact.ENTITY_VERSION, 
                    catFactId, 
                    "id"
                );
                return catFact != null && catFact.getData() != null;
            }
        } catch (Exception e) {
            logger.error("Error checking cat fact existence: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private boolean doesCampaignExist(String campaignId) {
        try {
            // Try to parse as UUID first, then fall back to business ID search
            try {
                UUID campaignUuid = UUID.fromString(campaignId);
                EntityResponse<EmailCampaign> campaign = entityService.getItem(campaignUuid, EmailCampaign.class);
                return campaign != null && campaign.getData() != null;
            } catch (IllegalArgumentException e) {
                // Not a UUID, try business ID search
                EntityResponse<EmailCampaign> campaign = entityService.findByBusinessId(
                    EmailCampaign.class, 
                    EmailCampaign.ENTITY_NAME, 
                    EmailCampaign.ENTITY_VERSION, 
                    campaignId, 
                    "id"
                );
                return campaign != null && campaign.getData() != null;
            }
        } catch (Exception e) {
            logger.error("Error checking campaign existence: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private boolean isEligibleForProcessing(Interaction interaction) {
        // Check if interaction is not a duplicate
        // Check if interaction occurred within reasonable timeframe
        // Validate interaction type matches campaign type
        
        // For now, basic validation - interaction should have valid timestamp
        if (interaction.getInteractionDate() == null) {
            return false;
        }
        
        // Check if interaction is not too old (e.g., within last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        if (interaction.getInteractionDate().isBefore(thirtyDaysAgo)) {
            logger.warn("Interaction is too old for processing: {}", interaction.getInteractionDate());
            return false;
        }
        
        return true;
    }
}
