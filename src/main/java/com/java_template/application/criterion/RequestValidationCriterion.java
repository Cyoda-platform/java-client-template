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

import java.util.regex.Pattern;

@Component
public class RequestValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", Pattern.CASE_INSENSITIVE);

    public RequestValidationCriterion(SerializerFactory serializerFactory) {
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

        DigestRequest digestRequest = context.entity();

        if (digestRequest.getUserEmail() == null || digestRequest.getUserEmail().isBlank()) {
            return EvaluationOutcome.fail("User email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!EMAIL_PATTERN.matcher(digestRequest.getUserEmail()).matches()) {
            return EvaluationOutcome.fail("User email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (digestRequest.getRequestMetadata() == null || digestRequest.getRequestMetadata().isBlank()) {
            return EvaluationOutcome.fail("Request metadata is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (digestRequest.getExternalApiEndpoint() == null || digestRequest.getExternalApiEndpoint().isBlank()) {
            return EvaluationOutcome.fail("External API endpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (digestRequest.getRequestTimestamp() == null) {
            return EvaluationOutcome.fail("Request timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional business rules can be added here

        return EvaluationOutcome.success();
    }
}
