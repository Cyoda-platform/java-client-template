package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class ChangeDetectionIsNewCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ChangeDetectionIsNewCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
        Laureate incoming = context.entity();
        if (incoming == null) {
            return EvaluationOutcome.fail("Laureate is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Heuristic: if businessId absent and fingerprint not found, treat as new
        if (incoming.getBusinessId() == null || incoming.getBusinessId().isBlank()) {
            return EvaluationOutcome.success();
        }

        // In real implementation we would query repository to find existing by businessId or fingerprint.
        // Here we use a simple heuristic: if businessId starts with "gen-" it's new
        if (incoming.getBusinessId().startsWith("gen-")) {
            return EvaluationOutcome.success();
        }

        // Default: not new
        return EvaluationOutcome.fail("Existing laureate likely present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
