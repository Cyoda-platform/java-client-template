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
public class FetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.warn("FetchFailureCriterion: job entity is null");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String state = job.getState();
         if (state == null || state.isBlank()) {
             logger.debug("FetchFailureCriterion: job.state is missing or blank for jobId={}", job.getJobId());
             return EvaluationOutcome.fail("Job state is required to evaluate fetch failure", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion satisfied only when the Job indicates a FAILED fetch
         if ("FAILED".equalsIgnoreCase(state)) {
             // Ensure we have error details for diagnostics; missing details is a data quality issue
             String errorDetails = job.getErrorDetails();
             if (errorDetails == null || errorDetails.isBlank()) {
                 logger.info("FetchFailureCriterion: job marked FAILED but missing errorDetails for jobId={}", job.getJobId());
                 return EvaluationOutcome.fail("Job is FAILED but errorDetails are missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // finishedAt is expected for terminal states; warn if missing but still treat criterion as satisfied
             if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                 logger.warn("FetchFailureCriterion: job.finishedAt is missing for FAILED jobId={}", job.getJobId());
                 // Still consider the criterion satisfied (but as a warning via reason attachment)
             }
             return EvaluationOutcome.success();
         }

         // Not in FAILED state - criterion not met
         return EvaluationOutcome.fail("Job is not in FAILED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}