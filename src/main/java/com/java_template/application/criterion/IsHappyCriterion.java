package com.java_template.application.criterion;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.calculation.EntityCriteriaCalculationRequest;
import com.java_template.common.workflow.calculation.EntityCriteriaCalculationResponse;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.reason.ReasonAttachmentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion: IsHappyCriterion
 * Returns true when mail.getIsHappy() == Boolean.TRUE
 */
@Component
public class IsHappyCriterion {
    private static final Logger logger = LoggerFactory.getLogger(IsHappyCriterion.class);

    private final SerializerFactory serializer;

    public IsHappyCriterion(SerializerFactory serializer) {
        this.serializer = serializer;
    }

    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating IsHappyCriterion for request: {}", request.getId());

        // The serializer API is provided by the platform. Use the standard fluent chain as required.
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    /**
     * Business logic for the criterion; pure function with no side effects.
     */
    private boolean validateEntity(Mail mail) {
        // Per functional requirement: return Boolean.TRUE.equals(mail.getIsHappy())
        if (mail == null) {
            return false;
        }
        boolean result = Boolean.TRUE.equals(mail.getIsHappy());
        logger.debug("IsHappyCriterion result={} for mail technicalId (if available)", result);
        return result;
    }
}
