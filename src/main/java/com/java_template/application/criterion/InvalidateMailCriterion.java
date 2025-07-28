package com.java_template.application.criterion;

import com.java_template.application.entity.Mail;
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

@Component
public class InvalidateMailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InvalidateMailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // Implement business logic to invalidate mail - here it means mail fails validation
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::invalidateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome invalidateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {

        Mail mail = context.entity();

        // Invalidate if any validation condition fails
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            return EvaluationOutcome.fail("mailList is empty or null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (mail.getContent() == null || mail.getContent().isBlank()) {
            return EvaluationOutcome.fail("content is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (mail.getIsHappy() == null) {
            return EvaluationOutcome.fail("isHappy flag is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If none of the invalid conditions met, then it's not invalid
        return EvaluationOutcome.success();
    }
}
