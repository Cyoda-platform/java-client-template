package com.java_template.application.criterion;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Criterion for validating email campaign data before sending.
 * Checks recipient count and required fields.
 */
@Component
public class EmailCampaignValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailCampaignValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailCampaign> context) {
        EmailCampaign entity = context.entityWithMetadata().entity();

        if (entity == null) {
            logger.warn("EmailCampaign is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("EmailCampaign is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate recipient count
        if (entity.getRecipientCount() == null || entity.getRecipientCount() <= 0) {
            logger.warn("No recipients for campaign: {}", entity.getCampaignId());
            return EvaluationOutcome.fail("No recipients for campaign", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

