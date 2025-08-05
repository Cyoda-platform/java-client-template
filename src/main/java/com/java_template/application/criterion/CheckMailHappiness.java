package com.java_template.application.criterion;

import com.java_template.application.entity.Mail;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CheckMailHappiness implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckMailHappiness(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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

        Mail mail = context.entity();

        // Business logic: The happiness criterion checks if content and mailList are valid (not null or blank) and sets isHappy accordingly
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            return EvaluationOutcome.fail("Content is required for happiness evaluation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            return EvaluationOutcome.fail("Mail list must not be empty for happiness evaluation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (String mailAddress : mail.getMailList()) {
            if (mailAddress == null || mailAddress.isBlank()) {
                return EvaluationOutcome.fail("Mail list contains blank email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Example happiness logic: if content contains "happy" (case insensitive), mark isHappy true
        if (mail.getContent().toLowerCase().contains("happy")) {
            mail.setIsHappy(Boolean.TRUE);
            return EvaluationOutcome.success();
        } else {
            mail.setIsHappy(Boolean.FALSE);
            return EvaluationOutcome.success();
        }
    }
}
