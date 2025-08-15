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
 * Criterion: IsGloomyCriterion
 * Returns true when mail.getIsHappy() is not Boolean.TRUE (includes null)
 */
@Component
public class IsGloomyCriterion {
    private static final Logger logger = LoggerFactory.getLogger(IsGloomyCriterion.class);

    private final SerializerFactory serializer;

    public IsGloomyCriterion(SerializerFactory serializer) {
        this.serializer = serializer;
    }

    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating IsGloomyCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    /**
     * Business logic for the criterion; pure function with no side effects.
     */
    private boolean validateEntity(Mail mail) {
        // Per functional requirement: return !Boolean.TRUE.equals(mail.getIsHappy())
        if (mail == null) {
            return true; // default to gloomy if entity missing
        }
        boolean result = !Boolean.TRUE.equals(mail.getIsHappy());
        logger.debug("IsGloomyCriterion result={} for mail", result);
        return result;
    }
}
