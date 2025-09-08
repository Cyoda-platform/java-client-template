package com.java_template.application.criterion;

import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
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
 * EmailDeliveryBounceCriterion - Checks if email bounced back from recipient's email server
 * Entity: EmailDelivery
 * Transition: SENT → BOUNCED
 */
@Component
public class EmailDeliveryBounceCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailDeliveryBounceCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailDelivery bounce criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailDelivery.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailDelivery> context) {
        EmailDelivery entity = context.entityWithMetadata().entity();

        if (entity == null || !entity.isValid()) {
            return EvaluationOutcome.fail("Invalid EmailDelivery entity", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if delivery status indicates bounce
        if ("BOUNCED".equals(entity.getDeliveryStatus())) {
            return EvaluationOutcome.success();
        }

        // Check if error message indicates bounce
        if (entity.getErrorMessage() != null && 
            (entity.getErrorMessage().toLowerCase().contains("bounce") ||
             entity.getErrorMessage().toLowerCase().contains("mailbox full") ||
             entity.getErrorMessage().toLowerCase().contains("user unknown"))) {
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("No bounce detected for email delivery", 
                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
