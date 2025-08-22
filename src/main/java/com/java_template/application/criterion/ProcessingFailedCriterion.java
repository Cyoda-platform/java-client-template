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
public class ProcessingFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingFailedCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.debug("ProcessingFailedCriterion: job entity is null");
             return EvaluationOutcome.fail("Job entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields checks
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             logger.debug("ProcessingFailedCriterion: job.status is missing or blank (id={})", job.getId());
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getEnabled() == null) {
             logger.debug("ProcessingFailedCriterion: job.enabled is null (id={})", job.getId());
             return EvaluationOutcome.fail("Job.enabled flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion is used to detect jobs that have failed during finalization.
         // It should only succeed when the job is in FAILED state and includes a lastResultSummary.
         String status = job.getStatus().trim();
         if (status.equalsIgnoreCase("FAILED")) {
             if (job.getLastResultSummary() == null || job.getLastResultSummary().isBlank()) {
                 logger.debug("ProcessingFailedCriterion: failed job missing lastResultSummary (id={})", job.getId());
                 return EvaluationOutcome.fail("Failed job must include lastResultSummary", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // All checks passed for a failed job -> criterion matched
             logger.debug("ProcessingFailedCriterion: job is FAILED and has lastResultSummary (id={})", job.getId());
             return EvaluationOutcome.success();
         }

         // If job is not in FAILED state, this criterion does not match.
         logger.debug("ProcessingFailedCriterion: job not in FAILED state (id={}, status={})", job.getId(), job.getStatus());
         return EvaluationOutcome.fail("Job is not in FAILED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}