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

        boolean anyValid = false;
        for (String addr : entity.getMailList()) {
            if (addr == null || addr.isBlank()) {
                return EvaluationOutcome.fail("mailList contains blank or null address", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!SIMPLE_EMAIL.matcher(addr).matches()) {
                return EvaluationOutcome.fail("mailList contains invalid email: " + addr, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            // Business rule example: if recipient includes "happy" domain treat as happy
            if (addr.toLowerCase().contains("happy@example.com") || addr.toLowerCase().contains("joy") ) {
                anyValid = true;
            }
            // Another example: if recipient is 'gloom@example.com' it's explicitly gloomy -> not happy
            if (addr.equalsIgnoreCase("gloom@example.com")) {
                return EvaluationOutcome.fail("Recipient indicates gloomy classification", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        if (anyValid) {
            return EvaluationOutcome.success();
        }

        // Default to success of criterion if no explicit gloomy indicator found; higher level will choose gloom if needed
        return EvaluationOutcome.success();
    }
}
