package com.java_template.application.criterion;

import com.java_template.application.entity.DigestRequestJob;
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
public class JobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(DigestRequestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DigestRequestJob> context) {
        DigestRequestJob entity = context.entity();

        if (entity.getUserEmail() == null || entity.getUserEmail().isBlank()) {
            return EvaluationOutcome.fail("userEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Simple email format check
        if (!entity.getUserEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return EvaluationOutcome.fail("userEmail format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getEventMetadata() == null || entity.getEventMetadata().isBlank()) {
            return EvaluationOutcome.fail("eventMetadata is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
