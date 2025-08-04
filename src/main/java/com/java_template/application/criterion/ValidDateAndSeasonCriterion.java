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
public class ValidDateAndSeasonCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidDateAndSeasonCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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
        SnapshotJob snapshotJob = context.entity();

        // Validate season format: non-null, non-blank, and 4 digit year format
        if (snapshotJob.getSeason() == null || snapshotJob.getSeason().isBlank()) {
            return EvaluationOutcome.fail("Season is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!snapshotJob.getSeason().matches("\\d{4}")) {
            return EvaluationOutcome.fail("Season must be a 4-digit year", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate dateRangeStart and dateRangeEnd are non-null, non-blank
        if (snapshotJob.getDateRangeStart() == null || snapshotJob.getDateRangeStart().isBlank()) {
            return EvaluationOutcome.fail("dateRangeStart is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (snapshotJob.getDateRangeEnd() == null || snapshotJob.getDateRangeEnd().isBlank()) {
            return EvaluationOutcome.fail("dateRangeEnd is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate dateRangeStart < dateRangeEnd (lexicographical ISO date comparison)
        if (snapshotJob.getDateRangeStart().compareTo(snapshotJob.getDateRangeEnd()) >= 0) {
            return EvaluationOutcome.fail("dateRangeStart must be before dateRangeEnd", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
