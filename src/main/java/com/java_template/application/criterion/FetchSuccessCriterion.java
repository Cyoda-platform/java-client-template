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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchSuccessCriterion(SerializerFactory serializerFactory) {
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
         // Basic preconditions
         if (job == null) {
             logger.warn("FetchSuccessCriterion invoked with null job entity");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job hasn't finished yet, criterion is not satisfied
         if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
             logger.debug("Job {} not finished yet (finishedAt={})", job.getJobId(), job.getFinishedAt());
             return EvaluationOutcome.fail("Job not finished yet", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If there are error details, treat as fetch failure (business rule)
         if (job.getErrorDetails() != null && !job.getErrorDetails().isBlank()) {
             String detailPreview = job.getErrorDetails().length() > 200 ? job.getErrorDetails().substring(0, 200) + "..." : job.getErrorDetails();
             logger.info("Job {} finished with error details: {}", job.getJobId(), detailPreview);
             return EvaluationOutcome.fail("Job finished with errors: " + detailPreview, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // recordsFetchedCount must be present and non-negative for a successful fetch
         if (job.getRecordsFetchedCount() == null) {
             logger.warn("Job {} finished but recordsFetchedCount is missing", job.getJobId());
             return EvaluationOutcome.fail("Missing recordsFetchedCount after fetch", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getRecordsFetchedCount() < 0) {
             logger.warn("Job {} has negative recordsFetchedCount: {}", job.getJobId(), job.getRecordsFetchedCount());
             return EvaluationOutcome.fail("Invalid recordsFetchedCount (negative)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Optionally ensure sourceEndpoint and jobId are present (basic validation)
         if (job.getJobId() == null || job.getJobId().isBlank()) {
             logger.warn("Job with finishedAt={} missing business jobId", job.getFinishedAt());
             return EvaluationOutcome.fail("Missing jobId", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             logger.warn("Job {} finished but sourceEndpoint is missing", job.getJobId());
             return EvaluationOutcome.fail("Missing sourceEndpoint", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed: treat as successful fetch
         logger.info("Job {} considered fetch-successful (recordsFetchedCount={})", job.getJobId(), job.getRecordsFetchedCount());
         return EvaluationOutcome.success();
    }
}