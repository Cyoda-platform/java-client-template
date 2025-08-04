package com.java_template.application.criterion;

import com.java_template.application.entity.SnapshotJob;
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
public class IsInvalidJob implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsInvalidJob(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(SnapshotJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SnapshotJob> context) {

        SnapshotJob entity = context.entity();

        // Validate season format: must be 4-digit year
        if (entity.getSeason() == null || !entity.getSeason().matches("\\d{4}")) {
            return EvaluationOutcome.success(); // This criterion is used to detect invalid, so success means no error here
        }

        if (entity.getDateRangeStart() == null || entity.getDateRangeStart().isBlank()) {
            return EvaluationOutcome.success();
        }

        if (entity.getDateRangeEnd() == null || entity.getDateRangeEnd().isBlank()) {
            return EvaluationOutcome.success();
        }

        if (entity.getDateRangeStart().compareTo(entity.getDateRangeEnd()) >= 0) {
            return EvaluationOutcome.success();
        }

        // If none of the above invalid conditions met, fail the check
        return EvaluationOutcome.fail("Job is valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
