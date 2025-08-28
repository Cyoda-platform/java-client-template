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
public class IngestionFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionFailedCriterion(SerializerFactory serializerFactory) {
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
        // Must match the criterion name exactly (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("Job entity is null in IngestionFailedCriterion");
             return EvaluationOutcome.fail("Job entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate presence of essential fields
         if (job.getState() == null || job.getState().isBlank()) {
             logger.warn("Job {} has missing state", job.getId());
             return EvaluationOutcome.fail("Job state is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRunTimestamp() == null || job.getRunTimestamp().isBlank()) {
             logger.warn("Job {} has missing runTimestamp", job.getId());
             return EvaluationOutcome.fail("Job runTimestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String state = job.getState().trim();

         // Explicit failed state -> mark criterion as failed
         if ("FAILED".equalsIgnoreCase(state)) {
             logger.info("Job {} marked as FAILED", job.getId());
             return EvaluationOutcome.fail("Ingestion failed: job state is FAILED", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If summary reports failures or errors, consider ingestion failed
         Job.Summary summary = job.getSummary();
         if (summary != null) {
             Integer failedCount = summary.getFailedCount();
             if (failedCount != null && failedCount > 0) {
                 logger.info("Job {} reported {} failed records", job.getId(), failedCount);
                 return EvaluationOutcome.fail("Ingestion produced failed records: " + failedCount, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (summary.getErrors() != null && !summary.getErrors().isEmpty()) {
                 int errs = summary.getErrors().size();
                 logger.info("Job {} reported {} error(s) in summary", job.getId(), errs);
                 return EvaluationOutcome.fail("Ingestion reported errors: " + errs + " error(s)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Inconsistent completion metadata: completedTimestamp present but state not SUCCEEDED/FAILED
         if (job.getCompletedTimestamp() != null && !job.getCompletedTimestamp().isBlank()) {
             if (!"SUCCEEDED".equalsIgnoreCase(state) && !"FAILED".equalsIgnoreCase(state)) {
                 logger.warn("Job {} has completedTimestamp but state is {}", job.getId(), state);
                 return EvaluationOutcome.fail("Job has completion timestamp but state is inconsistent", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // No failure indications found
         return EvaluationOutcome.success();
    }
}