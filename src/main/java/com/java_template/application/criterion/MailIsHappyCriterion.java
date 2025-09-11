package com.java_template.application.criterion;

import com.java_template.application.entity.mail.version_1.Mail;
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
 * MailIsHappyCriterion - Determines if a mail entity should be routed to happy mail processing
 * 
 * This criterion evaluates the isHappy attribute to determine if the mail should be
 * processed as happy mail. Used in workflow transition from PENDING to HAPPY_PROCESSING.
 */
@Component
public class MailIsHappyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailIsHappyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking MailIsHappy criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for determining if mail is happy
     * Returns true if the mail should be processed as happy mail
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Mail entity is null");
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("Mail entity is not valid");
            return EvaluationOutcome.fail("Mail entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if mail is marked as happy
        if (entity.getIsHappy() == null) {
            logger.warn("Mail isHappy field is null");
            return EvaluationOutcome.fail("Mail isHappy field is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Return success if mail is happy, fail if not
        if (Boolean.TRUE.equals(entity.getIsHappy())) {
            logger.debug("Mail is marked as happy - routing to happy processing");
            return EvaluationOutcome.success();
        } else {
            logger.debug("Mail is not marked as happy - criterion fails");
            return EvaluationOutcome.fail("Mail is not marked as happy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
