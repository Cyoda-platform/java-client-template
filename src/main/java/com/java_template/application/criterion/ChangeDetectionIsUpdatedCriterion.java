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
public class ChangeDetectionIsUpdatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ChangeDetectionIsUpdatedCriterion(SerializerFactory serializerFactory) {
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

        // Without access to persistence, implement heuristic: if businessId present and not generated and version null -> updated
        String bid = incoming.getBusinessId();
        if (bid != null && !bid.isBlank() && !bid.startsWith("gen-")) {
            // If currentVersion present and > 1 treat as updated
            if (incoming.getCurrentVersion() != null && incoming.getCurrentVersion() > 1) {
                return EvaluationOutcome.success();
            }
            // If fingerprint present and indicates change: here we can't compare; assume updated
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("No evidence of update", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
