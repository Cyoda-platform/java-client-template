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

import java.util.regex.Pattern;

@Component
public class IsHappyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public IsHappyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            return EvaluationOutcome.fail("mailList is required and must contain at least one recipient", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        boolean anyHappyIndicator = false;

        for (String addr : entity.getMailList()) {
            if (addr == null || addr.isBlank()) {
                return EvaluationOutcome.fail("mailList contains blank or null address", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!SIMPLE_EMAIL.matcher(addr).matches()) {
                return EvaluationOutcome.fail("mailList contains invalid email: " + addr, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Explicit gloomy indicator means not happy
            if (addr.equalsIgnoreCase("gloom@example.com")) {
                logger.debug("Found explicit gloomy address -> not happy: {}", addr);
                return EvaluationOutcome.fail("Recipient indicates gloomy classification", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Business rule: addresses containing "happy" or "joy" keywords or known happy domain
            String lower = addr.toLowerCase();
            if (lower.contains("happy") || lower.contains("joy") || lower.endsWith("@happy.example.com")) {
                anyHappyIndicator = true;
            }
        }

        if (anyHappyIndicator) {
            return EvaluationOutcome.success();
        }

        // No explicit happy indicator -> not happy (higher level will decide gloom/default)
        return EvaluationOutcome.fail("No happy indicator found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    // Programmatic evaluation used by processors
    public boolean evaluate(Mail entity) {
        if (entity == null) return false;
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) return false;

        boolean anyHappyIndicator = false;
        for (String addr : entity.getMailList()) {
            if (addr == null || addr.isBlank()) {
                logger.debug("IsHappyCriterion.evaluate: null/blank address found for mail {}", entity.getTechnicalId());
                return false;
            }
            if (!SIMPLE_EMAIL.matcher(addr).matches()) {
                logger.debug("IsHappyCriterion.evaluate: invalid address {} for mail {}", addr, entity.getTechnicalId());
                return false;
            }
            if (addr.equalsIgnoreCase("gloom@example.com")) {
                logger.debug("IsHappyCriterion.evaluate: explicit gloomy address {} found for mail {}", addr, entity.getTechnicalId());
                return false;
            }
            String lower = addr.toLowerCase();
            if (lower.contains("happy") || lower.contains("joy") || lower.endsWith("@happy.example.com")) {
                anyHappyIndicator = true;
            }
        }
        if (anyHappyIndicator) {
            logger.debug("IsHappyCriterion.evaluate: happy indicator found for mail {}", entity.getTechnicalId());
            return true;
        }
        logger.debug("IsHappyCriterion.evaluate: no happy indicator for mail {}", entity.getTechnicalId());
        return false;
    }
}
