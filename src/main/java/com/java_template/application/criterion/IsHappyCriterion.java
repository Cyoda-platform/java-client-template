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

import java.util.List;
import java.util.regex.Pattern;

@Component
public class IsHappyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
        Mail mail = context.entity();

        if (mail == null) {
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        List<String> list = mail.getMailList();
        if (list == null || list.isEmpty()) {
            return EvaluationOutcome.fail("mailList is required and must contain at least one email", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // All recipients must be valid emails to be considered happy
        for (String recipient : list) {
            if (recipient == null || recipient.isEmpty() || !EMAIL_PATTERN.matcher(recipient).matches()) {
                return EvaluationOutcome.fail("mailList contains invalid email: " + recipient, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            // business rule: if any explicit fail marker, not happy
            if (recipient.equalsIgnoreCase("gloom@example.com")) {
                return EvaluationOutcome.fail("Recipient indicates gloomy template", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // If all checks pass, mail is happy
        return EvaluationOutcome.success();
    }
}
