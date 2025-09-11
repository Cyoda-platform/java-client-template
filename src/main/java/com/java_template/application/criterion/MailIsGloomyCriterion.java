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
 * MailIsGloomyCriterion - Evaluates whether a mail entity should be classified as gloomy content
 * 
 * This criterion determines if the mail should transition to the GLOOMY_READY state based on
 * the entity's isHappy attribute being set to false.
 */
@Component
public class MailIsGloomyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailIsGloomyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking MailIsGloomy criteria for request: {}", request.getId());
        
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
     * Main validation logic for determining if mail is gloomy
     * Checks if the mail entity's isHappy attribute is set to false
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Mail entity is null");
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if entity is valid
        if (!entity.isValid()) {
            logger.warn("Mail entity is not valid");
            return EvaluationOutcome.fail("Mail entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if isHappy attribute is null
        if (entity.getIsHappy() == null) {
            logger.warn("Mail isHappy attribute is null");
            return EvaluationOutcome.fail("Mail isHappy attribute is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if mail is gloomy (not happy)
        if (!entity.getIsHappy()) {
            logger.debug("Mail is gloomy - criterion passed");
            return EvaluationOutcome.success();
        } else {
            logger.debug("Mail is not gloomy - criterion failed");
            return EvaluationOutcome.fail("Mail is not gloomy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
