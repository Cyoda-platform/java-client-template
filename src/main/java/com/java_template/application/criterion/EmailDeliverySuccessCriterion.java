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
 * EmailDeliverySuccessCriterion - Checks if email was successfully delivered to recipient's inbox
 * Entity: EmailDelivery
 * Transition: SENT → DELIVERED
 */
@Component
public class EmailDeliverySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailDeliverySuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailDelivery success criteria for request: {}", request.getId());
        
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

        // Check if delivery status indicates success
        if ("DELIVERED".equals(entity.getDeliveryStatus())) {
            return EvaluationOutcome.success();
        }

        // For simulation purposes, assume delivery is successful if sent date is present and no error
        if (entity.getSentDate() != null && 
            (entity.getErrorMessage() == null || entity.getErrorMessage().trim().isEmpty())) {
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("Email delivery not confirmed as successful", 
                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
