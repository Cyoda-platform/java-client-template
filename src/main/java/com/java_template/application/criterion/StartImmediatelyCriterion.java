package com.java_template.application.criterion;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class StartImmediatelyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StartImmediatelyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
        BatchJob job = context.entity();
        if (job == null) return EvaluationOutcome.fail("Job missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (job.getScheduledFor() == null || job.getScheduledFor().isBlank()) {
            return EvaluationOutcome.success();
        }
        try {
            Instant scheduled = Instant.parse(job.getScheduledFor());
            if (!scheduled.isAfter(Instant.now())) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("scheduledFor is in the future", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (DateTimeParseException ex) {
            return EvaluationOutcome.fail("scheduledFor is not a valid ISO datetime", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
