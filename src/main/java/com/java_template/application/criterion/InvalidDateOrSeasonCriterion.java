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
public class InvalidDateOrSeasonCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InvalidDateOrSeasonCriterion(SerializerFactory serializerFactory) {
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

        // This criterion is the negation of ValidDateAndSeasonCriterion

        // Check for missing or blank season
        if (snapshotJob.getSeason() == null || snapshotJob.getSeason().isBlank()) {
            return EvaluationOutcome.success();
        }
        // Check for season not matching 4-digit year
        if (!snapshotJob.getSeason().matches("\\d{4}")) {
            return EvaluationOutcome.success();
        }

        // Check for missing or blank dates
        if (snapshotJob.getDateRangeStart() == null || snapshotJob.getDateRangeStart().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (snapshotJob.getDateRangeEnd() == null || snapshotJob.getDateRangeEnd().isBlank()) {
            return EvaluationOutcome.success();
        }

        // Check for dateRangeStart >= dateRangeEnd
        if (snapshotJob.getDateRangeStart().compareTo(snapshotJob.getDateRangeEnd()) >= 0) {
            return EvaluationOutcome.success();
        }

        // If none of the invalid conditions met, this criterion fails
        return EvaluationOutcome.fail("Date and season appear valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
