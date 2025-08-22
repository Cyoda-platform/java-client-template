package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
public class ProcessingSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingSucceededCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // status is required for finalization decisions
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Business rule: only pass this criterion when job reached COMPLETED
         if (!"COMPLETED".equals(job.getStatus())) {
             return EvaluationOutcome.fail("Job status is not COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         // Data quality checks for completed job: ensure run/result timestamps and summary present
         if (job.getLastResultSummary() == null || job.getLastResultSummary().isBlank()) {
             return EvaluationOutcome.fail("lastResultSummary is required for completed jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getLastRunTimestamp() == null || job.getLastRunTimestamp().isBlank()) {
             return EvaluationOutcome.fail("lastRunTimestamp is required for completed jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // All checks passed
         return EvaluationOutcome.success();
    }
}