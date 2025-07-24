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
public class IsValidDigestRequestCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsValidDigestRequestCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsValidDigestRequestCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestRequestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsValidDigestRequestCriterion".equals(modelSpec.operationName()) &&
               "digestRequestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestRequestJob entity) {
        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!entity.getEmail().contains("@")) {
            return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getRequestMetadata() == null || entity.getRequestMetadata().isBlank()) {
            return EvaluationOutcome.fail("Request metadata is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!entity.getStatus().equals("PENDING") && !entity.getStatus().equals("COMPLETED") && !entity.getStatus().equals("FAILED")) {
            return EvaluationOutcome.fail("Status must be PENDING, COMPLETED, or FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("CreatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // completedAt can be null or blank if status is not COMPLETED or FAILED
        if ((entity.getStatus().equals("COMPLETED") || entity.getStatus().equals("FAILED")) &&
            (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank())) {
            return EvaluationOutcome.fail("CompletedAt is required when status is COMPLETED or FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
