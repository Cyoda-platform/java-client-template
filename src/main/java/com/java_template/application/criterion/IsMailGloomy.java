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
public class IsMailGloomy implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsMailGloomy(SerializerFactory serializerFactory) {
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

        // Validation: isHappy must be false to be considered gloomy mail
        if (mail.getIsHappy() == null) {
            return EvaluationOutcome.fail("isHappy flag must not be null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (mail.getIsHappy()) {
            return EvaluationOutcome.fail("Mail is not gloomy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate mailList is not null or empty
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            return EvaluationOutcome.fail("Mail list must not be empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate no blank emails in mailList
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                return EvaluationOutcome.fail("Mail list contains blank email", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
