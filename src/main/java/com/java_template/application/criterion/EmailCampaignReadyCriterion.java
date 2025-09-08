package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailCampaignReadyCriterion - Checks if the campaign is ready to be executed
 * Entity: EmailCampaign
 * Transition: SCHEDULED → EXECUTING
 * 
 * Evaluation Logic:
 * 1. Get the scheduled date from the campaign entity
 * 2. Get the current timestamp
 * 3. Check if current time >= scheduled time
 * 4. Verify that a cat fact is assigned (catFactId is not null)
 * 5. Verify that there are active subscribers available
 * 6. Return true if all conditions are met
 * 7. Return false otherwise
 */
@Component
public class EmailCampaignReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignReadyCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign ready criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for checking if campaign is ready for execution
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailCampaign> context) {
        EmailCampaign entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("EmailCampaign entity is null");
            return EvaluationOutcome.fail("EmailCampaign entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("EmailCampaign entity is not valid");
            return EvaluationOutcome.fail("EmailCampaign entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if scheduled date is set
        if (entity.getScheduledDate() == null) {
            logger.warn("EmailCampaign scheduled date is null");
            return EvaluationOutcome.fail("Campaign scheduled date is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if current time >= scheduled time
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(entity.getScheduledDate())) {
            logger.debug("EmailCampaign {} not ready - scheduled for {}, current time is {}", 
                        entity.getCampaignName(), entity.getScheduledDate(), now);
            return EvaluationOutcome.fail("Campaign scheduled time has not arrived yet", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if cat fact is assigned
        if (entity.getCatFactId() == null || entity.getCatFactId().trim().isEmpty()) {
            logger.warn("EmailCampaign {} has no cat fact assigned", entity.getCampaignName());
            return EvaluationOutcome.fail("Campaign must have a cat fact assigned", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if there are active subscribers
        if (!hasActiveSubscribers()) {
            logger.warn("No active subscribers found for campaign execution");
            return EvaluationOutcome.fail("No active subscribers available for campaign", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("EmailCampaign {} is ready for execution", entity.getCampaignName());
        return EvaluationOutcome.success();
    }

    /**
     * Check if there are active subscribers available
     */
    private boolean hasActiveSubscribers() {
        try {
            ModelSpec subscriberModelSpec = new ModelSpec()
                    .withName(Subscriber.ENTITY_NAME)
                    .withVersion(Subscriber.ENTITY_VERSION);

            // Create condition to find active subscribers
            SimpleCondition activeCondition = new SimpleCondition()
                    .withJsonPath("$.isActive")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(activeCondition));

            List<EntityWithMetadata<Subscriber>> activeSubscribers = 
                    entityService.search(subscriberModelSpec, condition, Subscriber.class);

            return !activeSubscribers.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking for active subscribers: {}", e.getMessage());
            return false;
        }
    }
}
