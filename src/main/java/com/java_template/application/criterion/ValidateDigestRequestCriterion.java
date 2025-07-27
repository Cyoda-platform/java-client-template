package com.java_template.application.criterion;

import com.java_template.application.entity.DigestRequest;
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
public class ValidateDigestRequestCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateDigestRequestCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(DigestRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DigestRequest> context) {

        DigestRequest entity = context.entity();

        // Validate userEmail format and required fields
        if (entity.getUserEmail() == null || entity.getUserEmail().isBlank()) {
            return EvaluationOutcome.fail("User email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Simple email format check (basic)
        if (!entity.getUserEmail().matches("^[\w.%+-]+@[\w.-]+\\.[a-zA-Z]{2,6}$")) {
            return EvaluationOutcome.fail("User email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getRequestMetadata() == null || entity.getRequestMetadata().isBlank()) {
            return EvaluationOutcome.fail("Request metadata is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getExternalApiEndpoint() == null || entity.getExternalApiEndpoint().isBlank()) {
            return EvaluationOutcome.fail("External API endpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
