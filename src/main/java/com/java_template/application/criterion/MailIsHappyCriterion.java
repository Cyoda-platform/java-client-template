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
 * MailIsHappyCriterion - Determines if a mail entity should be processed as happy mail
 * 
 * This criterion evaluates the isHappy attribute to determine if the mail should
 * be routed to happy mail processing. Returns true only when isHappy is explicitly
 * set to true and the entity is in a valid state.
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
        logger.debug("Checking MailIsHappyCriterion for request: {}", request.getId());
        
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
     * 
     * Evaluates to true only when:
     * - Entity is not null
     * - Entity is valid (has required fields)
     * - Entity state is PENDING
     * - isHappy is explicitly set to true
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail entity = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();
        String entityId = context.entityWithMetadata().metadata().getId().toString();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Mail entity is null for ID: {}", entityId);
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate entity state - should be PENDING for evaluation
        if (!"pending".equalsIgnoreCase(currentState)) {
            logger.debug("Mail entity {} is not in PENDING state (current: {}), skipping happy criterion", entityId, currentState);
            return EvaluationOutcome.fail("Entity not in PENDING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate entity has required fields
        if (!entity.isValid()) {
            logger.warn("Mail entity {} is not valid", entityId);
            return EvaluationOutcome.fail("Mail entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate isHappy attribute
        if (entity.getIsHappy() == null) {
            logger.warn("isHappy attribute is null for mail entity {}", entityId);
            return EvaluationOutcome.fail("isHappy attribute is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate mailList is not empty
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            logger.warn("mailList is empty for mail entity {}", entityId);
            return EvaluationOutcome.fail("mailList is empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Main evaluation logic - check if mail is happy
        boolean isHappy = Boolean.TRUE.equals(entity.getIsHappy());
        
        logger.debug("MailIsHappyCriterion evaluation for entity {}: {}", entityId, isHappy);
        
        if (isHappy) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Mail is not happy (isHappy = false)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
